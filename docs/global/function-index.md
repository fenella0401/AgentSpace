# 功能树与功能索引

## 功能树

```text
AgentSpace
├── 空间与 Harness 配置
│   └── F-001 团队空间初始化与 Harness 配置
└── 权限与访问控制
    └── F-002 基于 RBAC 的团队空间权限管理
```

## 功能索引

| 功能编号 | 功能名称 | 所属节点 | 状态 | 功能设计文档 | 说明 |
| --- | --- | --- | --- | --- | --- |
| F-001 | 团队空间初始化与 Harness 配置 | AgentSpace / 空间与 Harness 配置 | 草稿 | `docs/function-design/F-001-team-space-initialization-and-harness-configuration.md` | 支持团队管理员初始化项目空间、配置知识库，并管理 Agent、Skill、MCP、Workflow 和环境变量等 Harness 配置。 |
| F-002 | 基于 RBAC 的团队空间权限管理 | AgentSpace / 权限与访问控制 | 草稿 | `docs/function-design/F-002-team-space-rbac-permission-management.md` | 对接公司 devuc 服务进行鉴权，支持创建者、管理员、团队成员和访客角色，并约束访客只读、团队成员无 Harness 配置权限等操作边界。 |

## 变更记录

| 日期 | 变更 | 说明 |
| --- | --- | --- |
| 2026-06-04 | 初始化产品定位与首个功能 | 新增 AgentSpace 产品定位，创建 F-001 团队空间初始化与 Harness 配置。 |
| 2026-06-04 | 新增团队空间 RBAC 权限管理需求 | 新增 F-002 基于 RBAC 的团队空间权限管理，并同步产品定义中的角色、鉴权和权限边界。 |
