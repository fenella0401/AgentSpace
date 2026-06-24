# AgentSpace 产品定义

## 1. 基本信息

| 字段 | 内容 |
| --- | --- |
| 产品名称 | AgentSpace |
| 最近更新 | 2026-06-24 |
| 文档状态 | 产品定义草案 |
| 文档职责 | 定义产品总体定位、产品原则、核心概念、跨模块规则、产品边界和630版本范围，并作为各功能模块文档的入口 |
| 不包含 | 页面布局、字段级规则、操作流程、异常反馈等具体交互；此类内容由对应功能模块文档定义 |
| 本次变更 | 以 modules 中各模块文档为基准刷新总纲；压缩入口文档细节；统一 630版本口径 |
| 安全机制 | 本文档及功能模块文档写入时需要产品经理人工确认 |

---

## 2. 产品定位

AgentSpace 是面向公司内部团队的 Agent 工作空间产品。

团队可以在 AgentSpace 中创建团队空间，配置任务触发规则、Agent 能力和 Agent 协作方式；系统根据事件、定时计划或人工指令，触发单 Agent 或 AgentFlow 执行。

人在 Agent 执行过程中主要承担监管、审查、审批和干预职责，在关键节点实现 Human in the loop。系统通过任务记录、实例看板、文件变更和审计链路，帮助团队追踪 Agent 工作过程和最终交付结果。

> AgentSpace 是团队级 Agent 工作空间，用于配置、触发、监管和追踪 Agent 协同完成团队任务的全过程。

AgentSpace 先以需求开发、架构设计看护、代码变更、测试集成和发布状态追踪等软件生产场景打样，长期可扩展到其他团队任务场景。

---

## 3. 产品目标与原则

### 3.1 产品目标

1. 让团队围绕项目、业务场景或长期任务域创建独立 Agent 工作空间。
2. 让团队管理员配置 Agent 能力、Agent 角色、AgentFlow、知识和环境变量。
3. 让团队成员通过事件任务、定时任务或临时任务触发 Agent 工作。
4. 让复杂任务通过 AgentFlow 编排多个 Agent 角色进行长程处理。
5. 让人在关键节点查看、审批、驳回、补充信息、审查文件变更或终止执行。
6. 让团队通过任务记录和实例看板查看任务进度、运行链路和执行产物。
7. 让所有 Agent 执行过程具备可追踪、可审计、可复盘能力。
8. 让公司基于系统级节点和 PDU 部门节点沉淀可发布、可浏览、可复用的节点模板。
9. 让节点管理员基于已发布模板版本创建 Extension，并发布到本人具备资格的 AgentCenter 市场组织。

### 3.2 产品原则

1. **团队空间隔离**：配置、运行记录、权限和审计均归属于明确的团队空间。
2. **配置与运行分离**：长期规则以配置态对象管理，每次实际执行以运行态对象记录。
3. **运行快照固化**：运行开始后固化所使用的 Agent、模型、能力、知识和 AgentFlow 配置，后续配置变更不改写历史。
4. **人可介入**：关键节点必须能够等待明确的处理人完成审批、补充、审查或终止。
5. **过程可追踪**：任务来源、执行过程、状态变化、人工操作和产物均可回溯。
6. **权限以后端为准**：前端隐藏、禁用或只读仅用于改善体验，不能代替后端鉴权。
7. **外部系统保持权威**：AgentSpace 消费外部资源并展示执行结果，不替代 Codehub、AgentCenter、AgentCore、PDU、DevUC 等源系统。
8. **630版本保持清晰边界**：优先支持单 Harness、顺序 AgentFlow、明确任务入口和统一状态机，不提前引入复杂编排能力。
9. **组织主数据只读**：PDU 是部门节点的权威来源，AgentSpace 只读取节点并关联模板和管理员，不修改组织树。

---

## 4. 功能模块导航

| 模块 | 产品定位 | 文档 |
| --- | --- | --- |
| 任务记录 | 统一查看 Agent Run 和 AgentFlow Run，进入执行详情、处理人工交互，并发起临时任务 | [任务记录](./modules/task-records.md) |
| 事件任务 | 配置事件触发规则，并通过实例看板管理事件命中的 Task Run | [事件任务](./modules/event-tasks.md) |
| 定时任务 | 配置调度规则，并查看和处理每次调度产生的 Task Run | [定时任务](./modules/scheduled-tasks.md) |
| Harness 工程配置 | 配置团队空间内 Agent 可使用的能力、角色、流程、知识和环境变量 | [Harness 工程配置](./modules/harness-configuration.md) |
| 团队空间与成员 | 创建、进入和切换团队空间，维护空间成员和角色权限 | [团队空间与成员](./modules/team-space-and-members.md) |
| AgentSpace 后端管理面 | 基于系统级节点和 PDU 部门节点浏览、维护和发布节点模板及 Extension | [AgentSpace 后端管理面](./modules/backend-management.md) |

模块边界：

1. AgentFlow 的定义和发布属于 Harness；AgentFlow Run 的查看和交互属于任务记录。
2. 事件任务和定时任务管理长期配置及其 Task Run；底层 Agent Run 详情统一跳转任务记录。
3. 临时任务只在任务记录模块发起和查看，不进入事件任务或定时任务配置清单。
4. 节点模板和 Extension 属于后端管理面，不等同于团队空间 Harness，也不自动应用到团队空间。
5. 团队空间不设置上级归属容器，空间治理只基于团队空间成员角色和只读的所属部门元数据。

---

## 5. 目标用户与角色

| 角色 | 产品级职责摘要 | 详细规则 |
| --- | --- | --- |
| 系统管理员 | 管理平台级治理能力；在后端管理面拥有系统级节点能力，并可维护 PDU 部门节点成员授权 | [后端管理面](./modules/backend-management.md) |
| 节点管理员 | 在授权节点及允许的直属下级节点内维护成员、模板和 Extension | [后端管理面](./modules/backend-management.md) |
| 团队创建者 | 创建团队空间，并默认拥有团队管理员和团队成员能力 | [团队空间与成员](./modules/team-space-and-members.md) |
| 团队管理员 | 管理团队空间成员、Harness、任务配置和重要执行结果 | [团队空间与成员](./modules/team-space-and-members.md) |
| 团队成员 | 发起临时任务，使用已发布能力，查看并处理被授权的任务实例 | [任务记录](./modules/task-records.md) |
| 访客 | 只读查看被授权的团队空间内容和执行记录 | [团队空间与成员](./modules/team-space-and-members.md) |

系统管理员和节点管理员不因该身份自动获得团队空间权限。进入团队空间后的权限只由有效的团队空间角色决定。

---

## 6. 核心概念

### 6.1 团队空间

团队空间是 AgentSpace 的基本工作单元，通常对应一个团队、项目、业务场景或长期任务域。

团队空间独立创建；创建时系统自动记录创建者所属最子部门作为所属部门元数据，630版本仅展示，不允许用户修改。空间内包含成员与权限、Harness、任务配置、任务运行记录、看板和文件变更记录。

详细规则见[团队空间与成员](./modules/team-space-and-members.md)。

### 6.2 Harness

Harness 是团队空间内用于约束和驱动 Agent 执行的一组工程化配置，包含 Skill、MCP、Agent 角色、AgentFlow、知识库和环境变量。

630版本每个团队空间默认拥有一个全局 Harness。能力中心是 Skill 和 MCP 的内部业务分组，前端导航以 Skill、MCP、Agent 角色、AgentFlow、知识库、环境变量为入口。

详细规则见[Harness 工程配置](./modules/harness-configuration.md)。

### 6.3 Agent 角色与 Agent Run

Agent 角色定义 Agent 的身份、能力范围、Prompt 和运行约束。团队空间创建后系统生成默认 Agent，团队管理员可以按需创建自定义 Agent 角色。

Agent Run 是单个 Agent 的一次实际执行，记录输入、运行配置快照、过程消息、工具调用、状态变化、人工交互、输出结果和文件变更。

运行查看和交互见[任务记录](./modules/task-records.md)。

### 6.4 AgentFlow 与 AgentFlow Run

AgentFlow 是由 Flow、Stage 和 Step 组成的顺序 Agent 协作流程。Flow 定义流程，Stage 用于逻辑分组，Step 是实际执行单元并指定执行 Agent。

630版本 AgentFlow 按 Stage 和 Step 配置顺序执行，不支持并行、循环、条件分支和子流程。AgentFlow Run 是 AgentFlow 的一次运行实例，其中 Step Run 可以启动 Agent Run。

配置规则见[Harness 工程配置](./modules/harness-configuration.md)，运行查看见[任务记录](./modules/task-records.md)。

### 6.5 任务配置与 Task Run

任务配置描述一类任务如何被长期触发和执行，包括事件任务配置和定时任务配置。临时任务不是长期配置，而是团队成员即时发起的一次运行。

Task Run 是一次具体任务实例，来源类型包括：

| 来源类型 | 含义 | 模块 |
| --- | --- | --- |
| `event` | 事件命中配置后生成的任务实例 | [事件任务](./modules/event-tasks.md) |
| `scheduled` | 到达调度时间后生成的任务实例 | [定时任务](./modules/scheduled-tasks.md) |
| `temporary` | 团队成员即时发起的临时任务 | [任务记录](./modules/task-records.md) |

Task Run 关联一个单 Agent Run 或一个 AgentFlow Run。

### 6.6 Human in the loop

Human in the loop 是 AgentSpace 的核心协作机制。人在执行过程中可以查看过程与产物、输入指令、补充信息、审查文件变更、确认准出、驳回或终止执行。

具体处理入口和权限规则见[任务记录](./modules/task-records.md)、[事件任务](./modules/event-tasks.md)和[定时任务](./modules/scheduled-tasks.md)。

### 6.7 管理域与管理节点

后端管理面通过管理域区分系统级节点树和 PDU 部门节点树。

管理节点是系统级节点和 PDU 部门节点在后端管理面中的统一抽象。系统级节点由 AgentSpace 定义；PDU 部门节点由 PDU 提供，AgentSpace 只读展示并关联模板、Extension、管理员授权和审计记录。

详细规则见[AgentSpace 后端管理面](./modules/backend-management.md)。

### 6.8 节点模板

节点模板是独立于团队空间 Harness 的主数据对象。一个管理节点可以创建多个节点模板，不同模板相互隔离。

节点模板仅包含 Skill、MCP、Agent 角色和 AgentFlow。模板发布后生成不可变的已发布版本，供全员只读查看、供 Extension 绑定，并为后续团队空间初始化预留可追溯来源。

630版本节点模板不在新建团队空间时复用，也不会自动影响已有团队空间 Harness。

### 6.9 Extension

Extension 是基于管理节点的插件定义，归属于当前管理节点，不归属于某个模板草稿。

每个 Extension 必须绑定当前节点下某个已发布模板版本，并从该版本中选择一个或多个 AgentFlow。Extension 发布时，AgentSpace 后端按照 AgentCenter 格式要求，将界面配置的 Harness 内容打包为 AgentRuntime 可识别和使用的 ZIP 文件，并推送到当前用户本人所属且具备资格的 AgentCenter 市场组织。

详细规则见[AgentSpace 后端管理面](./modules/backend-management.md)。

---

## 7. 对象关系

### 7.1 配置态对象

```text
Management Domain
  └── Management Node
        ├── Node Admin Grant
        ├── Node Harness Template
        │     ├── Draft
        │     └── Published Version
        │           ├── Skill
        │           ├── MCP
        │           ├── Agent Role
        │           └── AgentFlow
        └── Extension
              ├── Draft / Published Version
              ├── Package Artifact
              └── AgentCenter Push Record

Team Space
  ├── Department Metadata
  ├── Member
  ├── Harness
  │     ├── Skill
  │     ├── MCP
  │     ├── Agent Role
  │     ├── AgentFlow
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
| 事件任务配置 | 事件任务实例 | 每次命中规则生成一条 Task Run |
| 定时任务配置 | 定时任务实例 | 每次到达调度时间生成一条 Task Run |
| Agent 角色 | Agent Run | Agent Run 启动时固化角色配置快照 |
| AgentFlow | AgentFlow Run | AgentFlow Run 是已发布流程的一次运行 |
| AgentFlow Stage | AgentFlow Step Run 分组 | Stage 是逻辑分组，不产生独立 Agent 执行 |
| AgentFlow Step | AgentFlow Step Run | Step Run 记录本步骤输入、输出、状态和准出结果 |
| Harness | 运行上下文 | 运行启动时读取并固化被引用配置 |

---

## 8. 统一执行状态机

本节定义跨模块共用的运行态基础状态。各模块只定义状态如何在对应页面展示以及允许执行哪些操作。

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

## 9. 外部系统关系

| 系统 | 与 AgentSpace 的关系 |
| --- | --- |
| AgentCore | AgentSpace 调用 AgentCore 云服务启动 Agent Run，并接收执行事件 |
| AgentCenter | AgentSpace 选择已发布版本的 Skill 和 MCP；Extension 发布时推送到用户具备资格的市场组织 |
| PDU | 提供 PDU 部门节点树、节点标识和状态；AgentSpace 不修改 PDU 数据 |
| Codehub | 知识库绑定 Codehub 仓库目录；AgentSpace 展示文件树、变更草稿和 diff |
| eDevOps FE | 630版本事件任务的外部事件源 |
| DevUC | 提供公司统一身份、用户所属部门和权限能力 |
| 密钥管理系统 | 管理环境变量中的敏感凭证，AgentSpace 不存储或回显明文 |

---

## 10. 跨模块一致性规则

1. 所有运行态对象必须关联团队空间。
2. 团队空间创建时必须记录创建者所属最子部门元数据；630版本该元数据仅展示，不允许用户修改。
3. 所有 Agent Run 必须记录触发来源及关联 Task Run。
4. 所有 Agent Run 必须固化启动时使用的 Agent 角色、模型、Skill、MCP、知识库范围、环境变量引用和 AgentFlow 配置。
5. 所有文件变更必须关联到具体 Agent Run。
6. 所有 Waiting Human 状态必须有明确处理人或处理人范围。
7. 所有人工确认、驳回、输入、重试、关闭和终止行为必须记录操作者与时间。
8. 所有任务实例必须能够跳转到关联 Agent Run 或 AgentFlow Run。
9. 所有 Agent Run 必须能够查看运行详情。
10. 所有 AgentFlow Step Run 必须关联所属 Stage，并能够查看输入、输出、产物清单、自动门禁结果、自动重试和人工确认记录。
11. 配置修改不会改写已启动运行的快照；历史运行按启动时配置解释。
12. AgentSpace 不自建独立文件源。Agent 文件变更是对 Codehub 绑定目录的变更草稿或 diff，最终提交到 Codehub。
13. 敏感环境变量不得出现在知识库、Prompt、日志、diff、错误详情、运行快照或前端回填值中。
14. AgentFlow Step 的实际执行输入必须由所选 Agent 的 System Prompt 与 Step Input Prompt 拼接生成；Step 不得覆盖 Agent 的 System Prompt。
15. AgentFlow 自动门禁仅校验指定产物清单是否存在变更；自动门禁通过后才能进入人工确认。
16. 普通用户查看节点模板时只能读取最近成功发布版本，草稿和失败发布不得覆盖公开版本。
17. 节点模板和节点管理员的写操作必须同时校验当前用户权限及节点有效状态。
18. Extension 发布必须基于发布时配置、绑定模板版本和所选 AgentFlow 打包生成 ZIP，并重新校验节点权限和 AgentCenter 市场组织资格；该 ZIP 供用户在其他系统从 AgentCenter 下载插件后配置到 AgentRuntime 中使用。

---

## 11. 630版本范围与非目标

### 11.1 630版本交付范围

1. 团队空间创建、切换、成员管理和空间级角色权限。
2. 单 Harness 配置：Skill、MCP、Agent 角色、AgentFlow、知识库和环境变量。
3. 顺序型 AgentFlow：Flow、Stage、Step、产物清单、自动门禁和人工确认。
4. 事件任务：eDevOps FE 事件源、基础过滤、Agent/AgentFlow 执行、自动或人工启动、实例看板和运行控制。
5. 定时任务：基础周期配置、Agent/AgentFlow 执行、任务执行记录、关闭、重试和持续调度。
6. 任务记录：统一运行清单、详情查看、人工交互、文件变更审阅和临时任务。
7. 后端管理面：系统级节点与 PDU 部门节点分离、全员模板浏览、节点管理员治理、多个节点模板、模板草稿与发布、复制初始化、Extension 保存与发布。

### 11.2 非目标与后续扩展

1. 不做通用个人聊天助手，重点服务团队空间内的任务执行和协作。
2. 不替代完整项目管理系统，看板用于 Agent 任务执行过程追踪。
3. 不替代 Codehub，Codehub 仍是代码和文件的源系统。
4. 不自研底层 AgentRuntime，对接 AgentCore。
5. 不承担 AgentCenter 市场中的 Skill、MCP 发布，以及插件审核、上架、下架和组织管理。
6. 不建设完整数据权限治理平台，只消费团队空间授权结果、节点授权结果和只读 PDU 组织信息。
7. 不维护 PDU 组织树，不支持在 AgentSpace 中新建、编辑、移动或删除部门节点。
8. 不支持多 Harness、复杂 AgentFlow 编排、Harness 版本对比和回滚、跨团队空间协作、节点模板自动应用到团队空间、团队模板市场、执行回放、自动参与人匹配和消息提醒。

---

## 12. 文档维护规则

1. 本文档负责稳定的产品定义、核心概念、跨模块规则和模块入口。
2. 模块文档负责用户可见的功能与交互细节。
3. 跨模块规则变更应先修改本文档，再同步检查受影响模块。
4. 模块内页面、字段或交互调整只修改对应模块文档，避免把细节回填到入口文档。
5. 未经确认的设计使用“待产品确认”明确标识，不作为既定规则。
