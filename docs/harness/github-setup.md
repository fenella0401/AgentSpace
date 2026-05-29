# GitHub 配置

## 仓库初始化

1. 为 AgentSpace 创建私有 GitHub 仓库。
2. 将当前本地仓库推送到 GitHub。
3. 为 `main` 启用分支保护。
4. 合并前要求 PR 审查与状态检查通过。
5. 在 `.github/CODEOWNERS` 配置代码归属团队或用户。

## Codex 审查自动化

工作流 `.github/workflows/codex-pr-review.yml` 会在非草稿 PR 上执行 Codex 审查。

必需配置：

- 添加仓库密钥 `OPENAI_API_KEY`。
- 保持 `.github/codex/prompts/pr-review.md` 与当前审查策略一致。
- 若组织使用 `@codex review` 等评论流，请启用仓库级 Codex 云端/代码审查能力。

## 建议分支保护策略

- 要求线性历史。
- 要求 CODEOWNERS 审批。
- 要求测试、lint、typecheck 与 Codex PR 审查状态检查通过。
- 禁止对 `main` 强推。
- 若公司策略要求，开启签名提交。

## 建议标签

- `area:product`
- `area:ux`
- `area:frontend`
- `area:backend`
- `area:ai-platform`
- `area:security`
- `area:infra`
- `type:feature`
- `type:bug`
- `type:spike`
- `risk:launch-blocker`
