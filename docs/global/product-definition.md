# 产品定义

## 产品概述

- 产品名称：AgentSpace
- 产品定位：AgentSpace 是公司内部员工、团队和租户在线使用 Agent 的 Web SaaS 产品，面向租户治理、团队空间、知识上下文、Harness 基础配置、Agent 会话、Harness CICD 流水线任务、权限管理和协作管理提供统一入口。
- 目标用户：
  - 系统管理员：拥有全局管理权限，负责创建租户、授予租户管理员、维护全局团队模板、治理平台级能力与审计。
  - 租户管理员：负责本租户管理面内的租户配置、租户级团队模板和租户内团队空间清单治理。
  - 团队创建者：在所属租户内创建团队空间，默认拥有该空间管理员权限，并负责空间初始治理。
  - 团队管理员：管理成员、知识库、Harness 基础配置和 Harness CICD 流水线，并查看团队空间 Agent 会话与 Harness CICD 流水线任务历史。
  - 团队成员：在被授权空间中使用 Agent，新建手工 Agent 会话或启动已发布 Harness CICD 流水线任务，不具备配置权限。
  - 访客：在被授权空间中只读查看空间内容、Agent 会话历史和 Harness CICD 流水线任务历史。

## 需求背景

- 公司员工和团队需要在统一 Web Portal 中使用 Agent，而不是依赖各自本地环境或分散配置。
- Agent 作业既可能面向软件开发，也可能面向客服、法务、运营、研究或其他业务场景，产品不能强制要求代码仓、开发工具或任一 Harness 配置。
- Agent 需要理解空间长期指引和项目知识；长期规则、按需专业能力、确定性自动化和外部工具应分层配置，避免所有内容都进入每次任务上下文。
- 团队空间需要从零配置快速开始，并能逐步完善 AGENT.md、Skill、Tool、Agent、Harness CICD 流水线、Hook 和环境变量。
- Tool 的连接和复杂参数由公司 AI 市场统一治理，AgentSpace 只选择已发布 Tool，不重复建设连接配置。
- 多角色协作需要统一 RBAC 权限管理，并对接公司 devuc 服务完成租户和空间鉴权。
- Agent 会话是原子 Agent 执行记录；手工 Agent 会话由用户直接发起，Harness CICD 流水线节点 Agent 会话由系统根据 Harness CICD 流水线自动调起。
- Harness CICD 流水线需要由确定性的 HarnessPipelineEngine 控制节点、分支、重试和人工确认；Harness CICD 流水线配置是结构化流水线定义，不作为 prompt 交给 Agent 自由执行。
- Hook 需要在明确生命周期事件上执行校验、确认、通知和审计，不能依赖模型记住所有规则。

## 产品目标

- 提供全局管理面和租户管理面，支持租户创建、租户管理员授权及租户级治理。
- 将租户作为团队空间必选上级域，确保空间、权限、模板和审计具备明确归属。
- 提供团队空间创建、成员角色配置和项目编号关联入口。
- 允许新空间在没有知识库、远程仓或自定义 Harness 的情况下，直接使用平台默认 Agent 开始手工 Agent 会话。
- 提供知识库根目录和多层级知识域，支持上传资料、手工文件夹和可选远程代码仓绑定。
- 提供 AGENT.md 中心，维护空间级长期指引和知识域级局部指引。
- 提供 Skill 中心，支持从 AI 市场选择或通过 AI 辅助新建按需能力。
- 提供 Tool 中心，只允许从 AI 市场选择已发布 Tool；MCP 仅作为可能的底层协议。
- 提供 Agent 中心，支持从市场选择或 AI 辅助新建；未配置时使用平台默认 Agent。
- 提供 Hook 中心，通过内置模板或结构化自定义规则响应明确生命周期事件。
- 提供环境变量中心，仅在资源声明依赖时按需配置，敏感值由安全存储托管。
- 提供独立 Harness CICD 流水线中心，通过自然语言生成结构化草稿、可视化画布确认、测试发布，并由 AgentSpace HarnessPipelineEngine 执行 Harness CICD 流水线任务。
- 支持知识库配置文件与 Portal 双向同步，并通过版本、校验、测试和 diff 避免静默覆盖。
- 支持从用户选定知识库目录中的 `.codex`、`.claudecode`、`.opencode` 导入历史 Harness 草稿，导入后仍需人工发布。
- 支持团队成员创建手工 Agent 会话、继续历史会话、处理 Agent 询问、查看 Agent 会话历史并审阅文档变更。
- 支持团队成员启动已发布 Harness CICD 流水线任务，查看 Harness CICD 流水线任务清单、Run 状态、节点状态和关联 Agent 会话详情。

## 范围边界

**包含：**

- 全局管理面、租户管理面和租户管理员治理。
- 团队空间初始化，且新建空间必须归属一个租户。
- 创建者、管理员、团队成员和访客四类团队空间基础角色。
- 对接 devuc 完成身份、租户访问和团队空间权限鉴权。
- 零配置空间使用平台默认 Agent 直接创建手工 Agent 会话。
- 知识库根目录、多层级文件夹、资料上传和可选远程代码仓绑定。
- 空间级 `/AGENT.md` 和知识域级 `/<knowledge-domain>/AGENT.md`。
- `/.agentspace/` 目录中的 Agent、Skill、Harness CICD 流水线、Hook、Tool 引用和环境变量引用配置。
- Agent、Skill、Tool、Hook 和环境变量五个基础资源中心，以及 AGENT.md 指引中心。
- Skill 和 Agent 支持市场选择或空间内新建；AI 生成结果只形成结构化草稿。
- Tool 仅从 AI 市场选择，保存市场资源 ID、版本、能力快照和授权引用。
- Harness CICD 流水线支持 Agent、条件、人工确认、并行、子流水线和输出节点，用于串联产品 Agent、开发 Agent、运营 Agent 等大颗粒度 Agent 标准作业。
- HarnessPipelineEngine 负责流程状态、分支、重试、幂等、暂停、恢复和取消。
- Harness CICD 流水线任务拥有独立清单和详情；每个 Agent 节点自动创建 `source=harness_pipeline_node` 的 Agent 会话并进入 Agent 会话历史。
- Hook 支持手工 Agent 会话、Tool 调用前后、对象写入前后、Harness CICD 流水线生命周期、Harness 发布前和发布后事件。
- Hook 动作支持注入上下文、校验、阻止、人工确认、执行 Skill、启动 Harness CICD 流水线、通知和审计。
- 环境变量按资源依赖补充，敏感值只保存安全引用。
- 配置版本使用草稿、待补全、校验失败、测试通过、已发布和已停用状态，资源可同时保留当前已发布版本和编辑中草稿。
- Portal 保存前进行结构化校验、模拟测试、配置 diff 和版本冲突检查。
- 当前生效 Harness 配置生成手工 Agent 会话级或 HarnessPipelineRun 级快照。
- Agent 会话创建、继续输入、询问回答、会话历史、文档 diff 审阅。
- Harness CICD 流水线配置、任务启动、Run 状态、节点状态、人工确认、取消和重试。
- 全局团队模板和租户级团队模板，可预置知识库结构和 Harness 配置。

**不包含：**

- 公司 AI 市场自身的 Tool、Skill 或 Agent 发布、审核、计费和连接配置流程。
- 在 AgentSpace 中新建 Tool、编辑 MCP 地址、启动命令、认证参数或协议细节。
- 任意脚本型 Hook、任意 Shell 执行和模型自行猜测 Hook 事件。
- 外部 agent core 自身的模型调度、运行沙箱、内部状态机和任务队列实现。
- 将 Harness CICD 流水线定义作为 prompt 交给 Agent 自由执行流程。
- 租户计费、套餐、容量配额和商业化治理。
- 将历史 `.codex`、`.claudecode`、`.opencode` 配置作为运行时兼容布局或直接生效配置；这些目录只可由用户显式导入为草稿。
- 强制要求空间配置知识库、远程仓、AGENT.md、Skill、Tool、Agent、Harness CICD 流水线、Hook 或环境变量。
- 远程代码仓平台的账号体系、凭据申请和仓库权限审批。
- 项目编号体系本身的创建、校验规则和主数据治理。

## 核心场景

| 场景 | 用户 | 目标 | 说明 |
| --- | --- | --- | --- |
| 创建租户 | 系统管理员 | 开通 AgentSpace 租户 | 在全局管理面维护租户和状态。 |
| 管理租户 | 租户管理员 | 治理本租户配置 | 查看团队空间清单和租户级团队模板。 |
| 创建零配置空间 | 团队创建者 | 立即开始使用 Agent | 不配置知识库或 Harness，直接使用平台默认 Agent。 |
| 创建团队项目空间 | 团队创建者 | 初始化空间并邀请成员 | 可选填写项目编号和添加成员。 |
| 配置知识库 | 团队管理员 | 建立 Agent 可感知的知识结构 | 支持业务资料和可选远程代码仓，不限定开发项目。 |
| 配置 AGENT.md | 团队管理员 | 维护长期规则和领域约定 | 空间级指引始终适用，知识域指引按结构化任务范围加载。 |
| 配置 Skill | 团队管理员 | 提供按需专业能力 | 从市场选择或 AI 辅助新建并测试。 |
| 添加 Tool | 团队管理员 | 为 Agent 提供外部能力 | 仅从 AI 市场添加，不配置底层连接。 |
| 配置 Agent | 团队管理员 | 组合职责、知识和能力 | 从市场选择或新建；未配置时使用默认 Agent。 |
| 配置 Hook | 团队管理员 | 在生命周期事件执行确定性治理 | 使用模板或结构化规则，不允许任意脚本。 |
| 配置 Harness CICD 流水线 | 团队管理员 | 沉淀标准作业流水线 | 进入独立 Harness CICD 流水线中心，通过画布确认后发布。 |
| 按需补充环境变量 | 团队管理员 | 满足资源依赖 | 仅在依赖出现时配置，敏感值进入安全存储。 |
| 导入历史配置 | 团队管理员 | 复用已有 Agent 配置 | 从 `.codex`、`.claudecode`、`.opencode` 生成 Portal 草稿，不直接生效。 |
| 新建手工 Agent 会话 | 团队成员 | 输入指令并启动 Agent | 可使用默认 Agent 或自定义 Agent。 |
| 启动 Harness CICD 流水线任务 | 团队成员 | 按标准流程完成复杂工作 | Harness CICD 流水线任务独立展示，节点自动创建 Agent 会话。 |
| 回答 Agent 询问 | 对话创建人 | 推动手工会话继续运行 | 支持选项型或开放式回答。 |
| 处理 Harness CICD 流水线确认 | Harness CICD 流水线任务创建人 | 推动流程继续或终止 | 在 Harness CICD 流水线任务页处理确认、取消和重试。 |
| 查看 Agent 会话历史 | 空间用户 | 查看会话清单和详情 | 同时包含手工会话与 Harness CICD 流水线节点会话。 |
| 查看 Harness CICD 流水线任务历史 | 空间用户 | 查看流程清单和 Run 详情 | 访客只读，创建人可处理运行控制。 |
| 审阅文档变更 | 对话创建人 | 接受或拒绝 Agent 修改 | 文档变更先形成待接受 diff。 |
| 应用团队模板 | 团队管理员 | 快速初始化空间 | 模板中的每项配置均可选，不阻止零配置开始作业。 |

## 关键能力

- 零配置运行：空间创建完成后可直接使用平台默认 Agent。
- 三层执行模型：Agent 会话是原子 Agent 执行记录；Harness CICD 流水线配置是结构化流水线定义；Harness CICD 流水线任务 / Run 由 HarnessPipelineEngine 状态机执行。
- 分层上下文：AGENT.md 存放长期指引，知识库保存资料，Skill 按任务加载。
- AGENT.md 继承：空间级规则与 `knowledgeDomainIds` 或执行对象声明命中的知识域规则叠加，局部规则不能放宽平台安全策略。
- Tool 市场引用：AgentSpace 只消费市场发布版本和授权引用，底层可由 MCP 等协议实现。
- AI 辅助配置：自然语言只生成结构化草稿和依赖建议，发布前必须人工确认、测试和查看 diff。
- 确定性 Harness CICD 流水线：HarnessPipelineEngine 控制多 Agent 流程，agent core 负责执行 Agent 节点；Skill 和 Tool 是 Agent 执行过程中的能力。
- 节点会话统一记录：Harness CICD 流水线每个 Agent 节点自动创建 `source=harness_pipeline_node` 的 Agent 会话，并在 Agent 会话历史中可查看。
- 确定性 Hook：只响应明确事件，支持同步拦截和异步通知；Harness 发布拆分为可阻止的 before 和不可回滚的 after，并限制调用链递归。
- 依赖驱动配置：环境变量和 Tool 按 Skill、Agent、Harness CICD 流水线或 Hook 的依赖就地补充。
- 历史配置导入：旧目录只作为草稿来源，MCP/server/tool 配置仅生成市场 Tool 匹配建议，subagent 配置映射为 Agent 草稿，Harness CICD 流水线配置映射为 Harness CICD 流水线草稿候选。
- 配置生命周期：版本状态为草稿、待补全、校验失败、测试通过、已发布和已停用；编辑草稿不覆盖当前生效版本。
- 新文件布局：

```text
/AGENT.md
/<knowledge-domain>/AGENT.md
/.agentspace/
  manifest.yaml
  agents/
  skills/
  hooks/
  harness-pipelines/
  tools.yaml
  env.yaml
```

- 双向同步：Portal 与新文件布局之间同步，外部变化只形成待确认草稿，保存前校验基准版本，冲突时禁止静默覆盖。
- 版本快照：手工 Agent 会话和 HarnessPipelineRun 固定 AGENT.md、Agent、Skill、Tool、Harness CICD 流水线、Hook 与环境变量引用版本。
- 非开发适配：远程代码仓、开发 Tool 和代码规则均为可选，业务团队可以只使用资料、AGENT.md 和默认 Agent。

## 约束与风险

- AGENT.md 过长会挤占任务上下文，需要提供长度提示、分层指引和知识索引。
- AGENT.md、Skill 与知识库内容容易重复，需要明确“长期规则、按需方法、资料正文”的边界。
- Tool 市场资源的版本、权限、授权失效和下线会影响空间配置稳定性。
- Harness CICD 流水线的重试、并行、人工确认、子流程和节点 Agent 会话关联会增加状态恢复与幂等复杂度。
- Hook 可能形成 Hook 到 Harness CICD 流水线、子流水线再触发 Hook 的循环，需要调用链和最大深度保护。
- Portal 与配置文件并发修改存在版本漂移和冲突风险。
- 敏感环境变量、Tool 授权和任务事件必须防止明文进入日志、diff、知识库或快照。
- 新布局运行时不兼容 `.codex`、`.claudecode` 或 `.opencode`；历史配置导入只生成草稿，不保证所有第三方字段可映射。
- R-0630 同时交付 Harness 基础配置、手工 Agent 会话、Harness CICD 流水线任务运行和 Hook 执行，研发与验收范围较大。

## 会签评审

| 角色 | 结论 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认 AGENT.md 内容上限、知识域命中规则、默认 Agent、六个基础配置中心交互稿和 Harness CICD 流水线独立入口。 |
| 技术 | 待确认 | 需确认 `.agentspace` Schema、AgentSession、HarnessPipelineEngine、Hook 事件协议、Tool 市场契约和 Secret Service。 |
| 测试 | 待确认 | 需覆盖零配置、分层 AGENT.md、市场 Tool、配置发布、手工 Agent 会话、Harness CICD 流水线任务、节点会话、Hook 治理和非开发空间。 |
