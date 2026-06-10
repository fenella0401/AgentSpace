# Agent Core 需求清单

> 从《Agent Core 接口与能力要求》提取合并。按优先级分三组：**P0**=核心，**P1**=必要，**P2**=增强。

## 架构

```text
┌─ Agent Orchestration（编排层）──────────────────────────┐
│                                                        │
│  ┌─ Workflow ──────────┐   ┌─ 通用任务 ────────┐      │      ┌─ Agent-Management ───────┐
│  │  step DAG           │   │  无需模板，即席触发 │      │      │  （业务控制面）           │
│  │  run/step/attempt   │   │                   │      │◄─────┤                          │
│  │  requiresConfirmation│  │                   │      │ 启动 │  harness 配置：           │
│  └─────────┬───────────┘   └────────┬──────────┘      │ run  │   agent / skill / MCP /   │
│            │ step 就绪              │ 触发 session 创建 │      │   知识库 / 上下文 / 模型   │
│            └──────────┬────────────┘                   │      │  版本化，发布即不可变      │
│                       ▼                                │      └──────────┬───────────────┘
│  组装 & 调度：                                          │                 │
│    · harnessRef（整体兜底，命中已同步配置）              │                 │ harness 发布时
│    · 或具体字段：skill/MCP/知识库/上下文(agents.md)      │                 │ 推送配置（直连，
│    · 代码仓/模型(modelRef)/环境变量(envVars)            │                 │ 不经编排层）
│    · agentRuntime / configKey                          │                 │ POST /harness/sync
│    · 持有 sessionKey ↔ sessionId / 调度增删会话          │                 │
│                                                        │                 │
└──────────────────────────┬─────────────────────────────┘                 │
                          │                                                 │
      POST /sessions      │   GET /sessions/{id}/chat                       │
      GET /sessions/{id}  │   POST /sessions/{id}/abort                     │
      DELETE /sessions/{id}│   ← 事件回调(created/completed/failed/          │
                          │      aborted/timeout/heartbeat)                 │
                          ▼                                                 │
┌────────────────────────────────────────────────────────┐                 │
│                Agent Core（运行时层）        ◄───────────┼─────────────────┘
│                                                         │
│  harness 配置本地缓存（按 harnessRef）                   │
│  session 生命周期管理                                    │
│                                                         │
│  ┌─ 沙箱（编排层不可见）──────────────────────────────┐  │
│  │  代码 Agent（claude-code / ...）                   │  │
│  │  装配的 skill / MCP   clone 的代码仓   workspace 卷 │  │
│  └────────────────────────────────────────────────────┘  │
│                                                         │
│  对话隔离 / 文件隔离 / 进程隔离 / 网络隔离                 │
│  网络 egress 管控 / 工具 allowlist / 凭证注入与回收        │
│  事件流（SSE）+ 回调上报                                 │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## P0 —— 不具备则无法交付

### R1 初始化会话

提供 session 生命周期接口与环境准备：

- `POST /sessions` 创建会话。入参：`sessionKey`、`harnessRef`（可选，整体兜底）、`skillSnapshotRefs`、`mcpSnapshotRefs`、`knowledgeBaseRefs`（可选）、`contextRef`（可选）、`modelRef`（可选）、`repo`（可选）、`envVars`（可选，环境变量）、`agentRuntime`、`configKey`（可选）。返回 `sessionId` + `chatUrl`。幂等。
- `GET /sessions/{id}` 查询状态，返回 `status`（ACTIVE/COMPLETED/FAILED）和 `lastActiveAt`。
- 事件回调通道，推送 session 生命周期事件：`session.created`、`session.completed`、`session.failed`（任务失败）、`session.aborted`（运行时异常：沙箱崩溃/OOM）、`session.timeout`（执行超时）、`session.heartbeat`。其中 `failed` 是任务本身失败，`aborted`/`timeout` 是运行环境异常（编排层据此区分可重试场景）。
- `DELETE /sessions/{id}` 销毁 session，幂等。

配置来源二选一：传 `harnessRef` 直接命中已同步的 harness 配置（整体兜底，无需逐项拉取，harness 同步见 R2）；或传具体 skill/MCP/知识库/上下文字段自行指定。两者同传时以具体字段为准、不混用。`harnessRef` 未命中（同步延迟或丢失）时回源拉取或返回可识别错误。

session 初始化时，按入参装配 skill 和 MCP server、注入短期凭证；按 `contextRef` 拉取项目知识说明（如 agents.md）注入对话上下文；按 `knowledgeBaseRefs` 挂载知识库；按 `repo` clone/checkout 代码仓（clone 时机自定）。不同 session 的环境相互独立。

初始化性能按任务类型自动分级（隔离强度看是否运行不可信代码），编排层不感知：

| 场景 | 入参特征 | 启动要求 | 沙箱建议 |
|---|---|---|---|
| 普通对话 | 无 repo、无外部或仅可信工具 | 百毫秒级 | 可不用沙箱（或 WASM 等轻量运行时）|
| 中等 | 无 repo、有 contextRef 和较多 skill/MCP | 秒级 | Firecracker microVM（外部 MCP/知识库潜在不可信，需强隔离）|
| 完整 | 有 repo、需 clone 代码仓 | 十秒级 | 容器沙箱（项目自有代码相对可信，IO/命令性能更好）|

### R2 harness 配置同步

提供 `POST /harness/sync`：接收 Agent-Management 在 harness 发布时推送的全套配置，本地按 `harnessRef` 缓存，供 session 初始化以 `harnessRef` 整体兜底命中，免去逐项拉取。

同步内容（一个 harness 版本的完整配置）：

| 内容 | 说明 |
|---|---|
| `harnessRef` | 版本引用，缓存键，不可变 |
| `tenantId` | 租户标识，隔离与配额归属 |
| `agentRuntime` | 执行器类型（如 claude-code）|
| skill 快照 | 每项含 skill 标识、版本、内容引用（脚本/工具定义）|
| MCP server 配置 | 每项含 server 名、启动方式（命令/镜像/URL）、传输协议、凭证引用、工具 allowlist |
| 知识库 | 每项含标识、形态（文件挂载/检索服务）、内容引用或检索端点、挂载路径 |
| 项目上下文 | agents.md 等，项目背景、编码规范、约定 |
| 模型路由 | 默认模型、可选模型 allowlist、端点与凭证引用、预算/限流护栏 |
| 工具策略 | 工具/命令/路径 allowlist、危险操作规则 |
| 网络策略 | egress allowlist（模型端点/MCP/repo 源/知识库服务）|
| 默认环境变量 | harness 级默认 envVars（session 入参可覆盖）|
| 资源画像 | CPU/内存/磁盘配额、沙箱类型倾向 |

> 凭证不同步明文，只同步引用；运行时由 Agent Core 经凭证服务换取短期令牌。

规则：

- Agent-Management 直接推送、不经编排层；
- harness 发布新版本时推送（事件驱动），非 session 初始化时拉取；
- harness 版本不可变，已同步配置可长期缓存，新版本生成新 `harnessRef`；
- session 初始化传 `harnessRef` 未命中时，回源拉取或返回可识别错误。

### R3 对话

提供 `GET /sessions/{id}/chat` 对话接口。建连参数：`prompt`、`resumeFromSessionRef`（可选）。按 `agentRuntime` 选择执行器启动代码 Agent，经 SSE 流式返回所有执行事件（thinking / message / tool_use / tool_result / stdout / stderr / session.completed / session.failed）。`session.completed` payload 含 summary、result、artifactRefs、commitSha、sessionRef。

提供 `POST /sessions/{id}/abort` 中止当前正在执行的对话（不销毁 session）：停止本轮 agent 执行，保留对话上下文，session 仍可续聊。用于 agent 陷入死循环、或用户主动打断。幂等。

提供 `GET /sessions/{id}/changes` 查看本次改动：不带参数返回改动文件列表（路径、变更类型、增删行数 + baseCommit/headCommit）；带 `path` 返回该文件的 unified diff。基于 workspace 内 git 工作区计算，供前端展示 diff、用户审查。超大/二进制文件按策略截断或标记。

**单会话串行**：同一 session 同时只允许一个活跃对话连接。已有对话在执行时，新建连请求被拒绝或排队（断连重连场景下，旧连接需先释放）。避免同一对话上下文被并发写坏。

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

### R4 安全管控

- 默认最小化出网，仅放行 allowlist（模型端点、已批准 MCP server、repo 源、知识库服务），禁止任意外联；
- 可执行的工具、命令、文件路径受策略约束，不开放任意脚本；
- Agent 只能访问本 session 的 workspace 路径及显式挂载的只读路径，越界拒绝；
- 命中高风险操作（修改鉴权、删除数据、变更 CI、访问生产、越权）时阻断并上报。

### R5 支持多种代码 Agent 运行时

支持多种 `agentRuntime`（当前需支持 `claude-code`），按 `modelRef` 选择本次模型。不同运行时的装配方式、prompt 格式、事件协议由 Agent Core 内部适配，对外 SSE 事件流保持统一协议。

### R6 资源管控

- 每个 session 可施加资源配额上限（CPU/内存/磁盘/时长），超限按策略处理；
- 资源池不足时 session 创建快速拒绝，不长时间阻塞；
- session 空闲时冻结沙箱释放算力、保留文件，需要时解冻恢复；
- 相同 `configKey` 的 session 缓存已就绪的环境模板，后续跳过环境准备快速复用。

---

## P2 —— 长期增强

### R7 效率与可观测

- 相邻 session 相同 MCP 快照时复用已启动的 MCP server 进程；
- 执行中上报审查规则命中及规模信号（工具调用数、耗时、改动范围）；
- session 空闲时沙箱自动流转（存活→冻结→回收），逐级释放资源；
- 上报 token 消耗、执行耗时、资源占用等指标；
- 执行环境镜像受版本与来源管控，可复现、可审计。
