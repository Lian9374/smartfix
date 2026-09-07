# Contributing to SmartFix

Short version of the **non-negotiable** contribution rules. Full context lives in the
README (the handbook) and in `docs/` — link to those where a rule needs explanation.

> If you are new here, start with `README.md`. It explains the project, the
> architecture, where code goes, and how to work safely.

---

## Branching

- `main` is the stable branch. **Never** commit or push to it directly.
- Work on short-lived branches named after the Jira issue:

| Prefix | Example |
|---|---|
| `feature/` | `feature/SCRUM-21-request-submission` |
| `fix/` | `fix/SCRUM-33-duplicate-assignment` |
| `chore/` | `chore/SCRUM-12-upgrade-logging` |
| `docs/` | `docs/SCRUM-15-architecture-diagrams` |
| `test/` | `test/SCRUM-41-dispatch-ranking-tests` |

## Commit expectations

- Small, meaningful commits; conventional-style messages:
  `feat(request): add request submission validation`, `fix(workorder): prevent duplicate
  assignment`, `docs: clarify local setup`.
- Stage only related files. **Never** commit `.env`, `target/`, IDE files or logs.
- No vague messages (`update`, `changes`, `fix bug`, `final`).

## Pull Requests

- All changes to `main` go through a PR. Use the template
  (`.github/pull_request_template.md`) and link the Jira issue.
- A PR needs **at least one review** from a teammate; respond to or resolve every
  comment.
- **CI must be green before merge.** Do not merge a red build "because it works locally",
  and never disable tests to make CI pass.

## Testing

- Add/update tests with the behaviour they cover (`docs/testing-guide.md`).
- `mvn test` must pass before you open a PR.
- `mvn clean package` must pass (builds the runnable jar).

## Architecture boundaries

- Follow the layer and module rules (`README.md` §6, §10; `docs/architecture.md`).
- Keep controllers thin; put business logic in services/domain, persistence in
  repositories.
- Access another module only through its **public service/API**, never its repository.
- **`common` is not a dumping ground** — no business helpers there.
- **No premature design patterns.** A pattern (e.g. a matching `Strategy`) is added only
  after the design problem justifies it.
- Cyclic module dependencies are prohibited — stop and redesign instead.

## Database migrations

- The schema is managed **only by Flyway** (`ddl-auto: none`).
- Add schema changes as **new, numbered migrations** (`V2__…`, `V3__…`), never by editing
  an already-applied migration (`docs/database-guide.md`).
- Pull latest `main` before choosing the next version number.
- Destructive SQL needs a review.

## Security & secrets

- **Never commit** `.env`, passwords, API keys, tokens or real credentials
  (`README.md` §21).
- The current `SecurityConfig` is a temporary permit-all baseline. Do not remove or
  weaken security config to fix an access problem; design real auth/RBAC in Sprint 2.

## Definition of Done

A story is done when it meets the DoD (`README.md` §18). In short: acceptance criteria
met, tests added and passing, build green, no secrets, migrations correct, docs updated,
PR reviewed, CI green, Jira updated, merged to `main`.

---

Guides: [`docs/architecture.md`](docs/architecture.md) ·
[`docs/module-guide.md`](docs/module-guide.md) ·
[`docs/development-guide.md`](docs/development-guide.md) ·
[`docs/team-workflow.md`](docs/team-workflow.md) ·
[`docs/git-safety-guide.md`](docs/git-safety-guide.md) ·
[`docs/database-guide.md`](docs/database-guide.md) ·
[`docs/testing-guide.md`](docs/testing-guide.md)
