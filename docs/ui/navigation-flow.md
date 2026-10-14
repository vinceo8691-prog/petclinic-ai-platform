# Navigation Flow

Status as of 2026-10-14. How users move between the screens in
[screen-inventory.md](screen-inventory.md). Screens marked ⚠️ are proposed, not decided.

## Primary flow (decided)

```
Owner search ──▶ Owner details ──▶ Add pet ──▶ Owner details
  /owners         /owners/:id      /owners/:id/pets/new   (new pet visible)
```

1. The app opens at `/owners`; any unknown path redirects there.
2. Selecting an owner (the name link, or anywhere on the row) opens **Owner details**.
3. **Add pet** (the button in the Pets heading row) on Owner details opens the pet form.
4. Saving returns to **Owner details** with the new pet listed. Cancel returns there too
   without saving.

## Add owner (built)

```
Owner search ──[Add owner]──▶ Add owner ──(save)──▶ Owner details (new owner)
                                  └──(Cancel / "← Owners")──▶ Owner search
```

"Add owner" is the primary button in the Owners page header (and appears again in the "No
owners yet" empty state). Saving opens the new owner's details page; Cancel and the back
link return to the list.

## Edit and delete owner (built)

```
Owner details ──[Edit owner]──▶ Edit owner ──(save / Cancel / back link)──▶ Owner details
      └──[Delete owner]──▶ confirmation dialog ──(confirm)──▶ Owner search + "Deleted owner …" message
                                  └──(Cancel / Esc)──▶ stays on Owner details, focus back on Delete owner
```

Edit owner prefills the Add owner form; saving returns to Owner details showing the new values.
Edit pet works the same way with the Add pet form (planned).

## Where the pet and visit actions sit (planned, decision G)

```
Owner details
 ├─ Pets heading row ........ [Add pet]
 └─ each pet card
     ├─ top-right icons ..... [✎ Edit pet] [🗑 Delete pet]
     └─ visits area ......... [Add visit]
```

Actions are buttons on the page, never a click-through on the card. Edit pet, Add pet and Add
visit navigate to their forms; Delete pet opens the dialog, and Cancel returns focus to the trash
icon that opened it.
The deletion message comes from the navigation state, so it shows once and clears as soon as
the user searches or pages.

## Full map

```
                       ┌──────────────▶ Add owner ──(save)──▶ Owner details
                       │                  /owners/new
 Owner search ─────────┤
   /owners             └──(select)──▶ Owner details ─┬──▶ Edit owner ──(save/cancel)──▶ Owner details
                                       /owners/:id   ├──▶ Add pet ──────(save/cancel)────▶ Owner details
                                                     ├──▶ Edit pet ⚠️ ──(save/cancel)────▶ Owner details
                                                     ├──▶ Add visit ⚠️ ─(save/cancel)────▶ Owner details
                                                     └──▶ Delete owner…  ──(confirm)──▶ Owner search

 Edit visit, Delete pet and Delete visit are omitted here; see "Planned screens" below.
 Vets list ⚠️ (/vets) — reached from top navigation; Add vet, Edit vet and Delete vet (see below).
```

## Planned screens (from the 2026-10-13 analysis; decisions A–H)

```
Owner details ─┬──[Add pet]──▶ Add pet ──(save/cancel)──▶ Owner details
               ├──[Edit pet]──▶ Edit pet ──(save/cancel)──▶ Owner details
               ├──[Delete pet]──▶ dialog "…and N visits" ──(confirm)──▶ Owner details
               ├──[Add visit]──▶ Add visit ──(save/cancel)──▶ Owner details
               ├──[Edit visit]──▶ Edit visit ──(save/cancel)──▶ Owner details
               └──[Delete visit]──▶ dialog ──(confirm)──▶ Owner details

Vets list ─┬──[Add vet]──▶ Add vet ──(save/cancel)──▶ Vets list
 /vets     ├──[Edit vet]──▶ Edit vet ──(save/cancel)──▶ Vets list
           └──[Delete vet]──▶ dialog ──(confirm)──▶ Vets list

Specialties (/specialties) and Pet types (/pet-types): one page each. Add, edit and delete all
happen on the page (decision B); a delete dialog states what else goes. No outgoing links.

Add user (/admin/users/new): deferred until login exists.
```

Edit pet and Edit visit find their record inside the owner already loaded (decision A), so a
direct visit to their URL loads the owner first; an unknown pet or visit shows a "not found"
message with a link back to the owner. Top navigation gains *Vets*, *Specialties* and *Pet types*
as those screens are built. Until login exists they are all shown, and the API's `403` produces
the existing "You don't have access" state.

## Rules

- **Owner details is the hub.** Every owner, pet and visit create/edit screen returns to it on save
  or cancel, so the user always lands where they can see the result. The exception is deleting an
  owner, which returns to the Owner search with a confirmation message. Vet screens return to the
  Vets list, and the catalog pages stay on their own page.
- **Top navigation**: a link per built section (currently *Owners*), always visible, marked
  with `aria-current`. Child routes such as `/owners/new` keep their section marked.
  *Vets* is added when that screen exists.
- **Back navigation**: forms have a "← Owners" link and a Cancel button; Owner details links
  back to the search.
- **Search state lives in the URL query string** (`/owners?lastName=Davis&sort=lastName&direction=desc&page=1&size=20`):
  search text, sort, page and page size. Typing updates the URL after a short pause and resets to
  page 1; search typing replaces the history entry rather than adding one, while paging and
  sorting add one (sorting also resets to page 1). Returning from Owner details with the back button restores the list as it was.
- **Unknown owner/pet/visit IDs** (API `404`) show a "not found" message with a link back (built
  for owners, to the owner search; pets and visits follow with their screens, linking back to the
  owner).
- **Unsaved changes**: leaving a form with edits shows no prompt in the first version. ⚠️
- **Focus on navigation**: after moving to a new screen, focus moves to the main content and
  the document title updates, so keyboard and screen reader users start in the right place.

## Assistant panel

The panel is available on every screen from a floating launcher button at the bottom right
(hidden while the panel is open; closing the panel with its ✕ brings it back) and does not
change the route.
It keeps its open/closed state while navigating and shows the current screen as its context.
How a proposed change is handed to the user for Confirm/Reject (or opened prefilled in the
real form) is described in [ui-overview.md](ui-overview.md); the flow is not built yet.
