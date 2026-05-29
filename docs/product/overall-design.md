# AgentSpace 产品总设计

## 1. 产品定位

AgentSpace 是一个在线化地使用 Agent 完成项目工作的企业作业平台。它面向公司内部团队，让单人或多人项目能够在受治理的环境中共享 project harness 配置，基于 harness 调度 Agent 执行任务，并完整追踪每次 Agent 会话的过程、文件变更、配置变更、技能调用、审查节点和交付结果。

平台的核心价值不是提供一个通用聊天入口，而是把“AI 参与项目交付”产品化：

- 项目团队可以在线维护、版本化、审阅和回滚 harness 配置。
- 用户可以把项目事件、定时计划、一次性指令转化为可观察、可中断、可审查的 Agent 任务。
- 管理员和项目 owner 可以明确每个 Agent 工作流的权限、输入输出契约、工具策略、审查策略和评测要求。
- 审查者可以回放 Agent 会话，理解其修改了什么、为什么修改、调用了哪些技能和外部工具，并在关键节点执行批准、驳回或补充指令。

参考 OpenAI Symphony 的服务规格，AgentSpace 借鉴其“配置契约、调度器、隔离工作区、运行状态机、结构化日志”的模式。但 Symphony 更偏后台 runner 和 issue tracker 自动化；AgentSpace 的差异是提供企业 SaaS 控制面、多租户协作、在线 harness 版本管理、权限治理、审查提醒和可视化追踪。

参考资料：[OpenAI Symphony SPEC](https://github.com/openai/symphony/blob/main/SPEC.md)。

## 2. 设计目标

### 2.1 目标

- 支持单人和多人项目在平台内创建、配置、执行和监控 Agent 作业。
- 支持 project harness 的在线编辑、结构化校验、版本化、审查、发布、回滚和追溯。
- 支持三类任务来源：项目事件任务、定时任务、一次性下发任务。
- 支持每个任务绑定 harness 版本、Agent 策略、权限边界、审查规则和输出契约。
- 支持查看 Agent 会话全过程，包括消息、状态、文件 diff、配置 diff、技能调用、工具调用、日志、token、成本、错误和人工介入。
- 支持在高风险节点提醒用户执行审查动作，例如权限提升、外部写入、敏感文件修改、生产发布、异常重试和任务完成验收。
- 支持企业级身份、租户隔离、RBAC、审计日志、数据脱敏、最小权限集成和可观测性。

### 2.2 非目标

- 不做开放式通用 Agent 市场；首版只支持组织批准的 Agent、技能和连接器。
- 不把平台定位为替代 GitHub、Jira、Linear 或 CI/CD，而是通过连接器与现有系统协同。
- 不承诺 Agent 自动完成所有项目交付；高风险动作必须保留人类审查和回退路径。
- 不在首版支持任意脚本和任意网络访问；工具能力必须通过 policy allowlist 管理。
- 不把聊天作为唯一工作形态；聊天只是任务会话的一种交互表面。

### 2.3 当前产品决策

- 首个事件源选择 GitHub，优先覆盖 issue、PR 和仓库事件。
- Harness 底层配置使用 YAML，在线编辑器可以提供结构化表单视图。
- Agent runtime 初步仅使用容器沙箱，后续保留扩展到远程执行池或其他 workspace runtime 的接口。
- Trace 原文保留一年；默认脱敏展示 token、secret、credential、API key 等高密信息。
- 审查通知首版仅使用站内通知，后续再评估邮件、Slack、飞书等外部渠道。
- 任务结果是否可以直接写入外部系统由用户在 harness 中配置；平台仍需对高影响写入执行策略校验和 Review Gate。
- 模型供应商兼容 OpenAI 和 Anthropic 协议模型；模型路由策略由用户在 harness 中配置，并受组织级模型 allowlist 和预算护栏约束。

## 3. 用户与角色

### 3.1 主要用户

- 项目成员：创建任务、查看进度、补充上下文、审查 Agent 产物。
- 项目 owner：维护 project harness、设定任务规则、批准版本发布、处理风险例外。
- 审查者：在指定节点检查 Agent 会话、文件变更、配置变更、工具调用和结果质量。
- 平台管理员：管理组织、租户、用户、角色、连接器、模型、技能、全局策略和审计。
- AI 平台团队：维护 Agent 模板、技能包、评测集、提示词规范、模型路由和 trace 策略。
- SRE/安全团队：监控执行环境、隔离策略、敏感数据、异常行为、成本与故障。

### 3.2 权限角色

- `OrgAdmin`：组织级配置、身份、全局策略、审计和连接器。
- `ProjectOwner`：项目设置、成员、harness 发布、任务策略和审查规则。
- `ProjectMaintainer`：编辑草稿 harness、配置任务模板、管理项目事件绑定。
- `Contributor`：创建和执行允许范围内的任务，查看自己有权访问的会话。
- `Reviewer`：处理审查请求，批准、驳回、要求修改或接管任务。
- `Observer`：只读查看项目、任务、运行状态和允许范围内的审计信息。

权限必须同时满足组织、项目、资源和任务上下文的检查。任何 Agent 工具调用不能继承发起人的全部权限，只能获得任务策略显式授予的短期能力。

## 4. 核心概念

### 4.1 Organization / Tenant

组织是身份、策略、连接器、审计和数据隔离的最高边界。所有项目、harness、任务、会话、文件索引和日志都必须带有 `tenant_id`，跨租户查询和引用默认禁止。

### 4.2 Project

项目是 Agent 作业的协作空间。项目可以是单人或多人参与，包含成员、角色、harness、任务、事件源、运行环境、审查策略、审计日志和指标。

项目必须有 owner、业务目标、数据分类、默认 harness 渠道、默认审查策略和归档策略。

### 4.3 Project Harness

Project harness 是项目运行 Agent 的在线配置契约。它描述 Agent 如何理解项目、能访问哪些上下文、能使用哪些技能、如何执行任务、何时需要审查、如何产出结果、如何评估质量、如何回退。

Harness 不是单一 prompt，而是一组版本化配置：

- 项目说明与工作边界。
- Agent 模板和模型策略。
- 输入输出契约。
- 技能、工具、连接器和权限策略。
- 事件触发规则和定时任务规则。
- 工作区、文件访问和变更策略。
- 审查门禁和通知规则。
- 评测、验收和失败回退策略。
- 观测、日志和数据保留策略。

### 4.4 Harness Version

每次 harness 发布都会生成不可变版本。任务执行必须绑定明确的 harness 版本，确保执行结果可复现、可审计、可追溯。草稿可以多次编辑，发布版本不能原地修改，只能创建新版本或回滚到旧版本。

### 4.5 Task

Task 是一次 Agent 作业的调度单元。任务可以来自：

- Event Task：由项目事件触发，例如 issue 创建、PR 更新、文件变更、告警、业务表单提交。
- Scheduled Task：按 cron/日历规则触发，例如每日巡检、每周总结、每月评测。
- Dispatch Task：由用户一次性下发，例如“分析这个需求并生成实现计划”。

任务必须记录触发来源、发起人或系统身份、输入上下文、harness 版本、权限策略、审查策略、运行状态、重试策略、结果和审计链。

### 4.6 Agent Session

Agent Session 是一个任务的一次或多次 Agent 执行会话。每个 session 需要保留可回放 trace，包括模型消息、计划、工具调用、技能调用、文件 diff、配置 diff、日志摘要、错误、人工审查动作和最终交付物。

### 4.7 Workspace

Workspace 是任务执行的隔离工作区。它可以映射到代码仓库、文档空间、临时文件系统、沙箱容器或外部系统影子副本。工作区生命周期由 harness 和任务策略共同决定。

默认要求：

- 每个任务或任务组使用隔离 workspace。
- Agent 只能访问策略允许的路径、连接器和工具。
- 产物、diff、日志和 trace 必须与任务绑定。
- 工作区清理、保留、导出和恢复遵循项目数据策略。

### 4.8 Review Gate

Review Gate 是需要人工介入的审查点。它可以由 harness 显式配置，也可以由平台风险规则自动触发。审查动作包括批准继续、驳回停止、要求修改、补充上下文、提升权限、接管执行或创建后续任务。

## 5. 信息架构

### 5.1 全局导航

- 首页：我的项目、待审查、最近任务、风险提醒、平台公告。
- 项目：项目列表、收藏、角色、状态、数据分类。
- 任务：跨项目任务队列、筛选、运行状态、失败和待审查。
- Harness：可访问的 harness 模板、项目 harness、版本和变更记录。
- Agent 与技能：已批准 Agent、技能、工具和连接器目录。
- 审计与观测：运行日志、trace、成本、错误、安全事件。
- 管理：组织、成员、RBAC、策略、模型、连接器、保留周期。

### 5.2 项目空间

项目空间首屏面向执行和监控：

- Overview：项目目标、owner、成员、当前 harness 版本、健康状态、待办审查。
- Tasks：事件任务、定时任务、一次性任务、队列、运行中、历史记录。
- Harness：草稿、已发布版本、diff、校验结果、发布审批、回滚。
- Sessions：Agent 会话列表、过程回放、文件变更、技能调用和审查记录。
- Events：项目事件源、触发记录、失败重放、去重和限流。
- Reviews：待处理、已处理、超时、升级和审查策略。
- Settings：成员、角色、连接器、数据策略、通知和归档。

### 5.3 任务详情页

任务详情页必须让用户快速回答：

- 这个任务为什么被触发？
- 它使用了哪个 harness 版本？
- Agent 被允许做什么？
- 当前卡在哪个阶段？
- Agent 改了什么？
- 是否需要我审查？
- 结果能否交付或合并？

核心区域：

- 任务摘要：标题、来源、状态、优先级、发起人、负责 Agent、harness 版本。
- 时间线：触发、入队、启动、工具调用、文件修改、审查、重试、完成。
- 会话回放：消息、计划、工具调用、技能调用、日志摘要。
- 变更视图：文件 diff、配置 diff、产物、测试结果、风险标记。
- 审查面板：待审事项、建议、批准/驳回/要求修改/接管。
- 运行指标：耗时、token、成本、重试次数、错误、限流、资源使用。

## 6. 关键用户流程

### 6.1 创建项目

1. 用户创建项目，填写名称、目标、数据分类、owner 和成员。
2. 平台要求选择 harness 模板或从空白配置开始。
3. 项目 owner 配置连接器、默认 workspace、审查策略和通知渠道。
4. 平台运行安全预检，检查权限、连接器、敏感数据和默认策略。
5. 项目进入 Draft 或 Active 状态。

验收标准：

- 项目没有 owner、数据分类或默认策略时不能激活。
- 成员只能看到自己有权限的项目。
- 项目创建、成员变更和策略变更必须写入审计日志。

### 6.2 编辑和发布 harness

1. Maintainer 创建 harness 草稿。
2. 在线编辑器按模块展示配置，并支持结构化表单和 YAML 源码视图。
3. 平台即时校验 schema、权限、连接器、审查规则和危险配置。
4. 用户提交发布请求，系统生成 diff、风险摘要和影响范围。
5. Reviewer 或 ProjectOwner 审批发布。
6. 发布生成不可变版本，后续任务默认使用最新发布版本。

验收标准：

- 已发布版本不可被原地修改。
- 每个版本记录作者、审批人、时间、变更摘要、校验结果和引用任务。
- 删除技能、扩大权限、降低审查级别、修改外部写入能力必须触发高风险审批。

### 6.3 配置事件任务

1. 用户选择事件源，例如 issue、PR、仓库、告警或表单。
2. 选择触发条件、过滤规则、去重窗口、并发限制和失败处理。
3. 选择任务模板、harness 版本策略和输入映射。
4. 配置审查规则，例如“涉及生产配置时需要 SRE 审查”。
5. 启用后，事件进入触发记录，可重放和暂停。

验收标准：

- 事件输入必须经过结构化解析和大小限制。
- 触发规则变更必须写入审计日志。
- 重放任务必须标记为 replay，并记录操作者。

### 6.4 配置定时任务

1. 用户选择日历或 cron 规则。
2. 选择任务模板、运行时区、并发策略、错过执行处理和通知规则。
3. 平台展示未来若干次触发时间。
4. 启用后，任务按计划入队。

验收标准：

- 定时任务必须有 owner 和失败通知人。
- 同一任务重叠执行时必须按策略跳过、排队或取消旧运行。
- 时区、夏令时和暂停恢复行为必须明确展示。

### 6.5 一次性下发任务

1. 用户在项目内创建 dispatch task，填写目标、上下文、约束和期望输出。
2. 平台根据当前 harness 版本和用户权限计算可用 Agent、技能和工具。
3. 用户确认后任务入队。
4. Agent 执行，必要时请求用户审查或补充上下文。
5. 任务完成后输出结果、变更和建议后续动作。

验收标准：

- 用户不能通过 prompt 绕过工具权限和审查策略。
- 高风险操作必须先进入 Review Gate。
- 任务结果必须关联完整 session trace。

### 6.6 审查 Agent 会话

1. 用户收到待审查提醒。
2. 打开审查页面，查看触发原因、风险摘要、diff、工具调用和 Agent 解释。
3. 用户可以批准、驳回、要求修改、补充指令、接管或升级给其他角色。
4. 平台记录审查动作，并将结果反馈给任务编排器。

验收标准：

- 审查前后状态转换清晰可见。
- 审查动作必须记录用户、时间、原因和影响范围。
- 超时审查必须按项目策略升级或取消任务。

## 7. Harness 配置设计

### 7.1 配置结构

Harness 建议采用模块化、可校验的结构。在线编辑可以表单化，但底层应存储为版本化 YAML 文件。

```yaml
schema_version: 1
project:
  objective: "项目目标"
  boundaries:
    in_scope: []
    out_of_scope: []
  data_classification: internal

agents:
  default:
    template: coding_agent
    model_provider: openai_compatible
    model_policy: standard
    model_route: project_default
    max_turns: 20
    fallback: request_human_review

inputs:
  contracts:
    dispatch_task:
      required: [goal]
      optional: [context, constraints, expected_output]

tools:
  allowed_skills: []
  allowed_connectors: []
  filesystem:
    read_paths: []
    write_paths: []

tasks:
  event_rules: []
  schedules: []
  dispatch_templates: []
  result_writes:
    external_write_policy: review_required

reviews:
  gates: []
  escalation: []
  notification_channels: [in_app]

evals:
  required_checks: []
  fixtures: []

observability:
  trace_level: standard
  retention_days: 365
  redact_high_secret_tokens: true
```

### 7.2 配置状态

- Draft：可编辑，不能被生产任务使用。
- Validated：通过 schema 和策略校验，但尚未发布。
- In Review：等待审批。
- Published：不可变，可被任务绑定。
- Deprecated：仍可追溯，默认不再用于新任务。
- Rolled Back：标记被回滚的版本。
- Archived：项目归档后的只读版本。

### 7.3 配置校验

发布前必须校验：

- Schema 版本兼容。
- Agent 模板存在且已批准。
- 模型供应商、模型路由和模型策略符合组织 allowlist、预算护栏与 harness 配置。
- 技能、工具、连接器存在且授权范围明确。
- 文件读写路径符合项目边界。
- 审查规则覆盖高风险能力。
- 输入输出契约完整。
- 外部写入策略明确，并对高影响写入配置 Review Gate。
- 评测和验收检查可执行。
- 日志和 trace 不默认记录敏感原文。

### 7.4 版本追溯

每个 harness version 必须记录：

- `version_id`
- `project_id`
- `parent_version_id`
- `status`
- `created_by`
- `approved_by`
- `created_at`
- `published_at`
- `change_summary`
- `risk_summary`
- `config_hash`
- `schema_validation_result`
- `policy_validation_result`

任务记录必须保存执行时的 `harness_version_id` 和 `config_hash`，避免后续配置变更污染历史解释。

## 8. 任务编排设计

### 8.1 状态机

任务状态：

- `Draft`：用户或系统创建但尚未入队。
- `Queued`：等待调度。
- `Preparing`：准备 workspace、上下文和权限令牌。
- `Running`：Agent 正在执行。
- `WaitingForReview`：等待人工审查。
- `WaitingForInput`：等待用户补充上下文。
- `RetryScheduled`：失败后等待重试。
- `Succeeded`：完成并通过验收。
- `Failed`：失败且不再重试。
- `Canceled`：用户或系统取消。
- `Expired`：超过 SLA 或审查超时。

运行尝试状态：

- `PreparingWorkspace`
- `BuildingPrompt`
- `LaunchingAgent`
- `StreamingSession`
- `ApplyingChanges`
- `Evaluating`
- `RequestingReview`
- `Finishing`
- `Succeeded`
- `Failed`
- `TimedOut`
- `Stalled`
- `Canceled`

### 8.2 调度规则

- 编排器是任务状态的唯一写入方，避免重复调度。
- 调度前必须加载任务绑定的 harness version，并完成策略预检。
- 全局、项目、任务类型和 Agent 级并发限制都必须生效。
- 任务重试使用指数退避，并保留失败原因。
- 任务取消必须传播到 Agent session、workspace 和外部工具。
- 平台重启后必须能从持久化任务状态和 workspace 状态恢复。

### 8.3 风险触发器

以下情况默认触发 Review Gate：

- Agent 请求新增工具、扩大路径、连接外部系统或提高权限。
- 修改 harness、策略、部署配置、生产配置、身份权限或安全相关文件。
- 准备向外部系统写入，例如创建 PR、修改 issue 状态、发送消息、发布变更。
- 检测到敏感数据、密钥、客户数据或越权访问风险。
- 评测失败但 Agent 建议继续。
- 任务成本、运行时长、重试次数或输出大小超过阈值。

## 9. Agent 会话追踪

### 9.1 Trace 内容

每个 session trace 至少包含：

- session 元数据：任务、项目、harness version、Agent、模型、开始结束时间。
- 消息流：用户输入、系统指令摘要、Agent 回复、人工补充。
- 状态事件：启动、计划、工具调用、文件修改、审查、重试、完成。
- 技能调用：技能名、版本、输入摘要、输出摘要、耗时、错误。
- 工具调用：连接器、动作、权限、输入摘要、输出摘要、审批状态。
- 文件变更：路径、diff、变更原因、测试结果。
- 配置变更：harness diff、策略 diff、审批记录。
- 资源指标：token、成本、耗时、限流、沙箱资源。
- 风险标记：策略命中、敏感数据检测、失败原因。

### 9.2 展示原则

- 默认展示摘要和关键 diff，允许展开原始事件。
- 敏感内容默认脱敏，只有具备权限的用户可查看原文。
- 对外部工具写入和权限变化做视觉强调。
- 对 Agent 解释、真实 diff、测试结果分开展示，避免用户把解释当事实。
- 支持按状态、技能、文件、风险、审查动作过滤时间线。

## 10. 系统架构

### 10.1 服务边界

- Web App：项目、任务、harness、session、审查和管理 UI。
- API Gateway：认证、授权、租户上下文、请求限流、审计入口。
- Project Service：项目、成员、角色、设置和数据分类。
- Harness Service：配置草稿、schema 校验、版本发布、diff 和回滚。
- Task Orchestrator：任务队列、调度、状态机、重试、取消和恢复。
- Agent Runtime：首版使用容器沙箱启动 Agent 会话，负责流式事件、workspace 和工具策略；后续可扩展其他 runtime。
- Connector Service：统一外部系统连接、凭证代理、最小权限令牌和审计。
- Review Service：审查规则、待办、通知、升级和审查动作。
- Trace Service：session events、日志、diff、指标和可回放时间线。
- Eval Service：任务验收、回归评测、质量信号和发布门禁。
- Admin Service：组织、RBAC、策略、模型、技能和保留周期。

### 10.2 逻辑分层

- Policy Layer：harness、组织策略、项目策略、审查规则。
- Configuration Layer：schema、默认值、版本、校验、发布。
- Coordination Layer：任务编排、调度、并发、重试、恢复。
- Execution Layer：workspace、Agent session、技能和工具调用。
- Integration Layer：GitHub、issue tracker、文档、消息、CI、存储等连接器。
- Observability Layer：trace、日志、指标、审计、成本和告警。

### 10.3 数据存储

- 关系型数据库：项目、成员、RBAC、harness 版本、任务、审查和审计索引。
- 对象存储：session trace 原始事件、文件 diff、日志片段、产物和导出包。
- 搜索索引：任务、会话、审计事件和 harness 文档检索。
- 队列：任务调度、事件处理、通知、评测和异步导出。
- 密钥管理：连接器凭证、短期令牌和加密密钥。

## 11. 核心数据模型

### 11.1 Project

- `id`
- `tenant_id`
- `name`
- `description`
- `owner_user_id`
- `status`
- `data_classification`
- `default_harness_version_id`
- `created_at`
- `updated_at`

### 11.2 HarnessVersion

- `id`
- `tenant_id`
- `project_id`
- `version_number`
- `status`
- `parent_version_id`
- `config_hash`
- `config_blob_uri`
- `change_summary`
- `risk_summary`
- `created_by`
- `approved_by`
- `published_at`

### 11.3 Task

- `id`
- `tenant_id`
- `project_id`
- `task_type`
- `source`
- `title`
- `status`
- `priority`
- `created_by`
- `harness_version_id`
- `review_policy_id`
- `input_blob_uri`
- `result_blob_uri`
- `created_at`
- `started_at`
- `completed_at`

### 11.4 AgentSession

- `id`
- `tenant_id`
- `project_id`
- `task_id`
- `attempt_number`
- `agent_template_id`
- `model_policy_id`
- `status`
- `workspace_id`
- `trace_uri`
- `input_tokens`
- `output_tokens`
- `cost`
- `started_at`
- `ended_at`

### 11.5 ReviewRequest

- `id`
- `tenant_id`
- `project_id`
- `task_id`
- `session_id`
- `gate_type`
- `risk_summary`
- `status`
- `assigned_to`
- `required_role`
- `decision`
- `decision_reason`
- `decided_by`
- `due_at`
- `created_at`

### 11.6 AuditEvent

- `id`
- `tenant_id`
- `actor_type`
- `actor_id`
- `resource_type`
- `resource_id`
- `action`
- `decision`
- `ip_address`
- `metadata`
- `created_at`

审计事件中的 `metadata` 必须避免默认写入敏感原文，必要时只保存摘要、hash、引用和脱敏字段。

## 12. 安全与治理

### 12.1 授权

- 所有 API 必须执行租户、项目、角色和资源级授权。
- Agent 工具调用必须使用任务级短期能力令牌。
- 权限提升必须通过 Review Gate。
- Harness 中的工具策略不能扩大到组织策略之外。
- 外部连接器按最小权限授权，支持按项目和任务范围收敛。

### 12.2 数据保护

- Trace 原文保留一年；Trace、日志和 diff 默认脱敏敏感数据。
- 明确区分 secret、客户数据、内部数据和公开数据。
- token、secret、credential、API key 等高密信息默认只展示脱敏值、摘要或引用。
- 禁止把 secret 写入 harness、prompt、日志或审计 metadata。
- 高敏项目可配置更短保留周期、更严格审查和更低 trace 详细度。
- 导出任务结果必须记录审计事件并检查数据分类。

### 12.3 Prompt Injection 与工具安全

- 外部输入必须作为 untrusted context 注入，不能覆盖系统策略。
- Harness policy 与工具 allowlist 必须由平台强制执行，而不是只靠 prompt。
- 工具调用前进行参数校验、路径校验、权限校验和敏感写入检测。
- Agent 不得读取未授权路径，不得把未授权数据传给外部工具。
- 高影响写入默认先进入 Review Gate。

### 12.4 审计

必须审计：

- 登录、成员、角色和项目权限变更。
- Harness 草稿创建、编辑、发布、回滚和归档。
- 任务创建、取消、重试、状态转换和结果导出。
- Agent 工具调用、外部写入、权限提升和策略命中。
- 审查请求、审查决定、超时升级和人工接管。

## 13. 可观测性与运营

### 13.1 指标

- 任务成功率、失败率、取消率、超时率。
- 队列等待时间、执行时间、审查等待时间。
- Agent session token 用量、成本、模型供应商、模型路由分布和限流。
- 评测通过率、人工驳回率、返工率。
- 工具调用成功率、连接器错误率、外部写入次数。
- 安全策略命中、敏感数据检测、权限提升请求。

### 13.2 告警

- 任务队列积压。
- 调度器或 Agent runtime 无心跳。
- 连接器认证失败或错误率升高。
- 审查请求超时。
- 成本异常增长。
- 高风险策略频繁命中。
- Trace 或审计写入失败。

### 13.3 运行手册

首版至少需要：

- 任务卡住处理。
- Agent session 取消和重试。
- Harness 发布回滚。
- 连接器凭证轮换。
- 敏感数据泄露响应。
- 审计导出。
- 成本异常处置。

## 14. MVP 范围

### 14.1 MVP 必做

- 项目创建、成员管理和基础 RBAC。
- YAML harness 草稿、校验、发布、版本列表、diff 和回滚。
- 一次性下发任务。
- GitHub 事件任务来源，优先支持 issue、PR 和仓库事件。
- 一个定时任务类型。
- Agent session trace 时间线。
- 文件 diff 和工具调用展示。
- 基础 Review Gate 和站内通知。
- 容器沙箱 Agent runtime。
- Harness 配置的外部写入策略。
- OpenAI 和 Anthropic 协议模型兼容，以及 harness 配置的模型路由。
- 审计日志。
- 任务队列、状态机、重试、取消。
- 基础指标和错误日志。

### 14.2 MVP 可延后

- 复杂工作流画布。
- 多 Agent 自动协作编排。
- 跨项目 harness 继承。
- 高级成本预测。
- 自定义报表。
- 多区域部署。
- 面向外部客户的共享门户。

### 14.3 首个垂直切片建议

选择“GitHub issue 到 Agent 实现计划/小改动 PR”的工程项目场景：

1. 项目 owner 配置 GitHub 连接器和 harness。
2. 用户从 issue 触发一次 Agent 任务。
3. Agent 在容器沙箱 workspace 中分析 issue，生成计划并修改文件。
4. 平台展示 session trace、文件 diff、技能调用和测试结果。
5. 用户通过站内通知进入审查；是否允许创建 PR 由 harness 外部写入策略决定。
6. 全流程生成审计日志和任务指标。

该切片覆盖项目、harness、事件、任务、Agent session、文件 diff、审查和审计，是验证平台主干最小但足够完整的路径。

## 15. 成功指标

- 任务从创建到可审查结果的中位时间。
- 首次任务成功率和重试后成功率。
- 人工审查通过率、驳回率和要求修改率。
- Harness 发布失败率和回滚率。
- Agent 生成变更被合并或采纳的比例。
- 每个项目每周活跃任务数。
- 高风险策略命中后的正确拦截率。
- 审计事件完整率。
- 单任务平均成本和异常成本事件数。

## 16. 风险与待决策

### 16.1 风险

- Agent 获得过宽工具权限导致数据泄露或错误写入。
- Trace 和日志记录敏感业务数据。
- Harness 版本与任务执行绑定不严格，导致历史不可追溯。
- 审查提醒过多造成疲劳，过少又无法拦截高风险动作。
- 多人项目中 owner、reviewer 和 executor 责任不清。
- 外部连接器失败导致任务状态不一致。
- 成本和运行时长缺少硬限制。
- Harness 允许直接外部写入后，策略校验、审查和回滚路径不足会放大误操作影响。
- 兼容多模型协议后，不同供应商的能力、上下文长度、工具调用语义和数据处理承诺可能不一致。

### 16.2 已决策

- 首个事件源选择 GitHub。
- Harness 底层配置使用 YAML。
- Agent runtime 首版使用容器沙箱，后续保留扩展空间。
- Trace 原文保留一年，token 类高密信息默认脱敏显示。
- 审查通知首版仅使用站内通知。
- 任务结果是否直接写入由用户在 harness 中配置。
- 模型供应商兼容 OpenAI 和 Anthropic 协议模型，模型路由策略由用户在 harness 中配置。

### 16.3 剩余待决策

- Web 技术栈与托管平台。
- 身份提供方与 RBAC 单一事实源。
- GitHub 连接器首批事件范围、权限范围和审批流程。
- 容器沙箱的隔离级别、网络策略、资源限额和镜像治理。
- Harness 中外部写入策略的默认值、风险分级和强制审查条件。
- OpenAI/Anthropic 兼容模型的组织级 allowlist、预算上限和数据处理要求。
- 高敏项目是否允许配置短于一年的 trace 原文保留周期。

## 17. Definition of Done

产品总设计进入实现前，需要完成：

- 首个 MVP 用户故事和验收标准。
- Harness schema v1。
- 任务状态机和 Review Gate 状态机。
- RBAC 权限矩阵。
- 威胁模型和数据分类策略。
- 首个连接器设计。
- Trace event schema。
- MVP 原型或页面流程图。
- 发布、回滚、审计和运行手册草案。
