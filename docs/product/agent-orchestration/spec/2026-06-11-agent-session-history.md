# SF2：Agent 会话历史与详情查看

## 1. 功能逻辑设计

左栏展示所有对话历史，按状态筛选、按时间倒序；选中会话后，右栏展示概览信息，中间栏展示对话历史或 AgentFlow 横向流程（workflow 类型）。

**列表交互：**

1. 进入页面 → 拉取会话列表，默认选中最近一条；
2. 列表项显示标题、最近活跃时间、状态点（running/waiting/done/failed）、Agent 标识、类型标签（临时会话/事件任务/定时任务）；
3. 顶部状态标签栏切换筛选（全部/running/waiting/done/failed）；
4. 列表按时间倒序，不展开 step 细节；
5. 列表项 hover 出现重命名、归档、删除。

**详情交互：**

1. 选中会话 → 右栏加载概览信息（网格布局）、中间栏加载对话历史；
2. `event` / `scheduled` 类型：中间栏标题下方渲染 AgentFlow 横向流程图，各 step 带状态节点（✓已完成 / ▶进行中 / ◻待执行），当前步骤高亮；
3. step 状态通过 `GET /conversations/{id}/poll` 定期轮询刷新。

**轮询机制：**
- workflow 会话选中后启动轮询；
- 间隔：运行中 5~10s，待审批 10~15s，已完成/失败停止；
- 轻量增量查询：可带 `since` 参数只获取变化的 step。

## 2. 权限设计

| 操作 | 权限 |
|---|---|
| 查看会话列表 | 项目成员（ProjectMember 及以上）|
| 查看会话详情 | 项目成员（ProjectMember 及以上）；敏感字段（如 `eventSource` 详情）仅会话创建者和 ProjectOwner 可见 |
| 重命名 / 归档 / 删除会话 | 会话创建者或 ProjectOwner |

## 3. 流程逻辑设计

```text
页面加载
  │
  ├─ 左栏: GET /conversations → 渲染列表（按状态分组，时间倒序）
  │    用户切换状态标签 → 前端过滤显示
  │
  └─ 用户点击会话
        │
        ├─ GET /conversations/{id} → 右栏渲染概览 + 中间栏加载对话历史
        │
        ├─ 若 type = event / scheduled
        │    → 中间栏渲染 AgentFlow 横向流程（从 agentFlow.steps 取初始状态）
        │    → 启动轮询定时器: GET /conversations/{id}/poll
        │    → 每次轮询更新 step 状态节点
        │    → 状态变为 done / failed 时停止轮询
        │
        └─ 用户切换会话 → 清理旧轮询，重新执行上述流程

会话管理:
  重命名: PATCH /conversations/{id} { title }
  归档:   POST /conversations/{id}/archive
  删除:   DELETE /conversations/{id}
```

## 4. 实现逻辑设计

**前端：**
- 左栏会话列表组件：虚拟滚动（大量会话时）、状态筛选前端过滤；
- AgentFlow 流程组件：渲染 step 节点 + 状态点 + 箭头连线，当前步骤高亮；
- 轮询管理：useEffect / watch 监听选中会话变化，启动/清理定时器。

**编排层：**
- conversation 元数据管理：CRUD 接口 + 列表查询；
- poll 接口：从 Agent Core 获取 session 状态，聚合为 step 状态返回。

**Agent Core：**
- session 状态查询、step 进度信息由 Agent Core session 接口提供。

## 5. API 设计

### 5.1 会话列表

`GET /conversations`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | enum | 否 | `ad-hoc` / `event` / `scheduled` |
| `status` | enum | 否 | running / waiting_approval / done / failed |
| `keyword` | string | 否 | 标题搜索 |
| `cursor` / `limit` | - | 否 | 分页 |

返回 `items[]` + 分页游标。

### 5.2 会话详情

`GET /conversations/{id}`

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` / `title` / `type` / `status` | - | 基本信息 |
| `projectId` / `agentRuntime` / `createdBy` / `startedAt` / `endedBy` | - | 概览字段 |
| `runId` | string | 关联任务 |
| `agentFlow` | object | `{ flowRef, version, steps[] }`，steps 含 `stepId` / `name` / `status` / `order` / `conversationId` |
| `eventSource` | object | `{ type, summary }`，事件来源 |
| `messages` | object[] | 对话历史 |

### 5.3 轮询查询

`GET /conversations/{id}/poll`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `since` | datetime | 否 | 增量：返回该时间后状态变化的 step |

返回 `steps[]` + `progress`（`{ completed, total }`）+ `activeStep`。

### 5.4 会话管理

- `PATCH /conversations/{id}`（重命名）
- `POST /conversations/{id}/archive`
- `DELETE /conversations/{id}`

## 6. 数据模型设计

### Conversation（会话主表）

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` | string (PK) | 会话标识 |
| `projectId` | string | 所属项目 |
| `type` | enum | `ad-hoc` / `event` / `scheduled` |
| `status` | enum | running / waiting_approval / done / failed |
| `agentRuntime` | string | Agent 运行时 |
| `createdBy` | string | 创建人 |
| `title` | string | 会话标题 |
| `startedAt` | datetime | 开始时间 |
| `endedAt` | datetime | 结束时间 |
| `endedBy` | string | 结束人 |
| `lastActiveAt` | datetime | 最近活跃时间（列表排序键）|

### AgentFlow 步骤（内嵌于 agentFlow 字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `flowRef` | string | AgentFlow 引用 |
| `version` | string | AgentFlow 版本 |
| `steps[]` | array | step 列表 |
| `steps[].stepId` | string | step 标识 |
| `steps[].name` | string | step 名称 |
| `steps[].status` | enum | running / done / failed / pending |
| `steps[].order` | int | 排序序号 |
| `steps[].conversationId` | string | step 对应的独立 conversation（可用于查询对话）|

### 事件来源（内嵌）

| 字段 | 类型 | 说明 |
|---|---|---|
| `eventSource.type` | string | 事件类型（如 GitHub Release）|
| `eventSource.summary` | string | 事件摘要 |