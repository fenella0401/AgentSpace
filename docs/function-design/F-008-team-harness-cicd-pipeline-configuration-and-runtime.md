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

- 本次变更：将 Harness CICD 流水线从 Harness 基础配置和手工 Agent 会话运行中拆出，独立承接流水线定义配置、画布编辑、测试发布、流水线任务创建、Run 状态机、运行清单和 Agent 任务会话关联。
- 核心口径：
  - Harness CICD 流水线是基于 Harness 运行多个 Agent 完成工作的结构化流水线定义，不是通用流程编排器，也不作为 prompt 交给某个 Agent 自由执行。
  - 流水线图只编排 Agent 任务节点和任务依赖；不提供条件、人工确认、并行、子流水线、输出等独立节点类型。
  - 每个 Agent 任务内部声明目标 Agent、执行指令、输入、输出、用户审阅策略、重试、超时和失败策略。
  - 用户审阅是 Agent 任务自身的 `waiting_review` 运行状态和 `AgentTaskReview` 记录，只在 Agent 任务详情中配置和处理，不在配置画布或 Run 图节点上体现审阅行为。
  - AI 只生成结构化草稿；发布前必须人工确认配置、完成结构校验、模拟运行和 diff 确认。
  - HarnessPipelineEngine 根据任务依赖确定性调度 Agent 任务，并负责版本固定、重试、审阅等待与恢复、暂停恢复和取消。
  - 用户在新建 Agent 会话处的统一开始作业入口输入任务并选择已发布 Harness CICD 流水线，即创建并运行一条流水线任务。
  - Agent 任务执行时由系统自动创建 `source=harness_pipeline_node` 的 Agent 会话；该会话详情复用 F-006 展示能力。
- 影响范围：产品定义、功能索引、F-001、F-005、F-006、R-0630 发布计划、TC-001、TC-005、TC-006 和 TC-008。
- 与相邻功能的边界：
  - F-001 负责空间初始化、AGENT.md、Skill、Tool、Agent、Hook、环境变量和 `.agentspace` 基础布局；Harness CICD 流水线定义、测试和发布由本功能负责。
  - F-005 负责统一开始作业入口、执行目标选择、用户手工发起的 Agent 会话创建、继续和 Agent 询问处理；选择 Harness CICD 流水线时调用本功能创建 HarnessPipelineTask。
  - F-006 负责 Agent 会话历史与详情展示，包含手工会话和 Harness CICD 流水线 Agent 任务自动会话。
  - Hook 配置仍由 F-001 负责；Harness CICD 流水线生命周期 Hook 由本功能调用共享 Hook Engine。Hook 自身要求人工确认时仍使用 Hook 的 RuntimeConfirmation，但不会成为流水线节点。
- 待确认问题：
  - Agent 任务默认超时、流水线并发上限、重试间隔和最大运行时长。
  - Agent 任务审阅策略支持的审阅人范围、退回次数和超时处理方式。
  - Harness CICD 流水线任务命名规则、运行清单默认筛选项和 Run 事件保留策略。
  - agent core 的 Agent 任务执行、事件流、对象写入前置事件和错误码正式契约。
  - Hook 触发 Harness CICD 流水线时的权限主体、审计主体和通知文案。

## PRD 设计

### 用户目标

- 团队创建者或团队管理员可以通过自然语言生成 Harness CICD 流水线草稿，并在可视化画布中编排多个 Agent 任务及其依赖。
- 流水线可按“需求分析 -> 开发任务拆解 -> 开发任务执行 -> 集成测试”的形式展示标准工作步骤。
- 团队创建者或团队管理员可以在每个 Agent 任务中配置输入、输出、用户审阅、重试、超时和失败策略。
- 团队创建者或团队管理员可以测试、查看 diff 并发布 Harness CICD 流水线定义，确保只有结构正确、依赖完整且模拟通过的版本可被启动。
- 创建者、管理员和团队成员可以在新建 Agent 会话处输入任务并选择已发布 Harness CICD 流水线，启动流水线任务并选择可选知识域。
- HarnessPipelineEngine 可以按 Agent 任务依赖确定性执行 HarnessPipelineRun，维护 Run 与 Agent 任务状态，支持重试、审阅等待与恢复、暂停恢复和取消。
- 每个 Agent 任务运行时自动创建 Agent 会话，作为原子 Agent 执行记录进入 F-006 Agent 会话历史。
- 用户可以在 Harness CICD 流水线任务清单和详情中查看 Run 图、Agent 任务状态、输入输出、审阅事项、失败原因和关联 Agent 会话。
- 用户通过 Harness CICD 流水线任务页处理 Agent 任务审阅、取消和重试；流水线自动创建的 Agent 会话默认不可直接继续或改名。

### Use Story 拆分

| US 编号 | Use Story | 用户角色 | 用户价值 | 生成时间 | 交付版本 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| US-008-001 | 作为团队管理员，我希望创建并发布只编排 Agent 任务的 Harness CICD 流水线，以便沉淀可重复执行的多 Agent 标准作业。 | 团队创建者、团队管理员 | 标准化复杂工作 | 2026-06-08 | R-0630 | AI 只生成草稿，不直接发布 |
| US-008-002 | 作为团队管理员，我希望在每个 Agent 任务中声明输入、输出、审阅和失败处理，并在发布前完成校验和模拟，以便避免错误配置影响真实任务。 | 团队创建者、团队管理员 | 降低运行风险 | 2026-06-08 | R-0630 | 流水线图不增加审阅或输出节点 |
| US-008-003 | 作为团队成员，我希望在新建 Agent 会话处输入任务并选择已发布 Harness CICD 流水线，以便用同一个开始作业入口运行标准流水线任务。 | 创建者、管理员、团队成员 | 执行标准流程 | 2026-06-08 | R-0630 | 创建 HarnessPipelineTask，不创建手工 AgentSession |
| US-008-004 | 作为 Harness CICD 流水线任务创建人，我希望处理 Agent 任务审阅、取消和重试，以便控制流水线继续或终止。 | Harness CICD 流水线任务创建人 | 可控恢复与干预 | 2026-06-08 | R-0630 | 审阅属于 Agent 任务运行态 |
| US-008-005 | 作为团队空间用户，我希望查看 Harness CICD 流水线任务清单和详情，以便理解流水线进度、Agent 任务结果和失败原因。 | 创建者、管理员、团队成员、访客 | 流程透明可追踪 | 2026-06-08 | R-0630 | 访客只读 |
| US-008-006 | 作为平台后端，我希望每个 Agent 任务自动创建 Agent 会话，以便任务执行详情与普通会话统一查看和审计。 | AgentSpace 后端 | 统一执行记录 | 2026-06-08 | R-0630 | `source=harness_pipeline_node` |
| US-008-007 | 作为团队管理员，我希望 Hook 可以在允许的生命周期中启动 Harness CICD 流水线，以便把治理动作转为标准流水线任务。 | 团队创建者、团队管理员 | 治理自动化 | 2026-06-08 | R-0630 | 复用 Hook Engine 和调用链限制 |

### Use Case 编写

| UC 编号 | 关联 US | Use Case | 参与者 | 前置条件 | 业务流程 | 后置结果 |
| --- | --- | --- | --- | --- | --- | --- |
| UC-008-001 | US-008-001、US-008-002 | 创建并发布 Harness CICD 流水线定义 | 团队管理员、AgentSpace 后端 | 用户具备流水线配置权限；被引用 Agent 已存在或可补充 | 1. 用户描述目标工作过程。<br>2. 系统生成只包含 Agent 任务和依赖关系的结构化草稿。<br>3. 用户在画布确认“需求分析 -> 开发任务拆解 -> 开发任务执行 -> 集成测试”等 Agent 任务及依赖。<br>4. 用户在各 Agent 任务属性中配置目标 Agent、执行指令、输入输出、审阅、重试、超时和失败策略。<br>5. 系统拒绝非 Agent 任务节点，并校验图结构、输入输出映射、依赖和幂等风险。<br>6. 用户配置测试输入、Agent 任务 Mock、预期状态和输出断言。<br>7. 系统执行隔离模拟运行。<br>8. 测试通过后展示 diff，用户确认发布。 | HarnessPipelineDefinition 进入已发布状态，可被流水线任务引用 |
| UC-008-002 | US-008-003 | 从开始作业入口启动 Harness CICD 流水线任务 | 团队成员、AgentSpace 前端、AgentSpace 后端、HarnessPipelineEngine | 用户具备任务创建权限；流水线已发布且依赖可用 | 1. 用户进入新建 Agent 会话处的开始作业入口。<br>2. 输入任务说明。<br>3. 将执行目标切换为 Harness CICD 流水线。<br>4. 选择已发布流水线，并选择可选 `knowledgeDomainIds`。<br>5. 后端校验权限、任务说明、流水线输入、知识域和依赖可用性。<br>6. 固定流水线定义及依赖版本快照。<br>7. 创建 HarnessPipelineTask 和 HarnessPipelineRun。<br>8. 执行流水线任务开始 Hook。<br>9. Hook 未阻止时 Engine 调度首个就绪 Agent 任务。 | 流水线任务进入运行中并跳转任务详情；不创建手工 AgentSession |
| UC-008-003 | US-008-006 | 执行 Agent 任务并创建 Agent 会话 | HarnessPipelineEngine、agent core、AgentSpace 后端 | HarnessPipelineAgentTaskRun 进入 `ready` 状态 | 1. Engine 根据依赖计算可运行 Agent 任务。<br>2. 后端按流水线初始输入和上游已确认输出生成任务输入。<br>3. 后端为 Agent 任务创建 `source=harness_pipeline_node` 的 AgentSession 和 HarnessSnapshot。<br>4. AgentSession 关联 harnessPipelineTaskId、harnessPipelineRunId 和 harnessPipelineAgentTaskRunId。<br>5. 调用 agent core 执行 Agent 任务。<br>6. 保存 Agent 会话事件、任务输入输出、尝试次数和状态。<br>7. 无需审阅时任务完成并解锁下游；需要审阅时进入 `waiting_review`，下游不得读取未通过审阅的输出。 | Agent 任务执行详情可在 F-006 Agent 会话详情中查看 |
| UC-008-004 | US-008-004 | 审阅 Agent 任务输出并恢复流水线 | Harness CICD 流水线任务创建人、HarnessPipelineEngine | Agent 任务的 reviewPolicy 要求用户审阅，且 HarnessPipelineAgentTaskRun 为 `waiting_review` | 1. 流水线任务详情在该 Agent 任务内部展示输出摘要、修改内容、审阅要求和反馈输入。<br>2. 创建人提交通过或退回修改。<br>3. 后端按幂等键保存 AgentTaskReview。<br>4. 通过时 Agent 任务完成并解锁依赖它的下游任务。<br>5. 退回时保存反馈，并按任务重试策略创建新的执行尝试。 | 流水线继续运行、保持待审阅、失败或取消；流水线图中不增加审阅节点 |
| UC-008-005 | US-008-004 | 取消或重试 Harness CICD 流水线 | Harness CICD 流水线任务创建人、HarnessPipelineEngine | 流水线未完成，且目标状态允许操作 | 1. 创建人取消 Run，或对可重试失败/退回的 Agent 任务发起重试。<br>2. 后端校验权限、Run 状态、Agent 任务状态和幂等策略。<br>3. 取消时停止后续调度。<br>4. 重试时复用任务幂等策略并创建新的 Agent 任务尝试。 | Run 取消，或从目标 Agent 任务继续；已完成外部副作用不被静默撤销 |
| UC-008-006 | US-008-005 | 查看 Harness CICD 流水线任务清单和详情 | 空间用户、AgentSpace 后端 | 用户具备团队空间访问权限 | 1. 用户进入流水线任务清单。<br>2. 后端按权限返回任务名称、创建人、状态、流水线名称和最新更新时间。<br>3. 用户进入详情。<br>4. 前端展示只含 Agent 任务的 Run 图。<br>5. 用户选择 Agent 任务后，在任务详情中查看输入输出摘要、审阅事项、错误诊断和关联 Agent 会话入口。 | 用户可理解流水线进度；访客只读 |
| UC-008-007 | US-008-007 | Hook 启动 Harness CICD 流水线 | Hook Engine、HarnessPipelineEngine | Hook 动作配置为启动已发布流水线，且调用链未超限 | 1. Hook Engine 命中事件和条件。<br>2. 校验目标流水线版本、权限主体、输入映射和调用链深度。<br>3. 创建来源为 Hook 的 HarnessPipelineTask 和 HarnessPipelineRun。<br>4. Engine 按普通流水线任务执行。 | Hook 动作形成可审计流水线任务；超过调用链限制时拒绝启动 |

### 业务规则

- 概念边界：
  - Agent 会话是原子 Agent 执行记录，来源为 `manual` 或 `harness_pipeline_node`。
  - Harness CICD 流水线配置是结构化的多 Agent 任务流水线定义，不进入 Agent prompt 让模型自行解释流程。
  - Harness CICD 流水线任务是用户或 Hook 启动的流水线实例，包含 HarnessPipelineTask、HarnessPipelineRun 和多个 HarnessPipelineAgentTaskRun。
  - Harness CICD 流水线 Agent 任务自动创建的 Agent 会话展示在 Agent 会话历史中，但父级控制动作归流水线任务页。
- Harness CICD 流水线配置：
  - 流水线图只允许 Agent 任务节点；条件、人工确认、并行、子流水线、输出、Skill 和 Tool 均不是流水线节点类型。
  - 流水线定义通过 Agent 任务之间的有向依赖表达执行顺序。多个 Agent 任务同时满足依赖时，Engine 可按并发上限并发执行，但不需要并行节点。
  - `HarnessPipelineAgentTaskDefinition` 必须声明任务名称、Agent 版本、执行指令、输入 Schema 与映射、输出 Schema 与映射、reviewPolicy、retryPolicy、timeout 和 failurePolicy。
  - Agent 任务输入可引用流水线初始输入和已完成上游 Agent 任务的已确认输出。
  - 用户审阅属于 Agent 任务内部策略。reviewPolicy、审阅状态和审阅控件仅在 Agent 任务属性或详情中展示，配置画布和 Run 图节点不显示审阅行为。
  - Skill 和 Tool 是 Agent 任务执行过程中的能力或依赖，由对应 Agent 和 Harness 配置管理。
  - AI 可以生成 Agent 任务、依赖和任务契约草稿，但不能直接发布或决定运行时任务走向。
  - 发布前必须通过图结构校验、依赖校验、输入输出映射校验、模拟运行和 diff 确认。
- Harness CICD 流水线运行：
  - 用户启动入口复用 F-005 新建 Agent 会话处的开始作业入口；本功能提供可启动流水线列表、输入 Schema、依赖状态和创建任务接口。
  - 从流水线配置完成页点击“运行流水线”时，系统跳转到开始作业入口并预选当前已发布流水线。
  - 用户输入的任务说明保存为 HarnessPipelineTask 的 `taskInstruction`，并按流水线输入 Schema 映射为初始输入。
  - 流水线启动时固定定义和依赖版本，运行中不切换。
  - HarnessPipelineTask 状态为等待执行、运行中、等待输入、待审阅、完成、失败、已取消。
  - HarnessPipelineRun 状态为 `pending`、`running`、`waiting_input`、`waiting_review`、`completed`、`failed`、`cancelled`。
  - HarnessPipelineAgentTaskRun 状态为 `pending`、`ready`、`running`、`waiting_input`、`waiting_review`、`completed`、`failed`、`skipped`、`cancelled`。
  - Run 图将 `waiting_review` 归入 Agent 任务的进行中展示；流水线任务清单和 Agent 任务详情可展示待审阅，但 Run 图节点不显示审阅标识。
  - 每个 Agent 任务运行必须创建一个 `source=harness_pipeline_node` 的 AgentSession；任务重试创建新的 AgentTaskRun attempt 和 AgentSession，并保留重试关系。
  - 只有已完成且审阅通过的 Agent 任务输出可以解锁并传递给下游任务。
  - 流水线自动创建的 Agent 会话不可被用户从 Agent 会话详情中直接继续、改名或取消；用户需回到流水线任务页操作。
  - Hook 启动流水线和流水线生命周期 Hook 共享调用链；最大组合深度为 5。
- 权限与审计：
  - 创建者、管理员和团队成员可以启动已发布 Harness CICD 流水线；访客只读。
  - 首版只有 Harness CICD 流水线任务创建人可以处理 Agent 任务审阅、取消和重试；后续可按 reviewPolicy 扩展指定审阅人。
  - Harness CICD 流水线不能绕过当前用户、空间、Agent、Tool 和知识域权限。
  - 流水线定义发布、任务启动、Agent 任务状态、Agent 会话关联、审阅决定、取消、重试和 Hook 触发均需审计。

### 边界与异常

- 流水线图存在非 Agent 任务节点、不可达任务、无终点、循环依赖、缺失输入映射、输出 Schema 不兼容或非幂等重试风险时不能发布。
- 选择已停用、依赖失效或无权访问的流水线时，系统阻止启动并保留输入草稿。
- `knowledgeDomainIds` 包含不存在、跨空间或无权访问的知识域时，系统阻止启动并定位无效项。
- 流水线任务开始 Hook 阻止时，不创建可运行 Agent 任务，不调用 agent core，并展示阻止原因。
- Agent 任务失败时，流水线按该任务的重试或终止策略处理；Agent 内部 Skill 或 Tool 调用失败由对应 Agent 会话记录。
- Agent 任务缺失输入、输出不满足 Schema 或上游输出未通过审阅时，任务不得进入 `ready`。
- AgentTaskReview 重复提交同一幂等键时返回已有决定，不重复推进状态机。
- 审阅退回后达到最大尝试次数，Agent 任务和 Run 进入失败；超时按 reviewPolicy 的超时策略处理。
- 流水线取消后不再调度新 Agent 任务；已经完成的外部副作用不被静默撤销。
- 事件流中断或服务重启时，已保存 HarnessPipelineRun、HarnessPipelineAgentTaskRun、AgentTaskReview、AgentSession 和 HookExecution 不丢失。
- Hook 与 Harness CICD 流水线组合调用超过 5 层时，系统终止新调用并保留完整调用链。

## 验收标准

### 正常路径

- 团队管理员可以创建结构化 Harness CICD 流水线草稿，画布中只能编辑 Agent 任务和任务依赖。
- 默认示例按“需求分析 -> 开发任务拆解 -> 开发任务执行 -> 集成测试”展示。
- 每个 Agent 任务可以配置目标 Agent、执行指令、输入、输出、用户审阅、重试、超时和失败策略。
- 流水线发布前完成图结构、依赖、输入输出映射校验、模拟运行和 diff 确认。
- 创建者、管理员和团队成员可以在新建 Agent 会话处输入任务，并选择已发布 Harness CICD 流水线启动任务。
- 流水线配置完成页可以跳转到开始作业入口，并预选当前流水线。
- 流水线任务启动时固定流水线定义、Agent、Skill、Tool、Hook、AGENT.md 和环境变量引用版本。
- HarnessPipelineEngine 按 Agent 任务依赖执行、重试、等待审阅、恢复和取消；多个就绪任务可受并发上限控制并发运行。
- Agent 任务需要审阅时，该 Agent 任务内部进入 `waiting_review`；流水线任务清单和 Agent 任务详情展示待审阅，Run 图节点不显示审阅行为。
- 每个 Agent 任务自动创建 `source=harness_pipeline_node` 的 Agent 会话，并关联 HarnessPipelineTask、HarnessPipelineRun 和 HarnessPipelineAgentTaskRun。
- 流水线任务清单展示任务名称、流水线名称、创建人、状态和最新更新时间。
- 流水线任务详情展示只含 Agent 任务的 Run 图；用户选择 Agent 任务后，在任务详情中查看审阅事项、输入输出摘要、错误诊断和关联 Agent 会话入口。
- Hook 可以在支持事件中启动已发布 Harness CICD 流水线，并形成可审计流水线任务。

### 边界场景

- 流水线修改发布后，已启动 Run 继续使用启动时版本。
- 多个 Agent 任务同时满足依赖时可以并发执行，但画布和 Run 图中不存在并行节点。
- 上游 Agent 任务需要审阅时，只有通过审阅的输出可以传给下游任务；审阅状态不在 Run 图节点上展示。
- 流水线自动创建的 Agent 会话出现在 Agent 会话历史中，但不能从会话详情直接继续、改名或取消。
- Hook 要求人工确认时由 Hook 自身的 RuntimeConfirmation 处理，不在流水线图中生成确认节点。

### 异常场景

- 流水线包含非 Agent 任务节点、图结构错误、依赖缺失、输入输出不兼容或模拟运行失败时，系统阻止发布。
- 已发布流水线依赖失效或用户无权限时，系统阻止启动。
- 流水线任务开始 Hook 阻止时，系统不调度 Agent 任务并展示原因。
- Agent 任务失败、输出校验失败、审阅超时或事件流中断时，系统保存失败状态和诊断。
- 取消流水线后不再创建新 AgentTaskRun。
- Hook 与 Harness CICD 流水线组合调用超过 5 层时，系统终止新调用。

### 权限场景

- 访客不能启动 Harness CICD 流水线任务、处理 Agent 任务审阅、取消或重试。
- 非 Harness CICD 流水线任务创建人不能处理该任务的审阅、取消或重试。
- 团队成员可以启动已发布 Harness CICD 流水线，但不能配置、测试、发布或停用流水线。
- 流水线内部 Agent、Tool 和知识域访问必须按当前用户权限校验。

## UI 设计

### 是否需要刷新

- 结论：是
- 理由：新增独立 Harness CICD 流水线配置入口、流水线任务启动入口、任务清单、Run 详情、Agent 任务会话链接和任务内审阅/取消/重试控件。

### 页面与交互

- Harness CICD 流水线配置中心：
  - 自然语言生成后进入画布，画布只提供 Agent 任务节点，任务属性使用结构化侧栏。
  - 默认示例为“需求分析 -> 开发任务拆解 -> 开发任务执行 -> 集成测试”。
  - Agent 任务侧栏配置目标 Agent、执行指令、输入 Schema 与映射、输出 Schema 与映射、用户审阅策略、重试、超时和失败策略。
  - 提供图校验、模拟运行、测试断言、版本 diff、发布和停用。
- 开始作业入口中的 Harness CICD 流水线分支：
  - 入口位于 F-005 新建 Agent 会话处，用户输入任务说明后选择 Harness CICD 流水线。
  - 展示可启动流水线、输入字段、Agent 任务步骤摘要、依赖状态和任务审阅策略摘要。
  - 支持选择 R-0630 `knowledgeDomainIds`。
  - 配置完成页、流水线列表和版本详情中的“运行流水线”均跳转到该入口，并预选对应流水线。
  - 提交后跳转 Harness CICD 流水线任务详情页。
- Harness CICD 流水线任务清单：
  - 展示任务名称、流水线名称、创建人、状态和最新更新时间。
  - 支持点击进入任务详情。
- Harness CICD 流水线任务详情：
  - Run 图只展示 Agent 任务节点、依赖连线和通用执行状态，不展示用户审阅行为或审阅标识。
  - Agent 任务详情展示当前 Agent 任务、输入、输出、尝试次数、审阅状态和错误。
  - Agent 任务提供“查看 Agent 会话详情”入口，跳转 F-006。
  - Agent 任务处于待审阅时，在该任务详情内展示输出摘要、影响、反馈输入、通过和退回修改控件，不额外绘制审阅节点。
  - 运行中任务提供取消；失败或退回且可重试的 Agent 任务提供重试。

### 状态与文案

- Harness CICD 流水线配置状态：草稿、待补全、校验失败、测试通过、已发布、已停用。
- Harness CICD 流水线任务状态：等待执行、运行中、等待输入、待审阅、完成、失败、已取消。
- Agent 任务审阅提示：`Agent 任务“{taskName}”的输出等待审阅。`
- Agent 任务会话提示：`该 Agent 会话由 Harness CICD 流水线 Agent 任务自动创建，请回到流水线任务页处理操作。`
- 阻止提示：`Harness CICD 流水线被 Hook“{name}”阻止：{reason}`。

## 前端设计

### 是否需要刷新

- 结论：是
- 理由：需要新增只编排 Agent 任务的流水线配置画布、接入 F-005 开始作业入口的流水线目标选择、Run 图、任务内审阅、状态恢复、Agent 会话跳转和新接口。

### 页面、组件与状态

- 页面路由：
  - Harness CICD 流水线配置中心。
  - Harness CICD 流水线编辑与模拟运行页。
  - F-005 开始作业入口中的 Harness CICD 流水线分支。
  - Harness CICD 流水线任务清单页。
  - Harness CICD 流水线任务详情页。
- 核心组件：
  - `HarnessPipelineCanvas`、`HarnessPipelineAgentTaskPanel`、`HarnessPipelineSimulationPanel`、`HarnessPipelineDiffViewer`。
  - `HarnessPipelineTargetPicker`、`HarnessPipelineInputForm`、`HarnessPipelineTaskList`、`HarnessPipelineTaskListItem`。
  - `HarnessPipelineRunGraph`、`HarnessPipelineAgentTaskDetail`、`HarnessPipelineAgentTaskSessionLink`。
  - `AgentTaskReviewCard`、`HarnessPipelineRunActions`、`HarnessPipelineRuntimeStatus`、`HarnessPipelineErrorBanner`。
- 状态管理：
  - Harness CICD 流水线定义草稿、Agent 任务定义、测试结果、发布状态和依赖状态。
  - HarnessPipelineTask、HarnessPipelineRun、HarnessPipelineAgentTaskRun、AgentTaskReview、HookExecution 和 Agent 任务会话链接。
  - 事件流连接状态、断线补拉游标和幂等提交状态。

### 接口依赖与异常处理

- 查询、创建、更新、测试、发布和停用 Harness CICD 流水线定义。
- 向 F-005 开始作业入口提供可启动流水线、输入 Schema、Agent 任务摘要、依赖状态和预选流水线参数。
- 从 F-005 开始作业入口创建 Harness CICD 流水线任务，查询任务清单和任务详情。
- 查询 HarnessPipelineRun、HarnessPipelineAgentTaskRun、AgentTaskReview 和 HookExecution。
- 提交 Agent 任务审阅决定、取消 Run 和重试 Agent 任务。
- 查询 Agent 任务关联 Agent 会话详情入口。
- 状态流中断时保留最后事件，并通过详情查询恢复。
- 选择失效或依赖失效时保留用户输入并要求重新选择。

## 后端设计

### 是否需要刷新

- 结论：是
- 理由：新增 Harness CICD 流水线定义管理、HarnessPipelineEngine、流水线任务数据、Agent 任务状态机、AgentTaskReview、Agent 会话创建和运行恢复。

### 接口与数据

- 核心服务：
  - `HarnessPipelineDefinitionService`：管理 Harness CICD 流水线定义、版本和发布状态。
  - `HarnessPipelineValidationService`：执行 Schema、Agent 任务图、输入输出映射、依赖和权限校验。
  - `HarnessPipelineSimulationService`：执行隔离模拟运行和任务级测试断言。
  - `HarnessPipelineTaskService`：创建 HarnessPipelineTask、查询清单和详情。
  - `HarnessPipelineStartService`：向 F-005 开始作业入口提供可启动列表、输入 Schema、Agent 任务摘要、依赖状态和预选上下文。
  - `HarnessPipelineEngine`：按依赖调度 Agent 任务，处理并发上限、重试、审阅等待与恢复、暂停和取消。
  - `HarnessPipelineAgentTaskReviewService`：保存审阅决定和反馈，并按任务策略推进或重试。
  - `HarnessPipelineAgentSessionService`：为 Agent 任务创建并关联 AgentSession。
  - `HookEngine`：匹配 Harness CICD 流水线生命周期事件并执行同步或异步 Hook。
  - `AgentCoreAdapter`：执行 Agent 任务并接收事件。
  - `PermissionService`、`AuditService`。
- 核心数据对象：
  - `HarnessPipelineDefinition`：流水线输入输出、Agent 任务定义、任务依赖、全局失败策略和版本。
  - `HarnessPipelineAgentTaskDefinition`：任务标识、名称、Agent 版本、执行指令、输入 Schema 与映射、输出 Schema 与映射、reviewPolicy、retryPolicy、timeout 和 failurePolicy。
  - `HarnessPipelineTask`：任务 ID、流水线版本、任务说明、发起来源、创建人、状态和最新更新时间。
  - `HarnessPipelineRun`：Run ID、任务、状态、输入输出、当前调用深度和时间。
  - `HarnessPipelineAgentTaskRun`：Agent 任务、attempt、状态、输入输出、审阅状态、幂等键、重试次数、关联 AgentSession ID 和错误。
  - `AgentTaskReview`：AgentTaskRun ID、输出版本、审阅状态、决定、反馈、操作者、超时和幂等键。
  - `AgentSession`：`source=manual | harness_pipeline_node`，可关联 `harnessPipelineTaskId`、`harnessPipelineRunId`、`harnessPipelineAgentTaskRunId`。
  - `HarnessSnapshot`：`knowledgeDomainIds`、AGENT.md、Agent、Skill、Tool、Harness CICD 流水线、Hook 和环境变量引用版本。
  - `RuntimeCallChain`：根任务、父调用、深度和已执行 Hook 集合。
- 建议接口：
  - `GET /team-spaces/{spaceId}/harness-pipelines`
  - `POST /team-spaces/{spaceId}/harness-pipelines`
  - `POST /team-spaces/{spaceId}/harness-pipelines/{pipelineId}/test`
  - `POST /team-spaces/{spaceId}/harness-pipelines/{pipelineId}/publish`
  - `GET /team-spaces/{spaceId}/harness-pipelines?startable=true`
  - `GET /team-spaces/{spaceId}/harness-pipelines/{pipelineId}/start-context`
  - `POST /team-spaces/{spaceId}/harness-pipeline-tasks`
  - `GET /team-spaces/{spaceId}/harness-pipeline-tasks`
  - `GET /team-spaces/{spaceId}/harness-pipeline-tasks/{taskId}`
  - `POST /team-spaces/{spaceId}/harness-pipeline-runs/{runId}/agent-tasks/{agentTaskRunId}/reviews`
  - `POST /team-spaces/{spaceId}/harness-pipeline-runs/{runId}/cancel`
  - `POST /team-spaces/{spaceId}/harness-pipeline-runs/{runId}/agent-tasks/{agentTaskRunId}/retry`

### 业务规则、权限与异常处理

- 所有 Harness CICD 流水线定义、任务、Run、AgentTaskRun、AgentTaskReview 和 AgentSession 必须归属同一租户与团队空间。
- 用户启动流水线任务时必须从开始作业入口提交 taskInstruction 和 pipelineId；后端不依赖入口路由做权限判断。
- 流水线任务启动前必须先保存 HarnessPipelineTask、HarnessPipelineRun 和快照，再调用 Engine 调度。
- Agent 任务调用 agent core 前必须先创建 `source=harness_pipeline_node` 的 AgentSession。
- HarnessPipelineEngine 只根据持久化任务依赖、任务状态和已确认输出调度，不调用模型决定下一步执行哪个任务。
- Agent 任务审阅决定提交后按 AgentTaskReview 推进对应 HarnessPipelineAgentTaskRun；不创建独立流水线审阅节点，也不复用 Hook RuntimeConfirmation。
- Harness CICD 流水线与 Hook 的状态迁移使用持久化事件和幂等键，服务重启后可恢复。
- Tool 调用和对象写入前，agent core 必须通过可等待事件协议获取 Hook 决定。
- 取消流水线不自动撤销已完成外部副作用，补偿只能按已配置策略执行。
- 调用深度超过 5 或同一 Hook 重入时拒绝新调用。
- 所有权限判断以后端当前权限为准，快照不能授予用户原本没有的权限。

## 评审与会签

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认 Agent 任务审阅策略、流水线任务清单筛选、任务命名和取消补偿口径。 |
| UI | 待确认 | 需输出只编排 Agent 任务的流水线画布、任务清单、Run 图、任务内审阅和 Agent 会话链接交互稿。 |
| 前端 | 待确认 | 需确认事件订阅、Run 状态恢复、AgentTaskReview、Agent 会话跳转和幂等提交。 |
| 后端 | 待确认 | 需确认 HarnessPipelineEngine、AgentTaskRun、agent core 任务执行、Hook 事件和 Tool 适配契约。 |
| 测试 | 待确认 | 需按 TC-008 覆盖配置发布、Agent 任务状态机、任务内审阅、Agent 会话、恢复、Hook 和权限。 |
