# AgentSpace Harness

This directory describes how to use the Codex-style harness for AgentSpace.
The harness combines project rules, custom subagents, GitHub automation, and
team operating practices so the product can move from idea to production with
less coordination drag.

## Team Topology

The 10-person team is represented by project-scoped custom agents:

| Role | Custom agent | Primary responsibility |
| --- | --- | --- |
| Product Manager | `product_manager` | PRDs, roadmap, scope, metrics |
| UX Designer | `ux_designer` | IA, flows, interaction design |
| Solution Engineer | `solution_engineer` | stakeholder fit and integrations |
| System Architect | `system_architect` | architecture and service boundaries |
| Frontend Engineer | `frontend_engineer` | SaaS UI implementation |
| Backend Engineer | `backend_engineer` | APIs, authz, persistence |
| AI Platform Engineer | `ai_platform_engineer` | agents, prompts, evals, traces |
| QA Engineer | `qa_engineer` | test plans and release verification |
| SRE Engineer | `sre_engineer` | deploy, observability, runbooks |
| Security Reviewer | `security_reviewer` | privacy, security, AI safety |

## How To Use

- Ask for one or more named agents when a task benefits from parallel thinking.
- Keep the parent agent responsible for final edits and conflict resolution.
- Use GitHub issues for planned work and pull requests for reviewable changes.
- Run Codex PR review automation on non-draft pull requests.
- Keep product decisions, architecture notes, and runbooks in `docs/`.

## Example Prompts

```text
Plan the AgentSpace MVP. Spawn product_manager, ux_designer,
solution_engineer, system_architect, security_reviewer, and qa_engineer.
Wait for all results, then create a one-page execution plan.
```

```text
Implement the agent catalog. Have ux_designer define the UX, system_architect
check boundaries, frontend_engineer and backend_engineer implement the smallest
MVP, then qa_engineer and security_reviewer review the result.
```

```text
Prepare beta launch readiness. Use sre_engineer, qa_engineer,
security_reviewer, product_manager, and ai_platform_engineer. Return blockers,
owners, and a go/no-go recommendation.
```
