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

### 2.1 新建任务页

展示并驱动**当前对话**。

布局：

```text
┌────────────────────────────────────────────┐
│  [新建任务]                      [历史会话↗] │  顶栏
├────────────────────────────────────────────┤
│                                              │
│   （对话消息区）                              │
│   用户消息 / Agent thinking / 工具调用 /      │
│   Agent 回复 / 改动文件卡片                    │
│                                              │
│                                              │
├────────────────────────────────────────────┤
│  [输入框..................]  [发送]          │  输入区
│  可选：项目上下文 / agent 选择                 │
└────────────────────────────────────────────┘
```

交互流程：

1. 用户进入新建任务页 → 前端创建 conversation（`POST /conversations`），得到 `conversationId`；
2. 用户输入首条消息 → `POST /conversations/{id}/messages`，前端发起 SSE 订阅；
3. SSE 流式返回 agent 执行事件（thinking / message / tool_use / tool_result / 改动文件），前端实时渲染；
4. 一轮结束（`session.completed`）后展示结果摘要、改动文件卡片（点开看 diff）；
5. 用户继续输入 → 续聊，重复 2~4；
6. 顶栏「历史会话」跳转到会话记录页。

关键交互细节：

- **流式渲染**：thinking 灰字、message 正常气泡、tool_use/tool_result 折叠为可展开的工具卡片；
- **改动展示**：本轮有文件改动时，结果区出现「本次改动 N 个文件」卡片，点击拉取 diff（编排层代理 Agent Core 的 changes 接口）；
- **执行中状态**：发送后输入框禁用 + 显示「执行中…」，提供「中止」按钮（调 abort）；
- **错误**：session.failed / aborted / timeout 在对话流内以错误气泡提示，可重试。

### 2.2 会话记录页

左侧边栏列所有会话，右侧显示选中会话详情。

布局：

```text
┌──────────────┬─────────────────────────────┐
│ [+ 新建]     │  会话标题            [继续对话]│
│              │─────────────────────────────│
│ 🔍 搜索      │                              │
│──────────────│   （完整对话回放）            │
│ ● 会话A  今天 │   多轮消息 / 工具调用 /       │
│   会话B  昨天 │   改动文件                    │
│   会话C  3天前│                              │
│   ...        │                              │
│              │                              │
│ [筛选: 全部▾] │                              │
└──────────────┴─────────────────────────────┘
```

交互流程：

1. 进入页面 → 拉取会话列表（`GET /conversations`，分页）；
2. 左栏每项显示标题、最近活跃时间、状态标记（进行中/已完成/失败）；
3. 点击某会话 → 右侧加载完整对话历史（`GET /conversations/{id}`）；
4. 右上「继续对话」→ 跳到新建任务页风格的对话视图，在该 conversation 上续聊；
5. 左栏顶部搜索按标题/内容检索；底部筛选器（预留：全部 / 聊天 / 工作流任务，见 §5）。

关键交互细节：

- **列表项**：标题（无标题时取首条消息摘要）、时间、状态点；
- **搜索**：按标题和消息内容关键词；
- **会话操作**：每项 hover 出现重命名、归档、删除；
- **空状态**：无会话时引导「新建任务」。

## 3. API 设计（前端 ↔ 编排层）

> 编排层对前端暴露的聊天会话 API。编排层内部再调 Agent Core 的 session 接口（见沙箱设计）。

### 3.1 创建会话

`POST /conversations`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `title` | string | 否 | 会话标题，不传则首条消息后自动生成 |
| `projectId` | string | 否 | 关联项目（决定 repo/harness 上下文）；不传为无项目纯对话 |
| `agentRef` | string | 否 | 指定 agent；不传用项目默认 |

返回：`conversationId`、`status`、`createdAt`。

### 3.2 发送消息（发起一轮对话）

`POST /conversations/{id}/messages`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `content` | string | 是 | 用户输入 |

返回：`messageId` + SSE 订阅地址（或直接升级为 SSE 响应）。编排层据此向 Agent Core 发起 session 对话（首轮创建 session，续聊复用）。

### 3.3 订阅对话事件流

`GET /conversations/{id}/stream`（SSE）

编排层把 Agent Core 的 SSE 事件透传给前端：thinking / message / tool_use / tool_result / stdout / stderr / session.completed / session.failed / session.aborted / session.timeout。事件基座字段含 `eventId` / `eventType` / `sequence` / `timestamp`，支持断线重连按 `sequence` 续传。

### 3.4 中止当前对话

`POST /conversations/{id}/abort`

停止本轮 agent 执行，保留会话可续聊。代理到 Agent Core 的 abort。

### 3.5 查看本轮改动

`GET /conversations/{id}/changes`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `path` | string | 否 | 不传返回改动文件列表；带 `path` 返回该文件 unified diff |

代理到 Agent Core 的 changes 接口。

### 3.6 会话列表

`GET /conversations`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `keyword` | string | 否 | 标题/内容检索 |
| `type` | enum | 否 | 预留：`chat`（默认）/ `workflow` / `all`（见 §5）|
| `status` | enum | 否 | 进行中/已完成/失败 |
| `cursor` / `limit` | - | 否 | 分页 |

返回：会话列表（`conversationId`、`title`、`status`、`lastActiveAt`、`type`）+ 分页游标。

### 3.7 会话详情

`GET /conversations/{id}`

返回完整对话历史：多轮消息、工具调用、改动摘要、各轮终态结果。

### 3.8 会话管理

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

## 5. 工作流任务扩展预留

当前只做聊天会话，但为后续把工作流任务也纳入「会话记录」做如下预留，避免将来改数据模型和接口：

- **统一 `type` 字段**：conversation 列表项带 `type`（`chat` / `workflow`），列表接口 `GET /conversations?type=` 已预留筛选；会话记录页左栏筛选器预留「全部 / 聊天 / 工作流任务」选项；
- **统一列表视图**：工作流 run 未来作为一种 `type=workflow` 的条目混入同一列表（按时间排序），左栏交互不变；
- **详情页分流**：右侧详情按 `type` 渲染——`chat` 走对话回放，`workflow` 走 step/run 视图（后续设计），用同一个详情路由不同组件；
- **edge 区分**：聊天用 `POST /conversations` 即席创建；工作流由模板触发 run，仅在记录页只读展示，不在本模块新建。

> 本次不实现工作流相关页面与逻辑，仅保证数据模型（`type`）、列表接口（`type` 参数）、页面骨架（筛选器选项位）三处可平滑扩展。

## 6. 待确认 / 后续

| # | 议题 | 说明 |
|---|---|---|
| 1 | 会话与 session 的映射时机 | conversation 创建即建 session，还是首条消息时才建（影响空会话的资源占用）|
| 2 | SSE 透传 vs 重连补偿 | 编排层透传 Agent Core SSE 的断线补偿机制，与会话历史持久化的边界 |
| 3 | 标题自动生成 | 由 Agent 首轮总结生成，还是取首条消息截断 |
| 4 | 改动审查动作 | 查看 diff 后是否需要在聊天内提供「应用/丢弃/提 PR」等动作（涉及 repo 写回策略）|
| 5 | 多端同会话 | 同一用户多标签页打开同一 conversation 的并发处理（与 Agent Core 单会话串行衔接）|
