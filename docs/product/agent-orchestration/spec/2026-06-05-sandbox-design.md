# 沙箱设计：生命周期、工作流与聊天场景

> 文档定位：在 Agent-Orchestration / Agent Core 架构下，阐明 **Orchestration 层如何管理 session**，以及 **Agent Core 在 session 内如何管理沙箱**——工作流和聊天两种场景共用同一套 session 模型，差异仅在代码仓是否可选。
>
> 设计目标：① **规模化与资源效率**——大规模并发下资源用得合理；② **用户体验流畅**——响应快、对话连续、过程透明；③ **安全与隔离**——租户/任务的隔离与安全边界。
>
> **核心设计**：Orchestration **不感知沙箱**，只感知 session。session 初始化时传 MCP 引用、skill 引用、代码仓(可选)、凭证给 Agent Core；Agent Core 在 session 生命周期内**自行管理沙箱**——何时起、何时冻、何时回收，编排层完全不介入。

## 0. 总览

**编排层只看到 session。** 初始化时传入执行所需的全量配置（agent / skill / MCP 引用、代码仓、凭证），Agent Core 返回 `sessionId`，之后所有 attempt 只带 `sessionId` 和 `attemptNo`。

沙箱——何时起、何时冻、什么形态——是 Agent Core 内部的事，编排层完全透明。

session 初始化时唯一可变的是代码仓：绑了项目则传 `RepoRef`，不绑则不传。其余字段始终一致。工作流和聊天共用这套模型，差别仅在于代码仓是必传还是可选（见 §2、§3）。

## 1. 核心模型

### 1.1 编排层的视角：Session + Attempt

Orchestration 只看到两层，沙箱完全不暴露：

| 层 | 是什么 | 生命周期 | Orchestration 管什么 |
|---|---|---|---|
| **Session** | Agent 的持久执行上下文 | step 级（工作流）/ 会话级（聊天） | 创建/配置/结束，持有 sessionId 映射 |
| **Attempt** | 一次执行 = `startAttempt(sessionId, attemptNo)` | 一轮 | 发起/重试/续聊/取消 |

**沙箱是 Agent Core 内部的事。** 编排层不需要知道沙箱何时起、何时冻、几个实例、什么形态。编排层只对 session 说话。

### 1.2 Session 初始化

Orchestration 调用 Agent Core 初始化 session，传入执行所需的全量上下文：

| 入参 | 工作流 | 聊天 |
|---|---|---|
| agent 快照引用 | 本 step 的 `agentSnapshotRef` | 会话关联的 agent 配置 |
| skill 引用列表 | 本 step 的 `skillSnapshotRefs` | 会话关联的 skill |
| MCP 引用列表 | 本 step 的 `mcpSnapshotRefs` | 会话关联的 MCP |
| 代码仓 | 必传 `RepoRef`(repoUrl / branch / commit) | 可选：绑项目则传，不绑则不传 |
| 凭证引用 | `CredentialRefs` | 同左 |
| workspace 卷引用 | `workspaceRef / mountPath` | 同左 |

Agent Core 返回 `sessionId`。后续发 attempt 只带 `sessionId` 和 `attemptNo`，不再重复传配置。代码仓 clone 时机由 Agent Core 自行决定（可初始化时克隆，也可惰性到第一个 attempt）。

> 编排层视角：`POST /sessions` 初始化，得 `sessionId`；`POST /sessions/{id}/attempts` 发 attempt。

### 1.3 进程可死 + 对话恢复

"进程"指 Agent Core 在沙箱里跑 Agent 那一轮的执行进程（跑 LLM、调工具、读写文件的那个），不是沙箱容器本身。它是三层里最短命的：跑完一个 attempt 就能退，沙箱和盘上的对话上下文照样在。

- 对话上下文是独立于执行进程的持久资源，进程退出不丢；
- 恢复靠对话上下文持久化 + `--resume` 重载；
- 编排层对进程冷热**无感知**，续聊一律按"创建新 attempt"处理；冷热只影响延迟，不影响语义。

### 1.4 跨 Session 的文件与产物传递

不同 session 之间不共享 Agent Core 的本地盘。文件和产物的传递走两种方式：

- **代码仓**：上游 session 的 Agent 改动由 Agent Core commit/push，下游 session 初始化时按最新状态 checkout——改动传递由代码仓保证，不依赖共享卷；
- **StepOutput**（仅工作流）：结构化结果（summary/result/artifactRefs）经编排层 prompt 渲染传递（`{{steps.xxx.result}}`），与代码仓通道互补。

---

## 2. 工作流场景

### 2.1 核心思想

**一个 step = 一个 session。run 内的多个 step 是多个独立 session，文件通过代码仓 + StepOutput 交接。**

```text
run
  ├─ step1 → Session A（初始化时传代码仓 + mcp/skill） → attempt → commit
  ├─ step2 → Session B（初始化时传同一代码仓 + mcp/skill） → checkout（拿到 step1 的改动） → attempt → commit
  └─ step3 → Session C（同上）
```

不同 step 的 session 互不共享本地盘，但通过代码仓形成连续的改动链。每个 step 的 agent/mcp/skill 配置独立，在 session 初始化时分别传入。

### 2.2 Session 生命周期

- **创建**：step 就绪（入度依赖满足）时，Orchestration 初始化 session（传入本 step 的配置 + 代码仓 + 凭证）；
- **执行**：通过 attempt 完成（首次 attempt 或续聊 / 重试）；
- **挂起**：`requiresConfirmation` 满足后，step 挂起等用户确认，session 保留，编排层等待；
- **结束**：step 终态时 session 结束，编排层通知 Agent Core 销毁 session，Agent Core 自行回收沙箱资源；
- **失败**：step 失败后若需重试（未超 maxRetries），在**同一 session** 内发起新 attempt；若耗尽重试，session 结束。

### 2.3 与原有 run/step/attempt 模型的关系

编写层不改变既有的状态机——run/step/attempt 状态流转、串行单飞调度、reconcile/watchdog 等均保持不变，唯一新增的是 session 这一层映射：编排层在执行 step 时，负责初始化 session 并持有 `stepId ↔ sessionId` 映射，之后 attempt 以 sessionId 寻址。

---

## 3. 聊天场景

### 3.1 核心思想

**一个 conversation = 一个 session。** 同一个聊天窗口的所有轮次，共享同一个 session。聊天和工作流用的是**同一套 session 模型**，差别仅在于：

- 代码仓**可选**（绑定了项目就传，不绑则不传）；
- session 的创建是**惰性**的——纯问答和搜索检索不创建 session，只在需要沙箱内工具时才创建。

### 3.2 session 的惰性创建

Agent Core 收到聊天消息后：

- 如果本轮仅需 LLM 推理 + 沙箱外工具（搜索/检索），**不创建 session**，直接流式返回；
- 一旦本轮需要沙箱内工具（读写文件/执行命令）→ Orchestration 初始化 session（传 MCP/skill/可选代码仓），之后在该 session 内发 attempt 执行。

这个判断由 Agent Core 在执行中自然触发，无需编排层预判。

session 创建后的状态管理（存活/冻结/回收）全部由 Agent Core 内部负责，编排层不介入。

### 3.3 何时把对话转成任务

转任务 = 从当前聊天 session 派生一个**新的独立 session**（带代码仓，进编排层的正式任务流程）。判据是**要不要治理**（审查 + 正式追踪），不是"活大不大"——后台执行、可恢复、隔离这些能力 session 本身就有。

转任务的信号来自三处：

| 触发源 | 信号 | 为什么需要转任务 |
|---|---|---|
| **① 命中审查规则**（最硬，不可绕过）| 危险/高风险操作：改鉴权、删数据、动 CI、越权路径 | 必须有人工审查 gate |
| **② 需正式追踪/交付** | 需作为工单留档、进审计链 | 快路径只是"一次对话"，不是可追踪的任务 |
| **③ 用户显式要求**（最高优先级）| 用户说"建个任务跑"或界面主动标 | 用户意图优先 |

- 转任务是**提议**，用户可拒绝（唯独 ① 审查规则不可覆盖）；
- "活大"（调用多/耗时长/改动多）本身不触发转任务，至多提示用户。

### 3.4 转成任务时的上下文交接

从聊天 session 派生新任务 session 时：

- **对话摘要注入**：当前会话的关键意图、约束、已达成结论摘要后作为新 session 首轮 prompt 上下文；
- **文件状态交接**：若聊天 session 已改动 repo，先 commit，新 session 初始化时传该 commit，Agent Core 按此 checkout；
- **双向链接**：会话记录新 session 的 sessionId/runId，事件回流到会话展示。

---

## 4. 两场景对照

| 设计目标 | 工作流场景 | 聊天场景 |
|---|---|---|
| **规模化与资源效率** | session 按需创建、step 完成即销毁；空闲 session 由 Agent Core 自行冻结回收 | 惰性创建 session（纯问答不建）；Agent Core 内部逐级回收沙箱资源 |
| **用户体验流畅** | 状态机/重试/审查保障可靠交付 | 秒级响应、对话连续；session 透明流转 |
| **安全与隔离** | session 间互隔离；代码仓 + StepOutput 交接；审查 gate 内建 | 同上；无 repo session 进一步收紧权限 |

---

## 5. 能力要求

本节按两层分列：**编排层（Agent-Orchestration）自己的事**，和**Agent Core 需要提供的能力**。

### 5.1 编排层（Agent-Orchestration）职责

**核心（P0）：**

| 能力 | 说明 |
|---|---|
| session 生命周期管理 | 创建 session（传入 mcp/skill/repo/凭证 配置）、销毁 session |
| session 映射持有 | stepId/conversationId ↔ sessionId 映射，负责复用判定 |
| attempt 调度 | 基于 sessionId 发起/重试/续聊/取消 attempt |
| 转任务决策 | 据 Agent Core 上报的治理信号，判定是否从聊天派生新任务 session |

**必要（P1）：**

| 能力 | 说明 |
|---|---|
| 审查 gate 流程 | `requiresConfirmation` 状态机：暂停→人工审批→推进/驳回 |
| StepOutput 渲染 | 完成 step 的结构化输出注入下游 step 的 prompt |
| session 配额与并发 | 按用户/项目限制活跃 session 数量 |

### 5.2 Agent Core 接口定义

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
- 需要 → Agent Core 内部起沙箱执行，或通知编排层初始化 session（视聊天接入方式而定，见 §7#5）。

**Request：**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversationId` | string | 是 | 聊天会话标识 |
| `message` | string | 是 | 用户本轮输入 |
| `projectContext` | object | 否 | 项目上下文（含 repo/agent/skill/mcp 引用），用于需要时初始化 session |

**Response**：SSE 流式返回（thinking/message/tool_*），与事件协议一致。

---

#### （A）session 内执行能力（Agent Core 内部，编排层不感知）

| 能力 | 优先级 | 说明 |
|---|---|---|
| 沙箱创建与复用 | P0 | session 内部管理沙箱，编排层不感知 |
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

> **排期前提**：当前里程碑 MVP 仅做工作流场景（聊天页延后）。工作流仅依赖接口一~五 + (A) 节 P0/P1 内部能力。接口六（聊天）及 (A) 节 P2 待排期时整体提级，沙箱冻结/解冻因工作流需要已在 P1。

### 5.3 编排层（Agent-Orchestration）边界

- **工作流场景**：全程经编排层；session 初始化后，attempt 发 Agent Core；
- **聊天快路径**：直连 Agent Core 会话接口，不经编排层；
- **聊天转任务后**：新建 session 后回到编排层标准流程。

---

## 6. 与既有文档的关系

- 本文**修订**了原 spec 中悬空的"沙箱生命周期"——此前沙箱是 Orchestration 感知的显式概念（sandboxId 入参、编排层持映射、编排层控冻结）；本文改为 **Orchestration 只感知 session，沙箱是 Agent Core 内部领域**；
- 不改变 run/step/attempt 状态机、调度策略（串行单飞）；
- §8.8 对话恢复机制原样适用。

---

## 7. 待确认 / 后续

| # | 议题 | 说明 |
|---|---|---|
| 1 | session 初始化的 API 契约 | 独立 `POST /sessions` 还是合入 `StartAttempt`（首次 attempt 时附带配置）；若独立，session 创建与首个 attempt 之间是否可能无 attempt（纯环境准备） |
| 2 | Agent Core 沙箱冻结/解冻 | 编排层不感知；Agent Core 自行决定阈值、实现形态 |
| 3 | 转任务信号与阈值 | 审查规则集；规模信号阈值（调用数/耗时/改动数到多少提示用户）|
| 4 | session 配额与并发 | 按用户/项目限制活跃 session 数量 |
| 5 | 聊天快路径 API 边界 | 直连 Agent Core 接口定义，与编排层职责切分 |
| 6 | 跨 session 代码仓并发 | 同 repo 多 session 并行修改时的隔离/加锁策略 |
| 7 | MCP server 复用 | 跨 session 复用时的健康检查与失效回收 |