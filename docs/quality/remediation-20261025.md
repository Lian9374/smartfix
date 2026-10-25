# Quality remediation and hosted execution — 25 October 2026

This supplements the historical 24 October baseline; it does not replace that
baseline with results from another version. No AWS deployment or image publication
is authorized or claimed by this work.

## Hosted execution of the original quality branch

PR #47: https://github.com/Lian9374/smartfix/pull/47

Run 40173111438, attempt 2, source fd2d655:
https://github.com/Lian9374/smartfix/actions/runs/40173111438

- Build/default tests, full PostgreSQL profile/coverage and Docker runtime smoke
  verification actually passed on GitHub-hosted runners.
- SpotBugs failed with 87 Medium and 6 Low findings.
- After the repository owner configured NVD_API_KEY, Dependency-Check completed
  and found blocking dependency vulnerabilities. This is a failed security scan,
  not a missing-credential result and not a successful security clearance.
- ZIP packaging and deployment were blocked/skipped. Deployment was not requested.

## Static analysis remediation

57 Medium findings were addressed in code: collection snapshots own their input
structure and expose unmodifiable collections; technician form sets use defensive
copies; the upload view receives only immutable limits; domain/factory validation
executes before entity construction; two non-proxied configuration/helper classes
are final; account-detail equality explicitly preserves superclass semantics.

30 additional narrowly scoped rules are documented in
`config/quality/spotbugs-reviewed-exceptions.json`. They cover 30 Medium and two
Low findings: Spring-managed dependency identity, mutable nested configuration
binding, and quoted PostgreSQL sequence identifiers in two allowlisted migration
methods. Exact class/method/field/bug patterns are required. No package, business
class or vulnerability is broadly excluded. The original password-hash session
exception remains separate. Changes to these inputs require re-review.

Local checkpoint with Boot 3.5.16: `clean verify` passed with 554 Surefire and
277 default Failsafe cases, zero failures/errors/skips. SpotBugs then passed with
zero High/Medium and four non-blocking Low findings. These are local compatibility
results, not evidence that the upgraded dependency set passed a security scan.

Negative gate validation on the isolated ci/quality-gates branch: a temporary,
untracked `GateFailureProbe` produced a definite null dereference. The unchanged
native SpotBugs gate returned exit 1 with one blocking finding. Its source and
class were removed in a finally block; no probe is part of the application JAR.
Logs and summaries are under ignored `verification/publish-preflight/`.

## Dependency remediation

The latest publicly available Boot 3.5 maintenance release still uses Framework
6.2.19 and Security 6.5.11. Spring's advisories list later security fixes for these
branches as enterprise-only. There is no CVE suppression, threshold reduction or
commercial repository credential workaround.

An upgrade to supported OSS components must be reviewed and fully tested before
merge. Java 21 and the executable JAR/ZIP -> Beanstalk Java SE architecture stay
unchanged. Relevant primary references:

- https://spring.io/security/cve-2026-47884/
- https://spring.io/security/cve-2026-59270/
- https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide

Further execution results must identify their actual source commit and run;
configuration alone is never recorded as passed execution.

Local Boot 4.0.9 checkpoint: full `mvn verify` completed successfully, reporting
557 Surefire and 277 default Failsafe executions with zero failures/errors/skips.
The Surefire XML contains 557 testcase elements but its declared root counters
sum to 556; retain this distinction when aggregating the raw report. SpotBugs
passed with zero High/Medium and four Low findings. The unchanged session-rotation
assertion passes with an explicitly valid requested-session fixture. Thymeleaf
JavaScript rendering retains Jackson 2 Java-time support; HTTP JSON/catalogue
mapping uses Boot's Jackson 3 mapper. Two new tests verify that encoder failures
cannot partially change a password or its session security version.

The NVD data cache contains public vulnerability data only. Every run still
executes the scanner with updates enabled and enforces its four-hour database
freshness requirement. Restoring a cache never counts as a successful scan.
