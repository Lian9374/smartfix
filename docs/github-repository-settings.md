# SmartFix GitHub Repository Settings Guide

This is the **repository owner's** setup guide. GitHub protection settings must be
**configured in the GitHub UI** by the owner — they are **not** guaranteed to exist
merely because documentation describes them. If you are not the owner, share this page
with whoever administers the repository.

> Current status: protection settings are **recommended, not yet assumed configured**.
> Confirm in `Settings → Branches` (or `Settings → Rulesets`) before relying on them.

---

## 1. Recommended `main` branch protection

Use GitHub **branch protection rules** or the newer **rulesets** to protect `main`.

Recommended settings:

| Setting | Recommended value |
|---|---|
| Require a pull request before merging | **On** |
| Required approvals | **1** (where practical for a 5-person team) |
| Require status checks to pass | **On** — require the CI/Jenkins check for the team's pipeline (see below) |
| Block force pushes | **On** |
| Block branch deletion | **On** |
| Restrict who can push to `main` | **On** (admins/owner and via PRs only) |

Why: every change reaches `main` through a reviewed PR, CI stays green on `main`, and
nobody can accidentally force-push or delete the stable branch.

### Requiring the CI check
If the team runs Jenkins, the GitHub integration posts a status check. Once that check is
visible, add it to the required status checks so a red build blocks merges. Until the
integration is configured, "require status checks" cannot be turned on — do not claim CI
is enforced when it is not.

## 2. CODEOWNERS (optional, future)

`CODEOWNERS` lets you require review from specific people for specific paths. **We do not
ship a `CODEOWNERS` file yet** because it needs real GitHub usernames, which the team has
not supplied.

When the team is ready, a future `.github/CODEOWNERS` could look like (illustrative —
replace `@username` with real accounts):

```text
# Default owners for the whole repository
*                       @scrum-master

# Module ownership examples (illustrative)
/src/main/java/com/smartfix/request/    @member-a
/src/main/java/com/smartfix/dispatch/   @member-b
docs/                                   @member-c
```

Do **not** invent usernames — an invalid `CODEOWNERS` breaks review rules. Decide module
ownership together and only then add real handles.

## 3. GitHub Issues (optional, supporting)

Jira is the team's Agile backlog. GitHub Issues are **optional/supporting** — do not run
two competing backlogs. Issue templates are provided under
`.github/ISSUE_TEMPLATE/` (`feature.md`, `bug.md`) so that if the team *does* use issues,
they capture the same information the PR template expects (Jira key, owning module,
acceptance criteria).

## 4. Repository hygiene

- Keep `main` default branch name as `main`.
- Do not enable "auto-merge" for every PR until the team agrees — a human review gate is
  the safer default.
- Protect shared config files through review discipline (README §22), not just GitHub
  settings.
