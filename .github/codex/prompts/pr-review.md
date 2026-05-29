# AgentSpace PR 审查提示词

你正在审查 AgentSpace 的一个 Pull Request。AgentSpace 是企业内部 AI SaaS 产品。
在判断变更前，请先阅读 `AGENTS.md` 与相关文件。

请将当前分支与基线分支对比，并优先关注严重问题：

- 认证/授权绕过、租户隔离缺失、数据泄漏或密钥暴露。
- 不安全的 AI 工具执行、提示词注入、缺失评测或缺失追踪。
- API 行为错误、迁移问题、持久化问题、重试或幂等性问题。
- 影响核心流程、无障碍或管理端操作的 UX 回归。
- 可靠性、可观测性、发布、回滚或故障响应缺口。
- 变更行为缺少必要测试。

在有帮助时使用自定义 agent：

- `security_reviewer`：隐私、权限、AI 安全与合规。
- `qa_engineer`：验收与回归覆盖缺口。
- `frontend_engineer`、`backend_engineer`、`ai_platform_engineer`：实现细节风险。
- `sre_engineer`：部署与运行风险。

输出格式：

1. 合并建议：approve、request changes 或 needs follow-up。
2. 发现项：按 P0/P1 优先，附文件路径与具体理由。
3. 验证缺口：仍需补充的测试、评测或人工检查。
4. 简短总结：本次改动内容与当前可安全上线范围。

不要修改文件。除非会掩盖真实 bug 或运行风险，否则不要报告纯样式问题。
