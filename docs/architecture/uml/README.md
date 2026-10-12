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
2. **Identity** is a token, never a password: the employee logs in to a token issuer (ours
   first, Amazon Cognito later), the UI holds a short-lived token and exchanges it for a
   delegated read-only token (`act` claim) that it gives to the agent; the backend verifies and
   caps it. The agent never receives the employee's token or password. See
   [ADR-0007](../../adr/0007-ai-agent-acts-on-behalf-of-the-employee.md) decision 1 and the
   login and token ADR ([ADR-0009](../../adr/0009-login-and-authentication-mechanism.md),
   a placeholder) planned after the UI screens.
3. **The agent never writes.** It returns a proposal under a closed contract; the UI shows the
   exact change and executes the confirmed write with the employee's own token (ADR-0007,
   Accepted and amended).
4. **Concurrency and audit:** the confirm-time comparison is the demo fallback; `@Version` with
   `ETag` / `If-Match` is a production gate. The audit record is structured logging, not
   verified evidence (ADR-0007 decisions 5 and 9). Security failures answer 401 or 403 through
   one path ([ADR-0008](../../adr/0008-security-failures-answer-401-or-403-as-problemdetail.md)).
5. **Database** on AWS is RDS PostgreSQL (the `postgres` Spring profile exists).
6. **Routing**: one public ALB with path rules (`/petclinic/*` to the API, `/agent/*` to the
   agent), reached through CloudFront; the UI is served from S3 by the same distribution.
7. **Network**: Fargate tasks and RDS in private subnets; outbound via NAT gateway.
8. **CI/CD identity**: deployments use a role that GitHub Actions assumes through OpenID Connect
   (no long-lived AWS keys), with permission to push images and update ECS services but not to
   read secret values. See the IAM table in [04-security.md](04-security.md).
