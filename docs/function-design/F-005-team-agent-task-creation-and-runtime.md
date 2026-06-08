# F-005-团队空间 Agent 任务创建与运行 功能设计

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 功能编号 | F-005 |
| 功能名称 | 团队空间 Agent 任务创建与运行 |
| 所属功能树节点 | AgentSpace / Agent 任务与对话协作 |
| 关联版本 | R-0630 / R-0730 |
| 状态 | 草稿 |
| 最近更新 | 2026-06-08 |

## 变更摘要

- 本次变更：适配 F-001 新 Harness 模型，在 R-0630 增加零配置默认 Agent、专用 Agent/Workflow 选择、Workflow Engine 真实运行和 Hook 生命周期执行。
- 2026-06-08 口径修正：Workflow 运行调整为多 Agent 节点编排，Skill 和 Tool 作为 Agent 执行过程中的能力调用，不作为用户侧 Workflow 节点。
- R-0730 继续承接 US-005-007 `/` Agent 功能选择和 US-005-008 `@` 知识文件引用，不与 R-0630 的任务启动选择器混合。
- 影响范围：任务创建、Harness 快照、agent core 事件协议、Workflow Run、人工确认、Tool 调用拦截、Hook 执行、状态展示、TC-005 和发布计划。
- 待确认问题：
  - 外部 agent core 的正式任务、继续对话、Agent 节点、Agent 内部 Tool 调用前置事件和对象写入事件协议。
  - agent core 询问事件字段和开放式回答长度限制。
  - 对话名称生成模型、提示词和内容安全策略。
  - SSE 在目标网关中的支持情况；不支持时保持事件语义迁移为 WebSocket。
  - Workflow 节点和 Hook 执行的默认超时、并发上限和重试间隔。
  - R-0730 的功能目录字段和知识文件大小、数量及上下文预算。

## PRD 设计

### 用户目标

- 创建者、管理员和团队成员可以在空间内输入指令创建 Agent 对话任务。
- 零配置空间可以直接使用平台默认 Agent，不要求存在知识库或自定义 Harness。
- 用户可以在任务创建前通过专用选择器使用空间默认 Agent、其他已发布 Agent 或已发布 Workflow。
- 系统为每次指令固定 AGENT.md、Agent、Skill、Tool、Workflow、Hook 和环境变量引用版本。
- Workflow Engine 可以确定性执行多 Agent 作业流程中的 Agent 节点、分支、重试、人工确认、暂停恢复和取消。
- Hook 可以在明确任务生命周期事件上执行阻止、确认、Skill、Workflow、通知和审计动作。
- 对话创建人可以继续历史对话、修改名称并回答 Agent 询问。
- 团队成员可以理解任务、Workflow 和 Hook 的运行状态及失败原因。
- R-0730 交付后，用户可以在输入框中通过 `/` 选择本轮功能，通过 `@` 引用知识文件。

### Use Story 拆分

| US 编号 | Use Story | 用户角色 | 用户价值 | 生成时间 | 交付版本 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| US-005-001 | 作为团队成员，我希望输入首条指令创建任务，以便使用默认或指定 Agent。 | 创建者、管理员、团队成员 | 快速开始作业 | 2026-06-04 | R-0630 | 零配置使用平台默认 Agent |
| US-005-002 | 作为对话创建人，我希望系统自动生成并允许修改对话名称，以便识别历史任务。 | 对话创建人 | 提升历史识别效率 | 2026-06-04 | R-0630 | 生成失败使用兜底规则 |
| US-005-003 | 作为对话创建人，我希望永久保留历史并继续输入，以便围绕同一上下文持续推进。 | 对话创建人 | 支持持续协作 | 2026-06-04 | R-0630 | 首版仅创建人可继续 |
| US-005-004 | 作为对话创建人，我希望回答 Agent 询问，以便任务继续运行。 | 对话创建人 | 不中断任务 | 2026-06-04 | R-0630 | 支持选项型和开放式 |
| US-005-005 | 作为平台后端，我希望每次输入固定 Harness 快照后调用 agent core，以便运行可追踪。 | AgentSpace 后端 | 保证版本一致和审计 | 2026-06-04 | R-0630 | 敏感值只保存引用 |
| US-005-006 | 作为团队成员，我希望看到任务关键状态和错误，以便理解当前进度。 | 团队成员 | 提升运行可理解性 | 2026-06-04 | R-0630 | 包含 Workflow 摘要 |
| US-005-007 | 作为具备输入权限的用户，我希望通过 `/` 选择本轮 Agent 功能。 | 创建者、管理员、团队成员、对话创建人 | 结构化指定本轮意图 | 2026-06-05 | R-0730 | 适用于普通文本输入 |
| US-005-008 | 作为具备输入权限的用户，我希望通过 `@` 引用知识文件。 | 创建者、管理员、团队成员、对话创建人 | 明确本轮知识上下文 | 2026-06-05 | R-0730 | 文件需属于当前空间 |
| US-005-009 | 作为团队成员，我希望主动选择已发布 Workflow 启动任务，以便按标准流程完成复杂工作。 | 创建者、管理员、团队成员 | 执行确定性多步骤流程 | 2026-06-07 | R-0630 | 使用专用启动选择器 |
| US-005-010 | 作为团队管理员，我希望已发布 Hook 在真实任务生命周期中执行，以便自动落实治理规则。 | 团队创建者、团队管理员 | 稳定执行校验和自动化 | 2026-06-07 | R-0630 | Hook 配置由 F-001 管理 |

### Use Case 编写

| UC 编号 | 关联 US | Use Case | 参与者 | 前置条件 | 业务流程 | 后置结果 |
| --- | --- | --- | --- | --- | --- | --- |
| UC-005-001 | US-005-001、US-005-002、US-005-005 | 创建 Agent 任务 | 团队成员、AgentSpace 后端、agent core | 用户有任务创建权限 | 1. 用户选择默认 Agent 或已发布 Agent、可选知识域并输入指令。<br>2. 后端校验执行对象、`knowledgeDomainIds` 和指令。<br>3. 保存任务与轮次。<br>4. 生成 Harness 快照和对话名称。<br>5. 执行任务开始 Hook。<br>6. 无阻止结果时调用 agent core。 | 任务进入运行中；零配置使用平台默认 Agent |
| UC-005-002 | US-005-003、US-005-005 | 在历史对话继续输入 | 对话创建人、AgentSpace 后端、agent core | 任务静止且存在外部 conversation ID | 1. 创建人提交新指令。<br>2. 后端校验权限和状态。<br>3. 创建新轮次和新快照。<br>4. 执行任务开始 Hook。<br>5. 调用 agent core 继续会话。 | 新轮次进入运行中，历史轮次版本不变 |
| UC-005-003 | US-005-004 | 回答 Agent 询问 | 对话创建人、AgentSpace 后端、agent core | 任务处于待输入 | 1. 系统保存询问。<br>2. 前端展示选项或文本控件。<br>3. 创建人提交回答。<br>4. 后端校验幂等键和内容。<br>5. 开放式回答生成回答级快照并回传。 | 任务重新进入运行中；失败时保持待输入 |
| UC-005-004 | US-005-002 | 修改对话名称 | 对话创建人 | 对话已创建 | 1. 编辑名称。<br>2. 后端校验 4-10 个字。<br>3. 保存并审计。 | 列表和详情展示最新名称 |
| UC-005-005 | US-005-006 | 接收运行事件 | AgentSpace 后端、agent core | 任务运行中 | 1. 接收状态、询问、Tool 调用、对象写入、输出、完成或错误事件。<br>2. 保存事件。<br>3. 在对应事件执行 Hook。<br>4. 更新任务和运行状态。 | 前端获得可理解状态和执行结果 |
| UC-005-006 | US-005-009、US-005-005 | 启动 Workflow 任务 | 团队成员、Workflow Engine | 用户有任务创建权限；Workflow 已发布 | 1. 用户在专用选择器选择 Workflow、可选知识域并填写输入。<br>2. 后端校验 `knowledgeDomainIds`，保存 AgentTask 和首轮。<br>3. 固定 Workflow 及全部依赖版本。<br>4. 创建 WorkflowRun。<br>5. 执行任务开始 Hook。<br>6. Workflow Engine 启动首个节点。 | AgentTask 进入运行中并关联 WorkflowRun |
| UC-005-007 | US-005-009 | 执行 Workflow 节点 | Workflow Engine、agent core、Tool 适配层 | WorkflowRun 处于运行中 | 1. Engine 获取可运行节点。<br>2. Agent 节点调用 agent core。<br>3. Agent 执行过程中可调用已授权 Skill 和市场 Tool。<br>4. Tool 调用经 Hook 决策后继续或阻止。<br>5. 条件节点按结构化表达式选择分支。<br>6. 保存节点输入输出和状态并调度后续节点。 | 全部终点成功则完成；节点失败按策略处理 |
| UC-005-008 | US-005-009 | 等待人工确认并恢复 Workflow | 对话创建人、Workflow Engine | 运行到人工确认节点 | 1. WorkflowRun 进入等待确认。<br>2. 前端展示事项、影响和选项。<br>3. 创建人批准或拒绝。<br>4. 后端使用幂等键保存决定。<br>5. Engine 恢复对应分支。 | Workflow 继续、结束或失败 |
| UC-005-009 | US-005-009 | 取消或重试 Workflow | 对话创建人、Workflow Engine | Workflow 未完成 | 1. 用户取消，或对可重试失败节点发起重试。<br>2. 后端校验状态和幂等策略。<br>3. 更新 Run 与节点状态。 | Run 取消，或从目标节点继续 |
| UC-005-010 | US-005-010 | 执行同步 Hook | Hook Engine、AgentSpace 后端 | 命中任务开始、Tool 调用前或对象写入前事件 | 1. 匹配当前快照中的 Hook。<br>2. 按优先级计算条件。<br>3. 执行注入、校验、阻止、确认、Skill 或 Workflow 动作。<br>4. 返回继续、阻止或等待确认决定。 | 主动作按 Hook 决定继续或暂停 |
| UC-005-011 | US-005-010 | 执行异步 Hook | Hook Engine、AgentSpace 后端 | 命中 Tool 调用后、对象写入后、任务完成或失败事件 | 1. 保存主事件。<br>2. 异步创建 HookExecution。<br>3. 执行通知、审计、Skill 或 Workflow。<br>4. 保存结果。 | Hook 失败被记录，但不回滚已完成主动作 |
| UC-005-012 | US-005-007、US-005-008 | 输入框结构化选择 | 团队成员、对话创建人 | R-0730 输入增强启用 | 1. `/` 展示可用功能。<br>2. `@` 搜索知识文件。<br>3. 用户选择后保存结构化引用。<br>4. 后端提交前重新校验。 | 有效选择进入本轮快照 |

### 业务规则

- 任务入口：
  - 创建者、管理员和团队成员可以新建任务；访客不可创建。
  - 任务启动选择器提供“默认 Agent / 已发布 Agent / 已发布 Workflow”。
  - R-0630 提供独立的可选知识域选择器，请求保存 `knowledgeDomainIds`；它不依赖 R-0730 的 `@` 文件引用。
  - 未选择执行对象时使用空间默认 Agent；空间未指定默认 Agent 时使用平台默认 Agent。
  - R-0630 的启动选择器在提交前选择执行对象；R-0730 的 `/` 用于文本输入中选择本轮功能，两者独立保存。
- 对话与输入：
  - 一个对话对应一个 AgentTask，可以包含多轮用户指令。
  - 首版只有任务创建人可以继续输入、回答询问、处理 Workflow 人工确认、取消或重试 Workflow。
  - 继续输入仅允许任务处于完成静止态或异常静止态且存在 conversation ID。
  - 用户输入和开放式回答必须为非空文本。
  - 对话名称生成失败时，优先取首条指令前 8 个字；不足 4 个字时使用“新建对话”。
- Harness 快照：
  - 每轮指令和开放式回答生成独立 Harness 快照。
  - 快照固定解析后的空间级和命中知识域 AGENT.md、选中 Agent、相关 Skill、Tool、Workflow、Hook 版本和环境变量引用。
  - 命中知识域由请求中的 `knowledgeDomainIds`、执行对象声明的知识域及 R-0730 `@` 文件所属知识域取并集后去重，系统不通过自然语言猜测知识域。
  - 零配置空间的快照使用平台默认 Agent，其余资源集合为空。
  - 敏感环境变量只保存安全引用。
  - 快照创建后不受空间后续配置变化影响。
- Workflow：
  - Workflow Engine 执行 Agent、条件、人工确认、并行、子 Workflow 和输出节点，用于编排产品 Agent、开发 Agent、运营 Agent 等大颗粒度作业流程。
  - Workflow 不提供用户侧 Skill 节点或 Tool 节点；Skill 和 Tool 是 Agent 节点执行过程中的可用能力或依赖。
  - Agent 节点调用 agent core；Agent 内部 Tool 调用通过市场 Tool 适配层执行；条件与状态迁移由 Engine 计算。
  - WorkflowRun 状态为 `pending`、`running`、`waiting_input`、`waiting_approval`、`completed`、`failed`、`cancelled`。
  - WorkflowNodeRun 状态为 `pending`、`ready`、`running`、`waiting`、`completed`、`failed`、`skipped`、`cancelled`。
  - Workflow 启动时固定定义和依赖版本，运行中不切换。
  - 每个可重试 Agent 节点或写入动作必须有幂等键；重试不得产生重复外部副作用。
  - 并行节点独立保存状态；失败策略决定等待全部、快速失败或允许部分成功。
  - 子 Workflow 和 Hook 触发 Workflow 共享调用链；最大组合深度为 5。
- Hook：
  - Hook 只消费 AgentSpace 或 agent core 明确定义的事件，不从自然语言推测事件。
  - 支持任务开始、Tool 调用前后、对象写入前后、任务完成、任务失败事件；`harness.publish.before/after` 由 F-001 发出，但复用同一 Hook Engine、`HookExecution` 和确认服务。
  - `before` Hook 同步执行，可继续、阻止或等待确认；`after` Hook 异步执行，不回滚主动作。
  - Tool 调用前和对象写入前，agent core 必须等待 AgentSpace Hook 决定后继续。
  - 同一 Hook 在同一调用链最多执行一次；超过 5 层或检测到重复时终止新调用并审计。
  - Hook 不允许执行任意脚本；仅执行 F-001 发布定义中的结构化动作。
  - Hook 使用任务快照中的版本，运行中发布的新 Hook 不参与当前任务。
- 状态映射：
  - AgentTask UI 状态保持运行中、待输入、完成静止态和异常静止态。
  - Workflow 等待人工确认或 Hook 要求确认时，AgentTask 映射为待输入，并展示具体等待类型。
  - Workflow 完成映射为完成静止态；失败或取消映射为异常静止态并保留具体原因。
- 事件与持久化：
  - 后端必须先保存任务、轮次、快照或 Run，再调用外部执行服务。
  - agent core 的认证失败、调用失败、超时和事件流中断必须映射为内部错误事件。
  - Workflow 节点、Hook 决定、人工确认和外部副作用必须可审计。
  - Workflow 与 Hook 人工确认统一创建 `RuntimeConfirmation`；提交接口按来源类型恢复对应 WorkflowRun、HookExecution 或 Harness 发布请求。
- R-0730 输入增强：
  - `/` 可选择当前空间已发布 Agent、Skill、Workflow 或 Agent 内置能力，每轮至多一个。
  - `@` 可引用当前空间内用户可读的知识文件，每轮最多 10 个。
  - 结构化选择与自然语言冲突时，结构化选择作为路由信息，文本保留为任务说明。
  - 仅有选择而没有有效文本时不允许提交。

### 边界与异常

- 零配置空间创建任务时，快照生成平台默认 Agent 和空扩展集合。
- 选择的 Agent 或 Workflow 已停用、版本不可用或依赖失效时，系统阻止提交并保留指令草稿。
- `knowledgeDomainIds` 包含不存在、跨空间或无权访问的知识域时，系统阻止提交并定位无效项。
- 任务开始 Hook 阻止执行时，不调用 agent core 或 Workflow Engine，任务进入异常静止态并展示原因。
- Hook 要求人工确认时，任务进入待输入，确认后再启动主动作。
- Tool 调用前 Hook 超时按 Hook 的失败策略处理；没有明确允许时不得执行高风险 Tool。
- Agent 节点失败时，Workflow 按节点重试、备用分支或终止策略处理；Agent 内部 Skill 或 Tool 调用失败由该 Agent 节点输出结构化错误。
- 条件节点表达式无效、输入缺失或返回未知分支时，节点失败并记录诊断。
- 人工确认重复提交同一幂等键时返回已有决定，不重复推进流程。
- Hook 人工确认超时后按 Hook 失败策略结束；迟到或重复提交返回已有终态，不再次执行主动作。
- Workflow 取消后不再调度新节点；已经提交的外部副作用按节点补偿策略处理。
- 异步 Hook 失败不改变已完成任务或 Tool 调用结果。
- Hook 或子 Workflow 超过 5 层时，系统终止新调用并保留完整调用链。
- 快照生成失败时不调用外部服务，保留内部失败记录。
- 事件流中断时任务进入异常静止态，已保存 WorkflowNodeRun 和 HookExecution 不丢失。
- R-0730 的功能或文件引用失效时，系统拒绝本轮提交并要求重新选择。

## 验收标准

### R-0630 正常路径

- 零配置空间可以使用平台默认 Agent 创建任务。
- 用户可以通过专用选择器选择空间默认 Agent、其他已发布 Agent 或已发布 Workflow。
- 用户可以在 R-0630 通过独立选择器选择零个或多个可读知识域，快照不加载未选择且未被执行对象声明的知识域。
- 创建任务和继续输入时生成包含七类 Harness 版本的快照。
- 对话名称可以自动生成和由创建人修改。
- 创建人可以在静止态历史对话继续输入。
- 创建人可以回答选项型和开放式 Agent 询问。
- 选择 Workflow 后创建 WorkflowRun，并按图执行 Agent、条件、人工确认、并行、子 Workflow 和输出节点。
- 人工确认节点暂停 Workflow，创建人处理后正确恢复分支。
- 可重试 Agent 节点使用幂等键重试，不产生重复外部副作用。
- 同步 Hook 可以在任务开始、Tool 调用前和对象写入前继续、阻止或要求确认。
- 异步 Hook 可以在 Tool 调用后、对象写入后、任务完成或失败时执行通知、审计、Skill 或 Workflow。
- Workflow 和 Hook 的状态、输入输出、决定和调用链可查询并审计。

### R-0630 边界场景

- 未选择 Agent 或 Workflow 时，系统按空间默认 Agent、平台默认 Agent顺序回退。
- 任务开始后空间发布新 Harness 版本，当前任务和 WorkflowRun 继续使用原快照。
- Workflow 并行分支按配置等待全部、快速失败或允许部分成功。
- Workflow 等待确认和 Hook 等待确认都映射为任务待输入，并展示不同等待原因。
- Workflow 与 Hook 确认使用统一确认记录、幂等提交、超时和恢复机制。
- 异步 Hook 失败不回滚主动作。
- Workflow 被取消后不再创建新节点运行。

### R-0630 异常场景

- 选择已停用或依赖失效的 Agent、Workflow 时，系统阻止任务创建。
- Harness 快照生成失败时，不调用 agent core 或 Workflow Engine。
- Hook 阻止任务时，系统不执行主动作并展示原因。
- Tool 调用前 Hook 未明确允许高风险动作时，系统不执行该 Tool 调用。
- Workflow Agent 节点失败、条件无效、确认超时或事件流中断时，系统保存失败状态和诊断。
- Hook 或子 Workflow 调用超过 5 层时，系统终止新调用。
- 外部 agent core 创建、继续、节点执行或回答回传失败时，系统保留内部记录。

### R-0630 权限场景

- 访客不能创建 Agent 或 Workflow 任务。
- 非任务创建人不能继续输入、回答询问、处理 Workflow 确认、取消或重试。
- Hook 和 Workflow 不能绕过当前用户、空间和 Tool 权限。
- 任务、WorkflowRun、WorkflowNodeRun、HookExecution 和确认决定写入审计。

### R-0730 补充验收范围

- 用户在新建对话、继续输入或开放式回答中输入 `/`，可以选择当前可用功能。
- 用户输入 `@`，可以选择当前空间内有读取权限的知识文件。
- 结构化功能和文件引用进入本轮 Harness 快照。
- 取消选择时按普通文本提交；只有选择没有文本时阻止提交。
- 功能或文件引用失效、跨空间、无权限或超限时阻止提交。

## UI 设计

### 是否需要刷新

- 结论：是
- 理由：R-0630 新增执行对象选择、Workflow 运行视图、人工确认和 Hook 决定展示；R-0730 保留 `/` 与 `@` 输入增强。

### 页面与交互

- 新建对话主界面提供执行对象选择器：
  - 默认展示空间默认 Agent 或平台默认 Agent。
  - 可切换其他已发布 Agent 或 Workflow。
  - Workflow 展示输入字段、流程摘要和可能的人工确认节点。
- 对话详情：
  - 展示任务状态、当前 Agent 或 Workflow、Harness 快照版本。
  - Workflow 展示多 Agent 节点图、当前 Agent 节点、分支、重试和输出。
  - 待输入区分 Agent 询问、Workflow 人工确认和 Hook 人工确认。
  - Hook 决定展示 Hook 名称、触发事件、结果和原因。
- R-0730 输入框增加 `/` 功能菜单和 `@` 文件菜单，不替代执行对象选择器。

### 状态与文案

- 任务状态：运行中、待输入、完成静止态、异常静止态。
- Workflow 状态：等待执行、运行中、等待输入、等待确认、完成、失败、已取消。
- Hook 状态：待执行、运行中、允许、阻止、等待确认、成功、失败、跳过。
- 零配置提示：`当前使用平台默认 Agent`。
- Hook 阻止提示：`操作被 Hook“{name}”阻止：{reason}`。

## 前端设计

### 是否需要刷新

- 结论：是
- 理由：需要执行对象选择器、Workflow 图和状态、确认控件、Hook 决定及新增接口。

### 页面、组件与状态

- `ExecutionTargetPicker`：选择默认 Agent、已发布 Agent 或 Workflow。
- `KnowledgeDomainPicker`：R-0630 选择本轮知识域并保存 `knowledgeDomainIds`。
- `AgentConversationLauncher`、`ConversationInputBox`、`ConversationTitleEditor`。
- `WorkflowRunGraph`、`WorkflowNodeDetail`、`RuntimeConfirmationCard`、`WorkflowRunActions`。
- `HookDecisionCard`、`RuntimeStatus`、`RuntimeErrorBanner`。
- R-0730：`AgentFunctionSlashMenu`、`KnowledgeFileMentionMenu`、`InputReferenceChip`。
- 状态保存任务、轮次、执行对象、WorkflowRun、节点、HookExecution、询问和确认草稿。

### 接口依赖与异常处理

- 查询可用执行对象并创建 Agent 或 Workflow 任务。
- 查询 WorkflowRun、节点和 HookExecution 状态。
- 提交统一运行确认、取消 Run 和重试节点。
- 提交 Agent 询问回答、继续输入和修改名称。
- R-0730 查询功能目录和知识文件。
- 状态流中断时保留最后事件，并通过详情查询恢复。
- 选择失效时保留指令草稿并要求重新选择。

## 后端设计

### 是否需要刷新

- 结论：是
- 理由：新增 Workflow Engine、Hook Engine、运行数据、快照结构和 agent core 前置事件协议。

### 接口与数据

- 核心服务：
  - `AgentTaskService`：创建任务、继续输入和维护任务状态。
  - `ExecutionTargetService`：解析默认 Agent、指定 Agent 或 Workflow。
  - `HarnessSnapshotService`：生成七类 Harness 快照。
  - `WorkflowEngine`：调度节点、分支、重试、并行、确认、暂停和恢复。
  - `HookEngine`：匹配事件并执行同步或异步 Hook。
  - `AgentCoreAdapter`：执行 Agent 节点、接收 Agent 内部 Tool 调用和对象写入前置事件。
  - `MarketToolAdapter`：调用市场 Tool。
  - `ConversationTitleService`、`AgentQuestionService`、`AgentTaskEventService`。
  - `PermissionService`、`AuditService`。
- 核心数据对象：
  - `AgentTask`、`AgentTaskTurn`、`AgentTaskStatus`。
  - `HarnessSnapshot`：`knowledgeDomainIds`、AGENT.md、Agent、Skill、Tool、Workflow、Hook 和环境变量引用版本。
  - `WorkflowRun`：Run ID、任务、Workflow 版本、状态、输入输出、当前调用深度和时间。
  - `WorkflowNodeRun`：节点、状态、输入输出、幂等键、重试次数和错误。
  - `RuntimeConfirmation`：来源类型、来源执行 ID、题干、选项、状态、超时、决定、操作者和幂等键；可关联 WorkflowRun、HookExecution 或 Harness 发布请求。
  - `HookExecution`：Hook 版本、事件、作用对象、同步性、决定、调用链和结果。
  - `RuntimeCallChain`：根任务、父调用、深度和已执行 Hook 集合。
  - `AgentQuestion`、`AgentQuestionAnswer`。
- 建议接口：
  - `GET /team-spaces/{spaceId}/execution-targets`
  - `POST /team-spaces/{spaceId}/agent-tasks`
  - `POST /team-spaces/{spaceId}/agent-tasks/{taskId}/turns`
  - `GET /team-spaces/{spaceId}/agent-tasks/{taskId}/workflow-runs/{runId}`
  - `POST /team-spaces/{spaceId}/runtime-confirmations/{confirmationId}/decisions`
  - `POST /team-spaces/{spaceId}/workflow-runs/{runId}/cancel`
  - `POST /team-spaces/{spaceId}/workflow-runs/{runId}/nodes/{nodeRunId}/retry`
  - `POST /team-spaces/{spaceId}/agent-tasks/{taskId}/answers`
  - R-0730：功能目录和知识文件查询接口。

### 业务规则、权限与异常处理

- 所有执行对象和依赖必须来自同一租户与团队空间的已发布版本。
- Tool 调用和对象写入前，agent core 必须通过可等待的事件协议获取 Hook 决定。
- Workflow 与 Hook 的状态迁移使用持久化事件和幂等键，服务重启后可恢复。
- 确认决定提交后按 `sourceType` 恢复对应执行；超时、迟到和重复决定均保持单一终态。
- 并行节点、异步 Hook 和外部回调必须携带 Run、节点或 HookExecution 标识。
- 取消 Workflow 不自动撤销已完成外部副作用，补偿只能按已配置策略执行。
- 调用深度超过 5 或同一 Hook 重入时拒绝新调用。
- 前置 Hook 执行失败按失败策略决定阻止或警告；高风险 Tool 未得到明确允许时默认阻止。
- 后置 Hook 失败只记录，不改写主动作结果。
- 所有权限判断以后端当前权限为准，快照不能授予用户原本没有的权限。

## 评审与会签

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认执行对象选择、Workflow/Hook 等待文案和取消补偿口径。 |
| UI | 待确认 | 需输出 Workflow 运行图、人工确认和 Hook 决定交互稿。 |
| 前端 | 待确认 | 需确认事件订阅、Run 状态恢复和幂等提交。 |
| 后端 | 待确认 | 需确认 Workflow Engine、Hook Engine、agent core 前置事件和 Tool 适配契约。 |
| 测试 | 待确认 | 需按 TC-005 覆盖零配置、Workflow、Hook、恢复和权限。 |
