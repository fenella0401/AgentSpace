# agent-orchestration

AgentSpace 的 **Agent-Orchestration** 微服务：执行 Agent-Management 提交的 **AgentFlow** 不可变快照，
围绕 run / step / attempt 三级状态机做**可恢复、幂等、DB 驱动**的轻量工作流编排。它只执行 AgentFlow，
不读 Agent-Management 业务表、不感知底层运行时（容器/进程由 Agent Core 负责）。

设计文档：
- 概要设计 `../../docs/product/agent-orchestration/spec/2026-05-30-orchestrator-agentflow-workflow-template-design.md`
- 详细设计（DDL / API / 状态机）`../../docs/product/agent-orchestration/spec/2026-06-01-agent-orchestration-implementation-spec.md`
- 里程碑 / task / 特性(FE)分解 `../../docs/product/agent-orchestration/plan/`
- 上线 Checklist 与遗留项 `../../docs/product/agent-orchestration/operations.md`

---

## 1. 技术栈

| 层面 | 选型 |
|---|---|
| 语言 / 框架 | Java 21 + Spring Boot 3.2.5 |
| 构建 | Maven |
| 状态存储 | openGauss（官方 `opengauss-jdbc` 驱动）；测试用 H2 PostgreSQL 兼容模式 |
| 表结构 | 手动初始化（`db/schema.sql`，openGauss/PG 通用，含 `IF NOT EXISTS`）；JPA 仅 `validate`，不自动建表/迁移 |
| 调度 | DB polling + CAS（`@Version` 乐观锁）多副本不重复（`FOR UPDATE SKIP LOCKED` 为上线前优化，未落地） |
| 状态机 | 自研轻量三级状态机（非 Temporal/Camunda） |
| 入站事件 | HTTP callback（`POST /internal/agent-core/events`） |
| 出站回流 | HTTP callback + outbox（至少一次投递） |
| 展示事件下发 | 轮询（`GET /runs/{id}/events/poll`，推荐，兼容多副本）或 SSE（`GET /runs/{id}/events`，单实例内存广播） |
| 可观测性 | Micrometer + Prometheus（`/actuator/prometheus`） |

> **鉴权 MVP 暂缓**：只读接口当前为无鉴权直连，`team_id/user_id` 字段与 `401/403` 已预留，
> 上线前须补齐（见 operations.md）。

---

## 2. 架构

### 2.1 微服务边界

```
        浏览器
       │     │  只读直连(查询/事件流:轮询或 SSE)
 写操作│     └─────────────────────────┐
       ▼                               ▼
 Agent-Management ──POST /runs──▶  Agent-Orchestration  ──StartAttempt──▶  Agent Core
 (业务控制面)     ◀─outbox 回流──   (本服务:编排/状态机)  ◀──执行事件──────   (运行时执行层)
```

- **Agent-Management**：组装 AgentFlow、发起写操作、接收回流、持久化历史；
- **Agent-Orchestration（本服务）**：执行 AgentFlow、推进状态机、实时分发、回流；
- **Agent Core**：把单个 StepAttempt 落到实际执行环境，回报执行事件。

### 2.2 内部分层

```
controller  ── HTTP 端点(对外只读 /runs/**、内部回调 /internal/**，按路径区分)
    │
service     ── 业务逻辑:状态机推进 / 调度决策 / 事件处理 / 续聊 / outbox / 可观测性
    │             ├─ statemachine: 合法转换表
    │             └─ 校验器 / 渲染器 / DAG 工具
    ├── scheduler ── 定时触发:调度轮询 / watchdog / outbox worker / 重启 reconcile（详见 §2.5）
    ├── client    ── 出入站:Agent Core 调用 + Agent-Management 回流(各含 mock 实现)
    │                 └─ event: EventSink 投递抽象(解耦事件源与状态机)
    ├── repository── Spring Data JPA(openGauss 事实状态源)
    └── model     ── 数据结构:entity(JPA) / flow(AgentFlow 快照) / event(事件协议) / 枚举
```

### 2.3 启动控制流（POST /runs → 首个 attempt）

```
Agent-Management ──POST /runs(AgentFlow)──▶ RunService
   1. 幂等(idempotencyKey 命中→返回已存在 run)
   2. AgentFlowValidator 校验(DAG 无环/自环、edge 引用、step id 唯一)
   3. 落库:WorkflowRun(快照 JSON) + WorkflowStep[] + StepDependency[]
   4. DagSupport 算初始 ready(入度0)，串行选 order_index 最小者
   5. StepLauncher: 渲染 prompt → 建 StepAttempt → AgentCoreClient.startAttempt
   6. run → RUNNING，返回 {runId, status}
```

### 2.4 事件流（执行中）

```
Claude Code SDK ──stream-json──▶ ClaudeCodeStreamParser ─┐ (按 SDK 格式解析为内部事件)
Agent Core ──────事件───────────────────────────────────┴─▶ POST /internal/agent-core/events ──▶ EventIngestService
   去重(processed_event) → 归属校验 → 按 category 分流:
     ├ control(attempt.started/heartbeat/result) ─┐
     ├ runtime(runtime.completed/failed/...)      ├─▶ 乱序合并判终态 ─▶ AttemptResultHandler
     └ display(thinking/message/tool_*/std*)      ─┘     │  ├─ 成功+无需确认 → step COMPLETED → 触发下游 ready
            │                                            │  ├─ 成功+requiresConfirmation → SUSPENDED
            ├─▶ RunEventBroadcaster(SSE 实时推浏览器)    │  ├─ 失败+可重试 → AUTO_RETRY
            └─▶ OutboxService(回流 AM，受背压降采样)     │  └─ 失败+耗尽 → step FAILED → run FAILED
                                                          └─▶ RunStateRecalculator(step 聚合驱动 run 状态)
```

`attempt.result`(executor 语义结果) 与 `runtime.completed/failed`(运行环境物理终态)**可乱序到达**，
先到者暂存于 `step_attempt` 的 `pending_*` / `runtime_terminal` 字段，两信号齐了再定终态（见 §8.4 设计）。

**Claude Code 接入**：`client.claudecode` 把 SDK `stream-json` 输出（system/assistant/user/result）
翻译为内部统一事件——assistant 的 text/thinking/tool_use → display 类，user 的 tool_result → display 类，
`result` 行 → `attempt.result` + 合成的 `runtime.completed/failed`（直连作执行器+运行时时）。
eventId 由 attemptId+消息块确定性派生（SHA-256 截断），断线重读天然去重；thinking 取摘要、tool 输出限长（§8.4 约束）。

### 2.5 调度与自驱动（scheduler 层）

本服务是 **DB polling 架构**（非事件总线 / Temporal）：没有外部调度器，工作流靠 `scheduler` 包里的
定时任务**主动轮询数据库状态**来推进。该层只做「触发 / 扫描」，调度决策与状态推进逻辑都在 `service` 层
（便于测试时直接驱动逻辑、绕开定时器）。四个组件各管一类必须主动发生、不能靠外部请求驱动的事：

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ StepScheduler        每 2s   扫 READY step → SchedulingService 启动            │
│  (推进工作流)                step 完成→下游变 READY→下轮启动；这是流程前进的根本  │
│                              多副本：CAS(@Version) 乐观锁保证不重复调度          │
├──────────────────────────────────────────────────────────────────────────────┤
│ Watchdog             每 5s   扫卡住的执行，兜底判定：                            │
│  (兜底超时/卡死)             ├ RUNNING 心跳丢失(>heartbeat-timeout) → 判失败    │
│                              ├ RUNNING 硬超时(>step-timeout)        → 判失败    │
│                              └ STARTING 卡死(>start-timeout)        → reconcile 对齐│
├──────────────────────────────────────────────────────────────────────────────┤
│ OutboxWorker         每 1s   扫 outbox PENDING 且到期 → 投递 Agent-Management   │
│  (可靠回流)                  成功→SENT；失败→指数退避重试（至少一次投递）        │
├──────────────────────────────────────────────────────────────────────────────┤
│ ReconciliationRunner 启动时  对所有 in-flight attempt 跑一次 reconcile          │
│  (重启恢复，非定时)          查 Agent Core 实况对齐状态；@Profile("!test")       │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **StepScheduler** 让流程往前走，**Watchdog** 兜底卡死的，**OutboxWorker** 保证回流送达，
  **ReconciliationRunner** 管重启恢复——四者合起来构成这个无外部调度器的编排引擎的「自驱动」能力；
- **READY step 的并发抢占靠乐观锁 CAS，不是数据库行锁**：`SchedulingService.tryLaunchReadyStep` 依次过三关——
  ① 状态须仍为 READY（已被抢走则跳过）；② 串行单飞：同 run 已有 RUNNING step 则不启动；
  ③ `StepLauncher` 把 step 翻 RUNNING 时 `save` 走 `@Version`，SQL 为
  `UPDATE ... SET status='RUNNING', version=version+1 WHERE id=? AND version=?`。
  ①② 是无锁 SELECT，可能两副本同时通过（TOCTOU）；真正分胜负的是 ③——并发写同一行只有一个 CAS 命中，
  另一个影响 0 行抛 `OptimisticLockException` 回滚重试。另有 `step_attempt(step_id, attempt_no)` 唯一约束兜底；
- 状态写入全程 **CAS（`@Version`）+ 事务**：watchdog 判失败复用 `AttemptResultHandler.onAttemptFailed`，
  对已终态 attempt 幂等忽略，与正常 `attempt.result` 事件并发安全；
- 轮询间隔均可配（见 §8）；测试 profile 下间隔设为极大值，由测试显式调用 service 驱动，不靠定时器。

> `SELECT ... FOR UPDATE SKIP LOCKED`（悲观跳锁，从源头避免抢同一行的无效计算）为上线前的性能优化，
> **尚未落地**；当前 CAS 已保证多副本调度的正确性，见 `operations.md`。

### 2.6 任务全生命周期（初始化 → 启动 → 执行 → 结束）

把 §2.3~§2.5 串起来，跟随**单个 step / attempt** 走完一遍。竖线左侧是触发者，`▸` 为状态翻转：

```
① 初始化           POST /runs ─▶ RunService（同事务落库）
   (RunService)     ▸ run PENDING、step PENDING、依赖边落库
                    ▸ 入度0 的 step ▸ READY；run ▸ RUNNING（首个 step 同步走②③）

② 调度抢占         StepScheduler 每 2s 扫 READY ─▶ SchedulingService.tryLaunchReadyStep
   (scheduler→svc)  关卡：step 仍 READY？同 run 无 RUNNING（串行单飞）？
                    ▸ CAS 翻 step READY→RUNNING（@Version；多副本只一个赢）

③ 启动 attempt     StepLauncher.launch
   (StepLauncher)   渲染 prompt（失败→attempt/step FAILED，不调 Agent Core）
                    ▸ 建 StepAttempt PENDING → AgentCoreClient.startAttempt → ▸ STARTING
                    ⚠ startAttempt 中断：保持 STARTING，由⑤watchdog/reconcile 兜底

④ 执行中           Agent Core / Claude Code SDK ──事件──▶ /internal/agent-core/events
   (EventIngest)    去重→归属校验→分流：
                    ├ attempt.started/runtime.running ▸ attempt STARTING→RUNNING
                    ├ display 事件 → 浏览器（轮询 /events/poll 或 SSE）+ 回流 outbox
                    └ heartbeat 刷新 last_heartbeat_at（⑤watchdog 据此判活）

⑤ 终态判定         attempt.result + runtime.*（可乱序，pending_* 暂存，齐了才定）
   (ResultHandler)  ┌ 成功 → ▸ attempt SUCCEEDED
                    │        ├ 无需确认 → ▸ step COMPLETED → 触发下游 ▸ READY（回②）
                    │        └ requiresConfirmation → ▸ step SUSPENDED（等 confirm/continue）
                    ├ 失败 → ▸ attempt FAILED
                    │        ├ 未超 maxRetries → step ▸ READY 起 AUTO_RETRY attempt（回②③）
                    │        └ 超限 → ▸ step FAILED
                    └ 兜底：Watchdog 每 5s 扫超时/心跳丢失 → CancelAttempt + 判 FAILED
                            STARTING 卡死 → reconcile 查 Agent Core 实况对齐

⑥ run 收敛         每次终态变更 ─▶ RunStateRecalculator（step 聚合驱动 run）
   (Recalculator)   全 COMPLETED ▸ run COMPLETED ｜ 有 FAILED 且无可推进 ▸ FAILED
                    ｜ 任一 SUSPENDED ▸ run SUSPENDED ｜ cancel ▸ CANCELLING→CANCELLED
                    ▸ run 终态变更入 outbox 回流 Agent-Management
```

要点：**②③ 与 ⑤ 的下游/重试构成主循环**——一个 step 终态后下游变 READY，下一轮调度接力，直到无可推进的 step；
全程靠 §2.5 的轮询自驱动，无外部编排器。手动干预（confirm / continue / retry / cancel）从 ⑤ 的 SUSPENDED/FAILED
态切入，重新汇入②③。

---

## 3. 核心概念

| 概念 | 说明 | 实体 |
|---|---|---|
| **AgentFlow** | Agent-Management 启动 run 时冻结的不可变执行快照（steps + edges + 上下文 + 凭证引用） | `model/flow/AgentFlow` |
| **WorkflowRun** | 一次 run 实例，持有 AgentFlow 快照 | `workflow_run` |
| **WorkflowStep** | run 内一个逻辑步骤（对应 AgentFlowStep） | `workflow_step` |
| **StepAttempt** | step 的一次物理执行尝试（对应 Agent Core RuntimeAttempt） | `step_attempt` |
| **StepDependency** | run 内 step 的 DAG 边 | `step_dependency` |

---

## 4. 状态机

集中定义于 `service/statemachine/StateTransitions`，所有状态写入走 CAS + 事务，非法转换拒绝。

**Run**：`PENDING → RUNNING → {COMPLETED, FAILED, SUSPENDED}`；`* → CANCELLING → CANCELLED`
（由 step 聚合驱动：任一 SUSPENDED→run SUSPENDED；全 COMPLETED→COMPLETED；有 FAILED 且无可推进→FAILED）

**Step**：`PENDING → READY → RUNNING → {COMPLETED, SUSPENDED, FAILED}`；
`RUNNING→RUNNING`(自动重试)；`SUSPENDED→{COMPLETED(confirm), RUNNING(continue)}`；`FAILED→RUNNING`(手动 retry)

**Attempt**：`PENDING → STARTING → RUNNING → {SUCCEEDED, FAILED, CANCELLED}`（终态幂等）

---

## 5. API 端点

写操作经 Agent-Management 转发，只读接口浏览器可直连（鉴权暂缓）。

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/runs` | 启动 run（`Idempotency-Key` 头幂等） |
| POST | `/runs/{runId}/cancel` | 取消 run（级联删 in-flight attempt） |
| POST | `/runs/{runId}/steps/{stepId}/confirm` | 确认 SUSPENDED step，推进下游 |
| POST | `/runs/{runId}/steps/{stepId}/continue` | 续聊（`{feedback, actionKey}`），起新 attempt |
| POST | `/runs/{runId}/steps/{stepId}/retry` | 手动重试 FAILED step |
| GET | `/runs/{runId}` | 查询 run / step 状态快照 |
| GET (SSE) | `/runs/{runId}/events` | 展示类事件实时流（`?fromSequence=` 断线续传） |
| GET | `/runs/{runId}/events/poll` | 展示类事件增量轮询（`?fromSequence=&limit=`，建议 5~10s 间隔；无长连接、兼容多副本） |
| POST | `/internal/agent-core/events` | Agent Core 上报执行事件（内部） |
| POST | `/internal/agent-orchestration/events` | （出站）回流 Agent-Management |

错误码：`404 NOT_FOUND` / `409 CONFLICT` / `422 VALIDATION_ERROR` / `429 RESOURCE_EXHAUSTED`。
（`401/403` 随鉴权暂缓，保留备用。）

---

## 6. 数据模型（7 表）

`workflow_run`（run + AgentFlow 快照）、`workflow_step`、`step_attempt`、`step_dependency`、
`workflow_event`（事件留存）、`outbox_message`（回流队列）、`processed_event`（入站去重）。

- 幂等键：`workflow_run.idempotency_key` 唯一、`step_attempt(step_id, attempt_no)` 唯一、
  `processed_event.event_id` 主键、`outbox_message.id`=outboxId；
- run/step/attempt 均带 `version`（乐观锁 CAS）；
- JSON 列（AgentFlow 快照、payload）MVP 用 `TEXT` 存储以兼容 openGauss 与 H2，应用层以 String + Jackson 存取。

初始化脚本：`src/main/resources/db/schema.sql`（完整建表，openGauss/PG 通用，含 `IF NOT EXISTS` 可重复执行）。
上线前手动在目标库执行（`gsql -f schema.sql` / `psql -f schema.sql`）；应用启动只做 JPA `validate`。

---

## 7. 包结构

| 包 | 职责 | 关键类 |
|---|---|---|
| `controller` | HTTP 端点 + 全局异常映射 | `RunController`、`StepActionController`、`AgentCoreEventController`、`ApiExceptionHandler` |
| `controller/dto` | 请求/响应体 | `CreateRunRequest`、`RunDetailResponse`、`DisplayEventMessage` … |
| `service` | 业务逻辑核心 | `RunService`、`AttemptResultHandler`、`SchedulingService`、`StepLauncher`、`EventIngestService`、`SuspendResumeService`、`RunCancellationService`、`ReconciliationService`、`OutboxService`、`RunEventBroadcaster`、`RunStateRecalculator`、`DownstreamTrigger`、`AgentFlowValidator`、`PromptRenderer`、`DagSupport`、`OrchestrationMetrics` |
| `service/statemachine` | 合法状态转换表 | `StateTransitions` |
| `scheduler` | 定时触发（薄壳） | `StepScheduler`、`Watchdog`、`OutboxWorker`、`ReconciliationRunner` |
| `client` | 出入站客户端 | `AgentCoreClient`(+mock/unconfigured)、`AgentManagementClient`(+mock/http) |
| `client/claudecode` | Claude Code SDK 接入 | `ClaudeCodeEventAdapter`、`ClaudeCodeStreamParser`、`AttemptContext`（`stream-json` → 内部事件） |
| `event` | 事件投递抽象 | `EventSink`、`IngestingEventSink`、`LoggingEventSink` |
| `model` | 数据结构 | `entity/*`(JPA)、`flow/*`(AgentFlow)、`event/*`(事件协议)、`claudecode/*`(SDK 消息)、状态枚举 |
| `repository` | Spring Data JPA | 7 个 Repository |
| `config` | 配置 | `OrchestrationProperties`、`ClaudeCodeAdapterProperties`、`OrchestrationConfig`、`MockAgentCoreConfig` |

---

## 8. 配置（`orchestration.*`）

```yaml
orchestration:
  max-retries: 2                 # step 自动重试上限(全局默认)
  step-timeout: 30m              # 硬超时(watchdog)
  heartbeat-timeout: 5m          # 心跳丢失阈值(watchdog)
  scheduler-poll-interval-ms: 2000
  watchdog:
    poll-interval-ms: 5000
  outbox:
    max-retries: 8               # 投递重试上限
    backoff-base-seconds: 2      # 指数退避基数(next = base * 2^retry)
    max-pending: 10000           # 积压阈值，超过则展示事件降采样
    poll-interval-ms: 1000
  # overrides:                   # per-executorType 覆盖(MVP 不支持 per-flow)
  #   CODEX: { max-retries: 1, step-timeout: 20m }

agent-management:                # 回流目标(非 mock profile)
  base-url: ${AM_BASE_URL:http://localhost:9090}
  events-path: /internal/agent-orchestration/events
```

---

## 9. 构建与运行

本机 Maven 默认 JDK 可能非 21，构建请显式指定 Java 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

mvn compile                                    # 编译
mvn test                                       # 全量测试(H2 + schema.sql + mock)
mvn spring-boot:run                            # 本地启动(需 openGauss + 已执行 schema.sql，端口 8080)
mvn spring-boot:run -Dspring-boot.run.profiles=mock   # mock 联调:mock Agent Core + mock AM
```

**Profile**：
- 默认：连 openGauss（`DB_URL`/`DB_USER`/`DB_PASSWORD` 可覆盖，表需预先用 `schema.sql` 建好），真实 client 占位（`UnconfiguredAgentCoreClient`）；
- `mock`：`MockAgentCoreClient`（可配 SUCCEED/FAIL/TIMEOUT，异步推事件）+ `MockAgentManagementClient`；
- `test`：H2 PostgreSQL 兼容模式，启动时跑 `db/schema.sql` 建表（顺带验证该 SQL 可执行），关闭后台调度轮询（由测试显式驱动）。

健康检查与指标：

```bash
curl http://localhost:8080/actuator/health      # -> {"status":"UP"}
curl http://localhost:8080/actuator/prometheus   # 指标:attempts.running / outbox.pending / attempt.failures
```

---

## 10. 测试

16 个测试类（单元 + 集成），覆盖：AgentFlow 校验、prompt 渲染、状态转换、启动链路、
状态机推进与自动重试、事件去重/归属/乱序合并、SSE 分发、outbox 投递/背压、
suspend-resume、watchdog/取消级联/reconcile。集成测试用 H2 + mock，异步路径用 awaitility 等待收敛。

---

## 11. 现状

MVP 编排主干已实现并测试通过（FE1–FE7）。**上线前**仍需在真实环境完成：真实 Agent Core /
Agent-Management 联调、用户级鉴权、openGauss 行锁与压测、灰度上线——详见 `operations.md`。

已知遗留优化项：StartAttempt 的 afterCommit 时机、展示事件回流的完整性策略、run 级全局事件序号。
