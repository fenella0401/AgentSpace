# TC-008-团队空间 Harness CICD 流水线配置与任务运行

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 测试用例编号 | TC-008 |
| 关联功能 | F-008 |
| 关联版本 | R-0630 |
| 优先级 | 高 |
| 测试范围 | Harness CICD 流水线配置发布、Harness CICD 流水线任务运行、Run 状态机、节点 Agent 会话关联和 Hook 联动 |

## 前置条件

- 准备创建者、管理员、团队成员和访客账号。
- 准备已发布 Agent、Skill、Tool、Hook 和环境变量样例。
- 准备包含产品 Agent、开发 Agent、运营 Agent、条件、人工确认、并行、子流水线和输出节点的 Harness CICD 流水线草稿。
- 外部 agent core 支持 Agent 节点执行、询问、Agent 内部 Tool 调用前置事件和错误模拟。
- Hook Engine 支持 Harness CICD 流水线任务开始、节点执行、任务完成、任务失败和 Hook 启动 Harness CICD 流水线测试事件。

## 测试步骤

| 步骤 | 操作 | 预期结果 |
| --- | --- | --- |
| 1 | 团队管理员用自然语言生成 Harness CICD 流水线草稿 | 系统只生成结构化草稿，不直接发布或启动 |
| 2 | 在画布中编辑 Agent、条件、人工确认、并行、子流水线和输出节点 | 画布只展示支持的用户侧节点，Skill/Tool 不作为 Harness CICD 流水线节点 |
| 3 | 配置输入输出、节点依赖、失败策略和可重试 Agent 节点幂等键 | Harness CICD 流水线草稿保存成功，依赖关系可查看 |
| 4 | Harness CICD 流水线存在不可达节点、无终点、无界循环或缺失输入映射 | 系统阻止测试或发布并定位错误节点 |
| 5 | 配置测试输入、Mock、预期分支、预期节点状态和输出断言后模拟运行 | 模拟运行通过后 Harness CICD 流水线版本进入测试通过状态 |
| 6 | 查看 diff 并发布 Harness CICD 流水线 | HarnessPipelineDefinition 进入已发布状态，可被启动 |
| 7 | 团队成员启动已发布 Harness CICD 流水线并选择 `knowledgeDomainIds` | 系统创建 HarnessPipelineTask、HarnessPipelineRun 和固定版本快照 |
| 8 | Harness CICD 流水线任务开始 Hook 返回允许 | HarnessPipelineEngine 调度首个节点 |
| 9 | Harness CICD 流水线任务开始 Hook 返回阻止 | 不调度节点，不调用 agent core，Harness CICD 流水线任务展示阻止原因 |
| 10 | 执行产品 Agent 节点 | 系统先创建 `source=harness_pipeline_node` 的 AgentSession，再调用 agent core |
| 11 | 查看产品 Agent 节点对应的 Agent 会话历史和详情 | Agent 会话出现在 F-006 清单中，显示 Harness CICD 流水线来源、任务、Run 和节点链接 |
| 12 | Agent 节点执行过程中调用 Skill 和 Tool | Skill/Tool 作为 Agent 能力执行；Tool 调用前完成 Hook 决策并保存结果 |
| 13 | 执行条件节点 | Engine 按结构化表达式选择唯一分支，不调用模型决定流程 |
| 14 | 执行并行节点 | 各分支独立运行，并按配置汇总成功、快速失败或部分成功 |
| 15 | 执行人工确认节点 | HarnessPipelineRun 进入等待确认，Harness CICD 流水线任务详情展示确认事项和选项 |
| 16 | 创建人批准、拒绝和重复提交同一确认幂等键 | 流程进入对应分支；重复提交不重复推进 |
| 17 | 执行子流水线 | 创建关联 Run，调用链深度正确增加 |
| 18 | 取消运行中的 Harness CICD 流水线 | 不再调度新节点，Run 为已取消，已完成副作用不被静默撤销 |
| 19 | 对可重试失败 Agent 节点发起重试 | 使用配置的幂等策略，不产生重复外部副作用，并保留重试关系 |
| 20 | 修改已发布 Harness CICD 流水线后查看正在运行的 Run | 当前 Run 继续使用启动时版本 |
| 21 | 重启 HarnessPipelineEngine 后恢复未完成 Run | 根据持久化状态继续可运行节点，不重复已完成节点 |
| 22 | Hook 动作启动已发布 Harness CICD 流水线 | 系统创建来源为 Hook 的 HarnessPipelineTask 和 HarnessPipelineRun，并记录调用链 |
| 23 | 构造 Hook、Harness CICD 流水线和子流水线组合调用超过 5 层 | 系统终止新调用并保存完整调用链 |
| 24 | 团队成员使用 Harness CICD 流水线，Harness CICD 流水线内部尝试越权 Tool | 后端按当前用户权限拒绝，Harness CICD 流水线不能借配置越权 |
| 25 | 访客启动 Harness CICD 流水线、处理确认、取消或重试 | 后端拒绝请求 |
| 26 | 非 Harness CICD 流水线任务创建人处理确认、取消或重试 | 后端拒绝请求 |
| 27 | 选择已停用、依赖失效或无权访问的 Harness CICD 流水线启动 | 系统阻止启动并保留输入草稿 |
| 28 | 提交不存在、跨空间或无权访问的 `knowledgeDomainIds` | 系统阻止启动并定位无效知识域 |
| 29 | Harness CICD 流水线的 Agent 节点、agent core 或事件流失败 | 保存节点、Run 和任务错误状态，服务恢复后记录可查询 |
| 30 | 从 Harness CICD 流水线任务详情点击节点 Agent 会话入口 | 跳转到对应 Agent 会话详情；会话详情不展示继续或改名控件 |

## 验收结论

- 待执行

## 备注

- 联调前需补充 Harness CICD 流水线/Hook 超时阈值、agent core 前置事件样例、Agent 内部 Tool 调用幂等契约和错误码。
