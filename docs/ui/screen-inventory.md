# Screen Inventory

Status as of 2026-10-07. Authoritative list of the screens we have decided to build and
what each is for.

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
| 6 | Edit pet | `/owners/:ownerId/pets/:petId/edit` | Update a pet's name, birth date or type. | Proposed | Not started |
| 7 | Add visit | `/owners/:ownerId/pets/:petId/visits/new` | Record a visit (date, description) for a pet. | Proposed | Not started |
| 8 | Vets list | `/vets` | Read-only list of vets and their specialties. | Proposed | Not started |

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

**2. Owner details** — Header with the owner's name, **Edit owner** and **Delete owner**; a Contact
section (address, city, formatted telephone); a Pets section with one card per pet showing name,
type, birth date and its visits. Unknown or non-numeric ids show "Owner not found". Add pet and
Add visit actions arrive with those screens.

**3. Add pet** — Form with name (required), birth date (required), type (select populated
from pet types). On success, returns to screen 2 showing the new pet.

**4. Add owner** — Single-column form, all fields required, validated to match the API's
rules. On success it opens the new owner's details.

### Delete owner

Deletion lives on **Owner details (screen 2)**, never in the owner list. ⚠️ The earlier plan was a
"More actions" menu; with only one action in it, the page header shows a plain **Delete owner**
button (red outline) beside Edit owner instead. Say so if you would rather have the menu.

It opens a modal confirmation that names the owner and how many pets will go with them, with
**Cancel** focused first and a red **Delete** button. On success the user returns to the
Owner search with a "Deleted owner …" message; on failure the dialog stays open with the error.
It requires `OWNER_ADMIN`. Deleting pets and visits follows the same pattern when those screens are
built.

## Deliberately not included (⚠️ confirm)

- *Pet type, specialty, vet and user administration* — the API supports these; they are
  back-office tasks and not part of the first slices.
- *Standalone pet and visit lists* (`/pets`, `/visits`) — the owner flow reaches these
  through owner details.
- *Login* — see the security note in [ui-overview.md](ui-overview.md).

## Deferred

Nothing is deferred at the moment. The sortable Name column and the case-insensitive search
that used to be listed here were built in PC004.

## Suggested build order

Shell → Owner search → Add owner (done) → Owner details (with Edit and Delete, done) → Add pet →
the remaining proposed screens → connect the AI assistant once `petclinic-ai-agent` has
endpoints.
