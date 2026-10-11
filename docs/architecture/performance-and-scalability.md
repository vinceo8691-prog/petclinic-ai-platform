# Performance and scalability

Status as of 2026-10-11. This document records what is **known** about the system's
performance, concurrency, scale and failure behaviour, the **decisions** already made, and what
is **open** and why. It does not set targets or requirements ahead of the information needed to
set them.

## How this document is used

A decision is written down when there is enough information to make it. Until then, an item
stays *open* and says what information is missing. Each item carries one status:

| Status | Meaning |
|---|---|
| **Known** | A fact read from the code or configuration; nothing has been measured unless stated |
| **Decided** | A choice made, with a link to where it is recorded |
| **Open** | Not decided; the entry names what has to be learned first |

No latency, availability or capacity targets exist, and none are invented here. They are
introduced when real usage or a measurement gives a reason. See
[Measurements](#measurements).

Related: [system-overview](system-overview.md), [deployment diagram](uml/05-deployment.md),
[ADR-0007](../adr/0007-ai-agent-acts-on-behalf-of-the-employee.md).

## Latency

| Item | Status | Detail |
|---|---|---|
| Owner list and search are paged, with `size` capped at 100 | Decided | `GET /api/v2/owners`; the UI offers 10, 20 and 50 |
| UI debounces search (300 ms), keeps the previous page while the next loads, and does not refetch on window focus | Decided | `OwnersPage.tsx`, `App.tsx` (`staleTime` 30 s) |
| A page of owners probably costs a query per owner and per pet | Known (expected, not measured) | `Owner.pets` and `Pet.visits` are eager and the paged query has no fetch join |
| The last-name search and sort use `LOWER(last_name)`, which the plain `last_name` index may not serve on PostgreSQL or MySQL | Known (expected, not measured) | `OwnerRepository`; the tests do not exercise PostgreSQL or MySQL |
| With security enabled, every request pays a BCrypt check and two JDBC queries | Known (measured once, see [Measurements](#measurements)) | HTTP Basic is stateless and no session is created; about 70 to 90 ms per request against 5 to 10 ms with security off, on a local machine; `BasicAuthenticationConfig` |
| v1 list endpoints (`/owners`, `/pets`, `/visits`) are unbounded | Known | The UI uses the paged v2 endpoints |
| Whether any of the above matters at the real data size | Open | Needs a measurement with realistic data on PostgreSQL |
| Latency of the AI assistant | Open | There is no agent yet, and model latency is not known; a tool loop multiplies it by the number of tool calls |
| A latency target for any endpoint | Open | Not set until a measurement shows what is achievable and a use case shows what is needed |

## Concurrency

| Item | Status | Detail |
|---|---|---|
| `spring-petclinic-rest` and the agent are stateless services, so concurrent requests need no coordination between instances | Known | No session state; `open-in-view` is off |
| The backend has no optimistic locking: an owner update is a full replace that overwrites whatever is stored | Known | No `@Version`, no `If-Match`; `updateOwner` sets all five fields |
| AI-confirmed writes compare the owner with what the employee reviewed before sending the PUT; the remaining millisecond window is accepted | Decided | ADR-0007 decision 5 |
| Optimistic concurrency (`@Version`, `ETag` / `If-Match`, 412 and 428) is the target and gets its own PR | Decided (target; not scheduled) | ADR-0007 decision 5; it also fixes the existing Edit Owner page |
| Agent conversation state is scoped to the requesting user and never shared between employees | Decided | ADR-0007 decision 7 |
| Where agent conversation state lives once more than one agent task runs | Open | Needs the agent's actual state model; in-process memory is enough for one task |
| Database connection pool size and the transaction isolation behaviour under concurrent edits | Open | Defaults are in use and untested under concurrent load |

## Scale

| Item | Status | Detail |
|---|---|---|
| The three applications are separate deployables and scale independently | Decided | [ADR-0002](../adr/0002-separate-ai-agent-from-ui-and-rest-api.md) |
| The target runs on Fargate behind an ALB, with CloudFront and S3 for the UI and RDS for data | Decided (target; not built) | `CLAUDE.md`, [deployment diagram](uml/05-deployment.md) |
| Adding API tasks scales request handling; the database connection count and RDS size do not scale that way | Known | Connections equal tasks times pool size |
| The expected number of owners, users and requests | Open | Not known; this single fact decides most of the items in this document |
| Autoscaling policy, minimum and maximum task counts | Open | Needs the expected load and measured per-task throughput |
| RDS instance size, Multi-AZ, a read replica or RDS Proxy | Open | Needs data volume and connection behaviour under load |
| Caching of any kind | Open | Nothing is cached; add only where a measurement shows repeated expensive reads |
| Cost control for model usage (prompt size, tool-result size, rate limits) | Open | Needs a model provider and real prompt sizes; ADR-0007 already keeps prompts to data the employee may see |

## Failure behaviour

| Item | Status | Detail |
|---|---|---|
| Errors answer with RFC 7807 `ProblemDetail`: 400 for invalid input, 403 for a denied authorization check, 404 for missing records and integrity violations, 500 for the unexpected | Known | `ExceptionControllerAdvice`, [note](authorization-denied-status-code.md) |
| The UI retries queries up to three times on network failures and 5xx, and never on a 4xx other than 408 and 429 | Decided | `shouldRetry` in `petclinic-ui/src/api/client.ts` |
| The agent cannot corrupt data when it fails: it has no write path, and its proposals are validated and executed by the UI against the backend | Decided | ADR-0007 decisions 3 and 4 |
| The UI and API keep working when the agent or the model is unavailable, because the UI talks to both separately and the agent is optional | Decided | ADR-0002 |
| A 403 from the backend on an agent read is relayed to the employee as "not permitted", not retried | Decided | ADR-0007 decision 1 |
| A stale owner at confirmation blocks the write and shows the new values | Decided | ADR-0007 decision 5 |
| Timeouts for the model and for agent-to-API calls, retries with backoff, and a limit on tool calls per turn | Open | Needs the model provider's behaviour and measured latency |
| What the assistant shows when the model is slow, rate limited or down | Open | UI design for the assistant panel has not started |
| API and agent health checks, graceful shutdown and deployment strategy on ECS | Open | Belongs to the infrastructure work; Actuator is on the classpath |
| Behaviour when the database is unavailable beyond a 500 | Open | No decision yet on circuit breaking or degraded reads |
| Backup, restore and recovery time | Open | Needs a decision on the data's importance and the RDS configuration |

## Measurements

No measurements exist. The plan, in the order that gives information soonest:

1. **Count statements per page of owners.** Enable Hibernate statistics and assert how many
   queries one page issues. This turns the N+1 item from *expected* into *known*.
2. **Check the search plan on PostgreSQL.** Run `EXPLAIN (ANALYZE)` for the search and sort
   queries on a seeded table, before changing any index.
3. **Isolate authentication cost.** Time the owner list with `petclinic.security.enable`
   false and then true.
4. **Load test the paged endpoint and one write** (k6 or Gatling) at two or three data sizes,
   on the PostgreSQL profile, not H2.
5. **When the agent exists:** record model latency, tool calls per question, prompt and
   result sizes, and the share of time spent waiting on the model.

Record each result in the table below with the date and the data volume used. A target is
added to the sections above only after a result and a reason exist.

| Date | What | Data and setup | Result |
|---|---|---|---|
| 2026-10-11 | Does HTTP Basic create a session? | Local run, H2 profile, `petclinic.security.enable=true`, `curl` against `GET /api/v2/owners?size=1` | No `Set-Cookie` and no `JSESSIONID`; a request carrying only a cookie gets 401. Every request authenticates, so the per-request cost is real |
| 2026-10-11 | Authentication cost per request | Same setup, 10 requests each, seed data, one machine, includes `curl` start-up (an indication, not a load test) | Security off: 5 to 10 ms (first request 124 ms, cold). Security on, Basic `admin`: 69 to 97 ms. Wrong password: 69 to 88 ms, so BCrypt runs on failure too. About 65 to 80 ms of the difference is authentication |

## Open questions that block decisions

1. How many owners, users and requests per day should this system plan for? (Scale, and most
   of Latency)
2. Which model provider and model, and is streaming used? (Agent latency, failure, cost)
3. Is the production database PostgreSQL on RDS? (Search and indexing)
4. Is there any concurrent editing in practice that makes the millisecond window in
   ADR-0007 decision 5 unacceptable? (Concurrency)
