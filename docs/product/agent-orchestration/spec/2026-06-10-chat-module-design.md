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

单页面：左侧会话列表（含「新建」、聚合与过滤），右侧当前会话——含「对话」和「变更」两个视图。新建与查看/续聊历史共用同一布局。

布局：

```text
┌────────────────┬──────────────────────────────────┐
│ [+ 新建] 🔍搜索 │  会话标题            [对话][变更] [⋯]│
│                │──────────────────────────────────│
│ 聚合: [状态▾]   │  ┌─ 变更视图 ──────────────────┐  │
│ 过滤: [类型▾]   │  │  变更文件清单                 │  │
│────────────────│  │   M  src/a.ts   +12 -3       │  │
│ ▸ 进行中 (2)    │  │   A  src/b.ts   +40          │  │
│   ● 会话A  今天 │  │   D  old.ts     -20          │  │
│   ● 会话B  1h   │  ├──────────────────────────────┤  │
│ ▸ 已完成 (5)    │  │  选中文件的 diff               │  │
│   会话C  昨天   │  │   @@ -1,3 +1,5 @@             │  │
│   会话D  3天前  │  │   - old line                 │  │
│ ▸ 失败 (1)      │  │   + new line                 │  │
│   会话E  3天前  │  └──────────────────────────────┘  │
│                │  [输入框................] [发送]    │
└────────────────┴──────────────────────────────────┘
```

### 2.1 左栏：会话列表（聚合 + 过滤）

1. 进入页面 → 拉取会话列表（`GET /conversations`，分页），默认选中最近一条或停留在「新对话」；
2. **聚合**：列表可按维度分组折叠展示，每组带计数：
   - 按**状态**：进行中 / 已完成 / 失败 / 已归档；
   - 按**任务类型**：聊天 / 工作流任务（工作流为预留，见 §5）；
   - 默认按状态聚合，可切换；
3. **过滤**：按类型、状态、时间范围过滤，与搜索叠加；
4. 列表项显示标题（无标题时取首条消息摘要）、最近活跃时间、状态点；
5. 顶部「+ 新建」→ 右侧切到空白新对话（首条消息发送时再建 conversation，见 §6#1）；
6. 搜索按标题/内容检索；列表项 hover 出现重命名、归档、删除；
7. 空状态：无会话时右侧直接是新对话引导。

### 2.2 右栏：会话视图（对话 / 变更）

右栏顶部切换两个视图：

**「对话」视图**（默认）——承载新建与续聊：

1. **新对话**：空白对话 + 输入框。首条消息 → `POST /conversations`（若尚未创建）+ `POST /conversations/{id}/messages`，左栏插入并选中；
2. **历史会话**：点左栏项 → 加载完整对话历史（`GET /conversations/{id}`），可直接续聊；
3. 发送后发起 SSE 订阅，流式渲染 agent 执行事件；一轮结束（`session.completed`）展示结果摘要；继续输入则续聊；
4. 渲染细节：thinking 灰字、message 气泡、tool_use/tool_result 折叠为可展开工具卡片；执行中输入框禁用 + 「中止」按钮（调 abort）；session.failed/aborted/timeout 以错误气泡提示、可重试。

**「变更」视图**——展示本会话对代码仓的改动：

1. **上半部分：变更文件清单** —— 拉取 `GET /conversations/{id}/changes`（不带 path），列出改动文件：变更类型标记（M/A/D/R）、路径、增删行数（`+12 -3`）；
2. **下半部分：文件 diff** —— 点击清单中某文件 → 拉取 `GET /conversations/{id}/changes?path=xxx`，下半部分显示该文件的 unified diff（增删行高亮）；
3. 有改动时「变更」标签显示文件数角标（如 `变更 ³`）；无改动时视图为空状态；
4. 执行中实时刷新清单（随 SSE 改动事件增量更新），跑完定稿。



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
