# 5. Split the ClinicService god object into per-aggregate services

Date: 2026-10-04

## Status

Accepted

## Context

`ClinicService` was a single `@Service` class wrapping all six repositories in
the domain — `PetRepository`, `VetRepository`, `OwnerRepository`,
`VisitRepository`, `SpecialtyRepository`, and `PetTypeRepository` — behind one
facade. It was injected into every REST controller: all six v1 controllers
(`OwnerRestControllerV1`, `PetRestControllerV1`, `PetTypeRestControllerV1`,
`SpecialtyRestControllerV1`, `VetRestControllerV1`, `VisitRestControllerV1`)
and both v2 controllers (`OwnerRestControllerV2`, `PetRestControllerV2`).

In practice, most controllers only ever called methods touching one or two
aggregates — `PetTypeRestControllerV1` only ever called pet-type methods,
`SpecialtyRestControllerV1` only ever called specialty methods — but every one
of them compiled against, and could in principle call, methods for all six.
A change to any repository, or to any one aggregate's service methods, meant
`ClinicService` changed, which meant every controller in the system was
touching a class that had just changed, whether or not that controller cared
about the change. There was no way to tell from a constructor signature which
aggregates a given controller actually depended on.

This is the same shape of problem already addressed elsewhere in this
platform's history: [ADR-0004](0004-standardize-on-spring-data-jpa.md)
removed the JDBC/plain-JPA repository implementations because they cost real
maintenance with no corresponding benefit once only one was ever active; this
is the service-layer equivalent — one class doing the job of six, imposing a
shared dependency on every caller regardless of which part of it they needed.

## Decision

Replace `ClinicService` with six services, one per aggregate, each wrapping
only its own repository:

- `OwnerService` → `OwnerRepository`
- `PetService` → `PetRepository`
- `VisitService` → `VisitRepository`
- `VetService` → `VetRepository`
- `PetTypeService` → `PetTypeRepository`
- `SpecialtyService` → `SpecialtyRepository`

`PetService` additionally depends on `PetTypeService`, to resolve a pet's
`PetType` on save — the one genuine cross-aggregate collaboration, kept as an
explicit constructor dependency between two small services rather than as a
reason to merge them back together. No other service depends on another.

Each controller's constructor now only lists the services it actually calls —
`PetTypeRestControllerV1` and `SpecialtyRestControllerV1` depend on exactly
one service each; `VetRestControllerV1` depends on two
(`VetService` and `SpecialtyService`, the latter for resolving specialties by
name); `OwnerRestControllerV1` depends on three
(`OwnerService`, `PetService`, `VisitService`), reflecting that it's the one
controller that legitimately creates pets and visits as part of managing an
owner.

The not-found-returns-null handling that every `find*ById` method needed
(catching `ObjectRetrievalFailureException`/`EmptyResultDataAccessException`)
was extracted once into a small package-private `EntityLookup` helper instead
of being duplicated in all six services.

### Alternatives considered

- **Leave `ClinicService` as-is**: rejected — this is the problem being
  solved, not an alternative to it.
- **Give each new service an interface**: rejected for the same reason
  documented in [the service-interface collapse](../initial-petclinic-modernization.md) —
  no second implementation of any of these services exists or is anticipated,
  so an interface would be ceremony with no payoff.
- **Split further than per-aggregate** (e.g. separate read/command services
  per aggregate): rejected as premature — there's no current evidence this
  platform needs separate query/write models, and splitting further than the
  data actually warrants would just trade one kind of unnecessary coupling
  for unnecessary fragmentation.
- **Keep one facade class but have it delegate internally to smaller,
  private helper objects**: rejected — this doesn't fix the actual problem.
  Every controller would still formally depend on one `ClinicService` type,
  so a change to any aggregate's logic still "touches" the one class every
  controller is coupled to, even if the implementation behind it is tidier.

## Consequences

**Positive:**

- A controller's constructor now states exactly which aggregates it depends
  on — `PetTypeRestControllerV1`'s single-argument constructor is now accurate
  documentation of what it does, where before it was identical to every other
  controller's six-repository-wide dependency regardless of actual usage.
- Changing `VetService`, say, no longer requires reasoning about whether that
  change could affect `PetTypeRestControllerV1` or `SpecialtyRestControllerV1`
  — they don't depend on it, and now that's visible in their constructors.
- The dependency graph has exactly one cross-service edge
  (`PetService → PetTypeService`), down from a single class that every
  controller and every aggregate ran through.
- Verified behavior-preserving: the full test suite (126 tests, including the
  36-case integration suite covering every CRUD/delete path per aggregate)
  passes unchanged after the split, and each controller's mock-based unit
  tests were updated to mock only the services that controller now depends on.

**Negative / trade-offs accepted:**

- Six service files and six Spring beans to navigate instead of one — more
  files overall, though each is smaller and scoped to a single aggregate.
- `OwnerRestControllerV1`/`OwnerRestControllerV2` (and `PetRestControllerV1`/
  `PetRestControllerV2`) still share the same `OwnerService`/`PetService`
  instances — splitting the god object made this visible as two specific
  edges instead of one opaque one, but didn't remove the underlying v1/v2
  coupling.
- A harmless but real duplication from the original `ClinicService`
  (`findVets()` and `findAllVets()` doing the same `vetRepository.findAll()`)
  was preserved as-is in `VetService` rather than cleaned up, to keep this
  change behavior-preserving and reviewable as one thing at a time.

## Revisit if

- Any one of the six services grows enough unrelated responsibility that it
  starts to look like a smaller version of the original god object — split it
  further at that point, following the same per-aggregate reasoning.
- A genuine need for multiple implementations of one of these services
  emerges (e.g. a test double that can't just mock the concrete class, or an
  actual alternate implementation) — introduce an interface then, not
  preemptively.
