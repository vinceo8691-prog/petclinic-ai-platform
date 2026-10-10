# Project Status

**Week of 2026-10-05 to 2026-10-11**, updated 2026-10-10 (Saturday). Update this file at the
start of each week and replace the sections below; older weeks stay in git history.

> ⚠️ **Assumptions to confirm** are marked with ⚠️. The week boundaries (Monday to Sunday) and
> the PR numbers after PC003 are my assumptions, not decisions.

## Where we are

- `main` has the REST backend, the app shell, the Owners list and the Add owner page
  (PR #3, **PC001_Owner_UI_Screen**, merged 2026-10-08).
- Owner details, Edit owner and Delete owner are on `main` (PR #4, **PC002_Owner_Details_Screen**,
  merged 2026-10-10). (#4 is GitHub's own number; PC002 is our title prefix.)
- CI and repo hygiene are on `main` (PR #5, **PC003_CI_Repo_Enhancements**, squash-merged
  2026-10-10): root Dependabot config, Backend CI, UI CI and Newman smoke tests on every PR and on
  pushes to `main`, and a PR template. The `main protection` ruleset is active.
- **PC004** (last-name sort, case-insensitive search and the telephone fix) is in progress on
  `feature/backend_owner_sort_fix`.
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

**PC003, merged 2026-10-10** (PR #5)
- Dependabot config moved to the repo root, with monthly grouped updates and at most three open
  PRs per ecosystem; Backend CI, UI CI and Newman smoke tests (no path filters); a PR template;
  `mvnw` made executable in git. All checks passed on GitHub, including GitHub's own Dependabot
  config validation.

**PC002, merged 2026-10-10** (`feature/OwnerDetailsScreen`; 96 UI tests, lint and build pass)
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

1. **PC004 ⚠️, last-name sort, case-insensitive search and the telephone fix** (in progress,
   `feature/backend_owner_sort_fix`).
   - **Sort and search, backend first:** `sort` (`id` or `lastName`, default `id`) and `direction`
     (`asc` or `desc`, default `asc`) on `GET /v2/owners`; last-name sorts break ties on first name,
     then id; an invalid `sort` returns 400. Search becomes case-insensitive on both v1 and v2 with
     `%` and `_` escaped. Tests include the H2 profile; docs updated. Then the UI Name column sort
     (`aria-sort`, URL parameters). Full design is in the saved plan.
   - **Telephone validation mismatch:** the entity requires exactly 10 digits but `openapi.yml`
     allows up to 20, so a bad number gets a 500. Change the spec to exactly 10 digits, map entity
     validation failures to 400, and update the UI rule, tests and docs to match.
2. **PC005 ⚠️, owner delete fix.** Remove the cascade from `Pet.type` in the backend, with tests.
3. **Agent-authorization ADR, its own branch and PR ⚠️** (number not assigned; by default after
   PC005). Moved out of the sort PR on 2026-10-10 because it also needs a new role or scope in the
   backend. The backend enforces what AI-initiated writes may do; the agent's own credentials are
   read-only; a confirmed write runs with the confirming user's credentials and the exact approved
   payload, audited as AI-proposed and user-confirmed.
4. **Generate the TypeScript API types** from `openapi.yml` (small PR before Add pet, see the
   Backlog).
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
  returns 500, not 400. In progress in PC004.
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
  (types only) is planned after PC005 and the ADR branch; see the Backlog.

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

- **Dependabot config and workflows moved to the repo root** (done in PC003, merged).
  They had sat in `spring-petclinic-rest/.github/`, which GitHub does not read. Dependabot PRs #1
  and #2 were closed unmerged on 2026-10-08 (springdoc 3.1.0 and refactor-first plugin 0.9.0 stay
  as they are). Left behind on purpose: `spring-petclinic-rest/.github/workflows/docker-build.yml`
  (the upstream Docker Hub publish; inert there, needs secrets we have not chosen, and the plan
  deploys to AWS). Delete it or replace it when the image registry is decided.
- **`mvnw` was not executable in git** (mode 100644), which would have failed `./mvnw` on the Linux
  runners. Fixed in PC003 (now 100755).
- **First Dependabot PRs triaged (#6 to #12, 2026-10-10).** Merged: the GitHub Actions bumps #6
  (`upload-artifact` 7), #7 (`checkout` 7) and #8 (`setup-node` 7), and #9 (`typescript-eslint`
  8.71.1); all checks were green. Closed and ignored: #10 (`@vitejs/plugin-react` 6) and #12
  (`typescript` 7), which fail at `npm ci` with dependency conflicts (plugin-react 6 needs a newer
  Vite than our 6.4.3; typescript-eslint 8.71.0 does not support TypeScript 7), with
  `@dependabot ignore this major version`. Closed without merging: #11 (backend Maven group of
  five: springdoc 3.1.1, `jackson-databind-nullable` 0.2.12, refactor-first plugin 0.10.0,
  `openapi-generator` plugin 7.26.0, Maven 3.10.0). Checks were green, but they are routine
  updates, not security fixes, so the backend stays on springdoc 3.1.0, refactor-first 0.9.0 and
  the generator plugin 7.25.0 for now. Dependabot can propose newer versions on its monthly run.
  Upgrading Vite and TypeScript together with typescript-eslint is a deliberate future task.
- **Generate the TypeScript API types from `openapi.yml`** (ADR-0003 anticipated it). Proposed
  timing: a small PR before Add pet, when the type count grows (the CI freshness check is now
  possible, since PC003 is merged). Plan: add
  `openapi-typescript` as a dev dependency (types only, nothing shipped in the bundle; pin the
  version), an npm script reading `spring-petclinic-rest/src/main/resources/openapi.yml`, a
  committed generated file, `types.ts` re-exporting friendly names so screens do not change, and a
  CI check that fails when the committed file is out of date. Keep our own fetch client and query
  hooks (no generated client or hooks), and keep `ownerValidation.ts` hand-written, because types
  do not encode rules such as "exactly 10 digits". ⚠️ Check how readonly fields (`id`, `pets`)
  come out, and that the spec matches real responses (the hand-written `Visit` already lacks
  `petId`).
- ✅ Dependabot **security updates** were enabled in the GitHub repo settings (2026-10-09).
