# AgentSpace PR Review Prompt

You are reviewing a pull request for AgentSpace, an internal AI SaaS product.
Read `AGENTS.md` and the relevant files before judging the change.

Compare the pull request against the base branch and focus on serious issues:

- Authn/authz bypasses, tenant isolation gaps, data leakage, or secret exposure.
- Unsafe AI tool execution, prompt injection, missing evals, or missing traces.
- Incorrect API behavior, migrations, persistence, retries, or idempotency.
- UX regressions that break core workflows, accessibility, or admin operations.
- Reliability, observability, rollout, rollback, or incident response gaps.
- Missing tests for changed behavior.

Use custom agents when helpful:

- `security_reviewer` for privacy, auth, AI safety, and compliance.
- `qa_engineer` for acceptance and regression gaps.
- `frontend_engineer`, `backend_engineer`, or `ai_platform_engineer` for
  implementation-specific risks.
- `sre_engineer` for deployment and operational risk.

Output format:

1. Merge recommendation: approve, request changes, or needs follow-up.
2. Findings: P0/P1 issues first, with file paths and concrete reasoning.
3. Verification gaps: tests, evals, or manual checks still needed.
4. Short summary: what changed and what is safe to ship.

Do not modify files. Do not report style-only issues unless they hide a real
bug or operational risk.
