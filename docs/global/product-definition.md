# AgentSpace 产品定义

## 产品概述

- 产品名称：AgentSpace
- 产品定位：AgentSpace 是公司内部员工、团队和租户在线使用 Agent 的 Web SaaS 产品，面向租户治理、团队空间、知识上下文、Harness 基础配置、Agent 会话、AgentFlow、事件源自动触发、权限管理和协作管理提供统一入口。
- 目标用户：
  - 系统管理员：创建租户、授予租户管理员、维护平台级治理策略和事件源类型开放策略。
  - 租户管理员：治理本租户配置、租户级模板、租户级事件源授权和团队空间清单。
  - 团队创建者和团队管理员：管理成员、知识库、Harness 基础配置、AgentFlow 和事件源配置。
  - 团队成员：使用开始作业入口创建 Agent 会话或启动已发布 AgentFlow，并查看事件执行看板。
  - 访客：只读查看空间内容、Agent 会话历史、AgentFlow 任务历史和事件执行看板。

## 核心问题

- 团队需要在统一 Web Portal 中使用 Agent，而不是依赖本地环境或分散配置。
- 新空间必须能零配置开始，不能强制要求知识库、远程仓、AGENT.md、Skill、Tool、Agent、AgentFlow 或环境变量。
- Agent 需要长期指引、按需 Skill、外部 Tool 和可重复多 Agent 标准作业分层配置，避免所有内容进入每次 prompt。
- 公司 eDevOps 系统的 Feature/需求项会持续产生可由 Agent 或 AgentFlow 处理的新事件，人工搬运会造成响应延迟、重复触发和并发不可控。

## 功能范围

- 团队空间创建和成员治理，租户上下文与团队空间 RBAC。
- 知识库根目录、多层级知识域、资料上传和可选远程代码仓绑定；远程代码仓只作为知识来源，不作为 Harness 配置同步来源。
- AGENT.md 指引中心，支持空间级和知识域级 Markdown 指引、AI 草稿、加载预览、diff 和发布。
- Harness 基础配置中心，包含 Skill、Tool、Agent 和环境变量：
  - Skill 只支持从公司 AI 市场选择已发布版本。
  - Tool 只支持从公司 AI 市场添加已发布版本；MCP 仅作为市场或适配层底层协议，不作为用户配置项。
  - Agent 只支持空间内在线配置，配置内容包括 Agent 名称、描述、使用的 Skill、使用的 Tool 和预置 prompt。
  - 环境变量只在 Agent 或 Tool 等资源声明依赖时补充，敏感值进入 Secret Service。
- Agent 会话能力，支持统一开始作业入口、手工 Agent 会话创建、继续、询问回答、执行历史和详情。
- AgentFlow 能力，支持只编排 Agent 任务的结构化定义、画布确认、测试发布、任务启动、Run 状态机、任务内审阅、取消、重试和 Agent 会话关联。
- 事件源能力，R-0630 首版对接公司 eDevOps 系统的 Feature/需求项，支持同步、去重、输入映射、触发目标、并发上限和事件执行看板。
- 事件执行看板按 backlog、agent working、waiting approval、done 聚合事件触发任务，并跳转 Agent 会话详情或 AgentFlow 任务详情。

## 不做范围

- 外部 agent core 自身的模型调度、运行沙箱、内部状态机和任务队列实现。
- 远程代码仓平台的账号体系、凭据申请和仓库权限审批。
- 从代码仓、`.codex`、`.claudecode`、`.opencode` 或任意历史目录同步 Harness 配置。
- 在 AgentSpace 内新建 Skill、从市场选择 Agent、配置 MCP 连接参数、配置脚本型自动化或任意 Shell 执行。
- 将 AgentFlow 定义作为 prompt 交给 Agent 自由执行流程。
- eDevOps 系统自身的 Feature 创建、流转、审批、状态主数据治理和账号权限体系。
- 任意自定义脚本型事件采集、无结构爬虫、外部系统双向写回和实时 webhook 网关。
- 在事件执行看板内重新实现 Agent 会话详情或 AgentFlow 任务详情。

## 用户任务

| 任务 | 角色 | 用户价值 | 产品行为 |
| --- | --- | --- | --- |
| 创建零配置空间 | 团队创建者 | 立即开始使用 Agent | 不配置知识库或 Harness，直接使用平台默认 Agent。 |
| 配置知识库 | 团队管理员 | 建立 Agent 可感知的知识结构 | 支持业务资料和可选远程代码仓，不限定开发项目。 |
| 配置 AGENT.md | 团队管理员 | 维护长期指引 | 以 Markdown 编辑和发布空间级或知识域级指引。 |
| 选择 Skill | 团队管理员 | 引入可复用专业能力 | 只能从公司 AI 市场选择已发布 Skill。 |
| 添加 Tool | 团队管理员 | 复用公司统一治理的外部能力 | 只能从公司 AI 市场添加已发布 Tool，不配置 MCP 参数。 |
| 配置 Agent | 团队管理员 | 构建空间执行主体 | 在线配置名称、描述、Skill、Tool 和预置 prompt。 |
| 配置 AgentFlow | 团队管理员 | 沉淀标准作业流程 | 在独立 AgentFlow 中心通过画布确认后发布。 |
| 开始作业 | 团队成员 | 输入任务并选择执行方式 | 同一入口可选择 Agent 或 AgentFlow。 |
| 启动 AgentFlow 任务 | 团队成员 | 按标准流程完成复杂工作 | 选择已发布 AgentFlow，任务独立展示，每个 Agent 任务自动创建 Agent 会话。 |
| 配置事件源 | 团队管理员 | 自动接入外部需求项 | R-0630 首版接入 eDevOps FE，按规则同步和触发。 |
| 查看事件执行看板 | 空间用户 | 跟踪事件触发任务状态 | 看板按 backlog、agent working、waiting approval、done 展示。 |
| 查看执行详情 | 空间用户 | 深入查看处理过程 | Agent 任务进入 F-006，AgentFlow 任务进入 F-008。 |

## 信息架构

- 团队空间入口：空间概览、成员、知识库、Harness 基础配置、AgentFlow、Agent 会话、事件源、事件执行看板。
- 统一开始作业入口：用户输入任务后选择 Agent 或 AgentFlow，分别进入手工 Agent 会话或 AgentFlowTask。
- 事件驱动作业入口：管理员配置 eDevOps FE 事件源后，外部 Feature/需求项按计划同步进入 backlog，并自动触发 Agent 会话或 AgentFlow 任务。
- Agent 会话历史：展示 `manual`、`agent_flow_node` 和 `event_source` 来源的 Agent 会话。
- AgentFlow 任务历史：展示用户启动或事件源触发的 AgentFlowTask 与 AgentFlowRun。

## 关键规则

- 零配置优先：所有 Harness 基础配置中心为空时，空间仍能使用平台默认 Agent 创建会话。
- 依赖驱动配置：缺失依赖只阻止引用该依赖的资源发布或运行，不影响空间和其他资源。
- Skill 市场化：用户只能选择市场 Skill；市场下线、无权限或版本不兼容时阻止选择或发布相关 Agent。
- Tool 市场化：AgentSpace 不保存 Tool 连接地址、启动命令、认证参数或 MCP 配置。
- Agent 在线配置：Agent 不从市场选择，只在空间内维护名称、描述、Skill、Tool 和预置 prompt。
- AgentFlow 确定性执行：AgentFlowEngine 只根据持久化任务依赖、任务状态和已确认输出调度，不调用模型决定下一步。
- 事件源首版边界：R-0630 只对接 eDevOps FE，需求系统、缺陷系统等其他事件源作为后续扩展。
- 看板状态一致性：事件执行看板状态必须由关联 AgentSession 或 AgentFlowTask 状态映射得到，不允许看板与原子执行对象出现互相矛盾的终态。

## 文件与快照

```text
/AGENT.md
/<knowledge-domain>/AGENT.md
/.agentspace/
  manifest.yaml
  agents/
  skills.yaml
  tools.yaml
  env.yaml
```

- Portal 保存发布元数据和资源引用，不从远程代码仓同步 Harness 配置。
- 版本快照固定本次运行所使用的 AGENT.md、Agent、Skill、Tool、AgentFlow 和环境变量引用版本。
- 已启动 Agent 会话或 AgentFlowRun 不受后续配置发布影响。

## 风险与待确认

| 类别 | 状态 | 说明 |
| --- | --- | --- |
| 产品 | 待确认 | 需确认 eDevOps FE 首批字段白名单、事件类型和 waiting approval 完成确认入口。 |
| 技术 | 待确认 | 需确认 AgentSession、AgentFlowEngine、Tool 市场契约、Secret Service、eDevOps 适配器、同步水位和事件调度模型。 |
| 测试 | 待确认 | 需覆盖零配置、分层 AGENT.md、市场 Skill/Tool、在线 Agent、AgentFlow、事件源同步、状态映射和权限。 |
| 运维 | 待确认 | 需确认 AI 市场、Secret Service、AgentFlowEngine、外部 agent core、eDevOps 适配器和事件流稳定性。 |
