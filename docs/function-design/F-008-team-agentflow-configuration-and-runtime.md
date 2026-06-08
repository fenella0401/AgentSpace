# F-008-团队空间 AgentFlow 配置与任务运行 功能设计

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 功能编号 | F-008 |
| 功能名称 | 团队空间 AgentFlow 配置与任务运行 |
| 所属功能树节点 | AgentSpace / AgentFlow 与任务运行 |
| 关联版本 | R-0630 |
| 状态 | 草稿 |
| 最近更新 | 2026-06-08 |

## 变更摘要

- 本次变更：将多 Agent 标准作业能力统一命名为 AgentFlow，独立承接定义配置、画布编辑、测试发布、任务创建、Run 状态机、运行清单和 Agent 任务会话关联。
- 核心口径：
  - AgentFlow 是结构化多 Agent 任务定义，不是通用流程编排器，也不作为 prompt 交给某个 Agent 自由执行。
  - AgentFlow 图只编排 Agent 任务节点和任务依赖；不提供条件、人工确认、并行、子流程、输出等独立节点类型。
  - 每个 Agent 任务内部声明目标 Agent、执行指令、输入、输出、用户审阅策略、重试、超时和失败策略。
  - 用户审阅是 Agent 任务自身的 `waiting_review` 运行状态和 `AgentTaskReview` 记录，只在 Agent 任务详情中配置和处理。
  - AgentFlowEngine 根据任务依赖确定性调度 Agent 任务，并负责版本固定、重试、审阅等待与恢复、暂停恢复和取消。
  - 用户在统一开始作业入口输入任务并选择已发布 AgentFlow，即创建并运行一条 AgentFlowTask。
  - Agent 任务执行时由系统自动创建 `source=agent_flow_node` 的 Agent 会话；该会话详情复用 F-006 展示能力。
- 影响范围：产品定义、功能索引、F-001、F-005、F-006、F-009、R-0630 发布计划、TC-005、TC-006、TC-008 和 TC-009。

## PRD 设计

### 用户目标

- 团队创建者或团队管理员可以通过自然语言生成 AgentFlow 草稿，并在可视化画布中编排多个 Agent 任务及其依赖。
- 团队创建者或团队管理员可以在每个 Agent 任务中配置目标 Agent、执行指令、输入、输出、用户审阅、重试、超时和失败策略。
- 团队创建者或团队管理员可以测试、查看 diff 并发布 AgentFlow 定义，确保只有结构正确、依赖完整且模拟通过的版本可被启动。
- 创建者、管理员和团队成员可以在开始作业入口输入任务并选择已发布 AgentFlow，启动 AgentFlowTask 并选择可选知识域。
- AgentFlowEngine 可以按 Agent 任务依赖确定性执行 AgentFlowRun，维护 Run 与 Agent 任务状态，支持重试、审阅等待与恢复、暂停恢复和取消。
- 每个 Agent 任务运行时自动创建 Agent 会话，作为原子 Agent 执行记录进入 F-006 Agent 会话历史。
- 用户可以在 AgentFlow 任务清单和详情中查看 Run 图、Agent 任务状态、输入输出、审阅事项、失败原因和关联 Agent 会话。
- 用户通过 AgentFlow 任务页处理 Agent 任务审阅、取消和重试；自动创建的 Agent 会话默认不可直接继续、改名、取消或重试。

### Use Story 拆分

| US 编号 | Use Story | 用户角色 | 用户价值 | 生成时间 | 交付版本 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| US-008-001 | 作为团队管理员，我希望创建并发布只编排 Agent 任务的 AgentFlow，以便沉淀可重复执行的多 Agent 标准作业。 | 团队创建者、团队管理员 | 标准化复杂工作 | 2026-06-08 | R-0630 | AI 只生成草稿，不直接发布 |
| US-008-002 | 作为团队管理员，我希望在每个 Agent 任务中声明输入、输出、审阅和失败处理，并在发布前完成校验和模拟，以便避免错误配置影响真实任务。 | 团队创建者、团队管理员 | 降低运行风险 | 2026-06-08 | R-0630 | AgentFlow 图不增加审阅或输出节点 |
| US-008-003 | 作为团队成员，我希望在开始作业入口输入任务并选择已发布 AgentFlow，以便用同一个入口运行标准任务。 | 创建者、管理员、团队成员 | 执行标准流程 | 2026-06-08 | R-0630 | 创建 AgentFlowTask |
| US-008-004 | 作为 AgentFlow 任务创建人，我希望处理 Agent 任务审阅、取消和重试，以便控制流程继续或终止。 | AgentFlow 任务创建人 | 可控恢复与干预 | 2026-06-08 | R-0630 | 审阅属于 Agent 任务运行态 |
| US-008-005 | 作为团队空间用户，我希望查看 AgentFlow 任务清单和详情，以便理解流程进度、Agent 任务结果和失败原因。 | 创建者、管理员、团队成员、访客 | 流程透明可追踪 | 2026-06-08 | R-0630 | 访客只读 |
| US-008-006 | 作为平台后端，我希望每个 Agent 任务自动创建 Agent 会话，以便任务执行详情与普通会话统一查看和审计。 | AgentSpace 后端 | 统一执行记录 | 2026-06-08 | R-0630 | `source=agent_flow_node` |

### Use Case 编写

| UC 编号 | 关联 US | Use Case | 参与者 | 前置条件 | 业务流程 | 后置结果 |
| --- | --- | --- | --- | --- | --- | --- |
| UC-008-001 | US-008-001、US-008-002 | 创建并发布 AgentFlow 定义 | 团队管理员、AgentSpace 后端 | 用户具备 AgentFlow 配置权限；被引用 Agent 已发布 | 1. 用户描述目标工作过程。<br>2. 系统生成只包含 Agent 任务和依赖关系的结构化草稿。<br>3. 用户在画布确认 Agent 任务及依赖。<br>4. 用户在各 Agent 任务属性中配置目标 Agent、执行指令、输入输出、审阅、重试、超时和失败策略。<br>5. 系统拒绝非 Agent 任务节点，并校验图结构、输入输出映射、依赖和幂等风险。<br>6. 用户配置测试输入、Agent 任务 Mock、预期状态和输出断言。<br>7. 系统执行隔离模拟运行。<br>8. 测试通过后展示 diff，用户确认发布。 | AgentFlowDefinition 进入已发布状态，可被启动 |
| UC-008-002 | US-008-003 | 从开始作业入口启动 AgentFlow 任务 | 团队成员、AgentSpace 前端、AgentSpace 后端、AgentFlowEngine | 用户具备任务创建权限；AgentFlow 已发布且依赖可用 | 1. 用户进入开始作业入口。<br>2. 输入任务说明。<br>3. 将执行目标切换为 AgentFlow。<br>4. 选择已发布 AgentFlow，并选择可选 `knowledgeDomainIds`。<br>5. 后端校验权限、任务说明、输入 Schema、知识域和依赖可用性。<br>6. 固定 AgentFlow 定义及依赖版本快照。<br>7. 创建 AgentFlowTask 和 AgentFlowRun。<br>8. AgentFlowEngine 调度首个就绪 Agent 任务。 | AgentFlowTask 进入运行中并跳转任务详情；不创建 `source=manual` 的 AgentSession |
| UC-008-003 | US-008-006 | 执行 Agent 任务并创建 Agent 会话 | AgentFlowEngine、agent core、AgentSpace 后端 | AgentFlowAgentTaskRun 进入 `ready` 状态 | 1. Engine 根据依赖计算可运行 Agent 任务。<br>2. 后端按初始输入和上游已确认输出生成任务输入。<br>3. 后端为 Agent 任务创建 `source=agent_flow_node` 的 AgentSession 和 HarnessSnapshot。<br>4. AgentSession 关联 agentFlowTaskId、agentFlowRunId 和 agentFlowAgentTaskRunId。<br>5. 调用 agent core 执行 Agent 任务。<br>6. 保存 Agent 会话事件、任务输入输出、尝试次数和状态。<br>7. 无需审阅时任务完成并解锁下游；需要审阅时进入 `waiting_review`。 | Agent 任务执行详情可在 F-006 Agent 会话详情中查看 |
| UC-008-004 | US-008-004 | 审阅 Agent 任务输出并恢复 AgentFlow | AgentFlow 任务创建人、AgentFlowEngine | Agent 任务 reviewPolicy 要求用户审阅，且 AgentFlowAgentTaskRun 为 `waiting_review` | 1. Agent 任务详情展示输出摘要、修改内容、审阅要求和反馈输入。<br>2. 创建人提交通过或退回修改。<br>3. 后端按幂等键保存 AgentTaskReview。<br>4. 通过时 Agent 任务完成并解锁下游任务。<br>5. 退回时保存反馈，并按任务重试策略创建新的执行尝试。 | AgentFlow 继续运行、保持待审阅、失败或取消 |
| UC-008-005 | US-008-004 | 取消或重试 AgentFlow | AgentFlow 任务创建人、AgentFlowEngine | AgentFlow 未完成，且目标状态允许操作 | 1. 创建人取消 Run，或对可重试失败/退回的 Agent 任务发起重试。<br>2. 后端校验权限、Run 状态、Agent 任务状态和幂等策略。<br>3. 取消时停止后续调度。<br>4. 重试时复用任务幂等策略并创建新的 Agent 任务尝试。 | Run 取消，或从目标 Agent 任务继续；已完成外部副作用不被静默撤销 |
| UC-008-006 | US-008-005 | 查看 AgentFlow 任务清单和详情 | 空间用户、AgentSpace 后端 | 用户具备团队空间访问权限 | 1. 用户进入 AgentFlow 任务清单。<br>2. 后端按权限返回任务名称、创建人、状态、AgentFlow 名称和最新更新时间。<br>3. 用户进入详情。<br>4. 前端展示只含 Agent 任务的 Run 图。<br>5. 用户选择 Agent 任务后，在任务详情中查看输入输出摘要、审阅事项、错误诊断和关联 Agent 会话入口。 | 用户可理解流程进度；访客只读 |

### 业务规则

- 概念边界：
  - Agent 会话是原子 Agent 执行记录，来源为 `manual`、`agent_flow_node` 或 `event_source`。
  - AgentFlow 配置是结构化多 Agent 任务定义，不进入 Agent prompt 让模型自行解释流程。
  - AgentFlowTask 是用户或事件源启动的流程实例，包含 AgentFlowTask、AgentFlowRun 和多个 AgentFlowAgentTaskRun。
  - AgentFlow Agent 任务自动创建的 Agent 会话展示在 Agent 会话历史中，但父级控制动作归 AgentFlow 任务页。
- AgentFlow 配置：
  - AgentFlow 图只允许 Agent 任务节点；条件、人工确认、并行、子流程、输出、Skill 和 Tool 均不是节点类型。
  - 多个 Agent 任务同时满足依赖时，Engine 可按并发上限并发执行，但不需要并行节点。
  - `AgentFlowAgentTaskDefinition` 必须声明任务名称、Agent 版本、执行指令、输入 Schema 与映射、输出 Schema 与映射、reviewPolicy、retryPolicy、timeout 和 failurePolicy。
  - Agent 任务输入可引用 AgentFlow 初始输入和已完成上游 Agent 任务的已确认输出。
  - 用户审阅属于 Agent 任务内部策略；配置画布和 Run 图不显示独立审阅节点。
  - Skill 和 Tool 是 Agent 执行过程中的能力或依赖，由 F-001 的 Agent 配置引用。
  - 发布前必须通过图结构校验、依赖校验、输入输出映射校验、模拟运行和 diff 确认。
- AgentFlow 运行：
  - 用户启动入口复用 F-005 开始作业入口；本功能提供可启动 AgentFlow 列表、输入 Schema、Agent 任务摘要、依赖状态和创建任务接口。
  - 用户输入的任务说明保存为 AgentFlowTask 的 `taskInstruction`，并按 AgentFlow 输入 Schema 映射为初始输入。
  - AgentFlow 启动时固定定义和依赖版本，运行中不切换。
  - AgentFlowTask 状态为等待执行、运行中、等待输入、待审阅、完成、失败、已取消。
  - AgentFlowRun 状态为 `pending`、`running`、`waiting_input`、`waiting_review`、`completed`、`failed`、`cancelled`。
  - AgentFlowAgentTaskRun 状态为 `pending`、`ready`、`running`、`waiting_input`、`waiting_review`、`completed`、`failed`、`skipped`、`cancelled`。
  - 每个 Agent 任务运行必须创建一个 `source=agent_flow_node` 的 AgentSession；任务重试创建新的 AgentTaskRun attempt 和 AgentSession，并保留重试关系。
  - 只有已完成且审阅通过的 Agent 任务输出可以解锁并传递给下游任务。
  - 自动创建的 Agent 会话不可被用户从 Agent 会话详情中直接继续、改名、取消或重试。
- 权限与审计：
  - 创建者、管理员和团队成员可以启动已发布 AgentFlow；访客只读。
  - 首版只有 AgentFlow 任务创建人可以处理 Agent 任务审阅、取消和重试；事件源触发的任务由触发规则记录的审计主体处理。
  - AgentFlow 不能绕过当前用户、空间、Agent、Tool 和知识域权限。
  - 定义发布、任务启动、Agent 任务状态、Agent 会话关联、审阅决定、取消和重试均需审计。

### 边界与异常

- AgentFlow 图存在非 Agent 任务节点、不可达任务、无终点、循环依赖、缺失输入映射、输出 Schema 不兼容或非幂等重试风险时不能发布。
- 选择已停用、依赖失效或无权访问的 AgentFlow 时，系统阻止启动并保留输入草稿。
- `knowledgeDomainIds` 包含不存在、跨空间或无权访问的知识域时，系统阻止启动并定位无效项。
- Agent 任务失败时，AgentFlow 按该任务的重试或终止策略处理。
- Agent 任务缺失输入、输出不满足 Schema 或上游输出未通过审阅时，任务不得进入 `ready`。
- AgentTaskReview 重复提交同一幂等键时返回已有决定，不重复推进状态机。
- 审阅退回后达到最大尝试次数，Agent 任务和 Run 进入失败；超时按 reviewPolicy 的超时策略处理。
- AgentFlow 取消后不再调度新 Agent 任务；已经完成的外部副作用不被静默撤销。
- 事件流中断或服务重启时，已保存 AgentFlowRun、AgentFlowAgentTaskRun、AgentTaskReview 和 AgentSession 不丢失。

### PRD Review 结论

- 合理性：AgentFlow 只负责确定性多 Agent 任务编排，和 F-005 手工会话、F-006 会话详情、F-009 事件触发边界清晰。
- 一致性：命名已统一为 AgentFlow、AgentFlowTask、AgentFlowRun、AgentFlowAgentTaskRun 和 `source=agent_flow_node`。
- 已修正点：移除旧联动口径、旧数据对象命名和旧接口路径。
- 待确认点：Agent 任务默认超时、并发上限、审阅策略、事件源触发时的审计主体和 AgentCore 正式契约。

## 验收标准

### 正常路径

- 团队管理员可以创建结构化 AgentFlow 草稿，画布中只能编辑 Agent 任务和任务依赖。
- 每个 Agent 任务可以配置目标 Agent、执行指令、输入、输出、用户审阅、重试、超时和失败策略。
- AgentFlow 发布前完成图结构、依赖、输入输出映射校验、模拟运行和 diff 确认。
- 创建者、管理员和团队成员可以在开始作业入口输入任务，并选择已发布 AgentFlow 启动任务。
- AgentFlow 启动时固定 AgentFlow 定义、Agent、Skill、Tool、AGENT.md 和环境变量引用版本。
- AgentFlowEngine 按 Agent 任务依赖执行、重试、等待审阅、恢复和取消。
- 每个 Agent 任务自动创建 `source=agent_flow_node` 的 Agent 会话，并关联 AgentFlowTask、AgentFlowRun 和 AgentFlowAgentTaskRun。
- AgentFlow 任务清单展示任务名称、AgentFlow 名称、创建人、状态和最新更新时间。
- AgentFlow 任务详情展示只含 Agent 任务的 Run 图；用户选择 Agent 任务后查看审阅事项、输入输出摘要、错误诊断和关联 Agent 会话入口。

### 边界场景

- AgentFlow 修改发布后，已启动 Run 继续使用启动时版本。
- 多个 Agent 任务同时满足依赖时可以并发执行，但画布和 Run 图中不存在并行节点。
- 上游 Agent 任务需要审阅时，只有通过审阅的输出可以传给下游任务。
- AgentFlow 自动创建的 Agent 会话出现在 Agent 会话历史中，但不能从会话详情直接继续、改名、取消或重试。

### 异常场景

- AgentFlow 包含非 Agent 任务节点、图结构错误、依赖缺失、输入输出不兼容或模拟运行失败时，系统阻止发布。
- 已发布 AgentFlow 依赖失效或用户无权限时，系统阻止启动。
- Agent 任务失败、输出校验失败、审阅超时或事件流中断时，系统保存失败状态和诊断。
- 取消 AgentFlow 后不再创建新 AgentTaskRun。

### 权限场景

- 访客不能启动 AgentFlow 任务、处理 Agent 任务审阅、取消或重试。
- 非 AgentFlow 任务创建人不能处理该任务的审阅、取消或重试。
- 团队成员可以启动已发布 AgentFlow，但不能配置、测试、发布或停用 AgentFlow。
- AgentFlow 内部 Agent、Tool 和知识域访问必须按当前用户权限校验。

## UI 设计

### 是否需要刷新

- 结论：是
- 理由：需要统一 AgentFlow 命名、任务启动入口、任务清单、Run 详情、Agent 任务会话链接和任务内审阅/取消/重试控件。

### 页面与交互

- AgentFlow 配置中心：
  - 自然语言生成后进入画布，画布只提供 Agent 任务节点，任务属性使用结构化侧栏。
  - Agent 任务侧栏配置目标 Agent、执行指令、输入 Schema 与映射、输出 Schema 与映射、用户审阅策略、重试、超时和失败策略。
  - 提供图校验、模拟运行、测试断言、版本 diff、发布和停用。
- 开始作业入口中的 AgentFlow 分支：
  - 用户输入任务说明后选择 AgentFlow。
  - 展示可启动 AgentFlow、输入字段、Agent 任务步骤摘要、依赖状态和任务审阅策略摘要。
  - 支持选择 R-0630 `knowledgeDomainIds`。
  - 提交后跳转 AgentFlow 任务详情页。
- AgentFlow 任务清单：
  - 展示任务名称、AgentFlow 名称、创建人、状态和最新更新时间。
  - 支持点击进入任务详情。
- AgentFlow 任务详情：
  - Run 图只展示 Agent 任务节点、依赖连线和通用执行状态，不展示用户审阅行为或审阅标识。
  - Agent 任务详情展示当前 Agent 任务、输入、输出、尝试次数、审阅状态和错误。
  - Agent 任务提供“查看 Agent 会话详情”入口，跳转 F-006。
  - Agent 任务处于待审阅时，在该任务详情内展示输出摘要、影响、反馈输入、通过和退回修改控件。

### 状态与文案

- AgentFlow 配置状态：草稿、待补全、校验失败、测试通过、已发布、已停用。
- AgentFlow 任务状态：等待执行、运行中、等待输入、待审阅、完成、失败、已取消。
- Agent 任务审阅提示：`Agent 任务“{taskName}”的输出等待审阅。`
- Agent 任务会话提示：`该 Agent 会话由 AgentFlow Agent 任务自动创建，请回到 AgentFlow 任务页处理操作。`

## 前端设计

### 是否需要刷新

- 结论：是
- 理由：需要新增 AgentFlow 配置画布、开始作业入口目标选择、Run 图、任务内审阅、状态恢复、Agent 会话跳转和新接口。

### 页面、组件与状态

- 页面路由：
  - AgentFlow 配置中心。
  - AgentFlow 编辑与模拟运行页。
  - F-005 开始作业入口中的 AgentFlow 分支。
  - AgentFlow 任务清单页。
  - AgentFlow 任务详情页。
- 核心组件：
  - `AgentFlowCanvas`、`AgentFlowAgentTaskPanel`、`AgentFlowSimulationPanel`、`AgentFlowDiffViewer`。
  - `AgentFlowTargetPicker`、`AgentFlowInputForm`、`AgentFlowTaskList`、`AgentFlowTaskListItem`。
  - `AgentFlowRunGraph`、`AgentFlowAgentTaskDetail`、`AgentFlowAgentTaskSessionLink`。
  - `AgentTaskReviewCard`、`AgentFlowRunActions`、`AgentFlowRuntimeStatus`、`AgentFlowErrorBanner`。
- 状态管理：
  - AgentFlowDefinition 草稿、Agent 任务定义、测试结果、发布状态和依赖状态。
  - AgentFlowTask、AgentFlowRun、AgentFlowAgentTaskRun、AgentTaskReview 和 Agent 任务会话链接。
  - 事件流连接状态、断线补拉游标和幂等提交状态。

### 接口依赖与异常处理

- 查询、创建、更新、测试、发布和停用 AgentFlowDefinition。
- 向 F-005 开始作业入口提供可启动 AgentFlow、输入 Schema、Agent 任务摘要、依赖状态和预选参数。
- 从 F-005 开始作业入口创建 AgentFlowTask，查询任务清单和任务详情。
- 查询 AgentFlowRun、AgentFlowAgentTaskRun 和 AgentTaskReview。
- 提交 Agent 任务审阅决定、取消 Run 和重试 Agent 任务。
- 查询 Agent 任务关联 Agent 会话详情入口。
- 状态流中断时保留最后事件，并通过详情查询恢复。
- 选择失效或依赖失效时保留用户输入并要求重新选择。

## 后端设计

### 是否需要刷新

- 结论：是
- 理由：新增 AgentFlowDefinition 管理、AgentFlowEngine、AgentFlowTask 数据、Agent 任务状态机、AgentTaskReview、Agent 会话创建和运行恢复。

### 接口与数据

- 核心服务：
  - `AgentFlowDefinitionService`：管理 AgentFlowDefinition、版本和发布状态。
  - `AgentFlowValidationService`：执行 Schema、Agent 任务图、输入输出映射、依赖和权限校验。
  - `AgentFlowSimulationService`：执行隔离模拟运行和任务级测试断言。
  - `AgentFlowTaskService`：创建 AgentFlowTask、查询清单和详情。
  - `AgentFlowStartService`：向 F-005 开始作业入口提供可启动列表、输入 Schema、Agent 任务摘要、依赖状态和预选上下文。
  - `AgentFlowEngine`：按依赖调度 Agent 任务，处理并发上限、重试、审阅等待与恢复、暂停和取消。
  - `AgentFlowAgentTaskReviewService`：保存审阅决定和反馈，并按任务策略推进或重试。
  - `AgentFlowAgentSessionService`：为 Agent 任务创建并关联 AgentSession。
  - `AgentCoreAdapter`：执行 Agent 任务并接收事件。
  - `PermissionService`、`AuditService`。
- 核心数据对象：
  - `AgentFlowDefinition`：输入输出、Agent 任务定义、任务依赖、全局失败策略和版本。
  - `AgentFlowAgentTaskDefinition`：任务标识、名称、Agent 版本、执行指令、输入 Schema 与映射、输出 Schema 与映射、reviewPolicy、retryPolicy、timeout 和 failurePolicy。
  - `AgentFlowTask`：任务 ID、AgentFlow 版本、任务说明、发起来源、创建人、状态和最新更新时间。
  - `AgentFlowRun`：Run ID、任务、状态、输入输出和时间。
  - `AgentFlowAgentTaskRun`：Agent 任务、attempt、状态、输入输出、审阅状态、幂等键、重试次数、关联 AgentSession ID 和错误。
  - `AgentTaskReview`：AgentTaskRun ID、输出版本、审阅状态、决定、反馈、操作者、超时和幂等键。
  - `AgentSession`：`source=manual | agent_flow_node | event_source`，可关联 `agentFlowTaskId`、`agentFlowRunId`、`agentFlowAgentTaskRunId`。
  - `HarnessSnapshot`：`knowledgeDomainIds`、AGENT.md、Agent、Skill、Tool、AgentFlow 和环境变量引用版本。
- 建议接口：
  - `GET /team-spaces/{spaceId}/agent-flows`
  - `POST /team-spaces/{spaceId}/agent-flows`
  - `POST /team-spaces/{spaceId}/agent-flows/{agentFlowId}/test`
  - `POST /team-spaces/{spaceId}/agent-flows/{agentFlowId}/publish`
  - `GET /team-spaces/{spaceId}/agent-flows?startable=true`
  - `GET /team-spaces/{spaceId}/agent-flows/{agentFlowId}/start-context`
  - `POST /team-spaces/{spaceId}/agent-flow-tasks`
  - `GET /team-spaces/{spaceId}/agent-flow-tasks`
  - `GET /team-spaces/{spaceId}/agent-flow-tasks/{taskId}`
  - `POST /team-spaces/{spaceId}/agent-flow-runs/{runId}/agent-tasks/{agentTaskRunId}/reviews`
  - `POST /team-spaces/{spaceId}/agent-flow-runs/{runId}/cancel`
  - `POST /team-spaces/{spaceId}/agent-flow-runs/{runId}/agent-tasks/{agentTaskRunId}/retry`

### 业务规则、权限与异常处理

- 所有 AgentFlowDefinition、AgentFlowTask、AgentFlowRun、AgentFlowAgentTaskRun、AgentTaskReview 和 AgentSession 必须归属同一租户与团队空间。
- 用户启动 AgentFlowTask 时必须从开始作业入口提交 taskInstruction 和 agentFlowId；后端不依赖入口路由做权限判断。
- AgentFlowTask 启动前必须先保存 AgentFlowTask、AgentFlowRun 和快照，再调用 Engine 调度。
- Agent 任务调用 agent core 前必须先创建 `source=agent_flow_node` 的 AgentSession。
- AgentFlowEngine 只根据持久化任务依赖、任务状态和已确认输出调度。
- Agent 任务审阅决定提交后按 AgentTaskReview 推进对应 AgentFlowAgentTaskRun。
- Tool 调用权限、Agent 权限和知识域权限必须按当前用户或事件源审计主体校验。
- 取消 AgentFlow 不自动撤销已完成外部副作用，补偿只能按已配置策略执行。

## 评审与会签

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认 Agent 任务审阅策略、任务清单筛选、任务命名和取消补偿口径。 |
| UI | 待确认 | 需输出 AgentFlow 画布、任务清单、Run 图、任务内审阅和 Agent 会话链接交互稿。 |
| 前端 | 待确认 | 需确认事件订阅、Run 状态恢复、AgentTaskReview、Agent 会话跳转和幂等提交。 |
| 后端 | 待确认 | 需确认 AgentFlowEngine、AgentTaskRun、agent core 任务执行和错误码契约。 |
| 测试 | 待确认 | 需按 TC-008 覆盖配置发布、Agent 任务状态机、任务内审阅、Agent 会话、恢复和权限。 |
