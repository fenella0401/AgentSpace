# Agent-Orchestration 运维与上线 Checklist

> 本文记录 Agent-Orchestration 上线前需要在真实环境完成的事项，以及 MVP 阶段以
> mock / 单机验证替代、留待真实环境补齐的项。代码侧已完成的能力见各 FE 提交。

## 已实现并验证（单机 + H2 + mock）

- FE1 执行编排基座：数据模型 / Flyway 迁移 / 幂等 / `POST /runs` / DAG ready / prompt 渲染 / StartAttempt
- FE2 状态机与调度：run/step/attempt 三级状态机（CAS）/ 串行调度 / 自动重试
- FE3 事件接入：`POST /internal/agent-core/events` / eventId 去重 / 归属校验 / 乱序合并
- FE4 实时流：`GET /runs/{id}` / `GET /runs/{id}/events`（SSE）/ fromSequence 续传
- FE5 回流背压：outbox 同事务写 / worker 指数退避投递 / 背压降采样
- FE6 suspend-resume：confirm / continue / retry / cancel / 竞态拒绝
- FE7 可靠性：watchdog（超时/心跳丢失）/ cancel 级联 / reconcile / Prometheus 指标

## 上线前必须在真实环境完成

### 1. 数据库（openGauss）
- [ ] 在真实 openGauss 上执行 Flyway 迁移（当前迁移脚本以 TEXT 存 JSON，兼容 openGauss 与 H2）
- [ ] 评估 jsonb 原生类型 + GIN 索引需求（如需按 payload 检索），以 PG 专属迁移升级 `agent_flow` / `payload` 列
- [ ] 验证 `SELECT ... FOR UPDATE SKIP LOCKED` 在 openGauss 兼容模式下的行锁语义（当前调度以 CAS 保证正确性，SKIP LOCKED 为性能优化）
- [ ] 多副本部署下的调度并发测试（CAS 不重复调度）

### 2. 真实 Agent Core / Agent-Management 联调（替换 mock）
- [ ] 冻结并对齐 StartAttempt / CancelAttempt / QueryAttempt 接口与事件 envelope（T0.3）
- [ ] 用真实 Agent Core 替换 `MockAgentCoreClient`（非 mock profile 下实现 HTTP `AgentCoreClient`）
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

## 已知遗留优化项（非阻塞）

- **StartAttempt 的事务时机**：当前 `StepLauncher` 在 run/step 事务内调 Agent Core StartAttempt。
  更稳健的做法是注册 `afterCommit` 钩子在外层事务提交后再调，避免本地回滚产生 Agent Core 孤儿
  attempt。mock 当前以小延迟（`mock.agent-core.start-delay-ms`）规避事务可见性竞态；真实接入时
  改为 afterCommit。
- **outbox display 事件回流**：当前展示事件回流受背压降采样；如需保证历史回放完整性，
  可考虑展示事件独立队列或分级保留策略。
- **run 级全局事件序号**：`workflow_event.sequence_no` 为 attempt 内序号，跨 attempt 的 SSE
  断线续传以 eventId 去重兜底；如需严格全局有序，引入 run 级单调序号。
