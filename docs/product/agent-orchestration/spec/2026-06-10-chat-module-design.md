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

单页面三栏：**左**会话列表、**中**会话内容（顶部信息栏 + 对话/变更）、**右**变更文件（清单 + diff）。新建与查看/续聊历史共用同一布局。

布局：

```text
┌──────────────┬────────────────────────────┬─────────────────┐
│ [+ 新建对话] │  会话标题        [对话][变更]│ 变更文件   ³     │
│──────────────│────────────────────────────│─────────────────│
│ 进行中 (2)   │  ┌─ 信息栏（网格）────────┐│ M src/a.ts +12  │
│  会话A  今天 │  │ 会话名 类型 关联任务 …  ││ M src/b.ts  +9  │
│ ▾ 流水线 1h  │  │ AgentFlow 横向流程 ●→●→○││ A c.test.ts +34 │
│   ✓ step1    │  └────────────────────────┘│─────────────────│
│   ▶ step3    │                            │ 选中文件 diff    │
│   ○ step4    │  （对话内容 / 变更视图）    │ @@ -1,3 +1,5   │
│ 已完成 (3)   │                            │ - old          │
│  会话C  昨天 │                            │ + new          │
│ 失败 (1)     │  [输入框..........] [发送]  │                 │
└──────────────┴────────────────────────────┴─────────────────┘
```

### 2.1 左栏：会话列表

1. 进入页面 → 拉取会话列表（`GET /conversations`，分页），按状态分组（进行中/已完成/失败），默认选中最近一条或停留在「新对话」；
2. 列表项显示标题、最近活跃时间、状态点、类型标签（聊天/工作流）；
3. **workflow 会话可展开**：点标题前箭头展开，内嵌显示 AgentFlow 每个 step（带状态点）；点某 step → 中间区加载该 step 的对话（`GET /conversations/{id}/steps/{stepId}/messages`）；
4. 顶部「+ 新建对话」→ 中间区切到空白新对话（首条消息发送时再建 conversation，见 §6#1）；
5. 列表项 hover 出现重命名、归档、删除。

> 搜索、聚合、类型过滤为后续能力，本期不做（见 §5）。

### 2.2 中栏：会话内容（信息栏 + 对话/变更）

**顶部信息栏**（网格布局）展示会话概览：

- 通用字段：会话名称、类型、关联任务、状态、项目、发起人、开始时间、结束人；
- workflow 额外：当前步骤、所属 AgentFlow、事件来源；信息栏内渲染 **AgentFlow 横向流程图**（各 step 节点+状态，当前步骤高亮）。

信息栏下方切换两个视图：

**「对话」视图**（默认）——承载新建、续聊、step 对话：
1. 新对话/历史会话/step 对话 → 加载对应消息流并渲染；
2. 发送后发起 SSE 订阅，流式渲染 agent 执行事件；一轮结束（`session.completed`）展示结果摘要；
3. 渲染细节：thinking 灰字、message 气泡、tool_use/tool_result 折叠为可展开工具卡片；执行中输入框禁用 + 「中止」按钮（调 abort）；需人工确认的步骤显示「批准继续/驳回」；session.failed/aborted/timeout 以错误气泡提示、可重试。

**「变更」视图**——本会话对代码仓的改动（详细 diff 在右栏）。



## 3. API 设计（前端 ↔ 编排层）

> 编排层对前端暴露的聊天会话 API。编排层内部再调 Agent Core 的 session 接口（见沙箱设计）。

### 3.1 发起对话（创建会话 + 发送消息合二为一）

`POST /conversations`

首次调用创建 conversation，再次调用续聊。编排层内部：首次时初始化 session（调 Agent Core `POST /sessions`），续聊时复用已有 session 发 `GET /sessions/{id}/chat`。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversationId` | string | 否 | 续聊时传入；新对话不传，编排层自动创建 |
| `content` | string | 是 | 用户输入 |
| `title` | string | 否 | 会话标题，不传则首条消息后自动生成（仅首次生效）|
| `projectId` | string | 是 | 所属项目（决定 repo、harness 配置等执行上下文）|
| `agentRef` | string | 否 | 指定 agent；不传用项目默认 |

返回：`conversationId`、`messageId`、`status` + SSE 流地址。编排层据此向 Agent Core 发起 session 对话并透传 SSE 事件。

### 3.2 对话事件流（SSE）

编排层把 Agent Core 的 SSE 事件透传给前端：thinking / message / tool_use / tool_result / stdout / stderr / session.completed / session.failed / session.aborted / session.timeout。事件基座字段含 `eventId` / `eventType` / `sequence` / `timestamp`，支持断线重连按 `sequence` 续传。

### 3.3 中止当前对话

`POST /conversations/{id}/abort`

停止本轮 agent 执行，保留会话可续聊。代理到 Agent Core 的 abort。

### 3.4 查看本轮改动

`GET /conversations/{id}/changes`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `path` | string | 否 | 不传返回改动文件列表；带 `path` 返回该文件 unified diff |

代理到 Agent Core 的 changes 接口。

### 3.5 会话列表

`GET /conversations`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | enum | 否 | 预留：`chat`（默认）/ `workflow` / `all`（见 §5）|
| `status` | enum | 否 | 进行中/已完成/失败 |
| `cursor` / `limit` | - | 否 | 分页 |

返回：会话列表 + 分页游标。每个列表项：

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` | string | 会话标识 |
| `title` | string | 会话名称 |
| `type` | enum | `chat` / `workflow` |
| `status` | enum | running / completed / failed |
| `lastActiveAt` | datetime | 最近活跃时间 |
| `steps` | object[] | 仅 `workflow`：step 列表，每项 `stepId` / `name` / `status` / `order`，供左栏展开展示 |

> 搜索、聚合、类型过滤为后续能力，本期不做（见 §5）。

### 3.6 会话详情

`GET /conversations/{id}`

返回会话概览 + 对话历史：

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` / `title` / `type` / `status` | - | 基本信息 |
| `projectId` / `agentRuntime` / `createdBy` / `startedAt` / `endedBy` | - | 概览字段（前端信息栏展示）|
| `runId` | string | 关联任务（workflow 时有）|
| `agentFlow` | object | 仅 workflow：`{ flowRef, version, steps[] }`，steps 含名称与状态，供横向流程图与当前步骤展示 |
| `eventSource` | object | 仅事件触发：`{ type, summary, ... }`，信息栏「事件来源」字段展示 |
| `messages` | object[] | 对话历史：多轮消息、工具调用、改动摘要、各轮终态结果 |

### 3.7 查看 step 对话（workflow）

`GET /conversations/{id}/steps/{stepId}/messages`

工作流的每个 step 是独立 session，有自己的对话。左栏展开 workflow 后点击某 step → 拉取该 step 的对话历史，中间区渲染。返回结构同 §3.7 的 `messages`，附该 step 的状态与产出。

> 普通聊天会话无 step，直接用 §3.7 的 `messages`。

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
