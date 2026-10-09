# UI Overview

Status as of 2026-10-08. `petclinic-ui` has the app shell (header, navigation, assistant
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
- **Server state via TanStack Query**, local UI state via `useState`. No global store.
  Each feature has plain API functions in `src/api/`, one hooks file (`useOwners.ts`) wrapping
  them, then components. Query keys are `['owners', params]` and `['owner', id]`; every write
  invalidates what it changed (a deleted owner is removed from the cache instead). Server data
  is never copied into `useState`, and `useEffect` is used only to sync with the outside
  world (title, focus, timers, the dialog), not to derive values.
- **`204 No Content`** (the API's answer to PUT and DELETE) is handled in the API client, so
  those calls resolve to nothing; screens refetch rather than read a response.
- **Routing via React Router.** Every screen has its own URL. List state (search text, page,
  page size) lives in the query string so lists survive reload and back-navigation.
- **API types** are hand-written in `src/api/types.ts`. Generating them from `openapi.yml`
  is anticipated by ADR-0003 but not yet decided.
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
  A badge on the launcher will count unresolved suggestions made while the panel is closed. ⚠️ Whether the UI or the
  agent performs a confirmed write is undecided (default: the UI, after Confirm).

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
| Icon actions | Square 40px bordered icon controls (`iconAction`), a link or a button, for compact row and page actions: View (eye) in table rows; Edit (pencil) and Delete (trash, red outline) on Owner details. Each has an `aria-label` and a `title` tooltip. |
| Forms & inputs | Labels above fields, 40px inputs, 1px border, 2px accent focus ring. Helper text under the field. Required-ness stated once ("All fields are required") instead of asterisks. One `TextField` component renders label, input, hint and error; extra props go straight to the `<input>`. |
| Validation | Inline under the field in red with an icon and plain wording. Errors appear after the first submit attempt and then update live as the user fixes each field (leaving a field does not trigger an error). A failed submit shows a summary (focused, `role="alert"`) whose items link to the fields. Server errors show in an alert above the form and keep the entered values. The client rules mirror the API's; the server stays authoritative. |
| Tables | Real `<table>` with a hidden caption and `th scope`, inside a bordered rounded card with the pagination footer. Tinted header row, hairline row dividers, 60px rows, hover tint, no zebra striping, no vertical rules. Each row has an initials avatar (decorative), the name as a real link, and a **View** icon link; the whole row is also clickable as a mouse convenience. Pets show as pills (first two, then "+N"). There is no delete in the list. |
| Detail views | A bordered "Contact" panel (definition list; Address twice as wide as City and Telephone) and a "Pets" section of 240px cards (name and type tag side by side, birth date, visits). |
| Confirmation dialog | `ConfirmDialog` on the native `<dialog>`: modal, Esc closes it, Cancel is focused first, focus returns to the opener. Used for Delete owner; it names what will be removed, stays open and shows the error if the action fails. |
| Notices | A one-time confirmation (for example "Deleted owner …") is passed through navigation state and shown at the top of the next screen; it disappears as soon as the user searches or pages. |
| Icons | About ten 16px inline-SVG icons in `components/Icon.tsx` (`currentColor`, 1.5px stroke). Used beside text; icon-only controls carry an `aria-label`. |

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
  and needs its own ADR. Never put credentials in `VITE_*` variables: they are inlined into
  the public bundle.
- Owner data is personal data. It is held in memory only (React Query cache), never written
  to browser storage or logged. All API text is rendered through React's default escaping.
- Hiding or disabling actions by role is a convenience only; the backend enforces access.

## Known gaps

- **No sorting.** The API orders owners by id only. Last-name sorting and case-insensitive
  search are an approved backend change scheduled for a separate branch after this one
  merges. Until then the Name column is not sortable and search may be case-sensitive,
  depending on the database (H2 and PostgreSQL are; MySQL and HSQLDB are not).
- **Add pet and Add visit** are not built yet, so Owner details shows pets and visits but
  cannot add them.
- ⚠️ **Deleting an owner who has pets fails** because of a backend bug: `Pet.type` is mapped with
  `CascadeType.ALL`, so the delete tries to remove a shared pet type and the database rejects it
  (the API answers 404 "data constraint violation"; nothing is lost). The UI shows that message in
  the dialog. Owners without pets delete fine. Fix is in the backend, on its own branch; see
  [screen-mapping.md](screen-mapping.md).
- **Performance note:** the paged owners endpoint loads each owner's pets with a separate
  query (eager fetch), about 21 queries per 20-row page. Acceptable at clinic scale; known
  and deliberately left alone for now.

## Testing

Vitest + Testing Library, with `fetch` mocked at the boundary. Each screen is tested for its
loading, error, empty and data states and its main interactions, plus unit tests for
formatting and validation helpers. Run `npm test`, `npm run lint` and `npm run build`.
