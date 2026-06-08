# F-006-团队 Agent 会话历史与详情查看 功能设计

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 功能编号 | F-006 |
| 功能名称 | 团队 Agent 会话历史与详情查看 |
| 所属功能树节点 | AgentSpace / Agent 会话与对话协作 |
| 关联版本 | R-0630 |
| 状态 | 草稿 |
| 最近更新 | 2026-06-08 |

## 变更摘要

- 本次变更：将历史清单统一调整为“Agent 会话历史”，展示 `source=manual` 的手工 Agent 会话、`source=agent_flow_node` 的 AgentFlow Agent 任务自动会话和 `source=event_source` 的事件源触发会话。
- 核心口径：
  - 手工 Agent 会话由 F-005 创建。
  - AgentFlow Agent 任务会话由 F-008 AgentFlowEngine 自动创建。
  - 事件源 Agent 会话由 F-009 事件触发调度创建。
  - F-006 只提供列表、筛选、详情、执行过程、最终输出和来源链接，不提供创建、继续、审阅、取消或重试能力。
- 影响范围：Agent 会话看板、会话详情页、来源标识、AgentFlow 父任务链接、事件源来源摘要、事件查询、访客只读和 TC-006。

## PRD 设计

### 用户目标

- 用户可以查看团队空间内有权限访问的 Agent 会话历史。
- 用户可以筛选全部、我的、今天、近一周和不同来源的 Agent 会话。
- 用户可以查看 Agent 会话详情，包括输入、执行过程事件、最终输出、修改文件和错误原因。
- 用户可以识别手工、AgentFlow 和事件源三类会话来源。
- 用户可以从 AgentFlow Agent 任务会话跳回父 AgentFlow 任务详情。
- 用户可以从事件源会话看到 eDevOps FE 来源摘要，并跳回事件执行看板或外部事件摘要。
- 访客可以只读查看有权限访问的会话历史和详情。

### Use Story 拆分

| US 编号 | Use Story | 用户角色 | 用户价值 | 生成时间 | 交付版本 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| US-006-001 | 作为团队成员，我希望查看 Agent 会话历史，以便回顾团队空间中的 Agent 执行结果。 | 创建者、管理员、团队成员、访客 | 透明追踪 | 2026-06-05 | R-0630 | 访客只读 |
| US-006-002 | 作为团队成员，我希望查看 Agent 会话详情，以便理解输入、过程和最终输出。 | 创建者、管理员、团队成员、访客 | 结果可解释 | 2026-06-05 | R-0630 | 复用执行事件 |
| US-006-003 | 作为用户，我希望识别 AgentFlow Agent 任务自动创建的 Agent 会话并跳回父任务，以便理解它在流程中的上下文。 | 空间用户 | 串联流程与细节 | 2026-06-08 | R-0630 | `source=agent_flow_node` |
| US-006-004 | 作为用户，我希望识别事件源触发的 Agent 会话，以便理解它来自哪个 eDevOps FE。 | 空间用户 | 串联事件与执行 | 2026-06-08 | R-0630 | `source=event_source` |
| US-006-005 | 作为手工会话创建人，我希望历史和详情展示会话名称，以便保持历史会话易识别。 | 手工会话创建人 | 易于检索 | 2026-06-05 | R-0630 | 名称由 F-005 维护 |

### Use Case 编写

| UC 编号 | 关联 US | Use Case | 参与者 | 前置条件 | 业务流程 | 后置结果 |
| --- | --- | --- | --- | --- | --- | --- |
| UC-006-001 | US-006-001 | 查看 Agent 会话历史 | 空间用户、AgentSpace 后端 | 用户具备团队空间访问权限 | 1. 用户进入 Agent 会话历史。<br>2. 后端按权限返回会话列表。<br>3. 前端展示会话名称、来源、状态、创建人、最新更新时间和摘要。<br>4. 用户可按来源、我的、今天、近一周、关键词和分页筛选。 | 用户看到可访问的 Agent 会话历史 |
| UC-006-002 | US-006-002、US-006-003、US-006-004 | 查看 Agent 会话详情和执行过程 | 空间用户、AgentSpace 后端 | 目标会话存在且用户有权限 | 1. 用户点击会话清单项。<br>2. 后端校验会话详情访问权限。<br>3. 后端返回会话基础信息、来源、输入、执行过程事件、最终输出和修改文件。<br>4. 前端展示输入、执行过程和最终输出。<br>5. AgentFlow 来源额外展示父 AgentFlowTask、Run、Agent 任务和跳转链接。<br>6. 事件源来源额外展示 eDevOps FE 摘要、事件任务和看板链接。 | 用户看到会话详情和来源上下文 |
| UC-006-003 | US-006-005 | 查看会话名称 | 空间用户、AgentSpace 后端 | 会话存在 | 1. 后端返回会话名称和名称来源。<br>2. 前端在列表和详情中展示名称。<br>3. 手工会话名称修改后，列表和详情同步刷新。 | 会话易于识别 |

### 业务规则

- 会话来源：
  - `manual` 会话由 F-005 创建，可由创建人继续和改名。
  - `agent_flow_node` 会话由 F-008 自动创建，清单和详情展示父 AgentFlowTask、Run 和 Agent 任务链接，但不展示继续、回答、改名、取消或重试控件。
  - `event_source` 会话由 F-009 创建，清单和详情展示事件源、事件类型、eDevOps FE 标识和事件执行看板链接。
- 列表筛选：
  - 我的筛选项展示当前用户创建的手工会话、父 AgentFlow 任务由当前用户创建的 Agent 任务会话，以及审计主体为当前用户的事件源会话。
  - 今天和近一周按团队空间时区计算。
  - 来源筛选支持手工创建、AgentFlow Agent 任务和事件源触发。
- 详情展示：
  - 执行过程事件只有在 agent core 或 AgentSpace 后端判定为可展示时才写入或返回可展示内容。
  - Tool 参数、Tool 结果、文件内容和外部事件 payload 必须脱敏后展示。
  - 最终输出展示给用户的最终 output 和修改文件清单。
- 权限：
  - 访问会话详情必须校验团队空间访问权限和会话可见性。
  - 父 AgentFlow 任务或事件任务不可访问时，只展示来源标识和不可访问提示，不泄露父级详情。

### 边界与异常

- 空间无会话时展示空态。
- 会话事件尚未产生时，详情展示“暂无执行过程”。
- 事件流中断时，保留已保存事件，并允许通过详情查询补拉。
- 父 AgentFlow 任务不可访问时，仅展示来源标识和不可访问提示。
- eDevOps FE payload 含敏感字段时，只展示白名单摘要。
- 访客进入详情时只读，不展示继续、回答、改名、文档处理或运行控制控件。

### PRD Review 结论

- 合理性：F-006 只做历史和详情展示，三类会话来源均有清晰归属。
- 一致性：AgentFlow 来源统一使用 `agent_flow_node`，事件源来源统一使用 `event_source`。
- 已修正点：新增事件源会话展示口径，移除旧 AgentFlow 命名。
- 待确认点：事件源会话在“我的”筛选中的归属、eDevOps FE 摘要字段和事件详情跳转策略。

## 验收标准

### 正常路径

- 用户可以查看团队空间内有权限访问的 Agent 会话历史。
- 列表展示会话名称、来源、状态、创建人、最新更新时间和摘要。
- 来源可区分手工创建、AgentFlow Agent 任务和事件源触发。
- 用户可以查看手工 Agent 会话详情、执行过程、最终输出和修改文件。
- AgentFlow Agent 任务会话展示父 AgentFlow 任务、Run、Agent 任务链接和只读来源提示。
- 事件源触发会话展示 eDevOps FE 来源摘要、事件任务和看板链接。
- 访客可以只读查看有权限访问的列表和详情。

### 边界场景

- 无会话时展示空态。
- 父 AgentFlow 任务不可访问时，仅展示来源标识，不泄露父级详情。
- 事件任务不可访问时，仅展示事件源触发来源，不泄露 eDevOps 敏感字段。
- 执行过程为空时展示暂无执行过程，不影响最终输出展示。

### 异常场景

- 用户无权访问会话详情时，后端拒绝并返回无权限提示。
- 事件查询失败时，保留会话基础信息并提示事件加载失败。
- 事件流中断后，前端通过详情查询恢复最近事件。
- AgentFlow Agent 任务会话详情不展示继续、改名、取消或重试控件。

### 权限场景

- 团队成员可以查看有权限空间内的会话详情。
- 访客只读，不展示写操作。
- 非手工会话创建人不能改名或继续手工会话。
- 父任务链接必须二次校验可见性。

## UI 设计

### 是否需要刷新

- 结论：是
- 理由：需要新增三类来源标识、AgentFlow 父任务链接、事件源来源摘要和只读提示。

### 页面与交互

- Agent 会话历史：
  - 支持来源筛选：全部、手工创建、AgentFlow Agent 任务、事件源触发。
  - 支持我的、今天、近一周、关键词和分页。
  - 卡片展示名称、来源、状态、摘要、最新更新时间。
- Agent 会话详情：
  - 顶部展示会话名称、来源、创建人、状态和时间。
  - 手工会话展示继续和改名入口，权限不足时隐藏。
  - AgentFlow 来源展示父 AgentFlow 任务链接和只读提示。
  - 事件源来源展示 eDevOps FE 摘要和事件执行看板链接。
  - 执行过程按时间线展示可展示事件。

### 状态与文案

- 来源：手工创建、AgentFlow Agent 任务、事件源触发。
- 只读提示：`该 Agent 会话由 AgentFlow Agent 任务自动创建，请回到 AgentFlow 任务页处理操作。`
- 事件源提示：`该 Agent 会话由 eDevOps FE 事件触发。`

## 前端设计

### 是否需要刷新

- 结论：是
- 理由：需要新增来源字段、父 AgentFlow 链接、事件源摘要、只读控制和筛选项。

### 页面、组件与状态

- `AgentSessionBoard`、`AgentSessionListItem`、`AgentSessionSourceBadge`。
- `AgentSessionFilterTabs`、`AgentSessionSearchInput`、`AgentSessionPagination`。
- `AgentSessionDetail`、`AgentExecutionTimeline`、`ExecutionEventItem`。
- `AgentFlowParentLink`、`AgentFlowAgentTaskSourceBanner`。
- `EventSourceSessionBanner`、`EventExecutionBoardLink`。
- 状态保存列表筛选、分页、当前会话详情、来源上下文、事件流连接和补拉游标。

### 接口依赖与异常处理

- 查询团队空间 Agent 会话列表，支持来源、我的、关键词、页码和每页数量参数。
- 查询 Agent 会话详情、事件列表和来源上下文。
- 查询父 AgentFlow 任务链接可见性。
- 查询事件源触发上下文和看板链接。
- 事件流中断时通过详情查询补拉。

## 后端设计

### 是否需要刷新

- 结论：是
- 理由：需要提供 Agent 会话列表、详情、事件查询、来源字段、父 AgentFlow 链接和事件源来源摘要。

### 接口与数据

- 核心服务：
  - `AgentSessionQueryService`：按筛选项、关键词、来源和分页参数查询会话列表，并查询会话详情。
  - `AgentSessionEventService`：查询会话事件。
  - `AgentFlowParentLinkService`：解析 AgentFlow Agent 任务会话的父任务、Run 和 Agent 任务链接。
  - `EventSourceSessionLinkService`：解析事件源会话的事件任务、eDevOps FE 摘要和看板链接。
  - `PermissionService`。
- 核心数据对象：
  - `AgentSession`：会话 ID、租户 ID、团队空间 ID、创建人 ID、来源、会话名称、状态、最新更新时间和父对象关联。
  - `AgentSessionSource`：`manual`、`agent_flow_node`、`event_source`。
  - `AgentSessionExecutionEvent`：事件 ID、会话 ID、轮次 ID、事件类型、展示标题、展示摘要、结构化 payload、序号和创建时间。
  - `AgentSessionFinalOutput`：会话 ID、轮次 ID、最终 output、修改文件列表和创建时间。
  - `AgentFlowParentLink`：AgentFlowTask、AgentFlowRun、AgentFlowAgentTaskRun 的可见链接和摘要。
  - `EventSourceSessionLink`：EventExecutionTask、eDevOps FE ID、标题、状态和看板链接。

### 业务规则、权限与异常处理

- 查询会话列表时，返回当前团队空间内符合筛选、搜索和分页条件的 Agent 会话。
- 来源上下文必须二次校验可见性，不可见时不返回父级详情。
- 执行过程事件只有可展示内容才能返回。
- eDevOps FE payload 必须按 F-009 白名单脱敏后展示。

## 评审与会签

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认事件源会话在“我的”筛选中的归属和来源文案。 |
| UI | 待确认 | 需输出会话看板、三类来源标识、详情页、AgentFlow 链接和事件源摘要。 |
| 前端 | 待确认 | 需落实列表筛选搜索分页、来源字段、父链接和断线恢复。 |
| 后端 | 待确认 | 需确认 agent core 字段映射、事件查询、来源字段和父对象链接。 |
| 测试 | 待确认 | 需覆盖会话列表筛选搜索分页、三类来源、详情、访客只读和事件流异常。 |
