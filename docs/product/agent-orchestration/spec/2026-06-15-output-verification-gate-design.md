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

| # | 决策点 | MVP 取值 | 备选 |
|---|---|---|---|
| D1 | gate 范围 | MVP 先做 AgentFlow 任务级准出（run 状态机现成）；Agent 任务（通用任务）留扩展位 | 同时做 Agent 任务 |
| D2 | 验收器形态 | 确定性脚本（test/lint）+ LLM-judge 两种先落地；规则 policy 预留 | 三种全做 / 只做脚本 |
| D3 | 不通过默认动作 | 转人工挂起（任务级问题需人判断从何补救）| 自动续聊终端对话重试 N 次 / 直接 FAILED |
| D4 | 验收器执行环境 | 复用任务最终产出所在的 workspace（最终 commit 已在）| 新建独立轻量 session |
| D5 | LLM-judge 是否独立 step | 作为 gate 内部调用（不占 DAG 节点），与 AgentFlow 里手写 verify step 并存 | 强制建模为 DAG step |

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
│   FAIL        → 任务 SUSPENDED（人工决定补救）       │
│                 （或按 D3 自动续聊终端对话重试）      │
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

三种验收器，可组合（一个任务配多个，全部 PASS 才算通过）：

#### （A）确定性脚本验收器 `script`

在任务最终产出所在的 workspace（D4：最终 commit 已在）执行预定义命令，按退出码判定。

| 字段 | 类型 | 说明 |
|---|---|---|
| `command` | string | 执行命令，如 `npm test`、`./gradlew check` |
| `timeoutSec` | int | 超时，默认 600 |
| `passOn` | enum | `exit_zero`（默认）/ 自定义判定 |

- 复用 Agent Core：编排层发起一个轻量 session（同 repo / 同最终 commit，无需 LLM），跑命令收集退出码 + stdout/stderr；
- 退出码 0 → PASS；非 0 → FAIL，stdout/stderr 作为反馈；
- 任务级最适合放**全量测试 / 集成测试 / 整体构建**——这类检查本就要等所有改动到齐。

#### （B）LLM-judge 验收器 `llm`

用一个评审 Agent 评估任务最终产出是否满足任务目标（D5：作为 gate 内部调用，不占 DAG 节点）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `judgeAgentRef` | string | 评审用 agent 快照引用 |
| `rubric` | string | 评审标准（prompt 模板，可引用任务目标与总 diff）|
| `passThreshold` | number | 通过阈值（如评分 ≥ 0.8）|

- 编排层渲染 rubric（注入任务目标 / 总 diff / 各 step StepOutput），发起评审 session；
- 评审 Agent 返回结构化裁决 `{ verdict: pass/fail/needs_review, score, reasons[] }`；
- 注意：这与 AgentFlow 里手写一个 `verify` step（§7.4 范式）并存——手写 verify step 是流程显式节点（占 DAG、可人工确认），llm 验收器是任务收尾时 gate 内嵌的自动评审，二者按需选用。

#### （C）规则 policy 验收器 `policy`（预留）

确定性规则，不跑命令、不调 LLM，编排层本地判定。

| 规则 | 说明 |
|---|---|
| 改动文件白/黑名单 | 禁止改 CI 配置、鉴权、生产配置等 |
| 改动规模上限 | 文件数 / 增删行数超阈值 → NEEDS_REVIEW |
| 必须产出 commit | 声明应改代码却无 commit → FAIL |

MVP 预留接口，先不实现具体规则集。

### 4.4 裁决与动作

各验收器结果汇总为任务级裁决（最严格者优先：任一 FAIL 则 FAIL；无 FAIL 但有 NEEDS_REVIEW 则 NEEDS_REVIEW；全 PASS 则 PASS）：

| 裁决 | 动作 | 状态机 |
|---|---|---|
| **PASS** | 准出 | 任务 → COMPLETED |
| **FAIL** | 转人工（D3 默认）| 任务 → SUSPENDED，验收报告附在 suspend 信息里。人工决定从哪个 step 补救：confirm 强制准出 / 对终端 step 续聊或重试 / cancel。（D3 备选：自动对终端对话续聊重试 N 次，耗尽再挂起）|
| **NEEDS_REVIEW** | 转人工 | 任务 → SUSPENDED，人工 confirm（准出）/ continue（带反馈续聊）/ retry |

gate 自身的执行结果落 `workflow_event`（新增 category 见 §5），可审计、可在前端展示验收报告。

> **为何 FAIL 默认转人工而非自动重试**：任务级失败往往是跨 step 的整体问题（如 step2 的实现没满足 step1 的设计），不一定能靠重跑某个终端 step 修复——需要人判断从 DAG 哪一环补救。step 级的自动重试语义在任务级不再天然成立，故默认交人工。

### 4.5 与 requiresConfirmation 的关系

二者层级不同、互补：
- `requiresConfirmation` 是 **step 级流程语义**——某个中间 step 无论产出如何都要人确认（如"验证结果"step 必须人工签字）；
- 任务级 gate 是**任务级质量语义**——任务整体产出是否合格、能否准出。

执行顺序：各 step 按自己的 `requiresConfirmation` 走完（该挂起确认的挂起确认）→ 所有 step COMPLETED → 任务级 gate → gate PASS 后任务才 COMPLETED。两者不冲突：step 确认管的是"流程要不要人盯某一环"，gate 管的是"最终交付合不合格"。

## 5. 数据模型

### 5.1 AgentFlow 扩展（任务级验收配置）

验收配置挂在 **AgentFlow 整体**（任务级），而非单个 step 上。在 `AgentFlow`（见 AgentFlow 设计 §6）新增 `verification` 字段。由模板转换 AgentFlow 时带入（模板主表 `workflow_templates` 增 `verification` 列，归 Agent-Management）。

```java
class AgentFlow {
    String flowId;
    String flowName;
    List<AgentFlowStep> steps;
    List<AgentFlowEdge> edges;
    // ... 既有字段
    VerificationSpec verification;   // 新增，任务级准出配置，可为 null（无验收）
}

class VerificationSpec {
    List<Verifier> verifiers;        // 验收器列表，全部 PASS 才准出
    FailAction onFail;               // FAIL 时动作：SUSPEND（默认，转人工）/ AUTO_RETRY / FAIL
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
    Object policyRules;              // 预留
}

enum VerifierType { SCRIPT, LLM, POLICY }
enum FailAction { SUSPEND, AUTO_RETRY, FAIL }
```

> `verification` 为空 → 该任务不走准出 gate，所有 step 完成即 run COMPLETED，行为与现状完全一致（向后兼容）。
>
> Agent 模式（通用任务）的验收配置不来自 AgentFlow，载体见 §8。

### 5.2 新增表 `run_verification`

记录每次任务级 gate 执行的裁决，供审计与前端展示。一个 run 一次准出验收（FAIL 后若自动重试再验则多条，按 `attempt_round` 区分）。

```sql
CREATE TABLE run_verification (
    id              VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL REFERENCES workflow_run(id),
    attempt_round   INTEGER     NOT NULL DEFAULT 1,    -- 第几次准出验收（含自动重试后复验）
    verdict         VARCHAR(16) NOT NULL,              -- PASS / FAIL / NEEDS_REVIEW
    verifier_results JSONB      NOT NULL,              -- [{type, verdict, score?, detail, durationMs}]
    final_commit    VARCHAR(64),                        -- 本次验收针对的最终 commit
    report          TEXT,                               -- 汇总报告（前端展示 / 续聊反馈来源）
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_runverif_run ON run_verification (run_id, attempt_round);
```

> 通用任务（Agent 模式）若后续纳入，可复用同表，`run_id` 存 task 对应的 run 标识（或另设 `task_id` 列，见 §8 扩展位）。

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

## 8. 通用任务（Agent 模式）的扩展位

D1 决定 MVP 只做 AgentFlow 任务级准出。Agent 模式（见通用任务模块设计）一个 task 一个主 conversation、多轮对话，同样有明确的"执行完成"时点（对话任务收尾），可按相同的任务级 gate 思路接入。**本期不实现**，预留方向如下：

- **触发点**：task 即将 done 时（用户主动结束或满足收尾条件），对会话最终产出（最后 commit / 总 changes / 末轮 message）跑 gate；
- **裁决动作映射**：PASS → task done；FAIL（转人工）→ 标记待处理提示用户；NEEDS_REVIEW → 标记待人工确认；
- **配置载体**：Agent 模式没有 AgentFlow，验收配置需来自**项目级**或 **task 级**设置，需另设载体字段；
- **复用面**：可共用同一套 verifier 形态（script / llm / policy）、裁决逻辑与 `run_verification` 表，差异只在触发点与配置载体。

> 把这部分独立成扩展位的原因：Agent 模式的"收尾"判定（何时算对话完成）和验收配置载体都还没定，与 AgentFlow 的现成 run 状态机不同，强行一起做会拖慢 MVP。

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
| 通用任务模块设计 | （后续）增「Agent 任务准出验收」——触发点（task 收尾）、验收配置载体（项目级/task 级）、裁决动作。MVP 不改，仅记入扩展位 |

## 11. 待确认 / 后续

| # | 议题 | 说明 |
|---|---|---|
| 1 | gate 执行环境复用 vs 独立（D4）| 复用任务最终 workspace 省 clone，但任务收尾时 workspace 可能已释放/冻结，且与 editor pod 的 lease 互斥需协调（参见实现规范 §3 缺陷 #3）；独立 session 则需按最终 commit 重新 clone |
| 2 | LLM-judge 成本与一致性 | 评审 session 也耗 token；评审结果非确定性，是否需多次采样投票 |
| 3 | gate 超时阈值 | 任务级 script 验收（跑全量/集成测试）可能很慢，VERIFYING 的 watchdog 超时如何设 |
| 4 | FAIL 转人工的补救定位 | 任务级 FAIL 后人工从哪个 step 补救——是否需要 gate 在报告里给出"问题大概出在哪一 step"的定位提示 |
| 5 | onFail=AUTO_RETRY 的续聊目标 | 自动重试时续聊"终端 step"未必能改上游问题；是否限制只在单 step/单对话任务下开放自动重试 |
| 6 | Tier 1 与 Tier 2 检查项重叠 | 同样的测试在 hook（每次会话）和任务级 script 验收里都跑，能否分工——Tier 1 跑快检（lint/单测），Tier 2 跑慢检（集成/全量）|
| 7 | Agent 任务验收配置载体（D1）| Agent 模式验收配置放项目级还是 task 级、谁来配、数据按 run 还是 task 维度记录 |
| 8 | 多 verifier 并行 vs 串行 | 一个任务配多个 verifier 时并行跑省时间，但都要起 session，资源占用更高 |
