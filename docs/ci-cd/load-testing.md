# Reproducible SmartFix read-query load testing

## Scope and actual routes

The existing `tools/measure-userd-queries.mjs` measures isolated SQL plans in a
rollback transaction. It does not generate concurrent authenticated HTTP load.
This implementation reuses `tools/container_verify.py`, the Dockerfile's
`ci-runtime` target, and `deploy/docker/compose.verify.yml`; adds one load tool,
k6 1.8.2 (public tool image pinned by digest). No application rebuild occurs.
Formal deployment remains **JAR + configuration → ZIP → Elastic Beanstalk Java SE**.

These routes were checked against controllers, SecurityConfig and templates:

- `GET /campus-map/status`: any authenticated role; JSON building/fault counts,
  updated timestamp and unmapped request count. Assertion: actual field types,
  nonempty building catalogue and faults present in the managed fixture.
- `GET /requests/mine?page=0&size=20`: REQUESTER only; asserts actual
  `my-requests-title` HTML marker and the account's synthetic request title.
- `GET /community?q=smartfix-load-fixture&page=0&size=10`: authenticated;
  asserts `community-title` and matching synthetic question content.

Sources: `reporting/controller/CampusMapController.java`,
`request/controller/RequestQueryController.java`,
`community/controller/CommunityQuestionController.java`,
`auth/config/SecurityConfig.java`, under `src/main/java/com/smartfix/`.
Each VU has a distinct REQUESTER account. It obtains the real CSRF token from
`GET /login`, posts username/password/accountType/CSRF to `POST /login`, requires
the correct HTTP302 target and retains its session cookie across iterations.
Redirects are disabled for business queries, so a login page cannot count as a
successful list query. Status200 with missing/wrong content also counts as error.

## Targets fixed before execution

`perf/policy.json` was written before the first load attempt and is copied and
hashed into each run's plan. Initial engineering goals, chosen for interactive
read queries (not a measured production SLA): global **and every endpoint**
p95 ≤1000ms, p99 ≤2000ms; business/protocol errors <1%; at least100 measured
requests per endpoint; no authentication failures. Native k6 threshold failure
returns nonzero and fails this optional benchmark job. No `continue-on-error`.
Never change the policy during a run or lower it to relabel a failing result.

Three sequential profiles are independently recorded:

1. **smoke**:2 VUs ×2 iterations each (12 business requests). Zero errors;
   checks script correctness, not capacity. Its percentiles are diagnostic only.
2. **warmup**:5 VUs for30s. Errors/authentication remain checked; not included
   in the formal latency aggregate. A failed prerequisite stops later profiles.
3. **load**:default10 constant VUs for120s, with up to10s graceful completion.
   Each iteration reads map, own request list and community search, then sleeps1s.
   This is a closed concurrency model; slow responses reduce offered throughput.
   It is not a constant arrival-rate model or a search for maximum capacity.

Latency is k6 `http_req_duration` from each business response (server/network
receiving time, excludes blocked/connect time). Requests/s uses phase elapsed
time including initial logins. Health/auth/setup requests are separately counted
and excluded from business p95/p99/errors. No browser assets/rendering are tested.
Exact actual phase duration is reported; it may exceed nominal duration because
in-flight iterations complete. See [k6 thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/).

## One-command managed isolated run

Prerequisites:Python3.10+, Docker engine/Linux containers, Compose≥2.24.4;
access to public Java/PostgreSQL/k6 base images. No new Secret is needed.
Run from repository root; use a NEW output folder each time:

```powershell
python tools/run_load.py run --local-jar target/smartfix-0.0.1-SNAPSHOT.jar --output verification/load-001 --vus 10 --duration-seconds 120 --think-seconds 1
python tools/run_load.py cleanup --output verification/load-001
```

The local input is recorded as `LOCAL_WORKTREE_NOT_CI`, with base SHA, dirty
status and JAR SHA-256. It is not proof that uncommitted code ran on hosted CI.
In Actions use `--jar-dir incoming-jar` instead: it validates this run/attempt's
build metadata/hash and consumes the exact Maven-produced tested JAR.

The runner generates a random Compose project, isolated DB/upload volumes,
random database password, loopback-only random app port, and10 distinct accounts
(or the requested VU count). It uses real registration/CSRF/login/request/community
forms to create10 requests and3 questions **per account before measurement**.
Locations come from the actual form; guards and authentication are retained.
Only the disposable overlay raises registration-per-address to allow fixture
creation; it disables external notification delivery. Aggregate DB counts confirm
the fixtures. No production or preexisting application/database is targeted.

Resource limits are explicit:app2CPU/1GiB, PostgreSQL1CPU/512MiB, k6 load generator
1CPU/512MiB, Hikari pool10. `docker stats` samples app/DB CPU and memory;
`pg_stat_activity` samples total/active/idle client connections excluding the
monitor's own connection, roughly every5s plus collection overhead. Samples,
sampled maxima and collection errors are retained; these are not instantaneous
peak values or Hikari-internal metrics. Shared-host interference remains possible.

`finally` cleanup removes the uniquely named load generator, application/DB
containers, project network, fixture volumes and unique image tag. `cleanup` is
idempotent and is also an `if: always()` workflow step for interruptions. There
is no Docker prune and unrelated containers are untouched. If a process is
force-killed locally, execute the second command using the retained state.json.

## Configurable base URL and preprovisioned accounts

Managed runs intentionally do not accept a production target override. For a
different **isolated** environment, the same k6 script reads configuration from
`PERF_CONFIG`, policy from `PERF_POLICY`, and credentials from `PERF_ACCOUNTS`.
Defaults are `/input/config.json`, `/input/policy.json`, `/input/accounts.json`.
This advanced route performs only GET queries and login POSTs; it does not seed
or delete external data. Its operator must provision one REQUESTER per VU,
confirm local/isolation scope and clean up their accounts separately.

Example config (put outside repository/report folders):

```json
{"base_url":"http://app:8080","allowed_hosts":["app"],"profile":"load","vus":10,"duration_seconds":120,"think_seconds":1,"search_term":"smartfix-load-fixture","fixture_marker":"smartfix-load-fixture"}
```

Accounts file:JSON array of objects with `username` and `password`, one per VU.
Use synthetic accounts; do not commit or upload the file. Use profile `smoke`,
then `warmup`, then `load` with separate output folders. The supplied policy is
unchanged. Set `fixture_marker` to an empty string only for a separately prepared
dataset without that marker; HTTP/HTML/JSON assertions remain enabled.

```bash
docker run --rm --network YOUR_ISOLATED_NETWORK \
  --mount type=bind,src=/absolute/path/to/perf,dst=/scripts,readonly \
  --mount type=bind,src=/absolute/private/input,dst=/input,readonly \
  --mount type=bind,src=/absolute/writable/results,dst=/results \
  grafana/k6:1.8.2@sha256:bd6ae8607c0a05d0dc0dc3f5753c464b0929baf083ef0f20b7eed845a3d9e6b4 \
  run --out json=/results/points.json /scripts/read_queries.js
```

Output directory must be writable by the k6 image's user. Host must match the
explicit config allowlist; AWS/Beanstalk hostnames are refused. An allowlist is
not evidence of isolation: never point it at production/custom production domains.
This advanced external-target invocation is configured, **not executed as part
of the managed local benchmark**; collect its environment/version separately.

## Manual CI entry and reports

Existing workflow `SmartFix SWE5006 CI` →Run workflow:
`load_test=true`, `deploy=false`, `container_release=false`, `load_vus=10`,
`load_duration_seconds=120`. The modes are mutually exclusive.
`build-test + postgres-it → load-test`; same tested JAR downloaded, no Maven
rebuild. PRs/pushes do not run load. Benchmark failure fails its own job; formal
publication gates remain unchanged. It is **not a required PR/release gate**
because shared-runner variance and cross-environment baselines are not established.
Existing static/security failures can still fail the overall workflow; a passing
benchmark is not permission to publish/deploy. No workflow was remotely triggered
by this implementation.

Verification artifact:`swe5006-load-verification-RUN_ID-ATTEMPT` contains:

- `plan.json`, `policy.json`, `result.json`, `report.md`, input/image/run identity.
- Each profile's `points.json`(raw time series), `summary.json`(k6 aggregates and
  threshold decisions), `result.json`(normalized), `resources.json`(actual samples).
- Sanitized k6/container/command logs and cleanup receipts, including failed runs.

Credentials, cookies, CSRF tokens and response bodies are not report fields.
Managed account/config input exists only in a private temporary mount deleted
after execution. Raw metric tags omit URLs/account/session IDs; known generated
secrets are redacted from collected logs. No database dump/JAR is in this artifact.
Raw metrics can still describe the test environment: keep artifact access private
and use synthetic data. Local evidence is indexed in `load-validation.json` and
`evidence-index.md`. Actual results must always name environment/data/concurrency.
