# Agent-Orchestration 特性（FE）分解

> 范围：把 Agent-Orchestration（自研工作流编排微服务）的能力面按**纵向特性（Feature, FE）**切分，控制在个位数（7 个）。与里程碑（M0–M6）是正交视角：FE 是能力切片，M 是时间阶段。
>
> 实现依据：详细设计 `../spec/2026-06-01-agent-orchestration-implementation-spec.md`（DDL / API / 状态机）+ 概要设计 `../spec/2026-05-30-orchestrator-agentflow-workflow-template-design.md`。里程碑与 task 拆分见 `2026-06-01-agent-orchestration-milestones-plan.md`、`2026-06-01-agent-orchestration-tasks-plan.md`。
>
> **鉴权 MVP 暂缓**（产品决策）：只读接口当前为无鉴权直连，下文不含鉴权特性；`team_id/user_id` 字段与 401/403 错误码已在 spec 保留备用，上线前须补齐。

## 前置 Enabler（非特性）

跨团队契约冻结与工程脚手架是 FE1–FE3 的硬前置，不单列为特性：

- **契约冻结**：与 Agent Core 对齐 StartAttempt / Cancel / Query + 事件 envelope；与 Agent-Management 对齐 AgentFlow 字段与回流端点（对应 task T0.3）。
- **Mock Agent Core**：可配置成功 / 失败 / 超时、可主动推事件，供 FE1 起联调（T0.4）。
- **工程脚手架 + CI**：Spring Boot 3 分层骨架、本地 openGauss/Redis、编译/单测/lint/镜像（T0.1、T0.2）。

## FE 一览

| FE | 特性 | 一句话范围 | 对应 spec | 对应 plan |
|---|---|---|---|---|
| FE1 | 执行编排基座 | 数据模型 + 幂等 + AgentFlow 快照 + `POST /runs` + DAG/ready + prompt 渲染 + StartAttempt | §1, §2.1, §6, §7.1, §7.3 | M1, M2 / T1.x, T2.x |
| FE2 | 状态机与调度引擎 | run/step/attempt 三级状态机 + 串行 DAG 调度 + 自动重试 | §3.1–3.4, §8.5, §8.6 | M3 / T3.x |
| FE3 | 事件接入与状态推进 | 入站事件去重 + 归属校验 + 控制类事件驱动状态机 + 乱序合并 | §2.8, §8.3, §8.4 | M4 / T4.1, T4.2 |
| FE4 | 实时流与状态查询 | `GET /runs/{id}` + `GET /runs/{id}/events`(WS/SSE) + sequence 有序 + 断线续传 | §2.6, §2.7, §9.6, §10.2 | M4 / T4.4, T4.5 |
| FE5 | 状态回流与背压 | outbox + worker + 至少一次投递 + 指数退避 + 展示事件降采样 | §1.7, §2.9, §9.8, §8.2, §11#7 | M4 T4.6, M6 T6.5 |
| FE6 | Suspend-Resume 与续聊 | confirm / continue / retry + sessionRef 续聊 + action 级幂等 + 取消竞态 | §2.3–2.5, §8.8, §10.3, §11#4,#5 | M5 / T5.x |
| FE7 | 可靠性与上线 | watchdog + 重启恢复 reconcile + 取消级联 + 可观测性 + 压测/灰度 | §3.5, §11#1,#6 | M6 / T6.1–T6.4, T6.6, T6.7 |

## 依赖顺序

```
enabler ─► FE1 ─► FE2 ─► FE3 ─► ┬─► FE4 ─┐
                                 └─► FE5 ─┴─► FE6 ─► FE7
```

- FE4（只读路径）与 FE5（回流写路径）可并行；
- FE6 依赖 FE3/FE4 的事件与状态查询能力；
- FE7 贯穿收尾，依赖 FE2 的状态机与 FE5 的 outbox。

## 特性详情

### FE1 执行编排基座

**范围**：openGauss 持久化数据模型（run / step / attempt / dependency / event / outbox / processed_event）；幂等基础设施（idempotencyKey、(step_id, attempt_no)、eventId、outboxId）；AgentFlow 快照存取 + schema 校验（DAG 无环/自环、edge 引用合法、step id 唯一）；`POST /runs`；DAG 解析与首批 ready 计算（MVP 串行选一）；prompt 变量替换（global + step variables + 上游 stepOutput）；调用（mock）Agent Core StartAttempt 并落 attempt。

**关键验收**：提交三步 AgentFlow，run→RUNNING、首 step→RUNNING、attempt 创建并调用了（mock）Agent Core；重复提交同 idempotencyKey 不重复创建；AgentFlow 存取往返一致。

### FE2 状态机与调度引擎

**范围**：run / step / attempt 三级状态机（CAS + 事务，非法转换拒绝）；调度器（DB polling + `SELECT ... FOR UPDATE SKIP LOCKED`，多副本不重复，串行单飞）；attempt 成功 → step completed → 触发下游 ready → 调度下一 step；自动重试（retry_count < maxRetries 起新 attempt，超限 step→FAILED→run）；timeout / maxRetries 读运行时配置。

**关键验收**：三步链式 DAG 全 completed → run completed；注入失败触发自动重试到上限；多副本部署无重复调度（并发测试）。

### FE3 事件接入与状态推进

**范围**：`POST /internal/agent-core/events`；eventId 去重（processed_event）；run/step/attempt 归属校验；控制类事件（attempt.started/heartbeat/result、runtime.*）驱动状态机；`attempt.result` 与 `runtime.*` 乱序到达的合并判定。

**关键验收**：mock Agent Core 推一串事件，attempt/step/run 状态正确推进；重复 eventId 幂等丢弃；result 先于 running 等乱序场景可正确判终态。

### FE4 实时流与状态查询

**范围**：`GET /runs/{id}` 状态快照（run + steps + 当前 attempt 摘要）；`GET /runs/{id}/events`（WS/SSE）实时推送展示类事件（thinking / message / tool_use / tool_result / stdout / stderr）；按 `sequenceNo` 有序；`?fromSequence=<n>` 断线重连续传。

**鉴权**：MVP 暂缓，当前无鉴权直连（见顶部说明）。

**关键验收**：浏览器订阅 WS/SSE 按 sequence 有序收到展示事件；断线 `fromSequence` 续传不漏、不重；`GET /runs/{id}` 返回各级状态、失败原因、最近 heartbeat、sessionRef。

### FE5 状态回流与背压

**范围**：outbox（状态变更与 outbox 写入同事务）；worker 轮询 PENDING 投递 Agent-Management（`POST /internal/agent-orchestration/events`），至少一次，指数退避 + `next_retry_at`；背压：outbox 上限/告警，积压超阈值降采样展示类事件，控制类事件优先；429 RESOURCE_EXHAUSTED。

**关键验收**：Agent-Management 不可用时重试不丢、按 outboxId/eventId 去重不重；积压超阈值时展示事件降采样而控制类事件不受影响。

### FE6 Suspend-Resume 与续聊

**范围**：requiresConfirmation → attempt 成功后 step/run SUSPENDED 并持久化 sessionRef；`confirm`（step→COMPLETED，推进下游，不起新 attempt）；`continue`（起 CONTINUE attempt，resumeFromSessionRef + feedback，跑完回 SUSPENDED）；`retry`（FAILED step 手动重试，可选复用 session）；action 级幂等（actionKey 或 attempt_no CAS）；与 run cancel 的竞态（CANCELLING 后 confirm/continue 一律 409）。

**关键验收**：带确认的 step 能 SUSPENDED；confirm 推进下游；多轮 continue 续聊后再 confirm 收尾；并发 confirm/continue 不产生双 attempt；cancelling 后写操作返回 409。

### FE7 可靠性与上线

**范围**：watchdog（step 硬超时、heartbeat 连续丢失 → CancelAttempt → attempt FAILED → step 重试/失败，CAS 仲裁）；重启恢复 reconcile（扫 in-flight attempt → QueryAttempt 对齐 → 续调度/续 outbox）；run 取消级联（cancel → CANCELLING → 删 in-flight attempt → CANCELLED，仅释放 workspace lease 引用不删卷）；可观测性（Micrometer + Prometheus：调度延迟、running attempts、outbox 积压、失败原因 + 结构化日志）；真实联调替换 mock；压测（≥100 并发 running attempts）+ 灰度。

**关键验收**：进程重启后 in-flight run 不丢、能续跑；超时/僵死 attempt 被自愈；取消级联干净且与 confirm/continue 竞态无双重终态；压测达标；指标面板可用。

## 与里程碑的对应

| FE | 约等于里程碑 |
|---|---|
| FE1 | M1 + M2 |
| FE2 | M3 |
| FE3 | M4（事件入站 + 状态推进部分） |
| FE4 | M4（只读查询 + 实时流部分） |
| FE5 | M4（outbox）+ M6（背压） |
| FE6 | M5 |
| FE7 | M6 |

## 审视结论（spec 与 plan 出入）

整理本分解时对照两份 spec 发现并已处理：

1. **鉴权口径**：spec 原文多处写"AO 自身做用户级鉴权"，与"MVP 暂缓"决策冲突——已在两份 spec 与两份 plan 统一标注为"规划 / MVP 暂缓"，字段与错误码保留备用（已落库提交）。
2. **T4.3 编号缺口**：tasks-plan M4 跳过 T4.3（"展示类事件→浏览器流"并入 T4.5），无实质遗漏，建议后续补号或加说明。
3. **开放问题 #6 无对应 task**：spec §11 #6（failed/cancelled run 的 workspace 与对话上下文清理）主责在 Agent-Management；AO 侧"释放 lease 引用"已归入 FE7 取消级联。
