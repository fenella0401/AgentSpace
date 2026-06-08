# TC-008-团队空间 AgentFlow 配置与任务运行

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 测试用例编号 | TC-008 |
| 关联功能 | F-008 |
| 关联版本 | R-0630 |
| 优先级 | 高 |
| 测试范围 | AgentFlow 配置发布、从开始作业入口创建任务、Agent 任务状态机、任务内审阅、Agent 会话关联 |

## 前置条件

- 准备创建者、管理员、团队成员和访客账号。
- 准备已发布 Agent、Skill、Tool 和环境变量样例。
- 准备包含“需求分析 -> 开发任务拆解 -> 开发任务执行 -> 集成测试”四个 Agent 任务的 AgentFlow 草稿。
- 每个 Agent 任务均声明目标 Agent、执行指令、输入 Schema 与映射、输出 Schema 与映射、reviewPolicy、retryPolicy、timeout 和 failurePolicy。
- 外部 agent core 支持 Agent 任务执行、询问、任务输出、Agent 内部 Tool 调用和错误模拟。

## 测试步骤

| 步骤 | 操作 | 预期结果 |
| --- | --- | --- |
| 1 | 团队管理员用自然语言生成 AgentFlow 草稿 | 系统只生成结构化草稿，不直接发布或启动 |
| 2 | 在画布中编辑四个 Agent 任务及依赖 | 画布只展示 Agent 任务节点和任务依赖，Skill/Tool 不作为节点 |
| 3 | 尝试新增条件、人工确认、并行、子流程或输出节点 | 系统拒绝非 Agent 任务节点 |
| 4 | 为每个 Agent 任务配置目标 Agent、执行指令、输入输出、审阅策略、失败策略和可重试幂等键 | AgentFlow 草稿保存成功，任务契约和依赖关系可查看 |
| 5 | AgentFlow 存在不可达任务、循环依赖、无终点、缺失输入映射或输出 Schema 不兼容 | 系统阻止测试或发布并定位错误 Agent 任务 |
| 6 | 配置测试输入、Agent 任务 Mock、预期任务状态和输出断言后模拟运行 | 模拟运行通过后 AgentFlow 版本进入测试通过状态 |
| 7 | 查看 diff 并发布 AgentFlow | AgentFlowDefinition 进入已发布状态，可被启动 |
| 8 | 从 AgentFlow 配置完成页点击“运行” | 系统跳转到开始作业入口，并预选当前已发布 AgentFlow |
| 9 | 在开始作业入口输入任务、选择已发布 AgentFlow 并选择 `knowledgeDomainIds` | 系统创建 AgentFlowTask、AgentFlowRun 和固定版本快照，不创建 `source=manual` 的 AgentSession |
| 10 | 执行“需求分析”Agent 任务 | 系统先创建 `source=agent_flow_node` 的 AgentSession，再调用 agent core |
| 11 | 查看“需求分析”Agent 任务对应的 Agent 会话历史和详情 | Agent 会话出现在 F-006 清单中，显示 AgentFlow 来源、任务、Run 和 Agent 任务链接 |
| 12 | “需求分析”Agent 任务输出需求摘要和验收重点 | 输出通过 Schema 校验后保存为该 Agent 任务的已确认输出，并传递给下游 |
| 13 | “开发任务拆解”Agent 任务配置为需要用户审阅 | Agent 任务完成执行后内部进入 `waiting_review`；Agent 任务详情展示待审阅 |
| 14 | 创建人通过“开发任务拆解”的输出审阅 | 保存 AgentTaskReview；该 Agent 任务完成，并解锁“开发任务执行” |
| 15 | 创建人退回输出审阅并重复提交同一幂等键 | 首次提交保存反馈并按重试策略创建新尝试；重复提交返回已有决定 |
| 16 | 多个 Agent 任务同时满足依赖且未超过并发上限 | Engine 可并发调度就绪 Agent 任务；画布和 Run 图仍不出现并行节点 |
| 17 | “集成测试”Agent 任务输出测试结果 | AgentFlowRun 汇总已确认输出并完成 |
| 18 | 取消运行中的 AgentFlow | 不再调度新 Agent 任务，Run 为已取消，已完成副作用不被静默撤销 |
| 19 | 对可重试失败或退回的 Agent 任务发起重试 | 使用配置的幂等策略创建新的 AgentTaskRun attempt 和 AgentSession，并保留重试关系 |
| 20 | 修改已发布 AgentFlow 后查看正在运行的 Run | 当前 Run 继续使用启动时版本 |
| 21 | 重启 AgentFlowEngine 后恢复未完成 Run | 根据持久化状态继续可运行 Agent 任务，不重复已完成任务 |
| 22 | 团队成员使用 AgentFlow，流程内部尝试越权 Tool | 后端按当前用户权限拒绝，AgentFlow 不能借配置越权 |
| 23 | 访客在开始作业入口选择 AgentFlow、处理 Agent 任务审阅、取消或重试 | 后端拒绝请求 |
| 24 | 非 AgentFlowTask 创建人处理 Agent 任务审阅、取消或重试 | 后端拒绝请求 |
| 25 | 在开始作业入口选择已停用、依赖失效或无权访问的 AgentFlow | 系统阻止启动并保留输入草稿 |
| 26 | 提交不存在、跨空间或无权访问的 `knowledgeDomainIds` | 系统阻止启动并定位无效知识域 |
| 27 | AgentFlow 的 Agent 任务、agent core 或事件流失败 | 保存 Agent 任务、Run 和任务错误状态，服务恢复后记录可查询 |
| 28 | 从 AgentFlow 任务详情点击 Agent 任务会话入口 | 跳转到对应 Agent 会话详情；会话详情不展示继续或改名控件 |

## 验收结论

- 待执行

## 备注

- 联调前需补充 AgentFlow 超时阈值、agent core 任务执行样例、Agent 内部 Tool 调用幂等契约、AgentTaskReview 决策契约和错误码。
