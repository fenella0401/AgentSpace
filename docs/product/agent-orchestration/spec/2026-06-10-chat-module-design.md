# 通用任务模块设计

> 文档定位：定义「通用任务」的前端模块设计——API、页面交互、功能。**任务（task）** 是顶层实体，**对话（conversation）** 是任务下的子实体。任务执行分两种模式：**AgentFlow（工作流编排）** 和 **Agent（直接对话）**。后端经 **Agent Orchestration 编排层**代理到 Agent Core，前端不直连 Agent Core。

## 1. 概念与定位

用户在本模块中发起**任务**完成工作。任务是顶层管理单元，一个任务包含一个或多个对话。

**任务执行模式：**

| 模式 | 说明 | 对话关系 |
|---|---|---|
| **AgentFlow** | 基于工作流模板的编排执行，任务含多个 step，每个 step 是一个独立对话 | 任务 1 : N 对话（每 step 一个） |
| **Agent** | 直接与 Agent 对话，任务内一个对话、支持多轮续聊 | 任务 1 : 1 对话（多轮） |

**层级关系：**

```text
Task（任务）── 顶层管理单元
  ├─ executionMode: agentFlow | agent
  ├─ 元数据：标题、创建人、状态、时间
  │
  ├─ AgentFlow 模式
  │   └─ Conversation（step 1）
  │   └─ Conversation（step 2）
  │   └─ ...
  │
  └─ Agent 模式
      └─ Conversation（主对话，多轮）
```

- 前端只跟编排层打交道，编排层持有 task 元数据，并代理对话流到 Agent Core；
- 编排层负责 task 列表、检索、归档等管理能力——这些是 Agent Core 不关心的业务层职责；
- 一个 task 内的 conversation 映射到 Agent Core 的一个 session（见沙箱设计 §1）。

```text
┌─ 前端（通用任务模块）──────────────────┐
│  新建任务页  │  任务记录页              │
└──────┬───────────────┬─────────────────┘
       │ 任务 API（REST + SSE）
       ▼
┌─ Agent Orchestration（编排层）─────────┐
│  task 管理（列表/元数据/检索）           │
│  代理对话流 → Agent Core session        │
│  AgentFlow 调度（step DAG 执行）        │
└──────┬─────────────────────────────────┘
       │ session 接口（见沙箱设计）
       ▼
┌─ Agent Core ───────────────────────────┐
└────────────────────────────────────────┘
```

## 2. 页面设计

单页面三栏：**左**任务列表、**中**对话（消息流 + 输入）、**右**任务概览 + 文件目录。新建与查看/续聊历史共用同一布局。

布局：

```text
┌──────────────┬────────────────────────┬──────────────────┐
│ [+ 新建任务] │  任务标题               │ ┌─ 概览（网格）─┐ │
│──────────────│                        │ │ 任务名 模式 … │ │
│ 进行中 (2)   │  ┌ AgentFlow 横向 ────┐ │ │ AgentFlow横向  │ │
│  任务A  今天 │  │ ✓step1 → ▶step2 → ◻step3 │ └──────────────┘ │
│  Agent 模式  │  └────────────────────┘ │──────────────────│
│              │   （对话消息区）         │ 文件目录          │
│ ▾ 任务B  1h  │   用户消息 / thinking   │ M src/a.ts  +12  │
│  AgentFlow   │   工具卡片 / Agent 回复  │ M src/b.ts   +9  │
│   ✓ step1    │                       │ ▶ 点击查看 diff   │
│   ▶ step3    │                       │                   │
│ 已完成 (3)   │  [输入框....] [发送]   │                   │
│ 失败 (1)     │                       │                   │
└──────────────┴────────────────────────┴──────────────────┘
```

### 2.1 左栏：任务列表

1. 进入页面 → 拉取任务列表（`GET /tasks`，分页），默认选中最近一条或停留在「新任务」；
2. 列表项显示标题、最近活跃时间、状态点、**执行模式标签**（AgentFlow / Agent）；
3. 列表按时间倒序；AgentFlow 任务可展开查看 step 列表（step 状态点：✓已完成 / ▶进行中 / ◻待执行）；
4. 顶部「+ 新建任务」→ 弹出执行模式选择（AgentFlow / Agent）→ 中间区切到空白新任务：
   - **Agent 模式**：直接进入对话，首条消息发送时创建 task + conversation；
   - **AgentFlow 模式**：需选择 AgentFlow 模板，确认后创建 task，编排层按模板调度 step 执行；
5. 列表项 hover 出现重命名、归档、删除；
6. 顶部状态标签栏切换筛选（全部 / running / waiting_approval / done / failed）。

### 2.2 中栏：对话

消息流 + 输入区：
1. **Agent 模式**：选中任务 → 加载该任务的对话消息流，与普通聊天一致；
2. **AgentFlow 模式**：标题下方渲染 **AgentFlow 横向流程图**（各 step 带状态节点 + 箭头连线），点击某 step → 加载该 step 对应对话的消息流；
3. 发送后发起 SSE 订阅，流式渲染 agent 执行事件；一轮结束展示结果摘要；
4. 渲染细节：thinking 灰字、message 气泡、tool_use/tool_result 折叠卡片；执行中输入框禁用 + 中止按钮；需人工确认显示「批准/驳回」；
5. AgentFlow 模式下 step 状态通过 `GET /tasks/{id}` 定期轮询刷新。

### 2.3 右栏：概览 + 文件

**右上：任务概览**（网格布局）

- 通用字段：任务名称、执行模式（AgentFlow / Agent）、状态、项目、发起人、开始时间、结束人；
- **AgentFlow 模式额外**：所属 AgentFlow 模板、当前步骤、进度（completed/total），并渲染 **AgentFlow 横向流程图**；
- **Agent 模式额外**：使用的 Agent 名称、模型信息；
- `event` / `scheduled` 类型额外：事件来源 / 定时规则。

**右下：文件目录**——本任务变更文件列表（M/A/D 标记 + 增删行数）。点击某文件 → 弹窗全屏展示 unified diff，不直接在右栏展开。



## 3. API 设计（前端 ↔ 编排层）

> 编排层对前端暴露的任务与对话 API。编排层内部再调 Agent Core 的 session 接口（见沙箱设计）。

### 3.1 对话 SSE（创建 + 发送 + 事件流合一）

`GET /tasks/chat`

一个 SSE 长连接承载全部——首次建连即创建 task + conversation + 发消息，续聊同地址。编排层内部：首次时初始化 session，续聊时复用。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `taskId` | string | 否 | 续聊时传入，指定已有任务；首次不传 |
| `conversationId` | string | 否 | 续聊时指定对话（AgentFlow 模式下用于定位 step 对应对话）；首次不传 |
| `content` | string | 是 | 用户输入 |
| `title` | string | 否 | 任务标题，不传则首条消息后自动生成（仅首次生效）|
| `projectId` | string | 是 | 所属项目 |
| `executionMode` | enum | 否 | `agentFlow` / `agent`，仅首次生效（任务创建后不可更改），不传默认 `agent` |
| `agentFlowRef` | string | 否 | 仅 executionMode=`agentFlow` 时必填，指定使用的 AgentFlow 模板 |
| `agentRef` | string | 否 | 仅 executionMode=`agent` 时生效，指定 agent；不传用项目默认；仅首次生效（任务创建后不可更改）|
| `lastSequence` | long | 否 | 断线重连续传游标 |

#### SSE 事件流

消息格式遵循 Anthropic Messages Streaming 规范（业界标准 SSE 协议），每条事件为 `event:` + `data:` 行对，`data` 为 JSON 对象，内含 `type` 字段标识事件种类。

##### 事件类型

| SSE event | data.type | 说明 |
|---|---|---|
| `message_start` | `message_start` | 消息帧开始。首次建连时含 `taskId` + `conversationId`；`message.model` 标识运行时、`message.usage` 含初始 token 用量 |
| `content_block_start` | `content_block_start` | 内容块开始。`content_block.type` 为 `text` / `tool_use` / `thinking`，`index` 自增 |
| `content_block_delta` | `content_block_delta` | 增量内容。`delta.type` 区分：`text_delta`（文本）、`input_json_delta`（工具参数 JSON 片段）、`thinking_delta`（思考过程）|
| `content_block_stop` | `content_block_stop` | 内容块结束 |
| `message_delta` | `message_delta` | 消息终止信息：`delta.stop_reason`（`end_turn` / `tool_use` / `cancelled` / `error` / `timeout`）、`usage.output_tokens`、`payload` 含 summary / commitSha / artifactRefs / sessionRef |
| `message_stop` | `message_stop` | 消息帧结束，本轮对话完成 |
| `ping` | `ping` | 心跳保活 |

##### AgentFlow 模式额外事件

| SSE event | data.type | 说明 |
|---|---|---|
| `step_started` | `step_started` | AgentFlow 某 step 开始执行，含 `stepId` / `stepName` / `conversationId`，前端据此更新 AgentFlow 流程图 |
| `step_completed` | `step_completed` | AgentFlow 某 step 执行完成 |
| `step_failed` | `step_failed` | AgentFlow 某 step 执行失败 |
| `task_completed` | `task_completed` | 全部 step 完成，任务结束 |

##### 完整示例

```text
event: message_start
data: {"type":"message_start","message":{"id":"msg_001","role":"assistant","model":"claude-code","content":[],"usage":{"input_tokens":0}},"taskId":"task-123","conversationId":"conv-789"}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"分析中…"}}

event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: content_block_start
data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","name":"read_file","input":{}}}

event: content_block_delta
data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"path\":\"src/auth/token.ts\"}"}}

event: content_block_stop
data: {"type":"content_block_stop","index":1}

event: content_block_start
data: {"type":"content_block_start","index":2,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"已完成修改"}}

event: content_block_stop
data: {"type":"content_block_stop","index":2}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":128},"payload":{"summary":"修复了 token 刷新","commitSha":"a1b2c3d","artifactRefs":[],"sessionRef":"srf-456"}}

event: message_stop
data: {"type":"message_stop"}
```

##### 前端渲染映射

| delta.type | 前端渲染 |
|---|---|
| `thinking_delta` | 灰字，折叠区域 |
| `text_delta` | 正常消息气泡 |
| `content_block` type=`tool_use` | 工具调用卡片（含 `name`），参数由 `input_json_delta` 拼接后展示 |
| `message_delta` stop=`cancelled` / `error` / `timeout` | 错误气泡 |

##### 断线重连

`message_start` 事件含 `sequence` 字段（递增），前端记录最后收到的 `sequence`。断线后重连同一 URL 带 `lastSequence`，编排层从该位置续传后续事件。

#### 编排层衔接

编排层是**透明代理**——不缓存事件、不转换内容、不做语义理解。逻辑：
- **Agent 模式首次**：创建 task + conversation → 调 Agent Core `POST /sessions` → 透传 SSE；
- **AgentFlow 模式首次**：创建 task → 加载 AgentFlow 模板 → 按 DAG 调度 step，每个 step 创建 conversation → 调 Agent Core session → 透传 SSE；
- **续聊**：根据 `taskId` / `conversationId` 定位已有 session → 调 Agent Core `GET /sessions/{id}/chat` → 透传。

#### 前端处理

- 监听 SSE 按 `data.type` 分发渲染；
- `message_start` 保存 `taskId` + `conversationId`（首次），记录 model 信息展示；
- `message_stop` 本轮结束，关闭连接；
- 继续对话：重新发起同一 URL 带 `taskId` / `conversationId`；
- AgentFlow 模式：监听 `step_started` / `step_completed` / `step_failed` / `task_completed` 更新流程图。

### 3.2 中止当前对话

`POST /tasks/{taskId}/abort`

停止本轮 agent 执行，保留任务与对话可续聊。代理到 Agent Core 的 abort。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversationId` | string | 否 | 指定中止哪个对话；不传中止任务当前活跃对话 |

### 3.3 查看本轮改动

`GET /tasks/{taskId}/changes`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversationId` | string | 否 | 指定查看某对话改动；不传返回任务全部改动 |
| `path` | string | 否 | 不传返回改动文件列表；带 `path` 返回该文件 unified diff。文件详情区提供「放大」按钮，点击以弹窗全屏展示完整 diff |

代理到 Agent Core 的 changes 接口。

### 3.4 任务列表

`GET /tasks`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `executionMode` | enum | 否 | `agentFlow` / `agent`，筛选执行模式 |
| `status` | enum | 否 | running / waiting_approval / done / failed |
| `projectId` | string | 是 | 所属项目 |
| `cursor` / `limit` | - | 否 | 分页 |

返回：任务列表 + 分页游标。每个列表项：

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskId` | string | 任务标识 |
| `title` | string | 任务名称 |
| `executionMode` | enum | `agentFlow` / `agent` |
| `status` | enum | running / waiting_approval / done / failed |
| `lastActiveAt` | datetime | 最近活跃时间（列表按此倒序排列）|
| `agentRef` | string | Agent 模式时使用的 agent |
| `agentFlowRef` | string | AgentFlow 模式时使用的模板引用 |
| `steps` | object[] | AgentFlow 模式时：step 列表，每项 `stepId` / `name` / `status` / `order` / `conversationId`，供左栏展开和点击查看 step 对话；Agent 模式时为空 |
| `conversationId` | string | Agent 模式时：主对话 ID |

> 搜索、聚合为后续能力，本期不做（见 §6）。

### 3.5 任务详情

`GET /tasks/{id}`

返回任务概览 + 对话历史：

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskId` / `title` / `executionMode` / `status` | - | 基本信息 |
| `projectId` / `createdBy` / `startedAt` / `endedBy` | - | 概览字段（前端信息栏展示）|
| `runId` | string | 关联编排 run |
| `agentRef` | string | Agent 模式：使用的 Agent（如 Claude Code）|
| `agentFlow` | object | AgentFlow 模式：`{ flowRef, version, status, steps[], progress: {completed, total}, activeStep }`。`status` 为 AgentFlow 整体状态（running / suspended / completed / failed）；steps 含 `stepId` / `name` / `status` / `order` / `conversationId`；Agent 模式时为 null |
| `eventSource` | object | 触发来源溯源信息，**仅 `event` 类型有值**（`ad-hoc` 手动创建、`scheduled` 定时触发时为空）。`{ type, summary }`：`type` 为触发事件类型（如 GitHub Release / PR / Webhook），`summary` 为事件摘要（如"发布 tag v2.3.0"）。用于任务详情概览区展示"由什么外部事件触发"，供溯源与审计 |
| `conversations` | object[] | 对话列表，每项 `conversationId` / `stepId`（AgentFlow 模式映射到 step）/ `messages[]` |
| `messages` | object[] | Agent 模式：直接包含任务主对话的消息历史（多轮消息、工具调用、改动摘要、各轮终态结果）；AgentFlow 模式时为空（消息在 conversations[].messages 中）|

### 3.6 任务管理

- `PATCH /tasks/{id}`：重命名（`title`）；
- `POST /tasks/{id}/archive`：归档；
- `DELETE /tasks/{id}`：删除。

## 4. 功能清单

| 功能 | 优先级 | 说明 |
|---|---|---|
| 新建任务（Agent 模式）| P0 | 选择 Agent，发消息，创建 task + conversation，SSE 流式渲染 |
| 新建任务（AgentFlow 模式）| P0 | 选择 AgentFlow 模板，创建 task，按模板调度 step 执行，每 step 作为独立 conversation |
| 多轮续聊 | P0 | 在已有 task 的 conversation 上继续对话 |
| 中止当前对话 | P0 | abort，保留 task 与 conversation |
| 查看本轮改动 | P0 | 改动文件列表 + diff |
| 任务列表与详情 | P0 | 左栏任务列表、中间栏对话回放、右栏概览 + AgentFlow 流程图 |
| 任务检索 | P1 | 按标题/内容搜索 |
| 任务管理 | P1 | 重命名、归档、删除 |
| 断线重连 | P1 | SSE 按 sequence 续传 |
| 数据保留 | P1 | 遵循 Agent Core 保留策略（用户级最近 50 条 + 超 15 天，见沙箱设计）|

## 5. 任务执行模式

任务按执行方式分为两种模式：**Agent** 和 **AgentFlow**。二者在触发方式上均可为 `ad-hoc`（手动）/ `event`（事件）/ `scheduled`（定时），即执行模式与触发方式正交。

### 5.1 Agent 模式

用户直接与 Agent 对话完成任务。一个 task 包含一个主 conversation，支持多轮续聊。

```text
Task（Agent 模式）
  │
  └─ Conversation（主对话）
       ├─ Round 1: 用户消息 → Agent 回复（thinking + tool_use + text）
       ├─ Round 2: 用户消息 → Agent 回复
       └─ ...
```

- 创建时选择 Agent（如 Claude Code），创建后不可更改；
- 输入框始终绑定到唯一 conversation；
- 右栏概览展示 Agent 名称、模型信息。

### 5.2 AgentFlow 模式

用户选择 AgentFlow 模板启动任务。编排层按模板 DAG 调度 step，每个 step 创建独立 conversation 执行。

```text
Task（AgentFlow 模式）
  │
  ├─ Step 1: "代码分析"  → Conversation（step 1 对话）
  ├─ Step 2: "实现功能"  → Conversation（step 2 对话）
  ├─ Step 3: "编写测试"  → Conversation（step 3 对话）
  └─ ...
```

- 创建时选择 AgentFlow 模板，模板定义 step DAG；
- 中间栏上方渲染 AgentFlow 横向流程图（step 节点 + 状态 + 连线）；
- 点击 flowchart 中某个 step → 切换到该 step 的 conversation；
- 每个 step 对应一个独立 conversation（独立 Agent Core session）；
- step 状态由 `GET /tasks/{id}` 轮询刷新；
- 全部 step 完成后任务状态变为 `done`，任一 step 失败则任务标记 `failed`。

> AgentFlow 模板由 Agent-Management 管理，编排层在创建任务时获取模板快照并持久化（见 AgentFlow 设计文档）。

## 6. 待确认 / 后续

| # | 议题 | 说明 |
|---|---|---|
| 1 | 任务与 session 的映射时机 | task + conversation 创建即建 session，还是首条消息时才建（影响空任务的资源占用）|
| 2 | SSE 透传 vs 重连补偿 | 编排层透传 Agent Core SSE 的断线补偿机制，与会话历史持久化的边界 |
| 3 | 标题自动生成 | 由 Agent 首轮总结生成，还是取首条消息截断 |
| 4 | 改动审查动作 | 查看 diff 后是否需要在聊天内提供「应用/丢弃/提 PR」等动作（涉及 repo 写回策略）|
| 5 | 多端同任务 | 同一用户多标签页打开同一 task 的并发处理（与 Agent Core 单会话串行衔接）|
| 6 | AgentFlow 模板选择 UX | 新建任务时如何浏览/搜索 AgentFlow 模板（下拉列表 vs 弹窗选择器）|
| 7 | AgentFlow step 间的文件传递 | step 间文件通过代码仓 commit/checkout 传递（见沙箱设计），前端是否需展示传递状态 |
| 8 | 轮询合并 | AgentFlow 模式下任务详情接口 `GET /tasks/{id}` 已含 `agentFlow` 字段，是否仍需独立 `poll` 端点 |
