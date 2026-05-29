# Harness Workflows

## Discovery To PRD

Prompt:

```text
Create a PRD for <feature>. Spawn product_manager, ux_designer,
solution_engineer, security_reviewer, and qa_engineer. Wait for all results and
produce scope, non-goals, personas, acceptance criteria, metrics, risks, and
open decisions.
```

Expected output:

- One-page product summary.
- User journey and primary screens.
- Requirements and non-goals.
- Security, privacy, and compliance risks.
- Test and eval strategy.

## Architecture Review

Prompt:

```text
Review the proposed architecture for <feature>. Spawn system_architect,
backend_engineer, ai_platform_engineer, sre_engineer, and security_reviewer.
Return blocking risks, recommended interfaces, and staged implementation plan.
```

Expected output:

- Domain boundaries and data contracts.
- AI workflow contract and eval plan.
- Observability and rollout plan.
- Security and privacy concerns.

## Implementation Slice

Prompt:

```text
Implement the smallest vertical slice for <feature>. Use frontend_engineer,
backend_engineer, and ai_platform_engineer where relevant. Keep changes scoped,
add tests, and report verification commands.
```

Expected output:

- Focused code changes.
- Tests or evals.
- Short verification report.
- Known follow-ups.

## PR Review

Prompt:

```text
Review this branch against main. Have security_reviewer, qa_engineer, and the
most relevant implementation agent inspect the diff. Lead with P0/P1 findings,
then summarize verification gaps.
```

Expected output:

- Concrete findings with file references.
- Missing tests or evals.
- Security, privacy, and reliability risks.
- Short merge recommendation.

## Launch Readiness

Prompt:

```text
Prepare a launch readiness review for <release>. Spawn product_manager,
qa_engineer, security_reviewer, sre_engineer, and ai_platform_engineer. Produce
a go/no-go decision with blockers, owners, and rollback plan.
```

Expected output:

- Go/no-go recommendation.
- Blocker list and owners.
- Rollback and monitoring plan.
- Post-launch feedback loop.
