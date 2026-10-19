# Verification evidence

All evidence below is local. No AWS resource was provisioned, no AWS deployment was executed, and no AWS CI/CD success is inferred from configuration files.

## Commands and actual outcomes

- Initial baseline: 524 unit tests, 10 failures. Reasons: HomeControllerTests lacked a mock for the new notification navigation query; UserManagementControllerTest had stale empty-state copy and a Principal incompatible with Spring Security advice. Those fixtures were corrected without weakening route or business authorization.
- Initial default integration run also exposed old technician notification fixtures, inconsistent navigation assumptions and PostgreSQL tests accidentally included without a database. Native PostgreSQL tests now run with `-Ppostgres-it`; all relevant checks remain enabled there.
- Real PostgreSQL run exposed V26/V27 operational rows missing from older fixture cleanup, a legacy-v24 fixture copying newer migrations, and a migration-page context missing SMTP configuration. These test fixtures were repaired; no existing schema migrations V1–V27 were edited.
- `mvn -B verify`: default unit/H2 integration suite passed before final review. Final release run `mvn -B -Ppostgres-it verify` passed **531 unit tests +454 integration tests**, with 0 failures/errors/skips; counts are recorded in `results/java-verification.json`.
- `python3 -m unittest discover -s tests -v` in `services/notification-worker`: 8 tests passed (validation, duplicate/colliding payloads, callback/SMTP failure, skip and acknowledgement ordering).
- `cfn-lint infra/aws/stack.json`: exit 0. This validates static structure, not actual AWS permissions, capacity, DNS or deployment behavior.
- Real Compose E2E submitted a request through authenticated CSRF-protected forms, reviewed/assigned it, accepted it, wrote a repair record, completed it and observed asynchronous completion notification. Attachment upload/download also passed. Latest run: `results/e2e-latest.json`.
- `scripts/swe5001/reliability.py`: replayed an already processed real event three times; notification count stayed 1; queue drained. `results/reliability.json`.
- `docker compose -f compose.swe5001.yml run --rm fault-tests`: real PostgreSQL + private Core HTTP checks passed. Missing/wrong token and mismatched task key were rejected; Core accepted a callback while intent remained PENDING; injected crash after callback rolled back Worker inbox and replay kept one notification; six concurrent inbox consumers executed one callback. `results/fault-tests.json`. The test models the state left by a publish/commit gap; it does not kill an actual Core process between SQS send and commit.

## Local scaling measurement, 19 October 2026

Machine: Apple M3 Pro, 11 logical CPUs, Docker memory 8,321,372,160 bytes. Every Core replica limited to 0.5 CPU and 1 GiB. Worker count fixed at 1; same two databases and queue. k6 runs 20-second ramps to 2, 5, 10 VUs, then 5-second ramp-down. Workflow uses a real login, CSRF page and maintenance submission. Raw results and sampled CPU/memory/queue depth are committed in `results/`.

- **1 Core:** 684 successful workflow iterations, 10.499 workflows/s; submission p95 388.847 ms; workflow and HTTP error rates 0. Mean Core CPU 32.06% of one CPU, max 49.95%; mean memory 534.89 MiB, max 542.8 MiB. Maximum sampled visible queue 6.
- **2 Cores:** 1,010 successful iterations, 15.533 workflows/s; submission p95 73.652 ms; workflow and HTTP error rates 0. Mean CPU per Core 24.21%, max 48.62%; mean memory per Core 520.47 MiB, max 552 MiB. Maximum sampled visible queue 5.
- Throughput increased about 48%; p95 decreased about 81% in this experiment. This is evidence of benefit under this workload, not a promise of linear scaling or production capacity.

The workload has a 0.2-second think time and progressively increasing concurrency. `iterations.rate` counts the maintenance workflow; `http_reqs.rate` counts all HTTP calls including login/form requests. Submission latency excludes the asynchronous completion delay. Docker CPU percentages are relative to one CPU (50% approximately saturates the 0.5-CPU limit). Queue CSV is sampled and may miss transient peaks.

## Limits and corrections

One run per count, fixed ascending order, increasing database size, warm JVM/cache differences and shared-host resources limit causal precision. Repeat longer runs with reversed/random order and controlled data before making capacity claims. Final review guards affect only legacy admin retry/drain and were added after measurement, without changing the exercised request/outbox hot path; the measured image id is in benchmark-summary.json.

An exploratory first load run failed because k6 cleared cookies between iterations while the script cached its login flag. Raw failed data is preserved under `results/initial-cookie-reset-failure/`; it is not used in the comparison. `noCookiesReset:true` fixes the harness. Both final threshold exit codes are 0. The benchmark now retains both scenarios even when a threshold fails, and returns failure afterwards.

The independent code review identified skipped-email retry incompatibility and an overly broad completion assertion. Distributed mode now rejects legacy retries of terminal SKIPPED receipts and prevents synchronous Core draining; the UI describes the limitation. E2E now requires the exact current-ticket completion message and asserts EXPECTED_CORE_COUNT when validating multiple replicas. AWS optional SMTP delivery, actual autoscaling and managed-service availability require separate runtime evidence.

## Final image acceptance

After the review fixes, the complete Java/PostgreSQL suite passed again (531 +454). Final images were rebuilt, then two Core and two Worker containers started healthy. `EXPECTED_CORE_COUNT=2 python3 scripts/swe5001/e2e.py` passed for ticket `SF-2026-001710`; the same authenticated browser reached `172.20.0.6:8080` and `172.20.0.9:8080`, and repeated attachment reads matched the uploaded bytes. The exact current-ticket completion notification arrived. Fault tests and gateway smoke passed again against these final containers. Raw E2E and fault records contain UTC timestamps; Singapore local time is UTC+8.


## GitHub-hosted CI evidence

[SWE5001 verification run 39227770132](https://github.com/Lian9374/smartfix/actions/runs/39227770132) completed **successfully** for code commit `3f38e97aca2a9806f5e7e2bbab45f390c8feaa3f`. The clean Ubuntu runner passed Java/PostgreSQL tests, Worker tests, CloudFormation lint, full container builds/startup, authenticated E2E, two Core +two Worker E2E, real HTTP/PostgreSQL fault tests and artifact upload. Machine-readable status/steps are in `results/github-ci.json`. A later documentation-only evidence commit records this run without repeating unchanged-code tests. This is CI evidence; AWS image publishing/deployment was not triggered.


## Follow-up local-only verification

The user selected local testing first and avoiding AWS expenditure. The existing two-Core/two-Worker environment was rechecked, with all four Prometheus targets UP. This found an uncovered nginx `/healthz` bug: the default upstream Host contained an underscore, and the route also targeted the business port rather than the management readiness endpoint. The new local verification command failed on HTTP 400 before the fix. nginx now forwards readiness to port 8081 with the original Host; the same verification command then validates the full workflow, shared state, HTTP/database fault cases and monitoring. The CI workflow now checks this route explicitly. See `results/local-verification.json` for the fresh local outcome. No AWS command was invoked and no AWS resource was created.
