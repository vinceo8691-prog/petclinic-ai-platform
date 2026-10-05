# 6. Move pet/owner relationship wiring out of OwnerRestControllerV1

Date: 2026-10-04

## Status

Accepted

## Context

[ADR-0005](0005-split-clinicservice-into-per-aggregate-services.md) split the
`ClinicService` god object into per-aggregate services but explicitly called
out one thing it left unfixed: `OwnerRestControllerV1.addPetToOwner` directly
manipulated persistence relationships instead of delegating that to a
service.

```java
Pet pet = petMapper.toPet(petFieldsDto);
owner.setId(ownerId);
pet.setOwner(owner);
pet.getType().setName(null);
this.petService.savePet(pet);
```

Each line had a problem:

- `owner.setId(ownerId)` was dead code — `owner` was already loaded via
  `ownerService.findOwnerById(ownerId)`, so `owner.getId()` already equalled
  `ownerId`.
- `pet.setOwner(owner)` wired only the foreign-key side of the relationship by
  hand, duplicating — incompletely — what `Owner.addPet(Pet)` already does
  (`getPetsInternal().add(pet); pet.setOwner(this);`). The controller's
  version never added the new pet to `owner`'s in-memory `pets` collection.
- `pet.getType().setName(null)` was a no-op given `PetService.savePet`'s
  existing behavior: `savePet` always replaces `pet`'s `PetType` entirely by
  re-fetching it by ID (`petTypeService.findPetTypeById(pet.getType().getId())`),
  so whatever name was on the client-supplied stub never survives to be
  persisted regardless of whether it's nulled out first.

None of this is a controller's job. A controller should translate HTTP
requests into service calls and service results into HTTP responses; here it
was instead assembling entity relationships and compensating for persistence
details that belong next to the repository that enforces them.

## Decision

Add one method to `PetService` that does this orchestration correctly, by
reusing the entity's own relationship-wiring method and the existing
`savePet` logic:

```java
@Transactional
public void addPetToOwner(Owner owner, Pet pet) throws DataAccessException {
    owner.addPet(pet);
    savePet(pet);
}
```

`OwnerRestControllerV1.addPetToOwner` now only loads the owner, maps the DTO,
delegates, and maps the result back:

```java
Owner owner = this.ownerService.findOwnerById(ownerId);
if (owner == null) {
    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
}
Pet pet = petMapper.toPet(petFieldsDto);
this.petService.addPetToOwner(owner, pet);
PetDto petDto = petMapper.toPetDto(pet);
```

`PetService` was chosen to host this method — rather than giving `PetService`
a new dependency on `OwnerService`, or putting the method on `OwnerService`
instead — specifically to avoid adding a second cross-service edge on top of
the one (`PetService → PetTypeService`) that already exists per ADR-0005. The
controller, which already depends on both `OwnerService` and `PetService`,
loads the `Owner` and passes it in as a parameter; no service gained a new
dependency on another service as a result of this change.

## Consequences

**Positive:**

- The three entity-manipulation lines and the one dead line are gone from the
  controller, replaced by a single call whose name states what it does.
- `owner.addPet(pet)` now correctly updates `owner`'s in-memory `pets`
  collection in addition to the FK side, fixing the latent inconsistency in
  the old hand-wired version — a behavior improvement, not just a readability
  one.
- `PetService.addPetToOwner` reuses `savePet` rather than duplicating its
  type-resolution logic, so that logic still lives in exactly one place.
- No new service-to-service dependency was introduced; the dependency graph
  from ADR-0005 (one cross-service edge, `PetService → PetTypeService`) is
  unchanged.
- Closes the specific gap ADR-0005 named as unfinished.

**Negative / trade-offs accepted:**

- `PetService` now has a method that takes an `Owner` as a parameter, which
  is a slightly unusual shape for a service named after a different aggregate
  — justified here specifically to avoid the alternative (a new
  `PetService → OwnerService` dependency), but worth noting as an exception
  to "a service only talks about its own aggregate."

## Verification

Full test suite (126 tests) passes unchanged in behavior terms; the two
`OwnerRestControllerV1Tests` cases that simulated a persistence failure during
pet creation were updated to stub `PetService.addPetToOwner` instead of the
no-longer-called `PetService.savePet`, since `addPetToOwner` is now the method
the controller actually invokes.
