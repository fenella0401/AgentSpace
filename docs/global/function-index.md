# 功能树与功能索引

## 功能树

```text
AgentSpace
├── 租户与管理面
│   └── F-003 租户管理与租户权限治理
├── 空间与 Harness 配置
│   ├── F-001 团队空间初始化与 Harness 基础配置
│   └── F-004 团队模板配置与应用
├── 权限与访问控制
│   └── F-002 基于 RBAC 的团队空间权限管理
├── Agent 会话与对话协作
│   ├── F-005 团队空间 Agent 会话创建与运行
│   ├── F-006 团队 Agent 会话历史与详情查看
│   └── F-007 Agent 文档变更审阅与确认
├── Harness CICD 流水线与任务运行
│   └── F-008 团队空间 Harness CICD 流水线配置与任务运行
└── 事件源与自动触发执行
    └── F-009 团队空间事件源配置与事件执行看板
```

## 功能索引

| 功能编号 | 功能名称 | 所属节点 | 状态 | 功能设计文档 | 说明 |
| --- | --- | --- | --- | --- | --- |
| F-001 | 团队空间初始化与 Harness 基础配置 | AgentSpace / 空间与 Harness 配置 | 草稿 | `docs/function-design/F-001-team-space-initialization-and-harness-configuration.md` | 支持零配置空间使用默认 Agent，按需管理 AGENT.md、Skill、Tool、Agent、Hook 和环境变量，并保留 Harness CICD 流水线独立入口。 |
| F-002 | 基于 RBAC 的团队空间权限管理 | AgentSpace / 权限与访问控制 | 草稿 | `docs/function-design/F-002-team-space-rbac-permission-management.md` | 对接 devuc，支持创建者、管理员、团队成员和访客角色，并约束租户管理员不默认拥有团队空间写权限。 |
| F-003 | 租户管理与租户权限治理 | AgentSpace / 租户与管理面 | 草稿 | `docs/function-design/F-003-tenant-management-and-tenant-permission-governance.md` | 系统管理员创建租户并授予租户管理员；租户管理员治理本租户配置和团队空间清单。 |
| F-004 | 团队模板配置与应用 | AgentSpace / 空间与 Harness 配置 | 草稿 / 需求待澄清 | `docs/function-design/F-004-team-template-configuration-and-application.md` | 全局或租户模板可选预置知识库、AGENT.md 和 Harness 资源，应用失败不阻止空间使用默认 Agent。 |
| F-005 | 团队空间 Agent 会话创建与运行 | AgentSpace / Agent 会话与对话协作 | 草稿 | `docs/function-design/F-005-team-agent-session-creation-and-runtime.md` | R-0630 支持统一开始作业入口、Agent/流水线目标选择、手工 Agent 会话、继续、询问回答和 Hook 生命周期执行；R-0730 增加 `/` 功能选择和 `@` 文件引用。 |
| F-006 | 团队 Agent 会话历史与详情查看 | AgentSpace / Agent 会话与对话协作 | 草稿 | `docs/function-design/F-006-team-agent-session-history-and-detail-view.md` | 支持筛选、搜索、分页查看手工和 Harness CICD 流水线 Agent 任务会话，并查看指令、执行过程、输出、修改文件和流程来源。 |
| F-007 | Agent 文档变更审阅与确认 | AgentSpace / Agent 会话与对话协作 | 草稿 | `docs/function-design/F-007-agent-document-change-review-and-confirmation.md` | 支持展示 Agent 生成或修改的文档、查看内容和 diff，并接受或拒绝变更。 |
| F-008 | 团队空间 Harness CICD 流水线配置与任务运行 | AgentSpace / Harness CICD 流水线与任务运行 | 草稿 | `docs/function-design/F-008-team-harness-cicd-pipeline-configuration-and-runtime.md` | 支持只编排 Agent 任务的 Harness CICD 流水线配置、画布和测试发布，并通过开始作业入口创建任务、运行 Agent 任务状态机、处理任务内审阅和关联 Agent 会话。 |
| F-009 | 团队空间事件源配置与事件执行看板 | AgentSpace / 事件源与自动触发执行 | 草稿 / 版本待确认 | `docs/function-design/F-009-team-event-source-configuration-and-execution-board.md` | 支持管理员配置需求系统、缺陷系统等事件源，按事件类型设置同步时间、触发目标和并发上限，并通过事件执行看板跟踪 backlog、agent working、wait review 和 done。 |

## 变更记录

| 日期 | 变更 | 说明 |
| --- | --- | --- |
| 2026-06-04 | 初始化产品定位与首个功能 | 创建 F-001 团队空间初始化与 Harness 配置。 |
| 2026-06-04 | 新增权限、租户与模板需求 | 新增 F-002、F-003 和 F-004。 |
| 2026-06-04 | 新增 Agent 会话闭环 | 新增 F-005、F-006 和 F-007。 |
| 2026-06-05 | 调整输入增强版本 | US-005-007 `/` 功能选择和 US-005-008 `@` 文件引用后移至 R-0730。 |
| 2026-06-07 | 重设计 Harness 配置 | 新增 AGENT.md 与 Hook，将 MCP 用户概念调整为 Tool，支持零配置运行、`.agentspace` 新布局、HarnessPipelineEngine 和真实 Hook 执行。 |
| 2026-06-08 | 修正 Harness 配置口径 | 明确 Harness CICD 流水线为多 Agent 标准作业、历史配置目录仅导入为草稿，AGENT.md 保持纯 Markdown，Portal 仅保存文件级发布元数据，并补充 Hook 示例口径。 |
| 2026-06-08 | 拆分 Harness CICD 流水线与 Agent 会话模型 | 新增 F-008；F-001 收敛为 Harness 基础配置，F-005/F-006 改为 Agent 会话创建与历史，Harness CICD 流水线任务由独立状态机执行。 |
| 2026-06-08 | 新增事件源自动触发需求 | 新增 F-009，支持事件源同步、事件类型触发策略、并发限制和事件执行看板，并复用 Agent 会话与 Harness CICD 流水线任务原子详情。 |
