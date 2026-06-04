# Agent-Orchestration Task 级开发计划

> 把里程碑（`2026-06-01-agent-orchestration-milestones-plan.md`）展开成可认领的 task。实现依据：详细设计 `../spec/2026-06-01-agent-orchestration-implementation-spec.md`（DDL / API / 状态机）+ 概要设计 `../spec/2026-05-30-orchestrator-agentflow-workflow-template-design.md`。
>
> 预估单位：人日（熟悉 Spring Boot + 该设计的工程师）。Agent Core / Agent-Management 未就绪处用 mock。

## M0 脚手架与对齐

| ID | Task | 产出 / 验收 | 依赖 | 预估 |
|---|---|---|---|---|
| T0.1 | 初始化 Spring Boot 3 工程 | 分层骨架、配置、健康检查、本地 openGauss+Redis docker-compose | — | 1 |
| T0.2 | CI 流水线 | 编译/单测/lint/镜像构建，PR 触发 | T0.1 | 1 |
| T0.3 | 冻结对接契约 | 与 Agent Core 对齐 StartAttempt/Cancel/Query+事件 envelope；与 Agent-Management 对齐 AgentFlow 字段与回流端点；产出接口约定文档 | — | 2 |
| T0.4 | 定义 mock Agent Core | 可配置返回成功/失败/超时、可主动推事件，供后续里程碑联调 | T0.3 | 2 |

## M1 数据模型与持久化

| ID | Task | 产出 / 验收 | 依赖 | 预估 |
|---|---|---|---|---|
| T1.1 | 建表迁移脚本 | Flyway/Liquibase 落 §1 全部表 + 索引；可重复执行 | T0.1 | 1.5 |
| T1.2 | 实体与 Repository | run/step/attempt/dependency/event/outbox/processed_event 的实体 + Repository | T1.1 | 2 |
| T1.3 | 乐观锁 + CAS 封装 | version 字段更新封装，冲突重试工具 | T1.2 | 1 |
| T1.4 | 幂等基础设施 | idempotencyKey/attempt(step_id,no)/eventId/outboxId 唯一约束 + 应用层处理 | T1.2 | 1.5 |
| T1.5 | AgentFlow 快照存取 | jsonb 存取 + schema 校验器（字段、DAG 无环/自环、edge 引用合法、step id 唯一） | T1.2 | 2 |

## M2 启动 run 链路

| ID | Task | 产出 / 验收 | 依赖 | 预估 |
|---|---|---|---|---|
| T2.1 | POST /runs 接口 | Idempotency-Key 幂等、422 校验、落库快照、建 step/dependency | T1.4 T1.5 | 2 |
| T2.2 | DAG 解析 + ready 计算 | 从 edges 算初始 ready；多 ready 按 order_index 选一（串行） | T1.2 | 1.5 |
| T2.3 | prompt 渲染器 | 简单变量替换（global+step variables+上游 stepOutput）；缺变量→PROMPT_RENDER_ERROR | T1.2 | 1.5 |
| T2.4 | StartAttempt 客户端 | 调（mock）Agent Core，组装入参（prompt/agent/skill/mcp/kb/workspace/repo/cred refs/stream keys），落 attempt | T0.4 T2.3 | 2 |

## M3 状态机与调度器

| ID | Task | 产出 / 验收 | 依赖 | 预估 |
|---|---|---|---|---|
| T3.1 | attempt 状态机 | §3.1 全转换 + CAS + 终态幂等 + result/runtime 乱序合并 | T1.3 | 2.5 |
| T3.2 | step 状态机 | §3.2 全转换，含 ready 触发、自动重试、suspended | T3.1 | 2.5 |
| T3.3 | run 状态机（聚合） | §3.3 由 step 聚合驱动 run 状态 | T3.2 | 1.5 |
| T3.4 | 调度器 | DB polling + FOR UPDATE SKIP LOCKED，串行单飞，多副本不重复（并发测试） | T3.2 | 3 |
| T3.5 | 自动重试 | retry_count<maxRetries 起新 attempt；超限 step→FAILED→run；读运行时配置 | T3.2 | 1.5 |
| T3.6 | 链路集成测试 | 三步 DAG 全 completed；注入失败触发重试到上限 | T3.1-T3.5 | 2 |

## M4 事件流与实时输出

| ID | Task | 产出 / 验收 | 依赖 | 预估 |
|---|---|---|---|---|
| T4.1 | POST /internal/agent-core/events | eventId 去重、归属校验、按 category 分流 | T1.4 | 1.5 |
| T4.2 | 控制类事件→状态机 | 驱动 attempt/step/run 推进（事务+CAS） | T4.1 T3.3 | 2 |
| T4.4 | GET /runs/{id} 查询 | §2.6 响应；404（鉴权 MVP 暂缓，无鉴权直连） | T2.1 | 1 |
| T4.5 | GET /runs/{id}/events 实时流 | WS/SSE，展示类事件按 sequence 有序，fromSequence 续传（鉴权 MVP 暂缓，无鉴权直连） | T2.1 | 3 |
| T4.6 | outbox + worker | 状态变更同事务写 outbox；worker 投递 Agent-Management，指数退避重试，至少一次 | T4.2 | 2.5 |

## M5 suspend-resume 与续聊

| ID | Task | 产出 / 验收 | 依赖 | 预估 |
|---|---|---|---|---|
| T5.1 | suspended 流转 | attempt 成功+requiresConfirmation→step/run suspended，持久化 session_ref | T3.2 | 1.5 |
| T5.2 | POST .../confirm | §2.3；409 非 suspended；推进下游 | T5.1 | 1.5 |
| T5.3 | POST .../continue | §2.4；起 CONTINUE attempt（resume+feedback）；回 suspended | T5.1 T2.4 | 2 |
| T5.4 | POST .../retry | §2.5；failed step 手动重试 | T3.5 | 1.5 |
| T5.5 | action 级幂等 | continue/retry actionKey 或 attempt_no CAS，并发不双起 | T1.4 | 1.5 |
| T5.6 | 续聊集成测试 | 确认→续聊多轮→再确认收尾；并发 confirm/continue 不双 attempt | T5.1-T5.5 | 2 |

## M6 可靠性与上线

| ID | Task | 产出 / 验收 | 依赖 | 预估 |
|---|---|---|---|---|
| T6.1 | watchdog | §3.5 超时/心跳丢失扫描→kill→失败；CAS 仲裁 | T3.1 | 2.5 |
| T6.2 | 重启恢复 reconcile | 启动扫 in-flight attempt→QueryAttempt 对齐→续调度/续 outbox | T3.4 T4.6 | 3 |
| T6.3 | run 取消级联 | §2.2 cancel→CANCELLING→删 attempt→CANCELLED；与 confirm/continue 竞态 | T3.3 | 2 |
| T6.4 | 可观测性 | Micrometer+Prometheus 指标（调度延迟/running attempts/outbox 积压/失败原因）+结构化日志 | T3.4 | 2 |
| T6.5 | 背压 | outbox 上限/告警/展示事件降采样；429 RESOURCE_EXHAUSTED | T4.6 | 1.5 |
| T6.6 | 真实联调 | 替换 mock 为真实 Agent Core / Agent-Management，端到端跑通 | 全部 | 3 |
| T6.7 | 压测 + 灰度 | ≥100 并发 running attempts 达标；灰度上线 | T6.6 | 3 |

## 汇总与关键路径

| 里程碑 | 预估（人日） |
|---|---|
| M0 | 6 |
| M1 | 8 |
| M2 | 7 |
| M3 | 13 |
| M4 | 10 |
| M5 | 10 |
| M6 | 17 |
| 合计 | ~71 人日 |

关键路径：`T0.1→T1.1→T1.2→T2.x→T3.x→T4.2→T5.x→T6.2→T6.6→T6.7`。

并行建议：
- T0.3/T0.4（契约+mock）尽早，解耦对其他团队的依赖；
- 文档（详细设计）已就绪，开发可直接按 §1/§2/§3 落地；
- 鉴权 MVP 暂缓（产品决策），只读接口当前为无鉴权直连，上线前须补齐（`team_id/user_id`、401/403 已在 spec 保留备用）。

风险延续概要设计第 11 章 7 项开放问题，已分别落到对应 task（如 #3 workspace 互斥→T5.3 校验 lease、#4 取消竞态→T6.3、#2 配置→T3.5）。
