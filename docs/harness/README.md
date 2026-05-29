# AgentSpace Harness

本目录说明如何在 AgentSpace 使用 Codex 风格 harness。
该 harness 将项目规则、自定义子代理、GitHub 自动化与团队协作实践整合在一起，
帮助产品从想法更高效地推进到生产。

## 团队拓扑

10 人团队以项目级自定义 agent 形式表示：

| 角色 | 自定义 agent | 主要职责 |
| --- | --- | --- |
| 产品经理 | `product_manager` | PRD、路线图、范围、指标 |
| UX 设计师 | `ux_designer` | 信息架构、流程、交互设计 |
| 解决方案工程师 | `solution_engineer` | 干系人适配与系统集成 |
| 系统架构师 | `system_architect` | 架构与服务边界 |
| 前端工程师 | `frontend_engineer` | SaaS UI 实现 |
| 后端工程师 | `backend_engineer` | API、授权、持久化 |
| AI 平台工程师 | `ai_platform_engineer` | agents、prompts、evals、traces |
| QA 工程师 | `qa_engineer` | 测试计划与发布验证 |
| SRE 工程师 | `sre_engineer` | 部署、可观测性、运行手册 |
| 安全审查 | `security_reviewer` | 隐私、安全与 AI 安全 |

## 使用方式

- 当任务适合并行思考时，可点名一个或多个 agent 协作。
- 父代理负责最终改动整合与冲突消解。
- 计划性工作使用 GitHub Issue，评审性改动使用 Pull Request。
- 对非草稿 PR 运行 Codex PR 审查自动化。
- 将产品决策、架构说明与运维文档沉淀在 `docs/`。

## 示例 Prompt

```text
请规划 AgentSpace MVP。拉起 product_manager、ux_designer、
solution_engineer、system_architect、security_reviewer、qa_engineer。
等待所有结果后，产出一页执行计划。
```

```text
请实现智能体目录。先由 ux_designer 给出 UX 方案，
system_architect 校验边界，再由 frontend_engineer 与 backend_engineer
实现最小 MVP，最后由 qa_engineer 与 security_reviewer 完成审查。
```

```text
请准备 Beta 发布就绪评审。使用 sre_engineer、qa_engineer、
security_reviewer、product_manager、ai_platform_engineer。
输出阻塞项、负责人和 go/no-go 建议。
```
