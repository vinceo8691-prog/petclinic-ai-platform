# 6. Declare a role hierarchy so ROLE_ADMIN outranks the resource-scoped roles

Date: 2026-10-05

## Status

Accepted

## Context

`security/Roles.java` defines three roles: `OWNER_ADMIN`, `VET_ADMIN`, and
`ADMIN`. Auditing every `@PreAuthorize` annotation in the codebase shows:

- `ADMIN` is checked in exactly one place: `UserRestControllerV1.addUser`
  (creating a new user account).
- Every other endpoint — all owner/pet/visit writes, all vet/specialty/pet-type
  writes — requires `OWNER_ADMIN` or `VET_ADMIN` specifically.
- No `RoleHierarchy` bean existed anywhere, and Spring Security's `hasRole()`
  does an exact authority match with no implicit relationship between roles
  by default.

The practical effect: `ADMIN` was the **narrowest**-scoped role in the system
— it could create user accounts and nothing else. It could not touch an
owner, pet, vet, or visit unless *also* separately granted `OWNER_ADMIN` and
`VET_ADMIN`. Meanwhile, `OWNER_ADMIN` + `VET_ADMIN` together already grant
full control over every clinic resource with no `ADMIN` role involved at all.

This inverts what the name promises. Anyone provisioning accounts would
reasonably read "ADMIN" as "can do everything" and be surprised that an
`ADMIN`-only account can't touch a single pet record — a naming/behavior
mismatch that's a real source of access-control mistakes (either
under-provisioning someone who should have broad access, or over-granting
`OWNER_ADMIN`+`VET_ADMIN` "just in case" to compensate).

## Decision

Declare a `RoleHierarchy` bean in `BasicAuthenticationConfig` (where
`@EnableMethodSecurity` already lives, and the only place this matters —
`DisableSecurityConfig` permits everything regardless of role):

```java
@Bean
public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("""
        ROLE_ADMIN > ROLE_OWNER_ADMIN
        ROLE_ADMIN > ROLE_VET_ADMIN
        """);
}
```

Spring Security's method-security configuration
(`PrePostMethodSecurityConfiguration`) autowires any `RoleHierarchy` bean
present in the context into its expression handler
(`@Autowired(required = false) void setRoleHierarchy(...)`), so every
existing `hasRole(@roles.OWNER_ADMIN)` and `hasRole(@roles.VET_ADMIN)` check
now also passes for a principal holding only `ROLE_ADMIN`. No controller or
`@PreAuthorize` annotation was touched.

### Alternatives considered

- **Change every `hasRole(X)`/`hasAnyRole(...)` to also accept `@roles.ADMIN`**:
  rejected — touches roughly 30 annotations across 8 controller files, with
  real risk of missing one and leaving a silent gap.
- **Have `UserService.saveUser` auto-expand an `ADMIN` grant into three role
  rows at write time**: rejected — conflates authentication data with
  authorization logic, duplicates rows, and wouldn't retroactively fix any
  `ADMIN` user already in the `roles` table.

## Consequences

**Positive:**

- `ADMIN` now behaves the way its name implies — a principal with only
  `ROLE_ADMIN` can do everything an `OWNER_ADMIN` or `VET_ADMIN` can, plus
  create users.
- Zero changes to any controller or existing `@PreAuthorize` annotation — the
  entire fix is one bean.
- The hierarchy is one-directional and explicit: `OWNER_ADMIN` and
  `VET_ADMIN` do not imply each other, and neither implies `ADMIN` — only
  `ADMIN` sits above both.

**Negative / trade-offs accepted:**

- A viewer of any single `@PreAuthorize("hasRole(@roles.VET_ADMIN)")`
  annotation can no longer tell from that line alone who can pass it — they
  also need to know the hierarchy exists. This is the standard trade-off of
  role hierarchies: less local reasoning, more compact and correct global
  behavior.
- This surfaced a separate, pre-existing gap while verifying the fix: a
  failed `@PreAuthorize` check throws `AuthorizationDeniedException`, which
  `ExceptionControllerAdvice` has no specific handler for, so it falls
  through to the generic `Exception.class` handler and returns **500**
  instead of 403/401. That's documented in the new test
  (`RoleHierarchyTests.ownerAdminIsStillDeniedTheVetAdminGatedEndpoint`) as
  current behavior, not fixed here — it's an orthogonal error-handling gap,
  not something this role-hierarchy change caused.

## Verification

Added `RoleHierarchyTests` (`security` test package): a principal with only
`ROLE_ADMIN` successfully calls `GET /api/vets` (`VET_ADMIN`-gated), and a
principal with only `ROLE_OWNER_ADMIN` is still denied the same endpoint —
confirming the hierarchy runs `ADMIN` downwards only, not sideways between
the two resource-scoped roles. Full suite (now 14 test classes) passes.

## Revisit if

- A fourth resource-scoped role is added — it needs its own
  `ROLE_ADMIN > ROLE_<NEW_ROLE>` line, or it silently sits outside the
  hierarchy the same way `OWNER_ADMIN`/`VET_ADMIN` did before this change.
