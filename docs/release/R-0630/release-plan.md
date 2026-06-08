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
| F-001 | 团队空间初始化与 Harness 配置 | `docs/function-design/F-001-team-space-initialization-and-harness-configuration.md` | 支持团队管理员创建开发或非开发空间；空间创建后点击“开始作业”进入新建对话，也可按需配置 AGENT.md、Skill、Tool、Agent、Workflow、Hook、环境变量，并从历史配置目录导入草稿。 |
| F-002 | 基于 RBAC 的团队空间权限管理 | `docs/function-design/F-002-team-space-rbac-permission-management.md` | 支持团队空间创建者、管理员、团队成员和访客角色，并明确租户管理员不默认拥有团队空间内容写权限。 |
| F-003 | 租户管理与租户权限治理 | `docs/function-design/F-003-tenant-management-and-tenant-permission-governance.md` | 支持系统管理员创建租户、授予租户管理员，租户管理员进入租户管理面查看本租户团队空间清单。 |
| F-005 | 团队空间 Agent 任务创建与运行 | `docs/function-design/F-005-team-agent-task-creation-and-runtime.md` | R-0630 核心功能，支持默认或自定义 Agent、已发布多 Agent Workflow 的真实运行，固定 Harness 快照，并由 Workflow Engine 和 Hook Engine 处理节点编排、生命周期 Hook、确认、重试、暂停恢复与审计；不包含 US-005-007 `/` Agent 功能选择和 US-005-008 `@` 知识文件引用。 |
| F-006 | 团队 Agent 任务历史与详情查看 | `docs/function-design/F-006-team-agent-task-history-and-detail-view.md` | R-0630 核心功能，支持具备团队空间访问权限的用户筛选、搜索、分页查看团队空间永久保留的任务清单，并查看用户指令、Agent 执行过程、最终 output、修改文件和 plan 进度。 |
| F-007 | Agent 文档变更审阅与确认 | `docs/function-design/F-007-agent-document-change-review-and-confirmation.md` | R-0630 核心功能，支持展示 Agent 生成或修改的文档、查看内容和 diff，并接受或拒绝文档变更。 |

## 时间安排

| 节点 | 时间 | 负责人 |
| --- | --- | --- |
| 需求与功能设计评审 | 2026-06-04 至 2026-06-10 | 产品 / 技术 / 测试待确认 |
| 研发实现与联调 | 2026-06-11 至 2026-06-23 | 前端 / 后端待确认 |
| 版本验收 | 2026-06-24 至 2026-06-28 | 测试待确认 |
| 发布准备 | 2026-06-29 至 2026-06-30 | 运维 / 发布负责人待确认 |

## 风险与依赖

- 依赖 devuc 提供系统管理员、租户管理员和团队空间访问鉴权能力。
- F-005、F-006、F-007 共同构成 R-0630 核心 Agent 任务闭环，依赖 F-001 的团队空间与 Harness 配置基础、F-002 的团队空间 RBAC 和 F-003 的租户上下文。
- F-001 必须保证所有 Harness 配置中心均可为空；知识库、远程代码仓和开发专属字段均不得成为非开发空间的创建或开始作业前置条件。
- F-001 运行时仅识别 `/AGENT.md`、顶层知识域 `AGENT.md` 与 `/.agentspace/` 新布局；`.codex`、`.claudecode`、`.opencode` 只支持用户显式导入为 Portal 草稿。
- Tool 只能引用 AI 市场已发布资源；市场负责协议、地址、认证和运行参数，AgentSpace 不提供 Tool 新建或连接参数编辑能力，MCP 仅作为底层适配协议。
- 历史 MCP、server 或 tool 配置不得导入连接参数，只能生成市场 Tool 匹配建议；第三方 subagent 配置导入为 Agent 草稿。
- F-001 依赖 AI 市场提供 Skill、Tool 和 Agent 的资源 ID、版本、能力与依赖声明；依赖 Secret Service 保存敏感环境变量值。
- F-005 依赖外部公司 agent core 服务提供 Agent 节点执行、继续对话、事件流、用户回答回传和错误码契约。
- F-005 依赖 AgentSpace Workflow Engine 提供多 Agent 节点调度、条件分支、并行、重试、幂等、人工确认、暂停恢复和版本固定能力。
- F-005 依赖 agent core 与 AgentSpace 发布确定性的 Hook 生命周期事件；Hook Engine 必须实现超时、失败策略、调用链和递归深度限制，不允许通过自然语言推测事件。
- R-0630 任务与 Workflow 启动请求支持可选 `knowledgeDomainIds` 结构化字段，知识域加载不依赖 R-0730 的 `@` 文件引用。
- Harness 发布 Hook 必须拆分为同步 `harness.publish.before` 和异步 `harness.publish.after`，并复用统一的 HookExecution 与确认记录。
- Harness 快照必须固定本次运行所使用的 AGENT.md 层级、Agent、Skill、Tool、Workflow、Hook 版本及环境变量引用；运行中配置更新不得影响当前实例。
- F-005 的 US-005-007 `/` Agent 功能选择和 US-005-008 `@` 知识文件引用后移至 R-0730，R-0630 发布验收和 TC-005 不执行对应两个测试点。
- F-006 依赖任务列表筛选搜索分页、分钟级相对更新时间、任务事件持久化、永久历史保留、对话名称同步、可展示执行过程事件、最终输出、修改文件、plan 进度事件和事件流稳定性。
- F-007 依赖 agent core 文档变更事件、知识库文档版本控制、diff 生成和并发冲突检测。
- 租户作为团队空间必选上级域后，历史未归属空间迁移策略仍待确认；R-0630 首期只约束新建空间。
- 租户管理员和团队管理员权限边界必须在前端权限态、后端鉴权和审计中保持一致。
- 知识域识别、AGENT.md 层级合并、Harness 配置文件同步、资源依赖解析和敏感环境变量引用仍需技术方案确认。
- Agent 任务事件流、Workflow 与 Hook 运行事件、执行过程可展示边界、plan 进度事件、文档 diff 暂存和文档版本冲突处理需要在研发前确认。
- R-0730 团队模板能力不进入本版本发布验收，仅保留后续需求衔接。

## 会签

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认 R-0630 以零配置可运行、可选 Harness 配置及真实 Workflow/Hook 运行闭环为范围，并确认历史对话继续输入、对话名称、访客只读、文档 diff 处理规则，以及 US-005-007、US-005-008 后移至 R-0730。 |
| 技术 | 待确认 | 需确认新 Harness 布局、市场资源引用、Secret Service、Workflow Engine、Hook 生命周期协议、agent core 节点执行契约、事件流方案、永久历史保留和文档版本控制。 |
| 测试 | 待确认 | 需覆盖零配置非开发空间、新建对话入口、AGENT.md 分层、市场 Tool、历史配置导入草稿、依赖按需补齐、多 Agent Workflow、Hook、敏感变量、Agent 任务、历史详情和文档 diff；US-005-007、US-005-008 对应测试点本版本不执行。 |
| 运维 | 待确认 | 需确认市场、Secret Service、Workflow Engine、Hook Engine、外部 agent core、事件流稳定性和发布回滚关注点。 |
