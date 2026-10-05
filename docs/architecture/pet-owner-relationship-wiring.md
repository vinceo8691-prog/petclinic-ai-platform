# Moving pet/owner relationship wiring out of the controller

This recounts a fix, not an architectural decision — it has no competing
alternatives of lasting consequence and nothing to revisit later, which is why
it lives here rather than in `../adr`. [ADR-0005](../adr/0005-split-clinicservice-into-per-aggregate-services.md)
(the ClinicService split) named this as a known gap it left unfixed; this is
that gap getting closed.

## Before: the controller assembling entity relationships by hand

`OwnerRestControllerV1.addPetToOwner` used to do this:

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
  re-fetching it by id (`petTypeService.findPetTypeById(pet.getType().getId())`),
  so whatever name was on the client-supplied stub never survives to be
  persisted regardless of whether it's nulled out first.

None of this is a controller's job. A controller should translate HTTP
requests into service calls and service results into HTTP responses; this one
was instead assembling entity relationships and compensating for persistence
details that belong next to the repository that enforces them.

## After: one method on PetService

```java
// Takes the already-loaded Owner rather than an id so PetService doesn't need
// a dependency on OwnerService -- owner.addPet wires both sides of the
// relationship, which hand-setting pet.setOwner(owner) in the caller used to skip.
@Transactional
public void addPetToOwner(Owner owner, Pet pet) throws DataAccessException {
    owner.addPet(pet);
    savePet(pet);
}
```

`OwnerRestControllerV1.addPetToOwner` now only loads the owner, maps the DTO,
delegates, and maps the result back — no entity manipulation left in the
controller.

`PetService` was chosen to host this method, taking `Owner` as a parameter,
specifically to avoid adding a second cross-service dependency on top of the
one (`PetService → PetTypeService`) that already exists per ADR-0005. The
controller already depends on both `OwnerService` and `PetService`, so it
loads the `Owner` and passes it in; no service gained a new dependency on
another service as a result of this change.

## What this fixed beyond readability

`owner.addPet(pet)` also fixed a real (if minor) latent bug the hand-wired
version had: it never added the new pet to `owner`'s in-memory `pets`
collection, only to `pet.owner`. That was harmless as long as nothing read
`owner.getPets()` again within the same request after creating a pet — but it
was never guaranteed to stay harmless, and now it's simply correct.

## Verification

The full test suite (126 tests) passes. The two `OwnerRestControllerV1Tests`
cases that simulated a persistence failure during pet creation were updated
to stub `PetService.addPetToOwner` instead of the no-longer-called
`PetService.savePet`, since `addPetToOwner` is what the controller invokes
now.
