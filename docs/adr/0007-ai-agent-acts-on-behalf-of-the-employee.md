# 7. The AI agent acts on behalf of the requesting employee and never writes

Date: 2026-10-10

## Status

Accepted (2026-10-11). Amended in place on 2026-10-11; see [Amendments](#amendments).

## Amendments

Changes made after acceptance, each marked where it applies. They are design decisions to be
implemented at the AI agent and security milestones, not now.

1. **No raw employee credentials through the agent; a token mechanism is required**
   (decision 1, decision 2, the staged table, alternatives, consequences, assumptions). The
   forwarded-`Authorization`-header interim is removed. Cognito is the eventual identity
   provider, and delegation is designed separately from it.
2. **One authoritative proposal contract, with shared contract tests** (decision 4, staged
   table, alternatives, consequences, assumptions). The Java agent, the TypeScript UI and the
   backend's audit validation must not each keep their own copy of the operation list and
   shape.
3. **Optimistic concurrency (`@Version`, `ETag` / `If-Match`) is a production-readiness
   gate, not an optional follow-up** (decision 5, staged table, consequences, revisit-if,
   new "Production-readiness gates" section). The confirm-time comparison is the portfolio
   fallback only and must not be described as production-grade.
4. **Audit information is kept distinct from independently verified evidence** (decision 9,
   production gates). The MVP keeps structured logging and makes no claim that it is a
   tamper-resistant approval history.
5. **Security failures are 401 or 403 through one path** (decision 1 item 4, decision 6).
   The old defect where a denied `@PreAuthorize` check answered 500 was already fixed on
   2026-10-05 and is tested; what is decided here is how failures behave with tokens and the
   agent-origin cap, in [ADR-0008](0008-security-failures-answer-401-or-403-as-problemdetail.md).
6. **Server-side approval records have two independent triggers** (staged delivery, decision
   9, alternatives, revisit-if). They were described as needed "only if the agent executes
   writes", which was inconsistent with decision 9: they are also what turns the audit
   record into evidence that a human approved, even when the UI executes the write.
7. **Decision 4 refinements** (four changes, added one at a time):
   1. **Location and files fixed:** `contracts/ai-proposals/` holds `proposal.schema.json` and
      `examples.json`, and nothing else to start with. The files are created when the agent
      is implemented, not before, and no later than the start of stage 2 (the first stage with
      a proposal).
   2. **The schema never describes endpoints or HTTP methods.** The expected request lives
      only in `examples.json` as test data, and the mapping lives only in UI code, so the
      model cannot influence which endpoint is called.
   3. **Consumer roles are stated.** The agent and the UI validate complete proposals; the
      backend validates only the operation metadata it consumes and keeps authorizing and
      validating business writes independently. Contract tests run in CI. The schema defines
      permitted proposal data; trusted UI code determines REST behaviour.
   4. **Scope limits and the bar for new operations.** One operation, one record and one
      resource per proposal, with a separate confirmation each; batch, deletes and
      multi-resource changes are prohibited. Every new operation needs a schema entry,
      examples, a fixed UI mapping and renderer, a defined test set, and independent backend
      authorization. Routine operations that conform need **no** ADR amendment; destructive,
      batch, multi-resource, autonomous or differently authorized operations need an
      amendment or a new ADR. (This replaces "every new operation needs an amendment".)
8. **The UI performs the token exchange, so the agent never holds a write-capable token**
   (decision 1 item 3, decision 2, alternatives, security implications, assumptions). Before,
   the agent received the employee's token and exchanged it, which left it briefly holding a
   token that could write and made "write-incapable by construction" an overstatement.

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

In short: **the agent can only read, and only what the requesting employee may read; it holds
no standing credential that can read data.** It reads with a short-lived delegated token
bound to that employee (decision 1), its own credential carries no data authority
(decision 2), it has no write or delete tool (decision 8), and the backend refuses any
non-read request that carries the agent's `act` claim (decision 6).

### 1. Every data call carries the requesting employee's identity, as a delegated token

All requests the agent makes to `spring-petclinic-rest` for data, reads included, are made
as the employee who asked. The backend's existing `@PreAuthorize` checks do the
enforcement; the agent does not reimplement permission logic. A `VET_ADMIN` asking for owner
data gets a 403 from the backend, and the agent relays it.

**The employee's password never reaches the agent.** Forwarding raw credentials (a Basic
`Authorization` header) through the agent is not an accepted implementation, not even as an
interim for the portfolio MVP. *(Amendment 1; this replaces the earlier interim of
forwarding the header per request.)*

**Required mechanism.** This is the design the implementation must meet; the login page and
the token details get their own ADR,
[ADR-0009](0009-login-and-authentication-mechanism.md) (a placeholder for now), planned after
the UI screens.

1. **Login and token issuance.** The employee authenticates once, over TLS, to a token
   issuer, which returns a short-lived signed access token (a JWT) with the employee as
   subject, an issuer, an audience (the PetClinic API), an expiry and the employee's roles.
   The UI holds the token, never the password.
2. **Verification by the backend.** `spring-petclinic-rest` becomes a resource server: on
   every request it validates the signature, issuer, audience and expiry (with a key set that
   supports rotation) and builds the authentication from the token. The existing
   `@PreAuthorize` checks then run unchanged.
3. **Delegation to the agent, by attenuation, performed by the UI.** *(Amendment 8.)* The
   agent never receives the employee's own token, because that would hand it the employee's
   full authority (writes included) for the token's lifetime and contradict decision 3.
   Instead the **UI** exchanges the employee's token at the token issuer (token exchange in
   the style of RFC 8693) for a **delegated token**: the same subject, an `act` claim naming
   the registered agent as the actor, a narrowed scope (read operations only), the same
   audience, and a lifetime of minutes. The UI sends only that delegated token to the agent
   with each chat request, and the agent uses it for its tool calls. The issuer checks that
   the employee token is valid, that the requested actor is a registered agent, and that the
   requested scope is read-only; it will not mint a delegated token with more. The employee
   token never leaves the UI, so at no point does the agent hold a token that could write.
4. **Enforcement by the backend.** A request whose token carries `act` is capped: the
   effective permission is the employee's roles ∩ the token's scope. This is a check
   separate from the role hierarchy (decision 6; ADR-0006 is unchanged), for example a
   filter or method-security rule that denies any non-read operation when `act` is present.
   A request denied by this cap answers 403 with a code that tells the UI it was "not
   permitted via the assistant", as defined in ADR-0008. The agent's tool allowlist
   (decision 8) is the first line of defence; this is the line that holds if the agent is
   compromised or prompt-injected.

**An identity provider alone does not provide delegation.** OIDC, including Cognito, gives a
verified employee identity and tokens. It does not define who is acting for whom, how the
agent's authority is narrowed, or how the backend refuses a write that arrives through the
agent. The `act` claim, the exchange, the scope and the backend enforcement above are ours
to design and test whichever issuer is used.

**Issuer path.** First an issuer we run ourselves (the backend or a small token component),
so the portfolio does not depend on AWS to be demonstrable; later Amazon Cognito for the
employee login (OIDC). The backend validates tokens against the issuer's metadata and keys,
so changing issuer changes configuration, not authorization code.

**Local development.** With `petclinic.security.enable=false` there is no identity and the
agent calls the API anonymously. The stage-1 role-by-tool tests run with security enabled.

### 2. The agent's own credential carries no data authority

The agent holds no standing credential that can read data, and it needs none to obtain
tokens: the UI obtains a short-lived delegated token and passes it to the agent with each
request (decision 1), so what the agent reads with is borrowed per request, not owned. Its
only secret is the LLM API key. If
the agent ever needs an identity of its own for some other purpose, that identity holds no
role that passes any `@PreAuthorize` check. It is **not** placed in the role hierarchy and
**ADR-0006 is unchanged**. No new authority role is introduced for the agent.

### 3. The agent never writes

The agent has no write tools. When the model decides a change is needed, the agent returns
a **proposal** under the closed contract in decision 4. The UI:

1. fetches the current record fresh from the API,
2. renders the exact before and after to the employee,
3. on Confirm, itself calls the REST API with the employee's own token (not the agent's
   delegated one) and exactly the payload that was displayed.

The backend authenticates, authorizes and validates that call like any other client write.
The model's output is treated as untrusted input throughout (for example, the 10-digit
telephone rule from PC004 is re-applied by the backend).

### 4. A proposal is a closed contract, not free-form REST

The agent may not return an arbitrary endpoint, HTTP method, header or unrestricted JSON
body. A proposal is a small, named **operation** with a fixed shape, drawn from the
**registry** of operations. In this ADR the registry is not a separate artifact: it is the
set of operation definitions in the authoritative schema,
`contracts/ai-proposals/proposal.schema.json` (see "One authoritative contract" below),
defined once and shared by the agent and the UI. The UI, not the model, decides which
request an operation turns into.

**The registry's first and only entry is one operation.** The operation and its shape are
decided here; the schema file that holds it is created with the agent (no later than the
start of stage 2, the first stage in which a proposal exists, since the stage-1 agent only
reads).

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
3. On Confirm, re-fetch and compare (decision 5; this is the fallback, replaced by the
   `If-Match` precondition once optimistic concurrency lands), then
   `PUT /api/owners/{ownerId}` with that full record, so the model cannot alter any other
   field (the update endpoint replaces the whole record).

The update answers **204 No Content**: `updateOwner` builds its response with an owner body
and `HttpStatus.NO_CONTENT`, and the container drops a body from a 204, which the controller
tests also assert. `openapi.yml` documents 200 with an `Owner` body instead, so the spec and
the behaviour disagree; the spec is to be corrected to 204 in its own change (PC010, before
the TypeScript types are generated), not the behaviour. The UI must therefore not rely on
the response body: after a confirmed write it re-fetches the owner to show the result (the
same `GET` used for the comparison in decision 5 is a candidate to reuse).

**Validation happens at every hop, and the proposal is never trusted:**

- The agent validates the model's output against the schema before returning it, and
  discards anything that does not match.
- The UI validates it again on receipt; a proposal that fails is shown as "the assistant
  produced an invalid proposal" and nothing is executed.
- The backend validates the final request as for any client (bean validation, `@PreAuthorize`).

**One authoritative contract.** *(Amendments 2 and 7.)* The platform maintains one
authoritative, language-neutral AI-proposal schema and a shared collection of valid and
invalid contract examples. Three components touch it, in two languages, with different
responsibilities:

| Component | Relation to the contract |
|---|---|
| Agent (Java) | Validates **complete proposals** against the schema before returning them |
| UI (TypeScript) | Validates **complete proposals** against the schema on receipt, then renders them and decides the REST behaviour in its own trusted code |
| Backend (Java) | Does **not** validate proposals. It validates only the operation metadata it consumes, the `X-AI-Operation` header value against the operation names (decision 9), and it continues to authorize and validate business writes independently, as for any client |

None of them holds its own hand-kept copy of the contract. So:

1. **A single schema is the source of truth**, kept outside any one module in a neutral,
   version-controlled directory, `contracts/ai-proposals/` at the repository root, as a
   closed JSON Schema (`additionalProperties: false`, one entry per operation, discriminated
   by `operation`). Nothing else defines what a proposal is.
2. **Shared examples are the contract tests.** A second file sits next to the schema: valid
   proposals, and invalid ones with the reason (unknown operation, extra field, missing
   field, 9- and 11-digit telephone, non-integer id, a URL or method smuggled in). For each
   valid proposal it also records the request the UI is expected to build (method, path,
   which single field changes), which pins "the UI decides the request" in a testable form.
   That expectation lives only here, as test data, and never in the schema (see the boundary
   rule below).
3. **The agent and the UI run the examples in their own test suites and must agree.** The
   agent runs them in Java, the UI in TypeScript. A proposal accepted by one side and
   rejected by the other fails a build. Types may be generated from the schema where that
   helps (the UI already generates API types from `openapi.yml`), but generated types do
   not validate at runtime, so each side still has a runtime validator, and the examples are
   what prove the validators agree.
4. **The backend's list of operation names comes from the same schema**, or is checked
   against it by a test, so adding an operation cannot leave the audit header validation
   behind. That test covers the operation names only; the backend's suite does not run
   the full proposal examples, because the backend never receives a proposal.
5. **CI enforces the pairing.** The monorepo runs every module's tests on every pull request
   (deliberately no path filters; see the comments in `.github/workflows/backend-ci.yml`), so
   a schema change that is not followed by the consumers
   fails the build, not a later integration.

**The contract in one sentence.** The schema defines the permitted proposal data; trusted UI
code, not the model and not the schema, determines the REST behaviour.

**The directory holds exactly two files to start with** *(Amendment 7)*:

| File | Holds |
|---|---|
| `contracts/ai-proposals/proposal.schema.json` | The valid proposal structure: the allowed operation names, the required fields of each, and the validation rules (closed object, types, the 10-digit telephone pattern). It names no endpoint, path, URL or HTTP method |
| `contracts/ai-proposals/examples.json` | Valid and invalid proposals, each with a short description, and for every valid proposal the expected UI behaviour (the request it must build and the single field that changes) |

**Boundary rule.** *(Amendment 7.)* The schema describes *what change is proposed*, never
*how it is carried out*. This preserves the boundary of this decision: the model never
decides which endpoint is called. Concretely:

- `proposal.schema.json` contains no endpoint, path, URL, HTTP method, header or request
  template, and no field that could carry one. Because the object is closed, a proposal that
  includes one is invalid (the examples include such a case).
- The mapping from an operation to its `GET` and `PUT` exists in exactly one place in
  production code: the UI's per-operation handler (decision 4, "How the UI turns it into a
  request"). It is code, reviewed like code, not data.
- The expected-request entries in `examples.json` are **test data**. The UI, the agent and
  the backend never read `examples.json` at runtime, and nothing builds a request from it. It
  is read only by the test suites, which assert that the UI's handler produces what the
  example says. If the file ever drove requests, an edit to data would become an edit to
  behaviour, and the schema's boundary would be bypassed through the examples.
- Adding an operation therefore changes three things in review together: the schema entry,
  the UI handler, and the example that pins the handler's output.

Two files are enough while there is one operation. When more operations are added, whether
they justify separate schemas or additional files is decided then, not now. The format is a
standalone JSON Schema in this directory, not a component of the agent's OpenAPI spec, so
the backend, which must not depend on the agent module, can read it too.

**Scope limits.** *(Amendment 7.)* Each AI-generated proposal is limited to **one
operation, one record and one resource**. Each operation requires its own employee
confirmation: nothing is approved in bulk or by implication. Under this ADR, batch
operations, deletes (decision 8) and multi-resource changes are **prohibited**.

**Every new operation requires all of the following:**

1. An entry in the authoritative proposal schema.
2. Valid and invalid examples in the shared contract tests.
3. A fixed UI-to-API mapping and a confirmation renderer (the mapping is code, per the
   boundary rule above).
4. Tests covering valid proposals, malformed proposals, unknown operations, unexpected
   fields, unauthorized employees, and successful confirmation.
5. Independent backend authorization and business validation: the operation uses an
   existing endpoint and its existing `@PreAuthorize` and validation rules, and gets no
   special treatment because it came from the assistant.

**When an ADR amendment or a new ADR is required.** A routine operation that conforms to
the constraints in this ADR does not need an amendment: the five requirements above, checked
in the pull request that adds it, are the control. An amendment to this ADR, or a new ADR,
is required when an operation introduces any of:

- destructive behaviour (a delete or an irreversible change);
- batch processing;
- changes to more than one resource;
- autonomous writes (a write without a fresh employee confirmation);
- materially different authorization or approval requirements (for example a role other
  than the one already guarding the endpoint, or an approver other than the requesting
  employee).

The first three are prohibited as written, so allowing one is a change to this decision, not
an addition to it.

### 5. Protect against stale data: confirm-time check as a fallback, optimistic concurrency as a production gate

**The problem.** The owner update is a full replace and the backend does not check what the
client last saw (no `@Version`, no `If-Match`). If Employee A reviews a proposal, Employee B
then changes the same owner, and A confirms, A's PUT silently restores the fields B changed,
including fields the proposal never meant to touch. Copying the fetched record (decision 4)
bounds the model, but it also makes this lost update possible, and the existing Edit Owner
page already has the same exposure. (The spec declares `ETag` response headers, but nothing
in the backend sends one.)

**Stage 2 fallback (decided): a confirm-time check in the UI only, with no backend
change.** It exists so the portfolio demonstration is not blocked on the backend work, and
it is **not** the production answer. On Confirm, the UI fetches the owner again and compares
all editable fields (`firstName`, `lastName`, `address`, `city`, `telephone`) with the
"before" the employee reviewed:

- If they are identical, the UI sends the PUT.
- If any differ, the UI sends nothing, tells the employee the owner changed since they opened
  it, and re-renders the proposal against the current values for a fresh review.

This shrinks the race from the whole review time to the interval between this second fetch
and the PUT, which is milliseconds. **That residual window is accepted as a deliberate
trade-off for the portfolio demonstration only**: it keeps the change minimal (no schema,
API or backend-test impact), and in that interval the last write wins. It is not
eliminated, and the system must not be presented as production-ready while it is the only
protection. *(Amendment 3: this was previously "accepted for the portfolio scope" with
optimistic concurrency as a later option; it is now a gate.)*

**Required before production-ready: optimistic concurrency with `@Version` and
`ETag` / `If-Match`.** Its own PR, scheduled after the stage-2 demonstration and before any
production-ready claim:

- **Scope:** every resource that has an update or delete endpoint (owners, pets, visits,
  vets, specialties, pet types), because the same lost update exists on each. Owners come
  first, since they are the AI write path and the Edit Owner page.
- **Storage:** a `@Version` column on each entity, added to every database schema (h2,
  hsqldb, mysql, postgres) and seed script. The version is exposed as a strong `ETag`
  (the quoted version) on `GET /{resource}/{id}` and returned on a successful update.
- **Preconditions:** `PUT` and `DELETE` require `If-Match`. A stale value answers
  `412 Precondition Failed`, a missing one `428 Precondition Required`, both as
  `ProblemDetail` from `ExceptionControllerAdvice`.
- **Two checks, not one:** the controller compares the client's `If-Match` with the loaded
  entity's version (Hibernate's `@Version` alone only guards the load-to-flush interval inside
  one persistence context and knows nothing about the client's copy), and `@Version` still
  catches a concurrent commit between load and flush, mapped to 412 as well.
- **Browser access:** the controllers' `@CrossOrigin(exposedHeaders = "errors, content-type")`
  must also expose `ETag`, or the UI cannot read it.
- **Spec:** `openapi.yml` gains the `If-Match` header parameter and the 412 and 428
  responses, and its already-declared `ETag` response headers become true.
- **Clients:** the UI sends the version it displayed (Edit Owner page and the AI-proposal
  flow); the Postman collection and CI smoke tests are updated.
- **Result for the AI flow:** a 412 on confirm replaces the confirm-time comparison, which
  is then removed.

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

### 9. Audit the AI-assisted operation (audit information, not verified evidence)

The backend records, as a structured audit event, every owner write that carries an
AI-assisted claim. Today the backend has no audit logging at all (only default Spring
logging), so this is new.

**Two different things, kept apart.** *(Amendment 4.)*

- **Audit information** is what the MVP produces: structured log lines that help the team
  see what happened, troubleshoot, spot anomalies and review how the assistant is used. It
  is recorded by the system that acted, can be altered or lost by anyone who controls that
  system or its log storage, and part of it is supplied by a browser.
- **Independently verified evidence** is a record whose authenticity does not depend on
  trusting the component being audited: it proves that this employee was shown exactly this
  proposal and confirmed it. The MVP does **not** produce this and does not claim to.

The MVP therefore keeps structured logging, and it is described everywhere as an *audit
record*. It is never described as an approval history, an approval trail, a tamper-resistant
log, or proof of consent.

**Event contents (minimum), and who supplies each field:**

| Field | Example | Supplied by | Trust |
|---|---|---|---|
| `employee` | `jsmith` (the authenticated username, never the password or header) | Backend, from the security context | Server-derived |
| `target` | `owner:123` | Backend, from the request path | Server-derived |
| `outcome` | `SUCCESS`, or `FAILURE` with a category (`FORBIDDEN`, `NOT_FOUND`, `INVALID`, `ERROR`) | Backend, from the response status | Server-derived |
| `timestamp` | ISO-8601 UTC | Backend clock | Server-derived |
| `claimedSource` | `AI_ASSISTED` | Request header sent by the UI | **Client-asserted** |
| `claimedOperation` | `UPDATE_OWNER_TELEPHONE` (an operation name from the schema, decision 4) | Request header sent by the UI | **Client-asserted** |
| `claimedProposalId` | UUID | Request header sent by the UI, issued by the agent | **Client-asserted** |

**Naming rule:** every field whose value comes from the browser is prefixed `claimed`, and
no server-derived field is. Anyone reading a log line can tell what the system established
from what it was told.

- **Request headers:** `X-AI-Operation` (an operation name from the schema) and
  `X-AI-Proposal-Id` (a UUID). Both are validated against the schema's operation names (see
  decision 4, item 4) and the UUID format before use; a value that fails
  validation is never logged raw (it would allow log injection) and is recorded as an
  invalid claim, with the business request still decided by authorization alone.
- **What is not recorded:** no owner record, no old or new values, no telephone number, no
  request body. The operation name already says which field the change concerns. The
  employee's identifier is personal data too, so the audit stream is access-controlled like
  other logs.
- **Failures are audited as well as successes,** including a 403, so the event is emitted
  around the controller (a filter or interceptor reading the final status), not inside the
  method body that `@PreAuthorize` may never reach. Every field in the table is available to
  such a filter: the principal, the path, the validated headers and the final status. A
  list of the fields that actually changed is deliberately not part of the minimum event,
  because it would need the stored record and so an emitter inside the service layer; add it
  only if a need appears.
- **Where it goes (MVP):** a dedicated logger category, one structured (JSON) line per event,
  shipped to CloudWatch Logs with its own retention. A persistent, queryable audit table is a
  later option if durable history is needed.
- **Log integrity is not provided.** Nothing signs, chains or write-once-protects these
  lines. Anyone who can write to or delete from the log group, or who controls the
  application, can add, change or remove events, and an outage can drop them. Restricting
  who can change the log group (IAM) and its retention reduce that risk; they do not
  remove it.
- **Corroboration from the agent:** when the agent issues a proposal it logs its own event
  (`proposalId`, `employee`, `operation`, `target`, timestamp; no values). The backend's
  `claimedProposalId` can then be matched to it. The agent's record is made server-side, so
  it is not browser-controlled, but it is the same kind of log and carries the same
  integrity limits.

**What this does and does not establish.**

| Question | Answer from the MVP audit record |
|---|---|
| Which authenticated employee made this write, to which target, when, with what outcome? | Yes, the backend derived those from the request and its own clock. Subject to the integrity limits above |
| Was this write made through the assistant? | Only as a *claim*: a modified client could omit the headers, or attach them to a write the assistant never proposed, and the backend cannot tell |
| Was this exact proposal displayed to the employee? | No |
| Did this employee confirm it? | No |
| Has the record been altered since it was written? | Not detectable |

**Appropriate uses:** troubleshooting, usage and error metrics, reviewing assistant
behaviour, and spotting anomalies such as a claimed proposal the agent never issued.
**Not appropriate:** settling a dispute about who agreed to what, or any compliance claim
that depends on an approval history. Authorization is not affected by the claim either
way: an employee who falsely claims "AI-assisted" could have made the same write directly.

**Independently verified evidence, if it is ever required** (for example in a regulated
setting), needs server-side proposal and confirmation records: the agent persists each
proposal, the backend issues a one-time confirmation bound to the employee, proposal id and
exact payload, and a write is accepted only with that confirmation; and the records
themselves need integrity protection (append-only storage, hashing or signing, restricted
write access). This is the verified-evidence trigger of the stage-3 work in the staged-delivery
table, and it applies even though the UI executes the write: the UI would still execute the
write (decision 3), but the backend would verify the confirmation instead of trusting a
header. Until that exists, no document, screen or report may call the audit log an
approval history or tamper-resistant.

**Implementation notes:** no CORS change is needed (`@CrossOrigin` allows all request
headers by default), but check this if CORS configuration is made global or restrictive; the
headers must be sent only on the AI-confirmed write, not on ordinary Edit Owner requests.

## Production-readiness gates

The system is described as production-ready only when all of these hold. They are listed
here so the portfolio demonstration cannot be mistaken for a production design.

1. **Token-based authentication with delegation** (decision 1): no raw credentials through
   the agent, backend verification, and the `act`-based cap.
2. **Optimistic concurrency** (decision 5): `@Version` and `ETag` / `If-Match` on every
   updatable resource.
3. **Nothing relies on the stage-2 fallbacks**: the confirm-time comparison is removed.
4. **The audit record is not oversold**: either the records are independently verifiable
   (decision 9, stage 3), or every description of them says "audit record" and states that
   the AI-assisted mark is a browser claim and the log is not tamper-resistant.

## Staged delivery

| Stage | Scope | Done when |
|---|---|---|
| 1. Read-only agent | Authorized read tools only; every call made with a delegated read-only token (decision 1), which needs the login and token mechanism to exist first; no write tool | A role-by-tool test matrix passes: a `VET_ADMIN` is denied owner data via the agent, an `OWNER_ADMIN` is served, and no tool maps to a write endpoint. Test users cover `ROLE_ADMIN` alone and `ROLE_VET_ADMIN` alone, which the seed data does not |
| 2. AI-proposed write | One operation from the closed contract: `UPDATE_OWNER_TELEPHONE`; UI shows the diff and executes it; a structured audit event is emitted for the write (decision 9) | The exact displayed payload is what is sent; unknown or malformed proposals are rejected at the agent and the UI, and the shared contract examples pass in the agent and UI suites (the backend's suite checks only that its operation names match the schema); a changed owner at confirm time blocks the write and re-renders the proposal; the backend rejects an invalid or unauthorized payload |
| 3. Stronger guarantees (optional) | Server-side proposal and confirmation records, with proposal expiry and replay protection; needed for either of the two reasons below | Revisit only if one of the two triggers applies |
| Gate (not a stage) | Optimistic concurrency on every updatable resource (decision 5) and token-based authentication (decision 1) | Both exist before the system is called production-ready |

**Why stage 3 exists: two separate triggers.** *(Amendment 6.)* Server-side proposal and
confirmation records are not one requirement with one reason. Either trigger is enough, and
they are independent of each other:

| Trigger | The question it answers | Applies when | Does it apply while the UI executes the write? |
|---|---|---|---|
| **Execution integrity** | Is the thing being executed exactly what the human approved? | Any component other than the human's own client executes a write (the agent, a queue, a job) | No. The employee's own client sends the displayed payload with their own token, and the backend authorizes it as them |
| **Verified evidence** | Can we prove this employee was shown this proposal and confirmed it? | Anyone needs that proof (a regulated setting, a dispute, a compliance requirement), regardless of who executes | **Yes.** The backend cannot verify what a browser says it displayed, so the audit record (decision 9) is information, not evidence, however the write is executed |

Where the same records are built, they serve both. Expiry and replay protection belong to
the confirmations themselves and are needed under either trigger. Stage 2 as designed
triggers neither: the UI executes, and nobody has yet required proof of approval. If the
second trigger appears, that is a requirement change, not an agent-execution change.

## Alternatives considered

- **Agent service account with a global read-only role.** Rejected: it is the confused-deputy
  problem; every employee gains the agent's read reach.
- **Agent authenticates as itself and sends a "user" header.** Rejected: the backend would
  trust the agent's assertion about who the user is, which recreates the same flaw one hop
  later.
- **Each component keeps its own definition of the proposal** (a Java class in the agent, a
  TypeScript type in the UI, a list of names in the backend). Rejected (amendment 2): the
  copies drift silently, and the failure shows up as a valid proposal the UI refuses or, worse,
  an invalid one a component accepts. One schema and shared examples make drift a build
  failure.
- **The agent returns a ready-made request (endpoint, method, body).** Rejected: the model
  would choose what the UI sends, so injected text could steer the UI at any endpoint the
  employee can reach. A closed set of operations in the schema bounds the worst case to one
  named change that the employee reviews.
- **Agent executes confirmed writes (replaying the approved payload).** Rejected for now: it
  needs write-capable agent credentials and server-side proposal integrity (approval records,
  replay protection, expiry, the execution-integrity trigger). Having the UI execute the
  write avoids all of that and keeps the agent write-incapable by construction. It does not
  by itself provide verified evidence of approval; that is the separate trigger described
  under staged delivery.
- **Forward the employee's password or Basic header through the agent.** Rejected (amendment
  1): the agent would handle a long-lived, fully privileged secret that works everywhere, and
  every log line, prompt and crash dump becomes a place it can leak from.
- **The agent performs the token exchange** (the original wording of amendment 1). Rejected
  (amendment 8): the agent would have to receive the employee's write-capable token to
  exchange it, so it would hold a token that could write, however briefly, and "the agent
  cannot write" would depend on its behaviour instead of on the design. The UI performs the
  exchange so the agent only ever sees the attenuated token.
- **Pass the employee's own access token straight through to the API.** Rejected: it is
  short-lived, but it carries the employee's full authority, writes included, so the agent
  would no longer be write-incapable by construction. The token must be attenuated first.
- **Rely on OIDC or Cognito to solve delegation.** Rejected as insufficient (amendment 1): an
  identity provider authenticates the employee; it does not narrow the agent's authority or
  make the backend refuse writes that arrive through the agent.
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

- The platform needs a token issuer, key management and a token exchange before the agent
  can make any call, so the login and token work is a prerequisite for the agent stages, not
  something that can be deferred past them.
- Moving the API from HTTP Basic to token validation touches every client, including the
  existing UI, the Postman collection and the CI smoke tests.
- Until a delegated token exists there is no way to run the stage-1 agent against a secured
  backend, so early agent development runs with security disabled (no identity).
  [ADR-0009](0009-login-and-authentication-mechanism.md) is to specify a test-only way to
  produce employee and delegated tokens, so the backend's cap and the stage-1 role-by-tool
  matrix can be tested before the login flow and a real issuer exist.
- The contract lives in a shared directory and a change to it must update the agent and the
  UI, and the backend when an operation name changes, in the same pull request, which is a
  deliberate coupling.
- The UI needs a proposal renderer and confirm handler per write operation, and a `GET` at
  proposal time plus another at confirmation time.
- Until optimistic concurrency exists, a lost update is still possible in the milliseconds
  between the confirm-time fetch and the PUT (decision 5). Accepted for the portfolio
  demonstration only; it blocks any production-ready claim.
- Optimistic concurrency touches every updatable resource's schema in four database
  dialects, the OpenAPI spec, the UI and the CI tests, so it is a sizeable PR of its own.
- A 403 from the backend surfaces in the assistant as "not permitted", so the agent and UI
  need clear wording for it.

## Security implications

- **Authentication:** employee passwords go only to the token issuer, over TLS; the agent
  and the API handle short-lived signed tokens. Tokens are never logged or placed in prompts.
- **Delegation:** the agent receives only an attenuated token (`act` claim, read scope,
  minutes of lifetime) from the UI, and the backend, not the agent, enforces the cap. The
  token signing keys are held in Secrets Manager and are not readable by the agent.
- **Authorization:** enforced by the backend with the employee's roles; the agent's
  restrictions only narrow.
- **Secrets:** employee credentials and the LLM API key never reach logs, prompts, images or
  repository config. The LLM key lives in Secrets Manager.
- **Data exposure:** prompt content leaves the system for the LLM provider, hence decision 7.
- **Prompt injection:** cannot escalate beyond the requesting employee's rights, and cannot
  cause a write because the agent has no write tool.

## Assumptions to confirm

- ⚠️ The first issuer is one we run ourselves, with Cognito as the later employee login. To
  my knowledge Cognito does not offer RFC 8693 token exchange natively, so minting the
  delegated token would stay in our own component (or a Lambda) that validates the Cognito
  token; this must be checked when Cognito is adopted.
- Token lifetimes (employee token and delegated token), the claim names, and whether roles
  travel in the token or are reloaded from the database per request are not decided; the
  login and token ADR ([ADR-0009](0009-login-and-authentication-mechanism.md)) decides them.
- How often the UI obtains a fresh delegated token (per chat request, or per session with a
  short lifetime) and how it is passed to the agent is not decided; ADR-0009 decides it. The
  agent must never receive the employee's own token or password.
- Structured audit log lines are enough for the MVP (decision 9); a persistent audit table
  and a tamper-resistant approval trail are deferred. Whether to audit all owner writes (a
  baseline, marked server-side as carrying no AI claim) and not only AI-assisted ones is not
  decided.
- The header names `X-AI-Operation` and `X-AI-Proposal-Id` are proposals.
- The contract directory and its two files are decided (`contracts/ai-proposals/`,
  `proposal.schema.json` and `examples.json`); how the schema is organised once there is more
  than one operation is not.

## Revisit if

- The agent (or any component other than the employee's own client) ever needs to execute
  writes: that is the execution-integrity trigger, so add server-side approval records,
  replay protection and expiry (stage 3), and a distinct non-escalating credential.
- Anyone needs proof that an employee was shown and confirmed a proposal, whoever executes
  the write: that is the verified-evidence trigger, and it needs the same records with
  integrity protection (decision 9), even if the UI still executes.
- A fourth resource-scoped role is added: it needs its own hierarchy line (ADR-0006).
- Optimistic concurrency lands (`@Version`, `If-Match`): remove the confirm-time comparison
  and rely on 412 (decision 5).
- Anyone proposes to call the system production-ready, or to widen the AI write operations,
  before optimistic concurrency exists: that is excluded by the gate above.
- Cognito (or another identity provider) replaces the first issuer: revalidate the delegated
  token exchange (performed by the UI against the issuer) and the `act`-based cap, since
  the provider authenticates the employee but does not provide delegation.
- The login and token ADR changes the token claims or lifetimes: update decision 1 here.
