# Screen-to-Endpoint Mapping

Status as of 2026-10-07. Records which REST endpoints each screen depends on. Source of
truth for the endpoints is `spring-petclinic-rest/src/main/resources/openapi.yml`; all
paths are under `/petclinic/api` (the UI's `VITE_PETCLINIC_API_URL`). Screens marked ⚠️
are proposed, not decided.

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
| Add pet | `GET /pettypes` | `listPetTypes` | Populate the type select | OWNER_ADMIN or VET_ADMIN | Not started |
| Add pet | `POST /owners/{ownerId}/pets` | `addPetToOwner` | Create the pet | OWNER_ADMIN | Not started |
| Edit owner | `GET /owners/{ownerId}` | `getOwner` | Prefill the form | OWNER_ADMIN | Built |
| Edit owner | `PUT /owners/{ownerId}` | `updateOwner` | Save changes | OWNER_ADMIN | Built |
| Edit pet ⚠️ | `GET /owners/{ownerId}/pets/{petId}` | `getOwnersPet` | Prefill the form | OWNER_ADMIN | Not started |
| Edit pet ⚠️ | `GET /pettypes` | `listPetTypes` | Populate the type select | OWNER_ADMIN or VET_ADMIN | Not started |
| Edit pet ⚠️ | `PUT /owners/{ownerId}/pets/{petId}` | `updateOwnersPet` | Save changes | OWNER_ADMIN | Not started |
| Add visit ⚠️ | `POST /owners/{ownerId}/pets/{petId}/visits` | `addVisitToOwner` | Record the visit | OWNER_ADMIN | Not started |
| Vets list ⚠️ | `GET /vets` | `listVets` | Vets and specialties | VET_ADMIN | Not started |
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
  `204 No Content` (the generated controller attaches a body to it, but none is sent), so the UI
  refetches the owner instead of reading a response.
- **Delete owner**: `DELETE /owners/{ownerId}` answers `204`. `Owner.pets` and `Pet.visits` are mapped
  with `CascadeType.ALL`, so the owner's pets and their visits go with them; the confirmation says so.
  ⚠️ **Known backend bug (verified 2026-10-08):** deleting an owner who has pets fails. `Pet.type` is
  also mapped `CascadeType.ALL`, so removing a pet cascades a remove to its shared pet type; Hibernate
  then tries `update pets set type_id = null` for the other pets of that type, the NOT NULL column
  rejects it, and the transaction rolls back (no data is lost). The API answers `404` (the exception
  handler maps every data-integrity error to 404) with "data constraint violation". Owners without
  pets delete fine (`204`). Fix belongs in `spring-petclinic-rest` (drop the cascade on `Pet.type`)
  on its own branch, with a test; until then the UI shows the server message in the delete dialog.

## Notes

- **Unpaginated variants exist** (`GET /owners`, `GET /pets`) but the UI uses the paged
  `/v2/` endpoints where available so lists stay bounded as data grows.
- **Owner details needs only one call**: `getOwner` returns the owner with `pets[]`, and
  each pet carries its visits, per the `Owner` schema.
- **Errors** are `ProblemDetail` (`title`, `detail`, `status`): `400` is shown on the
  form, `404` as "not found", `401`/`403` as an access error, `5xx` and network failures as
  a retryable error.
- **Not used by any planned screen:** `/pets*`, `/visits*`, pet type / specialty / vet
  writes, other `DELETE` endpoints (until their screens exist), and `POST /users`.
- ⚠️ `GET /vets` requires `VET_ADMIN`, so an `OWNER_ADMIN`-only user would get `403` on
  the Vets list when security is on. Worth confirming whether that is intended.
- ⚠️ Adding a pet loads pet types from the API, so the Add pet screen has two calls and
  must handle either failing independently.
- **AI agent**: endpoints are unknown until `petclinic-ai-agent` is built. Writes it
  proposes are executed by the UI against the PetClinic endpoints above only after the
  user confirms (architecture rule).

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
