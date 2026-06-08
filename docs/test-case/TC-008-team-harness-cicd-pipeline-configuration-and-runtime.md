# TC-008-团队空间 Harness CICD 流水线配置与任务运行

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 测试用例编号 | TC-008 |
| 关联功能 | F-008 |
| 关联版本 | R-0630 |
| 优先级 | 高 |
| 测试范围 | Harness CICD 流水线配置发布、从开始作业入口创建流水线任务、Agent 任务状态机、任务内审阅、Agent 会话关联和 Hook 联动 |

## 前置条件

- 准备创建者、管理员、团队成员和访客账号。
- 准备已发布 Agent、Skill、Tool、Hook 和环境变量样例。
- 准备包含“需求分析 -> 开发任务拆解 -> 开发任务执行 -> 集成测试”四个 Agent 任务的 Harness CICD 流水线草稿。
- 每个 Agent 任务均声明目标 Agent、执行指令、输入 Schema 与映射、输出 Schema 与映射、reviewPolicy、retryPolicy、timeout 和 failurePolicy。
- 外部 agent core 支持 Agent 任务执行、询问、任务输出、Agent 内部 Tool 调用前置事件和错误模拟。
- Hook Engine 支持 Harness CICD 流水线任务开始、Agent 任务开始、Agent 任务完成、任务完成、任务失败和 Hook 启动 Harness CICD 流水线测试事件。

## 测试步骤

| 步骤 | 操作 | 预期结果 |
| --- | --- | --- |
| 1 | 团队管理员用自然语言生成 Harness CICD 流水线草稿 | 系统只生成结构化草稿，不直接发布或启动 |
| 2 | 在画布中编辑“需求分析 -> 开发任务拆解 -> 开发任务执行 -> 集成测试”四个 Agent 任务 | 画布只展示 Agent 任务节点和任务依赖，Skill/Tool 不作为流水线节点 |
| 3 | 尝试新增条件、人工确认、并行、子流水线或输出节点 | 系统拒绝非 Agent 任务节点，并提示流水线节点仅用于编排 Agent 任务 |
| 4 | 为每个 Agent 任务配置目标 Agent、执行指令、输入输出、审阅策略、失败策略和可重试幂等键 | Harness CICD 流水线草稿保存成功，任务契约和依赖关系可查看 |
| 5 | Harness CICD 流水线存在不可达任务、循环依赖、无终点、缺失输入映射或输出 Schema 不兼容 | 系统阻止测试或发布并定位错误 Agent 任务 |
| 6 | 配置测试输入、Agent 任务 Mock、预期任务状态和输出断言后模拟运行 | 模拟运行通过后 Harness CICD 流水线版本进入测试通过状态 |
| 7 | 查看 diff 并发布 Harness CICD 流水线 | HarnessPipelineDefinition 进入已发布状态，可被启动 |
| 8 | 从 Harness CICD 流水线配置完成页点击“运行流水线” | 系统跳转到新建 Agent 会话处的开始作业入口，并预选当前已发布流水线 |
| 9 | 在开始作业入口输入任务、选择已发布 Harness CICD 流水线并选择 `knowledgeDomainIds` | 系统创建 HarnessPipelineTask、HarnessPipelineRun 和固定版本快照，不创建 `source=manual` 的 AgentSession |
| 10 | Harness CICD 流水线任务开始 Hook 返回允许 | HarnessPipelineEngine 调度首个就绪 Agent 任务 |
| 11 | Harness CICD 流水线任务开始 Hook 返回阻止 | 不调度 Agent 任务，不调用 agent core，流水线任务展示阻止原因 |
| 12 | 执行“需求分析”Agent 任务 | 系统先创建 `source=harness_pipeline_node` 的 AgentSession，再调用 agent core |
| 13 | 查看“需求分析”Agent 任务对应的 Agent 会话历史和详情 | Agent 会话出现在 F-006 清单中，显示 Harness CICD 流水线来源、任务、Run 和 Agent 任务链接 |
| 14 | “需求分析”Agent 任务输出需求摘要和验收重点 | 输出通过 Schema 校验后保存为该 Agent 任务的已确认输出，并传递给“开发任务拆解” |
| 15 | “开发任务拆解”Agent 任务配置为需要用户审阅 | Agent 任务完成执行后内部进入 `waiting_review`；流水线任务和 Agent 任务详情展示待审阅，Run 图节点不显示审阅行为或新增审阅节点 |
| 16 | 创建人通过“开发任务拆解”的输出审阅 | 保存 AgentTaskReview；该 Agent 任务完成，并解锁“开发任务执行” |
| 17 | 创建人退回“开发任务拆解”的输出审阅并重复提交同一幂等键 | 首次提交保存反馈并按重试策略创建新尝试；重复提交返回已有决定，不重复推进状态机 |
| 18 | “开发任务执行”Agent 任务执行过程中调用 Skill 和 Tool | Skill/Tool 作为 Agent 能力执行；Tool 调用前完成 Hook 决策并保存结果 |
| 19 | 多个 Agent 任务同时满足依赖且未超过并发上限 | Engine 可并发调度就绪 Agent 任务；画布和 Run 图仍不出现并行节点 |
| 20 | “集成测试”Agent 任务输出测试结果 | 流水线汇总已确认输出并完成 HarnessPipelineRun |
| 21 | 取消运行中的 Harness CICD 流水线 | 不再调度新 Agent 任务，Run 为已取消，已完成副作用不被静默撤销 |
| 22 | 对可重试失败或退回的 Agent 任务发起重试 | 使用配置的幂等策略创建新的 AgentTaskRun attempt 和 AgentSession，并保留重试关系 |
| 23 | 修改已发布 Harness CICD 流水线后查看正在运行的 Run | 当前 Run 继续使用启动时版本 |
| 24 | 重启 HarnessPipelineEngine 后恢复未完成 Run | 根据持久化状态继续可运行 Agent 任务，不重复已完成任务 |
| 25 | Hook 动作启动已发布 Harness CICD 流水线 | 系统创建来源为 Hook 的 HarnessPipelineTask 和 HarnessPipelineRun，并记录调用链 |
| 26 | 构造 Hook 与 Harness CICD 流水线组合调用超过 5 层 | 系统终止新调用并保存完整调用链 |
| 27 | 团队成员使用 Harness CICD 流水线，流水线内部尝试越权 Tool | 后端按当前用户权限拒绝，流水线不能借配置越权 |
| 28 | 访客在开始作业入口选择 Harness CICD 流水线、处理 Agent 任务审阅、取消或重试 | 后端拒绝请求 |
| 29 | 非 Harness CICD 流水线任务创建人处理 Agent 任务审阅、取消或重试 | 后端拒绝请求 |
| 30 | 在开始作业入口选择已停用、依赖失效或无权访问的 Harness CICD 流水线 | 系统阻止启动并保留输入草稿 |
| 31 | 提交不存在、跨空间或无权访问的 `knowledgeDomainIds` | 系统阻止启动并定位无效知识域 |
| 32 | Harness CICD 流水线的 Agent 任务、agent core 或事件流失败 | 保存 Agent 任务、Run 和任务错误状态，服务恢复后记录可查询 |
| 33 | 从 Harness CICD 流水线任务详情点击 Agent 任务会话入口 | 跳转到对应 Agent 会话详情；会话详情不展示继续或改名控件 |

## 验收结论

- 待执行

## 备注

- 联调前需补充 Harness CICD 流水线/Hook 超时阈值、agent core 前置事件样例、Agent 内部 Tool 调用幂等契约、AgentTaskReview 决策契约和错误码。
