# AgentSpace Agent Instructions

## Product Context

AgentSpace is a Web SaaS product for an internal company AI system. It should
help employees discover, use, govern, and improve approved AI agents and
workflows. Treat the product as enterprise software that handles confidential
business data, identity, audit trails, and policy enforcement.

## Operating Model

- Work through GitHub issues, small pull requests, and reviewable artifacts.
- Start substantial work by clarifying the user, workflow, risk, and acceptance
  criteria before implementation.
- Keep product, UX, solution engineering, security, QA, SRE, and development
  concerns visible from discovery through launch.
- Prefer scoped changes that can be reviewed, tested, and shipped safely.
- Document assumptions and decisions in `docs/product/` or `docs/harness/`.
- Do not commit secrets, credentials, customer data, or generated private logs.

## Architecture Guardrails

- Build for tenant isolation, role-based access control, auditability, and
  least-privilege integrations from the first version.
- Every AI workflow should have an owner, input/output contract, tool policy,
  traceability, eval strategy, and fallback behavior.
- Store prompts, policies, model choices, and eval fixtures in versioned files
  when possible.
- Separate product UI, application APIs, AI orchestration, data connectors,
  observability, and deployment concerns.
- Prefer boring infrastructure and explicit interfaces over hidden coupling.

## Engineering Expectations

- Discover the existing stack before adding dependencies or conventions.
- Add or update tests when behavior changes.
- Run the narrowest meaningful verification before handing work back.
- Keep public APIs backward compatible unless a migration is documented.
- Make accessibility, localization readiness, and responsive behavior part of
  normal frontend work.
- Use structured parsers and typed contracts for config, API payloads, and evals.

## Review Guidelines

- Lead with correctness, security, privacy, reliability, and missing test risks.
- Treat authz bypass, data leakage, unsafe tool execution, prompt injection,
  audit gaps, and secret exposure as release blockers.
- Check that logs and traces do not include sensitive business data by default.
- Verify that AI outputs are bounded by policy, evals, and human escalation
  paths for high-impact workflows.
- Avoid style-only review comments unless they hide a real operational risk.

## Definition Of Done

- User story and acceptance criteria are clear.
- Design, API, data, security, observability, and rollout implications are
  considered.
- Tests or evals cover the changed behavior at the right level.
- Docs, runbooks, or migration notes are updated when behavior changes.
- The PR explains what changed, how it was verified, and remaining risks.
