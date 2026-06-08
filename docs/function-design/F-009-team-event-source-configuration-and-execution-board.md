# F-009-团队空间事件源配置与事件执行看板 功能设计

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 功能编号 | F-009 |
| 功能名称 | 团队空间事件源配置与事件执行看板 |
| 所属功能树节点 | AgentSpace / 事件源与自动触发执行 |
| 关联版本 | R-0630 |
| 状态 | 草稿 |
| 最近更新 | 2026-06-08 |

## 变更摘要

- 本次变更：将 F-009 纳入 R-0630，首版事件源明确对接公司 eDevOps 系统的 FE（Feature/需求项）。
- 核心口径：
  - 事件源负责把 eDevOps FE 中的新需求项同步到 AgentSpace，不替代 eDevOps 自身的 Feature 创建、审批、流转或状态治理。
  - 每类 eDevOps FE 事件进入后可以触发新建 `source=event_source` 的 Agent 会话，或触发新建来源为事件源的 AgentFlowTask。
  - 事件执行看板只聚合事件触发任务状态；点击 Agent 会话任务复用 F-006 Agent 会话详情，点击 AgentFlow 任务复用 F-008 AgentFlow 任务详情。
  - 事件执行看板状态固定为 backlog、agent working、waiting approval、done。
  - 同一事件类型的并发上限用于限制同一时间触发的 Agent 会话和 AgentFlowTask 数量，超出部分保留在 backlog。
- 影响范围：产品定义、功能索引、F-002 权限矩阵、F-006 Agent 会话来源与详情展示、F-008 AgentFlow 任务来源、R-0630 发布计划、TC-009。

## PRD 设计

### 用户目标

- 团队创建者或团队管理员可以在团队空间内配置 eDevOps FE 事件源，将公司 eDevOps 系统的新 Feature/需求项接入 AgentSpace。
- 管理员可以按 eDevOps FE 事件类型设置同步时间、过滤条件、去重键、输入映射、触发目标和启停状态。
- 管理员可以为每类事件设置同一时间可触发的 Agent 会话数量或 AgentFlowTask 数量，避免外部事件风暴压垮执行资源。
- 系统可以按计划同步 eDevOps FE 事件，将未触发的事件任务放入 backlog，并在并发额度允许时自动创建 Agent 会话或 AgentFlowTask。
- 空间用户可以通过事件执行看板查看事件触发任务在 backlog、agent working、waiting approval 和 done 中的处理状态。
- 空间用户可以从事件执行看板点击查看某个 Agent 会话任务或 AgentFlowTask 的原子执行详情。

### Use Story 拆分

| US 编号 | Use Story | 用户角色 | 用户价值 | 生成时间 | 交付版本 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| US-009-001 | 作为团队管理员，我希望配置 eDevOps FE 事件源，以便把公司需求项自动接入团队空间。 | 团队创建者、团队管理员 | 减少人工搬运事件 | 2026-06-08 | R-0630 | 首版只支持 eDevOps FE |
| US-009-002 | 作为团队管理员，我希望按 FE 事件类型设置同步时间和触发规则，以便需求项按合适节奏进入执行。 | 团队创建者、团队管理员 | 自动化任务入口 | 2026-06-08 | R-0630 | 包含过滤、去重和输入映射 |
| US-009-003 | 作为团队管理员，我希望每类事件可以选择触发 Agent 会话或 AgentFlowTask，以便把不同需求项交给合适的执行方式。 | 团队创建者、团队管理员 | 匹配处理复杂度 | 2026-06-08 | R-0630 | 触发目标二选一 |
| US-009-004 | 作为团队管理员，我希望限制每类事件同一时间触发的任务数量，以便控制资源占用和人工审批压力。 | 团队创建者、团队管理员 | 控制执行并发 | 2026-06-08 | R-0630 | 超限任务留在 backlog |
| US-009-005 | 作为空间用户，我希望在事件执行看板查看事件触发任务状态，以便知道哪些任务待执行、执行中、等待审批或已完成。 | 创建者、管理员、团队成员、访客 | 执行透明可追踪 | 2026-06-08 | R-0630 | 访客只读 |
| US-009-006 | 作为空间用户，我希望点击事件触发任务查看原子执行详情，以便查看 Agent 会话或 AgentFlow 的真实处理过程。 | 创建者、管理员、团队成员、访客 | 复用已有详情能力 | 2026-06-08 | R-0630 | 详情由 F-006 或 F-008 承接 |
| US-009-007 | 作为团队管理员，我希望暂停或启用事件源及单类事件触发，以便在外部系统异常或任务积压时保护执行环境。 | 团队创建者、团队管理员 | 降低事件风暴风险 | 2026-06-08 | R-0630 | 暂停后不再触发新任务 |

### Use Case 编写

| UC 编号 | 关联 US | Use Case | 参与者 | 前置条件 | 业务流程 | 后置结果 |
| --- | --- | --- | --- | --- | --- | --- |
| UC-009-001 | US-009-001 | 配置 eDevOps FE 事件源 | 团队管理员、AgentSpace 后端 | 用户具备事件源配置权限；租户已完成 eDevOps 授权 | 1. 管理员选择事件源类型 eDevOps FE。<br>2. 填写事件源名称、授权引用、项目范围、同步范围和启停状态。<br>3. 系统校验授权、字段 Schema 和空间权限。<br>4. 管理员执行试同步并查看样例 FE。<br>5. 保存事件源配置。 | EventSource 进入可配置事件类型状态 |
| UC-009-002 | US-009-002、US-009-003、US-009-004 | 配置 FE 事件类型触发策略 | 团队管理员、AgentSpace 后端 | 事件源已保存；目标 Agent 或 AgentFlow 已发布且可用 | 1. 管理员选择 eDevOps FE 事件类型，例如新建 FE、FE 状态变更。<br>2. 设置自动同步时间、过滤条件和去重键。<br>3. 选择触发目标为 Agent 会话或 AgentFlowTask。<br>4. 配置目标 Agent 或目标 AgentFlow、输入字段映射、知识域和幂等键。<br>5. 设置同一时间可触发的会话数量或 AgentFlowTask 数量。<br>6. 系统校验目标权限、依赖和输入 Schema。 | EventTriggerRule 保存为启用或停用状态 |
| UC-009-003 | US-009-002 | 自动同步 FE 事件进入 backlog | EventSourceSyncWorker、AgentSpace 后端 | 事件源和事件类型规则已启用；到达同步时间 | 1. 同步任务按计划拉取 eDevOps FE。<br>2. 系统按事件类型过滤、转换字段并计算去重键。<br>3. 已存在事件更新同步水位和摘要，不重复创建执行任务。<br>4. 新事件创建 EventExecutionTask。<br>5. 任务初始状态为 backlog。 | 新 FE 事件进入事件执行看板 backlog |
| UC-009-004 | US-009-003、US-009-004 | 按并发额度触发事件任务 | EventTriggerScheduler、AgentSpace 后端、agent core、AgentFlowEngine | backlog 中存在待触发任务；目标依赖可用 | 1. 调度器按事件类型计算当前运行中的 Agent 会话和 AgentFlowTask 数量。<br>2. 未超过并发上限时领取 backlog 任务。<br>3. 目标为 Agent 时创建 `source=event_source` 的 AgentSession 和 HarnessSnapshot。<br>4. 目标为 AgentFlow 时创建来源为事件源的 AgentFlowTask 和 AgentFlowRun。<br>5. 保存事件任务与原子执行对象的关联。<br>6. 原子执行对象进入运行后，事件任务状态映射为 agent working。 | 事件任务开始自动执行；超限任务保留 backlog |
| UC-009-005 | US-009-005 | 查看事件执行看板 | 空间用户、AgentSpace 后端 | 用户具备团队空间访问权限 | 1. 用户进入事件执行看板。<br>2. 后端按权限返回事件任务、状态、来源、事件类型、触发目标、等待原因和最新更新时间。<br>3. 前端按 backlog、agent working、waiting approval、done 分组展示卡片。<br>4. 用户可按事件源、事件类型、触发目标和状态筛选。 | 用户理解事件触发任务的整体处理状态 |
| UC-009-006 | US-009-006 | 查看事件任务原子详情 | 空间用户、AgentSpace 前端、F-006、F-008 | 用户具备目标原子对象访问权限 | 1. 用户点击事件任务卡片。<br>2. 若任务关联 AgentSession，系统跳转 F-006 Agent 会话详情。<br>3. 若任务关联 AgentFlowTask，系统跳转 F-008 AgentFlow 任务详情。<br>4. 原子详情展示执行过程、waiting approval 卡点、失败原因和最终结果。 | 用户在原子详情页查看真实执行过程 |
| UC-009-007 | US-009-007 | 暂停或启用事件触发 | 团队管理员、AgentSpace 后端 | 用户具备事件源配置权限 | 1. 管理员暂停事件源或单类事件触发规则。<br>2. 系统停止该范围内新同步或新触发。<br>3. 已运行中的 Agent 会话或 AgentFlowTask 继续由原子功能处理。<br>4. 管理员重新启用后，系统从保存的同步水位继续同步。 | 管理员可以控制事件流入和触发节奏 |

### 业务规则

- 权限与配置范围：
  - 团队创建者和团队管理员可以在团队空间内配置事件源、事件类型触发规则、并发上限和启停状态。
  - 团队成员和访客不可修改事件源配置；团队成员可按空间权限查看事件执行看板，访客只读。
  - 租户级 eDevOps 授权由租户管理员维护；团队空间配置只保存授权引用，不保存 eDevOps 凭据明文。
- 事件源与事件类型：
  - R-0630 只支持 eDevOps FE 事件源。
  - 首批事件类型支持新建 FE 和 FE 状态变更；其他 eDevOps 对象、需求系统和缺陷系统作为后续扩展。
  - 事件类型规则必须声明同步时间、过滤条件、去重键、触发目标、输入映射、并发上限和启停状态。
  - 自动同步时间支持固定间隔和每日定时。
- 去重与同步：
  - 去重键默认由 `eventSourceId + externalEventType + externalFeatureId` 组成。
  - 同步任务必须保存水位、外部更新时间和最近同步结果，避免重复创建任务。
  - 外部 FE 字段变化时更新 EventExecutionTask 摘要和 payload 快照，不覆盖已启动原子执行对象的执行快照。
  - 同步失败需要保留错误、重试次数和下一次同步时间，不清空已有 backlog。
- 字段白名单：
  - 默认允许进入看板摘要和 prompt 的字段为 FE ID、标题、描述摘要、优先级、状态、创建人、更新时间、项目标识和链接。
  - eDevOps 凭据、审批意见中的敏感字段、附件内容、用户隐私字段和平台未白名单字段不得进入 prompt、日志或看板摘要。
- 触发目标：
  - 每条事件类型规则只能选择一种默认触发目标：Agent 会话或 AgentFlowTask。
  - 触发 Agent 会话时，系统创建 `source=event_source` 的 AgentSession，并记录 eventSourceId、eventExecutionTaskId、externalFeatureId 和触发规则版本。
  - 触发 AgentFlowTask 时，系统创建来源为事件源的 AgentFlowTask，并记录 eventSourceId、eventExecutionTaskId、externalFeatureId 和触发规则版本。
  - 触发目标的 Agent、AgentFlow、知识域、Tool 和环境变量必须在触发时重新校验权限和依赖可用性。
- 并发与调度：
  - 每个事件类型规则分别配置同一时间可触发的 Agent 会话数量或 AgentFlowTask 数量。
  - 当前运行数量达到上限时，新事件任务保持 backlog，不创建原子执行对象。
  - backlog 默认按外部事件创建时间、进入 AgentSpace 时间和重试次数排序。
  - 暂停事件源或事件类型规则后，不再创建新的 EventExecutionTask 或触发新原子执行对象；已运行对象不被自动取消。
- 事件看板状态：
  - backlog：事件已同步并等待触发，或因并发上限、暂停、依赖待恢复而暂未触发。
  - agent working：事件已触发原子执行对象，Agent 会话或 AgentFlowTask 处于自动执行中。
  - waiting approval：原子执行对象进入等待人工审阅、确认或 Agent 询问的卡点；实际处理入口在 F-006 或 F-008 原子详情中。
  - done：原子执行对象已完成，或人工审阅/确认后被判定完成；完成确认来源必须可审计。
  - 事件看板状态由关联 AgentSession 或 AgentFlowTask 状态映射得到，不允许看板与原子执行对象出现互相矛盾的终态。
- 原子详情复用：
  - 事件看板不展示完整 Agent 运行日志、Run 图、节点输出或文档 diff。
  - 点击 Agent 会话任务进入 F-006 Agent 会话详情；F-006 识别 `source=event_source` 并展示 eDevOps FE 摘要。
  - 点击 AgentFlowTask 进入 F-008 AgentFlow 任务详情；F-008 识别任务来源为事件源并展示 eDevOps FE 摘要。

### 边界与异常

- eDevOps 授权失效、外部系统不可达或字段 Schema 变化时，系统停止本轮同步并展示错误，不删除既有事件任务。
- 外部 FE 缺少必需字段、去重键为空或输入映射失败时，事件进入同步异常记录，不触发 Agent 会话或 AgentFlowTask。
- 目标 Agent、AgentFlow、知识域或依赖失效时，事件任务留在 backlog 并展示依赖失效原因。
- 并发上限为 0 时视为暂停触发；已运行对象继续执行。
- 同一外部 FE 重复同步时，不重复创建 EventExecutionTask；若原事件已 done，仅更新最近同步信息和审计记录。
- Agent 会话或 AgentFlowTask 失败时，事件任务展示失败原因；是否开放重新触发由后续版本确认。
- waiting approval 超时后，事件任务保持 waiting approval 或转为失败的策略由原子执行对象规则决定。
- 用户无权访问原子详情时，看板仅展示其有权查看的事件摘要，不泄露 Agent 输出、AgentFlow 节点或 eDevOps 敏感字段。
- 事件源同步、触发调度和原子执行对象创建必须具备幂等键；服务重启后不能重复触发同一事件。

### PRD Review 结论

- 合理性：PRD 覆盖 eDevOps FE 配置、同步、去重、触发、并发、看板和原子详情复用，满足 R-0630 首版范围。
- 一致性：事件源只聚合状态，Agent 会话和 AgentFlowTask 仍是原子执行对象，避免重复建设详情能力。
- 已修正点：关联版本改为 R-0630，首版事件源改为 eDevOps FE，看板状态从 waiting approval 统一替代旧状态。
- 待确认点：eDevOps FE 字段白名单细节、事件任务失败后的重新触发入口和 waiting approval 超时最终映射。

## 验收标准

### 正常路径

- 团队管理员可以配置 eDevOps FE 事件源，保存授权引用、项目范围、同步范围和启停状态。
- 团队管理员可以执行试同步并查看脱敏后的样例 FE。
- 团队管理员可以为新建 FE 和 FE 状态变更配置触发策略。
- 触发策略可以选择 Agent 会话或 AgentFlowTask 作为默认目标。
- 系统按计划同步 eDevOps FE，新事件进入 backlog。
- 并发额度允许时，目标为 Agent 的事件创建 `source=event_source` 的 AgentSession。
- 并发额度允许时，目标为 AgentFlow 的事件创建来源为事件源的 AgentFlowTask 和 AgentFlowRun。
- 事件执行看板按 backlog、agent working、waiting approval、done 展示卡片。
- 点击 Agent 会话任务进入 F-006 Agent 会话详情。
- 点击 AgentFlowTask 进入 F-008 AgentFlow 任务详情。

### 边界场景

- 并发上限达到时，新事件保留在 backlog，不创建原子执行对象。
- 并发上限为 0 时视为暂停触发。
- 暂停事件源或事件类型规则后，不再同步或触发新任务，已运行对象继续执行。
- 重复同步同一个 eDevOps FE 时，不重复创建 EventExecutionTask。
- FE 字段变化后更新看板摘要，不覆盖已启动任务快照。
- 目标依赖失效时，事件任务留在 backlog 并展示依赖失效原因。
- 原子对象等待审阅、确认或询问时，看板状态映射为 waiting approval。

### 异常场景

- eDevOps 授权失效、系统不可达或字段 Schema 变化时，同步失败并保留已有 backlog。
- FE 缺少必需字段、去重键为空或输入映射失败时，事件进入同步异常记录。
- 原子执行对象创建失败时，事件任务保留错误和幂等键，不重复触发。
- Agent 会话或 AgentFlowTask 失败时，看板展示失败原因。
- 用户无权访问原子详情时，不泄露 Agent 输出、AgentFlow 节点或 eDevOps 敏感字段。

### 权限场景

- 团队创建者和团队管理员可以配置事件源、规则、并发上限和启停状态。
- 团队成员可以查看事件执行看板，但不能修改事件源配置。
- 访客只读查看有权限的事件摘要和状态。
- 跨空间事件源、事件任务和原子执行对象不可互相访问。
- 租户级 eDevOps 授权缺失时，团队空间不能保存启用状态的事件源。

## UI 设计

### 是否需要刷新

- 结论：是
- 理由：新增 eDevOps FE 事件源配置中心、触发策略表单、同步测试结果、事件执行看板和原子详情跳转入口。

### 页面与交互

- 事件源配置页：
  - 展示事件源类型 eDevOps FE。
  - 表单包含名称、授权引用、项目范围、同步范围和启停状态。
  - 提供试同步按钮和样例 FE 预览。
- 事件类型规则编辑页：
  - 支持新建 FE 和 FE 状态变更。
  - 配置同步时间、过滤条件、去重键、触发目标、输入映射、知识域、并发上限和启停状态。
  - 触发目标选择 Agent 时展示 Agent 选择器；选择 AgentFlow 时展示 AgentFlow 选择器和输入 Schema。
- 事件执行看板：
  - 四列固定为 backlog、agent working、waiting approval、done。
  - 卡片展示 FE 标题、FE ID、事件类型、触发目标、当前状态、等待原因和最新更新时间。
  - 支持按事件源、事件类型、触发目标和状态筛选。
  - 点击卡片进入对应 Agent 会话或 AgentFlow 任务详情。

### 状态与文案

- 固定看板状态文案：backlog、agent working、waiting approval、done。
- 空态：`暂无 eDevOps FE 事件任务`
- 依赖失效：`目标依赖不可用，事件暂留 backlog`
- waiting approval 提示：`该事件正在等待人工审阅、确认或 Agent 询问处理`

## 前端设计

### 是否需要刷新

- 结论：是
- 理由：需要新增事件源配置页面、事件执行看板页面、同步测试交互、规则编辑状态、看板轮询或事件流恢复，以及跳转 F-006/F-008 的路由能力。

### 页面、组件与状态

- 页面：
  - `EventSourceSettingsPage`
  - `EventTriggerRuleEditor`
  - `EventExecutionBoard`
- 组件：
  - `EdevopsFeatureSourceForm`、`SyncProbeResultPanel`、`EventTypeSelector`。
  - `TriggerTargetSelector`、`EventInputMappingEditor`、`EventConcurrencyInput`。
  - `EventBoardColumn`、`EventExecutionCard`、`EventBoardFilters`。
  - `EventStatusBadge`、`EventDetailLink`。
- 状态管理：
  - EventSource 配置、EventTriggerRule 草稿、同步测试结果、EventExecutionTask 列表、看板筛选、分页或游标。
  - 轮询或事件流连接状态、断线补拉游标和幂等提交状态。

### 接口依赖与异常处理

- 查询、创建、更新、启停 eDevOps FE 事件源。
- 试同步 eDevOps FE 并返回脱敏样例。
- 查询、创建、更新、启停事件类型触发规则。
- 查询事件执行看板任务列表和状态统计。
- 查询 Agent 和 AgentFlow 可触发目标。
- 点击卡片时根据关联对象路由到 F-006 或 F-008。
- 同步测试失败时保留配置草稿和错误详情。
- 目标依赖失效时保留规则草稿并提示重新选择。

## 后端设计

### 是否需要刷新

- 结论：是
- 理由：需要新增 eDevOps 适配器、EventSource、EventTriggerRule、EventExecutionTask、同步水位、去重、调度器、并发计数、原子执行对象创建和状态映射。

### 接口与数据

- 核心服务：
  - `EventSourceService`：管理事件源配置、授权引用、启停状态和试同步。
  - `EdevopsFeatureAdapter`：按授权引用拉取 eDevOps FE，执行字段转换和白名单脱敏。
  - `EventTriggerRuleService`：管理事件类型触发规则、输入映射和并发上限。
  - `EventSourceSyncWorker`：按计划同步外部事件、保存水位和去重。
  - `EventTriggerScheduler`：按并发额度领取 backlog 并创建原子执行对象。
  - `EventExecutionBoardService`：查询看板任务、状态映射和筛选结果。
  - `EventExecutionLinkService`：解析 AgentSession 或 AgentFlowTask 跳转目标。
  - `AuditService`。
- 核心数据对象：
  - `EventSource`：事件源 ID、空间 ID、类型、名称、授权引用、项目范围、同步范围、启停状态和最近同步结果。
  - `EventTriggerRule`：事件源 ID、外部事件类型、同步时间、过滤条件、去重键、触发目标、输入映射、知识域、并发上限和启停状态。
  - `EventExecutionTask`：事件任务 ID、事件源 ID、外部事件类型、externalFeatureId、去重键、状态、摘要、payload 快照、关联原子对象和等待原因。
  - `EventSourceSyncWatermark`：事件源 ID、事件类型、同步游标、最近外部更新时间、最近同步时间和错误。
  - `EventExecutionStatus`：`backlog`、`agent_working`、`waiting_approval`、`done`。
  - `EventTriggerTarget`：`agent_session`、`agent_flow_task`。
- 建议接口：
  - `GET /team-spaces/{spaceId}/event-sources`
  - `POST /team-spaces/{spaceId}/event-sources`
  - `POST /team-spaces/{spaceId}/event-sources/{eventSourceId}/probe`
  - `PATCH /team-spaces/{spaceId}/event-sources/{eventSourceId}`
  - `GET /team-spaces/{spaceId}/event-sources/{eventSourceId}/rules`
  - `POST /team-spaces/{spaceId}/event-sources/{eventSourceId}/rules`
  - `PATCH /team-spaces/{spaceId}/event-sources/{eventSourceId}/rules/{ruleId}`
  - `GET /team-spaces/{spaceId}/event-execution-tasks`
  - `GET /team-spaces/{spaceId}/event-execution-board`

### 业务规则、权限与异常处理

- EventSource、EventTriggerRule、EventExecutionTask、AgentSession 和 AgentFlowTask 必须归属同一租户和团队空间。
- 同步和触发均必须使用幂等键；服务重启后不能重复触发同一外部 FE。
- 调度器创建 Agent 会话或 AgentFlowTask 前必须重新校验触发目标、知识域、Tool 和环境变量依赖。
- 看板状态只能从原子执行对象状态映射，不允许手工改写终态。
- eDevOps FE payload 进入 prompt、日志和看板前必须经过白名单过滤和脱敏。
- 后端必须拒绝非管理员修改事件源或触发规则。

## 评审与会签

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认 eDevOps FE 字段白名单、事件类型清单和 waiting approval 超时映射。 |
| UI | 待确认 | 需输出 eDevOps FE 配置、触发策略、事件执行看板和原子详情跳转交互。 |
| 前端 | 待确认 | 需评估看板刷新方式、规则编辑状态、路由跳转和异常恢复。 |
| 后端 | 待确认 | 需确认 eDevOps 适配器、同步水位、幂等、并发调度和审计主体。 |
| 测试 | 待确认 | 需按 TC-009 覆盖同步、去重、并发、暂停、权限、状态映射、详情跳转和外部源异常。 |
