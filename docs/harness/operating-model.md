# Operating Model

## Product Lifecycle

1. Discover: define user, problem, value, constraints, and success metric.
2. Shape: write PRD, UX flow, technical approach, threat model, and eval plan.
3. Build: implement small vertical slices behind feature flags.
4. Verify: run tests, UX checks, security review, evals, and release checklist.
5. Launch: roll out gradually, monitor, collect feedback, and document changes.
6. Operate: maintain SLOs, incident response, cost controls, and improvement
   loops.

## GitHub Flow

- `main` stays releasable.
- Branch names should start with `feature/`, `fix/`, `docs/`, `infra/`, or
  `experiment/`.
- Draft PRs are for exploration; ready PRs should be reviewable and scoped.
- Require at least one human review for production changes.
- Use Codex review for an additional high-signal pass focused on serious risks.

## Issue Types

- Product brief: problem, target users, success metric, acceptance criteria.
- Design task: flows, screens, states, accessibility, design risks.
- Engineering task: scope, contracts, tests, migration, rollout.
- Reliability task: SLO impact, observability, rollback, runbook.
- Security task: data, identity, permissions, audit, AI safety.

## Launch Gates

- Product owner accepts the user outcome and non-goals.
- UX flow covers empty, loading, error, permission, and narrow-screen states.
- Security review covers authz, data handling, tool permissions, and audit logs.
- QA verifies acceptance criteria and regression risks.
- AI platform review verifies prompts, tools, traces, evals, and fallbacks.
- SRE verifies deployment, rollback, observability, and incident runbook.
