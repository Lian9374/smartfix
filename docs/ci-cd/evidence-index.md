# SmartFix CI/CD evidence index

This index distinguishes configuration, actual execution, failed execution and
plans. Source files alone never establish a passed deployment/scan.

## Latest hosted publication checkpoint — 25 October

- Uploaded implementation: [PR #47](https://github.com/Lian9374/smartfix/pull/47).
- Original revision fd2d655, [run 40173111438, attempt 2](https://github.com/Lian9374/smartfix/actions/runs/40173111438):
  build/default tests PASS, PostgreSQL/core coverage PASS, Docker runtime PASS;
  static FAIL and completed dependency scan FAIL. ZIP/CD correctly skipped.
- NVD_API_KEY is now configured by the owner. Its value is never stored here.
- Remediation, finite static exceptions, local negative gate and dependency
  migration: [25 October record](../quality/remediation-20261025.md),
  [ADR-006](../decisions/ADR-006-supported-ci-dependencies.md).
- No AWS deployment or private image publication has been triggered. The
  following 24 October records remain historical evidence, not the latest run.

## A. Formal JAR/ZIP -> Elastic Beanstalk Java SE

- Implementation: .github/workflows/smartfix-ci.yml; deploy/beanstalk/ci.py,
  CI.md, Procfile/config/hooks. Same tested JAR wrapped into ZIP, no Maven rebuild.
- Previous local tooling checks: docs/ci-cd/beanstalk-pipeline-validation.md.
- Status of THIS change: formal path preserved; no AWS deployment attempted,
  no production resources changed, no new formal success record asserted.
- Historical local Docker CD evidence is not AWS/Beanstalk deployment evidence.

## B. Docker image release and test deployment

- Current implementation/precise scope: docs/ci-cd/container-release.md.
- Previous real local Docker build/runtime proof: docker-validation.json and
  C:/Users/zhour/SmartFix-Docker-Evidence-20261024/reports/container-final/result.json.
- Current refactored local verification and retain/cleanup proof:
  verification/container-release-local/result.json + cleanup-final.json.
  Executed PASS,13 actual HTTP checks; local dirty JAR origin, not hosted CI.
- Local synthetic registry fixture: verification/registry-protocol-fixture/.
  Actual Docker push FAILED, cleanup PASS; no digest and no SmartFix content.
- Read-only prerequisites audit: verification/container-release-prerequisites/.
  BLOCKED: current package query403, SpotBugs87 blockers, security NOT_COMPLETED.
- Unauthorized local release guard: verification/container-release-unauthorized/.
  Expected actual failure; no login/push. Synthetic guard tests are separate.
- New hosted private GHCR publication/digest pull/test deploy/artifact uploads:
  CONFIGURED ONLY, NOT RUN. Must be described as “临时容器环境中的部署验证”.
- Current validation summary: container-release-validation.json. Exported reports
  live under C:/Users/zhour/SmartFix-Container-Release-Evidence-20261024/reports/.

## C. Tests and quality

- docs/quality/coverage-baseline.json and validation.json: measured baseline,
  final local Maven/Surefire/Failsafe results, coverage PASS, static FAIL and
  dependency scan NOT_COMPLETED, with exact scope/hashes.
- docs/quality/static-findings.json:87 still-blocking Medium findings +6 Low.
- C:/Users/zhour/SmartFix-Quality-Evidence-20261024/smartfix-quality-reports.zip:
  actual reports/negative gates; no fabricated successful vulnerability result.
- New unit tests validate release controls using synthetic metadata, not live
  GitHub credentials or a successful hosted publication.

## D. Optional isolated read-query load benchmark

- Implementation and fixed pre-run targets: `load-testing.md`, `perf/policy.json`,
  `perf/read_queries.js`, `perf/compose.load.yml`, `tools/run_load.py`.
- Actual local result: `load-validation.json`; exported machine-readable/raw
  evidence and Markdown report:
  `C:/Users/zhour/SmartFix-Load-Evidence-20261024/reports/load-attempt-03/`.
- EXECUTED PASS:2VU script smoke,5VU/30s warmup,10VU/120s formal load;
  3366 measured business queries,0 observed errors;17 formal thresholds passed.
- Failed attempts01(fixture redirect assertion) and02(k6 non-load threshold
  syntax) are preserved; neither executed formal load. Every project cleanup PASS.
- Source is a LOCAL_WORKTREE_NOT_CI dirty JAR, checksum recorded. Results are
  local Docker Desktop performance, not AWS performance or deployment success.
- Manual workflow `load_test=true` and external isolated-target k6 config are
  CONFIGURED, NOT EXECUTED remotely. No required PR/performance release gate added.

Planned work includes resolving existing quality blockers, granting an
existing private package access and observing an authorized real workflow run.
There is no claimed persistent test environment, paid resource or formal rollout.
