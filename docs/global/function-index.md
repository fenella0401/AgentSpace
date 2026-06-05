# 功能树与功能索引

## 功能树

```text
AgentSpace
├── 租户与管理面
│   └── F-003 租户管理与租户权限治理
├── 空间与 Harness 配置
│   ├── F-001 团队空间初始化与 Harness 配置
│   └── F-004 团队模板配置与应用
├── 权限与访问控制
│   └── F-002 基于 RBAC 的团队空间权限管理
└── Agent 任务与对话协作
    ├── F-005 团队空间 Agent 任务创建与运行
    ├── F-006 团队 Agent 任务历史与详情查看
    └── F-007 Agent 文档变更审阅与确认
```

## 功能索引

| 功能编号 | 功能名称 | 所属节点 | 状态 | 功能设计文档 | 说明 |
| --- | --- | --- | --- | --- | --- |
| F-001 | 团队空间初始化与 Harness 配置 | AgentSpace / 空间与 Harness 配置 | 草稿 | `docs/function-design/F-001-team-space-initialization-and-harness-configuration.md` | 支持团队管理员在所属租户内初始化项目空间、配置知识库，并管理 Agent、Skill、MCP、Workflow 和环境变量等 Harness 配置。 |
| F-002 | 基于 RBAC 的团队空间权限管理 | AgentSpace / 权限与访问控制 | 草稿 | `docs/function-design/F-002-team-space-rbac-permission-management.md` | 对接公司 devuc 服务进行鉴权，支持团队空间创建者、管理员、团队成员和访客角色，并约束租户管理员不默认拥有团队空间内容写权限。 |
| F-003 | 租户管理与租户权限治理 | AgentSpace / 租户与管理面 | 草稿 | `docs/function-design/F-003-tenant-management-and-tenant-permission-governance.md` | 系统管理员在全局管理面创建租户并授予租户管理员；租户管理员进入租户管理面治理本租户配置和团队空间清单。 |
| F-004 | 团队模板配置与应用 | AgentSpace / 空间与 Harness 配置 | 草稿 / 需求待澄清 | `docs/function-design/F-004-team-template-configuration-and-application.md` | 系统管理员维护全局团队模板，租户管理员维护租户级模板，团队管理员初始化团队空间时选择模板以初始化知识库目录、远程配置仓和 Agent 使用配置。 |
| F-005 | 团队空间 Agent 任务创建与运行 | AgentSpace / Agent 任务与对话协作 | 草稿 | `docs/function-design/F-005-team-agent-task-creation-and-runtime.md` | R-0630 核心功能，支持团队成员新建 Agent 对话任务，生成 Harness 快照，调用外部 agent core，继续历史对话，维护对话名称，并处理 Agent 询问。 |
| F-006 | 团队 Agent 任务历史与详情查看 | AgentSpace / Agent 任务与对话协作 | 草稿 | `docs/function-design/F-006-team-agent-task-history-and-detail-view.md` | R-0630 核心功能，支持具备团队空间访问权限的用户筛选、搜索、分页查看团队空间永久保留的任务清单，并查看用户指令、Agent 执行过程、最终 output、修改文件和 plan 进度。 |
| F-007 | Agent 文档变更审阅与确认 | AgentSpace / Agent 任务与对话协作 | 草稿 | `docs/function-design/F-007-agent-document-change-review-and-confirmation.md` | R-0630 核心功能，支持展示 Agent 生成或修改的文档、查看内容和 diff，并接受或拒绝文档变更。 |

## 变更记录

| 日期 | 变更 | 说明 |
| --- | --- | --- |
| 2026-06-04 | 初始化产品定位与首个功能 | 新增 AgentSpace 产品定位，创建 F-001 团队空间初始化与 Harness 配置。 |
| 2026-06-04 | 新增团队空间 RBAC 权限管理需求 | 新增 F-002 基于 RBAC 的团队空间权限管理，并同步产品定义中的角色、鉴权和权限边界。 |
| 2026-06-04 | 新增租户管理与团队模板需求 | 新增 F-003 租户管理与租户权限治理并纳入 R-0630；新增 F-004 团队模板配置与应用并暂纳入 R-0730 需求池。 |
| 2026-06-04 | 新增 R-0630 核心 Agent 任务对话需求 | 新增 Agent 任务与对话协作能力，并拆分为 F-005 任务创建与运行、F-006 任务历史与详情、F-007 文档变更审阅与确认。 |
