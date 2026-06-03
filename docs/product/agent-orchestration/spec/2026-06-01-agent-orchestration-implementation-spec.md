# Agent-Orchestration 详细设计（实现规范）

> 范围：Agent-Orchestration 微服务的实现级规范——数据库 DDL、对外 API 详细 schema、run/step/attempt 状态机完整规则。配套概要设计见 `2026-05-30-orchestrator-agentflow-workflow-template-design.md`，里程碑计划见 `../plan/2026-06-01-agent-orchestration-milestones-plan.md`。
>
> 数据库：openGauss（PostgreSQL 兼容）。语言：Java 21 + Spring Boot 3。
>
> **MVP 范围（鉴权暂缓）**：经产品决策，MVP 阶段**不实现用户级鉴权**。下文凡涉及 `Authorization: Bearer <JWT>`、`401 UNAUTHENTICATED`、`403 FORBIDDEN`、`team_id/user_id` 授权范围比对、「浏览器可直连」的描述均为**后续目标**，MVP 暂不落地；只读接口当前为无鉴权直连。`team_id/user_id`、`401/403` 等字段与错误码予以保留，供后续补齐时直接启用。安全风险（任何人凭 runId 可读他人 run）需单独跟踪。
>
> **架构修订（单存储，2026-06）**：改为**单存储**——状态与事件只存本服务，**不回流 Agent-Management**。下文 §1.7 `outbox_message` 表、§2.9 `POST /internal/agent-orchestration/events` 出站接口、以及所有「写 outbox / 同事务回流 / 至少一次投递 / 背压降采样」描述**均已废弃**，相关代码（`outbox_message` 表、`OutboxService`/`OutboxWorker`/`AgentManagementClient`）已移除。取而代之：Agent-Management 经 `GET /runs/{id}` 查询 run 终态、经 `GET /runs/{id}/events/poll` 拉取历史展示事件；新增轮询端点见 §9.6。

## 1. 数据库 DDL

### 1.1 表清单

| 表 | 用途 |
|---|---|
| `workflow_run` | run 实例 + AgentFlow 快照 |
| `workflow_step` | run 内逻辑步骤 |
| `step_attempt` | step 的物理执行尝试（对应 Agent Core RuntimeAttempt） |
| `step_dependency` | run 内 step 的 DAG 边 |
| `workflow_event` | 事件留存（控制类全量 + 展示类按需） |
| `outbox_message` | 回流 Agent-Management 的可靠投递队列 |
| `processed_event` | Agent Core 入站事件 eventId 去重 |

ID 统一用 `varchar(64)`（UUID/雪花皆可）；时间用 `timestamptz`；枚举用 `varchar` + 应用层校验（不绑数据库枚举，便于演进）；JSON 用 openGauss `jsonb`。

### 1.2 `workflow_run`

```sql
CREATE TABLE workflow_run (
    id                  VARCHAR(64)  PRIMARY KEY,
    idempotency_key     VARCHAR(128) NOT NULL,
    status              VARCHAR(20)  NOT NULL,          -- PENDING/RUNNING/SUSPENDED/COMPLETED/FAILED/CANCELLING/CANCELLED
    flow_id             VARCHAR(64)  NOT NULL,          -- AgentFlow.flowId（来源模板 id）
    flow_snapshot_id    VARCHAR(64)  NOT NULL,          -- 本次冻结副本 id
    flow_name           VARCHAR(256),
    schema_version      VARCHAR(20)  NOT NULL,          -- AgentFlow schemaVersion
    team_id             VARCHAR(64)  NOT NULL,          -- 鉴权授权范围
    user_id             VARCHAR(64)  NOT NULL,
    task_id             VARCHAR(64)  NOT NULL,
    project_id          VARCHAR(64)  NOT NULL,
    agent_flow          JSONB        NOT NULL,          -- AgentFlow 完整不可变快照
    error_code          VARCHAR(64),
    error_message       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    version             INTEGER      NOT NULL DEFAULT 0  -- 乐观锁
);

CREATE UNIQUE INDEX uk_run_idempotency ON workflow_run (idempotency_key);
CREATE INDEX idx_run_status      ON workflow_run (status);
CREATE INDEX idx_run_task        ON workflow_run (task_id);
CREATE INDEX idx_run_team        ON workflow_run (team_id);
```

- `idempotency_key` 唯一约束实现 `POST /runs` 幂等；
- `agent_flow` 存整份快照，渲染、重试、reconcile 都从这里读，不回查 Agent-Management；
- `team_id/user_id` 规划供用户级鉴权（验 JWT 后比对授权范围）——**鉴权 MVP 暂缓，见顶部范围说明**；字段保留，暂不参与鉴权判定。

### 1.3 `workflow_step`

```sql
CREATE TABLE workflow_step (
    id                  VARCHAR(64)  PRIMARY KEY,
    run_id              VARCHAR(64)  NOT NULL REFERENCES workflow_run(id),
    step_key            VARCHAR(128) NOT NULL,          -- AgentFlowStep.id
    name                VARCHAR(256),
    status              VARCHAR(20)  NOT NULL,          -- PENDING/READY/RUNNING/SUSPENDED/COMPLETED/FAILED/CANCELLED
    order_index         INTEGER      NOT NULL,          -- 多个 ready 时的串行选择顺序
    executor_type       VARCHAR(32)  NOT NULL,          -- CLAUDE_CODE/OPENCODE/CODEX
    requires_confirmation BOOLEAN    NOT NULL DEFAULT false,
    rendered_prompt     TEXT,                            -- 执行前渲染好的 prompt
    session_ref         VARCHAR(256),                    -- 对话上下文标识，续聊用
    output_summary      TEXT,                            -- StepOutput.summary
    output_result       TEXT,                            -- StepOutput.result
    output_artifact_refs JSONB,                           -- StepOutput.artifactRefs (string[])
    retry_count         INTEGER      NOT NULL DEFAULT 0,  -- 已自动重试次数
    error_code          VARCHAR(64),
    error_message       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    version             INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_step_run_key ON workflow_step (run_id, step_key);
CREATE INDEX idx_step_run_status   ON workflow_step (run_id, status);
```

- `session_ref` 在 attempt 成功后写入，`continue` 续聊时作为 `resumeFromSessionRef` 回传；
- `retry_count` 与配置的 maxRetries 比较决定是否自动重试。

### 1.4 `step_attempt`

```sql
CREATE TABLE step_attempt (
    id                  VARCHAR(64)  PRIMARY KEY,
    run_id              VARCHAR(64)  NOT NULL REFERENCES workflow_run(id),
    step_id             VARCHAR(64)  NOT NULL REFERENCES workflow_step(id),
    attempt_no          INTEGER      NOT NULL,          -- 从 1 递增
    status              VARCHAR(20)  NOT NULL,          -- PENDING/STARTING/RUNNING/SUCCEEDED/FAILED/CANCELLED
    trigger             VARCHAR(20)  NOT NULL,          -- INITIAL/AUTO_RETRY/MANUAL_RETRY/CONTINUE
    runtime_attempt_ref VARCHAR(256),                    -- Agent Core 返回的运行态引用
    resume_from_session_ref VARCHAR(256),                -- 续聊入参（CONTINUE 时非空）
    feedback            TEXT,                             -- 续聊用户反馈（CONTINUE 时）
    failure_reason      VARCHAR(32),                     -- RUNTIME_CREATE_FAILED/RUNTIME_FAILED/EXECUTOR_FAILED/TIMEOUT/HEARTBEAT_LOST/PROMPT_RENDER_ERROR/SECRET_ERROR/UNKNOWN
    last_heartbeat_at   TIMESTAMPTZ,
    error_message       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    version             INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_attempt_step_no ON step_attempt (step_id, attempt_no);
CREATE INDEX idx_attempt_run_status   ON step_attempt (run_id, status);
CREATE INDEX idx_attempt_heartbeat    ON step_attempt (status, last_heartbeat_at);  -- watchdog 扫描
```

- `(step_id, attempt_no)` 唯一，配合应用层 CAS 实现 attempt 创建幂等；
- `trigger` 区分首次/自动重试/手动重试/续聊，便于审计与统计；
- `idx_attempt_heartbeat` 支持 watchdog 扫"RUNNING 且 last_heartbeat_at 过旧"。

### 1.5 `step_dependency`

```sql
CREATE TABLE step_dependency (
    id              VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL REFERENCES workflow_run(id),
    from_step_key   VARCHAR(128) NOT NULL,
    to_step_key     VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_dep ON step_dependency (run_id, from_step_key, to_step_key);
CREATE INDEX idx_dep_to    ON step_dependency (run_id, to_step_key);   -- 算 ready：查某 step 的所有上游
CREATE INDEX idx_dep_from  ON step_dependency (run_id, from_step_key); -- 算下游
```

run 创建时从 AgentFlow.edges 落库；MVP 串行调度也保留，便于演进到并行。

### 1.6 `workflow_event`

```sql
CREATE TABLE workflow_event (
    id              VARCHAR(64) PRIMARY KEY,           -- 内部主键
    event_id        VARCHAR(64) NOT NULL,              -- AgentExecutionEvent.eventId
    run_id          VARCHAR(64) NOT NULL,
    step_id         VARCHAR(64),
    attempt_id      VARCHAR(64),
    event_type      VARCHAR(64) NOT NULL,
    category        VARCHAR(16) NOT NULL,              -- control/display/runtime
    sequence_no     BIGINT,                             -- attempt 内顺序
    source          VARCHAR(20),                        -- agent-core/executor/runtime
    payload         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_run_seq  ON workflow_event (run_id, sequence_no);
CREATE INDEX idx_event_attempt  ON workflow_event (attempt_id);
```

- 控制类事件全量留存（审计/重放状态推进）；展示类可按保留策略裁剪（量大）；
- 实时输出走内存/Redis 推送，本表用于补偿和审计，不是实时主路径。

### 1.7 `outbox_message`

```sql
CREATE TABLE outbox_message (
    id              VARCHAR(64) PRIMARY KEY,           -- outboxId
    run_id          VARCHAR(64) NOT NULL,
    payload         JSONB        NOT NULL,             -- 回流 Agent-Management 的事件体
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/SENT/FAILED
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_outbox_dispatch ON outbox_message (status, next_retry_at);
```

状态变更与 outbox 写入在**同一事务**，worker 轮询 `PENDING` 投递 Agent-Management，至少一次。

### 1.8 `processed_event`

```sql
CREATE TABLE processed_event (
    event_id        VARCHAR(64) PRIMARY KEY,           -- Agent Core 入站事件去重
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

入站事件先查此表，存在则丢弃，实现 eventId 幂等消费。可定期清理旧记录。

### 1.9 索引与约束小结

- 幂等：`workflow_run.idempotency_key` 唯一、`step_attempt(step_id, attempt_no)` 唯一、`processed_event.event_id` 主键、`outbox_message.id`=outboxId；
- 调度热点：`idx_step_run_status`、`idx_attempt_run_status` 配合 `FOR UPDATE SKIP LOCKED`；
- watchdog：`idx_attempt_heartbeat`；
- 乐观锁：run/step/attempt 都带 `version`，状态更新走 CAS。

## 2. 对外 API 详细 schema

通用约定：

- 所有请求**规划**带 `Authorization: Bearer <JWT>`（**鉴权 MVP 暂缓，见顶部范围说明**）；写操作经 Agent-Management 转发，只读接口浏览器直连；
- 错误响应统一体：`{ "errorCode": string, "message": string, "details"?: object }`；
- 通用错误码：`401 UNAUTHENTICATED`(未认证)、`403 FORBIDDEN`(越权/不在 run 授权范围)、`404 NOT_FOUND`(run/step 不存在)、`409 CONFLICT`(状态不允许该操作)、`422 VALIDATION_ERROR`(参数/AgentFlow 校验失败)、`429 RESOURCE_EXHAUSTED`、`500 INTERNAL`。其中 `401/403` 随鉴权 MVP 暂缓而暂不返回，保留备用。

### 2.1 POST /runs — 启动 run

请求：
```json
Header: Idempotency-Key: <idempotencyKey>
Body:   { "agentFlow": { /* 完整 AgentFlow，见概要设计 §6 */ } }
```

成功 200：
```json
{ "runId": "run_abc", "status": "RUNNING" }
```

行为与错误：
- 按 `Idempotency-Key` 幂等；命中已存在 run 返回其当前 `{runId,status}`（200，不报错）；
- 422：AgentFlow schema 非法（缺字段、DAG 有环/自环、edge 引用不存在 step、step id 重复）；
- 403：JWT 用户不在 AgentFlow.tenant 授权范围（**鉴权 MVP 暂缓，暂不返回**）；
- 同一 Idempotency-Key 但 body 不一致 → 409。

### 2.2 POST /runs/{runId}/cancel — 取消 run

成功 200：
```json
{ "runId": "run_abc", "status": "CANCELLING" }
```

行为与错误：
- 幂等；run 已终态（COMPLETED/FAILED/CANCELLED）→ 直接返回终态（200，no-op）；
- 404：run 不存在；403：越权；
- 进入 CANCELLING 后异步删 in-flight attempt，终态后转 CANCELLED。

### 2.3 POST /runs/{runId}/steps/{stepId}/confirm — 确认通过

前置：step 处于 SUSPENDED。

成功 200：
```json
{ "runId": "run_abc", "stepId": "step_verify", "stepStatus": "COMPLETED", "runStatus": "RUNNING" }
```

行为与错误：
- 409：step 非 SUSPENDED（已 completed、运行中、run 已 cancelling 等）；
- 404：run/step 不存在；403：越权；
- 成功后 step→COMPLETED，触发下游 ready，run→RUNNING（若无下游则 run→COMPLETED）。

### 2.4 POST /runs/{runId}/steps/{stepId}/continue — 续聊

前置：step 处于 SUSPENDED。

请求：
```json
{ "feedback": "请补充并发场景的测试", "actionKey": "<幂等键，可选>" }
```

成功 200：
```json
{ "runId": "run_abc", "stepId": "step_verify", "attemptNo": 2, "stepStatus": "RUNNING" }
```

行为与错误：
- 起新 attempt（trigger=CONTINUE，resume_from_session_ref=step.session_ref，feedback 入参）；
- `actionKey` 或基于 step 当前 attempt_no 的 CAS 防重复起 attempt（并发 continue 只成一个）；
- 409：step 非 SUSPENDED；422：feedback 为空；404/403 同上；
- 续聊 attempt 成功后 step 回 SUSPENDED。

### 2.5 POST /runs/{runId}/steps/{stepId}/retry — 手动重试

前置：step 处于 FAILED。

请求：
```json
{ "resumeSession": false, "actionKey": "<幂等键，可选>" }
```

成功 200：
```json
{ "runId": "run_abc", "stepId": "step_fix", "attemptNo": 3, "stepStatus": "RUNNING" }
```

行为与错误：
- 起新 attempt（trigger=MANUAL_RETRY）；`resumeSession=true` 时复用 session_ref，否则从该 step 重新开始；
- 409：step 非 FAILED；幂等同 continue；404/403 同上。

### 2.6 GET /runs/{runId} — 查询（浏览器可直连；鉴权 MVP 暂缓）

成功 200：
```json
{
  "run": { "runId": "run_abc", "status": "RUNNING", "startedAt": "...", "errorCode": null },
  "steps": [
    { "stepId": "step_analyze", "stepKey": "analyze", "status": "COMPLETED",
      "outputSummary": "...", "currentAttemptNo": 1 },
    { "stepId": "step_fix", "stepKey": "fix", "status": "RUNNING",
      "currentAttemptNo": 2, "lastHeartbeatAt": "..." }
  ]
}
```
- 用户级鉴权（验 JWT + 比对 run 的 team_id/user_id 授权范围；403 越权；404 不存在）——**鉴权 MVP 暂缓，当前无鉴权直连，仅保留 404**。

### 2.7 GET /runs/{runId}/events — 实时事件流（WS/SSE，浏览器可直连；鉴权 MVP 暂缓）

- 协议：WebSocket 或 SSE；连接时**规划**校验 JWT + run 授权范围（**鉴权 MVP 暂缓，当前无鉴权直连**）；
- 推送展示类事件（thinking/message/tool_use/tool_result/stdout/stderr），按 `sequenceNo` 有序；
- 支持 `?fromSequence=<n>` 断线重连续传；
- 消息体：`{ eventId, eventType, category:"display", sequenceNo, payload }`。

### 2.8 POST /internal/agent-core/events — 入站事件（内部）

请求：`AgentExecutionEvent`（见概要设计 §8.3）。

成功 200：`{ "accepted": true }`

行为与错误：
- 先查 `processed_event` 按 `eventId` 去重，重复 → 200 直接返回（幂等）；
- 校验 run/step/attempt 归属，不匹配 → 422；
- 控制类推进状态机（事务 + CAS），展示类入实时通道 + 按需留存；
- 写 outbox（回流 Agent-Management）与状态变更同事务。

### 2.9 出站：POST /internal/agent-orchestration/events（调 Agent-Management）

- 由 outbox worker 发起；体：`{ outboxId, type, runId, stepId?, attemptId?, payload }`；
- Agent-Management 按 outboxId/eventId 去重；失败重试（指数退避，记 `next_retry_at`）。

## 3. 状态机完整规则

三级状态机：WorkflowRun、WorkflowStep、StepAttempt。所有状态写入走 **CAS（version 乐观锁）+ 事务**；命中竞态（version 不匹配）则重读重判，不强写。非法转换一律拒绝（接口层返 409，内部触发记为告警并丢弃）。

### 3.1 StepAttempt 状态机

| From | To | 触发 | 动作 |
|---|---|---|---|
| (无) | PENDING | 调度器/confirm 决定要跑一次 | 创建 attempt 记录 |
| PENDING | STARTING | 调 Agent Core StartAttempt 前 | 标记 starting |
| STARTING | RUNNING | 收到 `runtime.attempt_created`/`runtime.running`/`attempt.started` | 记 runtime_attempt_ref、started_at |
| STARTING | FAILED | StartAttempt 调用失败 | failure_reason=RUNTIME_CREATE_FAILED |
| RUNNING | SUCCEEDED | `attempt.result(success)` + `runtime.completed` | 写 output、session_ref、finished_at |
| RUNNING | FAILED | `attempt.result(failed)` / `runtime.failed` / watchdog 超时 / 心跳丢失 | 记 failure_reason |
| PENDING/STARTING/RUNNING | CANCELLED | run cancel 级联 | 删 runtime、记 finished_at |

终态：SUCCEEDED / FAILED / CANCELLED（不可再转）。

并发边界：
- attempt 创建用 `(step_id, attempt_no)` 唯一约束 + CAS 防重复（并发 continue/retry 只成一个）；
- 终态幂等：已终态再收到同类事件 → 丢弃；
- `attempt.result` 与 `runtime.completed` 可乱序到达：先到者记录，待两者齐或 watchdog 兜底再定终态。

### 3.2 WorkflowStep 状态机

| From | To | 触发 | 动作 |
|---|---|---|---|
| (无) | PENDING | run 创建时按 AgentFlow.steps 建 | — |
| PENDING | READY | 所有上游 step COMPLETED（无上游则建后即 READY） | 进入可调度集 |
| READY | RUNNING | 调度器创建并启动 attempt | retry_count 视 trigger |
| RUNNING | COMPLETED | attempt SUCCEEDED 且 `requires_confirmation=false` | 写 output，触发下游算 ready |
| RUNNING | SUSPENDED | attempt SUCCEEDED 且 `requires_confirmation=true` | 持久化 session_ref |
| RUNNING | RUNNING | attempt FAILED 且 `retry_count < maxRetries` | retry_count++，起新 attempt(AUTO_RETRY) |
| RUNNING | FAILED | attempt FAILED 且重试耗尽 | 记 error |
| SUSPENDED | COMPLETED | confirm | 触发下游算 ready |
| SUSPENDED | RUNNING | continue | 起新 attempt(CONTINUE)，跑完回 SUSPENDED |
| FAILED | RUNNING | 手动 retry | 起新 attempt(MANUAL_RETRY) |
| 任意非终态 | CANCELLED | run cancel 级联 | — |

终态：COMPLETED / CANCELLED（FAILED 可经手动 retry 复活，故非严格终态）。

并发边界：
- “算下游 ready” 必须在 step→COMPLETED 同事务内或之后用 `FOR UPDATE` 锁下游行，避免漏触发/重复触发；
- continue 跑完回 SUSPENDED：续聊 attempt SUCCEEDED 时，因 `requires_confirmation=true`，走 RUNNING→SUSPENDED 而非 COMPLETED。

### 3.3 WorkflowRun 状态机

| From | To | 触发 | 动作 |
|---|---|---|---|
| (无) | PENDING | POST /runs 创建 | 落库快照、建 step/dep |
| PENDING | RUNNING | 调度首批 ready step | started_at |
| RUNNING | SUSPENDED | 任一 step 进入 SUSPENDED | — |
| SUSPENDED | RUNNING | confirm/continue 使无 step 处于 SUSPENDED | — |
| RUNNING | COMPLETED | 所有 step COMPLETED | finished_at |
| RUNNING | FAILED | 某 step FAILED 且不再重试、无法继续 | 记 error |
| 任意非终态 | CANCELLING | cancel 请求 | 删 in-flight attempt |
| CANCELLING | CANCELLED | 所有 attempt 终态 | finished_at |

终态：COMPLETED / FAILED / CANCELLED。

run 状态由 step 聚合驱动（每次 step 状态变更后重算 run 状态）：
- 有 step SUSPENDED → run SUSPENDED；
- 否则有 step RUNNING/READY/PENDING → run RUNNING；
- 全 COMPLETED → run COMPLETED；
- 有 step FAILED 且无可继续 → run FAILED。

### 3.4 跨级竞态处理

| 竞态 | 处理 |
|---|---|
| cancel 与 confirm/continue 并发 | run 进入 CANCELLING 后，confirm/continue 一律 409；以 run 状态为准 |
| 事件乱序（result 先于 running） | 状态机按"可达性"判断，缺中间态可补；非法跳转记告警 |
| 多副本同时调度同一 step | `SELECT ... FOR UPDATE SKIP LOCKED` 抢占 + CAS，只一个成功 |
| watchdog 与正常 result 并发 | 谁先 CAS 成功定终态，另一个判终态后丢弃 |
| 重复 retry/continue | actionKey 或 attempt_no CAS，唯一约束兜底 |

### 3.5 watchdog 规则

- 扫描 `step_attempt` 中 `status=RUNNING` 且 `last_heartbeat_at < now - heartbeatTimeout`（连续缺失阈值，默认 ~5min）；
- 或 `started_at < now - stepTimeout`（步骤硬超时，默认 30min，可配）；
- 命中 → 调 Agent Core CancelAttempt → attempt FAILED(TIMEOUT/HEARTBEAT_LOST) → 走 step 重试/失败逻辑；
- 与正常事件并发用 CAS 仲裁，避免双重终态。


