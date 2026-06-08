# F-005-团队空间 Agent 会话创建与运行 功能设计

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 功能编号 | F-005 |
| 功能名称 | 团队空间 Agent 会话创建与运行 |
| 所属功能树节点 | AgentSpace / Agent 会话与对话协作 |
| 关联版本 | R-0630 / R-0730 |
| 状态 | 草稿 |
| 最近更新 | 2026-06-08 |

## 变更摘要

- 本次变更：统一使用“Agent 会话”表达原子 Agent 执行记录，并将新建 Agent 会话处升级为统一“开始作业”入口；用户输入任务后可以选择 Agent 或已发布 Harness CICD 流水线。
- 职责口径：F-005 负责统一开始作业入口、目标选择交互和手工 Agent 会话分支；当用户选择 Harness CICD 流水线时，前端调用 F-008 创建 HarnessPipelineTask，后续 Run 状态机、任务清单和 Agent 任务会话关联仍由 F-008 负责。
- 2026-06-08 拆分口径：
  - 手工 Agent 会话：由用户在对话入口输入指令创建，`AgentSession.source=manual`。
  - Harness CICD 流水线 Agent 任务会话：由 F-008 HarnessPipelineEngine 自动创建，`AgentSession.source=harness_pipeline_node`，不在本功能中发起。
  - F-005 不创建 HarnessPipelineTask，不展示 HarnessPipelineRun 图、Agent 任务审阅、取消或任务重试。
- R-0730 继续承接 US-005-007 `/` Agent 功能选择和 US-005-008 `@` 知识文件引用；这些输入增强可作用于手工 Agent 会话分支，流水线分支的结构化输入由 F-008 校验。
- 影响范围：Agent 会话创建入口、Harness 快照、agent core 事件协议、Hook 生命周期执行、会话命名、询问回答、TC-005、F-006 和发布计划。
- 与相邻功能的边界：
  - F-006 负责 Agent 会话历史与详情展示，包含 `manual` 和 `harness_pipeline_node` 两类来源。
  - F-008 负责 Harness CICD 流水线配置、Harness CICD 流水线任务创建、Run 状态机、Agent 任务会话创建和 Harness CICD 流水线任务清单，并向统一开始作业入口提供可启动流水线列表和创建任务接口。
  - Hook 配置由 F-001 管理，本功能负责手工 Agent 会话生命周期中的 Hook 执行。
- 待确认问题：
  - 外部 agent core 的正式会话创建、继续对话、询问、Agent 内部 Tool 调用前置事件和对象写入事件协议。
  - agent core 询问事件字段和开放式回答长度限制。
  - 会话名称生成模型、提示词和内容安全策略。
  - SSE 在目标网关中的支持情况；不支持时保持事件语义迁移为 WebSocket。
  - R-0730 的功能目录字段和知识文件大小、数量及上下文预算。

## PRD 设计

### 用户目标

- 创建者、管理员和团队成员可以在空间内输入指令，手工创建 Agent 会话。
- 创建者、管理员和团队成员可以在同一个开始作业入口输入任务，并选择“Agent”或“Harness CICD 流水线”作为执行目标。
- 零配置空间可以直接使用平台默认 Agent，不要求存在知识库或自定义 Harness。
- 用户可以在创建会话前选择空间默认 Agent 或其他已发布 Agent。
- 系统为每次用户输入固定 AGENT.md、Agent、Skill、Tool、Hook 和环境变量引用版本。
- 对话创建人可以继续本人创建的历史手工会话、修改会话名称并回答 Agent 询问。
- Hook 可以在明确的手工 Agent 会话生命周期事件上执行阻止、确认、Skill、通知和审计动作。
- 团队成员可以理解手工 Agent 会话的运行状态、等待原因和失败原因。
- R-0730 交付后，用户可以在输入框中通过 `/` 选择本轮功能，通过 `@` 引用知识文件。

### Use Story 拆分

| US 编号 | Use Story | 用户角色 | 用户价值 | 生成时间 | 交付版本 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| US-005-001 | 作为团队成员，我希望输入首条指令创建 Agent 会话，以便使用默认或指定 Agent。 | 创建者、管理员、团队成员 | 快速开始作业 | 2026-06-04 | R-0630 | 零配置使用平台默认 Agent |
| US-005-010 | 作为团队成员，我希望在开始作业入口选择 Agent 或 Harness CICD 流水线，以便用同一个输入入口启动不同执行方式。 | 创建者、管理员、团队成员 | 降低入口理解成本 | 2026-06-08 | R-0630 | 选择流水线时由 F-008 创建任务 |
| US-005-002 | 作为对话创建人，我希望系统自动生成并允许修改会话名称，以便识别历史会话。 | 对话创建人 | 提升历史识别效率 | 2026-06-04 | R-0630 | 生成失败使用兜底规则 |
| US-005-003 | 作为对话创建人，我希望继续本人创建的手工会话，以便围绕同一上下文持续推进。 | 对话创建人 | 支持持续协作 | 2026-06-04 | R-0630 | Harness CICD 流水线 Agent 任务会话不可直接继续 |
| US-005-004 | 作为对话创建人，我希望回答 Agent 询问，以便会话继续运行。 | 对话创建人 | 不中断会话 | 2026-06-04 | R-0630 | 支持选项型和开放式 |
| US-005-005 | 作为平台后端，我希望每次输入固定 Harness 快照后调用 agent core，以便运行可追踪。 | AgentSpace 后端 | 保证版本一致和审计 | 2026-06-04 | R-0630 | 敏感值只保存引用 |
| US-005-006 | 作为团队成员，我希望看到手工 Agent 会话关键状态和错误，以便理解当前进度。 | 团队成员 | 提升运行可理解性 | 2026-06-04 | R-0630 | 不含 HarnessPipelineRun 图 |
| US-005-007 | 作为具备输入权限的用户，我希望通过 `/` 选择本轮 Agent 功能。 | 创建者、管理员、团队成员、对话创建人 | 结构化指定本轮意图 | 2026-06-05 | R-0730 | 适用于手工 Agent 会话输入 |
| US-005-008 | 作为具备输入权限的用户，我希望通过 `@` 引用知识文件。 | 创建者、管理员、团队成员、对话创建人 | 明确本轮知识上下文 | 2026-06-05 | R-0730 | 文件需属于当前空间 |
| US-005-009 | 作为团队管理员，我希望已发布 Hook 在真实手工会话生命周期中执行，以便自动落实治理规则。 | 团队创建者、团队管理员 | 稳定执行校验和自动化 | 2026-06-08 | R-0630 | Hook 配置由 F-001 管理 |

### Use Case 编写

| UC 编号 | 关联 US | Use Case | 参与者 | 前置条件 | 业务流程 | 后置结果 |
| --- | --- | --- | --- | --- | --- | --- |
| UC-005-001 | US-005-001、US-005-002、US-005-005、US-005-009 | 创建手工 Agent 会话 | 团队成员、AgentSpace 后端、agent core | 用户有 Agent 会话创建权限 | 1. 用户选择默认 Agent 或已发布 Agent、可选知识域并输入指令。<br>2. 后端校验 Agent、`knowledgeDomainIds` 和指令。<br>3. 保存 `source=manual` 的 AgentSession 与首轮输入。<br>4. 生成 Harness 快照和会话名称。<br>5. 执行会话开始 Hook。<br>6. 无阻止结果时调用 agent core。 | Agent 会话进入运行中；零配置使用平台默认 Agent |
| UC-005-002 | US-005-003、US-005-005、US-005-009 | 继续手工 Agent 会话 | 对话创建人、AgentSpace 后端、agent core | 会话为 `source=manual`；会话静止且存在外部 conversation ID | 1. 创建人提交新指令。<br>2. 后端校验权限、来源和状态。<br>3. 创建新轮次和新快照。<br>4. 执行会话开始 Hook。<br>5. 调用 agent core 继续会话。 | 新轮次进入运行中，历史轮次版本不变 |
| UC-005-003 | US-005-004 | 回答 Agent 询问 | 对话创建人、AgentSpace 后端、agent core | 手工会话处于待输入 | 1. 系统保存询问。<br>2. 前端展示选项或文本控件。<br>3. 创建人提交回答。<br>4. 后端校验幂等键和内容。<br>5. 开放式回答生成回答级快照并回传。 | 会话重新进入运行中；失败时保持待输入 |
| UC-005-004 | US-005-002 | 修改手工会话名称 | 对话创建人 | 会话为 `source=manual` 且已创建 | 1. 编辑名称。<br>2. 后端校验 4-10 个字。<br>3. 保存并审计。 | Agent 会话历史和详情展示最新名称 |
| UC-005-005 | US-005-006、US-005-009 | 接收手工会话运行事件 | AgentSpace 后端、agent core | 会话运行中 | 1. 接收状态、询问、Tool 调用、对象写入、输出、完成或错误事件。<br>2. 保存事件。<br>3. 在对应事件执行 Hook。<br>4. 更新会话和轮次状态。 | 前端获得可理解状态和执行结果 |
| UC-005-006 | US-005-009 | 执行同步 Hook | Hook Engine、AgentSpace 后端 | 命中会话开始、Tool 调用前或对象写入前事件 | 1. 匹配当前快照中的 Hook。<br>2. 按优先级计算条件。<br>3. 执行注入、校验、阻止、确认或 Skill 动作。<br>4. 返回继续、阻止或等待确认决定。 | 主动作按 Hook 决定继续或暂停 |
| UC-005-007 | US-005-009 | 执行异步 Hook | Hook Engine、AgentSpace 后端 | 命中 Tool 调用后、对象写入后、会话完成或失败事件 | 1. 保存主事件。<br>2. 异步创建 HookExecution。<br>3. 执行通知、审计或 Skill。<br>4. 保存结果。 | Hook 失败被记录，但不回滚已完成主动作 |
| UC-005-008 | US-005-007、US-005-008 | 输入框结构化选择 | 团队成员、对话创建人 | R-0730 输入增强启用 | 1. `/` 展示可用 Agent 功能。<br>2. `@` 搜索知识文件。<br>3. 用户选择后保存结构化引用。<br>4. 后端提交前重新校验。 | 有效选择进入本轮快照 |
| UC-005-009 | US-005-010 | 从开始作业入口选择 Harness CICD 流水线 | 团队成员、AgentSpace 前端、F-008 后端 | 用户有开始作业权限；存在已发布且可启动的 Harness CICD 流水线 | 1. 用户进入新建 Agent 会话处的开始作业入口。<br>2. 输入任务说明。<br>3. 将执行目标从 Agent 切换为 Harness CICD 流水线。<br>4. 选择已发布流水线和可选知识域。<br>5. 前端调用 F-008 创建 HarnessPipelineTask。 | 系统进入 Harness CICD 流水线任务运行；不创建 `source=manual` 的 AgentSession |

### 业务规则

- 开始作业入口：
  - 创建者、管理员和团队成员可以在新建 Agent 会话处使用统一开始作业入口；访客不可提交。
  - 执行目标包含“Agent”和“Harness CICD 流水线”。默认目标为 Agent，以保证零配置空间可直接开始作业。
  - 选择 Agent 时创建 `source=manual` 的 AgentSession，本分支由 F-005 负责。
  - 选择 Harness CICD 流水线时必须选择一个已发布且可启动的流水线；前端将任务说明、pipelineId、`knowledgeDomainIds` 和幂等键提交给 F-008 创建 HarnessPipelineTask。
  - 选择 Harness CICD 流水线不会创建手工 AgentSession；后续只有流水线 Agent 任务运行时才由 F-008 创建 `source=harness_pipeline_node` 的 AgentSession。
  - 未选择 Agent 时使用空间默认 Agent；空间未指定默认 Agent 时使用平台默认 Agent。
  - R-0630 提供独立的可选知识域选择器，请求保存 `knowledgeDomainIds`；它不依赖 R-0730 的 `@` 文件引用。
  - R-0730 的 `/` 用于文本输入中选择本轮 Agent 功能，不替代 Agent 选择器。
- 会话与输入：
  - 一个 AgentSession 可以包含多轮用户输入。
  - 手工创建的 AgentSession 来源为 `manual`。
  - Harness CICD 流水线 Agent 任务自动创建的 AgentSession 来源为 `harness_pipeline_node`，由 F-008 创建，不允许从 F-005 入口继续、改名或取消。
  - 首版只有会话创建人可以继续输入、回答询问和修改名称。
  - 继续输入仅允许 `source=manual` 的会话处于完成静止态或异常静止态且存在 conversation ID。
  - 用户输入和开放式回答必须为非空文本。
  - 会话名称生成失败时，优先取首条指令前 8 个字；不足 4 个字时使用“新建会话”。
- Harness 快照：
  - 每轮指令和开放式回答生成独立 Harness 快照。
  - 快照固定解析后的空间级和命中知识域 AGENT.md、选中 Agent、相关 Skill、Tool、Hook 版本和环境变量引用。
  - 手工 Agent 会话快照不固定 Harness CICD 流水线定义；Harness CICD 流水线 Agent 任务会话的流水线版本关系由 F-008 维护。
  - 命中知识域由请求中的 `knowledgeDomainIds`、选中 Agent 声明的知识域及 R-0730 `@` 文件所属知识域取并集后去重，系统不通过自然语言猜测知识域。
  - 零配置空间的快照使用平台默认 Agent，其余资源集合为空。
  - 敏感环境变量只保存安全引用。
  - 快照创建后不受空间后续配置变化影响。
- Hook：
  - Hook 只消费 AgentSpace 或 agent core 明确定义的事件，不从自然语言推测事件。
  - 支持会话开始、Tool 调用前后、对象写入前后、会话完成、会话失败事件。
  - `before` Hook 同步执行，可继续、阻止或等待确认；`after` Hook 异步执行，不回滚主动作。
  - Tool 调用前和对象写入前，agent core 必须等待 AgentSpace Hook 决定后继续。
  - 同一 Hook 在同一调用链最多执行一次；超过 5 层或检测到重复时终止新调用并审计。
  - Hook 不允许执行任意脚本；仅执行 F-001 发布定义中的结构化动作。
  - Hook 使用会话快照中的版本，运行中发布的新 Hook 不参与当前会话。
- 状态映射：
  - AgentSession UI 状态保持运行中、待输入、完成静止态和异常静止态。
  - Agent 询问或 Hook 要求确认时，AgentSession 映射为待输入，并展示具体等待类型。
  - 会话完成映射为完成静止态；失败或取消映射为异常静止态并保留具体原因。
- 事件与持久化：
  - 后端必须先保存会话、轮次和快照，再调用外部执行服务。
  - agent core 的认证失败、调用失败、超时和事件流中断必须映射为内部错误事件。
  - Hook 决定、人工确认和外部副作用必须可审计。
  - Hook 人工确认统一创建 `RuntimeConfirmation`；提交接口按来源类型恢复对应 HookExecution 或会话轮次。
- R-0730 输入增强：
  - `/` 可选择当前空间已发布 Agent、Skill 或 Agent 内置能力，每轮至多一个。
  - `@` 可引用当前空间内用户可读的知识文件，每轮最多 10 个。
  - 结构化选择与自然语言冲突时，结构化选择作为路由信息，文本保留为任务说明。
  - 仅有选择而没有有效文本时不允许提交。

### 边界与异常

- 零配置空间创建手工 Agent 会话时，快照生成平台默认 Agent 和空扩展集合。
- 选择的 Agent 已停用、版本不可用或依赖失效时，系统阻止提交并保留指令草稿。
- `knowledgeDomainIds` 包含不存在、跨空间或无权访问的知识域时，系统阻止提交并定位无效项。
- 会话开始 Hook 阻止执行时，不调用 agent core，会话进入异常静止态并展示原因。
- Hook 要求人工确认时，会话进入待输入，确认后再启动主动作。
- Tool 调用前 Hook 超时按 Hook 的失败策略处理；没有明确允许时不得执行高风险 Tool。
- Agent 内部 Skill 或 Tool 调用失败时，保存结构化错误事件并按 Agent 输出策略处理。
- 用户尝试继续、改名或直接取消 `source=harness_pipeline_node` 的 Agent 会话时，系统拒绝并提示回到 Harness CICD 流水线任务页处理。
- Hook 人工确认超时后按 Hook 失败策略结束；迟到或重复提交返回已有终态，不再次执行主动作。
- 异步 Hook 失败不改变已完成会话或 Tool 调用结果。
- Hook 调用超过 5 层时，系统终止新调用并保留完整调用链。
- 快照生成失败时不调用外部服务，保留内部失败记录。
- 事件流中断时会话进入异常静止态，已保存 AgentSession 和 HookExecution 不丢失。
- R-0730 的功能或文件引用失效时，系统拒绝本轮提交并要求重新选择。

## 验收标准

### R-0630 正常路径

- 用户可以在新建 Agent 会话处的开始作业入口输入任务，并在 Agent 与 Harness CICD 流水线之间选择执行目标。
- 零配置空间可以使用平台默认 Agent 创建手工 Agent 会话。
- 用户可以通过 Agent 选择器选择空间默认 Agent 或其他已发布 Agent。
- 用户选择已发布 Harness CICD 流水线后，前端调用 F-008 创建 HarnessPipelineTask，不创建 `source=manual` 的 AgentSession。
- 用户可以在 R-0630 通过独立选择器选择零个或多个可读知识域，快照不加载未选择且未被 Agent 声明的知识域。
- 创建手工会话和继续输入时生成包含 AGENT.md、Agent、Skill、Tool、Hook 和环境变量引用的快照。
- 会话名称可以自动生成和由创建人修改。
- 创建人可以在静止态历史手工会话继续输入。
- 创建人可以回答选项型和开放式 Agent 询问。
- 同步 Hook 可以在会话开始、Tool 调用前和对象写入前继续、阻止或要求确认。
- 异步 Hook 可以在 Tool 调用后、对象写入后、会话完成或失败时执行通知、审计或 Skill。
- Hook 状态、决定和调用链可查询并审计。

### R-0630 边界场景

- 未选择 Agent 时，系统按空间默认 Agent、平台默认 Agent 顺序回退。
- 开始作业入口默认选中 Agent；空间没有任何已发布 Harness CICD 流水线时，不展示可选流水线或展示不可用空态。
- 会话开始后空间发布新 Harness 版本，当前会话和轮次继续使用原快照。
- Harness CICD 流水线任务启动后，用户回到 F-008 任务详情处理 Agent 任务审阅、取消和重试。
- Hook 等待确认映射为会话待输入，并展示等待原因。
- Hook 确认使用统一确认记录、幂等提交、超时和恢复机制。
- 异步 Hook 失败不回滚主动作。
- Harness CICD 流水线 Agent 任务自动创建的 Agent 会话不能从 F-005 入口继续、改名或取消。

### R-0630 异常场景

- 选择已停用或依赖失效的 Agent 时，系统阻止会话创建。
- 选择已停用、依赖失效或无权访问的 Harness CICD 流水线时，入口保留任务说明并提示重新选择。
- Harness 快照生成失败时，不调用 agent core。
- Hook 阻止会话时，系统不执行主动作并展示原因。
- Tool 调用前 Hook 未明确允许高风险动作时，系统不执行该 Tool 调用。
- agent core 创建、继续、询问回答或事件流失败时，系统保留内部记录。
- Hook 调用超过 5 层时，系统终止新调用。

### R-0630 权限场景

- 访客不能提交开始作业，既不能创建 Agent 会话，也不能启动 Harness CICD 流水线任务。
- 非会话创建人不能继续输入、回答询问或修改名称。
- 用户不能直接操作 `source=harness_pipeline_node` 的 Agent 会话运行控制。
- Hook 不能绕过当前用户、空间和 Tool 权限。
- AgentSession、AgentSessionTurn、HookExecution 和确认决定写入审计。

### R-0730 补充验收范围

- 用户在新建、继续输入或开放式回答中输入 `/`，可以选择当前可用 Agent 功能。
- 用户输入 `@`，可以选择当前空间内有读取权限的知识文件。
- 结构化功能和文件引用进入本轮 Harness 快照。
- 取消选择时按普通文本提交；只有选择没有文本时阻止提交。
- 功能或文件引用失效、跨空间、无权限或超限时阻止提交。

## UI 设计

### 是否需要刷新

- 结论：是
- 理由：R-0630 将新建 Agent 会话处调整为统一开始作业入口，需要同时承载 Agent 目标选择和 Harness CICD 流水线目标选择；Run 图和 Agent 任务审阅仍移交 F-008。

### 页面与交互

- 新建 Agent 会话主界面提供开始作业入口：
  - 顶部展示执行目标选择：Agent / Harness CICD 流水线。
  - 默认目标为 Agent，保持零配置开始作业路径最短。
  - 输入框文案统一为任务说明；提交按钮根据目标显示“开始 Agent 会话”或“运行流水线”。
- Agent 目标：
  - 默认展示空间默认 Agent 或平台默认 Agent。
  - 可切换其他已发布 Agent。
- Harness CICD 流水线目标：
  - 展示 F-008 返回的可启动已发布流水线、依赖状态和输入摘要。
  - 从 Harness CICD 流水线配置完成页点击“运行流水线”时，跳转到本入口并预选对应流水线。
  - 提交后进入 F-008 Harness CICD 流水线任务详情页。
- 会话详情：
  - 展示会话状态、当前 Agent、Harness 快照版本。
  - 待输入区分 Agent 询问和 Hook 人工确认。
  - Hook 决定展示 Hook 名称、触发事件、结果和原因。
  - 对 `source=harness_pipeline_node` 的会话详情展示只读来源提示和 Harness CICD 流水线任务链接，运行控制不可用。
- R-0730 输入框增加 `/` 功能菜单和 `@` 文件菜单，不替代 Agent 选择器。

### 状态与文案

- 会话状态：运行中、待输入、完成静止态、异常静止态。
- Hook 状态：待执行、运行中、允许、阻止、等待确认、成功、失败、跳过。
- 零配置提示：`当前使用平台默认 Agent`。
- Harness CICD 流水线来源提示：`该 Agent 会话由 Harness CICD 流水线 Agent 任务自动创建，请回到 Harness CICD 流水线任务页处理操作。`
- Hook 阻止提示：`操作被 Hook“{name}”阻止：{reason}`。

## 前端设计

### 是否需要刷新

- 结论：是
- 理由：需要统一开始作业入口、目标选择器、Agent 选择器、Harness CICD 流水线选择器、知识域选择、确认控件、Hook 决定和 AgentSession 新接口；Harness CICD 流水线运行组件仍由 F-008 负责。

### 页面、组件与状态

- `StartWorkTargetSelector`：在 Agent 与 Harness CICD 流水线之间切换执行目标。
- `AgentPicker`：选择默认 Agent、空间默认 Agent 或已发布 Agent。
- `HarnessPipelineTargetPicker`：查询并选择 F-008 提供的可启动已发布流水线。
- `KnowledgeDomainPicker`：R-0630 选择本轮知识域并保存 `knowledgeDomainIds`。
- `AgentSessionLauncher`、`ConversationInputBox`、`ConversationTitleEditor`。
- `AgentQuestionCard`、`RuntimeConfirmationCard`、`HookDecisionCard`。
- `RuntimeStatus`、`RuntimeErrorBanner`、`HarnessPipelineAgentTaskSourceBanner`。
- R-0730：`AgentFunctionSlashMenu`、`KnowledgeFileMentionMenu`、`InputReferenceChip`。
- 状态保存会话、轮次、Agent、快照、HookExecution、询问和确认草稿。

### 接口依赖与异常处理

- 查询可用 Agent 并创建手工 Agent 会话。
- 查询 F-008 可启动 Harness CICD 流水线；当目标为流水线时调用 F-008 `POST /team-spaces/{spaceId}/harness-pipeline-tasks`。
- 继续手工会话、提交 Agent 询问回答和修改会话名称。
- 查询 HookExecution 状态并提交统一运行确认。
- R-0730 查询功能目录和知识文件。
- 状态流中断时保留最后事件，并通过详情查询恢复。
- 选择失效时保留指令草稿并要求重新选择。
- 对 `source=harness_pipeline_node` 会话的继续、改名或取消请求返回不可操作状态。

## 后端设计

### 是否需要刷新

- 结论：是
- 理由：需要 AgentSession 数据模型、手工会话接口、来源字段、统一开始作业目标路由、Hook Engine 联动和 agent core 前置事件协议。

### 接口与数据

- 核心服务：
  - `AgentSessionService`：创建手工会话、继续输入和维护会话状态。
  - `AgentSelectionService`：解析默认 Agent、空间默认 Agent 或指定 Agent。
  - `StartWorkTargetService`：校验开始作业目标，并在 Harness CICD 流水线目标下转交 F-008。
  - `HarnessSnapshotService`：生成手工 Agent 会话快照。
  - `HookEngine`：匹配事件并执行同步或异步 Hook。
  - `AgentCoreAdapter`：执行手工 Agent 会话、接收 Agent 内部 Tool 调用和对象写入前置事件。
  - `MarketToolAdapter`：调用市场 Tool。
  - `ConversationTitleService`、`AgentQuestionService`、`AgentSessionEventService`。
  - `PermissionService`、`AuditService`。
- 核心数据对象：
  - `AgentSession`：会话 ID、租户、空间、创建人、来源、名称、状态、外部 conversation ID、最新更新时间。
  - `AgentSessionSource`：`manual`、`harness_pipeline_node`。
  - `AgentSessionTurn`：轮次 ID、会话 ID、用户输入、快照、状态和时间。
  - `HarnessSnapshot`：`knowledgeDomainIds`、AGENT.md、Agent、Skill、Tool、Hook 和环境变量引用版本。
  - `HookExecution`：Hook 版本、事件、作用对象、同步性、决定、调用链和结果。
  - `RuntimeConfirmation`：来源类型、来源执行 ID、题干、选项、状态、超时、决定、操作者和幂等键。
  - `AgentQuestion`、`AgentQuestionAnswer`。
- 建议接口：
  - `GET /team-spaces/{spaceId}/agents`
  - `POST /team-spaces/{spaceId}/agent-sessions`
  - F-008：`GET /team-spaces/{spaceId}/harness-pipelines?startable=true`
  - F-008：`POST /team-spaces/{spaceId}/harness-pipeline-tasks`
  - `POST /team-spaces/{spaceId}/agent-sessions/{sessionId}/turns`
  - `POST /team-spaces/{spaceId}/agent-sessions/{sessionId}/answers`
  - `PATCH /team-spaces/{spaceId}/agent-sessions/{sessionId}/title`
  - `POST /team-spaces/{spaceId}/runtime-confirmations/{confirmationId}/decisions`
  - R-0730：功能目录和知识文件查询接口。

### 业务规则、权限与异常处理

- 所有 Agent、Skill、Tool、Hook 和环境变量依赖必须来自同一租户与团队空间的已发布版本。
- 手工 Agent 会话创建前必须先保存 AgentSession、AgentSessionTurn 和 HarnessSnapshot，再调用 agent core。
- 统一开始作业入口仅负责路由目标；目标为 Harness CICD 流水线时，F-005 不保存手工会话记录，由 F-008 保存 HarnessPipelineTask 和 HarnessPipelineRun。
- `source=harness_pipeline_node` 的 AgentSession 只能由 F-008 创建，本功能接口不得创建该来源会话。
- Tool 调用和对象写入前，agent core 必须通过可等待的事件协议获取 Hook 决定。
- Hook 状态迁移使用持久化事件和幂等键，服务重启后可恢复。
- 确认决定提交后按 `sourceType` 恢复对应 HookExecution 或会话轮次；超时、迟到和重复决定均保持单一终态。
- 调用深度超过 5 或同一 Hook 重入时拒绝新调用。
- 前置 Hook 执行失败按失败策略决定阻止或警告；高风险 Tool 未得到明确允许时默认阻止。
- 后置 Hook 失败只记录，不改写主动作结果。
- 所有权限判断以后端当前权限为准，快照不能授予用户原本没有的权限。

## 评审与会签

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认开始作业入口文案、默认目标、Harness CICD 流水线来源只读提示和会话名称规则。 |
| UI | 待确认 | 需输出执行目标选择、Agent 选择、Harness CICD 流水线选择、询问回答、Hook 决定和来源提示交互稿。 |
| 前端 | 待确认 | 需确认 AgentSession 接口、F-008 流水线启动接口、事件订阅、来源字段和只读控制。 |
| 后端 | 待确认 | 需确认 agent core 会话协议、Hook 前置事件和 Tool 适配契约。 |
| 测试 | 待确认 | 需按 TC-005 覆盖零配置、手工会话、继续、询问、Hook、来源限制和权限。 |
