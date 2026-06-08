# AgentSpace 功能树与功能索引

```text
AgentSpace
├── 空间与 Harness 配置
│   ├── F-001 团队空间初始化与 Harness 基础配置
│   └── F-004 团队模板配置与应用
├── 权限与访问控制
│   └── F-002 基于 RBAC 的团队空间权限管理
├── 租户与管理面
│   └── F-003 租户管理与租户权限治理
├── Agent 会话与对话协作
│   ├── F-005 团队空间 Agent 会话创建与运行
│   ├── F-006 团队 Agent 会话历史与详情查看
│   └── F-007 Agent 文档变更审阅与确认
├── AgentFlow 与任务运行
│   └── F-008 团队空间 AgentFlow 配置与任务运行
└── 事件源与自动触发执行
    └── F-009 团队空间事件源配置与事件执行看板
```

## 功能索引

| 功能编号 | 功能名称 | 所属节点 | 状态 | 文档路径 | 摘要 |
| --- | --- | --- | --- | --- | --- |
| F-001 | 团队空间初始化与 Harness 基础配置 | AgentSpace / 空间与 Harness 配置 | 草稿 | `docs/function-design/F-001-team-space-initialization-and-harness-configuration.md` | 支持零配置空间、AGENT.md、市场 Skill、市场 Tool、在线 Agent 和环境变量；不配置 Hook，不从代码仓同步 Harness 配置。 |
| F-002 | 基于 RBAC 的团队空间权限管理 | AgentSpace / 权限与访问控制 | 草稿 | `docs/function-design/F-002-team-space-rbac-permission-management.md` | 对接 devuc，支持创建者、管理员、团队成员和访客角色，并约束租户管理员不默认拥有团队空间写权限。 |
| F-003 | 租户管理与租户权限治理 | AgentSpace / 租户与管理面 | 草稿 | `docs/function-design/F-003-tenant-management-and-tenant-permission-governance.md` | 系统管理员创建租户并授予租户管理员；租户管理员治理本租户配置和团队空间清单。 |
| F-004 | 团队模板配置与应用 | AgentSpace / 空间与 Harness 配置 | 草稿 / 需求待澄清 | `docs/function-design/F-004-team-template-configuration-and-application.md` | 全局或租户模板可选预置知识库、AGENT.md、市场资源引用、在线 Agent 草稿和 AgentFlow 候选。 |
| F-005 | 团队空间 Agent 会话创建与运行 | AgentSpace / Agent 会话与对话协作 | 草稿 | `docs/function-design/F-005-team-agent-session-creation-and-runtime.md` | R-0630 支持统一开始作业入口、Agent/AgentFlow 目标选择、手工 Agent 会话、继续和询问回答；R-0730 增加 `/` 功能选择和 `@` 文件引用。 |
| F-006 | 团队 Agent 会话历史与详情查看 | AgentSpace / Agent 会话与对话协作 | 草稿 | `docs/function-design/F-006-team-agent-session-history-and-detail-view.md` | 支持筛选、搜索、分页查看手工、AgentFlow Agent 任务和事件源触发的 Agent 会话。 |
| F-007 | Agent 文档变更审阅与确认 | AgentSpace / Agent 会话与对话协作 | 草稿 | `docs/function-design/F-007-agent-document-change-review-and-confirmation.md` | 支持展示 Agent 生成或修改的文档、查看内容和 diff，并接受或拒绝变更。 |
| F-008 | 团队空间 AgentFlow 配置与任务运行 | AgentSpace / AgentFlow 与任务运行 | 草稿 | `docs/function-design/F-008-team-agentflow-configuration-and-runtime.md` | 支持只编排 Agent 任务的 AgentFlow 配置、画布、测试发布、任务运行、任务内审阅和 Agent 会话关联。 |
| F-009 | 团队空间事件源配置与事件执行看板 | AgentSpace / 事件源与自动触发执行 | 草稿 | `docs/function-design/F-009-team-event-source-configuration-and-execution-board.md` | R-0630 首版对接公司 eDevOps FE，支持同步、触发 Agent 会话或 AgentFlow，并通过 backlog、agent working、waiting approval、done 跟踪状态。 |

## 变更记录

| 日期 | 变更 | 说明 |
| --- | --- | --- |
| 2026-06-04 | 初始化产品定位与首个功能 | 创建 F-001 团队空间初始化与 Harness 配置。 |
| 2026-06-04 | 新增权限与租户能力 | 新增 F-002 和 F-003。 |
| 2026-06-04 | 新增 Agent 会话闭环 | 新增 F-005、F-006 和 F-007。 |
| 2026-06-08 | 新增 AgentFlow | 新增 F-008，独立承接多 Agent 标准作业配置与任务运行。 |
| 2026-06-08 | 新增事件源自动触发需求 | 新增 F-009，R-0630 首版对接 eDevOps FE 并复用 Agent 会话与 AgentFlow 原子详情。 |
| 2026-06-08 | 修正 Harness 基础配置口径 | 明确不配置 Hook、不从代码仓同步 Harness 配置、Skill 仅市场选择、Agent 仅在线配置。 |
