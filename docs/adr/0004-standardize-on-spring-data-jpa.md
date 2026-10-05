# 4. Standardize on Spring Data JPA, eliminate JDBC and plain-JPA repositories

Date: 2026-10-04

## Status

Accepted

## Context

Upstream Spring PetClinic (`spring-petclinic/spring-petclinic-rest`, the
`upstream` remote in this repo) implements every repository three separate
ways, selected at runtime by Spring profile:

- `repository/jdbc/Jdbc*RepositoryImpl.java` — hand-written `JdbcTemplate`/
  `NamedParameterJdbcTemplate` code: manual SQL, `RowMapper`/`ResultSetExtractor`
  classes to reassemble object graphs (e.g. `JdbcPetVisitExtractor`,
  `JdbcPetRowMapper`), `SimpleJdbcInsert` for inserts.
- `repository/jpa/Jpa*RepositoryImpl.java` — hand-written `EntityManager`-based
  code: manual JPQL/Criteria queries per method.
- `repository/springdatajpa/SpringData*Repository.java` — Spring Data JPA
  interfaces, where most methods need no implementation at all (Spring
  generates them from the method signature or an `@Query` annotation).

Each strategy sits behind the same `@Profile`-qualified interface (`OwnerRepository`,
`PetRepository`, etc.), so swapping `jdbc`/`jpa`/`hsqldb` in
`spring.profiles.active` swaps the entire persistence implementation without
touching callers. This is valuable as a teaching reference — it lets someone
compare three persistence approaches to the same problem side by side — but
this platform is not a teaching reference. In production, exactly one profile
is ever active. The other two implementations are permanent dead code that
still has to compile, still shows up in search results and code review, and
still has to be kept in sync by hand every time a repository method is added,
renamed, or changed — tripling the work for zero runtime benefit.

The three implementations also weren't perfectly interchangeable in practice:
subtly different exception-handling and not-found semantics between them meant
"swap the profile" was not actually a safe, behavior-preserving operation —
undermining the one theoretical benefit (pluggable persistence) the structure
was supposed to provide.

By the time this fork's own commit history begins, the JDBC and plain-JPA
implementations were already gone — only the Spring Data JPA strategy
remained, still split across a plain interface (`OwnerRepository`) and a
`springdatajpa`-package Spring Data specialization (`SpringDataOwnerRepository`)
left over from when three strategies had to share one abstraction. A later,
separately-tracked commit (`908341b`, "Flatten repository layer: merge
interfaces with Spring Data, drop springdatajpa package") finished the cleanup
by merging each pair into one interface per entity, now living directly in
`repository/` and extending Spring Data's `Repository<T, Integer>` — removing
the indirection that no longer served a purpose once there was only one
implementation to abstract over.

## Decision

The repository layer implements persistence exactly one way: Spring Data JPA.
Every repository (`OwnerRepository`, `PetRepository`, `PetTypeRepository`,
`SpecialtyRepository`, `UserRepository`, `VetRepository`, `VisitRepository`) is
a single interface extending `Repository<T, Integer>`, with derived query
methods and `@Query`-annotated JPQL for anything a method name can't express.
A small number of hand-written JPQL fragments remain as the `*RepositoryOverride`
/ `*RepositoryImpl` escape hatch (e.g. cascading pet/visit deletes in
`PetRepositoryImpl`) for the few operations Spring Data can't generate — this
is the only place implementation code is still written by hand.

No JDBC-based or plain-JPA (`EntityManager`-driven) repository implementation
exists anywhere in the codebase, and `spring.profiles.active` only ever
selects a *database* (`h2`, `hsqldb`, `mysql`, `postgres`), never a persistence
*strategy* — there is only one strategy.

### Alternatives considered

- **Keep all three strategies, profile-selectable** (the upstream approach):
  rejected — see Context. Correct for a teaching reference, pure maintenance
  cost for a platform with one real deployment target per environment.
- **Standardize on plain JPA (`EntityManager`) instead of Spring Data JPA**:
  would still require hand-writing every query and manually mapping results,
  with none of Spring Data's boilerplate reduction — strictly more code to
  maintain than Spring Data JPA for the same outcome.
- **Standardize on JDBC (`JdbcTemplate`) instead of Spring Data JPA**: gives
  the most explicit control over the exact SQL executed, but requires hand-
  written `RowMapper`/`ResultSetExtractor` classes to reconstruct the
  Owner→Pet→Visit object graph that JPA's entity relationships already express
  declaratively — rejected for the same reason plain JPA was: more code,
  no corresponding benefit for this application's query complexity.

## Consequences

**Positive:**

- Roughly two-thirds of the repository layer's files are simply gone — no
  `RowMapper`, `ResultSetExtractor`, or hand-written JPQL class exists for any
  operation Spring Data can derive from a method signature or a declarative
  `@Query`.
- One implementation per repository to read, test, and reason about — no risk
  of the active profile silently changing not-found or exception-handling
  behavior, because there's no second implementation left to diverge from.
- Adding or changing a query is usually a one-line method signature change
  instead of a change mirrored across JDBC, plain-JPA, and Spring Data
  versions of the same method.
- This decision is what made the later interface-flattening cleanup
  (`908341b`) possible — merging `OwnerRepository` with
  `SpringDataOwnerRepository` only made sense once there was a single
  implementation left to not need abstracting over.

**Negative / trade-offs accepted:**

- No ready-made JDBC fallback if a future query is too complex, or too
  performance-sensitive, for JPQL/derived methods to express well — that would
  need to be added back specifically for that case rather than already being
  wired in behind the same interface.
- Spring Data's derived-query and `@Query` translation is a layer of "magic"
  between the method signature and the SQL actually executed — debugging an
  unexpected query sometimes means understanding Spring Data's method-name
  parsing rules rather than reading literal SQL, the opposite trade-off from
  JDBC's explicitness.
- Whatever value the three-implementation structure had as a side-by-side
  comparison of persistence approaches (real for the upstream teaching
  project) is gone from this fork — acceptable here, since this platform was
  never using that comparison for anything.
- A handful of hand-written JPQL fragments (the `*RepositoryOverride` classes)
  still exist for cascading deletes — Spring Data JPA didn't fully eliminate
  hand-written query code, just almost all of it.

## Revisit if

- A specific query or bulk operation proves too slow or too awkward to express
  through JPQL/Spring Data derivation, and profiling shows hand-written SQL
  would meaningfully help — that would justify reintroducing JDBC for that one
  case, not reverting this decision wholesale.
