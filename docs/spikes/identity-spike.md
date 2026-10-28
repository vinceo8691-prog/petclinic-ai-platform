# Identity spike: Cognito employee authentication and the delegation grant

Tracking document for the limited identity spike approved on 2026-10-26. It was written by Claude Code for the project owner
("you" is the owner; "I" is Claude) and updated at the end of each session; Claude stopped after each session so the owner could
read it. It is kept as the log of the spike's evidence and reasoning. Sections marked **historical** record advice that was
later overtaken by decisions; the current decisions are in the ADRs and the roadmap. **Nothing here is production code.** (When this document was written no ADR
had been amended. Outcome, 2026-10-26: the owner chose Option 1b; the resulting decisions are drafted in ADR-0007
amendment 9, ADR-0008, ADR-0009, ADR-0010 and ADR-0013 and are pending the owner's acceptance. The estimate in this
document was superseded by a revised, itemized one: about 15.5 to 20.5 focused days of essential identity work.)

**About the identifiers:** the AWS account number, user pool id, app client ids and login domain of the disposable spike
resources are replaced by placeholders (`<aws-account-id>`, `<spike-user-pool-id>`, `<spike-spa-client-id>`,
`<spike-agent-client-id>`, `<spike-login-domain>`) because this repository is public. To rerun the scripts, set the
environment variables `SPIKE_POOL_ID` and `SPIKE_SPA_CLIENT_ID` (and `SPIKE_WORKDIR`, the folder outside the repository that
holds the generated passwords and the login domain). The real values are kept locally and never committed.

Branch: `feature/PC016_identity_spike` (worktree `petclinic-platform-spike`), prototype in `spikes/identity/`, kept
apart from `spring-petclinic-rest`, `petclinic-ui` and `petclinic-ai-agent`.

## Status

| Session | Scope | Planned | State |
|---|---|---|---|
| 1 | Cognito employee authentication (authorization code + PKCE), Spring Security mapping of the three roles, fail closed | 2 to 3 hours | Done, reviewed (you approved going on) |
| 2 | The agent's machine identity, and the delegation grant (Option 1b), including a dedicated read-only owner lookup endpoint | 3 to 4 hours | Done, reviewed |
| 3 | Security tests (invalid, expired, revoked, missing, mismatched grants; impersonation; permission limits), findings, effort estimate, ADR and backlog recommendations | 3 to 4 hours | **Done.** Owner chose Option 1b on 2026-10-26 |

Time box: about 10 to 12 focused hours in total, with a checkpoint if a major blocker appears. Session 1 took roughly
2 to 3 hours and Session 2 roughly 3 to 4 hours of focused work by my estimate (wall-clock was not measured precisely), so
and Session 3 roughly 3 to 4, so about 9 to 11 hours were used, inside the 10 to 12 hour box. **No blocker, and Option 1b
passed every security requirement tested.** The spike is committed on this branch and tagged `spike/identity-v1`; the code
stays on the branch and is not merged into `main`.

## Constraints from the approval

- Option 1b (short-lived, backend-controlled delegation grants) is the preferred architecture; if it proves too complex
  or fails a security requirement, **stop and report**, and Option 2a is evaluated next.
- Not implemented in this spike: Keycloak, Spring Authorization Server, the KMS/JWKS delegation issuer.
- Disposable infrastructure only; the prototype stays separate from production code until the design is approved.
- Prefer a dedicated read-only owner lookup tool endpoint with minimal owner and pet data.
- Deployed authentication must fail closed.
- Do not amend ADRs or begin production work until the final architecture is approved.

## Spike resources (disposable, in AWS account <aws-account-id>, us-east-1)

| Resource | Value | Notes |
|---|---|---|
| User pool | `<spike-user-pool-id>` ("petclinic-identity-spike") | Essentials tier, tagged `purpose=identity-spike`, `disposable=true`; admin-created users only; MFA off for the spike |
| SPA app client | `<spike-spa-client-id>` ("petclinic-spa") | Public, no secret; authorization code only; callback `http://localhost:5199/callback`; access token 5 minutes; no password flows |
| Login domain | `<spike-login-domain>` (hosted login) | `https://<spike-login-domain>.auth.us-east-1.amazoncognito.com` |
| Groups | `ADMIN`, `OWNER_ADMIN`, `VET_ADMIN` | Map to `ROLE_ADMIN`, `ROLE_OWNER_ADMIN`, `ROLE_VET_ADMIN` |
| Test users | `spike-admin` (ADMIN), `spike-owner` (OWNER_ADMIN), `spike-vet` (VET_ADMIN), `spike-nogroup` (none) | Passwords were generated and are kept **outside the repository**, never committed |
| Resource server | identifier `petclinic-api`, one scope `agent.read` (Session 2) | The scope names the agent's machine identity only; it names no PetClinic data |
| Agent app client | `<spike-agent-client-id>` ("petclinic-agent", Session 2) | Confidential (has a secret, kept outside the repository); client-credentials grant only; scope `petclinic-api/agent.read`; no callback URLs, no login flows; access token 5 minutes |

An existing user pool in the account (`<unrelated-existing-pool-id>`, "User pool") was not touched. Cost so far: 0 USD (Essentials
free tier). Teardown: `spikes/identity/infra/teardown.py` deletes only a pool tagged `purpose=identity-spike`; it has
**not** been run, and will wait for your word at the end of the spike.

## Session 1: what was built

| Piece | Where | What it shows |
|---|---|---|
| Resource server prototype | `spikes/identity/src/main` | Spring Boot 4.1.1, Java 25, JWT validation against Cognito's key set, issuer, expiry, token use and **app client** checks; group to role mapping; the ADR-0006 hierarchy; method security; fail-closed startup guard |
| 24 automated tests | `spikes/identity/src/test` | 16 employee-authentication cases and 8 fail-closed startup cases, run with locally signed tokens shaped like Cognito's |
| Live check against real Cognito | `spikes/identity/live/pkce-login.mjs` | Drives the real hosted login with a browser, performs authorization code + PKCE for every test user, attacks PKCE, then calls the API with the real tokens |
| Provisioning and teardown | `spikes/identity/infra/` | The exact commands and settings used; passwords are written to a folder outside the repository |

## Session 1: results

### Authorization code + PKCE with the real hosted login

| Test | Result |
|---|---|
| Normal login with a correct PKCE verifier, for all four users | Works: code issued, tokens returned, `state` echoed |
| Wrong `code_verifier` | Rejected (`invalid_request`) |
| `code_verifier` missing when a challenge was sent | Rejected (`invalid_request`) |
| Replaying an authorization code | Rejected (`invalid_grant`) |
| Code exchanged with a different `redirect_uri` | Rejected (`unauthorized_client`) |
| **Login with no PKCE at all** | **Accepted: a code and an access token were issued.** Cognito does not enforce PKCE for a public client |
| Password flow (`USER_PASSWORD_AUTH`) on the SPA client | Refused ("flow not enabled for this client") |

### What a real Cognito access token contains

| Claim | Finding |
|---|---|
| `aud` | **Absent.** The caller is identified by `client_id`, so the "audience" check must be a client check (the prototype does this) |
| `token_use` | `access`; an ID token is distinguishable and is rejected at the API (401, tested live) |
| `cognito:groups` | A list, e.g. `["OWNER_ADMIN"]`; **the claim is absent for a user with no groups** |
| lifetime | 300 seconds (5 minutes); the app client rejected 1 minute ("Invalid range for token validity"), so **5 minutes is the minimum Cognito allows** |
| other claims | `sub`, `username`, `scope`, `iss`, `jti`, `origin_jti`, `auth_time`, `event_id`, `version` |
| refresh token | Returned to the client (8 hours as configured); a browser must keep it in memory only |

### What the API did with real tokens (live, status codes)

| User (group) | whoami | GET owners | POST owners | GET vets | ID token used as an access token |
|---|---|---|---|---|---|
| `spike-admin` (ADMIN) | 200 | 200 | 201 | 200 | 401 |
| `spike-owner` (OWNER_ADMIN) | 200 | 200 | 201 | **403** | 401 |
| `spike-vet` (VET_ADMIN) | 200 | **403** | **403** | 200 | 401 |
| `spike-nogroup` (none) | 200 | **403** | **403** | **403** | 401 |
| no token | 401 | | | | |

The ADMIN-only user reaches both owner and vet endpoints through the ADR-0006 hierarchy, exactly as the current backend
behaves. A user with no group has no roles and is refused everywhere that needs one.

### Automated tests (local signing key, real filter chain), all passing

- Rejected with 401: no credential; garbage token; expired token; wrong issuer; token for another app client; ID token;
  tampered payload (groups edited to `ADMIN`); unsigned `alg: none`; token signed by another key.
- Role mapping: ADMIN-only passes both resource roles; OWNER_ADMIN passes owners only; VET_ADMIN passes vets only.
- No roles, therefore 403, for: an empty group list, a missing groups claim, unknown groups (`SUPERUSER`, `ROLE_ADMIN`,
  lower-case `admin`), and a groups claim that is a string instead of a list.

### Fail closed

| Check | Result |
|---|---|
| `deployed` profile with no settings | Refuses to start; the message names `issuer-uri` and `spa-client-id` as missing (live and in tests) |
| `deployed` profile with security disabled | Refuses to start |
| `deployed` profile with a blank issuer, a non-https issuer, or no client id | Refuse to start (tests) |
| Default value of the security switch | **On.** Only an explicit `spike.auth.enabled=false` turns it off (tests); this is the inverse of the production backend's current default |
| Key source unreachable (issuer pointed at a closed port), with a real valid token | **401**: the token is refused, never accepted. Spring returns 401, not 503, for a key-fetch failure |
| Real access token as it ages (lifetime 300 s) | Accepted at 330 s (inside Spring's default 60 s clock skew), **rejected (401) at 375 s**. So a token is usable for up to about 6 minutes, and the 60 s skew is a real extension (finding F9) |

## Findings from Session 1

| # | Finding | Effect |
|---|---|---|
| F1 | Authorization code + PKCE works against real Cognito, and Cognito rejects wrong verifiers, replayed codes and mismatched redirect URIs | The employee login flow is viable as designed |
| F2 | **PKCE is not enforceable on the server.** The app client has no setting for it, and a login without PKCE succeeds | Residual risk R1 below; protection rests on the SPA always sending PKCE and on strict redirect URIs |
| F3 | Cognito access tokens have no `aud`; `client_id` identifies the client | ADR-0009 and the validator must check `client_id`; confirmed working |
| F4 | The minimum access token lifetime is 5 minutes | A delegated credential issued by Cognito could not be shorter than 5 minutes. A backend-controlled grant (Option 1b) can be shorter, which is an advantage for 1b |
| F5 | `cognito:groups` is absent for a user with no groups | The converter must treat "absent" as "no roles"; it does, and a test proves it |
| F6 | Spring Boot 4.1.1 with Java 25 and the OAuth2 resource server starter works with Cognito tokens; the validators, converter, role hierarchy and `@PreAuthorize` behave as in the current backend | Spring Security integration is low risk for employee authentication |
| F7 | A key-source failure gives 401 (fail closed), not 503 | Acceptable. The requirement in ADR-0009 "503" would need a custom handler; I recommend relaxing it to "rejected" |
| F8 | The startup guard and the inverted default work in a real jar | The fail-closed requirement is cheap to meet |
| F9 | Spring accepts a token for up to 60 seconds after `exp` (default clock skew) | Consider a smaller skew; noted for Session 3 |
| F10 | The SPA client has no password flow and users are admin-created only | No self-registration or password-in-browser path exists |

## Unresolved security risks so far

| # | Risk | Mitigation to carry into the design |
|---|---|---|
| R1 | PKCE optional on Cognito: a client that forgets it is accepted | UI always sends PKCE; register only exact redirect URIs; test in the UI; never enable implicit or password flows |
| R2 | Access tokens are stateless: a logout or a revoked refresh token does not stop an access token before it expires (up to 5 minutes) | Accept for employees; the delegation grant (Session 2) is revocable at once, which is the credential that matters for the agent |
| R3 | Group changes reach the API only with the next token (up to 5 minutes) | Accept, or reload roles for sensitive calls; document |
| R4 | A refresh token (8 hours) goes to the browser | Hold in memory only (ADR-0009 rule); shorten the lifetime; decide hosted login versus a custom screen |
| R5 | Local development cannot use Cognito tokens offline | Security off explicitly for local use; tests use locally signed tokens (as in this spike) |

## Session 2: what was built

| Piece | Where | What it shows |
|---|---|---|
| The agent's machine identity | `infra/create-agent-client.py`, `live/machine-identity.mjs` | A Cognito resource server with one scope and a confidential client that can only use client credentials |
| Principals | `AgentIdentityAuthentication`, `DelegatedAuthentication`, `AgentAwareConverter` | A machine token becomes a principal with **no authorities**; only a grant turns it into "employee X acting through the agent" |
| The delegation grant | `DelegationGrant`, `GrantStore` (in memory), `GrantService`, `AgentGrantController` | An employee, with their own token, creates a grant and receives only a handle; the grant is stored hashed, bound to one agent client, and lives minutes |
| Using a grant | `AgentGrantFilter` | The agent presents its machine token **and** the handle; neither works alone |
| The agent-origin ceiling | `AgentOriginAuthorizationManager` | Deny by default: an agent under a grant may do only the operations in the grant (one: the owner lookup); a machine token without a grant may do nothing |
| The read-only tool | `AgentToolController`, `OwnerDirectory` | A dedicated owner lookup that returns an id, a name and each pet's name and type, and reports ambiguity instead of guessing |
| Live proof | `live/pkce.mjs`, `live/grant-flow.mjs` | A "UI" logs in through the real hosted page; a "stub agent" built from only a handle and its machine credentials records everything it holds, sends and receives |
| Tests | 42 in total (was 24) | Added 14 delegation tests and 4 tests over real HTTP |

The mechanism is about 450 lines of main code; the security-critical custom core (the filter, the ceiling and the grant
service) is about 200 lines. The whole prototype is 867 lines of main code and 711 lines of tests.

## Session 2: results

### The agent's machine identity (real Cognito)

| Check | Result |
|---|---|
| A client-credentials token | Issued: `sub` equals `client_id`, scope `petclinic-api/agent.read`, **no `username`, no groups, no `aud`**, 5 minutes |
| No secret, or a wrong secret | Refused (`invalid_client`) |
| A scope it was not granted (`openid`) | Refused (`invalid_scope`) |
| Password grant, or acting as a user | Refused (`unsupported_grant_type`, `invalid_grant`) |
| The public SPA client asking for client credentials | Refused |
| Client configuration | Only `client_credentials`, one scope, no callbacks, no explicit auth flows |
| **Standalone PetClinic permissions** | **None.** In the API its token becomes a principal with zero authorities: it gets 403 on every endpoint, including the lookup tool |

### The delegation grant, live against real Cognito (status codes)

The "UI" holds the employee's token; the "agent" holds only a handle and its own machine credentials.

| Attempt | Result |
|---|---|
| Employee (OWNER_ADMIN) creates a grant | 201: a handle, an id and an expiry; the response contains **no token** |
| Agent: owner lookup with machine token + handle | 200 |
| Machine token **alone** | 403 |
| Handle **alone** | 401 |
| Agent writes: POST, PUT, DELETE | 403 each |
| Agent reads any other endpoint (owners, vets, whoami) | 403 |
| Agent creates its own grant | 403 |
| Agent POSTs to the lookup endpoint | 403 |
| A made-up handle | 401 |
| **An employee token plus a handle** | 403 |
| A VET_ADMIN's grant used for the owner lookup | 403 (the employee's own roles still apply) |
| Ambiguous name "Davis" | 200 with `AMBIGUOUS` and 3 candidates (the prefix also matches "Davison"): never a guess |
| Revocation | lookup 200, employee revokes (204), the same lookup 401 at once |
| Expiry (the spike used a 25 second lifetime) | lookup 200 at once, 401 after waiting |

### What the agent never saw

The stub agent's inputs, every request it sent and every response it received were searched afterwards:
- the employee's access token: **absent**;
- the employee's Cognito subject (the employee's identity): **absent** (it exists only in the backend's grant record);
- the grant response contained no JWT.

### The tool returns the least that is needed

For a single match the result has exactly these fields: owner id, name, and each pet's name and type. Address, city,
telephone and visit text are never returned; a visit description containing an instruction aimed at a model ("Ignore previous
instructions ...") is in the source data and never appears in any response (tested). Results are `FOUND`, `AMBIGUOUS` (2 to
5 candidates), `TOO_MANY` (more than 5, no candidates) or `NOT_FOUND`.

## Findings from Session 2

| # | Finding | Effect |
|---|---|---|
| F11 | Cognito's machine token has `sub` equal to the client id and no user claims; the API turns it into a principal with no authorities | The machine identity needs no PetClinic permission at all, which satisfies "no standalone data permissions" |
| F12 | The grant mechanism works with standard Spring Security parts: a converter, a filter after the bearer filter, and an `AuthorizationManager` as the deny-by-default ceiling | Integration risk is low; no signing key, no JWKS, no second issuer |
| F13 | Two independent secrets are needed (the machine token and the handle); neither is enough alone, and an employee token cannot use a handle | Meets the binding goal of Option 1b |
| F14 | The employee token and the employee's identity never reach the agent, proven by searching everything the stub agent held, sent and received | The central ADR-0007 requirement holds |
| F15 | The allowlist is default-deny: every write, every other read and grant creation are refused for agent-origin requests | The agent cannot write by construction (also covered at the API: the agent has no write endpoint on its list) |
| F16 | A grant can be shorter than Cognito's 5 minute minimum (the spike ran 25 seconds live) and is revocable at once | Stronger than a Cognito-issued delegated token on both counts |
| F17 | **MockMvc tests hid a real defect.** Answering with `sendError` made the servlet container re-dispatch to `/error`, where the ceiling turned 401 into 403 (seen only in the live run). Fixed with `setStatus`, proven by a mutation check (the new real-HTTP tests fail when the old behavior is restored) | Production security tests must include real-server tests, not only MockMvc. Fail-closed held either way; only the status code was wrong |
| F18 | Grants live in memory here and vanish on restart (which fails closed); several API tasks need a shared store | Production needs a database table (a Flyway migration), a cleanup job, a per-employee cap and metrics |
| F19 | Cognito bills machine-token requests at 0.00225 USD each with no free tier; the stub agent fetches one token per instance | The real agent must cache its machine token until it nears expiry |
| F20 | The employee roles in a grant are a snapshot taken when it is created; a grant cannot outlive 5 minutes, so the snapshot cannot be stale for long | Acceptable, and the same bound as R3 |

## Unresolved security risks (as of Session 2; the final register is in Session 3)

| # | Risk | Mitigation to carry into the design |
|---|---|---|
| R1 | PKCE optional on Cognito | UI always sends PKCE; exact redirect URIs only; no implicit or password flows |
| R2 | Employee access tokens cannot be revoked before they expire (up to about 6 minutes with clock skew) | Accept; the agent's credential (the grant) is revocable at once |
| R3 | Group changes reach the API only with the next token | Accept, or reload roles for sensitive calls |
| R4 | A refresh token (8 hours) goes to the browser | Memory only; shorten; decide hosted login (recommended) |
| R5 | Local development cannot use Cognito tokens offline | Security off explicitly for local use; tests use locally signed tokens |
| R6 | The grant store must be shared and durable across API tasks; a database outage must deny, not allow | Table, tests for store failure (Session 3), fail closed |
| R7 | The handle travels from the browser to the agent and could be logged by either | HTTPS only; redact the header in logs and traces; never in prompts; short life |
| R8 | The agent's client secret is a standing credential (no authority alone, but with a stolen handle it gives access for the grant's lifetime) | Secrets Manager; rotation plan; cache the machine token; minimum scope |
| R9 | A compromised agent can replay a valid handle for its lifetime | Bounded by the allowlist (one read endpoint) and the lifetime (minutes); revocable |
| R10 | No per-employee cap on grants and no rate limit yet | Add before production (Session 3 recommendation) |

## Session 3: what was added

| Piece | What it shows |
|---|---|
| Two hardening changes to the prototype, found necessary by earlier risks | A per-employee cap on active grants (429) and a defined answer when the grant store fails (503 with `Retry-After`, never access) |
| `GrantAttackMatrixTest` (18 tests, with a clock the test moves and a store that can fail) | Expired, revoked, missing, malformed, mismatched, impersonation, permission limit, cap, store outage, concurrency, and the agent's own token under attack |
| `GrantAttackOverHttpTest` (8 tests, real embedded server over real HTTP) | The key attacks again where the servlet container can rewrite statuses, including 15 malformed or forbidden requests |
| `LogLeakageTest` (1 test) | The whole flow at debug level; no handle and no token in any log line |
| A final live run against real Cognito with the finished code | Same results as Session 2 |

**69 tests pass** (was 42): employee authentication 16, fail-closed startup 8, delegation 14, status over real HTTP 4,
attack matrix 18, attacks over real HTTP 8, log leakage 1.

## Session 3: the attack matrix and its results

Every row is a test that passes; "over HTTP" means it is also checked against a real server.

| Attack or failure | What must happen | Result |
|---|---|---|
| **Expired**: use a grant one millisecond before, exactly at, and long after its expiry | Works before; refused at and after the instant | 200, then 401 at expiry (clock-driven test; also over HTTP) |
| Ask for a longer life or wider grant: `ttl`, `expiresAt`, roles, operations, subject and agent id in the query, headers and body | All ignored; the grant has the verified subject, only the employee's own roles, the one operation, the configured agent, the configured lifetime | Exactly that (stored record inspected) |
| **Revoked**: employee revokes, then the agent calls; revoke twice | Refused at once; second revoke harmless | 401 (also over HTTP); 204 twice |
| Another employee revokes my grant | Not allowed, grant stays alive | 404, still 200 for the agent |
| The agent revokes or inspects grants | Not allowed | 403 |
| **Missing or malformed**: empty, blank, repeated headers; truncated, extended, upper-cased, re-prefixed, prefix-less, stored-hash-as-handle, 10,000-character and space-prefixed handles | All refused, none crashes | 401 for each; a control character is stopped by the firewall (400) |
| 100 random guesses while a live grant exists | None succeeds | 100 of 100 refused |
| **Mismatched**: a grant resolved by a different agent client, by none, or with no handle | Refused | `resolve` returns nothing for each |
| A **validly signed** machine token from a client the API does not know | Refused | 401 |
| The agent's machine token itself: expired, wrong issuer, an ID token, forged signature | Refused like any token | 401 each |
| **Impersonation**: an agent invents roles or an employee in headers and parameters | Ignored; the VET_ADMIN's grant still cannot read owners | 403 |
| An employee presents a handle | Refused | 403 (also over HTTP) |
| An agent creates a grant, with or without a valid one | Refused | 403 |
| **Permission limits**: VET_ADMIN's grant; ADMIN-only grant; employee with no roles | Employee's own roles apply; hierarchy works; no roles means no grant | 403; 200; 403 |
| The employee loses a role after creating a grant | **Known limitation**: the grant keeps the snapshot until it expires or is revoked | Documented by a test; the cure is revocation (risk R3) |
| Cap: more than the allowed active grants | Refused; revoked and expired grants free places; others unaffected | 429 (also over HTTP); places freed |
| **Grant store fails** | Agent refused, never let through; recovers | 503 with `Retry-After: 5` and no data (also over HTTP); 200 after recovery |
| **Concurrency**: 8 threads x 150 calls while the grant is revoked | No request that starts after the revoke has returned succeeds | 0 violations; both successes and refusals seen |
| Agent under a grant tries writes and other endpoints: POST, PUT, DELETE, PATCH, other reads, whoami, grant creation | All refused | 403 each |
| Malformed paths with a valid grant: `..`, `;x=1`, `//`, `%2e`, trailing slash | Refused, no data | 401 (firewall and error dispatch) or 403; never a success |
| **Logs**: the full flow at debug level, searching every line | No handle, no token | **Failed first** (see F21), passes after the fix |
| **Fail closed**: deployed profile with missing or invalid settings; unreachable key source | Refuse to start; refuse tokens | Done in Session 1 |

## Findings from Session 3

| # | Finding | Effect |
|---|---|---|
| F21 | **A real leak was found and fixed.** At debug level Spring MVC logs the grant-creation response body, which contains the handle. The log-leakage test failed (it named the line); the response type now masks the handle in `toString`, and the test passes | Handles must be redacted at the source, and production logging must keep `org.springframework.web` out of debug. The agent's own logs must never record request headers |
| F22 | The per-employee cap works and is cheap (a count over the employee's active grants) | Risk R10 is mitigated; a per-employee rate limit is still advisable |
| F23 | A grant store outage is defined behavior: 503, `Retry-After`, no access, automatic recovery | Risk R6 is handled at the API; production still needs a durable shared store |
| F24 | Revocation held under concurrency (in memory, one instance) | With a database the guarantee needs reads from the primary, not a lagging replica |
| F25 | Malformed paths are refused, but statuses differ (401 or 403 or 400) because the firewall rejection reaches the error dispatch | Cosmetic; a custom rejected-request handler would make them a uniform 400 |
| F26 | Grant creation takes every value from the verified token and the server's configuration; nothing in a request can change them | Closes the "ask for more" family of attacks |
| F27 | The role snapshot is the one real semantic gap (R3): a grant can outlive a role removal by its lifetime | Revoke an employee's grants on logout and on role change; the short life bounds the rest |
| F28 | Statuses are a contract worth writing down: 401 grant or token problem (no reason given), 403 no grant or not allowed, 429 cap, 503 store failure, 400 firewall | Goes into the ADR-0008 amendment |

## Final risk register

| # | Risk | Status | Mitigation for production |
|---|---|---|---|
| R1 | Cognito does not enforce PKCE | Open | UI always sends PKCE; exact redirect URIs; no implicit or password flows; a UI test |
| R2 | Employee access tokens live up to about 6 minutes after logout or revocation | Accepted | Short life; the agent's credential is revocable at once |
| R3 | Roles in a token or grant are a snapshot | Mitigated | Revoke grants on logout and role change; 5 minute bound |
| R4 | A refresh token goes to the browser | Open | Memory only; shortest useful life; hosted login |
| R5 | Local development cannot use Cognito offline | Accepted | Security off explicitly for local; tests use local keys |
| R6 | Grant store must be shared, durable and read-your-writes | Partly mitigated | Database table, primary reads, 503 on failure (tested) |
| R7 | The handle travels browser to agent and may be logged | Mitigated at the source | Redacting response type (tested); HTTPS; redact headers in every log, trace and access log; keep web debug logging off |
| R8 | The agent's client secret is a standing credential | Open | Secrets Manager; rotation runbook; cache the machine token; no authority without a grant |
| R9 | A compromised agent can replay a live handle for its lifetime | Accepted | One read endpoint, minutes, revocable, capped |
| R10 | No per-employee cap or rate limit | Cap done; rate limit open | Add a rate limit at the API or the load balancer |
| R11 | Cognito becomes a dependency of every login | New | Cached keys keep existing tokens valid; a Cognito outage blocks new logins; accept and document (the AI features fail, the rest of the app already needs login) |
| R12 | A compromised agent host sees every handle in flight | New | Handles are short and capped; the agent holds no stored data; rotate the machine secret on suspicion |

## Verification against your requirements

| Your requirement | Result |
|---|---|
| Cognito employee authentication with authorization code + PKCE; Spring mapping of ADMIN, OWNER_ADMIN, VET_ADMIN | Met (Session 1); PKCE cannot be enforced server side (R1) |
| The agent's machine identity has no standalone PetClinic data permissions | Met: zero authorities, 403 everywhere |
| Short-lived, backend-controlled delegation grants (Option 1b) | Met: hashed, bound to the agent, 5 minute ceiling, revocable, capped |
| The employee's token never reaches the agent; agent-origin requests cannot write | Met: proven by search over everything the agent held, and by 403 on every write and other read |
| Invalid, expired, revoked, missing and mismatched grants; impersonation; permission limits | Met: the matrix above; one documented limitation (R3) |
| A dedicated read-only owner lookup returning minimal data | Met: id, name, pets' name and type; no address, telephone or visit text; ambiguity never guessed |
| Deployed authentication fails closed | Met: startup guard, safe default, unreachable key source refused, store outage refused |
| No Keycloak, Spring Authorization Server or KMS/JWKS issuer | Respected |
| Disposable infrastructure, separate from production code | Respected; teardown pending your word |

**Conclusion: Option 1b is feasible and met every security requirement. It was not too complex: the security-critical core
is about 200 lines. Option 2a is not needed.**

## Estimated production implementation effort (historical)

> **Superseded (2026-10-26).** The owner rejected the 29 to 42 day figure below, which applied a flat 3 to 5 times multiplier to
> the spike's effort. A revised, itemized estimate replaced it: about 15.5 to 20.5 focused days of essential identity work
> (Release 1 about 9.5 to 13.5, Release 2 about 6 to 7), with about 5 to 8 days of optional hardening deferred. The current plan
> is in the roadmap (Release 1 is 7 weeks; the whole plan is 19 weeks). The table is kept as the original estimate.

Basis: the spike took about 9 to 11 focused hours for a prototype of about 910 main lines and 1,480 test lines. Production adds
integration with the real code, a database, the UI, infrastructure and operations, which in my experience multiplies the spike
by roughly 3 to 5. These are focused developer days, rough (plus or minus 40 percent), not calendar days.

| Work package | Release | Focused days |
|---|---|---|
| Write ADR-0009, the ADR-0007 amendment and the ADR-0008 update from this document | 1 | 1.5 to 2 |
| Cognito in infrastructure as code (pool, groups, SPA client, domain, resource server, per environment) | 1 | 2 to 3 |
| Resource server in `spring-petclinic-rest`: validators, group mapping, hierarchy, one-path 401 and 403, retire the JDBC Basic path outside local use, role matrix over the existing 36 secured methods | 1 | 4 to 6 |
| Fail closed in the real application: startup guard, inverted default, Newman and Postman and CI updates, docs | 1 | 2 to 3 |
| UI login: hosted login with PKCE, in-memory tokens, silent re-login, 401 and 403 handling, role-aware navigation, logout, tests | 1 | 5 to 7 |
| Agent-origin ceiling in the API (principals, converter, authorization manager, filter shell) and its tests | 1 | 2 to 3 |
| **Release 1 identity subtotal** | | **16.5 to 24** |
| Delegation grants in the API: table and migration, store, service, endpoints, cap, cleanup job, metrics, audit | 2 | 4 to 6 |
| The owner lookup endpoint over the real service with projection, ambiguity, tests | 2 | 2 to 3 |
| Agent side: client-credentials client with token cache, handle in the tool context, sanitized errors | 2 | 2 to 3 |
| Port the attack matrix, the real-HTTP tests and the log-leakage test to production code | 2 | 3 to 4 |
| Operations: secret in Secrets Manager, rotation runbook, alarms, log redaction rules | 2 | 1.5 to 2 |
| **Release 2 delegation subtotal** | | **12.5 to 18** |
| **Total** | | **about 29 to 42 focused days (6 to 8.5 weeks)** |

Option 2a for comparison would add roughly 2 to 3 days (KMS, signing, JWKS, two-issuer configuration, a test key) for the same
ceiling, so 1b is not the more expensive path.

**What this means for the roadmap.** Release 1 also holds AWS hosting, Flyway, PostgreSQL, pets and the demo in 5 weeks;
identity alone is about 3.5 to 5 weeks of focused work. Release 2 also holds the agent service, the contract and the
confirmation screen in 3 weeks; delegation alone is about 2.5 to 3.5 weeks. The weeks are targets, so I recommend moving them
(below) rather than cutting the security work.

## Recommended ADR amendments (historical; these were drafted afterwards)

> **Superseded (2026-10-26).** These recommendations were turned into drafts: ADR-0007 amendment 9, ADR-0008, ADR-0009,
> ADR-0010 and ADR-0013 (all pending the owner's acceptance). Where they differ, the ADRs win. Notably the employee access token
> is proposed as **15 minutes with no refresh token**, not the 5-minute minimum recommended below (5 minutes remains right for the
> agent's machine client).

**ADR-0007**
- Decision 1, "Required mechanism" and amendment 8: replace the token exchange and the `act` JWT with a **delegation grant**:
  the UI calls `POST /api/agent-grants` with the employee's own token; the backend stores a grant (hash of a 256-bit handle,
  the employee, the employee's roles, the agent client, read scope, an allowlist, a lifetime of at most 5 minutes) and returns the
  handle; the UI passes only the handle to the agent for each chat message; the agent calls the API with its machine token and
  the handle. Keep the intent: no password or employee token reaches the agent, the UI performs the step, the backend caps.
- Decision 1, issuer path: Cognito for employee authentication from the first deployed release. Keep the sentence that an
  identity provider alone does not provide delegation, and cite that Cognito has no RFC 8693 exchange.
- Decision 2: the agent holds a **machine credential that carries no authority** (and a model key if one is used); change "its
  only secret is the model key".
- Decision 6: key the ceiling on the principal type and the grant, with an allowlist of operations (default deny).
- Alternatives considered: add Option 2a (a delegated JWT signed with a KMS key) as rejected for now, with the reasons; state
  explicitly why a grant is not "the agent asserts a user": the identity comes from a backend record created by an
  employee-authenticated call and bound to the agent's own credential.
- Production gate 1: read "token-based authentication with delegation" to include grants. Decision 9: the audit event carries the
  grant id. Add the handling rules for the handle (never logged, never in a prompt, redacted at the source).

**ADR-0009** (move from placeholder to Proposed)
- Decide: an Amazon Cognito user pool (Essentials), hosted login, authorization code with PKCE, a public SPA client, admin-created
  users, groups `ADMIN`, `OWNER_ADMIN`, `VET_ADMIN` mapped to the `ROLE_` names; access token 5 minutes (the minimum), validators
  check `client_id` (no `aud`), an absent or unknown group claim means no roles; tokens in memory only; a smaller clock skew.
- Record that PKCE cannot be enforced by Cognito, so the UI must always send it and only exact redirect URIs are registered.
- Fail closed: a deployed profile refuses to start unless security is on and the issuer, client ids and key source are valid;
  invert the `petclinic.security.enable` default; a key-source failure is a refusal (401), so relax the earlier "503" wording.
- Accounts: users live in Cognito; the Add user screen is dropped; `POST /users` and the BCrypt tables stay for local and test use or
  are retired.
- Test facility: locally signed tokens in test code only; grants inserted directly; neither exists in a deployed build.

**ADR-0008**: add the status contract (401 for any token or grant problem without a reason, 403 for no grant or not allowed with
the "not permitted via the assistant" code, 429 for the cap, 503 with `Retry-After` for a store outage, 400 for firewall rejections).

**ADR-0010**: add the Cognito resources, the grant table (a Flyway migration), the agent client secret in Secrets Manager, no KMS,
and the logging rule (no debug logging of `org.springframework.web` in deployed profiles).

## Recommended backlog and roadmap changes (historical)

> **Superseded (2026-10-26).** The backlog and roadmap were updated by the owner's decisions, and the item numbers below no
> longer match: **B-087** is now the API custom domain and **B-088** is revoking grants on logout and role change. Release 1 is
> 7 weeks and the plan is 19 weeks, not the roughly 21 weeks suggested below. Use the backlog and roadmap, not this section.

**Backlog (by id)**

| Id | Change |
|---|---|
| B-004 | Smaller: write up the decisions above and accept ADR-0009 (the design work was done by this spike) |
| B-005 | Becomes "resource server with Cognito tokens, group mapping, one-path 401 and 403"; size L stays |
| B-006 | Hosted login with PKCE; tokens in memory; role-aware navigation |
| B-018 | Becomes "the agent-origin ceiling: principals, converter, authorization manager, default deny" (Release 1) |
| B-085 | Becomes "delegation grants: table, service, endpoints, cap, cleanup, audit" (Release 2); no exchange endpoint, no key |
| B-019 | The owner lookup endpoint is now a designed, tested dedicated endpoint in the API; build it over the real service |
| B-086 | Keep; add "invert the default" and the deployed-profile guard as specified here |
| B-066 | Superseded (already) |
| Screen 14 (Add user) | Drop; users are administered in Cognito |
| New | **B-087** Cognito in infrastructure as code (Release 1); **B-088** real-server security test suite (authentication, ceiling, grants, log leakage) started in Release 1, extended in Release 2; **B-089** agent machine-token client with caching, secret handling and rotation runbook (Release 2); **B-090** grant lifecycle operations: cleanup job, metrics and alarms (Release 2); **B-091** log redaction policy across the API, the agent and the load balancer (Releases 1 and 2); **B-092** (Should) revoke an employee's grants on logout and on role change; **B-093** (Could) a uniform rejected-request handler |

**Roadmap**
- Release 1 and 2 scopes keep the same split (the ceiling in Release 1, the grants in Release 2) but their weeks should move: the
  estimate suggests Release 1 at about 7 to 8 weeks and Release 2 at about 5 to 6, which makes the plan roughly 21 weeks instead of
  17. The alternative is to cut scope; I do not recommend cutting the security work.
- Release 1 acceptance criterion 11 (the allowlist) stays; add "the real-server security suite passes". Release 2 criterion 12 is
  now about grants, not an exchange. The Release 2 owner lookup criterion 10 is already satisfied in design by this prototype.
- Remove the Cognito versus self-run comparison from Release 1's open decisions once you approve.

## Decisions for you (historical; all answered)

> **Answered (2026-10-26 to 2026-10-28):** 1. Option 1b approved. 2. Release 1 extended to 7 weeks (plan: 19 weeks). 3. The spike
> AWS resources are kept for now; teardown has not been run. 4. The spike was committed and pushed (sanitized) as branch
> `feature/PC016_identity_spike`, tag `spike/identity-v1`.

1. **Do you approve Option 1b as the final architecture?** If yes, I would then draft the ADR amendments and the backlog and
   roadmap changes for review (this document only recommends them).
2. **The schedule:** accept longer Release 1 and 2 weeks (my recommendation), or cut scope elsewhere?
3. **The spike's AWS resources:** keep them until you have reviewed and approved (my suggestion, they cost nothing at this size and
   the tests can be re-run), or delete them now with `spikes/identity/infra/teardown.py`? I have **not** run teardown.
4. **Commit Session 3 to the local spike branch**, as for Sessions 1 and 2?

## How to reproduce

```
SPIKE_WORKDIR=<a folder outside the repo>        # holds spike-users.json (generated passwords)
python spikes/identity/infra/provision-users.py  # needs the pool and client created from infra/*.json first
cd spikes/identity && ./mvnw test                # 69 tests (Sessions 1 to 3)
./mvnw package -DskipTests
java -jar target/identity-spike-0.0.1-SNAPSHOT.jar --server.port=9969 \
  --spike.auth.issuer-uri=https://cognito-idp.us-east-1.amazonaws.com/<spike-user-pool-id> \
  --spike.auth.spa-client-id=<spike-spa-client-id>
node spikes/identity/live/pkce-login.mjs         # Session 1: needs playwright-core and Microsoft Edge
python spikes/identity/infra/create-agent-client.py   # Session 2: the agent's machine identity
node spikes/identity/live/machine-identity.mjs        # Session 2: what the machine identity can and cannot do
node spikes/identity/live/grant-flow.mjs              # Session 2: the delegation grant end to end (start the API with
                                                      #   --spike.auth.agent-client-id=<id> --spike.auth.grant-ttl=PT25S)
```
