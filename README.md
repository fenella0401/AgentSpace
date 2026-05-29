# AgentSpace

AgentSpace 是公司内部 AI 系统使用的 Web SaaS 产品。本仓库已初始化为
Codex 风格 harness，包含项目指引、专业子代理、GitHub 审查自动化与面向
10 人产品工程团队的协作运行文档。

## Harness 快速开始

1. 阅读产品简报：`docs/product/brief.md`。
2. 阅读团队角色与流程：`docs/harness/`。
3. 将仓库连接到 GitHub。
4. 如需使用 Codex GitHub Action，配置仓库密钥 `OPENAI_API_KEY`。
5. 通过 GitHub Issue 与小型 Pull Request 推进工作。

## 建议的首批里程碑

- M0：明确产品范围、目标用户、系统边界与成功指标。
- M1：完成架构、身份模型、AI 工作流契约与威胁模型。
- M2：完成智能体目录、聊天/工作流运行器、管理控制台、审计视图与工作流构建器的设计原型。
- M3：实现 MVP，包括认证、RBAC、一个已批准工作流、追踪、评测与部署流水线。
- M4：Beta 发布，补齐运维手册、故障流程与持续改进闭环。
