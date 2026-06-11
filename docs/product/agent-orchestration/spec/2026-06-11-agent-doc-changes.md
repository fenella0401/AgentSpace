# SF3：Agent 文档变更查阅

> 覆盖范围：查看本次对话的代码仓改动——变更文件清单与 diff。

## 1. 概述

对话结束后，在右侧栏展示本 session 改动的文件列表。点击文件以弹窗查看完整 unified diff。

## 2. 接口

### 2.1 查看本轮改动

`GET /conversations/{id}/changes`

代理到 Agent Core 的 changes 接口。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `path` | string | 否 | 不传返回改动文件列表；带 `path` 返回该文件 unified diff |

**文件列表响应：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `baseCommit` | string | 改动基线 commit |
| `headCommit` | string | 当前 commit |
| `files` | object[] | 改动的文件列表，每项含 `path`、`changeType`（M/A/D/R）、`additions`、`deletions` |

**文件 diff 响应（带 `path`）：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `path` | string | 文件路径 |
| `changeType` | enum | M / A / D / R |
| `diff` | string | 统一 diff 格式（unified diff）文本 |

> 超大 diff / 二进制文件按策略截断或标记，不撑爆响应。

## 3. 页面交互

### 3.1 右栏：文件目录

选中某会话后，右侧栏「文件目录」区展示本次变更文件列表：

- 每项显示变更类型标记（M 修改 / A 新增 / D 删除 / R 重命名）、文件路径、增删行数（`+N -M`）；
- 列表随 SSE 执行事件增量更新（执行中实时刷新，跑完定稿）。

### 3.2 文件 diff 弹窗

点击列表中某文件 → 拉取 `GET /conversations/{id}/changes?path=xxx`，弹出全屏弹窗（80vw × 85vh）展示 unified diff：

- 增行绿色背景（`+`），删行红色背景（`-`），hunk 头蓝色；
- 弹窗标题显示文件路径及增删行数；
- 关闭方式：点 ✕ 或点击遮罩背景；
- 执行中也可提前查看已提交的改动。

## 4. 功能清单

| 功能 | 优先级 | 说明 |
|---|---|---|
| 变更文件清单 | P0 | 本次改动的文件列表（M/A/D/R + 增删行数）|
| 文件 diff 弹窗 | P0 | 点击文件弹出全屏 unified diff |
| SSE 增量更新 | P1 | 执行中实时刷新文件清单 |