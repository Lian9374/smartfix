# SmartFix UI Guide

How the web pages are built, and how to add one without inventing a second style.
Everything here is Thymeleaf, CSS and a small amount of plain JavaScript — no frontend
build step, no framework, no external font or icon service. Community forms, lists and moderation actions work without JavaScript. Progressive
enhancements include mobile navigation, password reveal, form counters/error focus and
photo previews. The online Campus Map uses Leaflet JavaScript; its directory and data
explanation remain available as the fallback.

Read this before adding a page. The reference implementation for almost everything
below is `src/main/resources/templates/request/mine.html`.

---

## 1. Files

| File | What it owns |
| --- | --- |
| `templates/fragments/layout.html` | `head`, `mark`, `brand`, `userArea`, `sidebar`, `appbar`, `footer`, `scripts` — the shell every signed-in page uses |
| `templates/fragments/components.html` | `pageHeading`, `badge`, `statusBadge`, `urgencyBadge`, `roleBadge`, `accountStatusBadge`, `alert`, `emptyState`, `requestEmptyState`, `pageNote`, `timeline` |
| `templates/fragments/illustrations.html` | `campusAside`, `requestEmpty` — the two drawings, reused by the sign-in page, the overview and the requester empty state |
| `static/css/site.css` | Design tokens and every component class. Six commented sections: tokens, base, app shell, components, sign-in page, responsive |
| `static/images/favicon.svg`, `campus-maintenance.png` | Brand icon and decorative campus artwork |
| `static/css/visual-refresh.css` | Current card layouts, restrained teal/mint/cream palette, community feed and online map |
| `static/js/community-form.js` | Community character counters and first-invalid-field focus |
| `common/web/NavigationAdvice.java` | Adds `${navRole}` and `${navUsername}` to every model |
| `error.html` | The one page for every error status |

**There is no `topbar` fragment.** The horizontal bar is `appbar`, and the vertical
administrative rail is `sidebar`; §3 explains when each one is rendered. Any code or
document that still says `topbar` predates the layout work and will fail at render time.

---

## 2. Skeleton for a new signed-in page

Copy this and change the marked values. The structure is not decorative: the
navigation, the account block with its sign-out form, and the footer all come from
fragments, so a page never repeats them.

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('Page title')}"><!-- (1) -->
  <title>Page title · SmartFix</title>
</head>
<body>
<div class="app-shell">
  <th:block th:replace="~{fragments/layout :: sidebar('overview')}"></th:block><!-- (2) -->

  <div class="app-main">
    <th:block th:replace="~{fragments/layout :: appbar('overview')}"></th:block><!-- (2) -->

    <main id="main-content" class="content">
      <div class="page-head-row">
        <div class="page-head">
          <h1 class="page-head__title">Page title</h1>
          <p class="page-head__subtitle">One line saying what this page is for.</p>
        </div>
        <div class="page-head-row__actions">
          <a class="btn btn--primary" th:href="@{/somewhere}">Primary action</a>
        </div>
      </div>

      <section class="card" aria-labelledby="example-title">
        <div class="card__header">
          <h2 class="card__title" id="example-title">Card title</h2>
        </div>
        <div class="card__body">...</div>
      </section>
    </main>

    <footer th:replace="~{fragments/layout :: footer}"></footer>
  </div>
</div>

<th:block th:replace="~{fragments/layout :: scripts}"></th:block>
</body>
</html>
```

1. The string is the page title, used in `<title>` and in the browser tab.
2. The key of the **current** page, so the matching navigation link gets
   `aria-current="page"`. Both fragments are declared on a `<th:block>` root, so a
   page pulls each one in with `th:replace` on its own `<th:block>`. Pass the
   **same key to both**: a page does not know which layout its role wears, and the
   fragments decide that themselves (§3).

Existing keys: `overview`, `my-requests`, `user-management`, `request-lookup`,
`request-queue`. A page whose section depends on the role passes an expression
rather than a literal — `request/detail.html` does exactly that:

```html
<th:block th:replace="~{fragments/layout :: sidebar(
    ${navRole == 'ADMINISTRATOR'} ? 'request-lookup' : 'my-requests')}"></th:block>
```

### The page heading

Two equivalent shapes, and the guide's skeleton uses the one that fits:

- `~{fragments/components :: pageHeading(${title}, ${subtitle})}` renders
  `.page-head` with its `h1` and an optional subtitle paragraph. Pass `null` for
  `subtitle` and the paragraph is dropped entirely.
- The skeleton above writes the same markup inline. That is what
  `request/mine.html` does, because it needs the `.page-head-row` wrapper to put a
  primary action and a count beside the title.

Use one or the other, never both: two `h1`s on one page restates the same thing.

---

## 3. Navigation and roles

Navigation is built once, in `layout.html`, in two shapes, and every link in them
points at a route that exists:

| Fragment | Rendered for | Links |
| --- | --- | --- |
| `sidebar('…')` (rail) | `ADMINISTRATOR` only | Overview (`/`), Community (`/community`), Reported content (`/admin/community/reports`), Request Queue (`/admin/requests`), User Management (`/admin/users`), Request Lookup (`/admin/requests/lookup`), Campus Map (`/campus-map`) |
| `appbar('…')` (bar) | every role | `REQUESTER`: Home (`/`), Community (`/community`), My Requests (`/requests/mine`), Campus Map (`/campus-map`) · `TECHNICIAN`: Home (`/`), Community (`/community`), My Work Orders (`/workorders/mine`), Campus Map (`/campus-map`) · `ADMINISTRATOR`: brand and a Menu button only |

The administrator's bar carries no links and no account block on purpose: their
destinations are already in the rail, and a second copy would be the same links
twice in one page. Above the breakpoint that bar is `display:none`, because the
rail is already visible. Every other role has no rail at all and gets the full
content width.

`${navRole}` and `${navUsername}` come from `NavigationAdvice`, which reads the
authenticated principal. **`navRole` decides what is *shown*, never what is
*allowed*** — authorisation is `SecurityConfig` plus the service-layer ownership
checks, and those are the only things that protect a route.

### Adding a nav entry

1. Add the controller and the template (with the skeleton above).
2. Authorise the route in `SecurityConfig` — do this first; the link must never
   exist before the route does.
3. Add the link to the bar in `layout.html`, guarded by the role that may use it,
   with its own `active` key:

```html
<a class="appbar__link" th:href="@{/workorders/mine}" th:if="${navRole == 'TECHNICIAN'}"
   th:attr="aria-current=${active == 'work-orders'} ? 'page' : null">My Work Orders</a>
```

A rail entry is the same idea with the rail's own classes and an inline SVG icon
(`.nav__link` instead of `.appbar__link`, inside `<nav class="nav">`); the rail is
the place for management icons; the bar keeps compact primary destinations. Notifications have a separate labelled icon.

Three rules, all of which existing pages depend on:

- Icons are inline SVG with `aria-hidden="true" focusable="false"` and a sibling
  `<span>` carrying the visible text. There is no icon font.
- `aria-current` is written with `th:attr` and `null` for "not current", because
  `null` removes the attribute; `""` would not.
- The sign-out control stays the POST form inside the `userArea` fragment, which
  both the rail and the bar include. It is CSRF-protected and Spring Security owns
  the endpoint. Never replace it with a GET link.

---

## 4. Colours, spacing and shape

Use the tokens in `:root` (`site.css` §1). A literal hex value outside that block is a
bug: it drifts from the palette the moment anyone adjusts it.

| Purpose | Token |
| --- | --- |
| Brand | `--brand` `#246B63`, `--brand-dark` `#164D47`, `--brand-hover` `#1D5A53`, `--brand-tint` `#E7F2ED`, `--brand-tint-line` `#CFE3DA` |
| Surfaces | `--canvas` `#F6F8F7` (the page), `--canvas-warm` `#FAF9F6` (the sign-in panel), `--surface` `#FFFFFF` (cards, bars) |
| Text | `--text` `#182B29`, `--muted` `#5C6D6A`, `--muted-strong` `#46585A` (for text on a tinted pill) |
| Lines | `--border` `#E1E8E5`, `--border-strong` `#CBD8D4` |
| Accent | `--accent` `#E0A458`, `--accent-ink` `#8A5A1B` — illustration detail only, never UI |
| Status tones | `--tone-neutral` / `--tone-info` / `--tone-success` / `--tone-warning` / `--tone-danger`, each as a **triple**: `-bg`, `-ink`, `-bd` |
| Type scale | `--text-xs` 12px, `--text-sm` 13px, `--text-base` 15px, `--text-md` 16px, `--text-lg` 20px, `--text-xl` 24px, `--text-2xl` 32px |
| Families and leading | `--font-sans` (system stack), `--font-mono`, `--leading-tight`, `--leading-body` |
| Spacing | `--space-1..12` = 4, 8, 12, 16, 20, 24, 32, 40, 48px (no `--space-7` / `--space-9` / `--space-11`) |
| Shape | `--radius-sm` 6px, `--radius-md` 10px, `--radius-lg` 14px, `--radius-pill` 999px; `--shadow-sm`, `--shadow-md` |
| Layout | `--content-max` 1160px, `--content-pad` 24px, `--rail-width` 248px (the administrative rail), `--bar-height` 70px |
| Controls | `--field-height` 46px (every text input), `--focus-ring` 3px |
| Motion | `--transition` 140ms ease |

### Desktop measure, and why the bar lines up with the title

`--content-max` is the readable measure; `--content-pad` is the gutter *outside* it.
Keeping them as two tokens is what lets the account bar, the page title and the footer
share one left edge at every width. `.content` is capped at
`calc(--content-max + 2 * --content-pad)` and centred with `margin-inline: auto` inside
the area beside the rail; `.appbar__inner` and `.site-footer` then use

```css
padding-inline: max(var(--content-pad), calc((100% - var(--content-max)) / 2));
```

which resolves to the same left edge in all three regimes: wide enough that the measure
is capped (both centre on the same box), between the two (the gutter wins for both), and
narrow (both sit at the gutter). Verifying a change here means measuring the rendered
`left` of `.appbar__brand`, the page `h1` and the footer text — they must be equal.

Every status tone is used as a **triple**: `-ink` for text, `-bg` for the fill and `-bd`
for the border. The three were measured together (WCAG AA, 4.5:1) and an `-ink` is never
used on anything but its own `-bg`; the muted-text token is never used on a tint.

Type: system sans stack, body 15px (`--text-base`), form inputs 16px (`--text-md`, so
mobile Safari does not zoom on focus), page titles 24px (`.page-head__title` at
`--text-xl`). No external font is loaded.

The design is deliberately bright and low-contrast in *decoration* while staying high
contrast in *content*: no full-page dark backgrounds, no gradients, no glass, no heavy
shadows, no decorative animation. Hierarchy comes from borders and spacing.

---

## 5. Components

All of these are in `site.css` §4 and, where they render markup, in
`fragments/components.html`.

| Component | Markup | Notes |
| --- | --- | --- |
| Page heading | `~{fragments/components :: pageHeading(${title}, ${subtitle})}` or `.page-head` > `__title` + `__subtitle` | One `h1` per page, plus a one-line `__subtitle`; `subtitle` may be `null` |
| Page heading row | `.page-head-row` > `.page-head` + `__actions` + `__meta` | Wraps the heading when something belongs beside it. `__actions` holds buttons; `__meta` holds a count like `3 shown`. Stacks at ≤640px |
| Breadcrumb | `.crumbs` > `__link` + `__sep` + `__here` | Only pages with a real parent carry one — `request/detail.html` |
| Card | `.card` > `.card__header` (`__title`, `__hint`) + `.card__body` + `.card__footer` | Give the card `aria-labelledby` pointing at its title id |
| Flush card body | `.card__body--flush` | For a table that should reach the card edges |
| Button | `.btn` + `.btn--primary` \| `--secondary` \| `--danger`, optional `--small` | One primary action per screen. `--danger` is for the destructive choice only; `[disabled]` and `aria-disabled="true"` are styled |
| Text button | `.link-button` | Sign out, and actions that are genuinely link-like |
| Badge | `~{fragments/components :: badge(${text}, ${tone})}` | Tones: `brand`, `success`, `warning`, `danger`, `info`, `neutral` |
| Status badge | `statusBadge(${status})`, `urgencyBadge(${urgency})`, `roleBadge(${role})`, `accountStatusBadge(${status})` | **The single place** where a backend enum becomes a colour |
| Form field | `.form-field` > `__label` + control + `__hint` + `__error` | See §6 |
| Form grid / section | `.form-grid`, `.form-section` (a `fieldset` with a `legend`) | Groups a long form without a modal |
| Data table | `.table-scroll` > `table.data-table` | See §7 |
| Detail list | `.detail-list` (a `dl`) | The "facts" column of a detail page |
| Detail layout | `.detail-layout` > `__main` + `__aside` | See §8 |
| Empty state | `~{fragments/components :: emptyState(${title}, ${message})}` | Say what is missing and what would fill it. Carries one low-contrast linear icon (`__icon`), title and message; `message` may be `null` |
| Page note | `~{fragments/components :: pageNote(${text})}` | One quiet line at page level for a standing fact rather than an event — e.g. when a list can only ever be empty until another module is connected. Use it **once per page**: it belongs above the card, not repeated inside an empty state |
| Card link | `.card__link` | The single "View all …" style action in a card header, opposite the title |
| Action list | `.action-list` > `.action-list__link` > `__label` + `__desc` | A short list of real destinations. Each entry must lead somewhere that exists |
| Timeline | `~{fragments/components :: timeline(${entries})}` | Status history; states each transition in words |
| Alert | `~{fragments/components :: alert(${tone}, ${title}, ${message})}` | Page-level feedback; `error` is announced as `role="alert"`, the rest as `role="status"` |
| Notice | `.notice` | Something the reader can read but not act on — a dependency that is not connected, or a feature this release does not carry |
| Auth shell | `.auth` > `.auth__intro` + `.auth__panel` | The sign-in and sign-up pages, and nothing else. Two columns at `54fr / 46fr`; at ≤900px it collapses to one column, `.auth__intro` loses its art and `.auth__panel` moves onto `--canvas`. The form is set directly on the panel — deliberately not a `.card`, since a card there would draw a second vertical edge beside the one the split already draws |
| Account type | `.account-type` (a `fieldset`) > `__legend` + `__track` > `__option` > `__input` + `__face` | The three sign-in entry points, as one bar of three equal segments. Native radios in a `fieldset` with a `legend`, so arrow keys, the group announcement and the checked state are the browser's job. The input is transparent and 1px but **stays in the layout** — `display:none` or `hidden` would take away tabbing, the arrow keys and the whole group. The visible segment is the input's next sibling, and `:checked + .account-type__face` is the only thing that draws the selected state: there is no active class, and nothing else in the markup differs between the three segments, so the eye cannot disagree with the radio. The label wraps the input, so the whole segment is the click target |
| Prose | `.prose` | User-entered text; preserves line breaks |
| Report entry | `.report-entry` (a `details`) > `__summary` + `__form` > `__fields` | The report form on a question thread, closed until opened so a thread being read stays a thread. A `details`/`summary` pair and never a scripted toggle: the browser already gives `summary` the role, the keyboard behaviour and the expanded state, so this carries no `aria-expanded` of its own and keeps the marker it draws. `__fields` is one column, two at ≥720px. Offered only where the service would accept the report — public content, and not the reader's own |
| Report quote | `.report-quote` | The text a moderation decision is about, on the queue: a left rule in `--accent` over the content printed with `th:text`. It says "this is somebody's writing, reproduced", and reproduces nothing as markup |
| Utilities | `.stack`, `.stack--tight`, `.cluster`, `.meta`, `.text-muted`, `.visually-hidden` | Layout and text helpers |

### Controls inside a table cell

A `<select>` sizes itself to its **widest option**, so an option label sets the column
width for every row. `DISABLED - blocks sign-in` made the account status column 242px on
its own and pushed the table past the content width. Keep an option label to the name of
the choice — never a sentence — and state the consequence in the card footer instead; the
`aria-describedby` on the control ties the two together:

```html
<select name="accountStatus" aria-describedby="account-status-note">...</select>
...
<p class="meta" id="account-status-note"><strong>DISABLED</strong> signs the account out immediately ...</p>
```

An option's `value` is the enum the controller binds and is never touched for display;
only its label is. Roles therefore read as words in both the badge and the select:

```html
<option th:each="candidate : ${roles}"
        th:value="${candidate}"
        th:text="${#strings.capitalize(#strings.toLowerCase(candidate.toString()))}"
        th:selected="${candidate == user.role}">Requester</option>
```

Account status keeps the bare enum (`ACTIVE`, `DISABLED`) as its label, because the
footer note above names those constants literally; changing one without the other would
leave the note pointing at a word that is no longer on screen.

### Status labels

`fragments/components.html` maps each enum to a tone, and each badge prints the enum
name as **text** as well. Never convey a state with colour alone. When a new enum value
appears, add it there — one edit, and every page that shows it agrees.

| Fragment | Argument | Tone today |
| --- | --- | --- |
| `statusBadge(status)` | `RequestStatus` (10 values) | `SUBMITTED` → `info`; **every other value → `neutral`** |
| `urgencyBadge(urgency)` | `UrgencyLevel` | `HIGH` → `danger`, `MEDIUM` → `warning`, `LOW` / `null` → `neutral` |
| `roleBadge(role)` | `Role`, or the `String` `NavigationAdvice` supplies | always `brand`; the name is printed in title case |
| `accountStatusBadge(accountStatus)` | `AccountStatus` | `ACTIVE` → `success`, anything else → `danger` |

`statusBadge` is the one place that is behind the state machine: the request lifecycle
grew to ten statuses, and only `SUBMITTED` has a tone of its own so far. The other nine
render `neutral` and still read correctly, because the badge prints the name. Giving the
new values distinct tones is a visual change and belongs in the PR that owns the palette,
not in an unrelated page.

```html
<span th:replace="~{fragments/components :: statusBadge(${request.status})}">SUBMITTED</span>
```

### Icons

Inline SVG only, `viewBox="0 0 24 24"`, `stroke="currentColor"`, sized by CSS.
Decorative icons carry `aria-hidden="true" focusable="false"`; an icon that is the only
content of a control needs the control to have its own accessible name instead.

---

## 6. Forms

Keep the server contract exactly as the controller expects it:

- `th:action` (never a plain `action`) on any POST, so Thymeleaf injects the CSRF
  hidden field. The endpoint is CSRF-protected; the token is required, not decorative.
- Field `name`s, `th:object` / `th:field` bindings and the `BindingResult` error
  rendering stay as they are. `th:errors` renders the message the validator produced.
- A password input is **never** bound with `th:field`: binding writes the submitted
  value back into the HTML of a failed attempt, where it would sit in the browser
  cache and in any screenshot. `th:errors="*{password}"` still renders, because it
  reads the `BindingResult` directly.
- Make that a property of the data, not of the markup. Any command object holding a
  secret carries a `clearSecrets()` that the controller calls before re-rendering
  (`RegistrationCommand` is the pattern), so a future `th:field` cannot leak a value
  that is no longer there. A test asserting the password is absent from the refused
  response is what keeps it true.
- Password rules stated in the markup must match the backend policy
  (`PasswordPolicy`): at least 12 characters, at least one letter and one digit.
  Change the policy and the hint text together.
- A rejected submission returns to the page with the non-sensitive input preserved and
  the field-level error next to its input:

```html
<p class="form-field" th:classappend="${#fields.hasErrors('displayName')} ? 'form-field--invalid'">
  <label class="form-field__label" for="displayName">Display name</label>
  <input type="text" id="displayName" th:field="*{displayName}"
         aria-describedby="displayName-hint displayName-error"
         th:attr="aria-invalid=${#fields.hasErrors('displayName')} ? 'true' : null">
  <span class="form-field__hint" id="displayName-hint">Shown to other users.</span>
  <span class="form-field__error" id="displayName-error" th:errors="*{displayName}">Display name is required.</span>
</p>
```

Give every input a `<label for>`; placeholder text is not a label. Use
`autocomplete="username"` / `"current-password"` / `"new-password"` where they apply.

### Feedback, and which kind to use

Four different situations must not look alike:

| Situation | How it reads |
| --- | --- |
| Success after a redirect | `alert('success', ...)` at the top of the page, from a flash attribute |
| Field validation error | `.form-field__error` under its own input, with `aria-describedby` and `aria-invalid` |
| Page-level refusal | `alert('error', 'This change was refused', ${formError})` above the content |
| Nothing to show yet | `emptyState(...)`, inside the card that would have held the rows |

---

## 7. Tables

Every table goes in a `.table-scroll` wrapper. That is what confines horizontal
scrolling to the table on a phone, instead of letting the whole page scroll sideways.
`tabindex="0"` and `role="region"` are part of the pattern, not optional extras: the
wrapper is what scrolls, so it is what the keyboard has to be able to reach.

```html
<div class="table-scroll" tabindex="0" role="region" aria-label="Your maintenance requests">
  <table class="data-table">
    <caption class="visually-hidden">What these rows are.</caption>
    <thead><tr><th scope="col">Ticket</th>...</tr></thead>
    <tbody>
    <tr th:each="row : ${rows}">
      <th scope="row"><a th:href="@{/requests/{t}(t=${row.ticketNumber})}" th:text="${row.ticketNumber}">SF-2026-000001</a></th>
      <td class="data-table__wrap" th:text="${row.title}">Title</td>
      <td class="data-table__nowrap" th:text="${#temporals.format(row.createdAt, 'yyyy-MM-dd HH:mm')}">2026-09-20 10:30</td>
    </tr>
    </tbody>
  </table>
</div>
```

- The first cell of each row is a `<th scope="row">` — it is the row's identity.
- `data-table__wrap` lets a long value wrap; `data-table__nowrap` keeps a date or an id
  on one line. Column widths are set in CSS so headers and cells stay aligned.
- **Keep the table's min-content inside the content width.** An eight-column table is
  wide, and a column that only just overflows is worse than one that clearly does:
  the last column sits half off the card and the reader cannot tell there is a
  scrollbar. `.table-scroll` saves the layout, not the reader. When the table grows,
  measure it — put a probe in the page and read `table.scrollWidth` against
  `wrap.clientWidth` — then take the width out of a header label or a cell, as the
  account table does with `data-table__stack` (date above time, ~95px instead of
  ~155px for one line, and both values kept).
- Do not add search, filter, sort or pagination controls unless the backend actually
  returns paged, counted data. A control with nothing behind it is worse than no
  control.

### Filtering and pagination

`request/mine.html` is the reference. It renders a control only where the query behind
it really returns that data:

- a `?status=` filter form, because `RequestQueryService.listMyRequestsPage` takes a
  status and returns a `Page`;
- a `.page-head-row__meta` count (`3 shown`), which is `#lists.size(requests)` — the
  rows on screen, not a total it does not have;
- a pager below the table, driven by `${pagination}` (`.hasPrevious()`, `.hasNext()`,
  `.totalElements`, `.number`, `.size`), which is the `Page` the controller put in the
  model as `pagination`. The pager prints `N requests` from `totalElements` and links
  to the previous and next page, carrying the current filter and `size` with it.

Two things the pager deliberately does **not** carry over, both because the data is not
there: a page count or "page 2 of 7" (no page-size selector, so the count would restate
`totalElements`) and a first/last jump (the query is a straightforward `Page`; there is
nothing to jump by). A page whose service returns a plain `List` and no count renders an
`emptyState` and no pager at all — an empty list has no count to print, and printing
`0 shown` over a filtered-out result would state a fact the page cannot know.

The administrator's request queue (`admin/request-queue.html`) and the technician's work
order list (`workorder/mine.html`) are still the older, bare pages. They are correct —
they render `pagination.totalElements` and Previous/Next — but they do not yet wear the
shared shell. Integrating them is a follow-up owned by the module that owns them, not a
change to make casually from a UI task.

---

## 8. Page layouts

- **Overview**: it opens with the welcome band (`.welcome` — the greeting plus the
  `campusAside` drawing) rather than a `.page-head`, because the page has no single
  subject to title. Below it, one block per role and nothing else. A requester gets a
  two-column `.overview-grid`: their five most recent requests in a `.card` with a
  "View all requests" link to `/requests/mine`, and an `.essentials` aside whose foot
  carries the page's one primary action, "New request" → `/requests/new`. An
  administrator gets an `.action-list` of the three destinations that role really holds
  — Request queue, User management, Request lookup. A technician gets an `.action-list`
  with My work orders (`/workorders/mine`) and, while `${assignmentWired}` is false, a
  card footer stating that no work order can exist yet because the dispatch module's
  assignment adapter is not registered. That flag is
  `RequestAssignmentAccessService.isAvailable()`, so the footer removes itself when the
  adapter arrives instead of going stale the way a hard-coded "not available" string
  would. Show only what a public service can already answer — no invented counts, charts
  or placeholder rows to fill the space.
- **List page**: `.page-head-row` (title plus any action, e.g. "New request") + one card
  containing a `.table-scroll` table or an `emptyState`.
- **Detail page**: `.detail-layout` with a `__main` column (description, history) and an
  `__aside` column (the identifying facts, in a `.detail-list`). Put the `<aside>`
  **first in the markup**: on a phone it reads first, and CSS grid places it on the
  right at desktop width. Reordering with CSS alone would leave the reading order
  backwards for anyone using a screen reader at either size.
- **Form page**: a `.card` of width-limited content, split into `.form-section`
  fieldsets, with a primary submit and a secondary Cancel/Back.

`prefers-reduced-motion` is honoured globally: transitions are removed, not shortened.

---

## 9. Five Thymeleaf traps

**Attribute precedence.** `th:replace` and `th:insert` are applied at precedence 100,
`th:if` / `th:unless` at 300. On the same tag, the condition is dropped along with the
tag. Put the condition on a wrapper and the fragment on a child:

```html
<!-- Wrong: the th:if never runs. -->
<div th:replace="~{fragments/components :: emptyState('None', null)}" th:if="${empty}"></div>

<!-- Right. -->
<div th:if="${empty}">
  <div th:replace="~{fragments/components :: emptyState('None', null)}"></div>
</div>
```

**Fragment fragments.** A fragment declared on a container element carries that
container's tag with it, so `th:replace` would remove a wrapper the page relies on.
`mark` in `layout.html` is a bare `<svg>` for exactly this reason, and pages insert it
with `th:insert` into a sized wrapper.

**Ordinary HTML comments are rendered.** Thymeleaf strips only its own parser-level
blocks (`<!--/* … */-->`); a plain `<!-- … -->` in a template is passed through and
reaches the browser. Two consequences:

- A page's source comments are visible to anyone who views source. Never put a
  credential, an account name or an internal path in one.
- A comment counts as page content for anything that reads the response. Writing a
  phrase in a comment to explain why it was *removed* still puts that phrase in the
  output, and a rendering test asserting `not(containsString(...))` will fail on the
  explanation rather than on the markup. Describe the change without quoting the removed
  text — "a second heading over the same table only restated it" rather than the heading
  itself.

**`#fields` needs a form context, and it fails at render time — not in the tests you
expect.** `#fields.hasGlobalErrors()`, `#fields.globalErrors()` and the per-field
`#fields.hasErrors('x')` all read the *current form*. Outside a `th:object` there is no
current form, and Thymeleaf throws

```
Could not bind form errors using expression "global" ... line 60
```

which fails the **whole template** — so a page whose error summary sits above the
`<form>` (the intuitive place, and where it would be read first) returns 500 on a plain
`GET`, for a user who has not typed anything yet. In `register.html` the summary is the
first child *inside* the form for this reason, with the placement explained in a comment
so nobody "tidies" it back out.

**A `param.*` guard cannot be written with bare `and`.** `param.error` is *absent* — not
an empty array — when the page is opened without a query string, and SpEL refuses to read
that null as a boolean:

```
EL1001E: Type conversion problem, cannot convert from null to boolean
```

```html
<!-- Wrong: 500 on a plain GET /login. -->
<div th:if="${param.error and param.error[0] != 'type'}">

<!-- Right. -->
<div th:if="${param.error != null and param.error[0] != 'type'}">
```

The same failure mode applies to every `${param.x and ...}` in the codebase, not only to
`error`; check any new one by opening the page with no query string at all.

Use `th:text`, never `th:utext`: everything a user typed is escaped by default, and
that default is not worth trading for a line break.

**A `@Pattern` message reaches the page through `MessageFormat`.** Field-level bean
validation messages are formatted with `java.text.MessageFormat`, in which a single quote
is a *quoting marker*: it is consumed, and the text between two of them is taken
literally. `"…only letters, digits, '.', '_' or '-'."` therefore renders as
`…only letters, digits, ., _ or -.` — no quotes. This is not a Thymeleaf problem and not a
bug in any one page: it applies to every message that contains an apostrophe, so an
assertion on rendered text must use the post-`MessageFormat` wording. `'` or a doubled
`''` are the ways to actually show a quote; neither was applied to the shared username
message, since the admin account form renders the same string and changing it would change
a page outside this work.

---

## 10. Accessibility checklist

- One `h1` per page; headings in order.
- Every input has a label; every control has an accessible name — for a repeated
  control, one per row (`aria-label="Save new role for alice"`), and per-row ids
  suffixed with the row's id so a label never points at a duplicated id.
- The current navigation item carries `aria-current="page"`.
- Status is text plus colour, never colour alone; urgency and account status use words.
- Roles read as words — **Requester, Technician, Administrator** — wherever the interface
  shows one. The backend enum (`Role.REQUESTER`) and every permission rule keep their
  own spelling; only the label changes. `~{fragments/components :: roleBadge(${role})}`
  is the one place that maps between them, so the badge, the `appbar` and the account
  table cannot drift apart.
- Focus is visible on every interactive element. `:focus-visible` gives one 3px brand
  outline everywhere; check it on a `<select>`, on a link inside a table and on the
  scroll region below, not only on buttons.
- A control that is drawn by something else is hidden with `opacity: 0` and a 1px box,
  never with `display: none` or `hidden`: the second pair takes the control out of the
  tab order, off the arrow keys and out of the form, and the group it belonged to stops
  being operably one group. `.account-type__input` is the case in the codebase — the
  segment the reader sees is the input's adjacent sibling, and the state it draws is
  `:checked` on that input and nothing else.
- A way in for somebody without an account sits on the page in **every** state, as a
  real link to the real page. Not behind a toggle, not inside a collapsed section, and
  not switched by the account type: which kind of account the visitor is signing in as
  is not a reason to hide how to get one.
- A horizontally scrollable region carries `tabindex="0"`, `role="region"` and a label,
  so the columns past the right edge are reachable by keyboard and not by mouse alone.
  `.table-scroll` is the region; the markup is in §7.
  - **One deliberate exception:** the overview's recent-requests table
    (`home.html`) carries neither `tabindex` nor `role`, because every row holds a
    link — focusing a link scrolls it into view by itself, and an extra stop on a
    non-interactive element would announce nothing. Do not "fix" it by adding the
    attributes back, and do not copy the omission to a table whose rows are plain
    text: a text-only table past the right edge is genuinely unreachable without
    the wrapper attributes.
- The mobile drawer is a `button` with `aria-expanded` and `aria-controls`, closes on
  Escape and returns focus to the toggle, and is an enhancement — without JavaScript
  the navigation is simply stacked.
- Errors are `role="alert"`; confirmations are `role="status"`.
- A disclosure is a native `details` with a `summary` that reads as the action
  ("Report this answer"), never a scripted toggle and never a click handler on a `div`.
  The browser supplies the role, the expanded state and Space/Enter for free; the summary
  keeps its default marker, because the marker is what says the line opens, and a
  `summary` whose only content is a bare word is not a label anybody can act on. Nothing
  inside an open disclosure is a duplicate id: a repeated one carries the row's id as a
  suffix, as `.report-entry` does on a thread with several answers.
- Decorative images and icons are hidden from assistive technology.

---

## 11. Error page

`error.html` is reached by every unhandled outcome, from
`GlobalExceptionHandler` and from Spring Boot's own error controller. It is
intentionally standalone — no `sidebar`, no `appbar` — because a 404 can reach a visitor
who is not signed in. It renders the status, one explanation and one way out taken from
the reader's own role; it never renders the exception message, a stack trace, SQL, a
request path or a filesystem path. Add a new status by adding one `th:case` branch with
one wrapper element holding its heading and its explanation.

---

## 12. Tests

Templates are only checked at render time, so a broken expression is a 500 in a browser
and nothing at all in a test that mocks the service. Two tests cover the existing pages:

- `AuthenticationFlowIT` — sign-in, the three role homes, the 403 page and a real HTTP
  login. Its home assertions are per role: a technician's overview must link
  `/workorders/mine` and must not link `/requests/new` or `/admin/users`, and a requester
  must not link the administrator's area.
- `RequestPagesRenderingIT` — renders the overview, the request list, the request detail,
  the administrator lookup and the account list through the real template engine (H2
  underneath) and asserts the details each page exists to show, including which entry
  points each role's overview offers.

Both files are the place to state that a role-specific link is present and that another
role's entry point is absent. Keep that pairing when you add a role's link: the positive
assertion proves the route is offered, and the negative one proves the change did not
hand it to everyone.

A third file, `SecurityConfigTest`, loads `HomeController` in a `@WebMvcTest` slice to
check the route matrix. A slice loads controllers **without the service layer**, so every
service a controller takes in its constructor must be declared there as a `@MockitoBean`.
Add a constructor parameter to a controller this slice loads and, without that mock, the
whole slice fails to start and every case in it errors — 42 of them, from one missing
bean. When you add the mock, say why in a comment: the next reader sees a mock in a
security test and needs to know it is a slice artefact, not a weakened assertion.

Add a rendering test for any new page. Assert what the page must contain, not which CSS
classes it used.

---

## 13. Ground rules

- Thymeleaf, CSS and a little plain JavaScript. No React, Vue, SPA or new build chain.
- No cosmetic changes to `SecurityConfig`, to database configuration, or to business
  rules in a UI task.
- Never widen access to make a page visible.
- Sign out stays a CSRF-protected POST form.
- Do not add a page, controller, service or table for a feature that is not being
  built. If an entry point would lead nowhere, either remove it or say plainly, in a
  `.notice`, that the feature is not available yet.

New request is a contextual action on My Requests and the overview, not a top-level navigation destination. The submission form uses the shared shell with My Requests active, field-level validation, and optional photo previews. Upload guidance and browser checks use the configured server limits.

The signed-in visual refresh is layered in static/css/visual-refresh.css after site.css. Home uses a dark teal hero with the decorative campus-maintenance.png, compact workflow guidance and four contextual community links. The community filter is a compact GET toolbar and questions render as discussion rows; query parameters and pagination retain their existing behavior. The new-request form uses numbered sections, shorter copy and native details disclosures for optional help. Decorative illustrations have empty alt text; functional labels and validation remain explicit. No preview/sample records are added to the application database.

The accepted visual direction keeps the existing hero, illustration, palette and content arrangement. Peripheral polish uses subtle mint/amber radial backgrounds at desktop widths and a fluid content maximum of 1200–1440px above 1500px, shared by navigation, main content and footer. Keep future decorative changes restrained and preserve the mobile composition.

Content framing keeps section headings, result totals and pagination within their related surfaces. Home community shortcuts share one card; request pages use a framed page heading. The community desktop layout pairs a readable discussion feed with activity links, topic navigation and a requester-only maintenance action. At smaller widths, topic selection returns to the GET toolbar and the remaining sidebar cards stack below the feed. Topic links preserve the current search, filter and page size. Show one result total and render pagination only when another page exists. Use the decorative SVG fragments in fragments/ui-icons.html for consistent interface icons; functional controls keep explicit accessible labels.

## Community completion (2026-10-09)

Keep headings and statistics inside a `card page-context`, and keep one list total in
the pager/feed header. `New request` remains a contextual action on Home/My Requests,
not a separate global-navigation destination. Community personal activity lives at
`/community/mine?tab=QUESTIONS|ANSWERS`; handled report history remains reachable after hiding.

Question/answer editors use `.community-editor`; required hints are concise and native
`required`/`minlength`/`maxlength` mirror the service DTO. The server still validates.
`community-form.js` adds counters for prefilled and newly typed text, appends their unique
IDs to `aria-describedby`, and focuses the first server-invalid control. Global refusals
receive focus when no field is invalid. Do not add live announcements on every keystroke.
Report row hint IDs include the report ID. Preserve view/page/size in every moderation POST.

Display names are escaped text from UserService's batch public-name API; never render
stored HTML, credentials or private account fields. Counts include VISIBLE answers only,
loaded once for a page. Moderation retains account IDs for administrative traceability.
See `sprint3/UserA_Quality_Completion_CN.md` for browser/keyboard evidence and remaining
physical-device/screen-reader checks.
