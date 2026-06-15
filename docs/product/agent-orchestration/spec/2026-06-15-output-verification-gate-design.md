# 产出验收设计（Output Verification）

> 文档定位：定义 Agent 执行产出的验收机制。分两层——**会话内自检**（agent 自带 hook，沙箱内，best-effort）与**编排层验收 gate**（attempt 成功后，权威，驱动状态机）。两层职责正交，互不依赖。
>
> 关联文档：沙箱设计（Agent Core 接口）、AgentFlow 与 Workflow 模板设计（run/step/attempt 模型）、Agent-Orchestration 实现规范（状态机、DDL）、通用任务模块设计（Task/Conversation）。

## 0. 背景与目标

Agent 执行结束后，产出（代码改动 / StepOutput / artifact）不一定符合要求。需要一套机制在产出进入下游（推进 DAG、对用户呈现"完成"）之前做检查。

**核心边界判断**：需求是「会话**结束之后**检查产出」。按既有架构，"结束之后"是编排层领域——Agent Core 只负责运行时执行，业务/验收语义归编排层（见沙箱设计 §0、§7）。agent 自带 hook 跑在沙箱内、发生在会话"正要结束的一刻"，本质是会话内行为，承担不了"结束之后"的权威验收，且与具体 `agentRuntime` 强耦合（claude-code 的 hook 机制 opencode/codex 未必有）。

因此采用两层设计，定位严格区分：

| 层 | 位置 | 时机 | 定位 | runtime 耦合 |
|---|---|---|---|---|
| **会话内自检** | 沙箱内 | 会话结束的一刻 | best-effort 预过滤，让 agent 自纠 | 强（依赖具体 runtime hook）|
| **编排层验收 gate** | 编排层 | attempt SUCCEEDED 之后 | 权威验收，决定 step 去留 | 无（对所有 runtime 一致）|

一句话：**hook 让 agent 自己别交垃圾，gate 替系统判合不合格。**

## 1. 设计决策（待校准）

下列取值为 MVP 默认，标注供产品校准：

| # | 决策点 | MVP 取值 | 备选 |
|---|---|---|---|
| D1 | gate 范围 | 先做 AgentFlow step 验收（状态机现成）；通用任务（Agent 模式）预留扩展位 | 同时做通用任务 |
| D2 | 验收器形态 | 确定性脚本（test/lint）+ LLM-judge 两种先落地；规则 policy 预留 | 三种全做 / 只做脚本 |
| D3 | 不通过默认动作 | 自动重试 N 次（带验收反馈续聊）→ 耗尽转人工挂起（SUSPENDED）| 直接挂起等人 / 直接 FAILED |
| D4 | 验收器执行环境 | 复用被验 step 的 workspace（同 repo 改动可直接跑测试）| 新建独立轻量 session |
| D5 | LLM-judge 是否独立 step | 作为 gate 内部调用（不占 DAG 节点），与 AgentFlow 里手写 verify step 并存 | 强制建模为 DAG step |

## 2. 整体架构

```text
┌─ Agent Core（沙箱内）────────────────────────────────┐
│  代码 Agent 执行                                      │
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
                         │ attempt.result(success)
                         │ + StepOutput / commitSha / changes
                         ▼
┌─ Agent-Orchestration（编排层）───────────────────────┐
│  attempt → SUCCEEDED                                 │
│    │                                                 │
│    ▼                                                 │
│  ┌─ Tier 2: 验收 gate（权威）────────────────────┐  │
│  │  按 step.verification 配置执行验收器：          │  │
│  │   · 确定性脚本（在 workspace 跑 test/lint）      │  │
│  │   · LLM-judge（评审 StepOutput / diff）         │  │
│  │   · 规则 policy（改动范围 / 文件白名单）         │  │
│  │  汇总裁决 → PASS / FAIL / NEEDS_REVIEW          │  │
│  └──────────────────┬─────────────────────────────┘  │
│                     ▼                                 │
│   PASS        → step COMPLETED → 推进下游            │
│   FAIL        → AUTO_RETRY（带验收反馈续聊）          │
│                 重试耗尽 → step FAILED               │
│   NEEDS_REVIEW→ step SUSPENDED（人工确认）           │
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

## 4. Tier 2：编排层验收 gate（权威）

### 4.1 触发点

在 StepAttempt 进入 SUCCEEDED 之后、WorkflowStep 决定 COMPLETED/SUSPENDED 之前，插入验收 gate。即修改现有 step 状态机（见实现规范 §3.2）的 `RUNNING → COMPLETED` 这条边——原来 `attempt SUCCEEDED 且 requires_confirmation=false` 直接 COMPLETED，现在先过 gate。

### 4.2 验收输入

gate 拿到的产出快照：

| 输入 | 来源 | 说明 |
|---|---|---|
| `StepOutput` | attempt 成功事件 | summary / result / artifactRefs |
| `commitSha` | session.completed payload | 本次代码改动的 commit |
| `changes` | `GET /sessions/{id}/changes` | 改动文件列表 + diff |
| `sessionRef` | step.session_ref | 供续聊（验收不通过反馈给 agent）|
| step 上下文 | AgentFlow 快照 | step 名称、prompt、上游 StepOutput |

### 4.3 验收器形态

三种验收器，可组合（一个 step 配多个，全部 PASS 才算通过）：

#### （A）确定性脚本验收器 `script`

在被验 step 的 workspace（D4：复用同一 workspace，改动已在）执行预定义命令，按退出码判定。

| 字段 | 类型 | 说明 |
|---|---|---|
| `command` | string | 执行命令，如 `npm test`、`./gradlew check` |
| `timeoutSec` | int | 超时，默认 600 |
| `passOn` | enum | `exit_zero`（默认）/ 自定义判定 |

- 复用 Agent Core：编排层发起一个轻量 session（同 repo / 同 commit，无需 LLM），跑命令收集退出码 + stdout/stderr；
- 退出码 0 → PASS；非 0 → FAIL，stdout/stderr 作为反馈。

#### （B）LLM-judge 验收器 `llm`

用一个评审 Agent 评估产出是否满足 step 目标（D5：作为 gate 内部调用，不占 DAG 节点）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `judgeAgentRef` | string | 评审用 agent 快照引用 |
| `rubric` | string | 评审标准（prompt 模板，可引用 step 目标与 diff）|
| `passThreshold` | number | 通过阈值（如评分 ≥ 0.8）|

- 编排层渲染 rubric（注入 StepOutput / diff / step 目标），发起评审 session；
- 评审 Agent 返回结构化裁决 `{ verdict: pass/fail/needs_review, score, reasons[] }`；
- 注意：这与 AgentFlow 里手写一个 `verify` step（§7.4 范式）并存——手写 verify step 是流程显式节点（占 DAG、可人工确认），llm 验收器是 gate 内嵌的自动评审，二者按需选用。

#### （C）规则 policy 验收器 `policy`（预留）

确定性规则，不跑命令、不调 LLM，编排层本地判定。

| 规则 | 说明 |
|---|---|
| 改动文件白/黑名单 | 禁止改 CI 配置、鉴权、生产配置等 |
| 改动规模上限 | 文件数 / 增删行数超阈值 → NEEDS_REVIEW |
| 必须产出 commit | 声明应改代码却无 commit → FAIL |

MVP 预留接口，先不实现具体规则集。

### 4.4 裁决与动作

各验收器结果汇总为 step 级裁决（最严格者优先：任一 FAIL 则 FAIL；无 FAIL 但有 NEEDS_REVIEW 则 NEEDS_REVIEW；全 PASS 则 PASS）：

| 裁决 | 动作 | 状态机 |
|---|---|---|
| **PASS** | 放行 | step → COMPLETED（若 step 本身 `requiresConfirmation=true`，仍按原逻辑先 SUSPENDED 等人工确认）|
| **FAIL** | 自动重试（D3）| 起新 attempt（trigger=`AUTO_RETRY`），把验收失败原因作为 feedback 续聊；retry_count < maxRetries 时重试，耗尽 → step FAILED |
| **NEEDS_REVIEW** | 转人工 | step → SUSPENDED，验收报告附在 suspend 信息里，用户 confirm（放行）/ continue（带反馈续聊）/ 走 retry |

gate 自身的执行结果落 `workflow_event`（新增 category 见 §5），可审计、可在前端展示验收报告。

### 4.5 与 requiresConfirmation 的关系

二者正交、可叠加：
- `requiresConfirmation` 是**流程语义**——这个 step 无论产出如何都要人确认；
- 验收 gate 是**质量语义**——自动判定产出是否合格。

叠加顺序：attempt SUCCEEDED → 跑 gate → gate PASS 后，若 `requiresConfirmation=true` 再 SUSPENDED 等人工。gate FAIL/NEEDS_REVIEW 时不进入 requiresConfirmation 的确认（先解决质量问题）。

## 5. 数据模型

### 5.1 AgentFlowStep 扩展

在 `AgentFlowStep`（见 AgentFlow 设计 §6）上新增 `verification` 字段，描述该 step 的验收配置。由模板转换 AgentFlow 时带入（模板侧 `workflow_template_steps` 增 `verification` 列，归 Agent-Management）。

```java
class AgentFlowStep {
    String id;
    String name;
    AgentSpec agent;
    PromptSpec prompt;
    boolean requiresConfirmation;
    VerificationSpec verification;   // 新增，可为 null（无验收）
}

class VerificationSpec {
    List<Verifier> verifiers;        // 验收器列表，全部 PASS 才通过
    int maxRetries;                  // gate FAIL 时自动重试上限，默认 2
    FailAction onExhausted;          // 重试耗尽后动作：FAIL（默认）/ SUSPEND
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
enum FailAction { FAIL, SUSPEND }
```

> `verification` 为空 → 该 step 不走 gate，行为与现状完全一致（向后兼容）。

### 5.2 新增表 `step_verification`

记录每次 gate 执行的裁决，供审计与前端展示。一个 attempt 一条 gate 记录（含各 verifier 子结果）。

```sql
CREATE TABLE step_verification (
    id              VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL REFERENCES workflow_run(id),
    step_id         VARCHAR(64) NOT NULL REFERENCES workflow_step(id),
    attempt_id      VARCHAR(64) NOT NULL REFERENCES step_attempt(id),
    verdict         VARCHAR(16) NOT NULL,          -- PASS / FAIL / NEEDS_REVIEW
    verifier_results JSONB      NOT NULL,          -- [{type, verdict, score?, detail, durationMs}]
    report          TEXT,                           -- 汇总报告（前端展示 / 续聊反馈来源）
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_verif_attempt ON step_verification (attempt_id);
CREATE INDEX idx_verif_step    ON step_verification (step_id);
```

### 5.3 `workflow_event` 扩展

`workflow_event.category` 现有 `control/display/runtime`，新增 `verification` 类，记录 gate 生命周期事件：

| eventType | 说明 |
|---|---|
| `verification.started` | gate 开始执行 |
| `verification.verifier_result` | 单个 verifier 出结果 |
| `verification.completed` | gate 汇总裁决（PASS/FAIL/NEEDS_REVIEW）|

控制类（推进状态机）全量留存；验收报告正文较大，按展示类保留策略裁剪。

## 6. 状态机集成

修改实现规范 §3.2 WorkflowStep 状态机，在 `RUNNING → COMPLETED` 之间插入 gate 中间态 `VERIFYING`：

| From | To | 触发 | 动作 |
|---|---|---|---|
| RUNNING | VERIFYING | attempt SUCCEEDED 且 step 配了 `verification` | 启动 gate（异步跑 verifiers）|
| RUNNING | COMPLETED | attempt SUCCEEDED 且**无** `verification` 且 `requiresConfirmation=false` | 原逻辑不变 |
| RUNNING | SUSPENDED | attempt SUCCEEDED 且**无** `verification` 且 `requiresConfirmation=true` | 原逻辑不变 |
| VERIFYING | COMPLETED | gate PASS 且 `requiresConfirmation=false` | 写 output，触发下游 |
| VERIFYING | SUSPENDED | gate PASS 且 `requiresConfirmation=true`；或 gate NEEDS_REVIEW；或 gate FAIL 且重试耗尽且 onExhausted=SUSPEND | 持久化 session_ref + 验收报告 |
| VERIFYING | RUNNING | gate FAIL 且 retry_count < maxRetries | retry_count++，起新 attempt（AUTO_RETRY，feedback=验收报告，resume session）|
| VERIFYING | FAILED | gate FAIL 且重试耗尽且 onExhausted=FAIL | 记 error |

- `VERIFYING` 也需纳入 watchdog（gate 自身可能超时/卡死，超时按 FAIL 处理）；
- run 状态聚合（实现规范 §3.3）：有 step VERIFYING → run 视为 RUNNING；
- 并发边界沿用 CAS + version：gate 完成回写 step 状态时若 version 不匹配（如 run 已 CANCELLING），以 run 状态为准放弃。

```text
attempt SUCCEEDED
   │
   ├─ 无 verification ──► （原逻辑）COMPLETED / SUSPENDED
   │
   └─ 有 verification ──► VERIFYING
                            │
                            ├─ PASS ──► COMPLETED（或 requiresConfirmation 时 SUSPENDED）
                            ├─ NEEDS_REVIEW ──► SUSPENDED（附验收报告）
                            └─ FAIL ──┬─ retry_count < maxRetries ──► RUNNING（AUTO_RETRY 续聊）
                                      └─ 耗尽 ──► FAILED / SUSPENDED（按 onExhausted）
```

## 7. API 设计

### 7.1 查询验收结果（并入 run 查询）

`GET /runs/{runId}` 的 step 列表项增加 `verification` 字段：

```json
{
  "stepId": "step_fix", "stepKey": "fix", "status": "VERIFYING",
  "currentAttemptNo": 2,
  "verification": {
    "verdict": "FAIL",
    "verifierResults": [
      { "type": "SCRIPT", "verdict": "FAIL", "detail": "3 tests failed", "durationMs": 12400 },
      { "type": "LLM", "verdict": "PASS", "score": 0.86 }
    ],
    "report": "单元测试未通过：UserServiceTest.testRefresh ..."
  }
}
```

### 7.2 实时事件流

`GET /runs/{runId}/events` 推送 `category=verification` 事件（started / verifier_result / completed），前端实时渲染验收进度与报告。

### 7.3 人工动作复用现有接口

gate 导致 SUSPENDED 后，用户操作复用既有接口（实现规范 §2.3~2.5），无需新增：
- `confirm`：放行验收（人工覆盖 NEEDS_REVIEW / FAIL）→ step COMPLETED；
- `continue`：带反馈续聊，让 agent 针对验收报告改进；
- `retry`：FAILED step 手动重试。

> gate 不引入新的写接口——验收是编排层自动行为，人工介入点完全落在现有 suspended/failed 的 confirm/continue/retry 上。

## 8. 通用任务（Agent 模式）的扩展位

D1 决定 MVP 只做 AgentFlow step 验收。通用任务（见通用任务模块设计）的 Agent 模式没有 step/DAG，验收 gate 的接入点不同，预留如下方向（本期不实现）：

- Agent 模式一个 task 一个主 conversation，可在「每轮 message_stop 之后」挂一个轻量 gate；
- 裁决动作映射：PASS → 正常展示；FAIL → 提示用户或自动追加一轮修复 prompt；NEEDS_REVIEW → 标记待人工；
- 配置来源不是 AgentFlow，而是项目级或 task 级验收配置，需另设载体。

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
| AgentFlow 与 Workflow 模板设计 | `AgentFlowStep` 增 `verification` 字段；模板表 `workflow_template_steps` 增 `verification` 列；§7.4 补充 gate 与 requiresConfirmation 的关系 |
| Agent-Orchestration 实现规范 | §1 增 `step_verification` 表；§3.2 step 状态机增 `VERIFYING` 态；§3.5 watchdog 纳入 VERIFYING；§2.6 run 查询增 `verification` 字段 |
| 沙箱设计 | §3.5 harness 配置增 `agentHooks` 段；明确 Agent Core 按 runtime 翻译 hook 的能力要求 |
| 通用任务模块设计 | §6 待确认补充「Agent 模式产出验收」议题 |

## 11. 待确认 / 后续

| # | 议题 | 说明 |
|---|---|---|
| 1 | gate 执行环境复用 vs 独立（D4）| 复用被验 step workspace 省 clone，但与续聊新 attempt、editor pod 的 workspace lease 互斥需协调（参见实现规范 §3 缺陷 #3）|
| 2 | LLM-judge 成本与一致性 | 评审 session 也耗 token；评审结果非确定性，是否需多次采样投票 |
| 3 | gate 超时阈值 | script 验收（跑全量测试）可能很慢，VERIFYING 的 watchdog 超时如何设 |
| 4 | 验收失败的反馈质量 | FAIL 续聊时把 stderr/评审理由原样灌给 agent 是否够，需不需要结构化 |
| 5 | 多 verifier 并行 vs 串行 | 一个 step 配多个 verifier 时并行跑省时间，但都要起 session，资源占用更高 |
| 6 | Tier 1 与 Tier 2 检查项重叠 | 同样的测试在 hook 和 script 验收里都跑，是否浪费；能否让 Tier 2 信任 Tier 1 结果（不建议，破坏权威性）|
| 7 | 通用任务模式验收载体（D1 扩展位）| Agent 模式验收配置放哪、谁来配 |
