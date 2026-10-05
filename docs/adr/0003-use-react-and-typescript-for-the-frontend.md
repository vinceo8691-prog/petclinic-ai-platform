# 3. Use React and TypeScript for the frontend

Date: 2026-10-04

## Status

Accepted

## Context

`petclinic-ui` has to do more than render CRUD forms against one backend. Per
`CLAUDE.md`, it talks to two independent services — `spring-petclinic-rest` for
business data and `petclinic-ai-agent` for AI-driven interactions — and it owns
the one place in the system where a human reviews and confirms an AI-initiated
write before it takes effect. That means the UI needs real client-side state
(a pending AI suggestion, a confirm/reject action, in-progress chat/agent
output) rather than just posting a form and reloading a page. The target
infrastructure in `CLAUDE.md` also calls for the frontend to be served as
static assets from CloudFront/S3, independent of either backend's deploy.

The realistic options for this were:

1. **React + TypeScript** (the choice made, already reflected in the
   `petclinic-ui` scaffold — Vite + React 19 + TypeScript)
2. **Angular**
3. **Vue**
4. **Server-side rendering from Spring, via Thymeleaf** (the approach the
   original Spring PetClinic project itself uses for its MVC frontend)

## Decision

Build `petclinic-ui` as a React + TypeScript single-page application.

### Why React/TypeScript over the alternatives

- **Component-based UI.** The interface is naturally a tree of composable
  pieces — an owner record, a pet list, a visit form, and critically an
  AI-suggestion/confirmation widget that needs to be reusable anywhere the
  agent proposes a write. React's component model maps directly onto that
  without requiring a larger framework's module/DI system to organize it.
- **Strong community and hiring pool.** React has the largest ecosystem and
  talent pool of the options considered, which matters for a platform
  expected to grow past a single developer — libraries, examples, and future
  hires are easiest to find here.
- **TypeScript against a generated contract.** `spring-petclinic-rest` already
  generates its OpenAPI spec and DTOs from `openapi.yml`. A TypeScript client
  can be generated from the same spec, so a drift between the API contract and
  the frontend becomes a compile error instead of a runtime bug — this
  benefit is independent of React specifically, but it's why TypeScript (not
  plain JS) was non-negotiable regardless of which component framework was
  picked.
- **Decoupled, static deployment.** A React SPA built with Vite compiles to
  static files that can be served from S3/CloudFront exactly as `CLAUDE.md`
  specifies, with zero coupling to either backend's runtime. This is the
  decision point that rules out Thymeleaf outright (see below), not just a
  minor preference.
- **Fits a rich, stateful interaction model.** Reviewing and confirming
  AI-initiated actions, and potentially streaming agent output, requires
  granular client-side state and incremental UI updates. That's squarely
  React's (and any SPA framework's) strength, and squarely the weak point of
  server-rendered, full-page-reload architectures.

### Why not the alternatives

- **Angular**: also component-based, also TypeScript-first, and a reasonable
  choice — but it's a full, opinionated framework (routing, forms, DI, RxJS
  all built in) with a steeper learning curve and more structural ceremony
  than this platform currently needs. For a small team iterating quickly,
  React's smaller core and freedom to add only the libraries actually needed
  (routing, data fetching, state) was judged a better fit than adopting
  Angular's larger built-in architecture up front.
- **Vue**: a legitimate, simpler alternative with solid TypeScript support in
  Vue 3, but a smaller ecosystem and hiring pool than React, with no
  compensating advantage specific to this platform's requirements (AI
  confirmation workflows, two independent backends) that Vue would handle
  better than React.
- **Thymeleaf (server-side rendered Spring MVC)**: this is the approach
  upstream Spring PetClinic itself uses, so it was the most natural "do
  nothing different" option. It was rejected because it structurally
  conflicts with this platform's architecture: Thymeleaf templates render
  inside the same Spring Boot process as the backend that serves them, which
  means the UI couldn't be an independently deployed static artifact on
  CloudFront/S3, couldn't cleanly call a *second* independent backend
  (`petclinic-ai-agent`) without that backend's logic also living in — or
  being proxied through — the rendering server, and would need significant
  extra tooling (htmx, heavy JS, WebSocket glue) to support the confirm/reject
  AI-action UI that a SPA gets for free. It remains the simplest option for
  pure server-rendered CRUD, which this platform is not.

## Consequences

**Positive:**

- UI pieces — forms, lists, and the AI-confirmation workflow — are reusable,
  composable components rather than server-rendered templates duplicated per
  page.
- Compile-time safety between the frontend and `spring-petclinic-rest`'s API
  contract, via TypeScript types generated from the same OpenAPI spec the
  backend already produces.
- `petclinic-ui` can be built once and deployed as static files to CloudFront/S3,
  fully decoupled from the release cycle of `spring-petclinic-rest` and
  `petclinic-ai-agent` — consistent with [ADR-0002](0002-separate-ai-agent-from-ui-and-rest-api.md)'s
  reasoning for keeping the three applications independently deployable.
- Large ecosystem of mature libraries for the specific problems this UI has
  (data fetching/caching against two backends, client-side routing, forms)
  without waiting on a single framework's built-in modules to catch up.
- Largest available hiring pool and reference material of the options
  considered, which matters as the team grows beyond one developer.

**Negative / trade-offs accepted:**

- React is a view library, not a framework — routing, state management, data
  fetching, and form handling all require separate library choices (and
  ongoing maintenance of those choices) that Angular would have provided out
  of the box.
- Introduces a second build toolchain (npm/Vite) alongside the existing Maven
  toolchain for the two Spring Boot applications, rather than staying inside
  one build system as Thymeleaf would have allowed.
- A SPA requires explicit client-side handling of auth tokens, API error
  states, and loading states that server-rendered pages get more cheaply by
  default.
- The fast-moving React/npm ecosystem means dependency upgrades and
  occasional churn (library deprecations, breaking major versions) are an
  ongoing cost, more so than Angular's more tightly versioned, batteries-
  included releases.

## Revisit if

- The team grows large enough that Angular's enforced structure and DI
  conventions would reduce inconsistency across many contributors more than
  React's flexibility helps velocity.
- The UI's needs shrink back down to simple CRUD forms with no AI-confirmation
  workflow and no need for independent static deployment — at that point
  Thymeleaf's simplicity would no longer be outweighed by its constraints.
