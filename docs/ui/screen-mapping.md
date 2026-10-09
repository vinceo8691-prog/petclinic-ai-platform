# Screen-to-Endpoint Mapping

Status as of 2026-10-07. Records which REST endpoints each screen depends on. Source of
truth for the endpoints is `spring-petclinic-rest/src/main/resources/openapi.yml`; all
paths are under `/petclinic/api` (the UI's `VITE_PETCLINIC_API_URL`). Screens marked ⚠️
are proposed, not decided.

**Auth column:** the role required when `petclinic.security.enable=true`. `ADMIN` also
satisfies `OWNER_ADMIN` and `VET_ADMIN` ([ADR-0007](../adr/0007-admin-role-hierarchy.md)).
Security is off by default.

**Build column:** `Built` means the UI calls it today.

| Screen | Endpoint | operationId | Purpose | Auth | Build |
|---|---|---|---|---|---|
| Owner search | `GET /v2/owners?lastName&page&size` | `listOwnersPage` | Paged owner list (`OwnerPage`) | OWNER_ADMIN | Built |
| Add owner | `POST /owners` | `addOwner` | Create the owner (201 returns the `Owner`) | OWNER_ADMIN | Built |
| Owner details | `GET /owners/{ownerId}` | `getOwner` | Owner with pets and visits | OWNER_ADMIN | Not started |
| Owner details | `DELETE /owners/{ownerId}` | `deleteOwner` | Delete action (behind a confirmation dialog) | OWNER_ADMIN | Not started |
| Add pet | `GET /pettypes` | `listPetTypes` | Populate the type select | OWNER_ADMIN or VET_ADMIN | Not started |
| Add pet | `POST /owners/{ownerId}/pets` | `addPetToOwner` | Create the pet | OWNER_ADMIN | Not started |
| Edit owner ⚠️ | `GET /owners/{ownerId}` | `getOwner` | Prefill the form | OWNER_ADMIN | Not started |
| Edit owner ⚠️ | `PUT /owners/{ownerId}` | `updateOwner` | Save changes | OWNER_ADMIN | Not started |
| Edit pet ⚠️ | `GET /owners/{ownerId}/pets/{petId}` | `getOwnersPet` | Prefill the form | OWNER_ADMIN | Not started |
| Edit pet ⚠️ | `GET /pettypes` | `listPetTypes` | Populate the type select | OWNER_ADMIN or VET_ADMIN | Not started |
| Edit pet ⚠️ | `PUT /owners/{ownerId}/pets/{petId}` | `updateOwnersPet` | Save changes | OWNER_ADMIN | Not started |
| Add visit ⚠️ | `POST /owners/{ownerId}/pets/{petId}/visits` | `addVisitToOwner` | Record the visit | OWNER_ADMIN | Not started |
| Vets list ⚠️ | `GET /vets` | `listVets` | Vets and specialties | VET_ADMIN | Not started |
| Assistant panel | `petclinic-ai-agent` (none yet) | — | TBD | TBD | Preview only |

## Request details for built screens

- **Owner search**: the UI sends `lastName` (only when non-empty, trimmed), `page` (zero-based)
  and `size` (10, 20 or 50). Results come back in id order. `Owner.pets` is populated in each
  row, which the Pets column uses.
- **Add owner**: body is `OwnerFields`: `firstName`, `lastName` (1–30 letters with optional
  space/hyphen/apostrophe separators, up to three words), `address` (≤255), `city` (≤80),
  `telephone` (digits only, ≤20). The UI validates the same rules before sending; the server
  stays authoritative.

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

## Pending backend changes

Approved but scheduled for a separate branch after the owner UI branch merges:

- `GET /v2/owners` gains `sort` (`id` | `lastName`, default `id`) and `direction`
  (`asc` | `desc`, default `asc`); last-name sorts break ties on first name, then id.
- `lastName` matching becomes case-insensitive on every database for both
  `GET /owners` and `GET /v2/owners`, with `%` and `_` escaped so they are not wildcards.

Until then the UI does not send `sort`/`direction` and the Name column is not sortable.

## Known performance note

`Owner.pets` is eagerly fetched, so the paged owners query issues one extra select per owner
(about 21 queries for a 20-row page). Fine at clinic scale; deliberately left alone for now.
Revisit (e.g. batch fetching) if list performance matters.
