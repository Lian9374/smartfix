# ADR-006: Supported OSS runtime for blocking dependency checks

- Date: 2026-10-25
- Status: Proposed in PR #47; merge requires passing mandatory checks and review.

## Context

The first completed hosted Dependency-Check scan blocked the old Boot 3.5.4
dependency set. A local compatibility checkpoint at Boot 3.5.16 passed the
unchanged default Java suite. However, Framework 6.2.19 and Security 6.5.11 remain
affected by published advisories whose fixes on those branches require enterprise
support. Suppressing those vulnerabilities to make the pipeline green would not
provide the requested dependency security gate.

## Decision

Use the published Boot 4.0.9 maintenance release and its managed Framework 7.0.10,
Security 7.0.8, Hibernate 7.2.25 and Tomcat 11.0.27. Keep Java 21, Maven,
server-side sessions, Spring MVC/Thymeleaf, JPA and PostgreSQL. Pin Log4j's BOM
to 2.26.1 and pgJDBC to 42.7.14. No commercial repository, new account, scanner
suppression or paid resource is introduced.

Follow the official modular starter/package migration: Web MVC, Flyway and the
corresponding test starters; Jackson's ObjectMapper moves to tools.jackson.
Existing security tests retain their absolute login redirect contract using
Spring Security's documented LoginUrlAuthenticationEntryPoint compatibility
setting. Two isolated controller slices exclude the now-separate generated-user
auto-configuration in addition to their pre-existing security exclusion. Full
security/role tests continue to execute; none are removed or weakened.

The deployable remains an executable JAR, packaged with configuration into a ZIP
for the existing Beanstalk Java SE environment. Docker remains a separate
verification/container-test delivery path. No database migration or formal
environment change is performed as part of this dependency decision.

## Verification and consequences

All existing test layers, core coverage floors, High/Medium SpotBugs rules and
CVSS >=7 / known-exploited vulnerability rules remain blocking. Compilation alone
does not establish compatibility: full default tests, real PostgreSQL tests,
coverage, static/security scans and Docker runtime verification must pass for
this revision before release. Reports from the original 3.5.4 revision remain
historical evidence with their original identities.

Spring's major-version migration is an explicit review item in PR #47. A failed
test or scan must be fixed or left as a release blocker, never bypassed for a
recording. Existing running environments are untouched until separately
authorized deployment.

## Primary references

- https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
- https://docs.spring.io/spring-security/reference/6.5/migration-7/web.html
- https://spring.io/security/cve-2026-47884/
- https://spring.io/security/cve-2026-59270/
- https://logging.apache.org/security.html
