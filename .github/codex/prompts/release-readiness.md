# AgentSpace Release Readiness Prompt

Prepare a release readiness review for the current branch.

Read `AGENTS.md`, `docs/harness/`, and any release notes or changed files.
Use `product_manager`, `qa_engineer`, `security_reviewer`, `sre_engineer`, and
`ai_platform_engineer` when helpful.

Return:

1. Go/no-go recommendation.
2. Launch blockers with owners and mitigation.
3. Product, UX, QA, security, AI platform, and SRE readiness.
4. Required tests, evals, and manual checks.
5. Rollback plan and monitoring checklist.
6. Post-launch feedback and improvement loop.

Do not modify files unless explicitly asked.
