# Screen-to-Endpoint Mapping

Status as of 2026-10-14. Records which REST endpoints each screen depends on. Source of
truth for the endpoints is `spring-petclinic-rest/src/main/resources/openapi.yml`; all
paths are under `/petclinic/api` (the UI's `VITE_PETCLINIC_API_URL`). Screens marked ⚠️
are proposed, not decided (the same status as in [screen-inventory.md](screen-inventory.md)).

**Auth column:** the role required when `petclinic.security.enable=true`. `ADMIN` also
satisfies `OWNER_ADMIN` and `VET_ADMIN` ([ADR-0006](../adr/0006-admin-role-hierarchy.md)).
Security is off by default.

**Build column:** `Built` means the UI calls it today.

| Screen | Endpoint | operationId | Purpose | Auth | Build |
|---|---|---|---|---|---|
| Owner search | `GET /v2/owners?lastName&sort&direction&page&size` | `listOwnersPage` | Paged owner list (`OwnerPage`) | OWNER_ADMIN | Built |
| Add owner | `POST /owners` | `addOwner` | Create the owner (201 returns the `Owner`) | OWNER_ADMIN | Built |
| Owner details | `GET /owners/{ownerId}` | `getOwner` | Owner with pets and visits | OWNER_ADMIN | Built |
| Owner details | `DELETE /owners/{ownerId}` | `deleteOwner` | Delete action (behind a confirmation dialog) | OWNER_ADMIN | Built |
| Add pet | `GET /pettypes` | `listPetTypes` | Populate the type select (the counts of `PetTypeWithCounts` are ignored here, decision H) | OWNER_ADMIN or VET_ADMIN | Not started |
| Add pet | `POST /owners/{ownerId}/pets` | `addPetToOwner` | Create the pet | OWNER_ADMIN | Not started |
| Edit owner | `GET /owners/{ownerId}` | `getOwner` | Prefill the form | OWNER_ADMIN | Built |
| Edit owner | `PUT /owners/{ownerId}` | `updateOwner` | Save changes | OWNER_ADMIN | Built |
| Edit pet ⚠️ | `GET /owners/{ownerId}` | `getOwner` | Prefill the form from the cached owner (decision A); `GET /owners/{ownerId}/pets/{petId}` (`getOwnersPet`) is not used | OWNER_ADMIN | Not started |
| Edit pet ⚠️ | `GET /pettypes` | `listPetTypes` | Populate the type select (counts ignored, as for Add pet) | OWNER_ADMIN or VET_ADMIN | Not started |
| Edit pet ⚠️ | `PUT /owners/{ownerId}/pets/{petId}` | `updateOwnersPet` | Save changes | OWNER_ADMIN | Not started |
| Delete pet ⚠️ | `DELETE /pets/{petId}` | `deletePet` | Delete action (confirmation states the visit count) | OWNER_ADMIN | Not started |
| Add visit ⚠️ | `GET /owners/{ownerId}` | `getOwner` | Find the pet being visited | OWNER_ADMIN | Not started |
| Add visit ⚠️ | `POST /owners/{ownerId}/pets/{petId}/visits` | `addVisitToOwner` | Record the visit | OWNER_ADMIN | Not started |
| Edit visit ⚠️ | `GET /owners/{ownerId}` | `getOwner` | Prefill the form from the cached owner | OWNER_ADMIN | Not started |
| Edit visit ⚠️ | `PUT /visits/{visitId}` | `updateVisit` | Save changes | OWNER_ADMIN | Not started |
| Delete visit ⚠️ | `DELETE /visits/{visitId}` | `deleteVisit` | Delete action | OWNER_ADMIN | Not started |
| Vets list ⚠️ | `GET /vets` | `listVets` | Vets and specialties | VET_ADMIN | Not started |
| Add vet ⚠️ | `GET /specialties` | `listSpecialties` | Populate the specialties choice | VET_ADMIN | Not started |
| Add vet ⚠️ | `POST /vets` | `addVet` | Create the vet | VET_ADMIN | Not started |
| Edit vet ⚠️ | `GET /vets/{vetId}`, `GET /specialties` | `getVet`, `listSpecialties` | Prefill the form and populate the choice | VET_ADMIN | Not started |
| Edit vet ⚠️ | `PUT /vets/{vetId}` | `updateVet` | Save changes | VET_ADMIN | Not started |
| Delete vet ⚠️ | `DELETE /vets/{vetId}` | `deleteVet` | Delete action | VET_ADMIN | Not started |
| Specialties | `GET`/`POST /specialties`, `PUT`/`DELETE /specialties/{id}` | `listSpecialties`, `addSpecialty`, `updateSpecialty`, `deleteSpecialty` | The whole catalog screen | VET_ADMIN | Not started |
| Pet types | `GET`/`POST /pettypes`, `PUT`/`DELETE /pettypes/{id}` | `listPetTypes`, `addPetType`, `updatePetType`, `deletePetType` | The whole catalog screen (`GET` is also open to `OWNER_ADMIN`); `GET` will return `PetTypeWithCounts` for the delete confirmation (decision H, planned) | VET_ADMIN | Not started |
| Add user | `POST /users` | `addUser` | Create an account (deferred, decision D) | ADMIN | Not started |
| Assistant panel | `petclinic-ai-agent` (none yet) | — | TBD | TBD | Preview only |

## Request details for built screens

- **Owner search**: the UI sends `lastName` (only when non-empty, trimmed), `page` (zero-based)
  and `size` (10, 20 or 50), plus `sort=lastName` and `direction=asc|desc` once the user has
  sorted by Name. With no sort the API returns id order. `lastName` is a case-insensitive prefix
  match, and `%` and `_` in it are literal characters. `Owner.pets` is populated in each row,
  which the Pets column uses. The API answers `400` for `size` outside 1 to 100, a negative or
  non-numeric `page`, a `sort` other than `id` or `lastName`, or a `direction` other than `asc`
  or `desc`; the UI only ever sends valid values.
- **Add owner**: body is `OwnerFields`: `firstName`, `lastName` (1–30 letters with optional
  space/hyphen/apostrophe separators, up to three words), `address` (≤255), `city` (≤80),
  `telephone` (exactly 10 digits, as the `Owner` entity requires). The UI validates the same
  rules before sending; the server stays authoritative and answers `400` with the offending
  field in `schemaValidationErrors` (it used to answer `500` for a telephone that was not 10
  digits).

- **Owner details / Edit owner**: `GET /owners/{ownerId}` returns the owner with each pet's `type`,
  `birthDate` and `visits`. A `404` (or a non-numeric id, which is never sent) shows "Owner not found".
- **Edit owner**: `PUT /owners/{ownerId}` takes the same `OwnerFields` body as Add owner. The API answers
  `204 No Content` (`openapi.yml` says so since PC010), so the UI refetches the owner instead of
  reading a response.
- **Delete owner**: `DELETE /owners/{ownerId}` answers `204`. `Owner.pets` and `Pet.visits` are mapped
  with `CascadeType.ALL`, so the owner's pets and their visits go with them; the confirmation says so.
  `Pet.type` is not cascaded, so the pet types the owner's pets shared with other pets stay in
  place. (Before PC005, deleting an owner who had pets failed with a `404` "data constraint violation"
  because the delete cascaded to the shared pet type; that bug is fixed.) If a delete fails for any
  other reason the UI shows the server's message in the dialog.

## Notes

- **Unpaginated variants exist** (`GET /owners`, `GET /pets`) but the UI uses the paged
  `/v2/` endpoints where available so lists stay bounded as data grows.
- **Owner details needs only one call**: `getOwner` returns the owner with `pets[]`, and
  each pet carries its visits, per the `Owner` schema.
- **Errors** are `ProblemDetail` (`title`, `detail`, `status`): `400` is shown on the
  form, `404` on a read as "not found", `401`/`403` as an access error, `5xx` and network failures as
  a retryable error. A failed write always shows the server's `detail`. A data-constraint failure on a
  write (for example a pet whose type does not exist) answers `409` since PC012; before that it answered
  `404` (decision F, [ui-overview.md](ui-overview.md)).
- **Not used by any planned screen:** `GET /pets`, `GET /pets/{id}`, `GET /visits`,
  `GET /visits/{id}` and the unpaginated `GET /owners`. The owner flow already reaches that data,
  so a standalone list would only duplicate it. (The write endpoints are used by the planned
  screens above; the operationIds in those rows match `openapi.yml`.)
- **Cache invalidation per write** (planned; the rule is in [ui-overview.md](ui-overview.md)):
  pet or visit writes invalidate `['owner', id]`, `['owners']` and `['pet-types']` (the pet type
  counts, decision H); vet writes `['vets']`; specialty
  writes `['specialties']` and `['vets']` (vets display specialties); pet type writes
  `['pet-types']`, plus `['owners']` and `['owner', …]` because pets show their type.
- **`PUT /vets/{id}` matches specialties by name**, not id, and silently drops names it does not
  recognise, so the vet form must send specialties taken from the loaded catalog, never free text.
- **The owner-scoped pet and visit writes do not verify ownership** (they do not check that the pet
  belongs to the owner, or that it exists). The UI always sends consistent ids; the check belongs
  on the backend backlog.
- ⚠️ `GET /vets` requires `VET_ADMIN`, so an `OWNER_ADMIN`-only user would get `403` on
  the Vets list when security is on. Worth confirming whether that is intended.
- ⚠️ Adding a pet loads pet types from the API, so the Add pet screen has two calls and
  must handle either failing independently.
- **AI agent**: endpoints are unknown until `petclinic-ai-agent` is built. Writes it
  proposes are executed by the UI against the PetClinic endpoints above only after the
  user confirms (architecture rule; [ADR-0007](../adr/0007-ai-agent-acts-on-behalf-of-the-employee.md)).

## Owner sorting and search (built in PC004)

- `GET /v2/owners` takes `sort` (`id` | `lastName`, default `id`) and `direction` (`asc` | `desc`,
  default `asc`). Sorting by last name ignores case and breaks ties on first name, then id; the
  direction applies to every key, so descending is the exact reverse of ascending.
- `lastName` matching is case-insensitive for both `GET /owners` and `GET /v2/owners`, with `%`
  and `_` matched literally (escaped with `!` in the `LIKE`).
- ⚠️ `LOWER()` stops the database using the plain `last_name` index. That is fine at clinic
  scale; a functional index on `lower(last_name)` could be added for PostgreSQL if it matters.
- ⚠️ Only H2 and HSQLDB are exercised by the tests. PostgreSQL and MySQL should behave the same
  (portable JPQL) but are unverified.

## Known performance note

`Owner.pets` is eagerly fetched, so the paged owners query issues one extra select per owner
(about 21 queries for a 20-row page). Fine at clinic scale; deliberately left alone for now.
Revisit (e.g. batch fetching) if list performance matters.
