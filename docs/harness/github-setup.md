# GitHub Setup

## Repository Setup

1. Create a private GitHub repository for AgentSpace.
2. Push this local repository to GitHub.
3. Enable branch protection for `main`.
4. Require pull request reviews and status checks before merge.
5. Add CODEOWNERS teams or users in `.github/CODEOWNERS`.

## Codex Review Automation

The workflow `.github/workflows/codex-pr-review.yml` runs Codex review on
non-draft pull requests.

Required setup:

- Add repository secret `OPENAI_API_KEY`.
- Keep `.github/codex/prompts/pr-review.md` aligned with current review policy.
- Enable Codex cloud/code review for the repository if your organization uses
  GitHub comment workflows such as `@codex review`.

## Suggested Branch Protection

- Require linear history.
- Require PR approval from code owners.
- Require status checks for tests, lint, typecheck, and Codex PR review.
- Block force pushes to `main`.
- Require signed commits if company policy requires it.

## Suggested Labels

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
