# 发布计划

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 版本编号 | R-0630 |
| 计划发布时间 | 2026-06-30 |
| 发布负责人 | 待确认 |
| 当前状态 | 计划中 |

## 发布范围

| 功能编号 | 功能名称 | 功能设计文档 | 说明 |
| --- | --- | --- | --- |
| F-001 | 团队空间初始化与 Harness 基础配置 | `docs/function-design/F-001-team-space-initialization-and-harness-configuration.md` | 支持零配置空间、可选知识库、AGENT.md、市场 Skill、市场 Tool、在线 Agent 和环境变量；不配置 Hook，不从代码仓同步 Harness 配置。 |
| F-002 | 基于 RBAC 的团队空间权限管理 | `docs/function-design/F-002-team-space-rbac-permission-management.md` | 支持团队空间创建者、管理员、团队成员和访客角色，并明确租户管理员不默认拥有团队空间内容写权限。 |
| F-003 | 租户管理与租户权限治理 | `docs/function-design/F-003-tenant-management-and-tenant-permission-governance.md` | 支持系统管理员创建租户、授予租户管理员，租户管理员进入租户管理面查看本租户团队空间清单。 |
| F-005 | 团队空间 Agent 会话创建与运行 | `docs/function-design/F-005-team-agent-session-creation-and-runtime.md` | 支持统一开始作业入口、Agent/AgentFlow 目标选择、零配置或指定 Agent 的手工 Agent 会话、继续输入、询问回答和固定 Harness 快照；不包含 US-005-007 `/`、US-005-008 `@` 输入增强。 |
| F-006 | 团队 Agent 会话历史与详情查看 | `docs/function-design/F-006-team-agent-session-history-and-detail-view.md` | 支持筛选、搜索、分页查看手工、AgentFlow Agent 任务和事件源触发的 Agent 会话，并查看执行过程、最终 output、修改文件和来源链接。 |
| F-007 | Agent 文档变更审阅与确认 | `docs/function-design/F-007-agent-document-change-review-and-confirmation.md` | 支持展示 Agent 生成或修改的文档、查看内容和 diff，并接受或拒绝文档变更。 |
| F-008 | 团队空间 AgentFlow 配置与任务运行 | `docs/function-design/F-008-team-agentflow-configuration-and-runtime.md` | 支持只编排 Agent 任务的 AgentFlow 配置、画布、测试发布、任务运行、任务内审阅、取消重试，以及每个 Agent 任务自动创建 Agent 会话。 |
| F-009 | 团队空间事件源配置与事件执行看板 | `docs/function-design/F-009-team-event-source-configuration-and-execution-board.md` | R-0630 首版对接公司 eDevOps FE，支持同步、去重、触发 Agent 会话或 AgentFlowTask，并通过 backlog、agent working、waiting approval、done 跟踪状态。 |

## 时间安排

| 节点 | 时间 | 负责人 |
| --- | --- | --- |
| 需求与功能设计评审 | 2026-06-04 至 2026-06-10 | 产品 / 技术 / 测试待确认 |
| 研发实现与联调 | 2026-06-11 至 2026-06-23 | 前端 / 后端待确认 |
| 版本验收 | 2026-06-24 至 2026-06-28 | 测试待确认 |
| 发布准备 | 2026-06-29 至 2026-06-30 | 运维 / 发布负责人待确认 |

## 风险与依赖

- 依赖 devuc 提供系统管理员、租户管理员和团队空间访问鉴权能力。
- F-005、F-006、F-007、F-008、F-009 共同构成 R-0630 核心 Agent 作业闭环，依赖 F-001 的团队空间与 Harness 基础配置、F-002 的团队空间 RBAC 和 F-003 的租户上下文。
- F-001 必须保证所有 Harness 基础配置中心均可为空；知识库、远程代码仓和开发专属字段均不得成为非开发空间的创建或开始作业前置条件。
- F-001 不从代码仓、`.codex`、`.claudecode`、`.opencode` 或任意历史目录同步 Harness 配置；远程代码仓只作为知识来源。
- Skill 和 Tool 只能引用 AI 市场已发布资源；AgentSpace 不提供新建 Skill、Tool 连接参数编辑或 MCP 配置能力。
- Agent 不支持市场选择，只支持在线配置名称、描述、使用的 Skill、使用的 Tool 和预置 prompt。
- F-001 依赖 AI 市场提供 Skill、Tool 的资源 ID、版本、能力与依赖声明；依赖 Secret Service 保存敏感环境变量值。
- F-005 依赖外部公司 agent core 服务提供手工 Agent 会话创建、继续对话、询问回答、事件流和错误码契约。
- F-006 依赖 AgentSession 事件持久化、来源字段、父 AgentFlow 链接、事件源来源摘要、最终输出、修改文件和事件流稳定性。
- F-008 依赖 AgentFlowEngine 提供多 Agent 任务依赖调度、已确认输出传递、并发上限、重试、幂等、任务内审阅等待与恢复、暂停恢复、版本固定和服务重启恢复能力。
- F-008 依赖 F-005/F-006 的 AgentSession 模型；每个 AgentFlow Agent 任务必须自动创建 `source=agent_flow_node` 的 Agent 会话。
- F-009 依赖租户级 eDevOps 授权、eDevOps FE 适配器、同步水位、字段白名单、去重和事件触发调度模型。
- R-0630 开始作业入口在 Agent 目标和 AgentFlow 目标下均支持可选 `knowledgeDomainIds` 结构化字段，知识域加载不依赖 R-0730 的 `@` 文件引用。
- Harness 快照必须固定本次运行所使用的 AGENT.md 层级、Agent、Skill、Tool、AgentFlow 和环境变量引用版本；运行中配置更新不得影响当前实例。
- F-005 的 US-005-007 `/` Agent 功能选择和 US-005-008 `@` 知识文件引用后移至 R-0730，R-0630 发布验收和 TC-005 不执行对应两个测试点。
- F-007 依赖 agent core 文档变更事件、知识库文档版本控制、diff 生成和并发冲突检测。
- 租户作为团队空间必选上级域后，历史未归属空间迁移策略仍待确认；R-0630 首期只约束新建空间。
- 租户管理员和团队管理员权限边界必须在前端权限态、后端鉴权和审计中保持一致。
- 知识域识别、AGENT.md 层级合并、资源依赖解析、敏感环境变量引用和 eDevOps 字段脱敏仍需技术方案确认。

## 会签

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认 R-0630 以零配置可运行、统一开始作业入口、可选 Harness 基础配置、手工 Agent 会话、AgentFlow 任务、事件源看板和文档审阅为范围，并确认会话名称、访客只读、AgentFlow 任务清单、eDevOps FE 字段白名单和 waiting approval 映射。 |
| 技术 | 待确认 | 需确认市场资源引用、Secret Service、AgentSession、AgentFlowEngine、agent core 契约、事件流方案、eDevOps 适配器、同步水位和文档版本控制。 |
| 测试 | 待确认 | 需覆盖零配置非开发空间、AGENT.md 分层、市场 Skill/Tool、在线 Agent、手工 Agent 会话、AgentFlow、事件源同步与看板、历史详情、文档 diff 和权限；US-005-007、US-005-008 对应测试点本版本不执行。 |
| 运维 | 待确认 | 需确认市场、Secret Service、AgentFlowEngine、外部 agent core、eDevOps 适配器、事件流稳定性和发布回滚关注点。 |
