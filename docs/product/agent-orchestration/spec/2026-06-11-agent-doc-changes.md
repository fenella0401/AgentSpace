# SF3：Agent 文档变更查阅

## 1. 功能逻辑设计

对话结束后（或执行中），在右侧栏展示本 session 改动的文件列表。点击文件以弹窗全屏查看 unified diff。

**交互：**

1. 选中会话后，右侧栏「文件目录」区展示本次变更文件列表；
2. 每项显示变更类型标记（M 修改 / A 新增 / D 删除 / R 重命名）、文件路径、增删行数（`+N -M`）；
3. 列表随 SSE 执行事件增量更新——执行中实时刷新，跑完定稿；
4. 点击列表中某文件 → 拉取单文件 diff → 弹窗全屏展示 unified diff（80vw × 85vh）；
   - 增行绿色背景（`+`），删行红色背景（`-`），hunk 头蓝色；
   - 弹窗标题显示文件路径及增删行数；
   - 关闭方式：点 ✕ 或点击遮罩背景；
5. 执行中也可提前查看已提交的改动。

## 2. 权限设计

| 操作 | 权限 |
|---|---|
| 查看变更文件列表 | 项目成员（ProjectMember 及以上）|
| 查看文件 diff | 项目成员（ProjectMember 及以上）|

> 文件变更是本次对话副产物，权限跟随会话查看权限。

## 3. 流程逻辑设计

```text
会话选中 / SSE 执行中
  │
  ├─ 文件清单初始化: GET /conversations/{id}/changes（不带 path）
  │    → 渲染文件列表到右栏
  │
  ├─ 执行中实时更新: SSE 流中收到改动事件
  │    → 前端增量更新文件清单（新增文件/更新行数）
  │
  └─ 用户点击文件
       → GET /conversations/{id}/changes?path=xxx
       → 弹窗渲染 unified diff
       → 用户关闭弹窗
```

## 4. 实现逻辑设计

**前端：**
- 文件清单组件：监听 SSE 改动事件，增量更新列表项（新增文件 push、已有文件更新行数）；
- Diff 弹窗组件：懒加载——点击时才请求单文件 diff，弹窗内渲染 unified diff 文本；
- 超大 diff 处理：超过阈值时截断显示，提示"diff 过大，部分内容已省略"。

**编排层：**
- 代理 Agent Core 的 changes 接口；
- 不做额外处理，直接透传。

**Agent Core：**
- 基于 workspace 内 git 工作区计算文件改动；
- 提供文件列表和单文件 diff。

## 5. API 设计

### 5.1 查看本轮改动

`GET /conversations/{id}/changes`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `path` | string | 否 | 不传返回文件列表；传路径返回该文件 unified diff |

**文件列表响应：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `baseCommit` | string | 改动基线 commit |
| `headCommit` | string | 当前 commit |
| `files` | object[] | 改动文件列表 |
| `files[].path` | string | 文件路径 |
| `files[].changeType` | enum | M（修改）/ A（新增）/ D（删除）/ R（重命名）|
| `files[].additions` | int | 新增行数 |
| `files[].deletions` | int | 删除行数 |

**单文件 diff 响应：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `path` | string | 文件路径 |
| `changeType` | enum | M / A / D / R |
| `diff` | string | 统一 diff 格式文本 |
| `truncated` | boolean | diff 过大时截断标记 |

> 二进制文件不返回 diff，仅标记为 `binary: true`。

## 6. 数据模型设计

文件变更数据不单独持久化——查询接口基于 Agent Core workspace 内的 git 工作区实时计算。编排层仅透传，不落库。

仅在 conversation 消息流中有引用记录：`message_delta.payload.commitSha` 可定位本次改动对应的 commit。文件清单和 diff 的内容存储完全由 Agent Core 管理。