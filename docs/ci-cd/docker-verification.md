# SmartFix disposable Docker verification

Formal deployment remains **JAR + configuration -> ZIP -> AWS Elastic Beanstalk
Java SE**. The image is **verification only, not part of formal deployment**.
This page records the initial local-verification implementation and its dated
evidence. The subsequent [container release and temporary test deployment path](container-release.md)
adds private publication configuration; it does not change formal deployment.
At the initial audit no image publication workflow existed. Current package
permission checks, failed transport attempts and execution gaps are recorded in
the new guide and [evidence index](evidence-index.md).

## Files and execution path

- Dockerfile: preserves the existing local source-build default target, shares
  its Java 21/non-root runtime, and adds `ci-runtime` to COPY an existing app.jar.
  BuildKit targets only that stage; it does not execute Maven/source stages.
- docker-compose.yml: existing PostgreSQL 16 + application structure, dependency
  healthcheck and networking are reused.
- deploy/docker/compose.verify.yml: verification-only overlay; Compose >=2.24.4
  required. Removes fixed container names/build settings/DB host port; replaces
  env and mounts with synthetic settings and project-scoped disposable volumes.
  The application binds only 127.0.0.1 on an automatically allocated port.
- tools/container_verify.py: standard-library harness; no second HTTP test tool
  or production authentication bypass is added.
- tools/test_container_verify.py: guard regression tests, not runtime evidence.
- .github/workflows/smartfix-ci.yml: `SWE5006 Docker Runtime Verification`, after
  both existing Maven checks. ZIP packaging and deployment require this job plus
  all existing required checks. No continue-on-error is configured.

CI downloads `swe5006-jar-<run_id>-<run_attempt>`. Existing provenance validation
checks repository, tested commit (PR merge ref when applicable), workflow/run/
attempt and JAR SHA-256. Only this JAR enters a temporary Docker build context.
There is no Maven command in the container harness. Image tags are
`smartfix-verification:sha-<full_commit>-<unique_run_suffix>`; the OCI revision
label must match. The report records image ID (local content-addressed identity),
repository digests if present, input/run identity, input JAR SHA-256, running
container JAR SHA-256 and non-root UID. A local image ID is not a published
registry manifest digest. `published=false` and `registry_digest=null` explicitly
record that no registry publication occurred.

## Actual runtime assertions

1. PostgreSQL readiness: pg_isready every 3 seconds, 3-second attempt timeout,
   30 retries / 5-second start period; Compose startup has a 180-second deadline.
2. Application readiness: GET /actuator/health must return HTTP 200 and JSON
   status UP within 180 seconds; retry interval 2 seconds, per-call timeout <=5s.
   Build/Compose/Docker CLI operations also have bounded timeouts; job max25min.
3. Check /app/app.jar in the running app equals the input hash; runtime UID !=0.
4. Anonymous /requests/mine must redirect to /login.
5. GET registration form and obtain real session CSRF token; POST creates a
   random, isolated REQUESTER via normal application code in the new test DB.
6. GET login form; wrong password must redirect to the application's expected
   /login?error&accountType=REQUESTER. Missing-CSRF login must return403.
7. Correct credentials + real CSRF must redirect to /. The subsequent protected
   /requests/mine must return200 with the real page identity, zero request count
   and expected empty state, rather than silently following a login redirect.
8. Filtered request query (SUBMITTED, page0, size5) must return200 with the page
   identity and filtered empty state; REQUESTER /admin/requests must return403.
9. POST logout with a new valid CSRF token; protected list must redirect again.

Every status/redirect/content assertion is blocking. HTTP redirects are not
automatically followed, avoiding false success from a200 login/error page.
This is a small smoke test of authentication/session/CSRF/RBAC and empty request
query behavior, not full repair lifecycle, non-empty result, browser JavaScript,
load testing, or formal environment acceptance.

## Collection and cleanup

`verification/container/result.json` separates build_status, runtime_status and
overall status. A successful build alone cannot mark runtime PASS. Missing
engine/report, timeout or assertion failure returns nonzero. Failure before any
resource creation explicitly records NOT_RUN for build/runtime/cleanup.

Python finally collects app+DB container logs (last2000 lines per service), safe
container state and diagnostics. Generated credentials/identity, cookies, CSRF
tokens, sensitive environment values and labelled secrets are redacted. HTTP
bodies/headers/cookie jars, interpolated env files and full Docker inspect dumps
are not archived. Accounts and fixtures never come from a real user database.

Finally runs `compose down --volumes --remove-orphans --timeout10`, verifies zero
containers/networks/volumes with this run's exact Compose project label, and
removes only its exact generated image tag. Cleanup failure is blocking. A
separate Actions always() step repeats idempotent scoped cleanup if Python was
interrupted, followed by always() upload of
`swe5006-container-verification-<run_id>-<run_attempt>` (30-day retention).
No global prune, unrelated resource removal or registry operation is used.
Shared base images/BuildKit caches remain Docker-managed; no shared cache is
pruned. Temporary build contexts and database/upload volumes are removed.
Runner hard termination cannot execute finally/always(); the GitHub-hosted
runner's ephemeral teardown is the remaining protection in that case.

## Local / hosted evidence, 2026-10-24

- Python guard tests:15 passed, including a real bounded child-process timeout,
  HTTP mismatch, wrong content/redirect,
  readiness timeout/retry, missing CSRF, checksum mismatch, secret redaction and
  leftover-resource cleanup failure. These use synthetic responses/credentials.
- Real Compose2.40.3 configuration validation:PASS. The merged model was checked
  for no DB host port, loopback random application port, no build/fixed names,
  synthetic DB/bootstrap-disabled env and project-scoped volumes. This does not
  prove container startup.
- Actionlint1.7.12:PASS (external shellcheck/pyflakes disabled).
- Initial sandbox Docker harness invocation:FAIL at docker-preflight; build NOT_RUN,
  runtime NOT_RUN, no image ID/digest and no HTTP checks. Ordinary sandbox access
  was denied; external Docker Desktop startup also failed. Its backend log
  reported an inaccessible dockerInference IPC listener; the Linux engine was
  initially unavailable. No Docker data reset/settings change was performed. An attempted
  rename of that single stale IPC file failed; it was not modified.
- Subsequent real Docker execution outside the sandbox:PASS. Engine29.1.3,
  Compose2.40.3. The tested-JAR target built successfully, app+PostgreSQL started,
  health returned200/UP after9 attempts, and all13 real HTTP smoke assertions
  passed. Runtime UID999 and running JAR SHA-256 matched the verified input.
  Sanitized container diagnostics collected successfully. Cleanup returned0 and
  verified0 project containers/networks/volumes and removed the exact image tag.
  First successful local image ID was
  sha256:ef97ea011a67f9512a692c94270cf2a1ab6356d4c41146607fc7220a245ed98d.
- Final hardened-harness rerun:PASS again,13/13 HTTP assertions, health200/UP
  after9 attempts, UID999, input/running JAR hashes identical, diagnostics and
  cleanup PASS. Final image ID:
  sha256:af1b4fc870b922b676ca75904d7c2369b76023f1d17662880999e26e5f8109d2;
  tag smartfix-verification:sha-7e91ce0a2fdc11279aa4f15218facf986abcb41a-ba98e6daacee.
  Logs and metadata remain after the generated image tag was removed.
- The local input is the previously tested worktree JAR, SHA-256
  `53e2fbfd0d7bd39966d3e9e9dc5f2782cbe40f867bf6bd159e8a63eae75259fe`.
  Base commit7e91ce0a2fdc11279aa4f15218facf986abcb41a, worktree_dirty=true;
  origin LOCAL_WORKTREE_NOT_CI. It is not presented as a hosted CI artifact.
- GitHub Docker build/runtime/upload behavior has not yet been observed. The
  successful local Docker run is actual local runtime proof, not hosted Actions
  evidence or formal-environment deployment proof.
- Independent host-only HTTP protocol validation:PASS using that same JAR and
  a newly initialized loopback-only PostgreSQL16 database. Health returned200/UP
  after6 attempts; all13 smoke assertions passed. The application and database
  were stopped and the temporary directory removed. This proves the smoke code
  agrees with actual application HTTP behavior; it does NOT prove image build,
  container startup, Compose networking/volume behavior or container cleanup.
  An initial sandbox attempt was interrupted after native process management
  stalled; its exact task processes/data were also cleaned. The successful
  host-only rerun used file-backed native-process diagnostics rather than pipes.

Exported real evidence (including final hardened-harness rerun) is under
C:/Users/zhour/SmartFix-Docker-Evidence-20261024/reports/ and in
smartfix-docker-verification-evidence.zip. It separates actual successful Docker
runs from the initial failed preflight and host-only protocol/config/guard checks.

Commands on a usable Docker host:

```powershell
# Explicit local mode records dirty/source context instead of fabricating CI provenance.
python tools/container_verify.py run --local-jar target/smartfix-0.0.1-SNAPSHOT.jar --output verification/container-local-new
python tools/container_verify.py cleanup --output verification/container-local-new
```

For CI use the configured job or dispatch with deploy=false. No new Secret is
required for disposable Docker testing. Existing SpotBugs/security blockers still
block release; they are not waived by this implementation. Observe a real hosted
Actions run and its uploaded artifact before claiming hosted CI execution in the
presentation. Local Docker runtime, local host-only protocol checks, hosted
Actions execution and actual AWS deployment remain distinct evidence scopes.
