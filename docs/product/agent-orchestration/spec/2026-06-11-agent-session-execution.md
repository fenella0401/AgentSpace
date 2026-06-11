# SF1：Agent 会话创建与运行

## 1. 功能逻辑设计

用户在当前项目下选择 Agent，发起对话。编排层代理到 Agent Core，经 SSE 长连接流式返回执行事件。

**核心交互：**

1. **新建对话**：空白对话 + Agent 选择器（仅创建时可操作），输入首条消息触发首次建连；
2. **继续对话**：选中已有会话，在输入框输入后继续对话。Agent 不可更改（提示"已创建，不可更改"）；
3. **中止执行**：执行中提供中止按钮，停止本轮 agent 但不销毁会话。

**事件渲染：**

| delta.type | 前端渲染 |
|---|---|
| `thinking_delta` | 灰字 |
| `text_delta` | 正常消息气泡 |
| `content_block` type=`tool_use` | 工具调用折叠卡片 |
| `message_delta` stop=`end_turn` | 本轮完成，结果摘要 |
| `message_delta` stop=`cancelled` / `error` / `timeout` | 错误气泡 |

**编排层角色**：透明代理——不缓存事件、不转换内容。唯一逻辑：首次建连创建 conversation + 初始化 session（调 Agent Core），将执行事件按 Anthropic SSE 格式透传给前端。

## 2. 权限设计

| 操作 | 权限 |
|---|---|
| 创建会话 | 项目成员（ProjectMember 及以上）|
| 发送消息 / 续聊 | 会话创建者 |
| 中止执行 | 会话创建者 |
| 选择 Agent | 仅首次创建时可指定；`agentRef` 需在项目允许的 Agent 列表内 |

Agent 固定不可更改——即对话创建后 `agentRef` 不可修改，前端选择器禁用。

## 3. 流程逻辑设计

```text
用户输入消息 → 前端 GET /conversations/chat
  │
  ├─ 首次（无 conversationId）
  │    编排层创建 conversation → 调 Agent Core POST /sessions
  │    → SSE: message_start（返回 conversationId）
  │
  └─ 续聊（带 conversationId）
       编排层复用已有的 conversation → 调 Agent Core GET /sessions/{id}/chat

  → 编排层透明代理 SSE 流：
      content_block_delta (thinking_delta / text_delta / input_json_delta)
      → message_delta (stop_reason + summary/commitSha/sessionRef)
      → message_stop

  → 前端按 data.type 分发渲染到消息区

中止流程：
  用户点击「中止」→ POST /conversations/{id}/abort
  → 编排层代理 Agent Core abort → 消息区显示"已中止"

断线重连：
  SSE 断开 → 记录最后 sequence → 重建连接带 lastSequence → 编排层续传
```

## 4. 实现逻辑设计

**前端：**
- 会话创建时，Agent 选择器绑定到 conversation 创建请求，首次发送后禁用；
- SSE 连接通过 EventSource 或 fetch + ReadableStream 实现，按 `data.type` 分发渲染组件；
- 断线重连：维护 lastSequence，断开后指数退避重试。

**编排层：**
- conversationId → sessionId 映射维护（首次创建，续聊复用）；
- `GET /conversations/chat` 处理：首次无 conversationId 时调用 Agent Core `POST /sessions`，续聊时调 `GET /sessions/{id}/chat`；
- 透传 Agent Core SSE 事件到前端，仅做归属校验。

**Agent Core：**
- 见沙箱设计文档接口定义。

## 5. API 设计

### 5.1 对话 SSE（创建 + 发送 + 事件流合一）

`GET /conversations/chat`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversationId` | string | 否 | 续聊时传入；首次不传，SSE 流 `message_start` 事件中返回 |
| `content` | string | 是 | 用户输入 |
| `title` | string | 否 | 会话标题，不传首条消息后自动生成（仅首次生效）|
| `projectId` | string | 是 | 所属项目 |
| `agentRef` | string | 否 | 指定 agent；仅首次生效，不传用项目默认 |
| `lastSequence` | long | 否 | 断线重连续传游标 |

### 5.2 SSE 事件流

遵循 Anthropic Messages Streaming 规范。

| SSE event | data.type | 说明 |
|---|---|---|
| `message_start` | `message_start` | 消息帧开始，首次含 `conversationId` |
| `content_block_start` | `content_block_start` | 内容块开始。`content_block.type` = `text` / `tool_use` / `thinking` |
| `content_block_delta` | `content_block_delta` | 增量内容。`delta.type` = `text_delta` / `input_json_delta` / `thinking_delta` |
| `content_block_stop` | `content_block_stop` | 内容块结束 |
| `message_delta` | `message_delta` | 终止信息：`delta.stop_reason` + `payload`（summary/commitSha/sessionRef）|
| `message_stop` | `message_stop` | 消息帧结束 |
| `ping` | `ping` | 心跳 |

### 5.3 中止对话

`POST /conversations/{id}/abort`

停止本轮执行，保留会话，幂等。

## 6. 数据模型设计

### Conversation

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversationId` | string (PK) | 会话标识 |
| `projectId` | string | 所属项目 |
| `agentRef` | string | 使用的 Agent 运行时（创建后不可变）|
| `createdBy` | string | 创建人 |
| `title` | string | 会话标题（首次消息后自动生成）|
| `status` | enum | running / waiting_approval / done / failed |
| `sessionId` | string | 映射的 Agent Core session 标识 |
| `createdAt` | datetime | 创建时间 |
| `lastActiveAt` | datetime | 最近活跃时间 |