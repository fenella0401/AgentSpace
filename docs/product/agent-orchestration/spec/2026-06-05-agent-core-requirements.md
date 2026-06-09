# Agent Core 需求清单

> 从《Agent Core 接口与能力要求》提取合并。按优先级分三组：**P0**=核心，**P1**=必要，**P2**=增强。

## P0 —— 不具备则无法交付

### R1 初始化会话

提供 session 生命周期接口与环境准备：

- `POST /sessions` 创建会话。入参：`sessionKey`、`skillSnapshotRefs`、`mcpSnapshotRefs`、`knowledgeBaseRefs`（可选）、`contextRef`（可选）、`modelRef`（可选）、`repo`（可选）、`agentRuntime`、`configKey`（可选）。返回 `sessionId` + `chatUrl`。幂等。
- `GET /sessions/{id}` 查询状态，返回 `status`（ACTIVE/COMPLETED/FAILED）和 `lastActiveAt`。
- 事件回调通道，推送 session.created / session.failed / session.heartbeat。
- `DELETE /sessions/{id}` 销毁 session，幂等。

session 初始化时，按入参装配 skill 和 MCP server、注入短期凭证；按 `contextRef` 拉取项目知识说明（如 agents.md）注入对话上下文；按 `knowledgeBaseRefs` 挂载知识库；按 `repo` clone/checkout 代码仓（clone 时机自定）。不同 session 的环境相互独立。

初始化性能按入参自动分级，编排层不感知：

| 场景 | 入参特征 | 启动要求 | 沙箱建议 |
|---|---|---|---|
| 轻量级 | 无 repo、少量 skill/MCP | 百毫秒级 | WASM 或等同 |
| 中等 | 无 repo、有 contextRef 和较多 skill/MCP | 秒级 | 容器沙箱 |
| 完整 | 有 repo、需 clone 代码仓 | 十秒级 | Firecracker microVM 或等同 |

### R2 对话

提供 `GET /sessions/{id}/chat` 对话接口。建连参数：`prompt`、`resumeFromSessionRef`（可选）。按 `agentRuntime` 选择执行器启动代码 Agent，经 SSE 流式返回所有执行事件（thinking / message / tool_use / tool_result / stdout / stderr / session.completed / session.failed）。`session.completed` payload 含 summary、result、artifactRefs、commitSha、sessionRef。

对话过程中 agent 的代码改动负责 commit/push。对话上下文持久化落盘（键=sessionRef），执行进程退出后不丢，续聊时 `--resume` 重载恢复。外部对进程冷热无感知，续聊一律走 `GET /sessions/{id}/chat`。

续聊按空闲时长分级恢复：

| 空闲时长 | 恢复方式 | 目标 | 说明 |
|---|---|---|---|
| < 5 分钟 | 热恢复 | 毫秒级 | 沙箱 WARM 态直接续聊 |
| > 5 分钟 | 冷恢复 | 十秒级 | 重建沙箱后 `--resume` 冷启 |

不同 session 的对话上下文、文件、进程、网络相互隔离。Agent 不能跨 session 读取对话历史；session 间不共享本地盘，文件传递只通过代码仓 commit/checkout；凭证作用域限本 session，结束后回收，不在日志和事件中回显。

**对话数据保留**：以用户为单位，保留最近 50 条 session 且超过 15 天的才删除。两个条件同时满足方可清理。

---

## P1 —— 规模化与安全合规所需

### R3 安全管控

- 默认最小化出网，仅放行 allowlist（模型端点、已批准 MCP server、repo 源、知识库服务），禁止任意外联；
- 可执行的工具、命令、文件路径受策略约束，不开放任意脚本；
- Agent 只能访问本 session 的 workspace 路径及显式挂载的只读路径，越界拒绝；
- 命中高风险操作（修改鉴权、删除数据、变更 CI、访问生产、越权）时阻断并上报。

### R4 支持多种代码 Agent 运行时

支持多种 `agentRuntime`（当前需支持 `claude-code`），按 `modelRef` 选择本次模型。不同运行时的装配方式、prompt 格式、事件协议由 Agent Core 内部适配，对外 SSE 事件流保持统一协议。

### R5 资源管控

- 每个 session 可施加资源配额上限（CPU/内存/磁盘/时长），超限按策略处理；
- 资源池不足时 session 创建快速拒绝，不长时间阻塞；
- session 空闲时冻结沙箱释放算力、保留文件，需要时解冻恢复；
- 相同 `configKey` 的 session 缓存已就绪的环境模板，后续跳过环境准备快速复用。

---

## P2 —— 长期增强

### R6 效率与可观测

- 相邻 session 相同 MCP 快照时复用已启动的 MCP server 进程；
- 执行中上报审查规则命中及规模信号（工具调用数、耗时、改动范围）；
- session 空闲时沙箱自动流转（存活→冻结→回收），逐级释放资源；
- 上报 token 消耗、执行耗时、资源占用等指标；
- 执行环境镜像受版本与来源管控，可复现、可审计。
