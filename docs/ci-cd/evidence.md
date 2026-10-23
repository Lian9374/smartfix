# Measured CI evidence — SWE5006

All times below use Asia/Shanghai (UTC+08:00) unless the exported JSON explicitly contains UTC timestamps.

## Remote verification: PASS

- PR: https://github.com/Lian9374/smartfix/pull/44 (open, not merged).
- Successful pull_request run: https://github.com/Lian9374/smartfix/actions/runs/39761037404 .
- Tested branch commit: a36881c5282597d588605551e0ba3fdd1980d675. Subsequent documentation-only commits do not change the tested implementation.
- Event: pull_request; created 2026-10-22 18:10:01; completed 18:13:14; elapsed 3m13s.
- SWE5006 Build and Test: PASS, 18:10:04–18:12:27 (2m23s); Maven 2m06s. Surefire 526, Failsafe 277.
- SWE5006 PostgreSQL Integration: PASS, 18:10:04–18:13:14 (3m10s); Maven 2m44s. Surefire 526, Failsafe 454.
- Each suite reports zero failures, zero errors and zero skipped tests. PostgreSQL adds 177 native cases beyond the 277 default integration cases; the profile intentionally repeats default coverage.
- Both initial PR creation and later pushes produced real automatic pull_request runs. The check names are copied from the actual jobs, not inferred from the YAML.

## Artifact verification: PASS

All three successful-run artifacts were listed through the API, downloaded, extracted and inspected locally. They were not expired at verification.

- Build reports: artifact 12418670399, 409233 ZIP bytes; Surefire XML for 64 classes and Failsafe XML for 16 classes, plus build-verify.log.
- PostgreSQL reports: artifact 12417779088, 571979 ZIP bytes; Surefire XML for 64 classes and Failsafe XML for 34 classes, plus postgres-verify.log.
- JAR: artifact 12417509457, 69431448 ZIP bytes; extracted smartfix-0.0.1-SNAPSHOT.jar is 76434235 bytes. Manifest declares Java 21, Spring Boot 3.5.4, JarLauncher, and com.smartfix.SmartFixApplication as Start-Class. The application class and 78 nested dependencies are present.
- JAR SHA-256: CB9BB07D72666F034DA44A4D310C65A76B959E4BC82903272093ADCACC7D419A.
- The JAR was structurally verified; application startup against a separate runtime database was NOT RUN and is outside this packaging check.
- Failure evidence also works: run 39759343004 retained build and PostgreSQL report artifacts. The initial build ZIP was downloaded and inspected; it contains HomeControllerTests/UserManagementControllerTest XML and the Maven log. The first run had 524 Surefire cases with 10 genuine failures. The successful version has two additional navigation regression tests.
- Local backup: C:/Users/zhour/smartfix-swe5006-evidence/ . GitHub artifacts are configured for 30 days; this backup preserves downloaded evidence beyond expiry.

## Earlier failures and diagnosis

Actual run history is exported in run-history.json. Logs were read before changes were made. No earlier failed run is represented as a success.

- https://github.com/Lian9374/smartfix/actions/runs/39759343004 — FAIL: 10 MVC/Surefire failures, demonstrating failure propagation and report preservation.
- https://github.com/Lian9374/smartfix/actions/runs/39759762156 — FAIL: default IT fixtures/navigation and native reset/legacy fixture/context initialization issues.
- https://github.com/Lian9374/smartfix/actions/runs/39760430004 — FAIL: 2 default / 4 full-profile failures exposed contradictory role-navigation expectations after an initial production-navigation change. That change was reverted; the map contract now checks role-specific navigation and direct route access.
- Native reset failures identified SQLSTATE 23503 and delivery_tasks_recipient_id_fkey when deleting synthetic users before dependent outbox rows. Cleanup now follows dependency order only in isolated test schemas.
- The legacy recovery failure was Flyway's `Unexpected older migrations: manual review required.` before migration SQL: the supposed V24 fixture had applied V26/V27 and omitted V25. Explicit target(24) repairs the fixture. Actual migration safety/data-preservation/rollback tests all pass in the successful PostgreSQL run.
- SQLSTATE 42P07 messages for already-existing objects in legacy fixtures are warnings emitted by existing guarded historical DDL; they are not evidence of CI success on their own. Full migration, validation and behavioral assertions determine the result.
- Published migrations and the production recovery whitelist are unchanged. No flyway clean/repair, validation disabling, forced push, production database mutation, or main merge was performed.

## Events and settings not yet exercised

- Push to main trigger: NOT RUN for this new workflow; no authorized merge has occurred.
- workflow_dispatch: NOT RUN; this new workflow is not yet present on the default branch. After approved merge, open Actions -> SmartFix SWE5006 CI -> Run workflow -> main, and retain that run URL.
- Required-check enforcement: NOT RUN; active ruleset currently requires PRs but not status checks. Add the two exact job names using README.md after user approval. Review/merge blocking must then be verified on a later PR.
- Docker container build: NOT RUN; the initial Docker engine was unavailable. The Java/Maven and PostgreSQL verifications use their actual installed runtimes and GitHub service respectively.

Machine-readable exports: github-run.json, github-jobs.json, github-test-results.json, jar-verification.json and run-history.json. Test routing inventory: test-inventory.md.

## Local default verification: PASS

- Commit a36881c5282597d588605551e0ba3fdd1980d675, clean isolated worktree C:/Users/zhour/smartfix-swe5006-local-default/.
- Java 21.0.9 and Maven 3.9.16 on Windows 11.
- Executed `mvn -B --no-transfer-progress clean verify`; exit 0 / BUILD SUCCESS; elapsed 4m48s; finished 2026-10-22 18:15:39.
- Compile: PASS; Surefire/mvn test phase: PASS (526); Spring Boot package/repackage: PASS; default Failsafe and verify: PASS (277). All counts have zero failures/errors/skips.
- These phases were actually executed within the single verify lifecycle; separate redundant compile/test/package commands were not needed.
- Log: local-default-validated.log in the implementation worktree and copied evidence backup.

## Local PostgreSQL verification: PASS

- Same implementation commit a36881c5282597d588605551e0ba3fdd1980d675 in C:/Users/zhour/smartfix-swe5006-ci/ (only documentation changes pending).
- Executed `mvn -B --no-transfer-progress -Ppostgres-it verify`; exit 0 / BUILD SUCCESS; elapsed 6m43s; finished 2026-10-22 18:18:07.
- PostgreSQL 16.11 was started in a new task-owned cluster at C:/Users/zhour/smartfix-swe5006-pgdata/, listening only on 127.0.0.1:55466, with database smartfix_ci_test. No existing application database was used. The task-owned server was stopped after verification; its data directory is retained.
- Compile/test/package/verify: PASS. Surefire 526 and full Failsafe 454, all zero failures/errors/skips. This includes native migrations, legacy upgrade/rollback/data retention, constraints, real PostgreSQL concurrency and delivery tests.
- Log: local-postgres-validated.log in the implementation worktree and copied evidence backup.
- An initial restricted-sandbox baseline stalled at Mockito attachment and was stopped. Two superseded local PostgreSQL attempts were stopped after diagnosis/code updates; they are incomplete attempts, not PASS. The full completed runs above are the final local evidence.

## Demo readiness

The PR pipeline, real passing Maven/PostgreSQL runs, report downloads and executable JAR evidence are ready for classroom demonstration. Main enforcement and the post-merge push/manual-trigger demonstration still require the user's approved merge/settings actions; they are not represented as completed. No test classes were deleted or disabled; two unit regressions and direct map-access assertions were added.
