# Project Status

**Week of 2026-10-05 to 2026-10-11**, written 2026-10-08 (Thursday). Update this file at the
start of each week and replace the sections below; older weeks stay in git history.

> ⚠️ **Assumptions to confirm** are marked with ⚠️. The week boundaries (Monday to Sunday) and
> the PR numbers after PC002 are my assumptions, not decisions.

## Where we are

- `main` has the REST backend, the app shell, the Owners list and the Add owner page
  (PR #3, **PC001_Owner_UI_Screen**, merged 2026-10-08).
- Owner details, Edit owner and Delete owner are built and tested but **not yet committed**.
  They are on `feature/OwnerDetailsScreen`, which becomes **PC002**.
- `petclinic-ai-agent` is still scaffolding only; the assistant in the UI is a stub panel.

## Completed this week

**Merged to `main`**
- **PC001 Owner UI** (PR #3): app shell with header, skip link and focus handling; Owners list
  (URL-driven search, paging and page size, loading/empty/error states, responsive table);
  Add owner page; a floating Assistant launcher with a docked right-hand panel (preview only);
  the four living docs in `docs/ui/`; tests for all of it.
- **ADMIN role hierarchy** in the backend, so `ROLE_ADMIN` outranks `OWNER_ADMIN` and
  `VET_ADMIN` (2026-10-05).
- **Fix for denied `@PreAuthorize` checks returning 500 instead of 403** (2026-10-05).
- **Development guidelines** added to `CLAUDE.md`: call out security implications, and no
  single-character names outside loops and short callbacks.

**Built, uncommitted on `feature/OwnerDetailsScreen`** (PC002, 84 UI tests, lint and build pass)
- **Redesign:** teal page background and artwork, one large rounded card, header with the dog
  mark and underlined current item, avatars and pet pills in the table, a single View icon per
  row, light-blue clouds, dog favicon.
- **Owner details page:** contact panel and a card per pet with its type, birth date and visits.
- **Edit owner page:** the Add owner form, prefilled, saving with `PUT`.
- **Delete owner:** icon button on the details page opening a confirmation dialog; on success it
  returns to the list with a "Deleted owner …" message.
- **Refactors:** shared `OwnerForm`, `TextField` (replaces the render-prop `FormField`),
  `OwnerRoute` (shared loader for details and edit), validation now appears after the first
  submit attempt, not on blur.
- **Docs:** the `docs/ui/` files updated for all of the above; ADR-0007 renumbered to
  ADR-0006 with its references fixed (including comments in three backend files).

## Next tasks

In the order we agreed, one branch at a time, each merged and approved before the next:

1. **PC002, owner details.** Finish your review of the owner screens, then commit, push and open
   the PR (you supply the text after `PC002_`).
2. **PC003 ⚠️, last-name sort and case-insensitive search.** Backend first: `sort` (`id` or
   `lastName`) and `direction` on `GET /v2/owners`, case-insensitive prefix search on both v1 and
   v2 with `%` and `_` escaped, tests including the H2 profile, docs. Then the UI Name column
   sort. Full design is in the saved plan.
3. **PC004 ⚠️, owner delete fix.** Remove the cascade from `Pet.type` in the backend, with tests.
4. **After that:** Add pet, then Edit pet, Add visit and the Vets list; the login design; wiring
   the assistant once `petclinic-ai-agent` has endpoints.

## Blockers and risks

- **Deleting an owner who has pets fails** (backend bug, reproduced 2026-10-08). `Pet.type` is
  mapped `CascadeType.ALL`, so the delete tries to remove a shared pet type and the database
  rejects it. The API answers 404 "data constraint violation" and no data is lost. Owners with
  no pets delete fine. Blocked until PC004; the UI shows the server's message in the dialog.
- **No login flow is designed.** Security is off by default and the UI sends no credentials.
  This blocks any non-local deployment and needs its own ADR.
- **No JDK on the shell `PATH`.** Backend builds from the terminal need `JAVA_HOME` set (a JDK
  is at `C:\Users\vince\.jdks\openjdk-25.0.2`). IntelliJ is unaffected.
- **Known and accepted:** the paged owners query loads each owner's pets separately (about 21
  queries per 20-row page). Deliberately left alone.
- ⚠️ **Last-write-wins edits:** `PUT /owners/{id}` has no version or ETag, so two people editing
  the same owner overwrite each other silently.

## Important decisions

**Process**
- Work in small branches, merged one at a time. Branch order: owner UI, owner details, last-name
  sort, owner delete fix (decided 2026-10-08).
- PR titles start with `PCXXX`, a three-digit number counting up from PC001, followed by text you
  supply (2026-10-08).
- ADR numbering has no gaps: the role-hierarchy ADR is now 0006 (2026-10-08). The old ADR-0006
  (pet/owner wiring) lives in `docs/architecture/` because it was a bug fix, not a decision.

**Architecture and conventions**
- Server data lives in TanStack Query; list state lives in the URL; everything else is local
  `useState`. No global store, form library or UI kit.
- Three layers per feature: plain `api/` functions, one hooks file, then components.
- Extract a shared component only after its second or third use.
- API types are hand-written for now; generating them from `openapi.yml` is anticipated by
  ADR-0003 but undecided.

**Owner UI**
- Add owner and Edit owner are their own pages, not dialogs.
- The Assistant is a floating button at the bottom right that opens a docked right-hand panel;
  the button hides while it is open (the panel is a preview, not connected to the agent).
- Owner delete lives only on the details page, behind a confirmation dialog, never in the list.
  The button reads "Delete" and a successful delete returns to the list with a message.
- Ten-digit phone numbers are shown as `(608) 555-1023`; other values are shown as stored.
- Look and feel: teal palette, white card over decorative artwork, English text, art hidden on
  phones, one dog (the header mark, also the favicon).

**Backend (approved, not yet built)**
- `GET /v2/owners` gains `sort` (`id` | `lastName`, default `id`) and `direction` (`asc` | `desc`,
  default `asc`); last-name sorts break ties on first name, then id. The UI keeps using `id`
  until the sort branch lands.
- Owner search becomes case-insensitive on both v1 and v2, with `%` and `_` escaped.

**Security**
- `ROLE_ADMIN` outranks `OWNER_ADMIN` and `VET_ADMIN` (ADR-0006).
- Denied authorization returns 403, not 500.
- Never put credentials in `VITE_*` variables; owner data is held in memory only; only the
  assistant's open/closed preference is stored in `localStorage`.
