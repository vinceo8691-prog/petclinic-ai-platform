# 7. The AI agent acts on behalf of the requesting employee and never writes

Date: 2026-10-10

## Status

Proposed

## Context

[ADR-0002](0002-separate-ai-agent-from-ui-and-rest-api.md) made the AI agent a separate
deployable that reaches PetClinic data only through the REST API, with human confirmation
before any AI-initiated write. It did not say *whose authority* the agent's REST calls run
with. Checking the backend as it exists today:

- Authorization is `@PreAuthorize` on 36 controller methods in 9 controllers, using
  `ROLE_OWNER_ADMIN`, `ROLE_VET_ADMIN` and `ROLE_ADMIN`.
- Roles are not split into read and write. `ROLE_OWNER_ADMIN` gates every owner, pet and
  visit endpoint, reads included; `ROLE_VET_ADMIN` gates every vet, specialty and pet-type
  write and all vet and specialty reads. Pet-type reads accept either role.
- `ROLE_ADMIN` implies both through the hierarchy in
  [ADR-0006](0006-admin-role-hierarchy.md). `ROLE_OWNER_ADMIN` and `ROLE_VET_ADMIN` do not
  imply each other.
- There is no agent principal. Authentication is HTTP Basic against the `users` and `roles`
  tables, and is off unless `petclinic.security.enable=true`.

Two problems follow if the agent simply gets credentials of its own:

1. **Confused deputy.** Suppose the agent holds a role that can read every owner. An employee
   with only `ROLE_VET_ADMIN` is denied `GET /api/owners` directly, but can ask the assistant
   to "find owners named Davis" and receive the same data. A service with more authority
   performs an operation for a caller with less.
2. **Consent is not authorization.** A confirmation dialog in the UI shows that a human
   agreed. If the agent holds write-capable credentials, nothing in the backend stops it
   from calling a write endpoint without showing the dialog, whether through a bug, a
   compromise, or prompt injection. The agent reads text that users typed (owner names,
   visit descriptions), so injection is realistic.

## Decision

### The rule

> An AI agent must not access or modify information beyond what the requesting employee is
> authorized to access, regardless of the agent's own service credentials.

Effective permission is **the employee's authorization ∩ what the agent is allowed to
do**. The employee's authorization is the authority; the agent's own restrictions can only
narrow it, never widen it.

### 1. Every data call carries the requesting employee's identity

All requests the agent makes to `spring-petclinic-rest` for data, reads included, are made
as the employee who asked. The backend's existing `@PreAuthorize` checks do the
enforcement; the agent does not reimplement permission logic. A `VET_ADMIN` asking for owner
data gets a 403 from the backend, and the agent relays it.

- **Interim (stage 1):** the UI sends the employee's `Authorization` header to the agent,
  which forwards it unchanged on each REST call. The agent holds it in memory for the
  duration of the request only, never persists or logs it, and never places it in a prompt.
  Transport must be TLS. This works with today's HTTP Basic, at the cost of the agent
  handling raw credentials.
- **Target:** a delegated token (for example OIDC, issued by an identity provider) that
  identifies the employee and an acting-via-agent claim, validated by the backend. This
  requires moving the platform from HTTP Basic to token-based authentication, which is a
  separate decision and PR. It is a prerequisite for any production-like deployment, not
  for the stage-1 portfolio demonstration.

### 2. The agent's own credential carries no data authority

If the agent needs a service credential at all (to identify itself as a caller), that
credential holds no role that passes any `@PreAuthorize` check. It is **not** placed in the
role hierarchy and **ADR-0006 is unchanged**. No new authority role is introduced for the
agent.

### 3. The agent never writes

The agent has no write tools. When the model decides a change is needed, the agent returns
a **proposal** under the closed contract in decision 4. The UI:

1. fetches the current record fresh from the API,
2. renders the exact before and after to the employee,
3. on Confirm, itself calls the REST API with the employee's own credentials and exactly the
   payload that was displayed.

The backend authenticates, authorizes and validates that call like any other client write.
The model's output is treated as untrusted input throughout (for example, the 10-digit
telephone rule from PC004 is re-applied by the backend).

### 4. A proposal is a closed contract, not free-form REST

The agent may not return an arbitrary endpoint, HTTP method, header or unrestricted JSON
body. A proposal is a small, named **operation** with a fixed shape, drawn from a registry
that is defined once and shared by the agent and the UI. The UI, not the model, decides
which request an operation turns into.

**Registry for the initial implementation: exactly one operation.**

```json
{
  "operation": "UPDATE_OWNER_TELEPHONE",
  "ownerId": 1,
  "telephone": "6085551023"
}
```

| Field | Rule |
|---|---|
| `operation` | Must equal a registered operation name; anything else is rejected |
| `ownerId` | Integer, the id of an owner the employee could already read |
| `telephone` | Exactly 10 digits (the same rule the backend applies) |
| any other field | Rejected: the schema is closed (`additionalProperties: false`) |

There is no URL, method, header, query string or free-text field in a proposal. Any
explanation the model offers for the employee is display-only text in the assistant message,
never part of the contract and never sent to the API.

**How the UI turns it into a request** is fixed, per operation, in UI code:

1. `GET /api/owners/{ownerId}` for the current record (the "before").
2. Build the "after" by copying that record and replacing only `telephone`.
3. On Confirm, re-fetch and compare (decision 5), then `PUT /api/owners/{ownerId}` with that
   full record, so the model cannot alter any other field (the update endpoint replaces the
   whole record).

**Validation happens at every hop, and the proposal is never trusted:**

- The agent validates the model's output against the schema before returning it, and
  discards anything that does not match.
- The UI validates it again on receipt; a proposal that fails is shown as "the assistant
  produced an invalid proposal" and nothing is executed.
- The backend validates the final request as for any client (bean validation, `@PreAuthorize`).

**Scope limits:** one operation per confirmation, one record per proposal, no batch, no
delete (decision 8), and no operation that touches more than one resource.

**Adding an operation** needs all of: a registry entry and schema, a fixed UI mapping and
renderer, tests (valid, malformed, unknown, extra fields, unauthorized employee), and an
amendment to this ADR. Operations that are destructive or span records need their own ADR.

### 5. Protect against stale data: confirm-time check now, optimistic concurrency later

**The problem.** The owner update is a full replace and the backend does not check what the
client last saw (no `@Version`, no `If-Match`). If Employee A reviews a proposal, Employee B
then changes the same owner, and A confirms, A's PUT silently restores the fields B changed,
including fields the proposal never meant to touch. Copying the fetched record (decision 4)
bounds the model, but it also makes this lost update possible, and the existing Edit Owner
page already has the same exposure. (The spec declares `ETag` response headers, but nothing
in the backend sends one.)

**Stage 2 (decided): a confirm-time check in the UI only, with no backend change.** On
Confirm, the UI fetches the owner again and compares all editable fields (`firstName`,
`lastName`, `address`, `city`, `telephone`) with the "before" the employee reviewed:

- If they are identical, the UI sends the PUT.
- If any differ, the UI sends nothing, tells the employee the owner changed since they opened
  it, and re-renders the proposal against the current values for a fresh review.

This shrinks the race from the whole review time to the interval between this second fetch
and the PUT, which is milliseconds. **That residual window is accepted as a deliberate
trade-off** for the portfolio scope: it keeps the change minimal (no schema, API or
backend-test impact), and in that interval the last write wins. It is not eliminated, and it
should not be described as eliminated.

**Target: optimistic concurrency with `@Version` and `ETag` / `If-Match`.** As its own PR,
not part of stage 2:

- `Owner` gets a `@Version` column (in every database schema), exposed as an `ETag` on
  `GET /api/owners/{id}`.
- `PUT /api/owners/{id}` requires `If-Match`; a stale value answers `412 Precondition Failed`
  and a missing one `428 Precondition Required`, both as `ProblemDetail` (400-series mapping
  in `ExceptionControllerAdvice`).
- The UI sends the version it displayed; the Edit Owner page and the AI-proposal flow both
  use it.
- The confirm-time comparison then becomes redundant and can be removed.

**Alternatives considered here:** a narrow `PATCH /api/owners/{id}/telephone` removes the
unrelated-field overwrite but not a same-field race, and adds API surface; a body-hash
`ShallowEtagHeaderFilter` cannot gate a write because the ETag is computed after the handler
has run.

### 6. An agent-origin ceiling is a separate, restrictive mechanism

Calls that arrive via the agent may additionally be capped, for example "no `DELETE`, no
non-`GET` at all". This cannot be expressed in the role hierarchy, which only adds authority
(`hasRole` is satisfied by holding a role or one above it). It must be a separate check
keyed on the request's origin (the acting-via-agent claim, or an agent scope), and it can
only deny. With decision 3 in force it is defense in depth: an agent-origin write is denied
even if the agent were somehow made to attempt one.

### 7. Data sent to the model and state kept by the agent

- Only data the requesting employee is authorized to see may be placed in a prompt, since
  prompt content goes to an external LLM provider.
- Conversation memory, caches and retrieved results are scoped to the requesting user. No
  state is shared across employees.

### 8. No delete capability

The agent's tool allowlist contains no delete operation and no write operation, and the
proposal contract (decision 4) has no delete operation. A test asserts both.

### 9. Audit the AI-assisted operation

The backend records, as a structured audit event, every owner write that carries an
AI-assisted claim. Today the backend has no audit logging at all (only default Spring
logging), so this is new.

**Event contents (minimum), and who supplies each field:**

| Field | Example | Supplied by | Trust |
|---|---|---|---|
| `employee` | `jsmith` (the authenticated username, never the password or header) | Backend, from the security context | Server-derived |
| `target` | `owner:123` | Backend, from the request path | Server-derived |
| `outcome` | `SUCCESS`, or `FAILURE` with a category (`FORBIDDEN`, `NOT_FOUND`, `INVALID`, `ERROR`) | Backend, from the response status | Server-derived |
| `timestamp` | ISO-8601 UTC | Backend clock | Server-derived |
| `source` | `AI_ASSISTED` | Request header sent by the UI | **Client-asserted** |
| `operation` | `UPDATE_OWNER_TELEPHONE` (the registry name from decision 4) | Request header sent by the UI | **Client-asserted** |
| `proposalId` | UUID | Request header sent by the UI, issued by the agent | **Client-asserted** |
| `changedFields` | `["telephone"]` | Backend, by comparing the stored record with the request | Server-derived |

- **Request headers:** `X-AI-Operation` (a registry name) and `X-AI-Proposal-Id` (a UUID). Both
  are validated against the registry and the UUID format before use; a value that fails
  validation is never logged raw (it would allow log injection) and is recorded as an
  invalid claim, with the business request still decided by authorization alone.
- **What is not recorded:** no owner record, no old or new values, no telephone number, no
  request body. Field *names* only. The employee's identifier is personal data too, so the
  audit stream is access-controlled like other logs.
- **Failures are audited as well as successes,** including a 403, so the event is emitted
  around the controller (a filter or interceptor reading the final status), not inside the
  method body that `@PreAuthorize` may never reach.
- **Where it goes (MVP):** a dedicated logger category, one structured (JSON) line per event,
  shipped to CloudWatch Logs with its own retention. A persistent, queryable audit table is a
  later option if durable history is needed.
- **Corroboration from the agent:** when the agent issues a proposal it logs its own event
  (`proposalId`, `employee`, `operation`, `target`, timestamp; no values). The backend event
  can then be matched to it by `proposalId`. The agent's record is made server-side, so it
  is not browser-controlled.

**What this does and does not prove.** The employee, target, outcome and time are
trustworthy because the backend derives them. The `source`, `operation` and `proposalId` are
**claims made by a browser**: a modified client could omit them, or attach them to a write
the assistant never proposed, and the backend cannot tell. The event is therefore an audit
aid. It is not evidence that a given proposal was displayed and that this employee
confirmed it. The matching agent-side event raises confidence (a claimed proposal that the
agent never issued is detectable) but does not make the trail tamper-resistant.
Authorization is not affected either way: an employee who falsely claims "AI-assisted" could
have made the same write directly.

**If a tamper-resistant approval trail is required** (for example for a regulated setting),
the design needs server-side proposal and confirmation records: the agent persists each
proposal, the backend issues a one-time confirmation bound to the employee, proposal id and
exact payload, and the write is accepted only with that confirmation. This is the stage-3
work in the staged-delivery table. The UI would still execute the write (decision 3), but
the backend would verify the confirmation instead of trusting a header.

**Implementation notes:** no CORS change is needed (`@CrossOrigin` allows all request
headers by default), but check this if CORS configuration is made global or restrictive; the
headers must be sent only on the AI-confirmed write, not on ordinary Edit Owner requests.

## Staged delivery

| Stage | Scope | Done when |
|---|---|---|
| 1. Read-only agent | Authorized read tools only; identity forwarded; no write tool or credential | A role-by-tool test matrix passes: a `VET_ADMIN` is denied owner data via the agent, an `OWNER_ADMIN` is served, and no tool maps to a write endpoint. Test users cover `ROLE_ADMIN` alone and `ROLE_VET_ADMIN` alone, which the seed data does not |
| 2. AI-proposed write | One operation from the closed contract: `UPDATE_OWNER_TELEPHONE`; UI shows the diff and executes it; a structured audit event is emitted for the write (decision 9) | The exact displayed payload is what is sent; unknown or malformed proposals are rejected at the agent and the UI; a changed owner at confirm time blocks the write and re-renders the proposal; the backend rejects an invalid or unauthorized payload |
| 3. Stronger guarantees (optional) | Optimistic concurrency (`@Version`, `ETag` / `If-Match`) as its own PR, proposal expiry, replay protection; server-side approval records only if the agent ever executes writes | Revisit only if needed |

## Alternatives considered

- **Agent service account with a global read-only role.** Rejected: it is the confused-deputy
  problem; every employee gains the agent's read reach.
- **Agent authenticates as itself and sends a "user" header.** Rejected: the backend would
  trust the agent's assertion about who the user is, which recreates the same flaw one hop
  later.
- **The agent returns a ready-made request (endpoint, method, body).** Rejected: the model
  would choose what the UI sends, so injected text could steer the UI at any endpoint the
  employee can reach. A closed operation registry bounds the worst case to one named change
  that the employee reviews.
- **Agent executes confirmed writes (replaying the approved payload).** Rejected for now: it
  needs write-capable agent credentials and server-side proposal integrity (approval records,
  replay protection, expiry). Having the UI execute the write avoids all of that and keeps the
  agent write-incapable by construction.
- **A read-only `ROLE_AI_ASSISTANT` in the hierarchy.** Rejected: it adds authority, so it
  cannot act as a ceiling, and with on-behalf-of calls the agent needs no authority of its
  own.

## Consequences

**Positive:**

- The agent can never see or change more than the employee behind the request, and a
  prompt-injected agent is bounded by the same rule.
- No change to the existing 36 `@PreAuthorize` checks, the role hierarchy or the seed roles.
- The agent holds no write capability in any stage, so "AI writes require human
  confirmation" is enforced structurally.

**Negative / trade-offs accepted:**

- In the interim the agent handles raw employee credentials. This is contained by never
  storing, logging or prompting them, but it is a real exposure until token-based
  authentication exists.
- The UI needs a proposal renderer and confirm handler per write operation, and a `GET` at
  proposal time plus another at confirmation time.
- Until optimistic concurrency exists, a lost update is still possible in the milliseconds
  between the confirm-time fetch and the PUT (decision 5). Accepted for the portfolio scope.
- A 403 from the backend surfaces in the assistant as "not permitted", so the agent and UI
  need clear wording for it.

## Security implications

- **Authentication:** interim credential forwarding over TLS; token-based authentication is
  the target and a prerequisite for production.
- **Authorization:** enforced by the backend with the employee's roles; the agent's
  restrictions only narrow.
- **Secrets:** employee credentials and the LLM API key never reach logs, prompts, images or
  repository config. The LLM key lives in Secrets Manager.
- **Data exposure:** prompt content leaves the system for the LLM provider, hence decision 7.
- **Prompt injection:** cannot escalate beyond the requesting employee's rights, and cannot
  cause a write because the agent has no write tool.

## Assumptions to confirm

- The interim forwarding of the `Authorization` header is acceptable for a portfolio
  demonstration.
- The token and identity-provider choice (and its PR) is deferred.
- Structured audit log lines are enough for the MVP (decision 9); a persistent audit table
  and a tamper-resistant approval trail are deferred. Whether to audit all owner writes (a
  baseline, with `source=DIRECT`) and not only AI-assisted ones is not decided.
- The header names `X-AI-Operation` and `X-AI-Proposal-Id` are proposals.

## Revisit if

- The agent ever needs to execute writes itself: reintroduce server-side approval records,
  replay protection and expiry (stage 3), and a distinct non-escalating credential.
- A fourth resource-scoped role is added: it needs its own hierarchy line (ADR-0006).
- Optimistic concurrency lands (`@Version`, `If-Match`): remove the confirm-time comparison
  and rely on 412 (decision 5).
- The system gains concurrent editors in a setting where a millisecond lost-update window is
  not acceptable: make the target in decision 5 a prerequisite, not a follow-up.
- Token-based authentication is adopted: replace the interim forwarding and implement the
  agent-origin ceiling on the acting-via-agent claim.
