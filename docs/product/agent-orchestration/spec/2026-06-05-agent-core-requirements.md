# Agent Core 需求清单

> 从《Agent Core 接口与能力要求》提取，按条整理。每条标注优先级：**P0**=核心、**P1**=必要、**P2**=增强。

## 接口

**R1** [P0] 提供 `POST /sessions` 创建会话。入参：`sessionKey`、`skillSnapshotRefs`、`mcpSnapshotRefs`、`knowledgeBaseRefs`（可选）、`contextRef`（可选）、`modelRef`（可选）、`repo`（可选）、`agentRuntime`、`configKey`（可选）。返回 `sessionId` + `chatUrl`。同一 `sessionKey` 重复调用幂等返回已有 `sessionId`。

**R2** [P0] 提供 `GET /sessions/{id}/chat` 对话接口。建连参数：`prompt`、`resumeFromSessionRef`（可选）。经 SSE 流式返回执行事件（thinking / message / tool_use / tool_result / stdout / stderr / session.completed / session.failed / governance.signal）。终态事件 payload 含 summary、result、artifactRefs、commitSha、sessionRef。

**R3** [P0] 提供 `GET /sessions/{id}` 查询 session 状态。返回 `status`（ACTIVE / COMPLETED / FAILED）和 `lastActiveAt`。

**R4** [P0] 提供事件回调通道。主动推送 session.created、session.failed、session.heartbeat 给外部。

**R5** [P0] 提供 `DELETE /sessions/{id}` 销毁 session。幂等，session 不存在时返回可识别状态。

## 会话与执行

**R6** [P0] session 初始化时按传入引用装配 skill 和 MCP server，注入短期凭证。

**R7** [P0] 按 `contextRef` 拉取项目知识说明（如 agents.md）注入对话上下文。

**R8** [P0] 按 `RepoRef` clone/checkout 代码仓，clone 时机自定。对话过程中 agent 的代码改动负责 commit/push。

**R9** [P0] 按 `knowledgeBaseRefs` 挂载文件形态知识库，或注册检索服务形态知识库。

**R10** [P0] 接收 prompt 启动代码 Agent 执行，SSE 流式返回所有事件。

**R11** [P0] 支持续聊：断连后重连 `GET /sessions/{id}/chat`，带 `resumeFromSessionRef` 恢复对话上下文继续执行。

**R12** [P0] 对话上下文持久化落盘（键 = sessionRef），执行进程退出后上下文不丢。续聊时 `--resume` 重载恢复。

**R13** [P1] 相同 `configKey` 的 session，缓存已就绪的环境模板，后续同配置 session 创建时跳过环境准备快速复用。

**R14** [P2] 相邻 session 引用相同 MCP 快照时，复用已启动的 MCP server 进程。

**R15** [P2] 执行中上报治理信号：审查规则命中 + 规模信号（工具调用数、耗时、改动范围）。

## 隔离

**R16** [P0] 不同 session 的对话上下文互不可见，Agent 不能跨 session 读取对话历史。

**R17** [P0] 不同 session 的文件操作相互隔离，不共享本地盘。跨 session 文件传递只能通过代码仓 commit/checkout。

**R18** [P0] 不同 session 的执行进程互不可见，进程命名空间隔离。

**R19** [P0] 不同 session 的网络命名空间隔离。

**R20** [P0] 凭证作用域限本 session，session 结束后回收。日志、事件、SSE 流不回显凭证明文。

## 安全

**R21** [P0] Agent 只能访问本 session 的 workspace 路径及显式挂载的只读路径，越界读写被拒绝。

**R22** [P1] 默认最小化出网，仅放行 allowlist（模型端点、已批准 MCP server、repo 源、知识库服务），禁止任意外联。

**R23** [P1] 可执行的工具、命令、文件路径受策略约束，不开放任意脚本执行和任意文件系统访问。

**R24** [P1] 命中高风险操作（修改鉴权配置、删除数据、变更 CI、访问生产、越权操作）时阻断执行并上报。

## 资源与生命周期

**R25** [P0] 每个 session 创建隔离沙箱，session 销毁时回收沙箱及所有关联资源（容器、进程、临时存储），清理幂等。

**R26** [P0] 提供 session 状态查询，供外部轮询判活。

**R27** [P1] 每个 session 可施加资源配额上限，超限按策略处理。

**R28** [P1] 资源池不足时 session 创建快速拒绝，不长时间阻塞。

**R29** [P1] session 空闲时冻结沙箱释放算力、保留文件，需要时解冻恢复。

**R30** [P1] 以用户为单位保留 session 数据：保留天数 ≥ 15 天 AND 用户 session 数 > 50 条，两个条件同时满足方可清理。

**R31** [P2] session 空闲时沙箱自动流转（存活→冻结→回收），逐级释放资源。

**R32** [P2] 上报 token 消耗、执行耗时、资源占用等指标。

**R33** [P2] 执行环境镜像受版本与来源管控，保证可复现、可审计。

## 代码 Agent 运行时

**R34** [P0] 支持多种 `agentRuntime`，按类型路由到对应执行器。当前需支持 `claude-code`。根据 `modelRef` 选择本次使用的模型。

**R35** [P0] 不同 agentRuntime 的 skill/MCP 装配方式、prompt 格式、事件协议由 Agent Core 内部适配，对外事件流保持统一协议。

## 初始化性能

**R36** 按入参场景分级启动性能：

| 场景 | 入参特征 | 启动要求 | 沙箱建议 |
|---|---|---|---|
| 轻量级 | 无 repo、少量 skill/MCP | 百毫秒级 | WASM 或等同轻量运行时 |
| 中等 | 无 repo、有 contextRef 和较多 skill/MCP | 秒级 | 容器沙箱 |
| 完整 | 有 repo、需 clone 代码仓 | 十秒级 | Firecracker microVM 或等同 |

沙箱选型为建议，具体由 Agent Core 自定。编排层不感知沙箱类型。

## 续聊恢复性能

**R37** 按空闲时长分级恢复：

| 空闲时长 | 恢复方式 | 目标 | 说明 |
|---|---|---|---|
| < 5 分钟 | 热恢复 | 毫秒级 | 沙箱 WARM 态直接续聊 |
| > 5 分钟 | 冷恢复 | 十秒级 | 重建沙箱后 `--resume` 冷启 |

编排层对冷热无感知，续聊一律走 `GET /sessions/{id}/chat`，只体现在延迟差异。
