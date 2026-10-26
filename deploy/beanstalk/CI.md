# SmartFix CI and opt-in Java SE Beanstalk CD

For failed control-plane delivery, use the separate manual
[same-role S3 diagnostics](../../docs/ci-cd/beanstalk-delivery-diagnostics.md)
before another deployment. It never updates the running environment.

Quality gates now extend this release path: see [quality rules and real results](../../docs/quality/README.md).
For the formal path, Docker is a mandatory verification check. A separate
[container release and temporary test deployment path](../../docs/ci-cd/container-release.md)
is selected explicitly and never deploys to the formal environment.
An additional [manual isolated load benchmark](../../docs/ci-cd/load-testing.md)
uses `load_test=true`, with both deployment switches false. It reuses the tested
JAR after build-test + postgres-it, runs smoke/warmup/load, always exports
`swe5006-load-verification-RUN_ID-ATTEMPT`, and cleans its temporary project.
It is not part of the PR/release requirements; no AWS target is used. The three
optional modes (formal deploy, private container release, load test) are mutually
exclusive. Normal container verification is skipped only in the two alternative
container-release/load modes to avoid duplicate image builds. All existing
quality requirements remain in force for publishing/deployment.

The existing `.github/workflows/smartfix-ci.yml` keeps its two Maven jobs and
passes one verified JAR to Java SE ZIP packaging. Existing Java test frameworks
are reused. No ECS, Docker Beanstalk platform or deployment-time Maven build
is introduced. Existing local deployment files are included in this branch;
checking out or validating this branch does not modify AWS.

## Triggers and dependencies

- PR targeting main: build/test, PostgreSQL integration, quality checks, Docker
  runtime verification and ZIP packaging; no deployment.
- Push main: the same validation/packaging, **no AWS deployment**.
- Existing workflow_dispatch on main: validation/packaging by default.
  Formal deployment requires deploy=true and container_release=false.
- Container release requires container_release=true and deploy=false. It selects
  container-publish -> container-test-deploy instead of the formal ZIP/CD path;
  its private package permissions are isolated from ordinary PR validation.
- Dispatch on another branch and fork repositories never deploy.

```text
build-test -----------+
  release-tool tests  |
  Maven clean verify  +--> package-eb --> deploy-eb --> post-deploy
postgres-it ----------+       |          explicit          |
  existing full profile       ZIP        manual main       final result
```

package-eb now also requires static-analysis, dependency-security and
container-verification. The container job needs both existing Maven jobs and
reuses build-test's JAR without Maven rebuilding. Core
coverage is enforced in postgres-it. deploy-eb explicitly needs all five
required checks and package-eb. Post-deploy
needs deploy-eb and package-eb. Default success semantics prevent a skipped or
failed prerequisite from allowing deployment. No continue-on-error is used.
The existing workflow concurrency group serializes the entire main run,
including post-deploy checks. Obsolete PR runs can still be cancelled.
The full postgres-it profile intentionally repeats default tests; it is retained
rather than introducing duplicate compile/test/package jobs for appearance.

## Build versus verification artifacts

Names below end in `<run_id>-<run_attempt>`; retention is 30 days.

Build artifacts:

- `swe5006-jar-*`: app.jar copied byte-for-byte from the sole Maven-produced
  target/smartfix-*.jar, plus build-metadata.json.
- `swe5006-beanstalk-zip-*`: deployment ZIP and release-metadata.json.

Verification artifacts:

- `swe5006-build-reports-*`: sanitized Surefire/default Failsafe XML, inventory,
  Maven log and Python release-tool test log.
- `swe5006-postgres-reports-*`: sanitized full-profile XML, inventory and Maven
  log; not another deployable JAR.
- `swe5006-package-reports-*`: packaging metadata or failure diagnostic.
- `swe5006-deployment-reports-*`: phase receipt and safe failure diagnostic.
- `swe5006-post-deploy-reports-*`: version/health/HTTP result or safe failure.
- `swe5006-default-coverage-*`, `swe5006-core-coverage-*`, `swe5006-spotbugs-*`,
  `swe5006-dependency-security-*`: actual quality reports/gate outcomes described
  in docs/quality/README.md, including explicit failed/incomplete scans.
- `swe5006-container-verification-*`: JAR/image provenance, real HTTP assertion
  outcomes, sanitized Docker logs and scoped cleanup results in normal mode.
- Separate container mode: swe5006-container-release-*, container-publish-reports-*
  and container-test-deployment-* record actual private publication/digest test
  deployment outcomes, not formal deployment. See the linked container guide.

Reports/log upload uses always() after earlier failures. Bash pipefail preserves
Maven/Python failures through redaction/tee. Missing XML is explicitly marked as
absent, not passing. Malformed XML fails preparation after retaining other valid
reports/logs. A JAR may be uploaded by build-test before the PG result is known;
package-eb and deploy-eb require BOTH jobs to pass before proceeding.

JUnit properties and captured app stdout/stderr are removed from exported XML;
failure elements/counts remain. Known sensitive environment values, password/token
assignments, JDBC userinfo and email addresses are redacted from logs. CI uses
synthetic users and an isolated PostgreSQL service, never real customer data or
production DB credentials. Redaction is additional protection, not permission to
test against production. Raw XML/logs, workspace-wide archives, environment dumps,
HTTP response bodies, headers, cookies and CSRF tokens are not uploaded.

## Same JAR and provenance

After Maven verifies, ci.py records commit SHA, repository, workflow, run URL,
run ID/attempt, unique application version and JAR SHA-256. For PR events the SHA
is the tested merge ref. package-eb downloads only this run/attempt's JAR,
validates provenance/hash and wraps the unchanged bytes with an explicit allowlist:
Procfile, prod YAML, Nginx config, environment/EFS hooks, generated configuration
hooks and the official public RDS CA. It never invokes Maven or recursively
archives .env, uploads, target, DB dumps, private keys or credentials.

Release metadata additionally records ZIP SHA-256 and bundle-file hashes. The CA
URL/hash are recorded; download failure fails packaging. Deploy rechecks the ZIP
and embedded JAR hashes; S3 must confirm the upload checksum. S3 object metadata
also records commit/run and JAR/ZIP hashes.

Version format: `smartfix-<first 12 commit chars>-<run_id>-<run_attempt>`.
Rerun **all jobs** for a new attempt: cross-attempt artifacts/metadata are refused.
build.ps1 remains a local convenience. Its UseVerifiedJar/mtime mechanism is not
CI provenance; the workflow does not call that script.

## Configure before the first authorized deployment

Configure GitHub environment `beanstalk` and restrict deployment branches to main.
The workflow and helper also require repository Lian9374/smartfix,
workflow_dispatch, refs/heads/main and explicit deploy=true. Existing main rules
were not changed; maintainers should require existing Maven jobs, quality gates,
SWE5006 Docker Runtime Verification and SWE5006 Beanstalk ZIP, and actually test
merge blocking on a failing PR.

Use repository/environment variables for these non-secret settings:

- AWS_REGION: region of the existing resources.
- EB_APPLICATION: existing application name, e.g. smartfix.
- EB_ENVIRONMENT: existing environment name, e.g. smartfix-prod.
- EB_S3_BUCKET: existing source-bundle bucket in that region.
- EB_BASE_URL: final HTTPS origin, without credentials/path/query/fragment.
- EB_ALLOW_HTTP_VERIFY: defaults to false. The owner explicitly authorized true
  for the existing HTTP-only demonstration endpoint on 25 October. Only anonymous
  health/login-page GETs are made, redirects are refused, and the HTTP hostname
  must match CNAME from authenticated Beanstalk metadata. No credentials are sent.
  Reports label transport=HTTP and https_transport_verified=false; this is not
  TLS validation. Keep the default false for an HTTPS deployment.
- AWS_BEANSTALK_ROLE_ARN: OIDC deployment role ARN.
- Optional AWS_BEANSTALK_VERIFY_ROLE_ARN: read-only verification role; otherwise
  verification obtains a fresh session of the deployment role.

OIDC trust must constrain audience to sts.amazonaws.com and the exact repository
environment subject, together with environment branch restrictions to main.
Read `GET /repos/Lian9374/smartfix/actions/oidc/customization/sub` before creating
trust: this repository currently reports `use_immutable_subject=true` and prefix
`repo:Lian9374@139187520/smartfix@1360254409`. Its environment subject is therefore
`repo:Lian9374@139187520/smartfix@1360254409:environment:beanstalk`, rather than the
older name-only example. Do not change the repository subject settings to fit
an outdated trust policy. No long-lived AWS key or runtime DB password is required
in Actions. See [GitHub's OIDC guidance](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws).
Deployment permissions must cover the actual existing application/environment
and bucket prefix smartfix/releases/: upload/read source objects, describe the
environment, create an application version and update the environment, plus any
platform-specific dependent permissions. Verification needs environment and
instance-health reads. Service/instance-role access to bundles/runtime resources
is configured separately. Verify least privilege in an authorized cloud run;
local mocked tests cannot prove IAM permissions.

Authorized preparation on 25 October created the `beanstalk` GitHub environment
with a main-only deployment branch policy, its verified target variables and a
dedicated GitHub OIDC role. Access Analyzer reported no policy findings after
review; IAM permission simulation allowed updating the existing SmartFix
environment and denied another environment. This establishes configuration and
policy-simulation evidence, not a successful OIDC exchange, deployment, or
post-deployment verification. No actual deployment was requested by the owner.

Runtime DB/SMTP credentials and EFS settings remain in Beanstalk's runtime
configuration/secret mechanism. The workflow never reads/archives that environment
configuration. AWS CLI stdout/stderr is captured; safe diagnostics contain the API
operation/error code rather than raw request/environment output. ci.py does not
create roles, buckets, VPCs, databases, EFS, listeners, security groups or environments.

After this workflow is reviewed and present on main, Run workflow with main and
deploy=true is the explicit deployment request. The checkbox defaults to false.
This implementation task has not requested or triggered such a deployment, and
has not changed GitHub variables/environments, roles or repository settings.

## Deployment result versus post-deploy result

deploy-eb validates config and artifact identity, refuses an unexpected application,
non-Ready environment or non-Corretto-21 platform, uploads ZIP, creates an EB
Application Version and requests update of the existing environment. Its receipt
says UPDATE_REQUESTED, not PASS.

post-deploy waits up to 600 seconds for exact VersionLabel, Ready AND Green health;
checks every instance Deployment.Status/version; then requires HTTPS health 200
and UP plus a 200 login HTML form. Redirects are not followed. Failures/timeouts
return nonzero and retain receipts. The login page is anonymous smoke, not an
authenticated business acceptance test. No automatic app rollback or DB downgrade
is implemented or claimed.

The prior audit found memory Warning and only HTTP on the current AWS environment.
These are not bypassed. Resolve resource health and configure HTTPS before
expecting post-deploy to pass. No EFS replacement persistence, load results,
Docker publication or full acceptance are claimed. Coverage/static results and
the incomplete dependency scan are documented separately in docs/quality/README.md.
Jenkins and the separate local-Docker/swe5001 branches are not this release path.

## Local checks without deployment

```text
python -m unittest discover -s deploy/beanstalk -p test_ci.py -v
actionlint -shellcheck= -pyflakes= .github/workflows/smartfix-ci.yml
mvn -B --no-transfer-progress clean verify
```

The Python tests use generated fixture JARs and mocked AWS/HTTP, not real user data
or AWS mutations. They exercise provenance/tampering, trust gates, ZIP structure,
S3 checksum, receipts, health/version failures and sanitized report preservation.
The existing native PostgreSQL profile needs a dedicated *_test database or the
GitHub service. Never use production. Local validation status is documented in
docs/ci-cd/beanstalk-pipeline-validation.md; historical evidence.md does not prove
this revised workflow ran in GitHub.

References: [AWS Java SE](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-platform.html),
[source-bundle deployment](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/environment-configuration-methods-after.html).
