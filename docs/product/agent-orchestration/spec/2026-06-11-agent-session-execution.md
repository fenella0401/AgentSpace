# SF1：Agent 会话创建与运行

> 覆盖范围：创建会话、发起对话、SSE 流式交互、中止对话、Agent 与项目选择。

## 1. 概述

用户在当前项目下选择 Agent，发起对话。编排层代理到 Agent Core，经 SSE 长连接流式返回执行事件。

## 2. 接口

### 2.1 对话 SSE（创建 + 发送 + 事件流合一）

`GET /conversations/chat`

一个 SSE 长连接承载全部——首次建连即创建 conversation + 发消息，续聊同地址。编排层内部：首次时初始化 session（调 Agent Core），续聊时复用。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversationId` | string | 否 | 续聊时传入；首次不传，编排层在 `conversation.created` 流事件中返回 |
| `content` | string | 是 | 用户输入 |
| `title` | string | 否 | 会话标题，不传则首条消息后自动生成（仅首次生效）|
| `projectId` | string | 是 | 所属项目 |
| `agentRef` | string | 否 | 指定 agent；**仅首次生效**（对话创建后不可更改），不传用项目默认 |

### 2.2 SSE 事件流

消息格式遵循 Anthropic Messages Streaming 规范。

| SSE event | data.type | 说明 |
|---|---|---|
| `message_start` | `message_start` | 消息帧开始。首次建连时含 `conversationId`；`message.model` 标识运行时 |
| `content_block_start` | `content_block_start` | 内容块开始。`content_block.type` 为 `text` / `tool_use` / `thinking`，`index` 自增 |
| `content_block_delta` | `content_block_delta` | 增量内容。`delta.type` 区分：`text_delta` / `input_json_delta` / `thinking_delta` |
| `content_block_stop` | `content_block_stop` | 内容块结束 |
| `message_delta` | `message_delta` | 终止信息：`delta.stop_reason`（`end_turn` / `tool_use` / `cancelled` / `error` / `timeout`）、`payload` 含 `summary` / `commitSha` / `artifactRefs` / `sessionRef` |
| `message_stop` | `message_stop` | 消息帧结束 |
| `ping` | `ping` | 心跳保活 |

示例：

```text
event: message_start
data: {"type":"message_start","message":{"id":"msg_001","model":"claude-code","content":[],"usage":{"input_tokens":0}},"conversationId":"conv-789"}

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

### 2.3 中止对话

`POST /conversations/{id}/abort`

停止本轮 agent 执行，保留会话可续聊。代理到 Agent Core 的 abort。幂等。

## 3. 页面交互

### 3.1 对话视图

中间栏为纯消息流 + 输入区：

1. **新对话**：空白对话 + 输入框上方 Agent 选择器（仅新对话时可操作）；首条消息触发 `GET /conversations/chat`（不传 conversationId），`conversation.created` 流事件中拿到 conversationId 后保存；
2. **继续对话**：点左栏已有会话 → 加载对话历史，输入框 Agent 选择器禁用，显示"已创建，不可更改"；
3. 发送后建立 SSE 连接，按 `data.type` 分发渲染：
   - `thinking_delta` → 灰字
   - `text_delta` → 正常消息气泡
   - `content_block` type=`tool_use` → 工具调用折叠卡片
   - `message_delta` stop=`end_turn` → 本轮完成，显示结果摘要
   - `message_delta` stop=`cancelled` / `error` / `timeout` → 错误气泡
4. 执行中输入框禁用 + 「中止」按钮（调 abort）；需人工确认的步骤显示「批准继续/驳回」；
5. `message_stop` 后关闭连接，可继续输入发起续聊。

### 3.2 编排层角色

编排层是**透明代理**——不缓存事件、不转换内容。唯一逻辑：首次建连创建 conversation + 初始化 session（调 Agent Core），将 Agent Core 的执行事件按上述 SSE 格式透传给前端。

## 4. 功能清单

| 功能 | 优先级 | 说明 |
|---|---|---|
| 创建会话并发送首条消息 | P0 | `GET /conversations/chat`，首次建连即创建 |
| SSE 流式渲染 | P0 | 遵循 Anthropic Messages Streaming 协议 |
| 续聊 | P0 | 带 `conversationId` 继续对话 |
| 中止执行 | P0 | `POST /conversations/{id}/abort` |
| 断线重连 | P1 | SSE 按 `sequence` 续传 |
| Agent 选择 | P0 | 仅创建时可配，后续不可更改 |
