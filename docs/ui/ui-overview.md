# UI Overview

Status as of 2026-10-14. `petclinic-ui` has the app shell (header, navigation, assistant
panel preview) and the owner screens: Owners list, Add owner, Owner details (with Delete)
and Edit owner. This document describes the approach and conventions the Owners page establishes for every
later screen; keep it current as the UI takes shape.

Companion docs: [screen-inventory.md](screen-inventory.md) (what we build),
[navigation-flow.md](navigation-flow.md) (how users move), and
[screen-mapping.md](screen-mapping.md) (which endpoints each screen calls).

> **Assumptions to confirm** are marked with ⚠️ throughout these docs.

## Approach

- **Single-page app**, React 19 + TypeScript, built with Vite into static files for
  CloudFront/S3 ([ADR-0003](../adr/0003-use-react-and-typescript-for-the-frontend.md)).
- **Talks to two backends**, per `CLAUDE.md`: `spring-petclinic-rest` for business data
  and `petclinic-ai-agent` for AI interactions. It never talks to the database.
- **Feature-oriented folders**: `src/features/<area>/` holds each area's pages, hooks and
  components; `src/api/` holds the typed REST client; `src/components/` holds the shared
  building blocks; `src/lib/` holds small helpers. Shared pieces are extracted only once a
  second or third screen needs them (straightforward over abstract, per `CLAUDE.md`).
- **Server state via TanStack Query**, local UI state via `useState`. No global store. Query
  keys are `['owners', params]` and `['owner', id]`. The rules for layering, state, invalidation
  and types are under [Frontend conventions](#frontend-conventions).
- **Retries:** failed queries retry up to three times, but never on a 4xx (404, 403, 400 …),
  which a retry cannot fix, so an unknown owner reports "not found" at once instead of after
  about seven seconds. Network failures, 5xx, 408 and 429 are retried (`shouldRetry` in
  `api/client.ts`). Writes are not retried.
- **`204 No Content`** (the API's answer to PUT and DELETE) is handled in the API client, so
  those calls resolve to nothing; screens refetch rather than read a response.
- **Routing via React Router.** Every screen has its own URL. List state (search text, page,
  page size) lives in the query string so lists survive reload and back-navigation.
- **API types** are generated from `openapi.yml` into `src/api/schema.d.ts` (`openapi-typescript`,
  ADR-0003; run `npm run api:generate`, CI checks it is up to date). `src/api/types.ts` gives
  them the friendly names the screens import.
- **Dev setup**: Vite proxies `/petclinic` to `localhost:9966`, so no CORS in dev.
  Production CORS for the CloudFront origin must be configured on the backend.
- **Styling**: plain CSS. Design tokens in `src/styles/tokens.css`, a small reset in
  `base.css`, and CSS Modules per component. No CSS framework and no icon library.

## Application shell

```
┌───────────────────────────────────────────────────────────────────────┐
│ 🐕 PetClinic    Owners                                                 │  64px white header
│                 ▔▔▔▔▔▔                                                 │  (underline = current)
├───────────────────────────────────────────────────────────────────────┤
│ ░░ teal artwork ░░  ┌──────────────────────────────┐  ░░ teal art ░░   │
│                     │ one big rounded white card    │                   │
│                     │ holds the screen              │     [💬 Assistant]│  floating launcher
│                     └──────────────────────────────┘                   │
└───────────────────────────────────────────────────────────────────────┘

   Panel open: the launcher disappears and the panel docks on the right
┌──────────────────────────────────────────────┬────────────────────────┐
│  page content                                │ Assistant            ✕ │
└──────────────────────────────────────────────┴────────────────────────┘
```

- **Header**: 64px, white with a hairline bottom border. It holds the dog mark
  (`src/assets/dog.svg`, rendered by `components/DogMark.tsx`; to replace it, swap the file)
  with a 20px "PetClinic" wordmark linking home, then the main navigation. Navigation items
  are plain 18px text links; the current one is teal and bold with a 3px underline just
  beneath the label, and other items get a gray underline on hover. Only built sections
  appear. The current section is marked with `aria-current`. With one section there is no
  collapsed mobile menu; add one when a second section lands. The dog also serves as the
  favicon (`index.html`).
- **Page card**: every screen sits in one large rounded white card (24px radius, soft teal
  shadow) floating over the page artwork. The list screen uses the wide card (max 1200px).
  Detail and form pages (`/owners/...` other than the list) use a narrow card that hugs a
  640px column (704px including padding), so the content and card edges line up.
- **Backdrop artwork** (`components/Backdrop.tsx`): decorative, `aria-hidden`, fixed behind
  the content: rich-teal blobs at the left and right edges, two light-blue clouds, and two
  leaf clusters at the bottom of the page. It is hidden below 640px. There is no cartoon dog
  in the art; the dog mark in the header is the only dog.
- **Skip link and focus**: a "Skip to content" link, and focus moves to the main content when
  the route changes.
- **Document title** is set per screen (`Owners – PetClinic`).

### Assistant panel

- **Launcher**: a floating "Assistant" button fixed at the bottom right of every page (rounded
  rectangle, chat icon, accent border). It is the only way to open the panel, and it is hidden
  while the panel is open. It has its own small shadow, because a floating control needs
  separation from the content beneath it. The page keeps extra bottom padding so it never
  covers the end of the content.
- A **right-hand docked panel**, 380px wide, opened from the launcher. It is a stub:
  not connected to `petclinic-ai-agent`, with a disabled input. It exists so layout and
  behavior are settled before the agent is.
- **Responsive**: ≥1280px it docks and the page narrows; 1024–1279px it overlays the page;
  below 1024px it fills the screen under the header.
- **Behavior**: opens with focus on its heading; the ✕ at the top of the panel, or Esc, closes
  it, the launcher reappears at the bottom right, and focus returns to it. Only the open/closed preference is stored in
  `localStorage`; conversation content will contain personal data and is never persisted.
- **Context**: the panel shows the current screen's name. When wired up, only the route and
  IDs are shared as context, never owner data; the agent reads data through the REST API.
- **Non-modal** in every mode (`aside`, labelled "Assistant"), so it has no focus trap.
  ⚠️ Revisit if the full-screen mobile mode needs modal behavior.
- **Confirmation (architecture rule: AI-initiated writes require human confirmation)**:
  proposed writes will appear in the panel as a distinct "AI suggestion" block with a
  plain-language summary and explicit Confirm/Reject, plus "Review in form", which opens the
  real form prefilled so the change goes through the same validated path as a manual edit.
  A badge on the launcher will count unresolved suggestions made while the panel is closed.
  **The UI, not the agent, executes a confirmed write**, with the employee's own token and
  exactly the payload shown ([ADR-0007](../adr/0007-ai-agent-acts-on-behalf-of-the-employee.md)).
  A proposal is one operation from a closed contract; the UI validates it on receipt, fetches
  the owner fresh to show before and after, and re-checks it at confirm time. Confirmation
  is not the access control: permission is enforced by the backend (see Security).
  ⚠️ "Review in form" opens an ordinary edit, which ADR-0007 does not cover: it should not be
  sent as an AI-assisted write unless that is decided. Open.

## Visual conventions

Friendly but still information-dense: a teal palette over a soft teal page, rounded white
surfaces, hairline borders and no gradients. The only shadows are the page card's soft one,
the assistant launcher's and the delete dialog's.

| Area | Convention |
|---|---|
| Typography | System font stack. 14px body and table text; 13px field labels (600); 12px helper text and uppercase column headers; 22px page title; 16px section headings. Line height 1.5. Header wordmark 20px, nav 18px. |
| Spacing | 4px scale: 4, 8, 12, 16, 24, 32, 48 (`--space-*`). |
| Page width | Wide card max 1200px (list); narrow card 704px (detail and form pages); forms and the contact panel max 640px. 24px page gutters, 16px on phones (card padding 32px, 16px on phones). |
| Colors | Page background `#EAF6F3` (soft teal). Accent teal `#0C7A6E` (hover `#0A665C`, tint `#E2F4F0`) for buttons, links, focus and the current nav item; white text on it is 5.2:1. The brighter artwork teals (`#2FB5A1`, `#7AD6C7`, `#BFEBE3`) and cloud blue `#C9E4F6` are decorative only: never put text on them (white on `#2FB5A1` is only 2.6:1). Danger `#B42318` for errors and destructive actions. All values are tokens in `tokens.css`. Light theme only for now. |
| Radius | 10px controls and buttons, 14px inner panels and cards, 24px for the page card. |
| Page header | Optional small **eyebrow** pill above the title (the Owners list uses "Clinic administration"; detail and form pages omit it), an icon tile beside the title, an optional subtitle, and actions on the right. The actions wrap below the title on narrow screens. |
| Buttons | 40px high (44px on touch), 10px radius, 14px/500. `primary` (filled accent, one per view), `secondary` (bordered), `ghost` (text), `danger` (solid red, only for the confirm button of a destructive action). Anything that navigates but looks like a button is a `<Link>` using `buttonClassName`. |
| Icon actions | Square 40px bordered icon controls (`iconAction`), a link or a button, for compact row and page actions: View (eye) in table rows; Edit (pencil) and Delete (trash, red outline) on Owner details and, planned (decision G), on each pet card. Each has an `aria-label` and a `title` tooltip. |
| Forms & inputs | Labels above fields, 40px inputs, 1px border, 2px accent focus ring. Helper text under the field. Required-ness stated once ("All fields are required") instead of asterisks. One `TextField` component renders label, input, hint and error; extra props go straight to the `<input>`. |
| Validation | Inline under the field in red with an icon and plain wording. Errors appear after the first submit attempt and then update live as the user fixes each field (leaving a field does not trigger an error). A failed submit shows a summary (focused, `role="alert"`) whose items link to the fields. Server errors show in an alert above the form and keep the entered values. The client rules mirror the API's; the server stays authoritative. |
| Tables | Real `<table>` with a hidden caption and `th scope`, inside a bordered rounded card with the pagination footer. Tinted header row, hairline row dividers, 60px rows, hover tint, no zebra striping, no vertical rules. Each row has an initials avatar (decorative), the name as a real link, and a **View** icon link; the whole row is also clickable as a mouse convenience. Pets show as pills (first two, then "+N"). There is no delete in the list. The **Name** header is a sort button: it cycles A to Z, Z to A, then the default order, shows an arrow icon for the applied sort (up = A to Z, down = Z to A), has a tooltip that says what the next click does, carries `aria-sort` on the `th` and a visually hidden hint of what it does next, and works at every width. Only Name sorts. |
| Detail views | A bordered "Contact" panel (definition list; Address twice as wide as City and Telephone) and a "Pets" section of 240px cards (name and type tag side by side, birth date, visits). Planned (decision G): the Pets heading row carries a right-aligned **Add pet** button; each card has an Edit (pencil) and Delete (trash, red outline) `iconAction` cluster in its top-right corner, the name truncating before it, and each icon's `aria-label` names the pet ("Edit Rosy"). Add visit is a text button in the card's visits area. |
| Confirmation dialog | `ConfirmDialog` on the native `<dialog>`: modal, Esc closes it, Cancel is focused first, focus returns to the opener. Used for Delete owner; it names what will be removed, stays open and shows the error if the action fails. |
| Notices | A one-time confirmation (for example "Deleted owner …") is passed through navigation state and shown at the top of the next screen; it disappears as soon as the user searches or pages. |
| Icons | Fourteen 16px inline-SVG icons in `components/Icon.tsx` (`currentColor`, 1.5px stroke). Used beside text; icon-only controls carry an `aria-label`. |

### Standard list-screen states

Every screen handles these, as the Owners page demonstrates:

- **Loading** (first load): skeleton rows in the real table layout, static, announced as
  "Loading…". Later loads (next page, new search) keep the previous rows, dimmed, with
  `aria-busy`.
- **Error**: an alert above the table with the server's message and **Retry**; existing rows
  stay visible. If a refresh fails while rows are showing, the alert says "Could not refresh
  owners" and "Showing the last results that loaded", so it never claims nothing loaded.
  401/403 get their own wording and no Retry.
- **Empty**: "No … yet" with an Add action when there is no data at all; "No … found" with a
  way back to the full list when a search matched nothing; a way back to page 1 when the URL
  asks for a page past the end.

### Responsive tables

≥1024px full table; 640–1023px the Address column folds under the name; below 640px each row
is a compact cell (name, then address and city, then phone and pets). No horizontal scrolling.

## Frontend conventions

Defaults the owner screens establish for every later feature. They guide judgment; they are not
a checklist to satisfy at the cost of simplicity.

1. **Server data lives in TanStack Query.** Do not mirror it into `useState` to display or derive
   it. Editable drafts are the exception: a form may be seeded once from the cache
   (`EditOwnerForm` does this), edited as local state, and discarded on save or cancel. Reset a
   draft when the record changes by giving the form a `key` (for example `key={owner.id}`). Use
   `useEffect` only to sync with the outside world (title, focus, timers, the dialog), never to
   derive values.
2. **Layering is a default, not a quota.** The usual shape is plain functions in `src/api/`,
   hooks in one file per feature (`useOwners.ts`), then components. Do not add a layer or
   wrapper that only forwards a call. A feature with one read can define its hook beside the
   page and split later, when a second consumer appears. Query keys are `['owners', params]`
   and `['owner', id]`.
3. **List state belongs in the URL; form and dialog state stays local.** After a successful
   write, invalidate only the queries that write affected, never everything: adding an owner
   invalidates `['owners']`; updating one also invalidates `['owner', id]`; deleting one removes
   `['owner', id]` and invalidates `['owners']`. Do not invalidate after a failed write. (Adding
   a pet later should refresh that owner's details, the owner lists, which show pets, and the
   pet type counts, but not other owners.)
4. **Types.** Use `interface` for API shapes and string unions instead of enums. Use
   `Record<K, V>` for typed key-to-value mappings (field labels, max lengths). Use `Partial<T>`
   when some properties of an object type may be omitted (form errors as
   `Partial<Record<OwnerField, string>>`, test overrides as `Partial<Owner>`). `Partial` is not
   specific to maps.
5. **Confirmation is UX; the backend enforces permission.** Destructive actions, and
   AI-initiated writes, go through a confirmation dialog so the user consents knowingly. A
   dialog never replaces authorization: anything that can call the API can skip it, so
   permission for AI writes is enforced in the backend (see
   [Security and authentication](#security-and-authentication)).
6. **Delete dialogs say what else goes** (decided 2026-10-13). Each names the item and states how
   many associated objects are deleted with it (an owner's pets, a pet's visits, the vets that
   lose a specialty, the pets of a pet type). The table of counts and their sources is in
   [screen-inventory.md](screen-inventory.md#delete-confirmations-state-what-else-goes-decided-2026-10-13).
7. **Write errors show the server's reason.** `ApiError` already carries the server's `detail`, and
   the Delete owner dialog shows it. The rule for every new write screen: show that `detail`, and
   never turn a write's `404` into "not found" (that wording is for reads of a missing record).
   Data-constraint failures answer `409` since PC012 (see below), but the rule stays: the server's
   reason is what the user needs. Still to do ahead of the next write screens (decision F): apply
   the rule in the shared error handling and read the field messages in `schemaValidationErrors`
   (see Known gaps).
8. **Catalog screens** (specialties, pet types) are one list page each with an inline add, edit and
   delete (decided, decision B). Worth extracting once the second use appears: a generic catalog
   page that takes the list, add, update and delete hooks; the `ConfirmDialog` wrapped as a
   `DeleteDialog` taking a name, a consequence sentence and a mutation; and a sibling of
   `useOwnerFromRoute` that finds a pet or visit inside the loaded owner.

### State and invalidation for the planned screens

| Layer | Holds | Where |
|---|---|---|
| Server state | owners, pet types, vets, specialties | TanStack Query cache |
| URL state | which owner or pet, list search and page | React Router |
| Local UI state | what is being typed, "tried to submit" | `useState` in the form |

A mutation's `onSuccess` invalidates the affected keys: pet or visit writes `['owner', id]`,
`['owners']` and `['pet-types']` (the pet type counts, decision H); vet writes `['vets']`;
specialty writes `['specialties']` and `['vets']`; pet type writes `['pet-types']` plus
`['owners']` and `['owner', …]`. Writes are never retried. After a
successful write, forms return to Owner details, or to the list page for admin screens.
Types: everything is an alias over the generated `schema.d.ts`; forms keep their own string-valued
value types (inputs yield strings, the API wants objects).

Validation the new forms should mirror (the server stays authoritative):

| Field | Rule |
|---|---|
| Pet name | required, at most 30 characters |
| Pet birth date | required, not in the future, not older than 50 years |
| Visit description | required, 1 to 255 characters |
| Visit date | optional (the backend defaults it to today) |
| Vet first and last name | 1 to 30 letters, up to three words; the last name may end in "." |
| Specialty and pet type name | 1 to 80 characters |
| Username and password | 1 to 80 characters |

## Backend behaviour the UI must respect

Findings from reading the backend code on 2026-10-13, not assumptions. Each has a consequence for
a screen.

1. **Deleting a pet type deletes every pet of that type and their visits.**
   `PetTypeRepositoryImpl.delete` removes them with JPQL and has no guard. The planned guard (refuse
   with a 409 when pets use the type) is in [project-status.md](../project-status.md). Decision C
   ships Delete now with a count-based confirmation; the counts come from `PetTypeWithCounts` on
   `GET /pettypes` (decision H, planned backend change; see screen-inventory.md).
2. **Deleting a specialty silently removes it from every vet that has it.** The confirmation says
   how many vets, which the UI can compute from the cached vets list.
3. **`PUT /vets/{id}` matches specialties by name** and drops names it does not recognise, so the vet
   form sends specialties from the loaded catalog only.
4. **Data-constraint failures answer `409` Conflict** (PC012; they answered `404` before) through
   the shared `DataIntegrityViolationException` handler in `ExceptionControllerAdvice`, with the
   generic detail "The change could not be saved because it conflicts with existing data" (the SQL
   and constraint name are logged, never returned). `openapi.yml` documents the `409` on every
   write operation (POST, PUT and DELETE), because the handler is global. The UI must show
   the server's `detail` for write errors (convention 7); the handler does not tell a foreign-key
   failure from a unique violation, by design.
5. **Owner-scoped pet and visit writes do not verify ownership.** The UI always sends consistent
   ids; the check belongs on the backend backlog.
6. **Deleting a pet deletes its visits** (`CascadeType.ALL`), so the dialog states the visit count.
7. `GET /vets` requires `VET_ADMIN`, so an owner-only user gets `403` on the Vets list when security
   is on.

## Reusable pieces

`AppShell`, `Backdrop`, `AssistantPanel`, `PageHeader`, `BackLink`, `Button`/`buttonClassName`,
`SearchField`, `Pagination`, `EmptyState`, `ErrorAlert`, `TextField`, `ConfirmDialog`, `Icon`,
`DogMark`, plus the `useDocumentTitle` hook. Owner-specific: `OwnersTable`, `OwnerForm`
(shared by Add and Edit) and `OwnerRoute` (`useOwnerFromRoute`, `OwnerLoadStatus`,
`OwnerNotFound`, shared by Details and Edit). Extract a shared table only when a second table
exists.

## Security and authentication

- The REST API has security **disabled by default** (`petclinic.security.enable=false`).
  When enabled it uses HTTP Basic with roles `OWNER_ADMIN`, `VET_ADMIN` and `ADMIN`
  (`ADMIN` outranks the other two, [ADR-0006](../adr/0006-admin-role-hierarchy.md)).
  Owner/pet/visit endpoints require `OWNER_ADMIN`; vet/specialty writes require
  `VET_ADMIN`; pet types are readable by either.
- ⚠️ **No login flow is designed yet.** Screens send no credentials and assume security is
  off; `401`/`403` are shown as "You don't have access…". A login design (and how
  tokens/credentials are held in the browser) is a prerequisite for any non-local deployment
  and needs its own ADR ([ADR-0009](../adr/0009-login-and-authentication-mechanism.md), a
  placeholder). Never put credentials in `VITE_*` variables: they are inlined into
  the public bundle.
- Owner data is personal data. It is held in memory only (React Query cache), never written
  to browser storage or logged. All API text is rendered through React's default escaping.
- Hiding or disabling actions by role is a convenience only; the backend enforces access. Until
  login exists, navigation shows every section and the API's `403` produces the existing "no
  access" state. A `403` is the normal "no access" state, not a bug.
- **Add user** (deferred, decision D) sends a plaintext password and role names from the browser to
  `POST /users`. That is acceptable only over HTTPS and only for `ADMIN`, so it waits for the login
  work (ADR-0009). The form must never log or cache the password; as a mutation, not a query, it is
  not cached.
- **Reference-data deletes are the most destructive operations in the system** (backend behaviour 1
  above), so they need an explicit confirmation and, ideally, the backend guard.
- AI writes to any of these endpoints still need human confirmation; the same write functions and
  dialogs are reused.
- **AI-initiated writes (decided in [ADR-0007](../adr/0007-ai-agent-acts-on-behalf-of-the-employee.md),
  not built):** a confirmation dialog is consent, not authorization. The agent can read owner and
  visit text that someone typed, so it must be treated as capable of being steered by that text.
  So the agent never writes: it acts as the requesting employee through a delegated read-only
  token that the backend verifies and caps, and it returns proposals that the UI shows and
  executes with the employee's own token. No password reaches the agent. The write is recorded
  as an audit record (structured logging, not verified evidence), and optimistic concurrency
  (`ETag` / `If-Match`) is a production gate. Until the login and token work exists, there is no
  identity to delegate, so none of this can be wired up (see
  [project-status.md](../project-status.md)).

## Known gaps

- **Server validation messages are generic.** A `400` from the API shows "The request contains
  invalid or missing parameters" above the form, because the UI does not yet read the field-level
  messages in `schemaValidationErrors` (part of decision F). The form rules mirror the API's (including the 10-digit
  telephone), so this is rarely reached.
- **Add pet and Add visit** are not built yet, so Owner details shows pets and visits but
  cannot add them. Their placement is decision G, and the full list of remaining screens is in
  [screen-inventory.md](screen-inventory.md).
- **Performance note:** the paged owners endpoint loads each owner's pets with a separate
  query (eager fetch), about 21 queries per 20-row page. Acceptable at clinic scale; known
  and deliberately left alone for now.

## Testing

Vitest + Testing Library, with `fetch` mocked at the boundary. Each screen is tested for its
loading, error, empty and data states and its main interactions, plus unit tests for
formatting and validation helpers. Run `npm test`, `npm run lint` and `npm run build`. After a change to `openapi.yml`, run
`npm run api:generate` and commit `src/api/schema.d.ts`; the UI CI job regenerates it and fails
if it differs.
