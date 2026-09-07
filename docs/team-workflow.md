# SmartFix Team Workflow — Jira → GitHub

This document defines how the five-member team turns a Jira story into merged code,
who is responsible at each step, and how the team coordinates when stories touch the
same code.

---

## 1. Story lifecycle

```text
Backlog → Selected for Sprint → Analysis → Design → Branch → Implementation
       → Test → PR → Review → CI → Merge → Jira Done → Sprint Review
```

### Step-by-step

| # | Step | Owner | Notes |
|---|---|---|---|
| 1 | **Backlog** | Product Owner (PO) / module lead | Stories groomed with acceptance criteria. |
| 2 | **Selected for Sprint** | Team in Sprint Planning | Small enough to finish in the sprint. |
| 3 | **Analysis** | Story owner | Use case, ECB sketch, ambiguity raised (e.g. request lifecycle). |
| 4 | **Design** | Story owner (+ reviewer for architecture impact) | Sequence/class notes where behaviour is non-trivial. |
| 5 | **Branch** | Story owner | `feature/SCRUM-XX-…` from latest `main`. |
| 6 | **Implementation** | Story owner | Follow README §9–§10; keep changes small. |
| 7 | **Test** | Story owner | `mvn test`; add tests for new behaviour. |
| 8 | **PR** | Story owner | Use the template; link Jira. |
| 9 | **Review** | ≥1 teammate | At least one review recommended. |
| 10 | **CI** | Jenkins | Must be green before merge. |
| 11 | **Merge** | Reviewer or owner | To `main` only via PR; never direct pushes. |
| 12 | **Jira Done** | Story owner | Move to Done only when the DoD (README §18) is met. |
| 13 | **Sprint Review** | Team | Demo, retro, re-prioritise. |

---

## 2. Responsibilities

### Story owner
- Owns the story end to end: analysis → merged, tested code → Jira Done.
- Keeps the branch up to date with `main`.
- Answers reviewer comments; does not leave them unresolved.
- Updates docs if behaviour or setup changes.

### Reviewer
- Checks the change against the story's acceptance criteria and DoD.
- Guards architecture: module boundaries, layer rules, `common` rule, no premature
  patterns, no foreign-repository access.
- Reviews database migrations (destructive SQL especially) and security changes.
- Approves only when CI is green and comments are addressed.

### Team (everyone)
- Keeps `main` healthy: no direct pushes, no force-pushes, no secret commits.
- Raises design ambiguity early instead of coding a guess.
- Watches for overlapping work and communicates.

### Architecture-affecting decisions
Raised by anyone, **reviewed by the team**, and recorded as an **ADR** when lasting
(README §32). Examples: new framework/database, major authentication approach, a new
large module, a style change. Never smuggled inside an unrelated feature PR.

---

## 3. Coordination when two stories touch the same code/module

When stories overlap (common in a five-person team), follow this protocol:

1. **Check before you start.** Announce the module/area you are taking on
   (e.g. "I'm working in `request/domain` for SCRUM-21").
2. **Pull `main` right before branching** so you start from the latest work.
3. **Keep the owning module's public API stable.** If your change needs another story's
   API, agree the API shape with its owner first.
4. **Sync often.** Merge `main` into your branch as other PRs land (README §14 / §6 of
   `docs/development-guide.md`).
5. **Two people editing the same files** should split work by concern, or pair up on one
   branch for that area. Avoid two branches rewriting the same domain class in parallel.
6. **Coordinate migrations** — pick the next free Flyway version only after pulling
   `main` (`docs/database-guide.md`). Two PRs both creating `V2__…` will conflict on
   purpose; resolve by renumbering one to the next free version.
7. If a conflict is real business logic (two features changing the same rule),
   **talk to the other owner before resolving** — never silently pick one side.

---

## 4. Jira hygiene

- Every branch/PR references the Jira key (`SCRUM-XX`).
- Move the story through its statuses: *In Progress → In Review → Done*.
- Acceptance criteria are the contract: a story that meets them but does not meet the
  DoD is still not done.
- Jira remains the **Agile backlog**. GitHub issues are optional/supporting only (see
  `.github/ISSUE_TEMPLATE/`); do not run two competing backlogs.

---

## 5. When a design decision is ambiguous

Do **not** invent an answer in code. The proposal already contains open points —
notably **who may move a request to Closed**, and whether **SSO** / a
**technician-matching Strategy** are required. The team should resolve these during
analysis and record the decision. Until then, the architecture deliberately leaves them
open (no status-transition engine, no Strategy classes, no SSO code).
