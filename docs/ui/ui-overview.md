# UI Overview

Status as of 2026-10-07. `petclinic-ui` has the app shell (header, navigation, assistant
panel preview), the Owners page and the Add owner page. Owner details is a placeholder.
This document describes the approach and conventions the Owners page establishes for every
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
  second screen needs them (straightforward over abstract, per `CLAUDE.md`).
- **Server state via TanStack Query**, local UI state via `useState`. No global store.
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
│ 🐕 PetClinic   Owners                                                  │  48px header
├───────────────────────────────────────────────────────────────────────┤
│  page content, max-width 1200px                                       │
│                                                          [💬 Assistant]│  floating launcher
└───────────────────────────────────────────────────────────────────────┘

   Panel open: the launcher disappears and the panel docks on the right
┌──────────────────────────────────────────────┬────────────────────────┐
│  page content                                │ Assistant            ✕ │
└──────────────────────────────────────────────┴────────────────────────┘
```

- **Header**: dog mark (`src/assets/dog.svg`, rendered by `components/DogMark.tsx`; to replace
  it, swap the file) and wordmark linking home, and the main navigation. Only built
  sections appear in the navigation. The current section is marked with `aria-current`.
  With one section there is no collapsed mobile menu; add one when a second section lands.
- **Skip link and focus**: a "Skip to content" link, and focus moves to the main content when
  the route changes.
- **Document title** is set per screen (`Owners – PetClinic`).

### Assistant panel

- **Launcher**: a floating "Assistant" button fixed at the bottom right of every page (rounded
  rectangle, chat icon, accent border). It is the only way to open the panel, and it is hidden
  while the panel is open. It carries the UI's one shadow, because a floating control needs
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

Restrained and information-dense: one accent color, hairline borders, no gradients, and no shadows except the floating assistant launcher's
or decoration.

| Area | Convention |
|---|---|
| Typography | System font stack. 14px body and table text; 13px labels and column headers (600); 12px helper text; 22px page title (600); 16px section headings. Line height 1.5. |
| Spacing | 4px scale: 4, 8, 12, 16, 24, 32, 48 (`--space-*`). |
| Page width | Content max 1200px, centered, 24px gutters (16px on phones). Forms max 640px. |
| Colors | Neutrals plus one muted teal accent `#0F766E`. Page background is a cool, slightly teal-tinted gray `#E8EDED` so white panels and the white header stand out; text, muted text and accent all keep at least 4.5:1 contrast on it. Danger `#B42318` for errors and destructive actions. All values are tokens in `tokens.css`; text meets 4.5:1 contrast. Light theme only for now. ⚠️ Accent and palette await approval after you see the page. |
| Buttons | 36px high (44px on touch), 6px radius, 14px/500. `primary` (filled accent, one per view), `secondary` (bordered), `ghost` (text), `danger` (only for confirmed destructive actions). Anything that navigates but looks like a button is a `<Link>` using `buttonClassName`. |
| Forms & inputs | Labels above fields, 36px inputs, 1px border, 2px accent focus ring. Helper text under the field. Required-ness stated once ("All fields are required") instead of asterisks. |
| Validation | Inline under the field in red with an icon and plain wording; shown after the field is left or after a submit attempt. A failed submit shows a summary (focused, `role="alert"`) whose items link to the fields. Server errors show in an alert above the form and keep the entered values. |
| Tables | Real `<table>` with a hidden caption and `th scope`. Tinted header row, hairline row dividers, 40px rows, hover tint, no zebra striping, no vertical rules. The name is a real link; the whole row is also clickable as a mouse convenience. |
| Panels | One surface style: white, 1px border, 8px radius, no shadow. Never nested. |
| Icons | About seven 16px inline-SVG icons in `components/Icon.tsx` (`currentColor`, 1.5px stroke). Used beside text; icon-only buttons carry an `aria-label`. |

### Standard list-screen states

Every screen handles these, as the Owners page demonstrates:

- **Loading** (first load): skeleton rows in the real table layout, static, announced as
  "Loading…". Later loads (next page, new search) keep the previous rows, dimmed, with
  `aria-busy`.
- **Error**: an alert above the table with the server's message and **Retry**; existing rows
  stay visible. 401/403 get their own wording and no Retry.
- **Empty**: "No … yet" with an Add action when there is no data at all; "No … found" with a
  way back to the full list when a search matched nothing; a way back to page 1 when the URL
  asks for a page past the end.

### Responsive tables

≥1024px full table; 640–1023px the Address column folds under the name; below 640px each row
is a compact cell (name, then address and city, then phone and pets). No horizontal scrolling.

## Reusable pieces

`AppShell`, `AssistantPanel`, `PageHeader`, `Button`/`buttonClassName`, `SearchField`,
`Pagination`, `EmptyState`, `ErrorAlert`, `FormField`, `Icon`, `DogMark`, plus the
`useDocumentTitle` hook. Table markup is owner-specific (`OwnersTable`); extract a shared
table only when a second table exists.

## Security and authentication

- The REST API has security **disabled by default** (`petclinic.security.enable=false`).
  When enabled it uses HTTP Basic with roles `OWNER_ADMIN`, `VET_ADMIN` and `ADMIN`
  (`ADMIN` outranks the other two, [ADR-0007](../adr/0007-admin-role-hierarchy.md)).
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
- **Owner details** is a placeholder page.
- **Performance note:** the paged owners endpoint loads each owner's pets with a separate
  query (eager fetch), about 21 queries per 20-row page. Acceptable at clinic scale; known
  and deliberately left alone for now.

## Testing

Vitest + Testing Library, with `fetch` mocked at the boundary. Each screen is tested for its
loading, error, empty and data states and its main interactions, plus unit tests for
formatting and validation helpers. Run `npm test`, `npm run lint` and `npm run build`.
