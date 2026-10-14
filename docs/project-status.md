# Project Status

**Week of 2026-10-12 to 2026-10-18**, updated 2026-10-14 (Wednesday). Update this file at the
start of each week and replace the sections below; older weeks stay in git history. The
previous week (2026-10-05 to 2026-10-11) is summarised under "Where we are".

> ⚠️ **Assumptions to confirm** are marked with ⚠️. The week boundaries (Monday to Sunday) are
> my assumption, not a decision. PR numbers are GitHub's own; the PCXXX prefix is our title
> convention.

## Where we are

`main` is at PR #22 and has everything below. PC008, PC009 and PC010 were merged on 2026-10-12,
PC011 to PC013 on 2026-10-14.

| PR | Title prefix | What it delivered | Merged |
|---|---|---|---|
| #3 | PC001 | App shell, Owners list (search, paging, page size), Add owner page, assistant panel preview, the four living docs in `docs/ui/` | 2026-10-08 |
| #4 | PC002 | Owner details, Edit owner, Delete owner; shared owner form; the 4xx retry rule | 2026-10-10 |
| #5 | PC003 | CI (backend, UI, Newman) on every PR and on `main`, root Dependabot config, PR template, `main` ruleset | 2026-10-10 |
| #13 | PC004 | Last-name sort, case-insensitive search, 10-digit telephone rule, 400 instead of 500 for bad input | 2026-10-10 |
| #14 | PC006 | ADR-0007, ADR-0002 pointer, five Mermaid UML diagrams, performance document; `system-overview.md` moved to `docs/architecture/` | 2026-10-11 |
| #15 | PC007 | Tooltip on the owners Name sort button; ADR-0007 accepted | 2026-10-11 |
| #16 | PC005 | Owner delete fix: `Pet.type` no longer cascades; `PetTypeRepositoryImpl.delete` dropped a dead `em.remove` | 2026-10-11 |
| #17 | PC009 | ADR-0007 amendments, ADR-0008 (Proposed), ADR-0009 (Placeholder), docs consistency pass | 2026-10-12 |
| #18 | PC010 | `openapi.yml` says 204 No Content (no body) for the six `PUT` and six `DELETE` endpoints; controllers return `ResponseEntity<Void>` | 2026-10-12 |
| #19 | PC008 | TypeScript API types generated from `openapi.yml` (`schema.d.ts`) with a CI freshness check; five `POST` specs corrected from `200` to `201` | 2026-10-12 |
| #20 | PC011 | Full-workflow screen analysis merged into `docs/ui/` (14 screens, build order); decisions A to H | 2026-10-14 |
| #21 | PC012 | Data-constraint failures answer `409` instead of `404`; `409` documented on all 21 write operations (decision F, backend half) | 2026-10-14 |
| #22 | PC013 | Server `detail` and field messages in the owner form; `describeWriteError` for 401 and 403 on writes (decision F, UI half) | 2026-10-14 |

Also already on `main` before the PC numbering: the `ROLE_ADMIN` hierarchy (ADR-0006) and the
fix that makes a denied `@PreAuthorize` answer 403 instead of 500 (2026-10-05).

**Open or planned work**

- **Screens analysis (PC011, merged).** The full workflow analysis, including the administration
  screens, is in the four UI docs. Decided:
  A (Edit pet and Edit visit prefill from the cached owner), B (Specialties and Pet types are one
  list page each with an inline form), C (Pet types include Delete now, with a count-based
  confirmation), D (Add user waits for login), E (every delete dialog states what else goes), F (write errors show the server's `detail`),
  G (pet and visit actions are icons on each pet card and buttons in the Pets heading row) and H
  (pet type counts come from a separate `PetTypeWithCounts` schema on `GET /pettypes`, and pet and
  visit writes invalidate `['pet-types']`). H's backend half is PC014, below; its invalidation
  lands with the pet and visit write screens. See `docs/ui/screen-inventory.md`.
- **PC014, pet type usage counts (decision H, backend; branch `feature/PC014_pet_count_usage`,
  uncommitted, awaiting review).** `GET /pettypes` and `GET /pettypes/{petTypeId}` return a new
  `PetTypeWithCounts` schema (`petCount`, `visitCount`); `POST` and `PUT` keep `PetType`. One
  grouped JPQL count query (`PetTypeRepository.findAllWithCounts` and `findWithCountsById`), a
  `PetTypeWithCounts` record, service and mapper methods. Tests: 238 backend (was 232), 134 UI,
  lint and build green; the real endpoint was also checked on a throwaway port (cat 4 pets and 4
  visits, a new type 0 and 0, unknown id 404). `schema.d.ts` regenerated and `PetTypeWithCounts`
  aliased in `types.ts`. No UI screen uses it yet (the Pet types screen is later), and the
  `['pet-types']` invalidation lands with the pet and visit write screens.
- `petclinic-ai-agent` is still scaffolding only; the assistant in the UI is a stub panel.

## Completed this week

Merged:

- **PC008 (PR #19)** and **PC010 (PR #18):** described in the table above.
- **PC011 to PC013 (PRs #20 to #22):** the screen analysis and decisions A to H, the `409` for
  data-constraint failures, and the server `detail` and field messages in the owner form. Backend
  232 tests and UI 134 tests, lint and build green at PC013.

PC009 (PR #17, docs only) delivered:

- **ADR-0007 amended in place, eight amendments:** no raw credentials through the agent and a
  token mechanism (amendment 1); one authoritative proposal contract with shared examples
  (2, and the four refinements in 7); optimistic concurrency as a production-readiness gate
  (3); audit information versus verified evidence (4); security failures as 401 or 403 (5);
  two independent triggers for server-side approval records (6); the UI performs the token
  exchange so the agent never holds a write-capable token (8).
- **ADR-0008** (Proposed): security failures answer 401 or 403 as `ProblemDetail`, through one
  path. The 500 defect itself was fixed on 2026-10-05.
- **ADR-0009** (Placeholder): the login and authentication mechanism, with its open
  questions, so the login screen stays on the radar.
- **A consistency pass over all docs**, including correcting ADR-0006's stale "not fixed" note.

PC008 (PR #19) was checked at 231 backend tests and 113 UI tests, with lint and build green.

## Next tasks

In the order we agreed, one branch at a time, each merged and approved before the next:

1. **PC009, security ADR enhancements** (PR #17, docs only): merged.
2. **PC010, body-on-204 `openapi.yml` fix** (PR #18): merged. The spec says 204 No Content for
   the six update and six delete endpoints, and the controllers no longer build a body.
3. **PC008, generate the TypeScript API types** from `openapi.yml` and fix the five `POST`
   specs to 201 (PR #19): merged.
4. **Planned flow for the rest of the project ⚠️ (proposed 2026-10-11):**
   1. List the screens needed to cover the API use cases. Done 2026-10-13: see
      [ui/screen-inventory.md](ui/screen-inventory.md) (14 screens, build order and decisions A to H).
   2. Build the screens one at a time. Write-error handling (decision F) is done (PC012 backend,
      PC013 UI) and the pet type counts (decision H) are in review as PC014. Next: the front desk
      screens (Add pet and Edit pet on one shared form, Add and Edit visit, Delete pet and visit),
      then the Vets list, the catalogs and vet admin.
   3. Connect the screens to the API.
   4. **Login page and its own ADR ([ADR-0009](adr/0009-login-and-authentication-mechanism.md),
      a placeholder listing the open questions)**, once the UI screens exist. This is the
      prerequisite for enabling security outside local development and for the agent flow,
      because ADR-0007 (amendments 1 and 8) requires a token mechanism in which the UI
      obtains a delegated read-only token for the agent, and rules out passing passwords or
      the employee's own token through the agent.
   5. Build the AI agent with its features, evals and guardrails, in the three stages in
      ADR-0007: read-only first, then one AI-proposed write (`UPDATE_OWNER_TELEPHONE`), then
      stronger approval guarantees only if needed.
   6. Integrate UI, agent and API (UI to agent to API) and implement the confirmed-write flow.

## Blockers and risks

- **No login flow is designed.** Security is off by default and the UI sends no credentials.
  This blocks any non-local deployment and needs its own ADR, tracked as the ADR-0009
  placeholder. It is planned for after the UI
  screens are built (see the planned flow above). ADR-0007 (amendment 1) requires a token
  mechanism and rules out forwarding passwords through the agent; the login and token ADR must
  decide the issuer, claims and lifetimes, and where the browser holds the token (memory only,
  never `localStorage`).
- **Not production-ready, by design (ADR-0007 gates).** Three things must exist before the
  system is called production-ready: token-based authentication with delegation (no raw
  credentials through the agent), optimistic concurrency (`@Version`, `ETag` / `If-Match`) on every
  updatable resource, and either independently verifiable approval records or wording that does
  not oversell the audit log. Today none of the three exists; the demo fallbacks are documented as
  fallbacks.
- **No JDK on the shell `PATH`.** Backend builds from the terminal need `JAVA_HOME` set (a JDK
  is at `C:\Users\vince\.jdks\openjdk-25.0.2`). IntelliJ is unaffected.
- **Known and accepted:** the paged owners query loads each owner's pets separately (about 21
  queries per 20-row page). Deliberately left alone.
- ⚠️ **Last-write-wins edits:** `PUT /owners/{id}` has no version or ETag, so two people editing
  the same owner overwrite each other silently. ADR-0007 decision 5: a confirm-time comparison is
  the demo fallback only, and `@Version` with `ETag` / `If-Match` on every updatable resource is a
  **production-readiness gate** (its own PR), which also fixes the Edit owner page.

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
- API types are generated from `openapi.yml` with `openapi-typescript` (types only, PC008) into
  `src/api/schema.d.ts`; `src/api/types.ts` gives them friendly names. Run `npm run api:generate`
  after any spec change; CI fails if the committed file is stale.

**Owner UI**
- Add owner and Edit owner are their own pages, not dialogs.
- The Assistant is a floating button at the bottom right that opens a docked right-hand panel;
  the button hides while it is open (the panel is a preview, not connected to the agent).
- Owner delete lives only on the details page, behind a confirmation dialog, never in the list.
  It is a trash icon (red outline) in the page header, and a successful delete returns to the list
  with a message.
- Ten-digit phone numbers are shown as `(608) 555-1023`; other values are shown as stored.
- Look and feel: teal palette, white card over decorative artwork, English text, art hidden on
  phones, one dog (the header mark, also the favicon).

**Backend (built in PC004)**
- `GET /v2/owners` takes `sort` (`id` | `lastName`, default `id`) and `direction` (`asc` | `desc`,
  default `asc`); last-name sorts break ties on first name, then id.
- Owner search is case-insensitive on both v1 and v2, with `%` and `_` escaped.

**Security**
- `ROLE_ADMIN` outranks `OWNER_ADMIN` and `VET_ADMIN` (ADR-0006).
- Denied authorization returns 403, not 500.
- ADR-0007 (Accepted 2026-10-11; on `main` since PR #14; **amended 2026-10-11 in PC009, eight
  amendments**): the agent acts as the requesting employee, can only read, and never writes. No
  raw credentials pass through the agent; the UI exchanges the employee's token for an
  attenuated, read-only delegated token and gives only that to the agent, so the agent never
  holds a token that could write. A proposal is a closed contract with one shared schema and
  contract tests (one operation to start); the UI executes confirmed writes; optimistic
  concurrency is a production gate; the audit event is structured logging and is audit
  information, not verified evidence; server-side approval records have two independent
  triggers. These are designs for the agent and security milestones, not implemented now.
- ADR-0008 (Proposed, PC009): security failures answer 401 or 403 as `ProblemDetail` through one
  path. The 500-for-denied-`@PreAuthorize` bug itself was already fixed on 2026-10-05.
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
- **Update and delete endpoints documented a body that the API never sends (PC010,
  merged as #18).** `openapi.yml` documented `200` with a body for the six `PUT`
  endpoints (owners, pets, pet types, specialties, vets, visits) and the six `DELETE` endpoints,
  while the controllers answer `204 No Content` (the container dropped the body they built) and
  the tests assert 204. **Decided 2026-10-12: option A, the spec says 204**, and the scope
  includes the deletes because the comparison found the same drift there. The spec responses are
  now `204` with no body, the twelve controller methods return `ResponseEntity<Void>`, and the
  seven update tests assert an empty body instead of a JSON content type. Done as its own PR
  before PC008, because PC008 generates types from this spec. Related: the optimistic-concurrency
  work later adds an `ETag` header to these responses.
- ✅ **POST specs for pet types, visits, specialties, vets and users said `200` but the
  controllers answer `201`** (found during PC010). Fixed in PC008 as a spec-only change: `201`,
  and the meaningless `ETag` and `304` blocks dropped from those creates.
- ✅ **Show the server's field messages in the forms** (PC013, owner form). Each later form needs
  its own small wording map on top of `serverFieldErrors`; raw rules (regular expressions) are
  replaced by "<Label> is not valid."
- **PostgreSQL and MySQL are not exercised by the tests.** The owner queries use portable JPQL, but
  only H2 and HSQLDB are verified. A Testcontainers-based test would close the gap. Also consider a
  functional index on `lower(last_name)` for PostgreSQL (`LOWER()` stops the plain index being used).
- **Refuse to delete a pet type that pets still use** (future backend fix, not scheduled; added
  2026-10-11). Today `DELETE /pettypes/{id}` deletes every pet of that type and all their visits
  (`PetTypeRepositoryImpl.delete` runs the JPQL deletes), and `shouldDeletePetType` asserts that
  by deleting type 1 (cat), which four seed pets use. Wanted: if any pet references the type,
  answer an error and delete nothing; an unused type still deletes. ⚠️ Assumptions to confirm
  when it is picked up: the status is 409 Conflict with a `ProblemDetail` that says how many pets
  use it (data-integrity errors answer 409 since PC012, so the status is consistent);
  `openapi.yml` gains a 409 response; `shouldDeletePetType` and `PetTypeRestControllerV1Tests`
  change to the new rule; there is no way to force the delete (once the guard exists, the Pet types screen
  tells the user to move or remove the pets first). **Interaction with UI decision C (2026-10-13):**
  the Pet types screen ships Delete first, with a confirmation that states how many pets and visits
  will go; the guard later replaces that confirmation with the refusal message. Do it after PC005, which removed the
  `Pet.type` cascade this behaviour sits next to and dropped the `em.remove` call in
  `PetTypeRepositoryImpl.delete`; the guard replaces that method's delete-everything logic.
- ✅ **TypeScript API types are generated from `openapi.yml`** (PC008, ADR-0003). Dev
  dependency `openapi-typescript` 7.13.0 (types only, pinned); `npm run api:generate` writes the
  committed `petclinic-ui/src/api/schema.d.ts`; `types.ts` keeps the friendly names; a CI step
  regenerates and fails on `git diff`. The fetch client, hooks and `ownerValidation.ts` stay
  hand-written. Wrinkles handled in `types.ts`: `Owner.id` is optional in the spec (aliased to
  required), `Visit` has a required `petId` and an optional `date`, and `ProblemDetail` stays
  `Partial`. A Dependabot bump of `openapi-typescript` will fail the freshness check until
  someone runs `npm run api:generate` and commits the result; that failure is expected.
- ✅ Dependabot **security updates** were enabled in the GitHub repo settings (2026-10-09).
