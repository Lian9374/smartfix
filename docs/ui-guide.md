# SmartFix UI Guide

How the web pages are built, and how to add one without inventing a second style.
Everything here is Thymeleaf, CSS and a small amount of plain JavaScript — no frontend
build step, no framework, no external font or icon service. Nothing on any page
requires JavaScript to work: the script only adds the mobile navigation drawer and the
password reveal button.

Read this before adding a page. The reference implementation for almost everything
below is `src/main/resources/templates/request/mine.html`.

---

## 1. Files

| File | What it owns |
| --- | --- |
| `templates/fragments/layout.html` | `head`, `mark`, `sidebar`, `topbar`, `footer`, `scripts` — the shell every signed-in page uses |
| `templates/fragments/components.html` | `badge`, `statusBadge`, `urgencyBadge`, `roleBadge`, `accountStatusBadge`, `alert`, `emptyState`, `timeline` |
| `static/css/site.css` | Design tokens and every component class. Six commented sections: tokens, base, app shell, components, sign-in page, responsive |
| `static/images/favicon.svg` | The only image asset |
| `common/web/NavigationAdvice.java` | Adds `${navRole}` and `${navUsername}` to every model |
| `error.html` | The one page for every error status |

---

## 2. Skeleton for a new signed-in page

Copy this and change the three marked values. The structure is not decorative: the
sidebar, the topbar, the sign-out form and the footer all come from fragments, so a
page never repeats them.

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('Page title')}"><!-- (1) -->
  <title>Page title · SmartFix</title>
</head>
<body>
<div class="app-shell">
  <aside th:replace="~{fragments/layout :: sidebar('overview')}"></aside><!-- (2) -->

  <div class="app-main">
    <header th:replace="~{fragments/layout :: topbar('Section')}"></header><!-- (3) -->

    <main id="main-content" class="content">
      <div class="page-header">
        <div class="page-header__text">
          <h1 class="page-header__title">Page title</h1>
          <p class="page-header__subtitle">One line saying what this page is for.</p>
        </div>
        <div class="page-header__actions">
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
2. The key of the **current** page, so the matching sidebar link gets
   `aria-current="page"`. Existing keys: `overview`, `my-requests`,
   `user-management`, `request-lookup`.
3. The section name shown in the topbar, e.g. `Administration`.

---

## 3. Navigation and roles

The sidebar is built once, in `layout.html`, and shows only routes that exist:

| Role | Links |
| --- | --- |
| everyone | Overview (`/`) |
| `REQUESTER` | My Requests (`/requests/mine`) |
| `ADMINISTRATOR` | User Management (`/admin/users`), Request Lookup (`/admin/requests/lookup`) |
| `TECHNICIAN` | Overview only, plus a note that work-order features are not available |

`${navRole}` and `${navUsername}` come from `NavigationAdvice`, which reads the
authenticated principal. **`navRole` decides what is *shown*, never what is
*allowed*** — authorisation is `SecurityConfig` plus the service-layer ownership
checks, and those are the only things that protect a route.

### Adding a nav entry

1. Add the controller and the template (with the skeleton above).
2. Authorise the route in `SecurityConfig` — do this first; the link must never
   exist before the route does.
3. Add the link inside `<nav class="nav">` in `layout.html`, guarded by the role
   that may use it, with its own `active` key:

```html
<a class="nav__link" th:href="@{/workorders}" th:if="${navRole == 'TECHNICIAN'}"
   th:attr="aria-current=${active == 'work-orders'} ? 'page' : null">
  <svg viewBox="0 0 24 24" ... aria-hidden="true" focusable="false"><!-- path --></svg>
  <span>My Work Orders</span>
</a>
```

Three rules, all of which existing pages depend on:

- Icons are inline SVG with `aria-hidden="true" focusable="false"` and a sibling
  `<span>` carrying the visible text. There is no icon font.
- `aria-current` is written with `th:attr` and `null` for "not current", because
  `null` removes the attribute; `""` would not.
- The sign-out control stays the POST form in `topbar`. It is CSRF-protected and
  Spring Security owns the endpoint. Never replace it with a GET link.

---

## 4. Colours, spacing and shape

Use the tokens in `:root` (`site.css` §1). A literal hex value outside that block is a
bug: it drifts from the palette the moment anyone adjusts it.

| Purpose | Token |
| --- | --- |
| Brand, hover, tint | `--color-brand` `#0f766e`, `--color-brand-hover` `#115e59`, `--color-brand-soft` |
| Page / card / muted surface | `--color-bg` `#f5f7fa`, `--color-surface` `#ffffff`, `--color-surface-muted` |
| Body / muted text | `--color-text` `#172b3a`, `--color-text-muted` `#526577` |
| Borders | `--color-border` `#dce4eb`, `--color-border-strong` `#c3cfda` |
| Status | `--color-success` `--color-warning` `--color-danger` `--color-info` `--color-neutral`, each with a `-soft` tint for backgrounds |
| Spacing | `--space-1..10` = 4, 8, 12, 16, 20, 24, 32, 40px |
| Shape | `--radius-card` 12px, `--radius-control` 8px, `--radius-pill` |
| Layout | `--sidebar-width` 224px, `--topbar-height` 64px, `--content-max` 1200px, `--content-pad` 40px (20px at ≤900px, 16px at ≤480px) |
| Control size | `--control-height` 40px (buttons), `--field-height` 44px (inputs) |

### Desktop measure, and why the topbar lines up with the title

`--content-max` is the readable measure; `--content-pad` is the gutter *outside* it.
Keeping them as two tokens is what lets the account bar, the page title and the footer
share one left edge at every width. `.content` is capped at
`calc(--content-max + 2 * --content-pad)` and centred with `margin-inline: auto` inside
the area beside the sidebar; `.topbar` and `.site-footer` then use

```css
padding-inline: max(var(--content-pad), calc((100% - var(--content-max)) / 2));
```

which resolves to the same left edge in all three regimes: wide enough that the measure
is capped (both centre on the same box), between the two (the gutter wins for both), and
narrow (both sit at the gutter). Verifying a change here means measuring the rendered
`left` of `.topbar__section`, the page `h1` and the footer text — they must be equal.

Every status colour is used as a **pair**: the dark token for text, the `-soft` token
for its background. That pairing is what keeps the text readable — each pair was
measured against WCAG AA (4.5:1) and the muted-text token is never used on a tint.

Type: system sans stack, body 16px, form inputs 16px (so mobile Safari does not zoom
on focus), `h1` 26–32px. No external font is loaded.

The design is deliberately bright and low-contrast in *decoration* while staying high
contrast in *content*: no full-page dark backgrounds, no gradients, no glass, no heavy
shadows, no decorative animation. Hierarchy comes from borders and spacing.

---

## 5. Components

All of these are in `site.css` §4 and, where they render markup, in
`fragments/components.html`.

| Component | Markup | Notes |
| --- | --- | --- |
| Page header | `.page-header` > `__text` + `__actions` | One `h1` per page, plus a one-line `__subtitle` |
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
| Page note | `~{fragments/components :: pageNote(${text})}` | One quiet line at page level for a standing fact — e.g. that submission is unavailable. Use it **once per page**: it belongs above the card, not repeated inside an empty state |
| Card link | `.card__link` | The single "View all …" style action in a card header, opposite the title |
| Action list | `.action-list` > `.action-list__link` > `__label` + `__desc` | A short list of real destinations. Each entry must lead somewhere that exists |
| Timeline | `~{fragments/components :: timeline(${entries})}` | Status history; states each transition in words |
| Alert | `~{fragments/components :: alert(${tone}, ${title}, ${message})}` | Page-level feedback; `error` is announced as `role="alert"`, the rest as `role="status"` |
| Notice | `.notice` | Something the reader can read but not act on — used for features that do not exist yet |
| Auth shell | `.auth-shell` > `.auth-intro` + `.auth-panel` | Sign-in only. Two columns at `48fr / 52fr`; at ≤900px the context column is hidden and `.auth-panel__brand` supplies a compact brand line |
| Prose | `.prose` | User-entered text; preserves line breaks |
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
  control. `request/mine.html` accepts `?page=`/`?size=` but renders no pager, because
  the query returns one page and no total.

---

## 8. Page layouts

- **Overview**: `.page-header` (greeting), an optional page note, then one card per
  role. A requester gets the five most recent of their own requests and a "View all
  requests" link; an administrator gets an `.action-list` of the destinations they
  actually have; a technician gets a card saying plainly that this release gives them no
  request workspace. Show only what a public service can already answer — no invented
  counts, charts or placeholder rows to fill the space.
- **List page**: `.page-header` + one card containing a `.table-scroll` table or an
  `emptyState`.
- **Detail page**: `.detail-layout` with a `__main` column (description, history) and an
  `__aside` column (the identifying facts, in a `.detail-list`). Put the `<aside>`
  **first in the markup**: on a phone it reads first, and CSS grid places it on the
  right at desktop width. Reordering with CSS alone would leave the reading order
  backwards for anyone using a screen reader at either size.
- **Form page**: a `.card` of width-limited content, split into `.form-section`
  fieldsets, with a primary submit and a secondary Cancel/Back.

`prefers-reduced-motion` is honoured globally: transitions are removed, not shortened.

---

## 9. Three Thymeleaf traps

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

Use `th:text`, never `th:utext`: everything a user typed is escaped by default, and
that default is not worth trading for a line break.

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
  is the one place that maps between them, so the badge, the topbar and the account
  table cannot drift apart.
- Focus is visible on every interactive element. `:focus-visible` gives one 3px brand
  outline everywhere; check it on a `<select>`, on a link inside a table and on the
  scroll region below, not only on buttons.
- A horizontally scrollable region carries `tabindex="0"`, `role="region"` and a label,
  so the columns past the right edge are reachable by keyboard and not by mouse alone.
  `.table-scroll` is the region; the markup is in §7.
- The mobile drawer is a `button` with `aria-expanded` and `aria-controls`, closes on
  Escape and returns focus to the toggle, and is an enhancement — without JavaScript
  the navigation is simply stacked.
- Errors are `role="alert"`; confirmations are `role="status"`.
- Decorative images and icons are hidden from assistive technology.

---

## 11. Error page

`error.html` is reached by every unhandled outcome, from
`GlobalExceptionHandler` and from Spring Boot's own error controller. It is
intentionally standalone — no sidebar, no topbar — because a 404 can reach a visitor
who is not signed in. It renders the status, one explanation and one way out taken from
the reader's own role; it never renders the exception message, a stack trace, SQL, a
request path or a filesystem path. Add a new status by adding one `th:case` branch with
one wrapper element holding its heading and its explanation.

---

## 12. Tests

Templates are only checked at render time, so a broken expression is a 500 in a browser
and nothing at all in a test that mocks the service. Two tests cover the existing pages:

- `AuthenticationFlowIT` — sign-in, the three role homes, the 403 page and a real HTTP
  login.
- `RequestPagesRenderingIT` — renders the overview, the request list, the request detail,
  the administrator lookup and the account list through the real template engine (H2
  underneath) and asserts the details each page exists to show.

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
