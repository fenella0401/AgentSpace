# Agent Core 接口与能力要求

> 文档定位：定义 Agent Core 暴露给外部的接口契约，以及内部需要具备的执行与沙箱能力。本文不涉及编排层（Agent-Orchestration）的职责——编排层如何调度 session、如何判定转任务等，由编排层文档自行定义。
>
> 核心约定：Agent Core 暴露 **session** 作为执行单元。外部初始化 session（传入 skill/MCP 引用列表、代码仓），得到 `sessionId`。对话通过 **SSE 连接**进行——建连即开始一轮对话，流式返回 Agent 执行事件；断连后重连同一 SSE 地址即可续聊。**沙箱在 Agent Core 内部管理，外部不可见。**

## 0. 总览

| 接口 | 说明 |
|---|---|
| `POST /sessions` | 初始化 session，传入全量配置，返回 `sessionId` |
| `GET /sessions/{id}/sse` | SSE 对话连接——建连即开始/续接对话，流式返回执行事件 |
| `GET /sessions/{id}` | 查询 session 状态 |
| 事件回调 → Orchestration | 执行过程中的生命周期事件主动推送 |
| `DELETE /sessions/{id}` | 销毁 session，回收沙箱 |

session 初始化时，代码仓（`RepoRef`）为可选字段——传则 Agent Core 按需 clone，不传则为无 repo 的 session。

---

## 1. 核心模型

### 1.1 Session

Agent Core 的执行单元。初始化时传入 skill/MCP 引用列表、代码仓（可选）和项目知识说明（可选，如 agents.md），workspace 卷与凭证由 Agent Core 内部自行管理。agent 概念只存在于编排层——编排层按 agent 组装好 skill 和 MCP 再传给 Agent Core。

对话通过 **SSE 连接**驱动：外部建连到 `GET /sessions/{id}/sse`，即可开始一轮对话或续接已有对话。SSE 流式返回 Agent 执行过程中的所有事件（thinking/message/tool_*/std*/终态结果）。断连后重连同一 SSE 地址，传入续聊参数即可恢复。

session 不感知 attempt——对话本身就是执行，不需要在 session 下再嵌套一轮的概念。

### 1.2 进程可死 + 对话恢复

"进程"指 Agent Core 在沙箱里跑 Agent 的执行进程（跑 LLM、调工具、读写文件的那个）。进程可以退出，沙箱和盘上的对话上下文照样在。

- 对话上下文是独立于执行进程的持久资源，进程退出不丢；
- 外部断连 SSE 后，重连时带 `resumeFromSessionRef` 即可继续对话；
- 外部对进程冷热**无感知**。

### 1.3 跨 Session 的文件传递

不同 session 不共享本地盘。文件传递走代码仓：上游 session 的改动由 Agent Core commit/push，下游 session 初始化时按最新状态 checkout。结构化结果（summary/result/artifactRefs）经事件回报，由外部在后续 session 的 prompt 中引用。

---

## 2. Session 的使用方式

外部将 session 作为执行单元——创建时传入当前任务所需的配置（skill/MCP 引用列表、代码仓），得 `sessionId` 后通过 SSE 连接进行对话。多个 step 或多轮对话通过多个 session 串联，文件传递靠代码仓 commit + checkout。

代码仓为可选字段：需要代码操作的 session 传 `RepoRef`，纯问答或轻量操作的 session 不传。

---

## 3. Agent Core 接口定义

> 以下接口按优先级分三档：**P0**= MVP 第一天必须具备；**P1**= 上规模/安全合规所需；**P2**= 可后置。聊天类归 P2（聊天页延后）。

---

### 接口一：POST /sessions（初始化 session）

优先级：**P0**

初始化 session，传入执行所需的全量上下文。Agent Core 准备环境（装配 MCP/skill、按需 clone repo），返回 `sessionId`。

**Request：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionKey` | string | 是 | 外部侧的 session 标识，供外部映射 |
| `skillSnapshotRefs` | string[] | 是 | Skill 快照引用列表（编排层已按 agent 组装好） |
| `mcpSnapshotRefs` | string[] | 是 | MCP Server 快照引用列表（编排层已按 agent 组装好） |
| `knowledgeBaseRefs` | string[] | 否 | 知识库引用列表 |
| `contextRef` | string | 否 | 项目空间知识说明引用（如 agents.md，包含项目背景、编码规范、约定等；Agent Core 将其注入对话上下文） |
| `repo` | RepoRef | 否 | 代码仓引用（repoUrl / branch / commit） |
| `executorType` | string | 是 | 执行器类型 |

**Response：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | string | Agent Core 分配的 session 标识 |
| `sseUrl` | string | SSE 对话连接地址 |

**幂等**：同一 `sessionKey` 重复调用返回已有 `sessionId`。

---

### 接口二：GET /sessions/{sessionId}/sse（SSE 对话）

优先级：**P0**

Agent Core 的**核心执行通道**。外部通过 SSE 长连接驱动对话：

- **新建对话**：session 首次 SSE 建连时，在首个 SSE 事件中发送本次的 prompt；Agent Core 开始执行，流式返回所有 Agent 事件；
- **续聊**：断连后重连同一 SSE 地址，在首个事件中带 `resumeFromSessionRef` + 新 prompt，Agent Core 恢复对话上下文后继续。

**SSE 建连参数（query / 首条事件）：**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `prompt` | string | 是 | 已渲染的 prompt 文本 |
| `resumeFromSessionRef` | string | 否 | 续聊时指向要恢复的对话上下文；为空表示新建对话 |

**SSE 事件流（Agent Core → 外部）：**

| eventType | 说明 |
|---|---|
| `thinking` | Agent 思考过程 |
| `message` | Agent 回复消息 |
| `tool_use` | 工具调用请求 |
| `tool_result` | 工具调用结果 |
| `stdout` / `stderr` | 标准输出/错误 |
| `session.completed` | 本轮对话执行成功终态（含 summary / result / artifactRefs / commitSha / sessionRef） |
| `session.failed` | 本轮对话执行失败终态（含 failureReason） |
| `governance.signal`（P2） | 审查规则命中 + 规模信号 |

**SSE 事件基座字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `eventId` | string | 事件唯一标识，供去重 |
| `eventType` | string | 见上表 |
| `sessionId` | string | 归属 session |
| `sequence` | long | 递增序号，供排序与断线重连 |
| `timestamp` | datetime | 事件产生时间 |
| `payload` | object | 具体事件内容，按 eventType 不同 |

**终态事件 payload（session.completed）：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `summary` | string | 必填，给前端展示 |
| `result` | string | 可选，完整文本结果 |
| `artifactRefs` | string[] | 可选，指向对象存储中的产物 |
| `commitSha` | string | 若本次有代码改动，返回 commit 的 sha |
| `sessionRef` | string | 本轮对话上下文标识，供续聊时作为 `resumeFromSessionRef` |

---

### 接口三：GET /sessions/{sessionId}（查询 session 状态）

优先级：**P0**

供外部轮询判活（外部 pull 驱动，Agent Core 无需主动推心跳）。

**Response：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | string | session 标识 |
| `status` | enum | `ACTIVE` / `COMPLETED` / `FAILED` |
| `lastActiveAt` | datetime | 最近活跃时间，供外部判活 |

---

### 接口四：Agent Core → 外部 事件回调

优先级：**P0**

SSE 之外的补充通道——Agent Core 在执行过程中，按统一协议 `POST /internal/agent-core/events` 主动推送生命周期事件给外部。**展示类事件（thinking/message/tool_*）走 SSE 流，生命周期事件（session 开始/异常等）走回调。**

**回调事件类型：**

| eventType | 说明 |
|---|---|
| `session.created` | session 已创建 |
| `session.failed` | session 执行失败 |
| `session.heartbeat` | 定期心跳（外部兜底判活） |

**回调事件基座字段（同 SSE 事件基座）。**

---

### 接口五：DELETE /sessions/{sessionId}（销毁 session）

优先级：**P0**

外部在 session 不再需要时通知 Agent Core 销毁。Agent Core 自行回收沙箱、释放 lease、清理临时凭证。

**幂等**：session 已不存在时返回可识别状态，不报错。

---

## 4. Agent Core 需具备的能力

以下为 Agent Core 内部必须满足的能力要求（非接口参数，而是 Agent Core 在实现接口时需保证的行为和约束），按优先级分档：**P0**= MVP 第一天必须具备，**P1**= 上规模/安全合规所需，**P2**= 可后置。

---

### 4.1 会话与沙箱隔离

| 能力 | 优先级 | 说明 |
|---|---|---|
| 对话隔离 | P0 | 不同 session 的对话上下文互不可见，session 内多轮对话共享上下文。Agent 不能跨 session 读取其他对话的消息历史 |
| 文件隔离 | P0 | 不同 session 的文件操作相互隔离。session 间不共享本地盘，文件和产物传递只能通过代码仓 commit/checkout 或外部传入的引用 |
| 进程隔离 | P0 | 不同 session 的执行进程互不可见，进程命名空间隔离 |
| 网络隔离 | P0 | 不同 session 的网络命名空间隔离；同一个沙箱内的多个对话轮次共享网络环境 |
| 沙箱生命周期管理 | P0 | session 创建时按需创建沙箱，session 销毁时回收沙箱及所有关联资源。沙箱的创建、冻结、解冻、回收均由 Agent Core 内部决定，外部不可见 |
| 凭证隔离 | P0 | 每个 session 使用的凭证（模型 API key、git credential、MCP token 等）作用域限定本 session，session 结束后回收。不在日志、事件、SSE 流中回显凭证明文 |

### 4.2 安全与权限管控

| 能力 | 优先级 | 说明 |
|---|---|---|
| 网络 egress 管控 | P1 | 默认最小化出网，仅放行 allowlist 内的目的地（模型端点、已批准 MCP server、repo 源、知识库服务）。禁止任意外联 |
| 工具 / 命令 allowlist | P1 | 可执行的工具、命令、文件路径受策略约束。不开放任意脚本执行、任意文件系统访问 |
| 文件系统边界 | P0 | Agent 只能访问本 session 的 workspace 路径及显式挂载的只读路径（如知识库文件）。越界读写被拒绝 |
| 危险操作拦截 | P1 | 命中高风险操作（修改鉴权配置、删除数据、变更 CI 流水线、访问生产环境、越权操作）时阻断执行并上报。阻断信号通过事件回报给外部 |

### 4.3 能力装配

| 能力 | 优先级 | 说明 |
|---|---|---|
| Skill 装配 | P0 | 按 session 初始化传入的 `skillSnapshotRefs` 拉取 skill 内容，装载进当前 session 的可用技能集 |
| MCP Server 装配 | P0 | 按 session 初始化传入的 `mcpSnapshotRefs` 拉取配置，启动或注册 MCP server，注入短期凭证 |
| 知识库挂载 | P0 | 按 `knowledgeBaseRefs` 以只读方式挂载文件形态知识库，或注册检索服务形态知识库 |
| 上下文注入 | P0 | 按 `contextRef` 拉取项目空间知识说明（如 agents.md），注入本 session 的对话上下文，让 agent 了解项目背景、编码规范、约定 |
| 能力集动态切换 | P0 | 不同 session 的能力集相互独立，按各自初始化参数装配，互不干扰 |
| MCP server 复用 | P2 | 相邻 session 引用相同 MCP 快照时可复用已启动的 MCP server 进程 |

### 4.4 资源管控

| 能力 | 优先级 | 说明 |
|---|---|---|
| 资源配额 | P1 | 每个 session 可施加 CPU、内存、磁盘、执行时长的配额上限。超限时按策略降级或终止，避免单 session 拖垮节点 |
| 容量背压 | P1 | 资源池不足时，session 创建快速拒绝（以可识别状态返回），不长时间阻塞 |
| 沙箱冻结 / 解冻 | P1 | session 空闲时冻结沙箱以释放算力、保留文件，后续需要时解冻恢复。工作流与聊天共用同一套机制 |
| 镜像治理 | P2 | 执行环境所用的镜像受版本与来源管控，保证可复现、可审计 |

### 4.5 可观测

| 能力 | 优先级 | 说明 |
|---|---|---|
| 执行事件上报 | P0 | SSE 流内完整上报 thinking/message/tool_use/tool_result/stdout/stderr/终态结果；事件回调通道上报 session 生命周期事件 |
| 状态查询 | P0 | `GET /sessions/{id}` 返回 session 当前状态及最近活跃时间，供外部轮询判活 |
| 资源指标 | P2 | 上报 token 消耗、执行耗时、资源占用等指标，供成本归属与告警 |

### 4.6 对话与执行

| 能力 | 优先级 | 说明 |
|---|---|---|
| 进程可死 + 对话恢复 | P0 | 对话上下文持久化落盘（键 = sessionRef），执行进程退出后上下文不丢。续聊时 `--resume` 重载即可恢复 |
| 代码仓操作 | P0 | 按 session 初始化传入的 `RepoRef` clone/checkout 代码仓；clone 时机由 Agent Core 自定；对话过程中 agent 的代码改动经 Agent Core commit/push |
| 无 repo 对话 | P2 | session 不传代码仓时，Agent Core 可直接做 LLM 推理 + 搜索检索，不进沙箱，经 SSE 流式返回 |
| 沙箱内/外工具分流 | P2 | Agent Core 自行判断本轮是否需沙箱内工具（读写文件/执行命令），仅沙箱内工具才触发沙箱创建；沙箱外工具（搜索/检索）可在无沙箱状态下直接调用 |
| 四态空闲流转 | P2 | 无代码仓 session 的沙箱按 NONE→WARM→FROZEN→RECLAIMED 自动流转，逐级回收资源 |

> **排期前提**：MVP 仅做工作流场景（聊天页延后）。工作流仅依赖所有 P0 + 安全/资源类 P1。聊天类 P2 待排期时整体提级。沙箱冻结/解冻因工作流需要已在 P1。

---

## 5. 与既有文档的关系

- 本文定义 Agent Core 的接口契约与内部能力；编排层文档据此调用
- 编排层自身职责（调度策略、session 映射、审查流程、转任务判定）由编排层文档自行定义

---

## 6. 待确认 / 后续

| # | 议题 | 说明 |
|---|---|---|
| 1 | SSE 续聊的参数传递方式 | `resumeFromSessionRef` 是通过 SSE 建连时的 query 参数传，还是建连后首条事件传 |
| 2 | 终态事件复用 | `session.completed` 在 SSE 和事件回调两通道都出现——两者是否需要完全一致，还是回调仅做通知 |
| 3 | Agent Core 沙箱冻结/解冻 | Agent Core 自行决定阈值、实现形态 |
| 4 | 聊天场景 session 创建时机 | 无代码仓的 session 是惰性创建（先 SSE 纯 LLM 对话，需要沙箱内工具时才创建 session）还是先建 session 再 SSE |
| 5 | 跨 session 代码仓并发 | 同 repo 多 session 并行修改时的隔离/加锁策略 |
| 6 | session 配额与并发 | 按用户/项目限制活跃 session 数量 |
| 7 | MCP server 复用 | 跨 session 复用时的健康检查与失效回收 |