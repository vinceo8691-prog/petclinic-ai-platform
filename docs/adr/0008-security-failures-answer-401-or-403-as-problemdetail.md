# 8. Authentication and authorization failures answer 401 or 403 as ProblemDetail, through one path

Date: 2026-10-11

## Status

Proposed

## Context

A request that is not allowed must answer 401 (not authenticated) or 403 (authenticated but
not permitted), never 500. The history and the current state:

- **Until 2026-10-05 a denied `@PreAuthorize` check answered 500.** The check throws
  `AuthorizationDeniedException`, and the global `@ExceptionHandler(Exception.class)` caught it
  inside Spring MVC before Spring Security's `ExceptionTranslationFilter` ever saw it.
  [ADR-0006](0006-admin-role-hierarchy.md) recorded this as "current behavior, not fixed here".
- **It was fixed on 2026-10-05** (commit `1c5624c`) with a specific
  `@ExceptionHandler(AccessDeniedException.class)` in `ExceptionControllerAdvice` that answers
  403 with a `ProblemDetail`. `AuthorizationFailureStatusTests` proves it through the real filter
  chain (`springSecurity()`); a standalone `MockMvc` cannot, because it has no
  `ExceptionTranslationFilter`. The account is in
  [authorization-denied-status-code.md](../architecture/authorization-denied-status-code.md),
  which says it is not a decision, since there was no alternative to weigh.
- **So the status code today is correct, and this ADR does not describe a live defect.** What
  it decides is how failures behave once the platform moves to tokens and an agent-origin
  cap (ADR-0007, amendment 1 and decisions 1 and 6), when the current arrangement stops being
  enough.

What the current arrangement does and does not give:

| Situation | Today | Concern at the token milestone |
|---|---|---|
| No or wrong Basic credentials | 401 from the filter chain (`BasicAuthenticationEntryPoint`) | The body is not the `ProblemDetail` the rest of the API uses (to be confirmed when implemented) |
| Authenticated, wrong role | 403 `ProblemDetail` from the advice | Works |
| Denied while **anonymous** (a path that is public but method-secured) | 403 from the advice, never 401 | The advice cannot tell anonymous from authenticated, so a client is never told to authenticate. Not reachable today because `anyRequest().authenticated()` stops anonymous requests first |
| Invalid or expired token, or a failure raised in a filter we add (the agent-origin cap) | Does not exist yet | Needs defined status codes and body |
| Any other security exception thrown inside a controller call | Falls to the general `Exception` handler: 500 | Same trap as the original bug |

## Decision

1. **Two statuses, no 500.** Not authenticated, or invalid, expired or missing credentials or
   token: **401**, with a `WWW-Authenticate` header for the scheme in use. Authenticated but
   not permitted, including a request capped because it carries the agent's `act` claim
   (ADR-0007 decision 1): **403**. A security failure is never 404 and never 500.
2. **One path, owned by Spring Security.** Security exceptions are not handled by controller
   advice. The general `@ExceptionHandler(Exception.class)` must rethrow
   `AuthenticationException` and `AccessDeniedException` so `ExceptionTranslationFilter`
   handles them, and the filter chain is configured with a single `AuthenticationEntryPoint`
   (401) and a single `AccessDeniedHandler` (403). Failures raised by filters we add (token
   validation, the agent-origin cap) use the same two handlers. Because the translation
   filter knows whether the caller is anonymous, a denied anonymous request becomes 401, not
   403.
3. **One body.** Both handlers write the same RFC 7807 `ProblemDetail` as
   `ExceptionControllerAdvice` (`type`, `title`, `status`, `detail`, `timestamp`), with a
   stable `title` that clients can rely on, and a stable code that separates "your role lacks
   permission" from "not permitted via the assistant", so the UI can word the two differently.
4. **No leakage.** The body never names the role that was missing, token contents or the
   reason a token failed beyond a generic category. Authorization is evaluated before the
   method runs, so a denied caller learns nothing about whether a record exists.
5. **Logged, not exposed.** Denied attempts are logged at WARN with the principal (as the
   advice does today) and never with the token or credential.
6. **The interim stays until this is built.** The advice's `AccessDeniedException` handler
   remains the live mechanism; it is replaced, not layered over, when the filter-level
   handlers produce an identical body.

## Verification required when this is implemented

Each case is tested through the real filter chain (`springSecurity()`), never a standalone
`MockMvc`, and asserts status, `WWW-Authenticate` where relevant, and the `ProblemDetail`
body:

- no credentials, an invalid token and an expired token: 401;
- a valid user without the role: 403, for a v1 and a v2 endpoint, a read and a write;
- `ROLE_ADMIN` alone passes (hierarchy, ADR-0006) and `OWNER_ADMIN` is denied `VET_ADMIN`
  endpoints;
- a delegated (`act`) token attempting a write: 403 with the assistant-specific code;
- an anonymous request to a method-secured path that is not behind
  `anyRequest().authenticated()`: 401;
- a security exception thrown from deep inside a service is not turned into 500.

## Alternatives considered

- **Keep the `AccessDeniedException` handler in the advice permanently.** Works for the case
  it was written for, but it cannot distinguish anonymous from authenticated callers, it
  duplicates what Spring Security already does, and every new security exception type needs
  another handler or becomes a 500.
- **Remove the advice handler now.** Rejected: that reintroduces the original 500 until the
  filter-level handlers exist.
- **Return 404 for resources the caller may not see.** Rejected: method security runs before
  the lookup, so 403 is already uniform, and 404 would hide real missing records.

## Consequences

**Positive:**

- One definition of what a security failure looks like, for the UI, the agent and tests.
- A client is told to authenticate (401) instead of being told it is forbidden (403) when it
  has not logged in, which the login page needs.
- New security exception types cannot silently become 500.

**Negative / trade-offs accepted:**

- Two places are touched together (the general handler and the security configuration), so
  they must change in one pull request with the tests above.
- Until then, the 401 body from the filter chain may differ from the `ProblemDetail` shape.

## Security implications

Error responses are an information channel, so decisions 3 and 4 are security decisions: they
keep the bodies free of role names, token details and record existence. The 401 and 403
distinction also affects what an attacker can learn about whether a credential was valid
(a generic 401 for every credential failure).

## Assumptions to confirm

- The current 401 body is not a `ProblemDetail`; this has not been measured and must be
  checked when this is implemented.
- Whether the login endpoint (outside this ADR) is public and how its failures are reported is
  decided in the login and token ADR.
- The stable `title` and code values are proposals.

## Revisit if

- The platform adopts an identity provider (Cognito) whose resource-server defaults write
  their own error bodies: re-check decision 3.
- A new filter can reject requests: it must use the same handlers.
