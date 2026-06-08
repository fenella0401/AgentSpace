# F-005-团队空间 Agent 会话创建与运行 功能设计

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 功能编号 | F-005 |
| 功能名称 | 团队空间 Agent 会话创建与运行 |
| 所属功能树节点 | AgentSpace / Agent 会话与对话协作 |
| 关联版本 | R-0630 |
| 状态 | 草稿 |
| 最近更新 | 2026-06-08 |

## 变更摘要

- 本次变更：统一使用“Agent 会话”表达原子 Agent 执行记录，并将新建 Agent 会话处升级为统一“开始作业”入口；用户输入任务后可以选择 Agent 或已发布 AgentFlow。
- 职责口径：
  - F-005 负责统一开始作业入口、目标选择交互和手工 Agent 会话分支。
  - 选择 AgentFlow 时，前端调用 F-008 创建 AgentFlowTask，不创建 `source=manual` 的 AgentSession。
  - 事件源触发 Agent 会话由 F-009 创建 `source=event_source` 的 AgentSession。
  - F-005 不创建 AgentFlowTask，不展示 AgentFlowRun 图、Agent 任务审阅、取消或任务重试。
- 影响范围：开始作业入口、Agent 会话创建、Harness 快照、agent core 会话协议、会话命名、询问回答、TC-005、F-006 和发布计划。

## PRD 设计

### 用户目标

- 创建者、管理员和团队成员可以在同一个开始作业入口输入任务，并选择“Agent”或“AgentFlow”作为执行目标。
- 零配置空间可以直接使用平台默认 Agent 创建手工 Agent 会话。
- 用户可以选择已发布 Agent 和可选知识域创建手工 Agent 会话。
- 系统为每次用户输入固定 AGENT.md、Agent、Skill、Tool 和环境变量引用版本。
- 手工 Agent 会话创建人可以继续本人创建的历史手工会话。
- Agent 询问时，创建人可以在会话中回答并恢复执行。
- 用户可以看到手工 Agent 会话关键状态、错误原因和最终输出。

### Use Story 拆分

| US 编号 | Use Story | 用户角色 | 用户价值 | 生成时间 | 交付版本 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| US-005-001 | 作为团队成员，我希望输入第一条指令创建 Agent 会话，以便让 Agent 开始处理任务。 | 创建者、管理员、团队成员 | 开始手工 Agent 作业 | 2026-06-04 | R-0630 | 默认使用平台默认 Agent |
| US-005-002 | 作为团队成员，我希望选择已发布 Agent 和知识域，以便让任务使用合适的职责与上下文。 | 创建者、管理员、团队成员 | 精准加载能力 | 2026-06-08 | R-0630 | 选择已发布 Agent |
| US-005-003 | 作为对话创建人，我希望继续本人创建的手工会话，以便围绕同一上下文持续推进。 | 对话创建人 | 支持持续协作 | 2026-06-04 | R-0630 | 仅限 `source=manual` |
| US-005-004 | 作为对话创建人，我希望回答 Agent 询问，以便补充执行所需信息。 | 对话创建人 | 解除执行卡点 | 2026-06-04 | R-0630 | 询问来自 agent core |
| US-005-005 | 作为团队成员，我希望在开始作业入口选择 Agent 或 AgentFlow，以便用同一个输入入口启动不同执行方式。 | 创建者、管理员、团队成员 | 降低入口理解成本 | 2026-06-08 | R-0630 | 选择 AgentFlow 时由 F-008 创建任务 |
| US-005-006 | 作为团队成员，我希望看到手工 Agent 会话关键状态和错误，以便理解当前进度。 | 团队成员 | 提升运行可理解性 | 2026-06-04 | R-0630 | 不含 AgentFlowRun 图 |
| US-005-007 | 作为团队成员，我希望通过 `/` 选择 Agent 功能，以便快速选择常用能力。 | 创建者、管理员、团队成员 | 输入增强 | 2026-06-08 | R-0730 | 不进入 R-0630 |
| US-005-008 | 作为团队成员，我希望通过 `@` 引用 Harness 知识空间文件，以便指定上下文文件。 | 创建者、管理员、团队成员 | 输入增强 | 2026-06-08 | R-0730 | 不进入 R-0630 |

### Use Case 编写

| UC 编号 | 关联 US | Use Case | 参与者 | 前置条件 | 业务流程 | 后置结果 |
| --- | --- | --- | --- | --- | --- | --- |
| UC-005-001 | US-005-001、US-005-002 | 创建手工 Agent 会话 | 团队成员、AgentSpace 后端、agent core | 用户有 Agent 会话创建权限 | 1. 用户选择默认 Agent 或已发布 Agent、可选知识域并输入指令。<br>2. 后端校验 Agent、`knowledgeDomainIds` 和指令。<br>3. 保存 `source=manual` 的 AgentSession 与首轮输入。<br>4. 生成 Harness 快照和会话名称。<br>5. 调用 agent core 创建会话。 | Agent 会话进入运行中；零配置使用平台默认 Agent |
| UC-005-002 | US-005-003 | 继续手工 Agent 会话 | 对话创建人、AgentSpace 后端、agent core | 会话为 `source=manual`；会话静止且存在外部 conversation ID | 1. 创建人提交新指令。<br>2. 后端校验权限、来源和状态。<br>3. 创建新轮次和新快照。<br>4. 调用 agent core 继续会话。 | 新轮次进入运行中，历史轮次版本不变 |
| UC-005-003 | US-005-004 | 回答 Agent 询问 | 对话创建人、AgentSpace 后端、agent core | 会话轮次处于待输入；询问未过期 | 1. 创建人提交回答。<br>2. 后端校验询问归属、幂等键和权限。<br>3. 保存回答并回传 agent core。<br>4. 会话恢复运行。 | Agent 会话继续执行 |
| UC-005-004 | US-005-006 | 接收手工会话运行事件 | AgentSpace 后端、agent core | 会话运行中 | 1. 接收状态、询问、Tool 调用、对象写入、输出、完成或错误事件。<br>2. 保存事件。<br>3. 更新会话和轮次状态。 | 前端获得可理解状态和执行结果 |
| UC-005-005 | US-005-005 | 从开始作业入口选择 AgentFlow | 团队成员、AgentSpace 前端、F-008 后端 | 用户有开始作业权限；存在已发布且可启动的 AgentFlow | 1. 用户进入开始作业入口。<br>2. 输入任务说明。<br>3. 将执行目标从 Agent 切换为 AgentFlow。<br>4. 选择已发布 AgentFlow 和可选知识域。<br>5. 前端调用 F-008 创建 AgentFlowTask。 | 系统进入 AgentFlow 任务运行；不创建 `source=manual` 的 AgentSession |

### 业务规则

- 开始作业入口：
  - 执行目标包含“Agent”和“AgentFlow”。默认目标为 Agent，以保证零配置空间可直接开始作业。
  - 选择 Agent 时必须提交指令；不选择 Agent 时使用空间默认 Agent，未设置空间默认 Agent 时使用平台默认 Agent。
  - 选择 AgentFlow 时必须选择一个已发布且可启动的 AgentFlow；前端将任务说明、agentFlowId、`knowledgeDomainIds` 和幂等键提交给 F-008。
  - 选择 AgentFlow 不会创建手工 AgentSession；后续只有 AgentFlow Agent 任务运行时才由 F-008 创建 `source=agent_flow_node` 的 AgentSession。
- Agent 会话：
  - 手工 Agent 会话来源为 `manual`。
  - 事件源触发的 Agent 会话来源为 `event_source`，由 F-009 创建，F-005 只复用运行事件处理规则。
  - AgentFlow Agent 任务自动创建的 AgentSession 来源为 `agent_flow_node`，由 F-008 创建，不允许从 F-005 入口继续、改名、取消或重试。
  - 手工会话创建人可以继续本人创建的静止态手工会话。
- 快照：
  - 每次新建或继续手工 Agent 会话时生成新快照。
  - 快照固定空间级和命中知识域 AGENT.md、选中 Agent、相关 Skill、Tool 和环境变量引用。
  - 手工 Agent 会话快照不固定 AgentFlow 定义；AgentFlow Agent 任务会话的 AgentFlow 版本关系由 F-008 维护。
- 状态：
  - AgentSession 状态聚合为运行中、待输入、完成静止态、异常静止态。
  - Agent 询问映射为待输入，并展示等待类型。
  - 会话名称可由系统根据首轮输入生成，创建人可以修改手工会话名称。
- 权限与审计：
  - 创建者、管理员和团队成员可以创建手工 Agent 会话或启动 AgentFlow。
  - 访客不能创建 Agent 会话、启动 AgentFlow、继续会话或回答询问。
  - 继续、回答、改名和会话事件均需写入审计。

### 边界与异常

- 空间没有任何 Harness 基础配置时，开始作业入口默认使用平台默认 Agent。
- 用户选择已停用、依赖失效或无权访问的 Agent 时，系统阻止创建并保留输入草稿。
- 用户选择已停用、依赖失效或无权访问的 AgentFlow 时，入口保留任务说明并提示重新选择。
- `knowledgeDomainIds` 包含不存在、跨空间或无权访问的知识域时，系统阻止创建并定位无效项。
- agent core 创建、继续、回答或事件流失败时，保存结构化错误并进入异常静止态。
- 用户尝试继续、改名、取消或重试 `source=agent_flow_node` 的 Agent 会话时，系统拒绝并提示回到 AgentFlow 任务页处理。
- 询问超时、迟到或重复回答时，后端按幂等键保持单一终态。

### PRD Review 结论

- 合理性：统一开始作业入口清晰承接 Agent 和 AgentFlow 两种目标，手工会话和流程任务边界明确。
- 一致性：F-005 不创建 AgentFlowTask，不处理 AgentFlow 审阅、取消和重试，避免和 F-008 重叠。
- 已修正点：移除手工会话生命周期 Hook 运行口径，快照中不再包含 Hook。
- 待确认点：会话名称生成规则、agent core 会话协议、询问超时策略和错误码。

## 验收标准

### 正常路径

- 用户可以在开始作业入口输入任务，并在 Agent 与 AgentFlow 之间选择执行目标。
- 默认目标为 Agent；零配置空间使用平台默认 Agent 创建 `source=manual` 的 AgentSession。
- 用户可以选择已发布 Agent 和 `knowledgeDomainIds` 创建手工 Agent 会话。
- 用户选择已发布 AgentFlow 后，前端调用 F-008 创建 AgentFlowTask，不创建 `source=manual` 的 AgentSession。
- 创建手工会话和继续输入时生成包含 AGENT.md、Agent、Skill、Tool 和环境变量引用的快照。
- 手工会话创建人可以继续本人创建的静止态手工会话。
- Agent 询问出现时，会话进入待输入，创建人回答后恢复运行。
- 会话完成后展示最终输出和修改文件摘要。

### 边界场景

- 空间没有自定义 Agent 时，开始作业入口仍可使用平台默认 Agent。
- 空间没有任何已发布 AgentFlow 时，不展示可选 AgentFlow 或展示不可用空态。
- AgentFlow 任务启动后，用户回到 F-008 任务详情处理 Agent 任务审阅、取消和重试。
- 事件源创建的 `source=event_source` 会话复用手工会话事件展示，但创建入口由 F-009 负责。

### 异常场景

- 选择已停用、依赖失效或无权访问的 Agent 或 AgentFlow 时，系统阻止提交并保留任务说明。
- agent core 创建、继续或回答失败时，系统保存错误状态和诊断。
- 用户尝试继续或改名 `source=agent_flow_node` 的会话时，系统拒绝。
- 询问重复回答、超时后迟到回答或服务重启后恢复时，统一确认记录保持单一终态。

### 权限场景

- 访客不能提交开始作业，既不能创建 Agent 会话，也不能启动 AgentFlow 任务。
- 非手工会话创建人不能继续会话、回答询问或改名。
- 团队成员可以使用已发布 Agent 和 AgentFlow，但不能修改 Harness 或 AgentFlow 配置。

## UI 设计

### 是否需要刷新

- 结论：是
- 理由：R-0630 将新建 Agent 会话处调整为统一开始作业入口，需要同时承载 Agent 目标选择和 AgentFlow 目标选择。

### 页面与交互

- 开始作业入口：
  - 顶部展示执行目标选择：Agent / AgentFlow。
  - 默认选中 Agent。
  - 输入框支持任务说明。
  - 支持选择已发布 Agent 和知识域。
- AgentFlow 目标：
  - 展示可启动 AgentFlow 列表、步骤摘要、依赖状态和输入要求。
  - 从 AgentFlow 配置完成页点击“运行”时，跳转到本入口并预选对应 AgentFlow。
  - 提交后进入 F-008 AgentFlow 任务详情页。
- 会话运行页：
  - 展示运行中、待输入、完成和异常状态。
  - 待输入区分 Agent 询问。
  - 对 `source=agent_flow_node` 的会话详情展示只读来源提示和 AgentFlow 任务链接，运行控制不可用。

### 状态与文案

- 执行目标：Agent、AgentFlow。
- 会话状态：运行中、待输入、已完成、异常。
- 来源提示：`该 Agent 会话由 AgentFlow Agent 任务自动创建，请回到 AgentFlow 任务页处理操作。`
- AgentFlow 空态：`当前空间暂无可启动的 AgentFlow`

## 前端设计

### 是否需要刷新

- 结论：是
- 理由：需要统一开始作业入口、目标选择器、Agent 选择器、AgentFlow 选择器、知识域选择、询问回答和 AgentSession 新接口。

### 页面、组件与状态

- `StartWorkTargetSelector`：在 Agent 与 AgentFlow 之间切换执行目标。
- `AgentTargetPicker`：查询并选择已发布 Agent。
- `AgentFlowTargetPicker`：查询并选择 F-008 提供的可启动 AgentFlow。
- `KnowledgeDomainPicker`：选择 R-0630 `knowledgeDomainIds`。
- `AgentSessionInput`、`AgentQuestionCard`、`RuntimeStatus`、`RuntimeErrorBanner`。
- `AgentFlowAgentTaskSourceBanner`：展示只读来源提示和父任务链接。
- 状态保存会话、轮次、Agent、快照、询问和输入草稿。

### 接口依赖与异常处理

- 创建手工 Agent 会话、继续手工会话、提交 Agent 询问回答。
- 查询已发布 Agent、知识域和默认 Agent。
- 查询 F-008 可启动 AgentFlow；目标为 AgentFlow 时调用 F-008 `POST /team-spaces/{spaceId}/agent-flow-tasks`。
- 订阅或查询 Agent 会话事件。
- 创建失败时保留输入草稿；AgentFlow 选择失效时要求重新选择。

## 后端设计

### 是否需要刷新

- 结论：是
- 理由：需要 AgentSession 数据模型、手工会话接口、来源字段、统一开始作业目标路由和 agent core 会话协议。

### 接口与数据

- 核心服务：
  - `AgentSessionCommandService`：创建和继续手工 Agent 会话。
  - `StartWorkTargetService`：校验开始作业目标，并在 AgentFlow 目标下转交 F-008。
  - `HarnessSnapshotService`：生成 AGENT.md、Agent、Skill、Tool 和环境变量引用快照。
  - `AgentQuestionService`：保存询问、回答和幂等结果。
  - `AgentCoreAdapter`：执行手工 Agent 会话、继续对话、回答询问并接收事件。
  - `AuditService`。
- 核心数据对象：
  - `AgentSession`：会话 ID、来源、创建人、状态、会话名称、外部 conversation ID 和父对象引用。
  - `AgentSessionSource`：`manual`、`agent_flow_node`、`event_source`。
  - `AgentSessionTurn`：轮次 ID、输入、状态、快照 ID 和时间。
  - `HarnessSnapshot`：`knowledgeDomainIds`、AGENT.md、Agent、Skill、Tool 和环境变量引用版本。
  - `AgentQuestion`：问题、选项、状态、超时、回答、操作者和幂等键。

### 业务规则、权限与异常处理

- 所有 Agent、Skill、Tool 和环境变量依赖必须来自同一租户与团队空间的已发布版本。
- 统一开始作业入口仅负责路由目标；目标为 AgentFlow 时，F-005 不保存手工会话记录。
- AgentFlow Agent 任务会话和事件源会话不得从 F-005 入口直接继续或改名，除非对应功能明确开放。
- 所有权限判断以后端当前权限为准，快照不能授予用户原本没有的权限。

## 评审与会签

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认开始作业入口文案、默认目标、AgentFlow 来源只读提示和会话名称规则。 |
| UI | 待确认 | 需输出执行目标选择、Agent 选择、AgentFlow 选择、询问回答和来源提示交互稿。 |
| 前端 | 待确认 | 需确认目标路由、输入草稿保留、事件流和错误恢复。 |
| 后端 | 待确认 | 需确认 agent core 会话协议、询问回答和错误码。 |
| 测试 | 待确认 | 需按 TC-005 覆盖零配置、手工会话、继续、询问、来源限制和权限。 |
