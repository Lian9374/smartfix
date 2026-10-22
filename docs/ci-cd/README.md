# SmartFix SWE5006 Continuous Integration

## Scope and audit

This implementation starts from main d586609 (2026-10-22 audit). The developer checkout on feature/requester-detail-refresh contained unrelated uncommitted changes; implementation uses the separate ci/swe5006-github-actions branch and worktree. Those changes are preserved.

Main had Jenkinsfile but no GitHub Actions workflows. Jenkins repeats compile/test/package/verify; this workflow uses one Maven lifecycle per isolated job. Dockerfile already builds with Java 21 and clean verify; Compose uses PostgreSQL 16 with a persistent development volume. CI does not use that volume or deploy anything.

SWE5001 workflows swe5001-ci.yml and swe5001-deploy.yml belong to swe5001-cloud-native and remain untouched. Previous successful Actions runs belonged to SWE5001 and are not evidence for this pipeline.

92 test Java source files were inspected. Surefire (inherited from Spring Boot parent) runs Test*, *Test, *Tests, *TestCase during test. Failsafe 3.5.3 runs IT*, *IT, *ITCase during integration-test and fails the lifecycle during verify. failIfNoTests is enabled for Failsafe. H2/Spring/Mockito fixtures cover default tests; the test profile disables Flyway and scheduled notification delivery. SMTP delivery is mocked in native reliability tests. PostgreSQL tests require TEST_DB_URL, TEST_DB_USERNAME and TEST_DB_PASSWORD, with dedicated *_test database guards and isolated random schemas.

The old explicit default exclusion list omitted CampusMapPostgresIT, DeliveryReliabilityPostgresIT and UserEDeliveryMigrationPostgresIT. The POM now routes all *PostgresIT plus four historically named native IT classes into postgres-it. The postgres-it profile clears exclusions, so every existing test remains in the full CI verification. No tests, assertions or published migrations are removed or changed.

## Architecture and stages

Developer push -> PR to main -> two independent required checks -> review -> user-approved merge -> main push verification.

- **SWE5006 Build and Test**: Ubuntu 24.04, Temurin 21, Maven dependency cache keyed by pom.xml; `mvn -B --no-transfer-progress clean verify`. Compiles application/tests, runs Surefire, creates executable Spring Boot JAR, runs default Failsafe tests, verifies results. The JAR is uploaded only after verification succeeds.
- **SWE5006 PostgreSQL Integration**: separate Ubuntu runner and PostgreSQL 16 service with health checks. `mvn -B --no-transfer-progress -Ppostgres-it verify` runs the full existing suite, including migrations, constraints, data-preserving upgrades and concurrency tests. Repeating default tests here follows the existing full profile contract; there are no extra standalone compile/test/package commands.

Jobs run in parallel and have 25/30 minute timeouts. Maven failure propagates through Bash pipefail even when tee captures logs. There is no continue-on-error. PR updates cancel older runs of the same PR. Main runs are not cancelled. Both checks must be required individually; there is no skipped aggregator check that could mask a failure.

## Workflow and triggers

File: .github/workflows/smartfix-ci.yml. Supports pull_request targeting main (opened/synchronize/reopened), push to main, workflow_dispatch. No paths filters exclude documentation changes. checkout tests the PR merge ref by default. A first PR can introduce its own pull_request workflow; verify the actual event/run before claiming success. Manual dispatch requires the workflow on the default branch; main push and dispatch remain NOT RUN until an approved merge or later dispatch occurs. Never merge solely to obtain evidence without approval.

## PostgreSQL and migration safety

The service creates smartfix_ci_test with synthetic credentials valid only within the disposable runner. No repository or production secrets are needed. Native tests create and remove their own random schemas. CI never invokes Flyway clean or repair. Published SQL remains immutable. Existing LegacyMigrationCompatibility and legacy recovery tests are part of the baseline, not a newly introduced migration workaround.

On failure, retain the exact failing migration version, SQLSTATE, object/constraint and stack trace from postgres-verify.log and Failsafe XML. Inspect data and schema before proposing a new forward migration or narrowly scoped compatibility fix. Never edit historical checksums, disable validation, or use clean/repair to obtain a green run.

## Artifacts and security

Each run retains build Surefire/Failsafe reports and build-verify.log; PostgreSQL Surefire/Failsafe reports and postgres-verify.log; and the verified JAR. Names include run ID and attempt. Reports/log upload uses always(), including test failures; missing all expected files fails upload. Retention is 30 days, subject to repository limits. Archive important demo evidence before expiry. Cancellation/runner failure can prevent complete reports.

Token permission is contents:read; checkout does not persist credentials. Actions are pinned to verified v4 release commit SHAs. No pull_request_target, deployment, secrets, write permissions, package publication or production connectivity.

## Pull request and main settings

The repository has an active ruleset `Protect SmartFix main branch` (22548119), enforcing PRs, deletion protection and no force pushes. At audit it required zero approvals and no status checks. Classic branch protection returned 404 because a ruleset is used. Settings are not changed without user approval.

After actual check names appear: Settings -> Rules -> Rulesets -> Protect SmartFix main branch -> Require status checks to pass -> add exactly `SWE5006 Build and Test` and `SWE5006 PostgreSQL Integration`, selecting GitHub Actions as source when available. Enable require branches to be up to date if desired. Keep enforcement Active and target main/default branch. Optionally require one reviewer if the team has an available independent reviewer. Do not require SWE5001 checks or a workflow display name. Preserve existing rules and bypass policy. Test the configured policy on a later PR; enforcement is NOT RUN until configured and observed.

## Troubleshooting and demo

- No run: inspect Actions enablement, PR base main, merge conflicts, workflow syntax, fork approval and actual event. Do not label missing checks PASS.
- Unit/default IT failure: use build reports; do not skip tests. Native environment-variable failure in default verify indicates incorrect test routing.
- PostgreSQL connection failure: inspect service health and TEST_DB_* names; database must end in _test.
- Migration failure: follow migration safety instructions above; retain failed-run evidence.
- JAR missing: inspect Maven package/repackage and earlier failures; do not upload a partially verified JAR.
- Missing required check: match exact job names from the latest PR run and remove obsolete contexts only with approval.

Demo: open the PR, show automatic pull_request event and both jobs, expand Maven stages, open XML reports/logs, download and inspect the executable JAR, show native migration/concurrency tests, then explain review and protected merge settings. Execution evidence is recorded separately in evidence.md. PASS means executed successfully; FAIL means executed unsuccessfully; NOT RUN means not executed; BLOCKED means an identified prerequisite prevented execution.

## English presentation script

SmartFix uses an independent GitHub Actions pipeline for SWE5006. Every pull request to main automatically starts two checks on Java 21. The first compiles the application, runs unit and default integration tests, and packages the Spring Boot JAR through Maven verify. The second starts a disposable PostgreSQL 16 database and executes our full PostgreSQL integration profile, including migrations and concurrency tests. Real failures fail the check, while test reports and logs remain available as artifacts. The workflow uses read-only permissions and performs no deployment. Requiring both checks on main connects automated verification with code review before merge. The evidence document distinguishes measured results from events that have not yet been executed.

References: https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows and https://github.com/actions/upload-artifact .
