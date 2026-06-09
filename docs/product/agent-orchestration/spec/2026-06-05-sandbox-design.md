# Agent Core 接口与能力要求

> 文档定位：定义 Agent Core 暴露给外部的接口契约，以及内部需要具备的执行与沙箱能力。本文不涉及编排层（Agent-Orchestration）的职责——编排层如何调度 session、如何判定转任务等，由编排层文档自行定义。
>
> 核心约定：Agent Core 暴露 **session** 作为执行单元。外部初始化 session（传入 agent/skill/MCP 引用、代码仓、凭证），得到 `sessionId`；后续所有 attempt 以 `sessionId` + `attemptNo` 寻址。**沙箱在 Agent Core 内部管理，外部不可见。**

## 0. 总览

Agent Core 提供以下接口：

| 接口 | 说明 |
|---|---|
| `POST /sessions` | 初始化 session，传入全量配置，返回 `sessionId` |
| `POST /sessions/{id}/attempts` | 在 session 内发起一轮执行 |
| `GET /sessions/{id}/attempts/{id}` | 查询 attempt 运行状态 |
| 事件回调 → Orchestration | Agent 执行事件统一上报 |
| `DELETE /sessions/{id}` | 销毁 session，回收沙箱 |
| `POST /chat`（P2） | 聊天会话接口，不经编排层 |

session 初始化时，代码仓（`RepoRef`）为可选字段——传则 Agent Core 按需 clone,不传则为无 repo 的 session。

## 1. 核心模型

### 1.1 Session 与 Attempt

Agent Core 暴露两层执行原语：

| 层 | 说明 |
|---|---|
| **Session** | 持久执行上下文。初始化时传入全量配置（agent/skill/MCP 引用、代码仓、凭证、workspace 卷），Agent Core 在此上下文中管理沙箱与对话。 |
| **Attempt** | session 内的一轮执行。同一 session 可多次 attempt（续聊、重试），对话上下文通过 `sessionRef` 在 attempt 间衔接。 |

沙箱不暴露——何时起、何时冻、何时回收，由 Agent Core 内部自行决定。

### 1.2 Session 初始化

外部调用 `POST /sessions`，传入执行所需的全量配置。详细字段见 §3 接口一。

Agent Core 返回 `sessionId`。后续 attempt 只带 `sessionId` 和 `attemptNo`，不再重复传配置。代码仓 clone 时机由 Agent Core 自行决定（可初始化时克隆，也可惰性到第一个 attempt）。

### 1.3 进程可死 + 对话恢复

"进程"指 Agent Core 在沙箱里跑 Agent 那一轮的执行进程（跑 LLM、调工具、读写文件的那个）。跑完一个 attempt 就能退，沙箱和盘上的对话上下文照样在。

- 对话上下文是独立于执行进程的持久资源，进程退出不丢；
- 恢复靠对话上下文持久化 + `--resume` 重载；
- 外部对进程冷热**无感知**，续聊一律按"创建新 attempt"处理。

### 1.4 跨 Session 的文件与产物传递

不同 session 之间不共享本地盘。文件传递走代码仓：上游 session 的改动由 Agent Core commit/push，下游 session 初始化时按最新状态 checkout——改动传递由代码仓保证，不依赖共享卷。结构化结果（summary/result/artifactRefs）经事件回报，由外部在 prompt 渲染时注入下游。

---

## 2. session 的使用方式

外部将 session 作为执行单元使用——创建时传入当前任务所需的配置(agent/skill/MCP 引用、代码仓、凭证),得 `sessionId` 后通过 attempt 驱动执行。多 step 或多轮对话通过多个 session 串联,文件传递靠代码仓 commit + checkout。

代码仓为可选字段:需要代码操作的 session 传 `RepoRef`,纯问答或轻量操作的 session 不传。

---

## 3. Agent Core 接口定义

> 以下接口按优先级分三档：**核心（P0）**= MVP 第一天必须具备；**必要（P1）**= 上规模/安全合规所需；**非必要（P2）**= 可后置。聊天类归 P2（聊天页延后）。

---

#### 接口一：POST /sessions（初始化 session）

优先级：**P0**

Orchestration 初始化 session，传入执行所需的全量上下文。Agent Core 准备环境（装配 MCP/skill、按需 clone repo），返回 `sessionId`。编排层持有 sessionId 用于后续 attempt。

**Request：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionKey` | string | 是 | Orchestration 侧的 session 标识（如 `stepId` 或 `conversationId`），供编排层映射 |
| `agentSnapshotRef` | string | 是 | Agent 配置快照引用 |
| `skillSnapshotRefs` | string[] | 否 | Skill 快照引用列表 |
| `mcpSnapshotRefs` | string[] | 否 | MCP Server 快照引用列表 |
| `knowledgeBaseRefs` | string[] | 否 | 知识库引用列表 |
| `repo` | RepoRef | 否 | 代码仓引用（repoUrl / branch / commit）。工作流必传；聊天可选 |
| `workspace` | WorkspaceRef | 是 | workspace 卷引用（workspaceRef / mountPath / leaseId） |
| `credentials` | CredentialRefs | 是 | 凭证引用 |
| `executorType` | string | 是 | 执行器类型 |

**Response：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | string | Agent Core 分配的 session 标识，后续 attempt 以此寻址 |
| `status` | enum | `READY` / `PREPARING`。`READY` 表示环境已就绪可直接发 attempt；`PREPARING` 表示仍在准备中 |

**幂等**：同一 `sessionKey` 重复调用返回已有 `sessionId`。

---

#### 接口二：POST /sessions/{sessionId}/attempts（发起 attempt）

优先级：**P0**

在一个已初始化的 session 内启动一轮执行。同一接口承载「新建对话」与「续聊」——`resumeFromSessionRef` 为空表示新建，非空表示在已有对话上续聊。

**Request：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `attemptId` | string | 是 | Orchestration 分配的 attempt 标识，用于幂等 |
| `attemptNo` | int | 是 | attempt 序号（1-based） |
| `renderedPrompt` | string | 是 | 已渲染的 prompt 文本 |
| `resumeFromSessionRef` | string | 否 | 续聊时指向要恢复的对话上下文（sessionRef）；为空表示新建对话 |

**Response：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `runtimeAttemptRef` | string | Agent Core 内部分配的运行时标识，仅做排障用 |
| `initialStatus` | enum | `STARTING` |

**幂等**：同一 `attemptId` 重复调用返回已有 RuntimeAttempt 状态。

---

#### 接口三：GET /sessions/{sessionId}/attempts/{attemptId}（查询 attempt 状态）

优先级：**P0**

供编排层轮询判活、配合超时与 watchdog 回收（编排层 pull 驱动，Agent Core 无需主动推 heartbeat）。

**Response：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `status` | enum | `STARTING` / `RUNNING` / `COMPLETED` / `FAILED` / `CANCELLED` |
| `exitCode` | int | 进程退出码（终态时有值） |
| `failureReason` | string | 失败原因（终态时有值） |
| `lastHeartbeatAt` | datetime | 最近活跃时间，供编排层判活 |
| `sessionRef` | string | 本次 attempt 产出的对话上下文标识，供续聊作为 `resumeFromSessionRef` |
| `result` | object | 执行结果（终态时有值），见下方 |

**result 字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `summary` | string | 必填，给前端展示 |
| `result` | string | 可选，完整文本结果 |
| `artifactRefs` | string[] | 可选，指向对象存储中的产物 |
| `commitSha` | string | 若本次执行有代码改动，返回 commit 的 sha（供下游 session 的 `RepoRef.commit` 使用）|

---

#### 接口四：Agent Core → Orchestration 事件回调

优先级：**P0**

Agent Core 在执行过程中，按统一协议 `POST /internal/agent-core/events` 推送事件给编排层。编排层是事件的事实消费方。

**事件类型：**

| eventType | 优先级 | 说明 |
|---|---|---|
| `runtime.created` | P0 | RuntimeAttempt 已创建 |
| `runtime.running` | P0 | 进入运行态 |
| `runtime.completed` | P0 | 执行成功终态 |
| `runtime.failed` | P0 | 执行失败终态 |
| `runtime.cancelled` | P0 | 被取消 |
| `heartbeat` | P0 | 定期心跳 |
| `thinking` | P0 | Agent 思考过程 |
| `message` | P0 | Agent 回复消息 |
| `tool_use` | P0 | 工具调用请求 |
| `tool_result` | P0 | 工具调用结果 |
| `stdout` / `stderr` | P0 | 标准输出/错误 |
| `attempt.result` | P0 | attempt 最终结果（含 sessionRef / commitSha / artifactRefs） |
| `governance.signal` | P2 | 审查规则命中 + 规模信号（工具调用数/耗时/改动范围等），供编排层判定转任务或提示 |

**事件基座字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `eventId` | string | 事件唯一标识，供去重 |
| `eventType` | string | 见上表 |
| `sessionId` | string | 归属 session |
| `attemptId` | string | 归属 attempt |
| `sequence` | long | 递增序号，供排序与断线重连 |
| `timestamp` | datetime | 事件产生时间 |
| `payload` | object | 具体事件内容，按 eventType 不同 |

---

#### 接口五：DELETE /sessions/{sessionId}（销毁 session）

优先级：**P0**

编排层在 step/会话结束时通知 Agent Core 销毁 session。Agent Core 自行回收沙箱、释放 lease、清理临时凭证。

**幂等**：session 已不存在时返回可识别状态，不报错。

---

#### 接口六：POST /chat（聊天会话接口，NONE 态——无 session）

优先级：**P2**

聊天页直连 Agent Core 的接口，不经编排层。Agent Core 自行判断本轮是否需要沙箱内工具：

- 不需要 → 直接流式返回 LLM 输出（不创建 session，不建沙箱）；
- 需要 → Agent Core 内部起沙箱执行，或通知编排层初始化 session（视聊天接入方式而定，见 §6#5）。

**Request：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversationId` | string | 是 | 聊天会话标识 |
| `message` | string | 是 | 用户本轮输入 |
| `projectContext` | object | 否 | 项目上下文（含 repo/agent/skill/mcp 引用），用于需要时初始化 session |

**Response**：SSE 流式返回（thinking/message/tool_*），与事件协议一致。

---

---

## 4. 内部能力要求

以下为 Agent Core 内部需具备的能力（非对外接口），按优先级分档。

| 能力 | 优先级 | 说明 |
|---|---|---|
| 沙箱创建与复用 | P0 | session 内部管理沙箱 |
| 租户 / 任务隔离 | P0 | 不同 session 的沙箱相互隔离 |
| 凭证隔离与短期化 | P0 | 短期令牌、最小权限、不持久化明文 |
| 文件系统边界 | P0 | Agent 只允许访问策略允许的路径 |
| 按需装配 skill/MCP/知识库 | P0 | 按 session 初始化传入的引用拉取装配 |
| 网络策略（egress 管控） | P1 | 默认最小出网，仅 allowlist |
| 工具 / 命令 allowlist | P1 | 工具、命令、路径受策略约束 |
| 危险操作拦截 | P1 | 命中高风险操作时阻断或上报 |
| 资源限额 | P1 | 沙箱可施加资源配额 |
| 容量与背压 | P1 | 容量不足时 attempt 快速拒绝 |
| 沙箱冻结 / 解冻 | P1 | session 空闲时冻结释放算力，需要时解冻 |
| 无沙箱对话（仅聊天） | P2 | 纯 LLM + 搜索检索不建沙箱，流式返回 |
| 四态自动流转（仅聊天） | P2 | NONE→WARM→FROZEN→RECLAIMED 自动流转 |
| 资源指标可观测 | P2 | token/成本/耗时/资源占用上报 |
| 镜像与运行时治理 | P2 | 执行环境可治理、可复现、可审计 |

> **排期前提**：MVP 仅做工作流场景（聊天页延后）。工作流仅依赖 P0 + P1。聊天类 P2 待排期时整体提级。沙箱冻结/解冻因工作流也需要已在 P1。

---

## 5. 与既有文档的关系

- 本文定义 Agent Core 的接口契约与内部能力；编排层文档据此调用
- 不改变 run/step/attempt 状态机
- 编排层自身职责（调度策略、session 映射、审查流程、转任务判定）由编排层文档自行定义

---

## 6. 待确认 / 后续

| # | 议题 | 说明 |
|---|---|---|
| 1 | session 初始化的 API 契约 | 独立 `POST /sessions` 还是合入 `StartAttempt`（首次 attempt 时附带配置）；若独立，session 创建与首个 attempt 之间是否可能无 attempt（纯环境准备） |
| 2 | Agent Core 沙箱冻结/解冻 | 编排层不感知；Agent Core 自行决定阈值、实现形态 |
| 3 | 转任务信号与阈值 | 审查规则集；规模信号阈值（调用数/耗时/改动数到多少提示用户）|
| 4 | session 配额与并发 | 按用户/项目限制活跃 session 数量 |
| 5 | 聊天快路径 API 边界 | 直连 Agent Core 接口定义，与编排层职责切分 |
| 6 | 跨 session 代码仓并发 | 同 repo 多 session 并行修改时的隔离/加锁策略 |
| 7 | MCP server 复用 | 跨 session 复用时的健康检查与失效回收 |