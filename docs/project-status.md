# Project Status

**Week of 2026-10-05 to 2026-10-11**, written 2026-10-08 (Thursday). Update this file at the
start of each week and replace the sections below; older weeks stay in git history.

> ⚠️ **Assumptions to confirm** are marked with ⚠️. The week boundaries (Monday to Sunday) and
> the PR numbers after PC003 are my assumptions, not decisions.

## Where we are

- `main` has the REST backend, the app shell, the Owners list and the Add owner page
  (PR #3, **PC001_Owner_UI_Screen**, merged 2026-10-08).
- Owner details, Edit owner and Delete owner are built and tested. They are on
  `feature/OwnerDetailsScreen` in the open PR #4, **PC002_Owner_Details_Screen**, awaiting
  review. (#4 is GitHub's own number; PC002 is our title prefix.)
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

**In PC002, open for review** (`feature/OwnerDetailsScreen`; 96 UI tests, lint and build pass)
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
- **Retry rule:** queries no longer retry client errors (4xx), so an unknown owner reports
  "not found" at once instead of after about seven seconds.
- **Docs:** the `docs/ui/` files updated for all of the above, including a new Frontend
  conventions section; ADR-0007 renumbered to ADR-0006 with its references fixed (including
  comments in three backend files); this status doc.

## Next tasks

In the order we agreed, one branch at a time, each merged and approved before the next:

1. **PC002, owner details (PR #4).** Review and approve, then merge.
2. **PC003 ⚠️, CI and repo hygiene** (decided 2026-10-09, moved ahead of the sort work so its
   tests run in CI). Move the Dependabot config and the build workflows to the repo root and
   adjust them for the `spring-petclinic-rest` subfolder, add a UI workflow (`npm ci`, lint, test,
   build), keep the Docker Hub push out, and set up a GitHub ruleset on `main` (pull request and
   passing checks required, approvals 0). CI runs on every PR and again on pushes to `main`.
   Also a short PR template (tests, docs, security implications) and an **AI review comment
   posted on each PR before it is merged**, starting as a manual step (see Important decisions).
3. **PC004 ⚠️, last-name sort and case-insensitive search.** Backend first: `sort` (`id` or
   `lastName`) and `direction` on `GET /v2/owners`, case-insensitive prefix search on both v1 and
   v2 with `%` and `_` escaped, tests including the H2 profile, docs. Then the UI Name column
   sort. Full design is in the saved plan. Also fix the **telephone validation mismatch** here (moved in 2026-10-09): the entity requires
   exactly 10 digits but `openapi.yml` allows up to 20, so a bad number gets a 500; change the spec
   to exactly 10 digits, map entity validation failures to 400, and update the UI rule and docs to
   match. Also draft the **agent-authorization ADR** during
   this backend work (the backend enforces AI-write permission; the agent's credentials are
   read-only).
4. **PC005 ⚠️, owner delete fix.** Remove the cascade from `Pet.type` in the backend, with tests.
5. **After that:** Add pet, then Edit pet, Add visit and the Vets list; the login design; wiring
   the assistant once `petclinic-ai-agent` has endpoints.

## Blockers and risks

- **Deleting an owner who has pets fails** (backend bug, reproduced 2026-10-08). `Pet.type` is
  mapped `CascadeType.ALL`, so the delete tries to remove a shared pet type and the database
  rejects it. The API answers 404 "data constraint violation" and no data is lost. Owners with
  no pets delete fine. Blocked until PC005; the UI shows the server's message in the dialog.
- **No login flow is designed.** Security is off by default and the UI sends no credentials.
  This blocks any non-local deployment and needs its own ADR.
- **No JDK on the shell `PATH`.** Backend builds from the terminal need `JAVA_HOME` set (a JDK
  is at `C:\Users\vince\.jdks\openjdk-25.0.2`). IntelliJ is unaffected.
- **Known and accepted:** the paged owners query loads each owner's pets separately (about 21
  queries per 20-row page). Deliberately left alone.
- **Telephone rule mismatch** (entity: exactly 10 digits; spec and UI: up to 20). A bad number
  returns 500, not 400. Scheduled in PC004.
- ⚠️ **Last-write-wins edits:** `PUT /owners/{id}` has no version or ETag, so two people editing
  the same owner overwrite each other silently.

## Important decisions

**Process**
- Work in small branches, merged one at a time. Branch order: owner UI, owner details, last-name
  sort, owner delete fix (decided 2026-10-08).
- PR titles start with `PCXXX`, a three-digit number counting up from PC001, followed by text you
  supply (2026-10-08).
- Every PR gets an AI review comment before the merge (decided 2026-10-09). It is a comment, not
  an approval, and starts as a manual step: Claude reviews the diff and posts the comment when
  asked. ⚠️ An automated GitHub Action is possible later, but it needs an API key stored as a repo
  secret and a third-party action, so it needs a security look first.
- ADR numbering has no gaps: the role-hierarchy ADR is now 0006 (2026-10-08). The old ADR-0006
  (pet/owner wiring) lives in `docs/architecture/` because it was a bug fix, not a decision.

**Architecture and conventions**
- Server data lives in TanStack Query; list state lives in the URL; everything else is local
  `useState`. No global store, form library or UI kit.
- Three layers per feature: plain `api/` functions, one hooks file, then components.
- Extract a shared component only after its second or third use.
- API types are hand-written for now. Generating them from `openapi.yml` with `openapi-typescript`
  (types only) is planned after PC003; see the Backlog.

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

## Backlog

Items with no date yet. **Copy this section forward each week; do not replace it.** If it grows
past about ten items, move it to GitHub Issues and link them here.

- **Move the Dependabot config and workflows to the repo root.** (Scheduled as PC003.) They sit in
  `spring-petclinic-rest/.github/`, but GitHub only reads the root `.github/`, so version-update
  PRs and the build workflows (Maven build, Docker build, Newman) probably no longer run. The
  workflows need their paths adjusted for the `spring-petclinic-rest` subfolder, so try them on a
  branch first. Dependabot PRs #1 and #2 were closed unmerged on 2026-10-08 (springdoc 3.1.0 and
  refactor-first plugin 0.9.0 stay as they are).
- **Generate the TypeScript API types from `openapi.yml`** (ADR-0003 anticipated it). Proposed
  timing: a small PR after PC003 (CI) and before Add pet, when the type count grows. Plan: add
  `openapi-typescript` as a dev dependency (types only, nothing shipped in the bundle; pin the
  version), an npm script reading `spring-petclinic-rest/src/main/resources/openapi.yml`, a
  committed generated file, `types.ts` re-exporting friendly names so screens do not change, and a
  CI check that fails when the committed file is out of date. Keep our own fetch client and query
  hooks (no generated client or hooks), and keep `ownerValidation.ts` hand-written, because types
  do not encode rules such as "exactly 10 digits". ⚠️ Check how readonly fields (`id`, `pets`)
  come out, and that the spec matches real responses (the hand-written `Visit` already lacks
  `petId`).
- ✅ Dependabot **security updates** were enabled in the GitHub repo settings (2026-10-09).
