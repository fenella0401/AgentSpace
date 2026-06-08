# F-008-团队空间 Harness CICD 流水线配置与任务运行 功能设计

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 功能编号 | F-008 |
| 功能名称 | 团队空间 Harness CICD 流水线配置与任务运行 |
| 所属功能树节点 | AgentSpace / Harness CICD 流水线与任务运行 |
| 关联版本 | R-0630 |
| 状态 | 草稿 |
| 最近更新 | 2026-06-08 |

## 变更摘要

- 本次变更：将 Harness CICD 流水线从 Harness 基础配置和手工 Agent 会话运行中拆出，独立承接 Harness CICD 流水线定义配置、画布编辑、测试发布、Harness CICD 流水线任务启动、Run 状态机、运行清单和节点会话关联。
- 核心口径：
  - Harness CICD 流水线配置是结构化流水线定义，不是作为 prompt 给 Agent 自由执行的文本。
  - AI 只生成 Harness CICD 流水线结构化草稿，发布前必须人工确认、图结构校验、模拟运行和 diff 确认。
  - HarnessPipelineEngine 确定性控制节点、分支、并行、重试、人工确认、暂停恢复和取消。
  - Agent 节点执行时由系统自动创建 `source=harness_pipeline_node` 的 Agent 会话；该会话详情复用 F-006 展示能力。
- 影响范围：产品定义、功能索引、F-001、F-005、F-006、R-0630 发布计划、TC-001、TC-005、TC-006 和新增 TC-008。
- 与相邻功能的边界：
  - F-001 负责空间初始化、AGENT.md、Skill、Tool、Agent、Hook、环境变量和 `.agentspace` 基础布局；Harness CICD 流水线定义、测试和发布由本功能负责。
  - F-005 负责用户手工发起的 Agent 会话创建、继续和 Agent 询问处理。
  - F-006 负责 Agent 会话历史与详情展示，包含手工会话和 Harness CICD 流水线节点自动会话。
  - Hook 配置仍由 F-001 负责；Harness CICD 流水线生命周期 Hook 由本功能调用共享 Hook Engine。
- 待确认问题：
  - Harness CICD 流水线节点默认超时、并发上限、重试间隔和最大运行时长。
  - Harness CICD 流水线任务命名规则、运行清单默认筛选项和 Run 事件保留策略。
  - agent core 的 Agent 节点执行、事件流、对象写入前置事件和错误码正式契约。
  - Hook 触发 Harness CICD 流水线时的权限主体、审计主体和通知文案。

## PRD 设计

### 用户目标

- 团队创建者或团队管理员可以通过自然语言生成 Harness CICD 流水线草稿，并在可视化画布中确认 Agent、条件、人工确认、并行、子流水线和输出节点。
- 团队创建者或团队管理员可以测试、查看 diff 并发布 Harness CICD 流水线定义，确保只有结构正确、依赖完整且模拟通过的版本可被启动。
- 创建者、管理员和团队成员可以从已发布 Harness CICD 流水线启动 Harness CICD 流水线任务，填写输入并选择可选知识域。
- HarnessPipelineEngine 可以确定性执行 HarnessPipelineRun，维护 Run 与节点状态，支持分支、并行、重试、人工确认、暂停恢复和取消。
- 每个 Agent 节点运行时自动创建 Agent 会话，作为原子 Agent 执行记录进入 F-006 Agent 会话历史。
- 用户可以在 Harness CICD 流水线任务清单中查看 Harness CICD 流水线任务，并进入详情查看 Run 图、节点状态、确认事项、失败原因和关联 Agent 会话。
- 用户通过 Harness CICD 流水线任务页处理人工确认、取消和重试；Harness CICD 流水线自动创建的 Agent 会话默认不可直接继续或改名。

### Use Story 拆分

| US 编号 | Use Story | 用户角色 | 用户价值 | 生成时间 | 交付版本 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| US-008-001 | 作为团队管理员，我希望创建并发布结构化 Harness CICD 流水线，以便沉淀可重复执行的多 Agent 作业流程。 | 团队创建者、团队管理员 | 标准化复杂流程 | 2026-06-08 | R-0630 | AI 只生成草稿，不直接发布 |
| US-008-002 | 作为团队管理员，我希望在发布前校验和模拟 Harness CICD 流水线，以便避免错误流程影响真实任务。 | 团队创建者、团队管理员 | 降低运行风险 | 2026-06-08 | R-0630 | 覆盖分支、重试、确认和失败策略 |
| US-008-003 | 作为团队成员，我希望启动已发布 Harness CICD 流水线任务，以便按标准流程完成复杂工作。 | 创建者、管理员、团队成员 | 执行标准流程 | 2026-06-08 | R-0630 | Harness CICD 流水线任务独立于手工 Agent 会话 |
| US-008-004 | 作为 Harness CICD 流水线任务创建人，我希望处理人工确认、取消和重试，以便控制流程继续或终止。 | Harness CICD 流水线任务创建人 | 可控恢复与干预 | 2026-06-08 | R-0630 | 首版仅任务创建人可操作 |
| US-008-005 | 作为团队空间用户，我希望查看 Harness CICD 流水线任务清单和详情，以便理解流程进度、节点结果和失败原因。 | 创建者、管理员、团队成员、访客 | 流程透明可追踪 | 2026-06-08 | R-0630 | 访客只读 |
| US-008-006 | 作为平台后端，我希望每个 Agent 节点自动创建 Agent 会话，以便节点执行详情与普通会话统一查看和审计。 | AgentSpace 后端 | 统一执行记录 | 2026-06-08 | R-0630 | `source=harness_pipeline_node` |
| US-008-007 | 作为团队管理员，我希望 Hook 可以在允许的生命周期中启动 Harness CICD 流水线，以便把治理动作转为标准流程。 | 团队创建者、团队管理员 | 治理自动化 | 2026-06-08 | R-0630 | 复用 Hook Engine 和调用链限制 |

### Use Case 编写

| UC 编号 | 关联 US | Use Case | 参与者 | 前置条件 | 业务流程 | 后置结果 |
| --- | --- | --- | --- | --- | --- | --- |
| UC-008-001 | US-008-001、US-008-002 | 创建并发布 Harness CICD 流水线定义 | 团队管理员、AgentSpace 后端 | 用户具备 Harness CICD 流水线配置权限；被引用 Agent 已存在或可补充 | 1. 用户描述目标流程。<br>2. 系统生成结构化 Harness CICD 流水线草稿。<br>3. 用户在画布确认节点、连线、输入输出映射、分支、重试、确认和失败策略。<br>4. 系统校验图结构、依赖和幂等风险。<br>5. 用户配置测试输入、Mock、预期分支、节点状态和输出断言。<br>6. 系统执行隔离模拟运行。<br>7. 测试通过后展示 diff，用户确认发布。 | HarnessPipelineDefinition 进入已发布状态，可被 Harness CICD 流水线任务引用 |
| UC-008-002 | US-008-003 | 用户启动 Harness CICD 流水线任务 | 团队成员、AgentSpace 后端、HarnessPipelineEngine | 用户具备任务创建权限；Harness CICD 流水线已发布且依赖可用 | 1. 用户从 Harness CICD 流水线入口选择已发布 Harness CICD 流水线。<br>2. 填写 Harness CICD 流水线输入并选择可选 `knowledgeDomainIds`。<br>3. 后端校验权限、输入、知识域和依赖可用性。<br>4. 固定 Harness CICD 流水线定义及依赖版本快照。<br>5. 创建 HarnessPipelineTask 和 HarnessPipelineRun。<br>6. 执行 Harness CICD 流水线任务开始 Hook。<br>7. Hook 未阻止时 Engine 调度首个节点。 | Harness CICD 流水线任务进入运行中，并出现在 Harness CICD 流水线任务清单 |
| UC-008-003 | US-008-006 | 执行 Agent 节点并创建 Agent 会话 | HarnessPipelineEngine、agent core、AgentSpace 后端 | HarnessPipelineNodeRun 进入可运行状态 | 1. Engine 计算可运行 Agent 节点。<br>2. 后端为节点创建 `source=harness_pipeline_node` 的 AgentSession 和 HarnessSnapshot。<br>3. AgentSession 关联 harnessPipelineTaskId、harnessPipelineRunId 和 harnessPipelineNodeRunId。<br>4. 调用 agent core 执行 Agent 节点。<br>5. 保存 Agent 会话事件、节点输入输出和节点状态。<br>6. Engine 根据节点结果调度后续节点。 | 节点执行详情可在 F-006 Agent 会话详情中查看 |
| UC-008-004 | US-008-004 | 人工确认并恢复 Harness CICD 流水线 | Harness CICD 流水线任务创建人、HarnessPipelineEngine | Run 运行到人工确认节点或 Hook 要求确认 | 1. Run 进入等待确认。<br>2. 前端展示确认事项、影响、选项和超时信息。<br>3. 创建人提交批准或拒绝。<br>4. 后端按幂等键保存 RuntimeConfirmation。<br>5. Engine 根据确认结果恢复对应分支或终止。 | Harness CICD 流水线继续、完成、失败或取消 |
| UC-008-005 | US-008-004 | 取消或重试 Harness CICD 流水线 | Harness CICD 流水线任务创建人、HarnessPipelineEngine | Harness CICD 流水线未完成，且目标状态允许操作 | 1. 创建人取消 Run，或对可重试失败节点发起重试。<br>2. 后端校验权限、Run 状态、节点状态和幂等策略。<br>3. 取消时停止后续调度。<br>4. 重试时复用节点幂等键策略并创建新的节点尝试。 | Run 取消，或从目标节点继续，已完成外部副作用不被静默撤销 |
| UC-008-006 | US-008-005 | 查看 Harness CICD 流水线任务清单和详情 | 空间用户、AgentSpace 后端 | 用户具备团队空间访问权限 | 1. 用户进入 Harness CICD 流水线任务清单。<br>2. 后端按权限返回任务名称、创建人、状态、Harness CICD 流水线名称和最新更新时间。<br>3. 用户进入详情。<br>4. 前端展示 Run 图、节点状态、输入输出摘要、确认事项、错误诊断和关联 Agent 会话入口。 | 用户可理解 Harness CICD 流水线进度；访客只读 |
| UC-008-007 | US-008-007 | Hook 启动 Harness CICD 流水线 | Hook Engine、HarnessPipelineEngine | Hook 动作配置为启动已发布 Harness CICD 流水线，且调用链未超限 | 1. Hook Engine 命中事件和条件。<br>2. 校验目标 Harness CICD 流水线版本、权限主体、输入映射和调用链深度。<br>3. 创建 HarnessPipelineTask 和 HarnessPipelineRun，来源标记为 Hook。<br>4. Engine 按普通 Harness CICD 流水线任务执行。 | Hook 动作形成可审计 Harness CICD 流水线任务；超过调用链限制时拒绝启动 |

### 业务规则

- 概念边界：
  - Agent 会话是原子 Agent 执行记录，来源为 `manual` 或 `harness_pipeline_node`。
  - Harness CICD 流水线配置是结构化流水线定义，不进入 Agent prompt 让模型自行解释流程。
  - Harness CICD 流水线任务是用户或 Hook 启动的流水线实例，包含 HarnessPipelineTask、HarnessPipelineRun 和多个 HarnessPipelineNodeRun。
  - Harness CICD 流水线节点自动创建的 Agent 会话展示在 Agent 会话历史中，但其父级控制动作归 Harness CICD 流水线任务页。
- Harness CICD 流水线配置：
  - 用户侧节点类型为 Agent、条件、人工确认、并行、子流水线和输出。
  - Skill 和 Tool 不作为用户侧 Harness CICD 流水线节点；它们是 Agent 节点执行过程中的可用能力或依赖。
  - Harness CICD 流水线定义必须声明输入、输出、Agent 节点依赖、失败策略和可重试 Agent 节点的幂等键策略。
  - 条件、分支、并行汇总、重试和状态迁移由 HarnessPipelineEngine 计算，不交给模型判断执行走向。
  - 发布前必须通过图结构校验、依赖校验、模拟运行和 diff 确认。
- Harness CICD 流水线运行：
  - Harness CICD 流水线启动时固定定义和依赖版本，运行中不切换。
  - HarnessPipelineTask 状态为等待执行、运行中、等待输入、等待确认、完成、失败、已取消。
  - HarnessPipelineRun 状态为 `pending`、`running`、`waiting_input`、`waiting_approval`、`completed`、`failed`、`cancelled`。
  - HarnessPipelineNodeRun 状态为 `pending`、`ready`、`running`、`waiting`、`completed`、`failed`、`skipped`、`cancelled`。
  - 每个 Agent 节点运行必须创建一个 `source=harness_pipeline_node` 的 AgentSession；节点重试可以创建新的会话轮次或新的节点会话，但必须保留重试关系。
  - Harness CICD 流水线自动创建的 Agent 会话不可被用户从 Agent 会话详情中直接继续、改名或取消；用户需回到 Harness CICD 流水线任务页操作。
  - 并行节点独立保存状态；失败策略决定等待全部、快速失败或允许部分成功。
  - 子流水线、Hook 触发 Harness CICD 流水线和 Harness CICD 流水线内 Hook 共享调用链；最大组合深度为 5。
- 权限与审计：
  - 创建者、管理员和团队成员可以启动已发布 Harness CICD 流水线；访客只读。
  - 只有 Harness CICD 流水线任务创建人可以处理人工确认、取消和重试。
  - Harness CICD 流水线不能绕过当前用户、空间、Agent、Tool 和知识域权限。
  - Harness CICD 流水线定义发布、任务启动、节点状态、Agent 会话关联、确认决定、取消、重试和 Hook 触发均需审计。

### 边界与异常

- Harness CICD 流水线图存在不可达节点、无终点、无界循环、缺失输入映射或非幂等重试风险时不能发布。
- 选择已停用、依赖失效或无权访问的 Harness CICD 流水线时，系统阻止启动并保留输入草稿。
- `knowledgeDomainIds` 包含不存在、跨空间或无权访问的知识域时，系统阻止启动并定位无效项。
- Harness CICD 流水线任务开始 Hook 阻止时，不创建可运行节点，不调用 agent core，并展示阻止原因。
- Agent 节点失败时，Harness CICD 流水线按节点重试、备用分支或终止策略处理；Agent 内部 Skill 或 Tool 调用失败由对应 Agent 会话记录。
- 条件节点表达式无效、输入缺失或返回未知分支时，节点失败并记录诊断。
- 人工确认重复提交同一幂等键时返回已有决定，不重复推进状态机。
- Harness CICD 流水线取消后不再调度新节点；已经完成的外部副作用不被静默撤销。
- 事件流中断或服务重启时，已保存 HarnessPipelineRun、HarnessPipelineNodeRun、AgentSession、HookExecution 和 RuntimeConfirmation 不丢失。
- Hook、Harness CICD 流水线和子流水线调用超过 5 层时，系统终止新调用并保留完整调用链。

## 验收标准

### 正常路径

- 团队管理员可以创建结构化 Harness CICD 流水线草稿，并在画布中编辑 Agent、条件、人工确认、并行、子流水线和输出节点。
- Harness CICD 流水线发布前完成图结构校验、依赖校验、模拟运行和 diff 确认。
- 创建者、管理员和团队成员可以启动已发布 Harness CICD 流水线任务。
- Harness CICD 流水线任务启动时固定 Harness CICD 流水线定义、Agent、Skill、Tool、Hook、AGENT.md 和环境变量引用版本。
- HarnessPipelineEngine 按定义执行节点、分支、并行、重试、人工确认、暂停恢复和取消。
- 每个 Agent 节点自动创建 `source=harness_pipeline_node` 的 Agent 会话，并关联 HarnessPipelineTask、HarnessPipelineRun 和 HarnessPipelineNodeRun。
- Harness CICD 流水线任务清单展示任务名称、Harness CICD 流水线名称、创建人、状态和最新更新时间。
- Harness CICD 流水线任务详情展示 Run 图、节点状态、确认事项、输入输出摘要、错误诊断和关联 Agent 会话入口。
- Hook 可以在支持事件中启动已发布 Harness CICD 流水线，并形成可审计 Harness CICD 流水线任务。

### 边界场景

- Harness CICD 流水线修改发布后，已启动 Run 继续使用启动时版本。
- 并行分支按配置等待全部、快速失败或允许部分成功。
- Harness CICD 流水线自动创建的 Agent 会话出现在 Agent 会话历史中，但不能从会话详情直接继续、改名或取消。
- Harness CICD 流水线等待确认映射为 Harness CICD 流水线任务待处理状态，并保留确认原因和超时信息。
- 子流水线与 Hook 启动 Harness CICD 流水线均计入同一调用链深度。

### 异常场景

- Harness CICD 流水线图结构错误、依赖缺失或模拟运行失败时，系统阻止发布。
- 已发布 Harness CICD 流水线依赖失效或用户无权限时，系统阻止启动。
- Harness CICD 流水线任务开始 Hook 阻止时，系统不调度节点并展示原因。
- Agent 节点失败、条件无效、确认超时或事件流中断时，系统保存失败状态和诊断。
- 取消 Harness CICD 流水线后不再创建新节点运行。
- Hook、Harness CICD 流水线和子流水线调用超过 5 层时，系统终止新调用。

### 权限场景

- 访客不能启动 Harness CICD 流水线任务、处理确认、取消或重试。
- 非 Harness CICD 流水线任务创建人不能处理该任务的确认、取消或重试。
- 团队成员可以启动已发布 Harness CICD 流水线，但不能配置、测试、发布或停用 Harness CICD 流水线。
- Harness CICD 流水线内部 Agent、Tool 和知识域访问必须按当前用户权限校验。

## UI 设计

### 是否需要刷新

- 结论：是
- 理由：新增独立 Harness CICD 流水线配置入口、Harness CICD 流水线任务启动入口、任务清单、Run 详情、节点会话链接和确认/取消/重试控件。

### 页面与交互

- Harness CICD 流水线配置中心：
  - 自然语言生成后进入画布，节点属性使用结构化侧栏。
  - 默认示例为“产品 Agent -> 人工确认 -> 开发 Agent -> 测试/发布确认 -> 运营 Agent”。
  - 提供图校验、模拟运行、测试断言、版本 diff、发布和停用。
- Harness CICD 流水线任务启动页：
  - 展示可启动 Harness CICD 流水线、输入字段、流水线摘要、依赖状态和可能的人工确认节点。
  - 支持选择 R-0630 `knowledgeDomainIds`。
- Harness CICD 流水线任务清单：
  - 展示任务名称、Harness CICD 流水线名称、创建人、状态和最新更新时间。
  - 支持点击进入任务详情。
- Harness CICD 流水线任务详情：
  - 展示 Run 图、当前节点、节点状态、分支、重试、确认、输出和错误。
  - Agent 节点提供“查看 Agent 会话详情”入口，跳转 F-006。
  - 待确认状态展示事项、影响、选项、超时和提交控件。
  - 运行中任务提供取消；失败且可重试节点提供重试。

### 状态与文案

- Harness CICD 流水线配置状态：草稿、待补全、校验失败、测试通过、已发布、已停用。
- Harness CICD 流水线任务状态：等待执行、运行中、等待输入、等待确认、完成、失败、已取消。
- 确认提示：`Harness CICD 流水线等待确认：{title}`。
- 节点会话提示：`该 Agent 会话由 Harness CICD 流水线节点自动创建，请回到 Harness CICD 流水线任务处理流水线操作。`
- 阻止提示：`Harness CICD 流水线被 Hook“{name}”阻止：{reason}`。

## 前端设计

### 是否需要刷新

- 结论：是
- 理由：需要新增 Harness CICD 流水线配置画布、任务启动、Run 图、状态恢复、确认控件、节点会话跳转和新接口。

### 页面、组件与状态

- 页面路由：
  - Harness CICD 流水线配置中心。
  - Harness CICD 流水线编辑与模拟运行页。
  - Harness CICD 流水线任务启动页。
  - Harness CICD 流水线任务清单页。
  - Harness CICD 流水线任务详情页。
- 核心组件：
  - `HarnessPipelineCanvas`、`HarnessPipelineNodePanel`、`HarnessPipelineSimulationPanel`、`HarnessPipelineDiffViewer`。
  - `HarnessPipelineLauncher`、`HarnessPipelineInputForm`、`HarnessPipelineTaskList`、`HarnessPipelineTaskListItem`。
  - `HarnessPipelineRunGraph`、`HarnessPipelineNodeDetail`、`HarnessPipelineNodeSessionLink`。
  - `RuntimeConfirmationCard`、`HarnessPipelineRunActions`、`HarnessPipelineRuntimeStatus`、`HarnessPipelineErrorBanner`。
- 状态管理：
  - Harness CICD 流水线定义草稿、测试结果、发布状态和依赖状态。
  - HarnessPipelineTask、HarnessPipelineRun、HarnessPipelineNodeRun、RuntimeConfirmation、HookExecution 和节点 AgentSession 链接。
  - 事件流连接状态、断线补拉游标和幂等提交状态。

### 接口依赖与异常处理

- 查询、创建、更新、测试、发布和停用 Harness CICD 流水线定义。
- 查询可启动 Harness CICD 流水线和依赖状态。
- 创建 Harness CICD 流水线任务，查询任务清单和任务详情。
- 查询 HarnessPipelineRun、HarnessPipelineNodeRun、HookExecution 和 RuntimeConfirmation。
- 提交确认决定、取消 Run 和重试节点。
- 查询节点关联 Agent 会话详情入口。
- 状态流中断时保留最后事件，并通过详情查询恢复。
- 选择失效或依赖失效时保留用户输入并要求重新选择。

## 后端设计

### 是否需要刷新

- 结论：是
- 理由：新增 Harness CICD 流水线定义管理、HarnessPipelineEngine、Harness CICD 流水线任务数据、节点状态机、节点 Agent 会话创建和运行恢复。

### 接口与数据

- 核心服务：
  - `HarnessPipelineDefinitionService`：管理 Harness CICD 流水线定义、版本和发布状态。
  - `HarnessPipelineValidationService`：执行 Schema、图结构、依赖和权限校验。
  - `HarnessPipelineSimulationService`：执行隔离模拟运行和测试断言。
  - `HarnessPipelineTaskService`：创建 HarnessPipelineTask、查询清单和详情。
  - `HarnessPipelineEngine`：调度节点、分支、重试、并行、确认、暂停和恢复。
  - `HarnessPipelineAgentSessionService`：为 Agent 节点创建并关联 AgentSession。
  - `HookEngine`：匹配 Harness CICD 流水线生命周期事件并执行同步或异步 Hook。
  - `AgentCoreAdapter`：执行 Agent 节点并接收事件。
  - `PermissionService`、`AuditService`。
- 核心数据对象：
  - `HarnessPipelineDefinition`：输入输出、节点、连线、失败策略和版本。
  - `HarnessPipelineTask`：任务 ID、Harness CICD 流水线版本、发起来源、创建人、状态、最新更新时间。
  - `HarnessPipelineRun`：Run ID、任务、状态、输入输出、当前调用深度和时间。
  - `HarnessPipelineNodeRun`：节点、状态、输入输出、幂等键、重试次数、关联 AgentSession ID 和错误。
  - `AgentSession`：`source=manual | harness_pipeline_node`，可关联 `harnessPipelineTaskId`、`harnessPipelineRunId`、`harnessPipelineNodeRunId`。
  - `HarnessSnapshot`：`knowledgeDomainIds`、AGENT.md、Agent、Skill、Tool、Harness CICD 流水线、Hook 和环境变量引用版本。
  - `RuntimeConfirmation`：来源类型、来源执行 ID、题干、选项、状态、超时、决定、操作者和幂等键。
  - `RuntimeCallChain`：根任务、父调用、深度和已执行 Hook 集合。
- 建议接口：
  - `GET /team-spaces/{spaceId}/harness-pipelines`
  - `POST /team-spaces/{spaceId}/harness-pipelines`
  - `POST /team-spaces/{spaceId}/harness-pipelines/{pipelineId}/test`
  - `POST /team-spaces/{spaceId}/harness-pipelines/{pipelineId}/publish`
  - `POST /team-spaces/{spaceId}/harness-pipeline-tasks`
  - `GET /team-spaces/{spaceId}/harness-pipeline-tasks`
  - `GET /team-spaces/{spaceId}/harness-pipeline-tasks/{taskId}`
  - `POST /team-spaces/{spaceId}/runtime-confirmations/{confirmationId}/decisions`
  - `POST /team-spaces/{spaceId}/harness-pipeline-runs/{runId}/cancel`
  - `POST /team-spaces/{spaceId}/harness-pipeline-runs/{runId}/nodes/{nodeRunId}/retry`

### 业务规则、权限与异常处理

- 所有 Harness CICD 流水线定义、任务、Run、节点和 Agent 会话必须归属同一租户与团队空间。
- Harness CICD 流水线任务启动前必须先保存 HarnessPipelineTask、HarnessPipelineRun 和快照，再调用 Engine 调度。
- Agent 节点调用 agent core 前必须先创建 `source=harness_pipeline_node` 的 AgentSession。
- Harness CICD 流水线与 Hook 的状态迁移使用持久化事件和幂等键，服务重启后可恢复。
- Tool 调用和对象写入前，agent core 必须通过可等待事件协议获取 Hook 决定。
- 确认决定提交后按 `sourceType` 恢复对应 HarnessPipelineRun 或 HookExecution；超时、迟到和重复决定均保持单一终态。
- 取消 Harness CICD 流水线不自动撤销已完成外部副作用，补偿只能按已配置策略执行。
- 调用深度超过 5 或同一 Hook 重入时拒绝新调用。
- 所有权限判断以后端当前权限为准，快照不能授予用户原本没有的权限。

## 评审与会签

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认 Harness CICD 流水线任务清单筛选、任务命名、确认文案和取消补偿口径。 |
| UI | 待确认 | 需输出 Harness CICD 流水线配置画布、任务清单、Run 图、确认和节点会话链接交互稿。 |
| 前端 | 待确认 | 需确认事件订阅、Run 状态恢复、节点会话跳转和幂等提交。 |
| 后端 | 待确认 | 需确认 HarnessPipelineEngine、agent core 节点执行、Hook 事件和 Tool 适配契约。 |
| 测试 | 待确认 | 需按 TC-008 覆盖配置发布、运行状态机、节点会话、恢复、Hook 和权限。 |
