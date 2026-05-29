# Harness 工作流

## Discovery 到 PRD

Prompt：

```text
请为 <feature> 产出 PRD。拉起 product_manager、ux_designer、
solution_engineer、security_reviewer、qa_engineer。
等待全部结果后，输出范围、非目标、用户画像、验收标准、指标、风险与待决策项。
```

期望输出：

- 一页产品摘要。
- 用户旅程与主要页面。
- 需求与非目标。
- 安全、隐私与合规风险。
- 测试与评测策略。

## 架构评审

Prompt：

```text
请评审 <feature> 的候选架构。拉起 system_architect、
backend_engineer、ai_platform_engineer、sre_engineer、security_reviewer。
返回阻塞风险、推荐接口与分阶段实施方案。
```

期望输出：

- 领域边界与数据契约。
- AI 工作流契约与评测计划。
- 可观测性与发布方案。
- 安全与隐私关注点。

## 最小实现切片

Prompt：

```text
请为 <feature> 实现最小垂直切片。按需使用 frontend_engineer、
backend_engineer、ai_platform_engineer。控制变更范围，补充测试，
并报告验证命令。
```

期望输出：

- 聚焦的代码改动。
- 测试或评测。
- 简短验证报告。
- 已知后续事项。

## PR 审查

Prompt：

```text
请对比 main 审查当前分支。让 security_reviewer、qa_engineer，
以及最相关的实现 agent 检查 diff。先给 P0/P1 问题，再总结验证缺口。
```

期望输出：

- 带文件定位的具体问题。
- 缺失测试或评测。
- 安全、隐私与可靠性风险。
- 简短合并建议。

## 发布就绪

Prompt：

```text
请为 <release> 准备发布就绪评审。拉起 product_manager、
qa_engineer、security_reviewer、sre_engineer、ai_platform_engineer。
给出 go/no-go 结论，并列出阻塞项、负责人与回滚方案。
```

期望输出：

- go/no-go 建议。
- 阻塞项与负责人。
- 回滚与监控方案。
- 上线后反馈闭环。
