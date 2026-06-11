# 通用聊天模块设计

> 文档定位：定义「通用任务」（即席触发、无需工作流模板，见沙箱设计 §0 架构图）的前端模块设计——API、页面交互、功能。后端经 **Agent Orchestration 编排层**代理到 Agent Core，前端不直连 Agent Core。
>
> 范围：当前仅覆盖**聊天会话**；数据模型与页面在「会话记录」处预留工作流任务的展示扩展位（见 §5）。

## 1. 概念与定位

通用聊天是用户不经工作流模板、直接与 Agent 对话完成任务的入口。一次聊天对应编排层的一个 **conversation**，conversation 映射到 Agent Core 的一个 session（见沙箱设计 §1）。

- 前端只跟编排层打交道，编排层持有 conversation 元数据（标题、创建人、状态、时间、关联 sessionId），并代理对话流到 Agent Core；
- 一个 conversation 内可多轮对话，支持续聊；
- 编排层负责 conversation 列表、检索、归档等管理能力——这些是 Agent Core 不关心的业务层职责。

```text
┌─ 前端（通用聊天模块）──────────────┐
│  新建任务页  │  会话记录页          │
└──────┬───────────────┬─────────────┘
       │ 聊天会话 API（REST + SSE）
       ▼
┌─ Agent Orchestration（编排层）─────┐
│  conversation 管理（列表/元数据/检索）│
│  代理对话流 → Agent Core session    │
└──────┬─────────────────────────────┘
       │ session 接口（见沙箱设计）
       ▼
┌─ Agent Core ───────────────────────┐
└────────────────────────────────────┘
```

## 2. 页面设计

单页面三栏：**左**会话列表、**中**对话（消息流 + 输入）、**右**概览 + 文件目录。新建与查看/续聊历史共用同一布局。

布局：

```text
┌──────────────┬────────────────────────┬──────────────────┐
│ [+ 新建对话] │  会话标题               │ ┌─ 概览（网格）─┐ │
│──────────────│                        │ │ 会话名 类型 … │ │
│ 进行中 (2)   │   （对话消息区）         │ │ AgentFlow横向  │ │
│  会话A  今天 │   用户消息 / thinking   │ └──────────────┘ │
│ ▾ 流水线 1h  │   工具卡片 / Agent 回复  │──────────────────│
│   ✓ step1    │                       │ 文件目录          │
│   ▶ step3    │                       │ M src/a.ts  +12  │
│ 已完成 (3)   │  [输入框....] [发送]   │ M src/b.ts   +9  │
│ 失败 (1)     │                       │ ▶ 点击查看 diff   │
└──────────────┴────────────────────────┴──────────────────┘
```

### 2.1 左栏：会话列表

1. 进入页面 → 拉取会话列表（`GET /conversations`，分页），默认选中最近一条或停留在「新对话」；
2. 列表项显示标题、最近活跃时间、状态点、类型标签（临时会话/事件任务/定时任务）；
3. **step 型会话可展开**（`event` / `scheduled` 类型）：点标题前箭头展开，内嵌显示 AgentFlow 每个 step（带状态点）；step 状态通过 `GET /conversations/{id}/poll` 定期轮询刷新（建议间隔 5~10s）；点某 step → 中间区以该 step 的 `conversationId` 查询对话；
4. 顶部「+ 新建对话」→ 中间区切到空白新对话（首条消息发送时再建 conversation）；
5. 列表项 hover 出现重命名、归档、删除。

### 2.2 中栏：对话

纯消息流 + 输入区：
1. 新对话/历史会话/step 对话 → 加载对应消息流并渲染；
2. 发送后发起 SSE 订阅，流式渲染 agent 执行事件；一轮结束展示结果摘要；
3. 渲染细节：thinking 灰字、message 气泡、tool_use/tool_result 折叠卡片；执行中输入框禁用 + 中止按钮；需人工确认显示「批准/驳回」。

### 2.3 右栏：概览 + 文件

**右上：会话概览**（网格布局）

- 通用字段：会话名称、类型、关联任务、状态、项目、发起人、开始时间、结束人；
- `event` / `scheduled` 类型额外：当前步骤、所属 AgentFlow、事件来源，并渲染 **AgentFlow 横向流程图**。

**右下：文件目录**——本会话变更文件列表（M/A/D 标记 + 增删行数）。点击某文件 → 弹窗全屏展示 unified diff，不直接在右栏展开。



## 3. API 设计（前端 ↔ 编排层）

> 编排层对前端暴露的聊天会话 API。编排层内部再调 Agent Core 的 session 接口（见沙箱设计）。

### 3.1 对话 SSE（创建 + 发送 + 事件流合一）

`GET /conversations/chat`

一个 SSE 长连接承载全部——首次建连即创建 conversation + 发消息，续聊同地址。编排层内部：首次时初始化 session，续聊时复用。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversationId` | string | 否 | 续聊时传入；首次不传，编排层在 `conversation.created` 事件中返回 |
| `content` | string | 是 | 用户输入 |
| `title` | string | 否 | 会话标题，不传则首条消息后自动生成（仅首次生效）|
| `projectId` | string | 是 | 所属项目 |
| `agentRef` | string | 否 | 指定 agent；仅首次生效（对话创建后不可更改），不传用项目默认 |
| `lastSequence` | long | 否 | 断线重连续传游标 |

#### SSE 事件流

消息格式遵循 Anthropic Messages Streaming 规范（业界标准 SSE 协议），每条事件为 `event:` + `data:` 行对，`data` 为 JSON 对象，内含 `type` 字段标识事件种类。

##### 事件类型

| SSE event | data.type | 说明 |
|---|---|---|
| `message_start` | `message_start` | 消息帧开始。首次建连时含 `conversationId`；`message.model` 标识运行时、`message.usage` 含初始 token 用量 |
| `content_block_start` | `content_block_start` | 内容块开始。`content_block.type` 为 `text` / `tool_use` / `thinking`，`index` 自增 |
| `content_block_delta` | `content_block_delta` | 增量内容。`delta.type` 区分：`text_delta`（文本）、`input_json_delta`（工具参数 JSON 片段）、`thinking_delta`（思考过程）|
| `content_block_stop` | `content_block_stop` | 内容块结束 |
| `message_delta` | `message_delta` | 消息终止信息：`delta.stop_reason`（`end_turn` / `tool_use` / `cancelled` / `error` / `timeout`）、`usage.output_tokens`、`payload` 含 summary / commitSha / artifactRefs / sessionRef |
| `message_stop` | `message_stop` | 消息帧结束，本轮对话完成 |
| `ping` | `ping` | 心跳保活 |

##### 完整示例

```text
event: message_start
data: {"type":"message_start","message":{"id":"msg_001","role":"assistant","model":"claude-code","content":[],"usage":{"input_tokens":0}},"conversationId":"conv-789"}

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

编排层是**透明代理**——不缓存事件、不转换内容、不做语义理解。唯一逻辑：首次建连创建 conversation + 初始化 session（调 Agent Core），将 Agent Core 的执行事件按上述 SSE 格式透传给前端。

#### 前端处理

- 监听 SSE 按 `data.type` 分发渲染；
- `message_start` 保存 conversationId（首次），记录 model 信息展示；
- `message_stop` 本轮结束，关闭连接；
- 继续对话：重新发起同一 URL 带 `conversationId`

### 3.2 中止当前对话


`POST /conversations/{id}/abort`

停止本轮 agent 执行，保留会话可续聊。代理到 Agent Core 的 abort。

### 3.3 查看本轮改动

`GET /conversations/{id}/changes`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `path` | string | 否 | 不传返回改动文件列表；带 `path` 返回该文件 unified diff。文件详情区提供「放大」按钮，点击以弹窗全屏展示完整 diff |

代理到 Agent Core 的 changes 接口。

### 3.4 会话列表

`GET /conversations`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | enum | 否 | 对话类型：`ad-hoc`（临时会话）/ `event`（事件任务）/ `scheduled`（定时任务）。默认 `ad-hoc`|
| `status` | enum | 否 | running / waiting_approval / done / failed |
| `cursor` / `limit` | - | 否 | 分页 |

返回：会话列表 + 分页游标。每个列表项：

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` | string | 会话标识 |
| `title` | string | 会话名称 |
| `type` | enum | `ad-hoc` / `event` / `scheduled` |
| `status` | enum | running / waiting_approval / done / failed |
| `lastActiveAt` | datetime | 最近活跃时间（列表按此倒序排列）|
| `steps` | object[] | `event` / `scheduled` 类型时：step 列表，每项 `stepId` / `name` / `status` / `order` / `conversationId`，供左栏展开和查询对话 |

> 搜索、聚合、类型过滤为后续能力，本期不做（见 §5）。

### 3.5 轮询查询（workflow 进度）

`GET /conversations/{id}/poll`

供前端**按固定间隔轮询** conversation 运行状态。常规聊天走 SSE 实时流，轮询接口主要用于：

- **workflow 场景**：前端未保持 SSE 长连接时，快速获取各 step 最新状态以刷新左栏和内嵌步骤进度；
- **断线补偿**：SSE 意外断开后，拉取当前状态同步 UI。

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `since` | datetime | 否 | 增量查询：返回该时间之后发生状态变化的 step，用于省带宽的轻量轮询 |

**Response：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` / `title` / `type` / `status` | - | 会话基本信息 |
| `progress` | object | 仅 `event` / `scheduled`：`{ completed, total }` 进度计数 |
| `steps` | object[] | step 列表，每项 `stepId` / `name` / `status` / `order` / `conversationId`，供左栏刷新状态点 |
| `activeStep` | string | 当前执行中的 stepId（无则为 null）|

> 建议轮询间隔：**运行中 5~10s，待审批 10~15s，已完成/失败停止轮询**。

### 3.6 会话详情

`GET /conversations/{id}`

返回会话概览 + 对话历史：

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` / `title` / `type` / `status` | - | 基本信息 |
| `projectId` / `agentRuntime` / `createdBy` / `startedAt` / `endedBy` | - | 概览字段（前端信息栏展示）|
| `runId` | string | 关联任务（`event` / `scheduled` 时有）|
| `agentFlow` | object | 仅 `event` / `scheduled` 类型：`{ flowRef, version, steps[] }`，steps 含名称/状态/`conversationId`。每 step 即独立 conversation，对话用 `GET /conversations/{stepConversationId}` 查询 |
| `eventSource` | object | 仅事件触发：`{ type, summary, ... }`，信息栏「事件来源」字段展示 |
| `messages` | object[] | 对话历史：多轮消息、工具调用、改动摘要、各轮终态结果 |

### 3.7 会话管理

- `PATCH /conversations/{id}`：重命名（`title`）；
- `POST /conversations/{id}/archive`：归档；
- `DELETE /conversations/{id}`：删除。

## 4. 功能清单

| 功能 | 优先级 | 说明 |
|---|---|---|
| 新建会话、多轮对话 | P0 | 创建 conversation，发消息，SSE 流式渲染 |
| 续聊 | P0 | 在已有 conversation 上继续对话 |
| 中止当前对话 | P0 | abort，保留会话 |
| 查看本轮改动 | P0 | 改动文件列表 + diff |
| 会话列表与详情 | P0 | 左栏列表、右栏回放 |
| 会话检索 | P1 | 按标题/内容搜索 |
| 会话管理 | P1 | 重命名、归档、删除 |
| 断线重连 | P1 | SSE 按 sequence 续传 |
| 会话数据保留 | P1 | 遵循 Agent Core 保留策略（用户级最近 50 条 + 超 15 天，见沙箱设计）|

## 5. 对话类型

对话按 `type` 分为三类：**临时会话**（`ad-hoc`）即席创建、**事件任务**（`event`）由外部事件触发、**定时任务**（`scheduled`）按计划执行。列表统一按时间排序，`event` / `scheduled` 类型内嵌步骤展开。

> `type` 枚举、列表接口、页面筛选器均为三类设计，无需后续改造。

## 6. 待确认 / 后续

| # | 议题 | 说明 |
|---|---|---|
| 1 | 会话与 session 的映射时机 | conversation 创建即建 session，还是首条消息时才建（影响空会话的资源占用）|
| 2 | SSE 透传 vs 重连补偿 | 编排层透传 Agent Core SSE 的断线补偿机制，与会话历史持久化的边界 |
| 3 | 标题自动生成 | 由 Agent 首轮总结生成，还是取首条消息截断 |
| 4 | 改动审查动作 | 查看 diff 后是否需要在聊天内提供「应用/丢弃/提 PR」等动作（涉及 repo 写回策略）|
| 5 | 多端同会话 | 同一用户多标签页打开同一 conversation 的并发处理（与 Agent Core 单会话串行衔接）|
