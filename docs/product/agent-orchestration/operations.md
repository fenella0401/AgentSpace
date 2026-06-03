# Agent-Orchestration 运维与上线 Checklist

> 本文记录 Agent-Orchestration 上线前需要在真实环境完成的事项，以及 MVP 阶段以
> mock / 单机验证替代、留待真实环境补齐的项。代码侧已完成的能力见各 FE 提交。

## 已实现并验证（单机 + H2 + mock）

- FE1 执行编排基座：数据模型 / 初始化 SQL（`db/schema.sql`）/ 幂等 / `POST /runs` / DAG ready / prompt 渲染 / StartAttempt
- FE2 状态机与调度：run/step/attempt 三级状态机（CAS）/ 串行调度 / 自动重试
- FE3 事件接入：`POST /internal/agent-core/events` / eventId 去重 / 归属校验 / 乱序合并
- FE4 实时流：`GET /runs/{id}` / `GET /runs/{id}/events`（SSE）/ fromSequence 续传
- FE5 回流背压：outbox 同事务写 / worker 指数退避投递 / 背压降采样
- FE6 suspend-resume：confirm / continue / retry / cancel / 竞态拒绝
- FE7 可靠性：watchdog（超时/心跳丢失）/ cancel 级联 / reconcile / Prometheus 指标
- Claude Code 接入：SDK `stream-json` 消息（system/assistant/user/result）→ 内部统一事件适配
  （`client.claudecode`），脱敏/限长、runtime 终态合成、确定性 eventId 去重；含端到端联调测试

## 上线前必须在真实环境完成

### 1. 数据库（openGauss）
- 应用运行时数据源用 openGauss 官方驱动（`org.opengauss:opengauss-jdbc`，`jdbc:opengauss://`）。
- 表结构手动初始化：上线前在目标库执行 `code/agent-orchestration/src/main/resources/db/schema.sql`
  （`gsql -f schema.sql`；openGauss/PG 通用，含 `IF NOT EXISTS` 可重复执行）。应用启动只做 JPA `validate`，不自动建表。
- [ ] 在真实 openGauss 上执行 `schema.sql` 建表，并启动应用确认 JPA `validate` 通过（本机无实例，仅 H2 PG 兼容模式 + 编译/配置验证过）
- [ ] 评估 jsonb 原生类型 + GIN 索引需求（如需按 payload 检索），以 PG 专属脚本升级 `agent_flow` / `payload` 列
- [ ] 验证 `SELECT ... FOR UPDATE SKIP LOCKED` 在 openGauss 兼容模式下的行锁语义（当前调度以 CAS 保证正确性，SKIP LOCKED 为性能优化）
- [ ] 多副本部署下的调度并发测试（CAS 不重复调度）

### 2. 真实 Agent Core / Agent-Management 联调（替换 mock）
- [ ] 冻结并对齐 StartAttempt / CancelAttempt / QueryAttempt 接口与事件 envelope（T0.3）
- [ ] 用真实 Agent Core 替换 `MockAgentCoreClient`（非 mock profile 下实现 HTTP `AgentCoreClient`）
- [x] 入站消息解析：Claude Code SDK `stream-json` → 内部事件已实现（`client.claudecode`，默认按 SDK 格式解析）
- [ ] 把 SDK 进程 stdout（或 Agent Core 转发的事件流）接到 `ClaudeCodeStreamParser.parseAndDispatch`，
      每个 attempt 提供 `AttemptContext`（runId/stepId/attemptId）；OPENCODE / CODEX 执行器另补适配器
- [ ] 配置 `agent-management.base-url`，验证 `HttpAgentManagementClient` 回流投递与去重
- [ ] 端到端跑通：issue → run → attempt → 事件流 → 审查 → 结果

### 3. 鉴权（MVP 暂缓，上线前必须补齐）
- [ ] 实现用户级鉴权：验 JWT + 比对 run 的 `team_id/user_id` 授权范围
- [ ] 启用 `401/403`（字段与错误码已在 spec/代码保留）
- [ ] 当前只读接口（`GET /runs/{id}`、`/events`）为无鉴权直连，存在越权读取风险，须在公网暴露前关闭

### 4. 压测与容量（设计目标：集群 ≥ 100 并发 running attempts）
- [ ] 压测调度延迟、事件吞吐、outbox 投递速率
- [ ] 校准 `orchestration.outbox.max-pending` 背压阈值与展示事件降采样策略
- [ ] 校准 watchdog 阈值（`step-timeout` / `heartbeat-timeout`，支持 per-executorType 覆盖）

### 5. 灰度上线
- [ ] 功能开关后发布，单项目灰度验证
- [ ] 指标面板（Prometheus + Grafana）：`orchestration.attempts.running`、`orchestration.outbox.pending`、`orchestration.attempt.failures`
- [ ] 告警：队列积压、调度器/runtime 无心跳、连接器错误率、outbox 投递失败、成本异常

## Agent Core API 中断的恢复机制

升级 / 网络导致 Agent Core API 中断时，按中断位置分别由以下机制兜底（已实现 + 测试）：

- **StartAttempt 调用中断**：`StepLauncher` 捕获异常，attempt 保持 `STARTING`（不回滚、不退回 READY、
  不重复起 attempt），由后续 reconcile / watchdog 对齐。
- **STARTING 卡死**：`Watchdog` 扫 `created_at` 超过 `orchestration.start-timeout`（默认 2m）的 STARTING
  attempt，触发 `ReconciliationService.reconcileOne`。
- **reconcile 据 Agent Core 实况判定**（`QueryAttemptResponse.found`）：
  - 未找到（StartAttempt 未送达）→ `RUNTIME_CREATE_FAILED` → step 自动重试（起全新 attemptId，无重复执行）；
  - 已在跑但本地 STARTING（响应丢失）→ 本地对齐成 RUNNING，补 `runtimeAttemptRef`；
  - 终态 → 推进成功 / 失败。
- **本服务重启**：`ReconciliationRunner` 启动时对所有 in-flight attempt 跑一次 reconcile。
- **执行中事件丢失**：watchdog 心跳 / 硬超时兜底；回流 AM 由 outbox 至少一次。

> 依赖：真实 Agent Core 必须保证 **StartAttempt 幂等**（同 attemptId 重复调返回同一 RuntimeAttempt）
> 与 **QueryAttempt 可按 attemptId 查 found/状态**。见下方真实 client 要求。

## 已知遗留优化项（非阻塞）

- **真实 HTTP AgentCoreClient**：尚未实现（当前 `UnconfiguredAgentCoreClient` 占位 + mock）。真实实现须带
  连接 / 读超时、有限重试，并依赖 StartAttempt 幂等键（用 attemptId）。StartAttempt 调用时机可进一步优化为
  外层事务 `afterCommit` 后再调，避免本地回滚产生 Agent Core 孤儿 attempt（当前已通过"中断保持 STARTING +
  reconcile 对齐"覆盖主要风险）。mock 以小延迟（`mock.agent-core.start-delay-ms`）规避事务可见性竞态。
- **outbox display 事件回流**：当前展示事件回流受背压降采样；如需保证历史回放完整性，
  可考虑展示事件独立队列或分级保留策略。
- **run 级全局事件序号**：`workflow_event.sequence_no` 为 attempt 内序号，跨 attempt 的 SSE
  断线续传以 eventId 去重兜底；如需严格全局有序，引入 run 级单调序号。
