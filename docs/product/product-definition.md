# AgentSpace 产品定义

## 1. 基本信息

| 字段 | 内容 |
| --- | --- |
| 产品名称 | AgentSpace |
| 最近更新 | 2026-06-22 |
| 文档状态 | 产品定义草案 |
| 文档职责 | 定义产品总体定位、产品原则、核心概念、跨模块规则、产品边界和首期范围，并作为各功能模块文档的入口 |
| 不包含 | 页面布局、字段级规则、操作流程、异常反馈等具体交互；此类内容由对应功能模块文档定义 |
| 本次变更 | 在用户侧功能模块之外新增独立的 AgentSpace 后端管理面，补充 PDU 节点模板、节点管理员和 Extension 发布能力 |
| 安全机制 | 本文档及功能模块文档写入时需要产品经理人工确认 |

---

## 2. 产品定位

AgentSpace 是面向公司内部团队的 Agent 工作空间产品。

团队可以在 AgentSpace 中创建团队空间，配置任务触发规则和 Agent 协作方式；系统根据事件、定时计划或人工指令，自动触发单 Agent 或 AgentFlow 执行。

人在 Agent 执行过程中主要承担监管、审查、审批和干预职责，在关键节点实现 Human in the loop。系统通过任务看板、执行记录、文件变更和审计链路，帮助团队追踪 Agent 工作过程和最终交付结果。

> AgentSpace 是团队级 Agent 工作空间，用于配置、触发、监管和追踪 Agent 协同完成团队任务的全过程。

AgentSpace 首先以需求开发、架构设计看护、代码变更、测试集成和发布状态追踪等软件生产场景打样，长期可扩展到其他团队任务场景。

---

## 3. 产品目标与价值

### 3.1 产品目标

1. 让团队围绕具体项目或业务场景创建独立的 Agent 工作空间。
2. 让团队管理员配置任务触发方式、Agent 能力和 Agent 协作流程。
3. 让团队成员通过事件任务、定时任务或临时任务触发 Agent 工作。
4. 让 Agent 基于团队空间内授权的 Skill、MCP、知识库和环境变量执行任务。
5. 让复杂任务通过 AgentFlow 编排多个 Agent 角色进行长程处理。
6. 让人在关键节点查看、审批、驳回、补充信息、修改指令或终止执行。
7. 让团队通过看板查看任务进度、Agent 运行记录、AgentFlow 链路和执行产物。
8. 让所有 Agent 执行过程具备可追踪、可审计、可复盘能力。
9. 让公司基于 PDU 组织树沉淀并公开节点级 Harness 模板。
10. 让节点管理员将 AgentFlow 组合为 Extension，并推送到本人所属的 AgentCenter 市场组织。

### 3.2 核心价值

1. 将团队任务配置化。
2. 将 Agent 能力工程化。
3. 将 Agent 执行过程可视化。
4. 将人的审查和干预机制产品化。
5. 将 Agent 产出和文件变更沉淀为可追踪交付件。
6. 将团队与 Agent 的协作变成可治理、可审计、可复盘的工作流。

AgentSpace 的核心不是普通聊天产品，也不是传统项目管理系统，而是帮助团队从“临时使用 Agent”升级为“配置和运营 Agent 工作系统”。

---

## 4. 产品原则

1. **团队空间隔离**：配置、运行记录、权限和审计均归属于明确的团队空间。
2. **配置与运行分离**：长期规则以配置态对象管理，每次实际执行以运行态对象记录。
3. **运行快照固化**：运行开始后固化所使用的 Agent、模型、能力、知识和 AgentFlow 配置，后续配置变更不改写历史。
4. **人可介入**：关键节点必须能够等待明确的处理人完成审批、补充、审查或终止。
5. **过程可追踪**：任务来源、执行过程、状态变化、人工操作和产物均可回溯。
6. **权限以后端为准**：前端隐藏或禁用仅用于改善体验，不能代替后端鉴权。
7. **外部系统保持权威**：AgentSpace 消费外部资源并展示执行结果，不替代 Codehub、AgentCenter、AgentCore 等源系统。
8. **首期保持简单**：优先支持单 Harness、顺序 AgentFlow、明确任务入口和统一状态机，不提前引入复杂编排能力。
9. **组织主数据只读**：PDU 是组织节点的唯一权威来源，AgentSpace 只读取节点并关联模板和管理员，不修改组织树。

---

## 5. 目标用户与角色

| 角色 | 产品级职责摘要 |
| --- | --- |
| 系统管理员 | 创建租户、授予租户管理员、管理平台级事件源和 Agent 运行资源 |
| 系统节点管理员 | 管理系统及一级 PDU 节点模板，维护一级、二级部门节点管理员 |
| 部门节点管理员 | 管理本部门节点模板，并按层级维护直属下级部门节点管理员 |
| 租户管理员 | 治理租户内团队空间清单和租户级资源；不自动拥有具体团队空间权限 |
| 团队创建者 | 创建团队空间，并默认拥有团队管理员和团队成员能力 |
| 团队管理员 | 管理成员、Harness、任务配置及重要执行结果 |
| 团队成员 | 发起临时任务，使用已发布能力，查看并处理被授权的任务实例 |
| 访客 | 只读查看被授权的团队空间内容和执行记录 |

团队空间内的完整权限矩阵和成员管理交互见[团队空间与成员](./modules/team-space-and-members.md)。PDU 节点管理员的授权边界见[AgentSpace 后端管理面](./modules/backend-management.md)。系统节点管理员和部门节点管理员均不因该身份自动获得租户或团队空间权限。

---

## 6. 核心概念

### 6.1 团队空间

团队空间是 AgentSpace 的基本工作单元，通常对应一个团队、项目、业务场景或长期任务域。空间内包含成员与权限、Harness、任务配置、任务运行记录、看板和文件变更记录。

### 6.2 Harness

Harness 是团队空间内用于约束和驱动 Agent 执行的一组工程化配置：

- 能力中心：团队空间已绑定的 Skill 和 MCP。
- Agent 角色：Agent 的身份、能力范围、System Prompt 和 Input Prompt。
- AgentFlow：由 Flow、Stage 和 Step 组成的顺序协作流程。
- 知识库：全局 Agent.md 和绑定的知识目录。
- 环境变量：Agent 执行所需的运行参数和安全引用。

首期前端不展示“能力中心”导航栏目，而是将 Skill 和 MCP 作为与 Agent 角色同级的独立导航入口。Harness 导航顺序为 Skill、MCP、Agent 角色、AgentFlow、知识库、环境变量。

当前阶段每个团队空间默认拥有一个全局 Harness。

### 6.3 任务配置

任务配置描述一类任务如何被长期触发和执行：

- **事件任务配置**：事件命中条件后生成任务实例。
- **定时任务配置**：到达调度时间后生成任务实例。

临时任务不是长期配置，而是团队成员即时发起的一次运行。

### 6.4 Task Run

Task Run 是一次具体的任务实例，来源类型为：

- `event`：事件任务实例。
- `scheduled`：定时任务实例。
- `temporary`：临时任务。

Task Run 关联一个单 Agent Run 或一个 AgentFlow Run。

### 6.5 Agent Run

Agent Run 是单个 Agent 的一次实际执行。它记录输入、运行配置快照、过程消息、工具调用、状态变化、人工交互、输出结果和文件变更。

前端统一使用“Agent”指代可被选择和运行的单 Agent 角色；需要强调运行实例时使用“Agent Run”。

### 6.6 AgentFlow 与 AgentFlow Run

AgentFlow 是由 Flow、Stage 和 Step 组成的顺序 Agent 协作流程：

- Flow 定义名称、启动命令和描述。
- Stage 是逻辑分组；每个 Flow 至少包含一个 Stage，每个 Stage 至少包含一个 Step。
- Step 是实际执行单元，定义执行 Agent、Input Prompt、产物清单和准出机制。

Flow、Stage 和 Step 首期均按配置顺序执行。Stage 不产生独立 Agent 执行；每个 Step 指定所属 Stage。

AgentFlow Run 是 AgentFlow 的一次实际运行实例；其中每个 AgentFlow Step Run 关联所属 Stage，并可以启动一个 Agent Run。首期仅支持顺序流转。

### 6.7 Human in the loop

Human in the loop 是 AgentSpace 的核心协作机制。人在执行过程中可以查看过程与产物、输入指令、补充信息、审查文件变更、确认准出、驳回或终止执行。

### 6.8 PDU 节点 Harness 模板

PDU 节点 Harness 模板是独立于团队空间 Harness 的主数据配置。每个系统或部门节点可以维护一份包含 Skill、MCP、Agent 角色和 AgentFlow 的模板；所有用户可以查看全部节点最近成功发布的模板，只有对应节点管理员可以维护草稿和发布。

节点模板可以在首次初始化时从任意节点的已发布模板一次性复制 Skill、MCP、Agent 角色和 AgentFlow。复制不建立持续继承关系，也不复制 Extension。节点模板当前不自动应用到团队空间。

### 6.9 Extension

Extension 是节点模板内可独立打包和发布的插件定义，由名称、描述、一个或多个 AgentFlow 和 README 组成。AgentSpace 调用打包服务生成 ZIP，并允许节点管理员将当前最新成功打包文件推送至本人所属且具备推送资格的 AgentCenter 市场组织。推送不重新打包；首次推送创建市场插件，重新打包后的后续推送创建新版本。

---

## 7. 对象模型

### 7.1 配置态对象

```text
PDU
  └── PDU Node（系统节点 / 一级至五级部门）
        ├── Node Admin Grant
        └── Node Harness Template
              ├── Skill
              ├── MCP
              ├── Agent Role
              ├── AgentFlow
              └── Extension
                    ├── Package Artifact
                    └── AgentCenter Push Record

Tenant
  └── Team Space
        ├── Member
        ├── Harness
        │     ├── Capability Center
        │     │     ├── Skill
        │     │     └── MCP
        │     ├── Agent Role
        │     ├── AgentFlow
        │     │     └── Stage
        │     │           └── Step
        │     ├── Knowledge
        │     └── Environment Variables
        └── Task Config
              ├── Event Task Config
              └── Scheduled Task Config
```

### 7.2 运行态对象

```text
Task Run
  ├── source_type: event / scheduled / temporary
  ├── Agent Run
  └── AgentFlow Run
        └── AgentFlow Step Run
              └── Agent Run
```

### 7.3 配置态与运行态关系

| 配置态对象 | 运行态对象 | 关系 |
| --- | --- | --- |
| 事件任务配置 | 事件任务实例 | 每次命中规则生成一条实例 |
| 定时任务配置 | 定时任务实例 | 每次到达调度时间生成一条实例 |
| Agent 角色 | Agent Run | Agent Run 启动时固化角色配置快照 |
| AgentFlow | AgentFlow Run | AgentFlow Run 是已发布流程的一次运行 |
| AgentFlow Stage | AgentFlow Step Run 分组 | Stage 是逻辑分组，不产生独立 Agent 执行 |
| AgentFlow Step | AgentFlow Step Run | Step Run 记录本步骤输入、输出、状态和准出结果 |
| Harness | 运行上下文 | 运行启动时读取并固化 Harness 中被引用的配置 |

---

## 8. 统一执行状态机

本节是运行态状态定义的唯一权威来源。各模块只定义状态如何在对应页面展示以及允许执行哪些操作。

### 8.1 基础状态

| 状态 | 含义 |
| --- | --- |
| Pending | 已创建，等待执行 |
| Ready | 已满足执行条件，等待系统调度 |
| Running | Agent 或 AgentFlow 执行中 |
| Waiting Human | 等待人工审批、补充信息或审查 |
| Blocked | 因依赖、权限、输入或外部系统问题被阻塞 |
| Succeeded | 执行成功 |
| Failed | 执行失败 |
| Cancelled | 已取消 |

### 8.2 看板显示状态

| 看板显示 | 对应基础状态 | 使用范围 |
| --- | --- | --- |
| TODO | Pending 或 Ready | 事件任务看板 |
| Running | Running；定时任务中等待系统调度的 Ready 也在该列标记展示 | 全部运行看板 |
| Waiting Approval | Waiting Human | 全部运行看板 |
| Blocked | Blocked | 事件任务和定时任务看板 |
| Succeeded | Succeeded | 事件任务和定时任务看板 |
| Failed | Failed | 全部运行看板 |
| Cancelled | Cancelled | 事件任务和定时任务看板 |
| Done | Succeeded，或由用户结束会话产生的 Cancelled | 任务记录 |

---

## 9. 功能模块导航

| 模块 | 用户目标 | 文档 |
| --- | --- | --- |
| 任务记录 | 查看所有 Agent/AgentFlow 运行、进入执行详情、处理人工交互并发起临时任务 | [任务记录](./modules/task-records.md) |
| 事件任务 | 配置事件触发规则并通过看板管理事件任务实例 | [事件任务](./modules/event-tasks.md) |
| 定时任务 | 配置调度规则并通过看板管理定时任务实例 | [定时任务](./modules/scheduled-tasks.md) |
| Harness 工程配置 | 配置 Agent 可使用的能力、知识、变量、角色和协作流 | [Harness 工程配置](./modules/harness-configuration.md) |
| 团队空间与成员 | 创建和切换团队空间，维护成员与空间级访问权限 | [团队空间与成员](./modules/team-space-and-members.md) |
| AgentSpace 后端管理面 | 基于 PDU 树查看和治理节点模板、节点管理员及 Extension 发布 | [AgentSpace 后端管理面](./modules/backend-management.md) |

模块边界：

- AgentFlow 的定义和发布属于 Harness；AgentFlow Run 的查看和交互属于任务记录。
- 事件任务和定时任务文档管理长期配置及其 Task Run；底层 Agent Run 详情统一跳转任务记录。
- 临时任务只在任务记录模块发起和查看，不进入事件任务或定时任务配置清单。
- PDU 节点模板和 Extension 属于独立后端管理面，不等同于团队空间 Harness，也不自动应用到团队空间。
- 平台级租户治理仍保留为产品角色背景，不在本轮扩展为独立模块。

---

## 10. 外部系统关系

| 系统 | 与 AgentSpace 的关系 |
| --- | --- |
| AgentCore | AgentSpace 调用 AgentCore 云服务启动 Agent Run 并接收执行事件 |
| AgentCenter | AgentSpace 选择已发布版本的 Skill 和 MCP；节点管理员可将 Extension ZIP 推送到本人所属的市场组织 |
| PDU | 提供系统节点及一级至五级部门的权威组织树、节点标识和状态；AgentSpace 不修改 PDU 数据 |
| Extension 打包服务 | 根据 Extension 最新配置、AgentFlow 清单和 README 生成 ZIP 文件 |
| Codehub | 知识库绑定 Codehub 仓库目录；AgentSpace 展示文件树、变更草稿和 diff |
| eDevOps FE | 首期事件任务的外部事件源 |
| DevUC | 提供公司统一身份、租户资格和权限能力 |
| 密钥管理系统 | 管理环境变量中的敏感凭证，AgentSpace 不存储或回显明文 |

---

## 11. 跨模块一致性规则

1. 所有运行态对象必须关联团队空间。
2. 所有 Agent Run 必须记录触发来源及关联 Task Run。
3. 所有 Agent Run 必须固化启动时使用的 Agent 角色、模型、Skill、MCP、知识库范围、环境变量引用和 AgentFlow 配置。
4. 所有文件变更必须关联到具体 Agent Run。
5. 所有 Waiting Human 状态必须有明确处理人或处理人范围。
6. 所有人工确认、驳回、输入、重试、关闭和终止行为必须记录操作者与时间。
7. 所有任务实例必须能够跳转到关联 Agent Run 或 AgentFlow Run。
8. 所有 Agent Run 必须能够查看运行详情。
9. 所有 AgentFlow Step Run 必须关联所属 Stage，并能够查看输入、输出、产物清单、自动门禁结果、自动重试和人工确认记录。
10. 配置修改不会改写已启动运行的快照；历史运行按启动时配置解释。
11. AgentSpace 不自建独立文件源。Agent 文件变更是对 Codehub 绑定目录的变更草稿或 diff，最终提交到 Codehub。
12. 敏感环境变量不得出现在知识库、Prompt、日志、diff、错误详情、运行快照或前端回填值中。
13. AgentFlow Step 的实际执行输入必须由所选 Agent 的 System Prompt 与 Step Input Prompt 拼接生成；Step 不得覆盖 Agent 的 System Prompt。
14. AgentFlow 自动门禁仅校验指定产物清单是否存在变更；自动门禁通过后才能进入人工确认。
15. 普通用户查看节点模板时只能读取最近成功发布版本，草稿和失败发布不得覆盖公开版本。
16. 节点模板和节点管理员的写操作必须同时校验当前用户权限及 PDU 节点有效状态。
17. Extension 打包必须基于打包时的最新配置生成 README 和 ZIP；每次成功重新打包将当前产物重置为未推送。推送只发送当前最新成功打包文件，不重新打包，并重新校验当前用户的节点权限与 AgentCenter 市场组织资格。

---

## 12. 典型场景

### 12.1 产品需求自动开发

eDevOps FE 中新增目标迭代需求后，事件任务触发需求开发 AgentFlow，依次完成需求分析、架构检查、代码修改和变更审查，并在关键节点等待人工确认。

### 12.2 架构文档变更审查

知识库中的架构文档发生修改后，事件任务触发审查 Agent，分析影响并生成调整计划，经架构师确认后更新文档并提交 Codehub。

### 12.3 定时运营分析

每天上午 9 点，定时任务触发 Data Agent 拉取昨日数据、生成分析报告并推送给相关成员。

### 12.4 临时团队协作

团队成员新建临时任务，请 Agent 基于团队知识库分析问题。成员可以持续追问、审查文件变更或要求将结果保存到知识库。

### 12.5 节点模板与 Extension 发布

部门节点管理员从公司级已发布模板复制初始化本部门的 Skill、MCP、Agent 和 AgentFlow，完成部门适配后发布供全员查看；随后选择多个 AgentFlow 创建 Extension，先生成 ZIP，再将当前最新打包文件推送到本人所属的 AgentCenter 市场组织。

---

## 13. 产品边界与非目标

1. 不做通用个人聊天助手，重点服务团队空间内的任务执行和协作。
2. 不替代完整项目管理系统，看板用于 Agent 任务执行过程追踪。
3. 不替代 Codehub，Codehub 仍是代码和文件的源系统。
4. 不自研底层 AgentRuntime，对接 AgentCore。
5. 不承担 AgentCenter 市场中的 Skill、MCP 发布，以及插件审核、上架、下架和组织管理；Extension 创建及新版本推送通过 AgentCenter 能力完成。
6. 不建设完整数据权限治理平台，只消费租户和团队空间授权结果。
7. 不直接面向外部客户开放，仅面向公司内部员工。
8. 不维护 PDU 组织树，不支持在 AgentSpace 中新建、编辑、移动或删除部门节点。

---

## 14. 首期范围

### 14.1 首期交付

1. 创建和管理团队空间及成员权限。
2. 配置团队空间能力中心和 Agent 角色。
3. 配置简单顺序型 AgentFlow。
4. 配置知识库和环境变量。
5. 配置 eDevOps FE 事件任务。
6. 配置定时任务。
7. 发起临时任务。
8. 查看 Agent Run 和 AgentFlow Run。
9. 支持 Waiting Human 审批、补充与审查。
10. 支持文件变更展示和人工审查。
11. 提供事件任务、定时任务和任务记录三类运行视图。
12. 提供 PDU 系统节点及一级至五级部门的全量只读视图，并支持节点管理员逐级治理。
13. 支持节点模板草稿、发布和首次复制初始化，配置 Skill、MCP、Agent 角色和 AgentFlow。
14. 支持一个节点配置多个 Extension，生成 ZIP 并推送到用户所属的 AgentCenter 市场组织。

### 14.2 暂缓能力

1. 一个团队空间配置多个 Harness。
2. AgentFlow 并行、循环、条件分支和子流程。
3. 自定义看板状态和高级权限策略。
4. Harness 历史版本对比和回滚。
5. 跨团队空间协作、将 PDU 节点模板应用到团队空间，以及团队模板市场。
6. 执行回放和当前轮次文件修改回退。
7. 人工介入超时、自动匹配参与人和消息提醒。
8. 更多事件源和团队空间内部资源事件。
9. 自动生成项目统计和开发运营看板。

---

## 15. 文档维护规则

- 本文档负责稳定的产品定义和跨模块规则。
- 模块文档负责用户可见的功能与交互细节。
- 跨模块规则变更应先修改本文档，再同步检查受影响模块。
- 模块内页面或字段调整只修改对应模块文档，避免把页面细节回填到入口文档。
- 未经确认的设计使用“待产品确认”明确标识，不作为既定规则。
