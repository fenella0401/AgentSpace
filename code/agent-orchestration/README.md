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
| 调度 | **直驱为主 + DB polling 兜底**：step 完成即发事件直驱启动下游（afterCommit 异步）；轮询降级为 sweeper 兜底滞留 READY。多副本不重复靠 CAS（`@Version` 乐观锁）（`FOR UPDATE SKIP LOCKED` 为上线前优化，未落地） |
| 状态机 | 自研轻量三级状态机（非 Temporal/Camunda） |
| 入站事件 | HTTP callback（`POST /internal/agent-core/events`） |
| 状态/历史 | **单存储**：run/step/attempt/event 只存 orchestration（openGauss）；Agent-Management 经 `GET /runs/{id}` 主动查询，不回流 |
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
 (业务控制面)     ──GET /runs/{id}─▶ (本服务:编排/状态机)  ◀──执行事件──────   (运行时执行层)
                  （主动查询，不回流）
```

- **Agent-Management**：组装 AgentFlow、发起写操作、按需查询 run 状态/历史（不接收回流）；
- **Agent-Orchestration（本服务）**：执行 AgentFlow、推进状态机、实时分发、状态/事件单存储；
- **Agent Core**：把单个 StepAttempt 落到实际执行环境，回报执行事件。

> **单存储**：run/step/attempt 状态与展示事件只持久化在本服务（openGauss）。Agent-Management
> 需要结果时调 `GET /runs/{id}` 主动查询（查到终态即结束其工作流），历史展示事件读 `GET /runs/{id}/events/poll`；
> 本服务不主动回流、无 outbox，去除了双写与最终一致的复杂度。

### 2.2 内部分层

```
controller  ── HTTP 端点(对外只读 /runs/**、内部回调 /internal/**，按路径区分)
    │
service     ── 业务逻辑，按职责分子包:
    │             ├─ run/          run/step/attempt 生命周期(启动/调度/推进/重试/续聊/取消/对账/直驱 kicker)
    │             ├─ event/        入站事件接入 + 实时分发(SSE)
    │             ├─ support/      AgentFlow 编解码/校验 / DAG 工具 / prompt 渲染
    │             ├─ exception/    业务异常(由 ApiExceptionHandler 映射 HTTP 码)
    │             ├─ statemachine/ 合法状态转换表
    │             └─ OrchestrationMetrics(可观测性，顶层)
    ├── scheduler ── 定时触发:调度轮询 / watchdog / 重启 reconcile（详见 §2.5）
    ├── client    ── 调用 Agent Core(含 mock)；claudecode 解析 SDK stream-json
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
   去重(processed_event) → 归属校验 → 全量落库(workflow_event) → 按 category 分流:
     ├ control(attempt.started/heartbeat/result) ─┐
     ├ runtime(runtime.completed/failed/...)      ├─▶ 乱序合并判终态 ─▶ AttemptResultHandler
     └ display(thinking/message/tool_*/std*)      ─┘     │  ├─ 成功+无需确认 → step COMPLETED → 触发下游 ready
            │                                            │  ├─ 成功+requiresConfirmation → SUSPENDED
            └─▶ RunEventBroadcaster(实时推浏览器)        │  ├─ 失败+可重试 → AUTO_RETRY
                （展示事件已落 workflow_event，          │  └─ 失败+耗尽 → step FAILED → run FAILED
                 浏览器另可经 /events/poll 拉取）         └─▶ RunStateRecalculator(step 聚合驱动 run 状态)
```

`attempt.result`(executor 语义结果) 与 `runtime.completed/failed`(运行环境物理终态)**可乱序到达**，
先到者暂存于 `step_attempt` 的 `pending_*` / `runtime_terminal` 字段，两信号齐了再定终态（见 §8.4 设计）。

**单存储**：所有事件经 `EventIngestService` 落 `workflow_event`（本服务即事实源），实时推浏览器、
供 `/events/poll` 历史回放、供重启重放——不回流 Agent-Management，后者按需 `GET /runs/{id}` 查询。

**Claude Code 接入**：`client.claudecode` 把 SDK `stream-json` 输出（system/assistant/user/result）
翻译为内部统一事件——assistant 的 text/thinking/tool_use → display 类，user 的 tool_result → display 类，
`result` 行 → `attempt.result` + 合成的 `runtime.completed/failed`（直连作执行器+运行时时）。
eventId 由 attemptId+消息块确定性派生（SHA-256 截断），断线重读天然去重；thinking 取摘要、tool 输出限长（§8.4 约束）。

### 2.5 调度与自驱动（直驱快路径 + 轮询兜底）

本服务**以事件直驱为主、DB 轮询为兜底**（非事件总线 / Temporal，无外部调度器）。两条路径都把
**调度决策与状态推进逻辑放在 `service` 层**（便于测试直接驱动、绕开定时器），触发方式不同：

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ 直驱快路径(主)        step 终态/confirm → 发 RunProgressedEvent                  │
│ RunScheduleKicker     → afterCommit 异步重扫本 run READY → 启动(零延迟)          │
│  (service/run)        这是正常流程前进的根本；提交后才异步执行，避开事务内 HTTP   │
├──────────────────────────────────────────────────────────────────────────────┤
│ StepScheduler  每15s  兜底 sweeper：扫滞留 READY step → SchedulingService 启动   │
│  (轮询兜底)           覆盖崩溃/丢事件/多副本 failover 下没被直驱接走的 READY      │
│                       多副本：CAS(@Version) 乐观锁保证与直驱并发不重复调度        │
├──────────────────────────────────────────────────────────────────────────────┤
│ Watchdog        每5s  扫卡住的执行，兜底判定：                                   │
│  (兜底超时/卡死)      ├ RUNNING 心跳丢失(>heartbeat-timeout) → 判失败            │
│                       ├ RUNNING 硬超时(>step-timeout)        → 判失败            │
│                       └ STARTING 卡死(>start-timeout)        → reconcile 对齐    │
├──────────────────────────────────────────────────────────────────────────────┤
│ ReconciliationRunner 启动时  对所有 in-flight attempt 跑一次 reconcile          │
│  (重启恢复，非定时)          查 Agent Core 实况对齐状态；@Profile("!test")       │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **直驱让流程零延迟前进，轮询 sweeper 兜底滞留**，**Watchdog** 兜底卡死，**ReconciliationRunner** 管重启恢复
  ——合起来构成无外部调度器的「自驱动」能力（单存储，无回流 worker）；
- **直驱快路径**（`RunScheduleKicker`）：推进 step 状态的事务（`AttemptResultHandler` 三个终态处理、
  `SuspendResumeService.confirm`）在结束时 `publishEvent(RunProgressedEvent)`。kicker 以
  `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 消费——**提交后**才在**独立线程、新事务**里
  重扫本 run 的 READY（按 order_index 升序）并 `tryLaunchReadyStep`。两点缺一不可：① afterCommit 保证读到已落库的
  READY；② 异步新事务保证 `StepLauncher` 对 Agent Core 的**同步 HTTP 调用不占用任何既有 DB 事务**（见 §11#1）。
  串行单飞下启到第一个成功即止，后续 step 由其完成时再次发出的事件接力；
- **为什么轮询不能去掉（它的唯一职责是 liveness 兜底，不是发现就绪）**：直驱是「快」而非「可靠」保证。
  发布事件的副本在提交后、kicker 执行前崩溃，或 kicker 抛异常、或多副本下事件回调落在已挂副本——这些场景下
  该跑的直驱代码根本没机会跑，READY step 会滞留。轮询保证「**不管怎么变成 READY，最终一定有人尝试启动**」，
  与进程内触发解耦。故间隔从直驱前的 2s 放宽到 **15s**：它已从「主驱动」降级为低频 sweeper；
- **READY step 的并发抢占靠乐观锁 CAS，不是数据库行锁**：直驱与轮询、多副本可能同时尝试启动同一 step，
  最终由 `SchedulingService.tryLaunchReadyStep` 三关仲裁——① 状态须仍为 READY（已被抢走则跳过）；
  ② 串行单飞：同 run 已有 RUNNING step 则不启动；③ `StepLauncher` 把 step 翻 RUNNING 时 `save` 走 `@Version`，
  SQL 为 `UPDATE ... SET status='RUNNING', version=version+1 WHERE id=? AND version=?`。
  ①② 是无锁 SELECT，可能多方同时通过（TOCTOU）；真正分胜负的是 ③——并发写同一行只有一个 CAS 命中，
  另一个影响 0 行抛 `OptimisticLockException` 回滚重试。另有 `step_attempt(step_id, attempt_no)` 唯一约束兜底；
- 状态写入全程 **CAS（`@Version`）+ 事务**：watchdog 判失败复用 `AttemptResultHandler.onAttemptFailed`，
  对已终态 attempt 幂等忽略，与正常 `attempt.result` 事件并发安全；
- 轮询间隔均可配（见 §8）；测试 profile 下轮询间隔设为极大值，链条推进改由直驱 kicker 自驱动（集成测试不再手动驱动调度）。

> `SELECT ... FOR UPDATE SKIP LOCKED`（悲观跳锁，从源头避免抢同一行的无效计算）为上线前的性能优化，
> **尚未落地**；当前 CAS 已保证直驱 + 多副本调度的正确性，见 `operations.md`。

### 2.6 任务全生命周期（初始化 → 启动 → 执行 → 结束）

把 §2.3~§2.5 串起来，跟随**单个 step / attempt** 走完一遍。竖线左侧是触发者，`▸` 为状态翻转：

```
① 初始化           POST /runs ─▶ RunService（同事务落库）
   (RunService)     ▸ run PENDING、step PENDING、依赖边落库
                    ▸ 入度0 的 step ▸ READY；run ▸ RUNNING（首个 step 同步走②③）

② 调度抢占         直驱：RunProgressedEvent ─(afterCommit 异步)─▶ RunScheduleKicker 重扫本 run READY
   (kicker/svc)     兜底：StepScheduler 每 15s sweeper 扫滞留 READY ──┐
                    两路均 ─▶ SchedulingService.tryLaunchReadyStep ───┘
                    关卡：step 仍 READY？同 run 无 RUNNING（串行单飞）？
                    ▸ CAS 翻 step READY→RUNNING（@Version；直驱/轮询/多副本并发只一个赢）

③ 启动 attempt     StepLauncher.launch
   (StepLauncher)   渲染 prompt（失败→attempt/step FAILED，不调 Agent Core）
                    ▸ 建 StepAttempt PENDING → AgentCoreClient.startAttempt → ▸ STARTING
                    ⚠ startAttempt 中断：保持 STARTING，由⑤watchdog/reconcile 兜底

④ 执行中           Agent Core / Claude Code SDK ──事件──▶ /internal/agent-core/events
   (EventIngest)    去重→归属校验→全量落库(workflow_event)→分流：
                    ├ attempt.started/runtime.running ▸ attempt STARTING→RUNNING
                    ├ display 事件 → 浏览器（轮询 /events/poll 或 SSE，本地表即事实源）
                    └ heartbeat 刷新 last_heartbeat_at（⑤watchdog 据此判活）

⑤ 终态判定         attempt.result + runtime.*（可乱序，pending_* 暂存，齐了才定）
   (ResultHandler)  ┌ 成功 → ▸ attempt SUCCEEDED
                    │        ├ 无需确认 → ▸ step COMPLETED → 触发下游 ▸ READY + 发 RunProgressedEvent（直驱回②）
                    │        └ requiresConfirmation → ▸ step SUSPENDED（等 confirm/continue）
                    ├ 失败 → ▸ attempt FAILED
                    │        ├ 未超 maxRetries → step ▸ READY 起 AUTO_RETRY attempt（同步走③，占单飞槽）
                    │        └ 超限 → ▸ step FAILED
                    └ 兜底：Watchdog 每 5s 扫超时/心跳丢失 → CancelAttempt + 判 FAILED
                            STARTING 卡死 → reconcile 查 Agent Core 实况对齐

⑥ run 收敛         每次终态变更 ─▶ RunStateRecalculator（step 聚合驱动 run）
   (Recalculator)   全 COMPLETED ▸ run COMPLETED ｜ 有 FAILED 且无可推进 ▸ FAILED
                    ｜ 任一 SUSPENDED ▸ run SUSPENDED ｜ cancel ▸ CANCELLING→CANCELLED
                    ▸ run 终态只落本地；Agent-Management 经 GET /runs/{id} 主动查询（不回流）
```

要点：**②③ 与 ⑤ 的下游/重试构成主循环**——一个 step 终态后下游变 READY，由 ⑤ 发出的 `RunProgressedEvent`
**直驱**接力启动下一个（零延迟），§2.5 的轮询 sweeper 仅兜底滞留，无外部编排器。手动干预（confirm / continue /
retry / cancel）从 ⑤ 的 SUSPENDED/FAILED 态切入：confirm 同样发事件直驱回②，continue/retry 自身同步走③、重新汇入主循环。

### 2.7 阶段 → 代码文件对照

§2.6 各阶段（含手动干预）对应的入口类与方法（包前缀 `com.agentspace.orchestration`）：

| 阶段 | 触发入口 | 核心处理 | 文件:方法 |
|---|---|---|---|
| ① 初始化 | `POST /runs` | 校验 → 落库 → 算 ready → 启首个 | `controller/RunController.createRun` → `service/run/RunService.startRun` |
| ① 校验 | — | DAG 无环 / edge 引用 / id 唯一 | `service/support/AgentFlowValidator.validate`、`service/support/DagSupport` |
| ① prompt 渲染 | — | 变量替换 | `service/support/PromptRenderer.render` |
| ② 调度抢占(直驱) | step 终态/confirm 发事件 | afterCommit 异步重扫本 run READY → 启动 | `service/run/RunScheduleKicker.onRunProgressed` → `service/run/SchedulingService.tryLaunchReadyStep` |
| ② 调度抢占(兜底) | 每 15s 轮询 | sweeper 扫滞留 READY → 单飞判定 → CAS 启动 | `scheduler/StepScheduler.pollAndSchedule` → `service/run/SchedulingService.tryLaunchReadyStep` |
| ③ 启动 attempt | — | 建 attempt → 调 Agent Core | `service/run/StepLauncher.launch` → `client/AgentCoreClient.startAttempt` |
| ④ 事件入站 | `POST /internal/agent-core/events` | 去重 / 归属 / 落库 / 分流 | `controller/AgentCoreEventController.receiveEvent` → `service/event/EventIngestService.ingest` |
| ④ SDK 解析 | （Agent Core 侧）| stream-json → 内部事件 | `client/claudecode/ClaudeCodeStreamParser` + `ClaudeCodeEventAdapter` |
| ④ 实时下发 | — | 推 SSE / 供轮询拉取 | `service/event/RunEventBroadcaster.publish`、`RunService.pollDisplayEvents` |
| ⑤ 终态判定 | — | result+runtime 乱序合并 → 成功/重试/失败 | `service/event/EventIngestService.tryFinalize` → `service/run/AttemptResultHandler.onAttempt{Succeeded,Failed,Cancelled}` |
| ⑤ 下游触发 | — | 完成后置下游 READY | `service/run/DownstreamTrigger.triggerDownstream` |
| ⑤ 兜底 | 每 5s 扫描 | 超时/心跳丢失判失败、STARTING 卡死对齐 | `scheduler/Watchdog.scan` → `service/run/ReconciliationService.reconcileOne` |
| ⑥ run 收敛 | — | step 聚合驱动 run 状态 | `service/run/RunStateRecalculator.recalculate` |
| 手动 confirm/continue/retry | `POST /runs/{id}/steps/{sid}/{action}` | 推进/续聊/重试 | `controller/StepActionController` → `service/run/SuspendResumeService.{confirm,continueStep,retry}` |
| 手动 cancel | `POST /runs/{id}/cancel` | 级联删 in-flight attempt | `controller/StepActionController.cancel` → `service/run/RunCancellationService.cancel` |
| 重启恢复 | 启动时 | 对 in-flight attempt 跑一次对齐 | `scheduler/ReconciliationRunner.run` → `service/run/ReconciliationService.reconcileInFlight` |
| 状态机仲裁 | — | 合法转换表（所有写入校验） | `service/statemachine/StateTransitions` |

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
| GET | `/runs/{runId}` | 查询 run / step 状态快照（Agent-Management 据此判终态） |
| GET (SSE) | `/runs/{runId}/events` | 展示类事件实时流（`?fromSequence=` 断线续传） |
| GET | `/runs/{runId}/events/poll` | 展示类事件增量轮询（`?fromSequence=&limit=`，建议 5~10s 间隔；无长连接、兼容多副本） |
| POST | `/internal/agent-core/events` | Agent Core 上报执行事件（内部） |

错误码：`404 NOT_FOUND` / `409 CONFLICT` / `422 VALIDATION_ERROR` / `429 RESOURCE_EXHAUSTED`。
（`401/403` 随鉴权暂缓，保留备用。）

---

## 6. 数据模型（6 表）

`workflow_run`（run + AgentFlow 快照）、`workflow_step`、`step_attempt`、`step_dependency`、
`workflow_event`（事件留存，单存储事实源）、`processed_event`（入站去重）。

- 幂等键：`workflow_run.idempotency_key` 唯一、`step_attempt(step_id, attempt_no)` 唯一、
  `processed_event.event_id` 主键；
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
| `service/run` | run/step/attempt 生命周期 | `RunService`、`SchedulingService`、`StepLauncher`、`AttemptResultHandler`、`DownstreamTrigger`、`RunScheduleKicker`(直驱)、`RunStateRecalculator`、`SuspendResumeService`、`RunCancellationService`、`ReconciliationService` |
| `service/event` | 事件接入与实时分发 | `EventIngestService`、`RunEventBroadcaster` |
| `service/support` | 无状态工具 | `AgentFlowCodec`、`AgentFlowValidator`、`DagSupport`、`PromptRenderer` |
| `service/exception` | 业务异常 | `IdempotencyConflictException`、`PromptRenderException`、`StepAction*Exception` … |
| `service/statemachine` | 合法状态转换表 | `StateTransitions` |
| `service`（顶层） | 可观测性 | `OrchestrationMetrics` |
| `scheduler` | 定时触发（薄壳） | `StepScheduler`、`Watchdog`、`ReconciliationRunner` |
| `client` | Agent Core 客户端 | `AgentCoreClient`(+mock/unconfigured) |
| `client/claudecode` | Claude Code SDK 接入 | `ClaudeCodeEventAdapter`、`ClaudeCodeStreamParser`、`AttemptContext`（`stream-json` → 内部事件） |
| `event` | 事件投递抽象 | `EventSink`、`IngestingEventSink`、`LoggingEventSink` |
| `model` | 数据结构 | `entity/*`(JPA)、`flow/*`(AgentFlow)、`event/*`(事件协议)、`claudecode/*`(SDK 消息)、状态枚举 |
| `repository` | Spring Data JPA | 6 个 Repository |
| `config` | 配置 | `OrchestrationProperties`、`ClaudeCodeAdapterProperties`、`OrchestrationConfig`、`MockAgentCoreConfig` |

---

## 8. 配置（`orchestration.*`）

```yaml
orchestration:
  max-retries: 2                 # step 自动重试上限(全局默认)
  step-timeout: 30m              # 硬超时(watchdog)
  heartbeat-timeout: 5m          # 心跳丢失阈值(watchdog)
  start-timeout: 2m              # STARTING 卡死兜底
  scheduler-poll-interval-ms: 15000   # 轮询已降级为兜底 sweeper（正常推进由直驱 kicker），故放宽到 15s
  watchdog:
    poll-interval-ms: 5000
  # overrides:                   # per-executorType 覆盖(MVP 不支持 per-flow)
  #   CODEX: { max-retries: 1, step-timeout: 20m }

# Claude Code SDK 消息适配（脱敏/限长与 runtime 终态合成）
claude-code:
  adapter:
    thinking-summary-max-chars: 2000
    tool-output-max-chars: 8000
    synthesize-runtime-terminal: true
```

> **单存储**：无 outbox、无 `agent-management` 回流配置——状态/历史只存本服务，
> Agent-Management 经 `GET /runs/{id}` 与 `/events/poll` 主动查询。

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
- `mock`：`MockAgentCoreClient`（可配 SUCCEED/FAIL/TIMEOUT，异步推事件）；
- `test`：H2 PostgreSQL 兼容模式，启动时跑 `db/schema.sql` 建表（顺带验证该 SQL 可执行），关闭后台调度轮询（由测试显式驱动）。

健康检查与指标：

```bash
curl http://localhost:8080/actuator/health      # -> {"status":"UP"}
curl http://localhost:8080/actuator/prometheus   # 指标:attempts.running / attempt.failures
```

---

## 10. 测试

20 个测试类（单元 + 集成），覆盖：AgentFlow 校验、prompt 渲染、状态转换、启动链路、
状态机推进与自动重试、事件去重/归属/乱序合并、SSE 分发与轮询、Claude Code SDK 解析与接入、
suspend-resume、watchdog/取消级联/reconcile。集成测试用 H2 + mock，异步路径用 awaitility 等待收敛。

---

## 11. 现状

MVP 编排主干已实现并测试通过（FE1–FE7）。**上线前**仍需在真实环境完成：真实 Agent Core 联调、
用户级鉴权、openGauss 行锁与压测、灰度上线——详见 `operations.md`。

已知遗留优化项：StartAttempt 的 afterCommit 时机、run 级全局事件序号、`FOR UPDATE SKIP LOCKED` 调度优化。
