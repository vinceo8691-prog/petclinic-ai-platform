# 2. Separate the AI Agent from the UI and the REST API

Date: 2026-09-30

## Status

Accepted. Amended by [ADR-0007](0007-ai-agent-acts-on-behalf-of-the-employee.md): the agent acts as the requesting employee and never writes; the UI executes confirmed writes.

## Context

The platform needs to let an LLM-backed agent read and write PetClinic data (e.g.
look up an owner's pets, propose a new appointment) while a human using the React UI
stays in control of anything the agent changes. There were three ways to structure
that:

1. Build the agent logic directly into `spring-petclinic-rest`,
   (the REST backend) — e.g. an `/ai/chat` endpoint inside the same Spring Boot app
   that already owns the database.
2. Build it into `petclinic-ui` — call an LLM provider directly from the frontend,
   orchestrating tool calls against the PetClinic API from the browser.
3. Build it as its own deployable application (`petclinic-ai-agent`) that talks to
   `spring-petclinic-rest` the same way any other REST client would,
   and that `petclinic-ui` talks to separately.

Agent/LLM code has a different risk and change profile than the rest of the
business API: it depends on an external LLM provider, its behavior is
non-deterministic, its library surface (LLM SDKs, prompt/tool-calling frameworks)
moves quickly, and it's the one part of the system whose output is allowed to
initiate writes that a human then has to confirm. Implementing this functionality directly within 
the `petclinic-ui` would couple AI orchestration and business integration logic to the 
presentation layer. It would also expose concerns such as model credentials, tool execution, 
backend-service coordination, and agent state to a client-side application that is deployed and scaled differently 
from backend services. Implementing that into the same process
as the system of record would make it much harder to guarantee the rule that matters most here:
the agent must never touch the database directly, and every write it proposes must
pass through the same authorization and confirmation path as any other API client.

## Decision

`petclinic-ai-agent` is its own Spring Boot application, deployed and scaled
independently of `spring-petclinic-rest` and `petclinic-ui`. It:

- never connects to the PetClinic database or shares a persistence layer with
  `spring-petclinic-rest`;
- reads and writes PetClinic data exclusively by calling `spring-petclinic-rest`'s REST
  endpoints, as an authenticated client like any other;
- requires human confirmation (surfaced through `petclinic-ui`) before any
  AI-initiated write takes effect.

`petclinic-ui` talks to both `spring-petclinic-rest` and `petclinic-ai-agent` directly as two
separate backends, rather than the agent being reachable only through the API or the
UI proxying AI calls through the API.

## Consequences

**Positive:**

- The "AI Agent never accesses the database directly" rule is enforced structurally,
  not just by convention — there is no shared datasource, connection pool, or JPA
  context for agent code to accidentally use. The only path to data is the same REST
  API every other client uses.
- Every agent-initiated write is authorized, validated, and audited exactly the same
  way as a write from the UI, because it goes through the same endpoints — there's no
  separate, weaker code path for "writes that came from the AI."
- Blast radius is contained: an LLM provider outage, a runaway agent loop, or a bug in
  agent/tool-calling code degrades AI features without taking down appointment
  booking, owner records, or any other core clinic operation running in
  `spring-petclinic-rest`.
- Fast-moving AI/LLM SDK dependencies stay isolated in `petclinic-ai-agent`'s
  dependency tree instead of being pulled into the system of record, reducing the
  upgrade/security surface of `spring-petclinic-rest`.
- The agent and the API can scale independently — agent workloads are latency-bound
  on external LLM calls rather than database-bound, so they can be sized, scaled, and
  given their own secrets (LLM API keys in Secrets Manager/Parameter Store) without
  touching the API's ECS/Fargate service.

**Negative / trade-offs accepted:**

- Every agent read or write costs a network hop and HTTP(S)/JSON serialization
  instead of an in-process call, adding latency versus embedding agent logic in the
  API process.
- The contract between `petclinic-ai-agent` and `spring-petclinic-rest` has to be maintained
  explicitly (OpenAPI spec, versioned endpoints) rather than shared Java
  types/interfaces — a breaking API change now has to be coordinated across two
  deployables instead of being caught by the compiler in one.
- Three ECS/Fargate services to provision, deploy, and monitor instead of two — more
  task definitions, health checks, and CloudWatch dashboards in the infrastructure-as-
  code to maintain.
- Exercising an end-to-end AI flow locally requires running all three applications
  together rather than a single process.

## Revisit if

- The agent and API end up always deploying together in lockstep anyway, making the
  operational separation pure overhead with none of the independent-scaling benefit.
- The network-hop latency between agent and API becomes a measurable problem for
  agent response times that can't be solved by caching or batching.
