# Screen Inventory

Status as of 2026-10-14. Authoritative list of the screens we have decided to build and
what each is for. The 2026-10-13 full-workflow analysis (including the administration screens) is
merged into this document, [navigation-flow.md](navigation-flow.md),
[screen-mapping.md](screen-mapping.md) and [ui-overview.md](ui-overview.md).

**Decision key:** `Decided` = you specified or approved it; `Proposed` = my draft, ⚠️ needs
confirmation before it counts as decided; `Deferred` = known but intentionally not scheduled.
**Build key:** `Built`, `Placeholder` (a stub page so links resolve), `Not started`.

| # | Screen | Route | Purpose | Decision | Build |
|---|---|---|---|---|---|
| 1 | Owner search | `/owners` | Find owners; paged list with a last-name search. Entry point to the owner flow. | Decided | Built |
| 2 | Owner details | `/owners/:ownerId` | Show one owner's contact info, their pets and each pet's visits. Hub for owner actions, including Edit owner and the delete action (see below). | Decided | Built (owner, contact and pets with visits; Add pet / Add visit not yet) |
| 3 | Add pet | `/owners/:ownerId/pets/new` | Register a new pet (name, birth date, type) for an owner. | Decided | Not started |
| 4 | Add owner | `/owners/new` | Create an owner as its own page (first/last name, address, city, telephone). | Decided | Built |
| 5 | Edit owner | `/owners/:ownerId/edit` | Update an owner's contact details; shares the Add owner form. | Decided | Built |
| 6 | Edit pet | `/owners/:ownerId/pets/:petId/edit` | Update a pet's name, birth date or type; shares the Add pet form. | Proposed | Not started |
| 7 | Add visit | `/owners/:ownerId/pets/:petId/visits/new` | Record a visit (date, description) for a pet. | Proposed | Not started |
| 8 | Vets list | `/vets` | List of vets and their specialties; Add, Edit and Delete actions for `VET_ADMIN`. | Proposed | Not started |
| 9 | Edit visit | `/owners/:ownerId/pets/:petId/visits/:visitId/edit` | Correct a visit's date or description; shares the visit form with Add visit. | Proposed | Not started |
| 10 | Add vet | `/vets/new` | Create a vet (first/last name, specialties chosen from the catalog). | Proposed | Not started |
| 11 | Edit vet | `/vets/:vetId/edit` | Update a vet; shares the Add vet form. | Proposed | Not started |
| 12 | Specialties | `/specialties` | One page: the list, an add field, and inline edit and delete for each row. | Decided (decision B) | Not started |
| 13 | Pet types | `/pet-types` | Same shape as Specialties. | Decided (decision B) | Not started |
| 14 | Add user | `/admin/users/new` | Create a login account (`ADMIN` only). Create only: the API has no list, edit or delete for users. | Decided; deferred until login exists (decision D) | Not started |

Delete pet, Delete visit and Delete vet are confirmation dialogs on screens 2 and 8, not routes
(the same pattern as Delete owner). Specialties and Pet types do their deleting inside their own
list pages. That makes nine routed screens still to build (3 and 6 to 13), plus Add user (14), which is
deferred, and three new dialogs.

### Who does what

The role on each endpoint divides the work into three jobs. Hiding a screen by role is only a
convenience; the API enforces access (see [ui-overview.md](ui-overview.md)).

| Job | Role | Screens |
|---|---|---|
| Front desk | `OWNER_ADMIN` | Owners, pets, visits (screens 1 to 7 and 9) |
| Clinic admin | `VET_ADMIN` | Vets, specialties, pet types (screens 8, 10 to 13) |
| System admin | `ADMIN` | Add user (screen 14); `ADMIN` also satisfies the other two roles ([ADR-0006](../adr/0006-admin-role-hierarchy.md)) |

The Owners list's header pill currently reads "Clinic administration", although owners are front
desk work and "clinic admin" here means `VET_ADMIN`. Decided 2026-10-13: rename the pill to
"Clinic Management", together with the Add pet change (not yet done; `ui-overview.md` still
describes the current wording).

## Shell-level features (not routes)

| Feature | Purpose | Decision | Build |
|---|---|---|---|
| App shell | Header with dog mark and main navigation, skip link, and a floating Assistant launcher at the bottom right; hosts every screen. | Decided | Built |
| AI assistant panel | Right-hand docked panel available on every page for chatting with `petclinic-ai-agent` and for reviewing/confirming/rejecting AI-proposed writes. | Decided | Preview only (not connected; empty, open/closeable) |

The assistant is part of the shell, not a screen of its own. See [ui-overview.md](ui-overview.md)
for its behavior and the confirmation rule.

## Screen notes

**1. Owner search** — Table of Name ("Last, First"), Telephone, Address, City, Pets (first
two names then "+N"), paged with a page-size choice (10/20/50, default 20). Selecting an
owner opens screen 2. The search box filters by last name (prefix match, via the API's
`lastName` parameter, ignoring case). "Add owner" is the page's primary action. The **Name**
column sorts by last name: its header is a button that cycles A to Z, Z to A, then back to the
default id order, and each change returns to page 1. Only Name is sortable.

**2. Owner details** — Header with the owner's name and two icon actions, **Edit owner** (pencil) and
**Delete owner** (trash, red outline); a Contact
section (address, city, formatted telephone); a Pets section with one card per pet showing name,
type, birth date and its visits. Unknown or non-numeric ids show "Owner not found". Add pet and
Add visit actions arrive with those screens. Where the pet actions sit is decision G below.

**3. Add pet** — Form with name (required), birth date (required), type (select populated
from pet types). On success, returns to screen 2 showing the new pet. The same form serves
**6. Edit pet**, prefilled from the pet in the cached owner (decision A); the page title and
submit button change, the fields and validation do not.

**4. Add owner** — Single-column form, all fields required, validated to match the API's
rules. On success it opens the new owner's details.

### Delete owner

Deletion lives on **Owner details (screen 2)**, never in the owner list. ⚠️ The earlier plan was a
"More actions" menu; with only one action in it, the page header shows a **Delete owner** icon action
(trash, red outline, with an `aria-label` and tooltip) beside the Edit owner pencil instead. Say so if
you would rather have the menu.

It opens a modal confirmation that names the owner and how many pets will go with them, with
**Cancel** focused first and a red **Delete** button. On success the user returns to the
Owner search with a "Deleted owner …" message; on failure the dialog stays open with the error.
It requires `OWNER_ADMIN`. Deleting pets and visits follows the same pattern when those screens are
built.

### Delete confirmations state what else goes (decided 2026-10-13)

Every delete dialog says how many associated objects will be deleted with the item, the way Delete
owner already does. Where the count comes from:

| Delete | What goes with it | Count comes from |
|---|---|---|
| Owner (built) | The owner's pets and their visits | The loaded owner |
| Pet | The pet's visits (`CascadeType.ALL`) | The loaded owner |
| Visit | Nothing else | — |
| Vet | Nothing else; its specialty links are removed | — |
| Specialty | It is silently removed from every vet that has it | The vets list (cached) |
| Pet type | Every pet of that type, and their visits (no guard exists) | `petCount` and `visitCount` on `PetTypeWithCounts`, returned by `GET /pettypes` (decision H) |

**Pet type counts (decision H).** The owner list is paged, so the UI cannot count the pets of a
type from data it already holds. The counts come from the backend instead: a new
`PetTypeWithCounts` schema in `openapi.yml` (the pet type fields plus read-only `petCount` and
`visitCount`), returned by `GET /pettypes` and `GET /pettypes/{petTypeId}`. `PetType` itself is
unchanged, so `Pet.type` stays as it is and pet payloads cost no extra query. This follows the
existing pattern of read-only derived data in a resource (`Owner.pets`). It is an API change, so
it is its own backend branch before the Pet types screen.
⚠️ Also, shipping Delete with a count-based confirmation now means the delete-everything behaviour
ships; the planned guard (refuse with a 409 when pets use the type, see
[project-status.md](../project-status.md)) would later replace it.

## Deliberately not included (⚠️ confirm)

- *Standalone pet and visit lists* (`/pets`, `/visits`) — the owner flow reaches these
  through owner details.
- *Login* — see the security note in [ui-overview.md](ui-overview.md).

## Decisions from the full-workflow analysis (2026-10-13 to 2026-10-14)

| | Decision | Status |
|---|---|---|
| A | Edit pet and Edit visit prefill from the owner already in the cache (no new calls) rather than their own GET endpoints. | **Decided** |
| B | Specialties and Pet types are one list page each with an inline form (a single name field makes separate Add and Edit pages near-empty), rather than separate routes like vets. Trade-offs: fewer screens, but inline editing needs care with focus, error placement and tests. | **Decided** |
| C | Pet types include Delete, with a count-based confirmation, now. | **Decided** (replaces the earlier recommendation to wait for the backend guard) |
| D | Add user is deferred until login exists ([ADR-0009](../adr/0009-login-and-authentication-mechanism.md)). The form sends a plaintext password, so it must wait for login and HTTPS. | **Decided** |
| E | Every delete dialog states how many associated objects go with the item (table above). | **Decided** |
| F | Write errors show the server's `detail`, never "not found": foreign-key failures answered `404` until PC012, which made them `409` (see [ui-overview.md](ui-overview.md#backend-behaviour-the-ui-must-respect)). The client already carries `detail` (the Delete owner dialog shows it), so the work is the rule for new write screens plus reading `schemaValidationErrors`. The backend status is done (PC012, decided 409); the UI rule and the field messages are not yet built. | **Decided** (backend done, UI not yet built) |
| G | Pet and visit actions sit on Owner details, not behind a pet page. The **Pets** heading row has a right-aligned **Add pet** text button. Each pet card has a top-right cluster of two icon actions, **Edit** (pencil, links to screen 6) and **Delete** (trash, red outline, opens the Delete pet dialog); the name truncates before the cluster. Each card's visits area has an **Add visit** text button (and, when Edit and Delete visit are built, the same per-visit icon pattern). Icon-only controls carry an `aria-label` that names the pet or visit ("Edit Rosy"), a `title` tooltip and a 40px hit area (44px on touch); Delete returns focus to its icon when cancelled. Owner details uses the same `iconAction` pattern for Edit owner and Delete owner. ⚠️ Assumptions: Add pet sits in the Pets heading row rather than beside the card icons, because those icons are per card; Add visit sits in the card body for the same reason. | **Decided** (not yet built) |
| H | Pet type counts come from the backend: a separate `PetTypeWithCounts` schema (pet type fields plus read-only `petCount` and `visitCount`) returned by `GET /pettypes` and `GET /pettypes/{petTypeId}`; `PetType` and `Pet.type` are unchanged. Pet and visit writes (add, edit, delete) also invalidate the `['pet-types']` query so the counts never go stale. Because `listPetTypes` now returns the counted schema everywhere, the Add pet and Edit pet type selects receive counts they ignore. | **Decided** (docs only; the `openapi.yml` and backend change is its own branch, not yet built) |

## Deferred

Nothing is deferred at the moment. The sortable Name column and the case-insensitive search
that used to be listed here were built in PC004.

## Suggested build order

Shell → Owner search → Add owner (done) → Owner details (with Edit and Delete, done) → then:

1. **Two backend-first fixes, each on its own branch** (decided 2026-10-13): the foreign-key
   failure that answered `404` and now answers `409` (decision F, PC012), then the pet-type usage counts for the Delete pet type
   dialog. Write-error handling in the UI (show the server's `detail` and field messages) follows
   so every later write screen reports errors correctly.
2. **Front desk core:** Add pet, Add visit, Edit pet, Edit visit, Delete pet, Delete visit.
3. **Clinic reads:** Vets list.
4. **Catalogs and vet admin:** Specialties, Add and Edit vet, Delete vet, Pet types.
5. **System admin:** Add user, after login (ADR-0009).
6. Connect the AI assistant once `petclinic-ai-agent` has endpoints.

The AI assistant stays a shell feature. Its confirmed writes call the same endpoints, so every
write function in `src/api/` stays a plain function the confirmation flow can also call.
