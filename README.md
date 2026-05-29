# AgentSpace

AgentSpace is an internal Web SaaS product for company AI systems. This
repository is initialized with a Codex-style harness: project instructions,
specialized subagents, GitHub review automation, and operating documents for a
10-person product engineering team.

## Harness Quick Start

1. Review the product brief in `docs/product/brief.md`.
2. Review team roles and workflows in `docs/harness/`.
3. Connect the repository to GitHub.
4. Add the `OPENAI_API_KEY` repository secret if using the Codex GitHub Action.
5. Open work through GitHub issues and small pull requests.

## Suggested First Milestones

- M0: Product scope, target users, system boundaries, and success metrics.
- M1: Architecture, identity model, AI workflow contracts, and threat model.
- M2: Design prototype for agent catalog, chat/workflow runner, admin console,
  audit views, and workflow builder.
- M3: MVP implementation with auth, RBAC, one approved agent workflow, traces,
  evals, and deployment pipeline.
- M4: Beta launch, operational runbooks, incident process, and improvement loop.
