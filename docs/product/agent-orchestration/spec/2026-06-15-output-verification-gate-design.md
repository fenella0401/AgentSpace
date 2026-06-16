# 产出验收设计（Output Verification）

> 文档定位：定义 Agent 执行产出的验收机制。分两层——**会话内自检**（agent 自带 hook，沙箱内，best-effort）与**编排层任务级准出 gate**（任务执行完成后，权威，决定任务能否准出）。两层职责正交，互不依赖。
>
> 关联文档：沙箱设计（Agent Core 接口）、AgentFlow 与 Workflow 模板设计（run/step/attempt 模型）、Agent-Orchestration 实现规范（状态机、DDL）、通用任务模块设计（Task/Conversation）。

## 0. 背景与目标

Agent 执行结束后，产出（代码改动 / StepOutput / artifact）不一定符合要求。需要一套机制在任务**准出**（对用户呈现"完成"、合并改动、进入下游消费）之前做检查。

**核心边界判断**：需求是「执行**结束之后**检查产出」。按既有架构，"结束之后"是编排层领域——Agent Core 只负责运行时执行，业务/验收语义归编排层（见沙箱设计 §0、§7）。agent 自带 hook 跑在沙箱内、发生在会话"正要结束的一刻"，本质是会话内行为，承担不了"结束之后"的权威验收，且与具体 `agentRuntime` 强耦合（claude-code 的 hook 机制 opencode/codex 未必有）。

**验收时机为任务级（准出关卡），而非 step 级。** 一个任务（AgentFlow 整条流程 / Agent 整段对话）真正产生价值的是它的**最终整体产出**，不是中间每个 step 的局部产出。把 gate 放在任务收尾处：

- 语义对齐"准出"——这是任务交付前的最后一道关，过了就算合格交付；
- 避免碎片化拦截——很多检查（全量测试、整体一致性、跨 step 的最终 diff）本来就要等所有改动到齐才有意义；
- 中间 step 的局部质量由 step 自己的 `requiresConfirmation`（人工确认）和 Tier 1 会话内自检兜底，不需要 gate 逐 step 拦。

因此采用两层设计，定位严格区分：

| 层 | 位置 | 时机 | 粒度 | 定位 | runtime 耦合 |
|---|---|---|---|---|---|
| **会话内自检** | 沙箱内 | 每次会话结束的一刻 | 会话级 | best-effort 预过滤，让 agent 自纠 | 强（依赖具体 runtime hook）|
| **任务级准出 gate** | 编排层 | 任务执行完成后 | 任务级 | 权威验收，决定任务能否准出 | 无（对所有 runtime 一致）|

一句话：**hook 让 agent 自己别交垃圾，gate 替系统判任务能不能交付。**

## 1. 设计决策（待校准）

下列取值为 MVP 默认，标注供产品校准：

| # | 决策点 | 取值 | 说明 |
|---|---|---|---|
| D1 | gate 范围 | AgentFlow + Agent 两种任务都做任务级准出，**最大化复用**同一套验收引擎 | 二者共用 verifier 形态、裁决逻辑、状态机、数据表，差异只在触发点与配置载体（见 §4.1、§8）|
| D2 | 验收器形态 | 三种全做：确定性脚本（script）+ LLM-judge（llm）+ 规则 policy | 统一 Verifier 接口，可任意组合 |
| D3 | 不通过动作 | **可配置**：`SUSPEND`（转人工）/ `AUTO_RETRY`（自动续聊 N 次后转人工）/ `FAIL` | 由 `VerificationSpec.onFail` 指定，默认 `SUSPEND` |
| D4 | 验收器执行环境 | 复用任务最终产出所在的 workspace（最终 commit 已在）| 新建独立轻量 session 为备选 |
| D5 | LLM-judge 是否独立 step | 作为 gate 内部调用（不占 DAG 节点），与 AgentFlow 里手写 verify step 并存 | 强制建模为 DAG step 为备选 |

## 2. 整体架构

```text
┌─ Agent Core（沙箱内）────────────────────────────────┐
│  代码 Agent 执行（每个 step / 每轮对话）              │
│    │                                                 │
│    │ 会话结束的一刻                                    │
│    ▼                                                 │
│  ┌─ Tier 1: 会话内自检（agent 自带 hook）──────────┐  │
│  │  Claude Code Stop / PostToolUse hook            │  │
│  │  跑 lint / typecheck / 单测 / 格式 / TODO 扫描   │  │
│  │  不通过 → exit 2 → stderr 反馈 → agent --resume  │  │
│  │  自纠（同上下文，不新建沙箱）                     │  │
│  │  通过/无 hook → 正常结束，上报 session.completed │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────┬─────────────────────────────┘
                         │ 任务全部 step / 对话完成
                         │ + 最终 StepOutput / commitSha / changes
                         ▼
┌─ Agent-Orchestration（编排层）───────────────────────┐
│  所有 step COMPLETED（AgentFlow）                    │
│   / 对话任务收尾（Agent 模式）                        │
│    │                                                 │
│    ▼                                                 │
│  ┌─ Tier 2: 任务级准出 gate（权威）──────────────┐  │
│  │  按 task.verification 配置执行验收器：          │  │
│  │   · 确定性脚本（在最终 workspace 跑 test/lint）  │  │
│  │   · LLM-judge（评审任务最终产出 / 总 diff）      │  │
│  │   · 规则 policy（改动范围 / 文件白名单）         │  │
│  │  汇总裁决 → PASS / FAIL / NEEDS_REVIEW          │  │
│  └──────────────────┬─────────────────────────────┘  │
│                     ▼                                 │
│   PASS        → 任务 COMPLETED（准出）              │
│   FAIL        → 按 onFail 配置（D3）：               │
│                 SUSPEND 转人工 / AUTO_RETRY 续聊 / FAIL│
│   NEEDS_REVIEW→ 任务 SUSPENDED（人工确认）           │
└──────────────────────────────────────────────────────┘
```

两层完全解耦：Tier 1 缺失（runtime 不支持 hook）不影响 Tier 2 正确性，只是少了一次廉价预过滤；Tier 2 不依赖 Tier 1 的任何信号。

## 3. Tier 1：会话内自检（agent 自带 hook）

### 3.1 定位

best-effort 的会话内预过滤，目标是让 agent 在交付前自己修掉**确定性、可机检**的低级问题，省掉一次昂贵的编排层往返（gate FAIL → 新 attempt 续聊需要重启/恢复沙箱）。它**不是验收依据**——即便 Tier 1 全过，Tier 2 仍独立验收。

### 3.2 实现（以 claude-code 为例）

通过 harness 配置下发 Claude Code 的 hook（见 §6 harness 衔接）。关键 hook：

| hook | 触发时机 | 用途 |
|---|---|---|
| `Stop` | agent 认为本轮结束、即将停止 | 跑确定性检查；不通过则阻止结束 |
| `PostToolUse` | 每次工具调用（如写文件）后 | 即时反馈（如格式化、单文件 lint），可选 |

`Stop` hook 约定：
- **退出码 0**：检查通过，放行结束；
- **退出码 2**：检查不通过，hook 的 stderr 内容回灌给 agent，agent 在**同一对话上下文**继续修复（`--resume` 语义，不新建沙箱），修完再次触发 `Stop`；
- 其他非零退出码：按 runtime 约定处理（一般记录但不阻断，避免 hook 自身故障卡死会话）。

检查内容（由 harness/项目配置，典型）：lint、typecheck、单元测试、代码格式、残留 TODO/FIXME、调试语句残留。

### 3.3 约束

- **runtime 适配**：claude-code 用 `Stop`/`PostToolUse` hook；opencode/codex 若无等价机制则 Tier 1 退化为无（不报错、不阻断）；
- **防死循环**：hook 自纠最多触发 K 次（runtime 内置或 harness 配置，默认 3），超过则放任结束，交给 Tier 2；
- **不上报为独立事件**：Tier 1 的自检过程是会话内行为，体现在正常的 thinking/tool_use/message 事件流里，编排层不感知、不为其建模。

## 4. Tier 2：编排层任务级准出 gate（权威）

### 4.1 触发点

在**任务执行完成**、判定任务终态为 COMPLETED 之前，插入一道准出 gate。两种任务模式各有明确的"执行完成"时点：

| 任务模式 | 完成时点 | gate 触发 |
|---|---|---|
| **AgentFlow** | 所有 step 进入 COMPLETED（DAG 跑完）| run 即将 RUNNING → COMPLETED 时 |
| **Agent** | 对话任务收尾（用户结束 / 终端轮次完成）| task 即将 done 时 |

即修改现有 run 状态机（见实现规范 §3.3）的 `RUNNING → COMPLETED` 这条边——原来"所有 step COMPLETED"直接 run COMPLETED，现在先过任务级 gate。**中间 step 不再被 gate 拦**，step 局部质量由其自身 `requiresConfirmation` 与 Tier 1 兜底。

### 4.2 验收输入

gate 拿到的是任务**最终整体产出**快照：

| 输入 | 来源 | 说明 |
|---|---|---|
| 最终 `commitSha` | 任务最后一个 commit | 任务全部改动的最终状态 |
| 总 `changes` | `GET /sessions/{id}/changes`（基线=任务起始）| 任务起始到结束的全量改动文件列表 + diff |
| 各 step `StepOutput` | AgentFlow 各 step 输出 | summary / result / artifactRefs，供 LLM-judge 综合评估 |
| 终端 `sessionRef` | 最后活跃 step / 对话的 session_ref | 供续聊补救（若 D3 选自动续聊）|
| 任务上下文 | AgentFlow 快照 / task 元数据 | 任务目标、整体 prompt、验收标准 |

> 关键差异：step 级 gate 看的是单 step 局部 diff；任务级 gate 看的是**任务起始基线到最终 commit 的总 diff**，能验证跨 step 的整体一致性（如 step1 建的接口 step3 是否正确实现）。

### 4.3 验收器形态

> **验收器（Verifier）定义**：任务级 gate 的执行单元，由 gate 在**任务收尾时**调用，对**任务最终整体产出**（最终 commit / 总 diff / 各 step StepOutput）做一次判定，产出 PASS / FAIL / NEEDS_REVIEW。
>
> 区分两个维度，避免与会话级 Tier 1 混淆：
> - **判定对象**：恒为**任务级产出快照**（不是某一次会话的改动）；
> - **执行机制**：因类型而异——`script` / `llm` 为了执行检查这个动作，会向 Agent Core 起一个**验证专用的临时 session**（用完即弃）；`policy` 在编排层本地判定、不起 session。
>
> 关键：`script` / `llm` 起的「验证 session」是**验收器干活的手段**，与被验收的业务任务会话无关——它不消费对话历史，只在最终产出快照上跑检查。所以验收器是**任务级**的，哪怕它内部借用了 session 这个执行机制。

三种验收器，可组合（一个任务配多个，全部 PASS 才算通过）：

| 验收器 | 判定对象 | 执行机制 | 典型用途 |
|---|---|---|---|
| `script` | 任务最终 commit / workspace | 起验证专用临时 session 跑命令 | 全量测试、集成测试、构建、lint |
| `llm` | 任务总 diff + 各 step 输出 | 起验证专用评审 session | 产出是否满足任务目标的语义评审 |
| `policy` | 任务总 changes | 编排层本地判定，不起 session | 改动范围 / 规模 / 文件白黑名单 |

#### （A）确定性脚本验收器 `script`

在任务最终产出所在的 workspace（D4：最终 commit 已在）执行预定义命令，按退出码判定。

| 字段 | 类型 | 说明 |
|---|---|---|
| `command` | string | 执行命令，如 `npm test`、`./gradlew check` |
| `timeoutSec` | int | 超时，默认 600 |
| `passOn` | enum | `exit_zero`（默认）/ 自定义判定 |

- **执行机制**：编排层向 Agent Core 起一个验证专用的轻量 session（同 repo / 同最终 commit、无需 LLM），跑命令收集退出码 + stdout/stderr，跑完即弃。此 session 仅为执行检查，与被验任务的对话会话无关；
- 退出码 0 → PASS；非 0 → FAIL，stdout/stderr 作为反馈；
- 任务级最适合放**全量测试 / 集成测试 / 整体构建**——这类检查本就要等所有改动到齐。

#### （B）LLM-judge 验收器 `llm`

用一个评审 Agent 评估任务最终产出是否满足任务目标（D5：作为 gate 内部调用，不占 DAG 节点）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `judgeAgentRef` | string | 评审用 agent 快照引用 |
| `rubric` | string | 评审标准（prompt 模板，可引用任务目标与总 diff）|
| `passThreshold` | number | 通过阈值（如评分 ≥ 0.8）|

- **执行机制**：编排层渲染 rubric（注入任务目标 / 总 diff / 各 step StepOutput），向 Agent Core 起一个验证专用的评审 session，评审完即弃。评审 session 是新建的独立上下文，**不续接**被验任务的对话；
- 评审 Agent 返回结构化裁决 `{ verdict: pass/fail/needs_review, score, reasons[] }`；
- 注意：这与 AgentFlow 里手写一个 `verify` step（§7.4 范式）并存——手写 verify step 是流程显式节点（占 DAG、可人工确认），llm 验收器是任务收尾时 gate 内嵌的自动评审，二者按需选用。

#### （C）规则 policy 验收器 `policy`

确定性规则，**不起 session、不跑命令、不调 LLM**，编排层在任务总 changes 上本地判定，开销最低。

| 字段 | 类型 | 说明 |
|---|---|---|
| `fileAllowlist` | string[] | 允许改动的路径 glob，超出 → FAIL |
| `fileDenylist` | string[] | 禁止改动的路径（CI 配置、鉴权、生产配置等），命中 → FAIL |
| `maxFiles` | int | 改动文件数上限，超出 → NEEDS_REVIEW |
| `maxLines` | int | 增删行数上限，超出 → NEEDS_REVIEW |
| `requireCommit` | bool | 为 true 时声明应改代码却无 commit → FAIL |

规则在任务总 changes 上判定，命中即出对应裁决。MVP 实现上述基础规则集，规则项可按需扩展。

### 4.4 裁决与动作

各验收器结果汇总为任务级裁决（最严格者优先：任一 FAIL 则 FAIL；无 FAIL 但有 NEEDS_REVIEW 则 NEEDS_REVIEW；全 PASS 则 PASS）：

| 裁决 | 动作 | 状态机 |
|---|---|---|
| **PASS** | 准出 | 任务 → COMPLETED |
| **FAIL** | 按 `onFail` 配置（D3）| 见下表，三选一 |
| **NEEDS_REVIEW** | 转人工 | 任务 → SUSPENDED，人工 confirm（准出）/ continue（带反馈续聊）/ retry |

**FAIL 动作可配置（`VerificationSpec.onFail`）：**

| onFail | 行为 | 适用 |
|---|---|---|
| `SUSPEND`（默认）| 任务 → SUSPENDED，验收报告附在 suspend 信息里，人工决定从哪个 step 补救（confirm 强制准出 / 对终端 step 续聊或重试 / cancel）| 任务级问题常需人判断补救点，最稳妥 |
| `AUTO_RETRY` | 自动对终端 step 续聊（CONTINUE，feedback=验收报告，resume session）重试，最多 `maxRetries` 次；每次重试后复验；耗尽仍 FAIL → 转 SUSPENDED | 单 step / 单对话任务、失败大概率可由终端续聊自纠 |
| `FAIL` | 任务直接 → FAILED，不自动补救也不挂起 | 验收失败即视为任务失败、不接受人工放行的严格场景 |

gate 自身的执行结果落 `workflow_event`（新增 category 见 §5），可审计、可在前端展示验收报告。

> **onFail 选型提示**：任务级失败往往是跨 step 的整体问题（如 step2 的实现没满足 step1 的设计），重跑终端 step 不一定修得动，需要人判断从 DAG 哪一环补救——因此默认 `SUSPEND`。`AUTO_RETRY` 更适合产出问题集中在终端环节、续聊能自纠的任务（见 §11 待确认 #5）。

### 4.5 与 requiresConfirmation 的关系

二者层级不同、互补：
- `requiresConfirmation` 是 **step 级流程语义**——某个中间 step 无论产出如何都要人确认（如"验证结果"step 必须人工签字）；
- 任务级 gate 是**任务级质量语义**——任务整体产出是否合格、能否准出。

执行顺序：各 step 按自己的 `requiresConfirmation` 走完（该挂起确认的挂起确认）→ 所有 step COMPLETED → 任务级 gate → gate PASS 后任务才 COMPLETED。两者不冲突：step 确认管的是"流程要不要人盯某一环"，gate 管的是"最终交付合不合格"。

## 5. 数据模型

> **复用原则（D1）**：两种任务共用同一套验收引擎——`VerificationSpec` / `Verifier` 数据结构、裁决逻辑、`run_verification` 表、`VERIFYING` 状态、verifier 执行器全部共享。差异**只在配置从哪来**（§5.1）与**触发点**（§4.1、§8），不在验收逻辑本身。

### 5.1 验收配置（`VerificationSpec`）与两种载体

验收配置统一用 `VerificationSpec` 表达，挂在**任务级**。两种任务模式的差异仅是这份 spec 从哪里读：

| 任务模式 | 配置载体 | 来源 |
|---|---|---|
| **AgentFlow** | `AgentFlow.verification` | 模板主表 `workflow_templates.verification`，run 启动时随 AgentFlow 快照冻结 |
| **Agent** | `task.verification` | task 创建时指定，缺省继承**项目级**默认验收配置（见 §8）|

两者解析出的 `VerificationSpec` 进入完全相同的 gate 执行路径。

```java
// AgentFlow 任务：verification 挂在 flow 上（随快照冻结）
class AgentFlow {
    String flowId;
    String flowName;
    List<AgentFlowStep> steps;
    List<AgentFlowEdge> edges;
    // ... 既有字段
    VerificationSpec verification;   // 新增，任务级准出配置，可为 null（无验收）
}

// 共用的验收配置结构（两种任务模式一致）
class VerificationSpec {
    List<Verifier> verifiers;        // 验收器列表，全部 PASS 才准出
    FailAction onFail;               // FAIL 时动作：SUSPEND（默认）/ AUTO_RETRY / FAIL
    int maxRetries;                  // onFail=AUTO_RETRY 时的续聊重试上限，默认 0
}

class Verifier {
    VerifierType type;               // SCRIPT / LLM / POLICY
    // SCRIPT
    String command;
    Integer timeoutSec;
    // LLM
    String judgeAgentRef;
    String rubric;
    Double passThreshold;
    // POLICY
    String[] fileAllowlist;
    String[] fileDenylist;
    Integer maxFiles;
    Integer maxLines;
    Boolean requireCommit;
}

enum VerifierType { SCRIPT, LLM, POLICY }
enum FailAction { SUSPEND, AUTO_RETRY, FAIL }
```

> `verification` 为空 → 该任务不走准出 gate，行为与现状完全一致（向后兼容）。

### 5.2 新增表 `run_verification`

记录每次任务级 gate 执行的裁决，供审计与前端展示。一个任务一次准出验收（FAIL 后若自动重试再验则多条，按 `attempt_round` 区分）。AgentFlow 与 Agent 两种任务共用此表。

```sql
CREATE TABLE run_verification (
    id              VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL REFERENCES workflow_run(id),  -- Agent 任务亦有对应 run 标识
    task_id         VARCHAR(64),                        -- 冗余便于按 task 维度查询（两种模式都填）
    attempt_round   INTEGER     NOT NULL DEFAULT 1,    -- 第几次准出验收（含自动重试后复验）
    verdict         VARCHAR(16) NOT NULL,              -- PASS / FAIL / NEEDS_REVIEW
    verifier_results JSONB      NOT NULL,              -- [{type, verdict, score?, detail, durationMs}]
    final_commit    VARCHAR(64),                        -- 本次验收针对的最终 commit
    report          TEXT,                               -- 汇总报告（前端展示 / 续聊反馈来源）
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_runverif_run  ON run_verification (run_id, attempt_round);
CREATE INDEX idx_runverif_task ON run_verification (task_id);
```

### 5.3 `workflow_event` 扩展

`workflow_event.category` 现有 `control/display/runtime`，新增 `verification` 类，记录任务级 gate 生命周期事件：

| eventType | 说明 |
|---|---|
| `verification.started` | 准出 gate 开始执行 |
| `verification.verifier_result` | 单个 verifier 出结果 |
| `verification.completed` | gate 汇总裁决（PASS/FAIL/NEEDS_REVIEW）|

控制类（推进状态机）全量留存；验收报告正文较大，按展示类保留策略裁剪。

## 6. 状态机集成

验收 gate 接在 **run 状态机**（见实现规范 §3.3）的收尾处，在 `RUNNING → COMPLETED` 之间插入准出中间态 `VERIFYING`。**step 状态机不变**——所有 step 照常跑到 COMPLETED，gate 只在 run 层面拦最终准出。

| From | To | 触发 | 动作 |
|---|---|---|---|
| RUNNING | VERIFYING | 所有 step COMPLETED 且 AgentFlow 配了 `verification` | 启动任务级 gate（在最终 commit 上跑 verifiers）|
| RUNNING | COMPLETED | 所有 step COMPLETED 且**无** `verification` | 原逻辑不变 |
| VERIFYING | COMPLETED | gate PASS | 任务准出，finished_at |
| VERIFYING | SUSPENDED | gate NEEDS_REVIEW；或 gate FAIL 且 onFail=SUSPEND；或 FAIL 自动重试耗尽 | 持久化验收报告 + 终端 session_ref，等人工 |
| VERIFYING | RUNNING | gate FAIL 且 onFail=AUTO_RETRY 且 round < maxRetries | round++，对终端 step 起新 attempt（CONTINUE，feedback=验收报告，resume session）|
| VERIFYING | FAILED | gate FAIL 且 onFail=FAIL（或重试耗尽且配置为 FAIL）| 记 error |

- `VERIFYING` 纳入 watchdog（gate 自身可能超时/卡死，超时按 FAIL 处理）；
- run 处于 VERIFYING 时，对外仍呈现"运行中"语义（任务尚未准出）；
- 并发边界沿用 CAS + version：gate 完成回写 run 状态时若 version 不匹配（如已 CANCELLING），以 run 状态为准放弃；
- **SUSPENDED 复用既有 suspended 语义**：现有 run 因 step `requiresConfirmation` 也会 SUSPENDED，准出 gate 导致的 SUSPENDED 同样落在 run SUSPENDED，前端通过 suspend 来源（step 确认 vs 准出验收）区分展示。

```text
所有 step COMPLETED
   │
   ├─ 无 verification ──► run COMPLETED（原逻辑）
   │
   └─ 有 verification ──► run VERIFYING
                            │
                            ├─ PASS ──► run COMPLETED（准出）
                            ├─ NEEDS_REVIEW ──► run SUSPENDED（附验收报告，人工确认）
                            └─ FAIL ──┬─ onFail=SUSPEND ──► run SUSPENDED（人工决定补救）
                                      ├─ onFail=AUTO_RETRY ──► run RUNNING（终端 step 续聊）
                                      │                        耗尽 ──► SUSPENDED
                                      └─ onFail=FAIL ──► run FAILED
```

## 7. API 设计

### 7.1 查询验收结果（并入 run 查询）

`GET /runs/{runId}` 在 run 级增加 `verification` 字段（任务级准出结果）：

```json
{
  "run": {
    "runId": "run_abc", "status": "VERIFYING", "startedAt": "...",
    "verification": {
      "verdict": "FAIL",
      "attemptRound": 1,
      "finalCommit": "a1b2c3d",
      "verifierResults": [
        { "type": "SCRIPT", "verdict": "FAIL", "detail": "3 tests failed", "durationMs": 12400 },
        { "type": "LLM", "verdict": "PASS", "score": 0.86 }
      ],
      "report": "集成测试未通过：UserServiceIT.testRefresh ..."
    }
  },
  "steps": [ /* 各 step 照常，无 step 级 verification 字段 */ ]
}
```

### 7.2 实时事件流

`GET /runs/{runId}/events` 推送 `category=verification` 事件（started / verifier_result / completed），前端在任务收尾阶段实时渲染准出验收进度与报告。

### 7.3 人工动作复用现有接口

准出 gate 导致 run SUSPENDED 后，用户操作复用既有接口（实现规范 §2.3~2.5），无需新增。任务级 SUSPENDED 时，人工介入点落在 run 的终端 step 上：
- `confirm`（对终端 step）：强制准出，覆盖 FAIL/NEEDS_REVIEW → run COMPLETED；
- `continue`（对终端 step）：带验收报告反馈续聊，让 agent 改进后重新触发准出；
- `retry`（对某 FAILED step）：从指定 step 重跑；
- `cancel`：放弃任务。

> gate 不引入新的写接口——验收是编排层自动行为，人工介入点完全落在现有 suspended/failed 的 confirm/continue/retry/cancel 上。任务级失败时由人决定从 DAG 哪一环补救。

## 8. Agent 模式（通用任务）的准出验收

D1 决定 AgentFlow 与 Agent 两种任务都做任务级准出，并最大化复用同一套验收引擎。Agent 模式（见通用任务模块设计）一个 task 一个主 conversation、多轮对话，"执行完成"时点是**对话任务收尾**。除触发点与配置载体外，与 AgentFlow 完全共用 verifier、裁决、状态、数据表。

### 8.1 触发点

Agent 任务无 DAG，收尾判定与 AgentFlow 不同：

| 收尾条件 | 说明 |
|---|---|
| 用户主动结束任务 | 前端「完成任务」动作 → 触发准出 gate |
| 满足自动收尾条件 | 如连续 N 轮无新改动、或达到预设终止条件（项目策略，可选）|

收尾时对会话最终产出（最后 commit / 任务起始到结束的总 changes / 末轮 message）跑 gate。

### 8.2 配置载体

Agent 模式没有 AgentFlow，`VerificationSpec` 来源（见 §5.1）：

- **task 级**：task 创建时显式指定验收配置（如本次任务要跑哪些测试、用什么 rubric）；
- **项目级默认**：task 未指定时，继承项目配置的默认 `VerificationSpec`（如项目统一的准出脚本）；
- 都没有 → 不走 gate（向后兼容）。

> 需在通用任务模块 / 项目配置中新增 `verification` 载体字段。这是 Agent 模式相对 AgentFlow 唯一需要额外建设的部分。

### 8.3 裁决动作映射

复用 §4.4 的裁决与 `onFail` 配置，映射到 task 状态：

| 裁决 / onFail | task 动作 |
|---|---|
| PASS | task → done（准出）|
| FAIL + SUSPEND | task 标记待处理（验收报告呈现给用户），等人工 confirm / 续聊 / 放弃 |
| FAIL + AUTO_RETRY | 自动对主对话追加一轮带验收反馈的修复 prompt，重试 ≤ maxRetries 次，耗尽转待处理 |
| FAIL + FAIL | task → failed |
| NEEDS_REVIEW | task 标记待人工确认 |

### 8.4 与 AgentFlow 的复用对照

| 维度 | AgentFlow | Agent | 是否共用 |
|---|---|---|---|
| 验收配置结构 | `VerificationSpec` | `VerificationSpec` | ✓ 同一结构 |
| verifier 形态 | script / llm / policy | script / llm / policy | ✓ 同一实现 |
| 裁决逻辑 | 最严格优先 | 最严格优先 | ✓ |
| FAIL 动作 | onFail 三选一 | onFail 三选一 | ✓ |
| 验收结果存储 | `run_verification` | `run_verification` | ✓ 同一表 |
| 状态中间态 | run `VERIFYING` | task 收尾态映射到等价中间态 | ✓ 语义一致 |
| **触发点** | 所有 step COMPLETED | 对话收尾 | ✗ 各自判定 |
| **配置载体** | `AgentFlow.verification` | `task` / 项目级 | ✗ 各自来源 |

> 设计取舍：把"验收引擎"与"任务编排"解耦——引擎只认 `VerificationSpec` + 一份最终产出快照，不关心任务是 DAG 还是单对话。这样两种模式的差异收敛到两个接入点，新增任务类型也能低成本接入。

## 9. harness 衔接（Tier 1 配置下发）

Tier 1 的 agent hook 通过 harness 配置下发到 Agent Core（见沙箱设计 §3.5 `POST /harness/sync` 的 `toolPolicy` 邻位）。建议在 harness 配置增 `agentHooks` 段：

| 字段 | 说明 |
|---|---|
| `stopHooks` | Stop 时执行的检查命令列表（lint/test/...）+ 各自的阻断策略 |
| `postToolUseHooks` | 工具调用后的即时检查（可选）|
| `maxSelfCorrect` | 自纠最大轮次（防死循环，默认 3）|

- Agent Core 按 `agentRuntime` 把这些配置翻译成对应 runtime 的 hook 形态（claude-code → Claude Code hooks；其他 runtime 无等价能力则忽略）；
- 凭证/敏感命令遵循沙箱安全策略（工具 allowlist、文件边界）。

## 10. 与既有文档的修改清单

落地本设计需同步修改：

| 文档 | 修改点 |
|---|---|
| AgentFlow 与 Workflow 模板设计 | `AgentFlow` 增任务级 `verification` 字段；模板主表 `workflow_templates` 增 `verification` 列；§7.4 补充任务级 gate 与 step 级 requiresConfirmation 的层级关系 |
| Agent-Orchestration 实现规范 | §1 增 `run_verification` 表；§3.3 **run** 状态机增 `VERIFYING` 态（step 状态机不变）；§3.5 watchdog 纳入 run VERIFYING；§2.6 run 查询增 run 级 `verification` 字段 |
| 沙箱设计 | §3.5 harness 配置增 `agentHooks` 段；明确 Agent Core 按 runtime 翻译 hook 的能力要求 |
| 通用任务模块设计 | 增「Agent 任务准出验收」：task 收尾触发点、`task.verification` 配置载体（缺省继承项目级默认）、裁决动作映射到 task 状态（见 §8）|
| 项目配置（Agent-Management）| 新增项目级默认 `VerificationSpec`，供 Agent 任务未指定时继承 |

## 11. 待确认 / 后续

| # | 议题 | 说明 |
|---|---|---|
| 1 | gate 执行环境复用 vs 独立（D4）| 复用任务最终 workspace 省 clone，但任务收尾时 workspace 可能已释放/冻结，且与 editor pod 的 lease 互斥需协调（参见实现规范 §3 缺陷 #3）；独立 session 则需按最终 commit 重新 clone |
| 2 | LLM-judge 成本与一致性 | 评审 session 也耗 token；评审结果非确定性，是否需多次采样投票 |
| 3 | gate 超时阈值 | 任务级 script 验收（跑全量/集成测试）可能很慢，VERIFYING 的 watchdog 超时如何设 |
| 4 | FAIL 转人工的补救定位 | 任务级 FAIL 后人工从哪个 step 补救——是否需要 gate 在报告里给出"问题大概出在哪一 step"的定位提示 |
| 5 | onFail=AUTO_RETRY 的续聊目标 | 自动重试时续聊"终端 step"未必能改上游问题；是否限制只在单 step/单对话任务下开放自动重试 |
| 6 | Tier 1 与 Tier 2 检查项重叠 | 同样的测试在 hook（每次会话）和任务级 script 验收里都跑，能否分工——Tier 1 跑快检（lint/单测），Tier 2 跑慢检（集成/全量）|
| 7 | Agent 任务收尾判定 | Agent 模式"对话完成"如何判定——纯靠用户「完成任务」动作，还是支持自动收尾条件（连续 N 轮无改动等），影响 gate 触发时机（见 §8.1）|
| 8 | Agent 任务验收配置优先级 | task 级与项目级默认 `VerificationSpec` 的合并/覆盖规则（整体覆盖还是字段级合并）|
| 9 | 多 verifier 并行 vs 串行 | 一个任务配多个 verifier 时并行跑省时间，但都要起 session，资源占用更高 |
