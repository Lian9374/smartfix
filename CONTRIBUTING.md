# Contributing to SmartFix

Thanks for helping build SmartFix. This short guide keeps the repository clean and the
Software Engineering process visible.

## Branch model

- `main` is the stable integration branch. Do not push to it directly.
- Work on short-lived branches, always named after the Jira issue:
  - `feature/SCRUM-XX-short-description`
  - `fix/SCRUM-XX-short-description`
  - `chore/SCRUM-XX-short-description`

Example:

```bash
git checkout -b feature/SCRUM-21-request-submission
```

## Process

1. Pick up a story in Jira and move it to **In Progress**.
2. Follow the analysis/design order in `docs/development-guide.md` (story → use case →
   ECB → sequence diagram → class design → implementation → tests). Do not implement
   before design.
3. Make small, focused commits.
4. Push the branch and open a pull request using the template
   (`.github/pull_request_template.md`), linking the Jira issue.
5. Ensure the checklist is satisfied and at least one teammate reviews.

## Definition of Done (per story)

- [ ] Analysis/design artefacts updated where relevant
- [ ] Code follows the layering rules in `docs/architecture.md`
- [ ] New domain concepts are reflected in a Flyway migration (no `ddl-auto` tables)
- [ ] Unit tests added for complex/stateful behaviour
- [ ] `mvn test` passes
- [ ] `mvn clean package` passes
- [ ] PR template filled in; Jira story moved to **Review/QA**

## Code conventions

- Java 21; code style per `.editorconfig` (4-space indentation for Java).
- Keep controllers thin; keep services cohesive; keep coupling low.
- No premature design patterns, no placeholder classes, no invented complexity.
- No secrets or real credentials; never commit `.env`.

## Reporting issues

Use the project's Jira board for issues/backlog items rather than ad-hoc channels, so
the work is visible to the whole team.
