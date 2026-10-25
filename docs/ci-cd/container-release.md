# Container release and test deployment / 容器发布与测试部署

This is a separate path from formal Java SE Elastic Beanstalk deployment.
任何临时 runner 上的容器测试都必须标注为 **“临时容器环境中的部署验证”**，
不能称为持久测试环境、正式上线或 Beanstalk 部署成功。

## A. Current formal JAR/ZIP path (preserved)

The normal PR/main validation path remains build-test + full PostgreSQL tests /
coverage + SpotBugs + dependency security + ordinary Docker runtime verification
-> package-eb (same JAR + configuration -> ZIP).

Formal CD still requires workflow_dispatch on main with deploy=true and
container_release=false, then deploy-eb -> post-deploy (Java SE Beanstalk version,
Green health and HTTPS assertions). It does not use the container registry,
ECS or Docker Beanstalk. No formal environment was modified or deployed in this
change. See deploy/beanstalk/CI.md and existing validation records; historical
local Docker deployment evidence is not proof of this AWS release path.

## B. Container release and test deployment path

Explicit dispatch on main with **container_release=true, deploy=false**:

```text
build-test -----------+
postgres-it/coverage -+--> container-publish ----------------> container-test-deploy
static-analysis ------+    preflight checks/private package    NEW ephemeral runner
dependency-security --+    same tested JAR -> image build      release.json identity
                           isolated PG +13 HTTP checks        private registry login
                           retain that passing local image    pull repo@sha256:digest
                           tag sha-<full source commit>        NO image/Maven build
                           push existing private GHCR package  fresh PG +13 HTTP checks
                           confirm registry digest/visibility  logs/results + cleanup
                           release.json + local cleanup
```

The ordinary container-verification job and privileged container-publish job
are mutually exclusive. The former validates PRs/pushes/default dispatches with
read-only repository permissions. The latter reuses the same harness but only
runs for the explicit trusted main release after ALL four required Maven/quality
jobs pass. This avoids double image builds in one run and avoids transporting
private image tar archives through artifacts on the public source repository.
Selecting both formal deployment and container release fails the input guard.
In container-release mode, the formal ZIP/deploy path is skipped intentionally.

Publication does not rebuild the image after verification. `--retain-image`
retains only the successful local image; containers/networks/DB/upload data are
already cleaned. The publisher requires the same image ID, OCI source revision,
JAR label, input/running JAR SHA-256,13 passing HTTP assertions and CI provenance.
The final always-cleanup step deletes the retained image on success OR failure.

The publisher additionally queries the CURRENT GitHub run/attempt and verifies
source SHA, main branch, workflow_dispatch event and workflow path. Exactly one
completed successful result is required for each job:

- SWE5006 Build and Test
- SWE5006 PostgreSQL Integration (includes coverage gate)
- SWE5006 SpotBugs Gate
- SWE5006 Dependency Security Gate

Missing, failed, skipped, duplicate or incomplete results fail closed before any
Docker login/push. Local worktree test evidence cannot authorize CI publication.

### Private registry policy and external configuration

Set repository variable **CONTAINER_IMAGE** to the existing private package's
repository, e.g. ghcr.io/lian9374/<existing-private-package> (no tag/digest).
There is no automatic default package creation. The API must return an existing
container package owned by Lian9374 with visibility exactly private. Public,
internal,404,403, missing token, network error and malformed metadata all fail
before push. Visibility is checked again after push; code never changes it.

The source repository is public; its visibility is not used to assume package
privacy. Existing private package Actions access must grant Lian9374/smartfix
write for publication and read for test deployment. The implementation uses the
automatic short-lived **GITHUB_TOKEN**, exposed as CONTAINER_GITHUB_TOKEN only to
necessary steps. Publish permissions: contents:read, actions:read, packages:write;
test deployment: contents:read, packages:read. PRs receive no package-writing job.
No new long-lived registry Secret is added. Metadata API permission must actually
work with that package/token; failure remains blocking rather than being waived.

Docker login uses password-stdin; credentials are stored only under a generated
temporary DOCKER_CONFIG and removed in finally. Tokens never appear in command
arguments, image layers, release metadata or artifacts. Only the tested JAR enters
the minimal build context. No private image archive is uploaded as a workflow
artifact. No visibility mutation, registry creation, public push or paid resource
creation is included.

### Digest, provenance and deployment gates

The native Docker push must succeed and return one sha256 registry digest. The
publisher queries the remote registry descriptor with buildx imagetools inspect
and requires the same digest. A local image ID/RepoDigests from an unpushed image
cannot substitute for this evidence. Tag: sha-<40-character commit>. A rerun may
move that tag; deployments always use the immutable digest stored in release.json.

The receipt records commit SHA, workflow/run URL, run ID/attempt, application
version, required successful job IDs, verification receipt SHA-256, JAR SHA-256,
verified local image ID, image tag, registry digest and canonical repo@digest.
If push succeeds but later confirmation/cleanup fails, publish-result.json records
the partial observed push and failure. No successful release.json is produced.
No remote package/version is deleted as an attempted rollback.

The next job runs on a NEW GitHub-hosted Ubuntu24.04 runner. It downloads only
release.json, revalidates this run's provenance and private package visibility,
logs in read-only, checks the remote digest and executes docker pull repo@digest.
There is no Maven/Docker build in tools/container_release.py or the deployment
job. It verifies the pulled OCI revision/JAR labels and the running JAR hash.
The shared Compose overlay starts a new test DB and random loopback app port.
Health must return200/UP within180 seconds, then all13 real registration/login/
CSRF/query/RBAC/logout assertions must pass. Any mismatch fails the job.

Reports explicitly say EPHEMERAL_CI_RUNNER_NOT_PERSISTENT_NOT_PRODUCTION and
“临时容器环境中的部署验证”. The deployment receipt connects its commit/run, image
tag/digest, publication-receipt hash, actual image, running JAR hash and test
results. Finally plus an Actions always() step clean only that project's
containers, networks, DB/upload volumes and pulled reference. A pre-existing
cached image reference is preserved. Shared base images/build cache are not
globally pruned. Diagnostics are redacted and uploaded on success/failure;
HTTP bodies, cookies, CSRF tokens, credentials and user data are not archived.

### Files and artifact names

- .github/workflows/smartfix-ci.yml: dispatch modes, dependencies, scoped package
  permissions, always collection and cleanup.
- Dockerfile: shared Java21 runtime; OCI revision + JAR-SHA labels.
- tools/container_verify.py + deploy/docker/compose.verify.yml: reused runtime,
  business smoke checks, retain/remove image lifecycle and isolated test DB.
- tools/container_release.py: preflight, publish, test-deploy; no build commands.
- tools/test_container_release.py: synthetic privacy/gate/provenance regressions.
- tools/check_registry_transport.py: opt-in local transport integration with a
  SYNTHETIC public-base marker image only; never reads/copies the SmartFix JAR.

Artifacts (suffix <run_id>-<run_attempt>,30-day retention):

- swe5006-container-release-*: successful release.json, no image archive.
- swe5006-container-publish-reports-*: preflight, actual verification outcome,
  publish-result.json, sanitized logs and cleanup results, including failures.
- swe5006-container-test-deployment-*: actual digest pull/deployment receipt,
  HTTP assertions, sanitized container logs and cleanup proof.

## C. Evidence and status,2026-10-24

**Executed successfully:**

- Refactored SmartFix local Docker build/runtime verification with previously
  tested JAR53e2fbfd0d7bd39966d3e9e9dc5f2782cbe40f867bf6bd159e8a63eae75259fe.
  Health200/UP after9 attempts,13/13 HTTP assertions, UID999, running/input JAR
  hashes identical. The image includes the new JAR-SHA label. Retain-for-release
  behavior passed, followed by explicit cleanup PASS:0 containers/networks/volumes
  and retained image removed. It is LOCAL_WORKTREE_NOT_CI with dirty=true, not an
  authorized publication artifact. No quality gate was bypassed for publication.
- 32 combined container/release guard tests passed (synthetic reports only),
  Actionlint1.7.12 passed (external shellcheck/pyflakes disabled), syntax/dependency
  checks passed. Guards cover failed quality jobs, wrong run/commit,403/public
  packages, mutable tags, digest mismatches and required smoke outcomes.

**Executed and failed/blocked (not success):**

- Current CLI authenticated as Lian9374; packages query returned403: missing
  read:packages. Package existence/private visibility/write permission cannot
  be confirmed. Repository admin permission is not package permission.
- Existing actual quality evidence still has87 Medium SpotBugs blockers and no
  completed dependency vulnerability scan. Both block SmartFix publication.
- Local preflight without authorized Actions context returned1 and explicit
  FAIL. Unit tests also prove failed mandatory checks never reach login/push.
- Local synthetic registry transport test: marker image build/test and registry
  startup succeeded (host-side HTTP readiness200), Docker push FAILED with a
  daemon connection-refused error to127.0.0.1's host-published port. No registry digest was
  obtained, no digest pull was executed. Cleanup passed, removing fixture image
  references, registry container/tmpfs data and its network. No daemon insecure-
  registry settings were modified. This is not SmartFix release/deployment proof.

**Configured only, not executed:**

- SmartFix GHCR push and private visibility confirmation; registry digest is null.
- SmartFix digest-only deployment and business smoke in a fresh CI runner.
- Real hosted dispatch, artifact uploads, selected package GITHUB_TOKEN grants.
- The formal Beanstalk deployment was not run by this change.

**External prerequisites / next authorized validation:**

- Supply/confirm CONTAINER_IMAGE for an existing PRIVATE package and grant the
  source repo read/write Actions access; current CLI lacks read:packages.
- Fix/review existing SpotBugs blockers and complete dependency scanning (existing
  NVD_API_KEY requirement remains). No new blanket exception was introduced.
- Apply these changes to main through normal review, then run container_release
  with deploy=false. Only the actual pushed digest/deployment artifact can prove
  this full hosted path. No persistent environment provisioning is planned here.
- The optional loopback registry fixture can be validated on a native Linux
  Docker host; its Docker Desktop loopback failure remains recorded.

Evidence index: docs/ci-cd/evidence-index.md and container-release-validation.json.

Primary references: [GitHub Container registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry),
[package API](https://docs.github.com/en/rest/packages/packages#get-a-package-for-a-user),
[remote manifest inspection](https://docs.docker.com/reference/cli/docker/buildx/imagetools/inspect/).
