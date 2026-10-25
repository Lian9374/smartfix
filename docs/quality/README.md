# SmartFix quality gates

> Publication update (25 October): the implementation is now uploaded in
> [PR #47](https://github.com/Lian9374/smartfix/pull/47). A real hosted run has
> executed, NVD_API_KEY is configured, and the first complete dependency scan
> failed on genuine dependency findings. Static/dependency remediation and exact
> execution scope are tracked in [the remediation record](remediation-20261025.md)
> and [ADR-006](../decisions/ADR-006-supported-ci-dependencies.md). The sections
> below retain the original 24 October local baseline; their original no-push /
> not-executed statements describe that historical checkpoint.

Implementation worktree: C:/Users/zhour/smartfix-quality-gates, branch
ci/quality-gates. Includes the preceding uncommitted Beanstalk workflow changes
without modifying the original developer checkout or previous worktree. No push,
workflow dispatch, production database operation or AWS deployment was performed.

## A. Tools and scope

The repository had no enabled coverage/static/dependency scanner. Jenkins only
contained a disabled Security placeholder. No Sonar/Checkstyle/PMD/Snyk duplicate
was introduced. Existing JUnit, Mockito, Spring tests, Surefire, Failsafe and the
full postgres-it profile remain intact; no test was removed or disabled.

- JaCoCo Maven 0.8.14: Java-21-compatible version already in the local Maven
  cache, pinned for reproducible measurement. Runtime agent covers Surefire and
  Failsafe; report includes all production classes without exclusions.
- SpotBugs Maven 4.10.5.0 / engine 4.10.5: all application bytecode, Effort Max,
  report threshold Low, block High/Medium, zero allowed blocking violations.
  Main/test classes are not confused: application bytecode is analyzed; test code
  remains subject to test execution. CI reuses the verified JAR's classes instead
  of adding another compilation/test job.
- OWASP Dependency-Check Maven 13.0.0: Maven dependency vulnerability checking,
  compile/runtime dependency scope, including transitive dependencies. Declared
  primary sources are NVD plus CISA KEV; optional account-backed OSS Index is not
  enabled. This initial source selection is not a CVE suppression/waiver. No
  vulnerability suppression file is configured. This is not a claim to scan
  Maven plugin dependencies, arbitrary vendored JavaScript, containers or DAST.
- tools/quality.py validates generated reports, provenance, database freshness and
  gate outcomes; it is not a second scanner. Existing release log redaction was
  extended to NVD_API_KEY and API-key-labelled messages.

## B. Gate rules and baseline rationale

Test gates: existing Maven verify fails on Surefire/Failsafe failures. Both jobs
remain mandatory. There is no continue-on-error. Coverage is measured by the same
tests, not a second invocation just to inflate coverage.

Coverage was measured BEFORE source fixes and before setting limits:

- Default suite: Surefire 526 + default Failsafe 277, all zero failures/errors/
  skips. Project LINE 89.2989%, BRANCH 66.9500%.
- Full dedicated PostgreSQL suite: Surefire 526 + Failsafe 454, all zero
  failures/errors/skips. Project LINE 93.0709%, BRANCH 74.2210%.
- Native tests raise SLA service coverage from LINE 9.80% / BRANCH 2.04% to
  LINE 90.20% / BRANCH 58.16% (exact counters are in coverage-baseline.json).
  Therefore the full PostgreSQL job, not the default report, is the primary gate.

config/quality/coverage-policy.json sets project LINE >=93% and BRANCH >=74%, plus
separate LINE/BRANCH minima for every service package and auth/security. Minima
are the measured ratios rounded down to whole percentage points: less than one
percentage point of explicit rounding allowance, not a claim of exact zero
regression. No business class/package is removed from measurement. Branchless
announcement/service has a line rule, not a fictional branch ratio. Missing
sessions, reports, packages or counters fail; new service packages require an
explicit policy review. CI never regenerates the policy from the current result.
Baseline hashes and counters are retained in coverage-baseline.json. Raise
thresholds when coverage improves; do not automatically lower them to pass.
Existing gaps such as SLA/audit branch coverage remain visible improvement work.

Static gate: report all Low/Medium/High findings; reject every High or Medium
finding (priority <=2), engine errors, missing classes, empty/incomplete reports
or zero analyzed classes. Low findings remain in reports/backlog. No blanket
baseline suppression or package exclusion is used. XML-only output avoids a
multi-format reporter statistics issue observed during local validation; the
same findings/severity rules remain. It does not hide the 87 existing blockers.

Dependency gate: native failBuildOnCVSS=7.0, failOnError=true; autoUpdate=true,
NVD cache checked within 4 hours. Additional report guard rejects analysis
exceptions, missing/empty reports, stale/missing/future-invalid NVD metadata,
known-exploited vulnerabilities at any CVSS, and findings without usable scores.
A failed/skipped native scanner can never be labelled PASS by a leftover clean
report: the Actions step outcome is passed to the report guard. A fresh usable
cache can be used within 4 hours; a missing/stale database is never accepted.

CI dependency graph:

The graph below describes normal validation/formal deployment mode. The separate
container_release dispatch requires all four test/quality jobs before its own
image verification/publication, then digest-only deployment on a fresh temporary
runner. See [container release](../ci-cd/container-release.md); existing static/
security failures also block that path.

```text
build-test -> static-analysis --+
build-test --------------------+
postgres-it + core coverage ----+--> package-eb --> deploy-eb --> post-deploy
dependency-security -----------+
container-verification --------+  (needs both Maven jobs; same JAR)
```

package-eb and deploy-eb explicitly require both existing jobs plus static-analysis
and dependency-security, plus the subsequent mandatory Docker container check
documented in [Docker verification](../ci-cd/docker-verification.md).
The former reuses the original JAR; the latter still
requires manual main dispatch with deploy=true. No deployment branch or runtime
architecture changed. Current existing blockers intentionally prevent publishing
the release ZIP/deployment; a test-passing JAR artifact alone does not permit CD.

## Existing findings: treatment, not blanket approval

Safely fixed in this change:

- AssignmentService: capture the nullable constraint name once before checking
  it, preserving conflict detection while removing an unsafe repeated getter.
- AttachmentInspectionService: express the non-null filename invariant for
  directory entries with Objects.requireNonNull, preserving existing behavior.
- UserManagementController: use the exact form object returned by the creation
  helper instead of casting a potentially absent model lookup.

One narrowly scoped exception:

- SE_TRANSIENT_FIELD_NOT_RESTORED only for
  SmartFixUserDetails.passwordHash. Restoring a credential hash from a serialized
  session is intentionally forbidden. The new serialization test proves the hash
  is omitted while identity/role/securityVersion are preserved. The exact
  class+field+pattern filter is in spotbugs-exclude.xml. Revisit it if session
  principal design changes; no other serialization warnings are waived.

Deferred, still blocking (no exception): 87 Medium findings. Most are mutable
reference exposure in Spring/JPA boundaries; changing all constructors/entities
to defensive copies/final classes can alter transaction/proxy behavior. They
require individual ownership/encapsulation decisions, not bulk suppression.
Constructor finalizer-attack warnings, authentication-detail equality and dynamic
legacy migration SQL also require focused review. The full per-finding source
locations and reasons are in static-findings.json. Do not label all of these
false positives without review. Six Low findings remain visible but are outside
the predeclared blocking confidence threshold.

Security findings cannot yet be triaged: no complete live vulnerability scan was
obtained without NVD access. Do not invent CVEs, claim zero vulnerabilities or
upgrade dependencies blindly to create a green result.

## C. Actual report locations / Actions artifacts

- target/site/jacoco/index.html, jacoco.xml, jacoco.csv: all-source coverage.
- verification/coverage/final-gate.json: local final primary gate result;
  CI writes verification/coverage/gate.json.
- target/spotbugsXml.xml: actual final static findings and complete analysis stats.
- verification/static/final-gate.json locally; CI writes static/gate.json.
- target/dependency-check/dependency-check-report.html / .json: only when the
  native dependency scanner actually generates them. They are not fabricated
  when the scanner cannot initialize.
- verification/security/preflight.json and gate.json: explicit prerequisite /
  incomplete-scan status. ci-logs holds sanitized diagnostics.

New Actions verification artifacts (suffix run_id-run_attempt):

- swe5006-default-coverage-* (report only, not the primary coverage gate).
- swe5006-core-coverage-* (full-profile report plus gate JSON).
- swe5006-spotbugs-* (XML, provenance, findings/status JSON and log).
- swe5006-dependency-security-* (actual native reports if generated; otherwise
  explicit failure status and diagnostics).

Always-upload steps preserve reports after failure. Missing reports/failed scans
are never replaced by empty success reports. Existing test/build/release artifact
separation and 30-day retention remain. Local copies of the actual reports are
also exported under C:/Users/zhour/SmartFix-Quality-Evidence-20261024/reports/.

## D. Real local execution versus external execution

See validation.json for measured results, command exits and hashes.

- Baseline default and full PG tests: PASS, counts above.
- Final normal application: Surefire 527 (one serialization regression added),
  Failsafe 454; failures/errors/skipped all zero; Maven exit 0, 06:36.
- Final coverage guard: PASS with the original measured policy unchanged.
- Final SpotBugs: COMPLETED, 295 classes, 0 analysis errors, 0 missing classes;
  93 findings = 87 Medium + 6 Low; native exit 1 and gate FAIL.
- Dependency plugin resolution/help: PASS. Actual deliberately empty API-key /
  empty-database execution: exit 1, UpdateException / NoDataException. This is
  failure-path evidence, NOT a successful vulnerability scan.
- Missing-secret preflight: exit 1, scan_status NOT_RUN, gate FAIL.
- Python release tests 24 and quality-guard tests 20: PASS, synthetic reports and
  credentials only. These are not live NVD or AWS evidence.
- actionlint / XML / Python syntax / whitespace checks: recorded in validation.json.

Deliberate negative cases were isolated in this temporary quality worktree:

1. A temporary external policy requiring project 100% coverage returned exit 1
   against the real report. The temporary policy was removed; the tracked
   measured policy was never lowered or replaced.
2. A temporary GateFailureProbe class containing a definite null dereference
   generated priority-1 NP_ALWAYS_NULL. SpotBugs returned exit 1 and increased
   blocking findings from 91 to 92 at that point. The source/class were deleted;
   final clean compilation/tests and final static XML contain no probe.

The dedicated loopback PostgreSQL 16 cluster on 55467 used only a new *_test DB
and synthetic fixtures. It was stopped after final validation. No production DB,
AWS resource, repository settings or Secret values were inspected or modified.
GitHub execution, artifact uploads and actual release blocking remain to be
observed on a real Actions run; local static checks do not prove hosted behavior.

## E. Outstanding external configuration / release prerequisites

- New Secret: NVD_API_KEY — NVD API access for database initialization/updates.
  Only its name/purpose is documented; no value is requested, printed or archived.
  Maven uses nvdApiKeyEnvironmentVariable, never -DnvdApiKey=<value> or debug dumps.
- Allow Maven Central/NVD/CISA data access; first database initialization can take
  20+ minutes. No complete vulnerability result is claimed until it succeeds.
- Fork PRs do not receive Secrets; their security gate fails closed. Do not use
  pull_request_target to expose credentials to untrusted PR code. Validate in a
  trusted branch context with the authorized secret before release.
- Existing Beanstalk environment/OIDC variables remain as documented in
  deploy/beanstalk/CI.md. No AWS deployment was attempted.
- Observe real Actions checks, then configure main required checks for existing
  build/test, PG/coverage, SpotBugs and dependency security (and ZIP packaging).
  Repository rules were not changed; a failed workflow alone is not proof of a
  protected merge policy.
- Review/fix the 87 static blockers and run/triage the dependency scan. These
  are release prerequisites, not waived historical debt.

References: [JaCoCo Maven](https://www.jacoco.org/jacoco/trunk/doc/maven.html),
[SpotBugs check](https://spotbugs.github.io/spotbugs-maven-plugin/check-mojo.html),
[Dependency-Check parameters](https://dependency-check.github.io/DependencyCheck/dependency-check-maven/check-mojo.html).
