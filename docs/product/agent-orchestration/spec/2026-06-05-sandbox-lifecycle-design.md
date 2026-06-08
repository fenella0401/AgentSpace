# 沙箱生命周期设计

> 文档定位：在 Agent-Orchestration / Agent Core 架构下，单独阐明**沙箱（Sandbox）的设计思想**——它是什么、何时常驻、何时销毁、step 与 session 如何映射、skill 与 MCP 如何加载。本文是对《orchestrator-agentflow-workflow-template-design》§8（Agent Core 接口）与 §8.8（对话上下文持久化）的细化与收口，不改变既有的 run/step/attempt 状态机与 StartAttempt 契约。
>
> 适用范围：Agent Core 的运行时实现约定。Agent-Orchestration 不感知沙箱实现细节，本文中标注「编排层视角」的部分才是契约面，其余为 Agent Core 内部实现指引。

## 1. 核心设计思想：沙箱按 run 常驻，step 是沙箱里的 session

一句话：**一个 run 一个沙箱，沙箱常驻整个 run 生命周期；每个 step 是这个沙箱里的一个 session；所有 step 共享同一块文件工作区。**

这是三层解耦后的自然结论，三层各管一件事、生命周期各不相同：

| 层 | 是什么 | 生命周期 | 共享范围 |
|---|---|---|---|
| **Sandbox（沙箱）** | 承载执行的隔离环境（容器/进程组 + 挂载的 workspace 卷） | **run 级**：run 启动时创建，run 终态时销毁 | 整个 run 共享一个 |
| **Session（对话）** | 一次 Agent 对话上下文，键 = `sessionRef`，落在沙箱内 workspace 路径 | **step 级**：每个 step 一个 session，可跨 attempt 续聊 | 同 step 的多个 attempt 共享 |
| **Attempt（执行轮次）** | 一轮执行 = 一次 `StartAttempt`；session 里的一个回合 | **attempt 级**：进程可死可重启 | 不共享，每轮独立 |

```text
Sandbox（run 级常驻，文件共享）
  ├─ Session A（step1）  ── Attempt #1
  ├─ Session B（step2）  ── Attempt #1 → Attempt #2（续聊）
  └─ Session C（step3）  ── Attempt #1
       ▲
       └ 三个 session 共用同一块 workspace 文件区；step1 改的文件 step2/step3 直接可见
```

### 为什么这样设计

- **文件天然共享，无需跨沙箱传递。** 所有 step 在同一沙箱、同一块本地盘上跑，上游 step 改的文件下游直接可见，不需要 git commit 接力，也不需要把产物推到对象存储再拉回。这避免了"名义共享卷、实则各写各的临时盘"导致的**改动丢失**问题。
- **冷启动只付一次。** 沙箱 boot（clone repo、装配环境）只在 run 开头发生一次（冷启 P95 < 60s），后续每个 step 都落在已就绪的热沙箱里，省去重复 provision。
- **与既有对话模型兼容。** §8.8 的"进程可死、靠 `--resume` 重载对话上下文"原样适用——进程死的是**沙箱里的执行进程**，不是沙箱本身。沙箱（连同盘上的对话上下文和文件）还在，resume 从盘上把对话捞回来即可。

## 2. 沙箱何时常驻、何时销毁

### 2.1 创建

- **触发时机**：run 内**第一个 step** 启动其首个 attempt 时。
- **find-or-create 键**：按 **run（workspaceRef）** 寻址。同一个 run 的所有后续 step 命中同一个沙箱；首个 step 未命中则创建。
- **创建动作**：分配运行环境 → 挂载 run 级 workspace 卷 → 按 `RepoRef` clone/checkout 代码仓 → 注入短期凭证。这就是冷启动的 60s 来源。

> 编排层视角：编排层不显式"创建沙箱"。它只发 `StartAttempt`（带 `workspaceRef`），由 Agent Core 按 workspaceRef 内部 find-or-create。编排层契约不变、不新增 `sandboxRef` 入参。

### 2.2 常驻（贯穿整个 run）

沙箱在 run 执行期间持续存在，跨越多个 step：

- step 之间切换：上一个 step 的 session 结束，下一个 step 在**同一沙箱**里开新 session，文件区不变。
- **step 挂起（`requiresConfirmation`）期间**：沙箱保持存活（可休眠以省资源），等待用户确认或续聊。这是 run 级常驻沙箱要承担的主要成本——等人确认可能持续数小时，期间沙箱占用资源。MVP 阶段可接受；后续可对挂起态沙箱做冻结/休眠优化。
- **进程退出 ≠ 沙箱销毁**：某个 attempt 的执行进程跑完退出后，沙箱与文件区、对话上下文都还在。续聊时在同一沙箱内 `--resume` 重载（详见 §4）。

### 2.3 销毁

沙箱在 run 进入**终态**时销毁：

| run 终态 | 沙箱处理 |
|---|---|
| `COMPLETED` | 销毁沙箱；workspace 卷与产物按数据策略保留/导出/清理 |
| `FAILED` | 销毁沙箱；workspace 与对话上下文清理时机归 Agent-Management 的 workspace 生命周期管理（见原 spec §12 未决项 6） |
| `CANCELLED` | 销毁沙箱；对话上下文是否保留由取消请求指明，默认保留以便后续恢复 |

- 销毁时回收运行时资源（容器、进程、临时凭证），**释放 workspace lease**。
- 编排层只释放 lease 引用，**不删卷**——卷的物理清理归 Agent-Management。

### 2.4 异常与超时

- **沙箱意外死亡**（节点故障/OOM）：当前 in-flight attempt 失败，由编排层按重试策略处理。重试创建新 attempt 时，Agent Core 重新 find-or-create 沙箱并 `--resume` 恢复对话；文件状态依赖 workspace 卷的持久性（见 §5）。
- **run 级硬超时 / watchdog**：编排层判超时后取消 run，触发 §2.3 的销毁路径。

## 3. step 与 session 的映射

- **一个 step = 一个 session**：step 启动首个 attempt 时，在沙箱内新建 session，分配 `sessionRef`；attempt 结束时 Agent Core 回报该 `sessionRef`，编排层持久化。
- **session 复用 = 续聊**：同一 step 的后续 attempt（续聊/重试）携带 `resumeFromSessionRef = 该 sessionRef`，在原对话上下文上接着跑。
- **session 之间文件共享、上下文不共享**：step2 的 session 看不到 step1 对话里的消息，但能看到 step1 在文件区留下的改动。step 间的**结构化结果**传递仍走 `StepOutput`（summary/result/artifactRefs），prompt 里用 `{{steps.xxx.result}}` 引用——这条与文件共享是两条独立通道，互补。

## 4. 进程生命周期与对话恢复（沿用 §8.8，在常驻沙箱内）

run 级常驻沙箱与"进程可死 + resume 恢复"**不冲突**，二者是不同维度：沙箱管文件共享，进程/对话管对话续接。组合后：

```text
step suspended（attempt #1 已产出结果）
   │ 用户带反馈点「继续对话」
   ▼
编排层创建 StepAttempt #2（带 resumeFromSessionRef）
   │
   ├─ 进程仍存活（热）：直接在原进程续聊，低延迟
   └─ 进程已退出（冷）：在【同一沙箱内】起新进程，--resume <sessionRef> 重载上下文后续聊
   ▼
attempt #2 succeeded ──► step 回到 suspended，等待下一次确认或续聊
```

要点：

- 对话上下文是独立于执行进程的持久资源，落在沙箱内 workspace 约定路径，进程退出不影响它。
- 进程默认可退出；恢复靠对话上下文持久化 + `--resume` 重载。
- 与原 §8.8 的唯一差别：重载发生在**同一个 run 级常驻沙箱内**，而非重新创建沙箱——文件区和对话上下文本就都在，恢复更轻。
- 编排层对进程冷热**无感知**，续聊一律按"创建新 attempt"处理；冷热只影响延迟，不影响语义。

## 5. workspace 与文件持久性

- workspace 是 **run 级**资源，全 run 共享一份 `workspaceRef / mountPath / leaseId`。
- 代码仓由 Agent Core 在沙箱创建时 clone/checkout 进 workspace；run 内后续 step 不重复 clone，文件已就位。
- **持久性要求**：workspace 必须是沙箱内可跨进程持久的文件区——某个 attempt 进程退出后，其文件改动对同沙箱内后续 step / 续聊 attempt 仍可见。这是 run 级单沙箱"文件共享"成立的前提。
- 并发：MVP 串行单飞调度下，同一时刻 run 内只有一个 step 的一个 attempt 在写文件区，无并发写冲突；续聊也按 conversation 串行。未来支持并行 DAG 时需引入文件区并发控制（原 spec §12 未决项）。

## 6. Skill 与 MCP 的加载

沙箱按 step 装配能力。因为每个 `AgentFlowStep` 自带 `AgentSpec`（可有不同的 agent、`skillSnapshotRefs`、`mcpSnapshotRefs`），**同一个常驻沙箱要在不同 step（session）启动时按需装配对应能力**。

### 6.1 加载时机与方式

`StartAttempt` 请求已携带本 step 所需的能力引用：`agentSnapshotRef`、`skillSnapshotRefs`、`mcpSnapshotRefs`、`knowledgeBaseRefs`。Agent Core 在该 step 的 session 启动时：

| 能力 | 引用字段 | 装配方式 |
|---|---|---|
| Agent 配置 | `agentSnapshotRef` | 按引用拉取快照，作为本 session 的 agent 配置 |
| Skill | `skillSnapshotRefs` | 按引用拉取 skill 内容，装载进本 session 的可用技能集 |
| MCP Server | `mcpSnapshotRefs` | 按引用拉取 MCP 配置，在沙箱内启动/注册 MCP server，注入对应 `mcpCredentialRefs` 的短期凭证 |
| 知识库（文件形态） | `knowledgeBaseRefs` | 以只读方式挂载到 workspace 内约定路径 |
| 知识库（检索服务形态） | `knowledgeBaseRefs` | 以检索服务（MCP 风格）注册，Agent 通过工具调用查询 |

引用的具体内容由 Agent Core 按引用拉取并组装，**Agent Core 不需要理解引用背后的业务来源**（这些来自 Agent-Management 上传的快照）。

### 6.2 常驻沙箱下的装配策略

run 级单沙箱意味着不同 step 的 skill/MCP 可能不同，沙箱内能力是**动态的**，不能假设一个沙箱终生只伺候一套配置。建议：

- **按 session 切换能力集**：每个 step 的 session 启动时，依据本 step 的 `skillSnapshotRefs / mcpSnapshotRefs` 装配；session 结束后相应能力可卸载或保留。
- **MCP server 复用**：若相邻 step 引用相同 MCP 快照，Agent Core 可复用已启动的 MCP server 进程，避免反复启停；以快照引用作为复用键。
- **凭证随 step 注入、用后回收**：MCP/模型/知识库凭证按 step 的 credential refs 换取短期凭证，遵循最小权限，session 结束后回收，不在沙箱内长期驻留明文。
- **装配失败处理**：skill/MCP/知识库装配失败导致 attempt 失败，按 step 重试策略处理；编排层收到的是 attempt 失败信号，不感知具体装配细节。

### 6.3 边界

- 编排层只**透传引用**，不解析 skill/MCP 内容，也不感知沙箱内如何装配/卸载/复用。
- 装配的具体格式、MCP 启动机制、知识库挂载路径由 Agent Core 与执行器实现，对编排层透明。

## 7. 与既有文档的关系

- 本文**收口**了原 spec 中悬空的"沙箱生命周期"——§8.2 的"冷启 60s"、"`RESOURCE_EXHAUSTED`"、§8.8 的"热窗口/进程可死"此前都预设了一个沙箱生命周期却未命名；本文明确为 **run 级常驻**。
- 本文**不改变** run/step/attempt 状态机、`StartAttempt` 契约（仍为 `workspaceRef + resumeFromSessionRef`，不新增 `sandboxRef` 入参）、StepOutput 传递机制、调度策略（串行单飞）。
- §8.8 的对话恢复机制原样适用，差别仅在于恢复发生在同一 run 级常驻沙箱内。

## 8. 待确认 / 后续

| # | 议题 | 说明 |
|---|---|---|
| 1 | 挂起态沙箱的资源优化 | `requiresConfirmation` 长时间挂起期间沙箱占资源；是否引入休眠/冻结，何时触发，唤醒延迟 |
| 2 | 可观测：sandboxRef 回报 | 建议 Agent Core 在事件中回报实际使用的 `sandboxRef`（与 `sessionRef` 平行），编排层仅存做排障用，不作为入参 |
| 3 | 并行 DAG 下的文件区并发 | 未来放开并行后，同沙箱多 step 并发写 workspace 的隔离/加锁策略 |
| 4 | MCP server 复用的失效与回收 | 跨 step 复用 MCP server 时的健康检查、配置变更失效、run 结束统一回收 |
