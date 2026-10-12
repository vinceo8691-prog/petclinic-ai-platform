# 9. Login and authentication mechanism

Date: 2026-10-11

## Status

**Placeholder. Nothing has been decided.** This record exists so the login screen and the
authentication mechanism stay visible in the documentation until they are designed. It is
written in full, and moved to Proposed, after the UI screens are built (see
[project-status.md](../project-status.md)); the open questions below are what that
write-up must settle.

## Why this is its own ADR

Login and authentication serve every client of the API (the UI, the Postman collection, the
CI smoke tests, and later the agent), not only the AI assistant. The agent's needs are
requirements on this ADR, stated in
[ADR-0007](0007-ai-agent-acts-on-behalf-of-the-employee.md); the mechanism is owned here.

## Context: what is already known

| Source | What it already fixes |
|---|---|
| Today | Security is off by default (`petclinic.security.enable=false`); when on, HTTP Basic against the `users` and `roles` tables with BCrypt. The UI has no login and sends no credentials. Roles are `ROLE_OWNER_ADMIN`, `ROLE_VET_ADMIN` and `ROLE_ADMIN`, with the hierarchy in [ADR-0006](0006-admin-role-hierarchy.md) |
| ADR-0007, decision 1 | The employee's password never reaches the agent. A token issuer returns a short-lived signed token (employee as subject, an audience, an expiry, roles). The backend verifies it on every request. The UI exchanges it for a delegated read-only token with an `act` claim and gives only that to the agent, and the backend caps requests that carry `act`. First our own issuer, later Amazon Cognito. An identity provider alone does not provide delegation |
| ADR-0008 (Proposed) | Not authenticated is 401 with `WWW-Authenticate`; not permitted is 403; both as one `ProblemDetail` through one path |
| ADR-0003 | A single-page application needs explicit client-side handling of tokens and API errors |
| `CLAUDE.md` and the UI docs | Never put credentials in `VITE_*` variables; owner data and tokens are held in memory only, never in browser storage; security implications are called out in every change |
| Project plan | Login comes after the UI screens and before the agent; it blocks any non-local deployment |

## Open questions this ADR must decide

**Login screen and flow**
- What the screen collects and what it shows on failure (using the 401 and 403 rules of
  ADR-0008, without revealing whether the username or the password was wrong).
- Brute-force protection: rate limiting or lockout, and where it lives.
- What the UI does when a token expires mid-session, and what "logout" means (client discard
  only, or server-side revocation).
- Whether the UI shows or hides actions by role (a convenience only; the backend enforces).

**Tokens**
- Token format and signing (algorithm, key storage in Secrets Manager, rotation).
- Claim names, and whether roles travel in the token or are reloaded from the database per
  request (cost versus how fast a role change takes effect).
- Lifetimes for the employee token and the delegated token, and whether there is a refresh
  token or silent re-login.
- Where the browser holds the token. The rule so far is memory only, which means a page
  reload logs the user out unless a refresh mechanism exists; that trade-off is to be decided,
  not assumed.
- The token-exchange endpoint the UI calls to obtain a delegated token for the agent: its
  contract, what the issuer checks (a valid employee token, a registered agent as actor,
  read-only scope), the delegated token's lifetime and how often the UI refreshes it, how it
  is passed to the agent, and where the cap is enforced (the ADR-0007 decision 1
  requirement). The agent never receives the employee's own token.

**Accounts**
- How users and passwords are created, changed and disabled. Today `POST /users` is
  `ROLE_ADMIN` only and passwords are BCrypt-hashed; whether that is enough, and whether
  there is a self-service reset.
- Whether the first issuer is the backend itself or a separate small component.

**Testing the delegation without the full login flow**
- This ADR will specify a **test-only way to produce employee and delegated tokens**, so the
  backend's token verification, the `act` and scope cap, and the agent's role-by-tool matrix
  (ADR-0007, stage 1) can be tested before, and independently of, the login screen and a
  real issuer. It is a requirement on this ADR, and the mechanism is not chosen yet.
- Options to weigh when writing it: Spring Security's test support for in-process tests (a
  request post-processor that supplies a JWT with chosen subject, roles, `act` and scope),
  and a test-only signing key with a small helper for cross-process tests (the agent calling
  a running backend).
- Constraints it must meet, because a way to mint tokens is itself a security risk: it exists
  only in test sources or a test-only profile and is not compiled into or reachable from a
  deployed build; any key it uses is never a production key and is never stored in
  Secrets Manager; a deployed environment cannot enable it by configuration alone; and a test
  proves it is absent from the production profile.
- What it must cover: an employee token for each role, a delegated token for each role with
  the read-only scope, and the negative cases (a delegated token attempting a write, an
  expired token, a token for the wrong audience).

**Platform impact**
- What changes for the Postman collection, the Newman smoke tests and the CI workflows when
  the API requires a token.
- The local development story: security stays off by default, and how a developer turns it on
  with a seeded user.
- CORS and CSRF: CSRF is disabled today because Basic auth is stateless; that stays correct
  only if tokens are sent in a header and never in a cookie. A cookie-based design would
  change it.
- The migration path to Cognito: what stays as configuration (issuer metadata, keys) and
  what is code.

## Out of scope

- The authorization model: roles, the hierarchy and the `@PreAuthorize` checks stay governed
  by ADR-0006. A split of roles into read and write, or per-resource permissions, would be
  its own decision.
- How the agent is authorized and what it may do: ADR-0007.
- Status codes and bodies for failures: ADR-0008.

## Decision

None yet.

## Security implications to address when this is written

Credential handling and storage in the browser, token theft and replay, key management and
rotation, brute force, account enumeration through error messages, logout and revocation, and
the effect of token lifetime on how quickly a removed role stops working. Also the test-only
token minting above: it must be impossible to turn on in a deployed environment.

## Revisit if

- The agent milestone is brought forward: this ADR then becomes its prerequisite and must be
  written first.
- Amazon Cognito is adopted before the first issuer is built.
