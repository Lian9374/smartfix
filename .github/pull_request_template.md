## Jira issue

- **SCRUM-** <!-- issue key, e.g. SCRUM-21 -->
- Link: <!-- https://your-jira.atlassian.net/browse/SCRUM-XX -->

## Summary

<!-- What does this change do and why? One or two sentences. -->

## Changes

<!-- Bullet list of concrete changes. Keep it reviewable. -->
- 

## Testing

<!-- What was executed to verify this change? -->
- [ ] `mvn test` passes locally
- [ ] `mvn clean package` passes locally
- [ ] Manual check (describe what you tried)

## Screenshots

<!-- UI changes only. Attach screenshots here or note "N/A". -->

## Checklist

- [ ] Code follows the layering rules in `docs/architecture.md` (controllers stay thin, no DB access in controllers/views)
- [ ] No premature business logic or design patterns were added beyond the Jira story
- [ ] No secrets, `.env`, or real credentials committed
- [ ] No generated/temporary files committed (`target/`, IDE files, logs)
- [ ] Jira story moved to the agreed status (e.g. In Review)
