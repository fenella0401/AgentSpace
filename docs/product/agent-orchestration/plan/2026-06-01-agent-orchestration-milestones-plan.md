# Agent-Orchestration 里程碑开发计划

> 范围：Agent-Orchestration（自研工作流编排微服务）的 MVP 落地计划，里程碑级。配套设计见 `specs/2026-05-30-orchestrator-agentflow-workflow-template-design.md`。本计划只覆盖 Agent-Orchestration 自身；Agent-Management、Agent Core 由对应团队负责，本计划仅标注对接依赖。

## 总览

| 里程碑 | 目标 | 关键产出 |
|---|---|---|
| M0 | 工程脚手架与设计补全 | 仓库、CI、DDL、API/状态机详细约定 |
| M1 | 数据模型与持久化 | 建表、Repository、幂等基础设施 |
| M2 | 启动 run 链路打通 | POST /runs → 调度首 step → 调 Agent Core（可用 mock） |
| M3 | 状态机与调度器 | run/step/attempt 三级状态机、串行 DAG 调度、自动重试 |
| M4 | 事件流与实时输出 | 接收 Agent Core 事件、推进状态、只读直连事件流、回流 Agent-Management |
| M5 | suspend-resume 与对话续聊 | confirm / continue / retry、sessionRef 续聊 |
| M6 | 可靠性与上线 | watchdog、重启恢复、取消级联、可观测性、压测 |

技术栈：Java 21 + Spring Boot 3 + openGauss + Redis（可选）。

## M0：脚手架与设计补全

**目标**：把"后续待展开"中阻塞开发的实现细节先定下来，避免 M1 开工即卡。

任务：
- 初始化 Spring Boot 3 工程、分层结构、配置管理、本地 openGauss/Redis 环境；
- CI：编译、单测、lint、容器镜像构建；
- 补 **数据库 DDL**（run / step / attempt / event / outbox / 幂等去重表，字段级）；
- 补 **API 详细 schema**（7 个接口的出入参、错误码 404/409/422）；
- 补 **状态机完整转换规则**（合法/非法转换、并发边界）；
- 拍板第 11 章开放问题中阻塞实现的三项：#2 timeout/retry 配置形态、#3 workspace lease 校验时机、#4 取消竞态处理。

验收：DDL、API schema、状态机规则文档评审通过；工程能跑通空接口 + CI 绿。

依赖：与 Agent-Management 对齐 AgentFlow 字段最终版；与 Agent Core 对齐 StartAttempt/Cancel/Query 接口与事件 envelope。

## M1：数据模型与持久化

**目标**：openGauss 上的事实状态层就绪。

任务：
- 按 DDL 建表 + 迁移脚本（Flyway/Liquibase）；
- 实体与 Repository：WorkflowRun / WorkflowStep / StepAttempt / WorkflowEvent / Outbox；
- 幂等基础设施：idempotencyKey（run）、attemptId、eventId 去重、outboxId；
- AgentFlow 快照持久化（JSON 列存储 + schema 校验）。

验收：建表迁移可重复执行；Repository 单测覆盖 CRUD + 幂等冲突；AgentFlow 存取往返一致。

依赖：M0 DDL。

## M2：启动 run 链路打通

**目标**：`POST /runs` 能把一个 run 跑到"调起首个 step attempt"，端到端骨架通。

任务：
- 实现 `POST /runs`：校验 AgentFlow schema、幂等、持久化快照、初始化 run/step；
- DAG 解析 + 计算首批 ready step（MVP 串行：选一个）；
- prompt 简单变量替换（AgentFlow.variables + step 局部变量）；
- 调 Agent Core StartAttempt（**Agent Core 可先用 mock/stub**）；
- 创建 StepAttempt 记录、记录 runtimeAttemptRef。

验收：提交一个三步 AgentFlow，run 进入 RUNNING、首 step 进入 RUNNING、attempt 创建并调用了（mock）Agent Core；重复提交同 idempotencyKey 不重复创建。

依赖：M1；Agent Core StartAttempt 契约（可 mock）。

## M3：状态机与调度器

**目标**：三级状态机 + 串行 DAG 调度 + 自动重试，能把整条 DAG 跑完。

任务：
- run / step / attempt 状态机实现（按 M0 规则）；
- 调度器：DB polling + `SELECT ... FOR UPDATE SKIP LOCKED`，多副本不重复调度；
- attempt 成功 → step completed → 触发下游 ready → 调度下一 step（串行单飞）；
- 自动重试：attempt failed 且未超 maxRetries → 起新 attempt；超限 → step failed → run failed；
- timeout / maxRetries 读运行时配置（M0 拍板的形态）。

验收：三步链式 DAG 全部 completed → run completed；注入失败可触发自动重试到上限；多副本部署无重复调度（并发测试）。

依赖：M2；Agent Core 返回 attempt 终态（mock 可控制成功/失败）。

## M4：事件流与实时输出

**目标**：接收 Agent Core 执行事件，推进状态 + 实时输出给浏览器 + 回流 Agent-Management。

任务：
- `POST /internal/agent-core/events`：eventId 去重、归属校验、按类别分流；
- 控制类事件（attempt.started/heartbeat/result、runtime.*）推进状态机；
- 展示类事件：经 `GET (WS/SSE) /runs/{id}/events` 实时推给直连浏览器（用户级鉴权：验 JWT + run 授权范围）；
- outbox + worker：异步回流 Agent-Management（`POST /internal/agent-orchestration/events`），至少一次投递；
- `GET /runs/{runId}` 查询接口。

验收：mock Agent Core 推一串事件，状态正确推进；浏览器订阅 WS 能实时收到展示事件并按 sequence 有序；outbox 在 Agent-Management 不可用时重试不丢；JWT 越权访问被拒。

依赖：M3；Agent Core 事件 envelope；Agent-Management 接收端点；鉴权（JWT + run 授权范围）。

## M5：suspend-resume 与对话续聊

**目标**：人工确认与多轮续聊闭环。

任务：
- requiresConfirmation：attempt 成功 + 需确认 → step/run suspended、持久化 sessionRef；
- `confirm`：step completed → 推进下游；
- `continue`：起新 attempt（resumeFromSessionRef + feedback）→ 续聊 → 回 suspended；
- `retry`：failed step 手动重试；
- continue/retry 的 action 级幂等（防重复起 attempt）。

验收：带确认的 step 能 suspended；confirm 推进下游；continue 多轮续聊后再 confirm 收尾；并发 confirm/continue 不产生双 attempt。

依赖：M4；Agent Core 支持 resumeFromSessionRef 与回报 sessionRef。

## M6：可靠性与上线

**目标**：达到生产可用，过验收与压测。

任务：
- watchdog：step 硬超时、heartbeat lost（连续缺失）→ kill attempt → 判失败；
- 重启恢复（reconcile）：扫 in-flight attempt → QueryAttempt 对齐 → 续调度 / 续 outbox；
- run 取消级联：cancel → CANCELLING → 删 in-flight attempt → CANCELLED；与 confirm/continue 竞态处理；
- 可观测性：Micrometer + Prometheus 指标（调度延迟、running attempts、outbox 积压、失败原因）、结构化日志；
- 背压：outbox 上限/告警/降采样；
- 压测（设计目标：集群 ≥ 100 并发 running attempts）+ 灰度上线。

验收：进程重启后 in-flight run 不丢、能续跑；超时/僵死 attempt 被自愈；取消级联干净；压测达标；指标面板可用。

依赖：M5；Agent Core QueryAttempt / CancelAttempt；目标环境 openGauss/Redis/K8s。

## 关键路径与并行

```
M0 ─► M1 ─► M2 ─► M3 ─► M4 ─► M5 ─► M6
       │                  │
       └ DDL/实体           └ 鉴权、Agent-Management 对接可并行准备
```

- M0 的"设计补全"是硬前置，建议优先完成 DDL / API schema / 状态机规则；
- Agent Core、Agent-Management 的真实联调可在 M2 起用 mock，到 M4/M5 替换为真实服务；
- 鉴权（JWT + run 授权范围）在 M4 才硬需要，可在 M1-M3 期间并行设计。

## 风险

| 风险 | 缓解 |
|---|---|
| Agent Core / Agent-Management 契约未冻结 | M0 先对齐并 mock，接口变更走版本 |
| 状态机并发边界复杂 | M0 先写全转换规则 + M3 重点并发测试 |
| 续聊 workspace 互斥（开放问题 #3） | M0 拍板 lease 校验时机，M5 落地 |
| openGauss 行锁/SKIP LOCKED 行为差异 | M1 早验证锁语义，M3 并发测试兜底 |
| 第 11 章 7 项开放问题未解 | M0 拍板阻塞项，其余排入对应里程碑 |
