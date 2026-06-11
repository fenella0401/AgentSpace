# SF2：Agent 会话历史与详情查看

> 覆盖范围：会话列表、状态筛选、会话详情与概览信息、workflow 轮询进度。

## 1. 概述

左栏展示所有对话历史，按状态和时间筛选；选中文档后，右栏展示会话概览信息。workflow 类型（事件任务/定时任务）在中间栏渲染 AgentFlow 横向流程图。

## 2. 接口

### 2.1 会话列表

`GET /conversations`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | enum | 否 | 对话类型：`ad-hoc`（临时会话）/ `event`（事件任务）/ `scheduled`（定时任务）。默认不筛选 |
| `status` | enum | 否 | running / waiting_approval / done / failed |
| `cursor` / `limit` | - | 否 | 分页 |

返回：

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` | string | 会话标识 |
| `title` | string | 会话名称（无标题时取首条消息摘要）|
| `type` | enum | `ad-hoc` / `event` / `scheduled` |
| `status` | enum | running / waiting_approval / done / failed |
| `lastActiveAt` | datetime | 最近活跃时间（列表按此倒序）|
| `agentRuntime` | string | 使用的 Agent 运行时（如 claude-code）|

### 2.2 会话详情

`GET /conversations/{id}`

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` / `title` / `type` / `status` | - | 基本信息 |
| `projectId` / `agentRuntime` / `createdBy` / `startedAt` / `endedBy` | - | 概览字段（右栏信息栏展示）|
| `runId` | string | 关联任务（`event` / `scheduled` 时有）|
| `agentFlow` | object | 仅 `event` / `scheduled` 类型：`{ flowRef, version, steps[] }`，steps 含名称/状态/`conversationId`。中间栏以此渲染 AgentFlow 横向流程 |
| `eventSource` | object | 仅事件触发时：`{ type, summary }`，信息栏「事件来源」字段 |
| `messages` | object[] | 对话历史 |

### 2.3 轮询查询（workflow 进度）

`GET /conversations/{id}/poll`

供前端**固定间隔轮询**。主要用于 workflow 场景——前端未保持 SSE 长连接时，获取各 step 最新状态以刷新中间栏 AgentFlow 横向流程；也用于 SSE 断线后补偿同步。

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `since` | datetime | 否 | 增量查询：返回该时间之后发生状态变化的 step |

**Response：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` / `title` / `status` | - | 基本信息 |
| `progress` | object | 仅 `event` / `scheduled`：`{ completed, total }` |
| `steps` | object[] | 每项 `stepId` / `name` / `status` / `order` / `conversationId` |
| `activeStep` | string | 当前执行中的 stepId |

> 建议轮询间隔：**运行中 5~10s，待审批 10~15s，已完成/失败停止轮询**。

### 2.4 会话管理

- `PATCH /conversations/{id}`：重命名（`title`）；
- `POST /conversations/{id}/archive`：归档；
- `DELETE /conversations/{id}`：删除。

## 3. 页面交互

### 3.1 左栏：会话列表

1. 进入页面 → 拉取会话列表（`GET /conversations`，分页），默认选中最近一条；
2. 列表项显示标题、最近活跃时间、状态点、Agent 标识、类型标签（临时会话/事件任务/定时任务）；
3. 列表按时间倒序，不展开 step 细节；workflow 的 step 状态通过轮询反映在中间栏；
4. 顶部搜索框 + 新建按钮；底部状态标签栏切换筛选（全部/running/waiting/done/failed）；
5. 列表项 hover 出现重命名、归档、删除。

### 3.2 右栏：会话概览

网格布局展示：

- 通用字段：会话名称、类型、关联任务、状态、运行时、项目、发起人、开始时间、结束人；
- `event` / `scheduled` 类型额外：当前步骤、所属 AgentFlow、事件来源。

### 3.3 中间栏：AgentFlow 横向流程

`event` / `scheduled` 类型会话选中后，中间栏标题下方渲染 **AgentFlow 横向流程图**——各 step 节点带状态点（✓完成/▶进行中/◻待执行），当前步骤高亮。状态通过 `GET /conversations/{id}/poll` 轮询刷新。

## 4. 功能清单

| 功能 | 优先级 | 说明 |
|---|---|---|
| 会话列表 | P0 | 按时间倒序，状态筛选 |
| 会话详情 | P0 | 概览字段 + 对话历史 |
| 轮询进度 | P0 | workflow step 状态定时刷新 |
| AgentFlow 横向流程 | P0 | `event` / `scheduled` 类型的步骤可视化 |
| 会话检索 | P1 | 按标题搜索 |
| 会话管理 | P1 | 重命名、归档、删除 |
| 会话数据保留 | P1 | 用户级最近 50 条 + 超 15 天 |