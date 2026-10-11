# Architecture diagrams (UML, Mermaid)

Diagrams are Mermaid code blocks, so they render on GitHub and in IntelliJ's Markdown preview
and diff cleanly in pull requests. Edit the text, not an image.

| Diagram | File | Answers |
|---|---|---|
| System context | [01-system-context.md](01-system-context.md) | Who and what the system talks to |
| Components | [02-components.md](02-components.md) | What each service is responsible for and depends on |
| Sequences | [03-sequences.md](03-sequences.md) | How an owner search and an AI tool call execute |
| Security | [04-security.md](04-security.md) | Authentication, RBAC, IAM, trust boundaries |
| Deployment | [05-deployment.md](05-deployment.md) | How the system runs on AWS |

## Current state vs. target

Per [system-overview](../system-overview.md), only `spring-petclinic-rest` and part of
`petclinic-ui` exist today. The agent is an empty scaffold and there is no infrastructure code.
Every diagram therefore marks elements as:

- **Built**: solid border, exists in the repository now.
- **Target**: dashed border, intended by `CLAUDE.md` and the ADRs but not built.

Keep that distinction when updating: move an element from target to built in the same PR that
implements it.

## Assumptions to confirm

These choices are not recorded anywhere in the repo; the diagrams assume them. Correct them here
and in the diagrams if they are wrong.

1. **LLM provider** is an external hosted API reached from the agent (provider unnamed).
2. **UI to agent authentication** forwards the employee's own credentials to the agent, which
   forwards them to the API per request (interim; a delegated token is the target and needs an
   identity provider such as Cognito, not yet decided). See
   [ADR-0007](../../adr/0007-ai-agent-acts-on-behalf-of-the-employee.md).
3. **The agent never writes.** It returns a proposal; the UI shows the exact change and executes
   the confirmed write with the employee's credentials (ADR-0007, status Proposed).
4. **Database** on AWS is RDS PostgreSQL (the `postgres` Spring profile exists).
5. **Routing**: one public ALB with path rules (`/petclinic/*` to the API, `/agent/*` to the
   agent), reached through CloudFront; the UI is served from S3 by the same distribution.
6. **Network**: Fargate tasks and RDS in private subnets; outbound via NAT gateway.
