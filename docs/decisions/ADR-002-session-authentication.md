# ADR-002: Sprint 2 session authentication and verification

- Status: Proposed — implemented on the B branch for team review; not a claim of team approval
- Date: 2026-09-20
- Scope: Sprint 2 category B; D11, D14–D16
- Base: category A commit `92e661b`

## Decision implemented on this branch

Use Spring Security form login and server-side sessions. A's UserService remains the
sole account access boundary. BCrypt comes from A's PasswordConfig.

Capture userId, role and securityVersion at login. ActiveAccountFilter runs after
SecurityContextHolderFilter and before CSRF/logout/authorization. Every authenticated
request reads the current account via UserService.getUserAccess. Disabled, missing,
changed-role or changed-version accounts lose their session before reaching a controller.
Database failures never permit the request. Password hashes are erased after authentication
and excluded from session serialization.

Default inactivity timeout: 30 minutes, configurable with SMARTFIX_SESSION_TIMEOUT.
Concurrent sessions are allowed; all are revoked on their next requests after an account
change. This sprint uses no distributed session store, JWT, SSO or public registration.
Cookies are HttpOnly, SameSite=Lax, and cookie-only session tracking is enabled.
SMARTFIX_SESSION_COOKIE_SECURE enables Secure cookies for HTTPS environments.

The route matrix follows plan section 13. Anonymous page access redirects to login;
wrong roles get 403. Resource ownership and its 404 semantics belong to D's
RequestAccessService and are reused by E. Error responses never render exception messages,
SQL, rejected inputs, password hashes or filesystem paths. Unknown MVC failures log only
a reference ID and exception type; detailed diagnostics must be investigated separately.

## Verification tooling

Add Maven Failsafe 3.5.3 so `*IT` tests actually run during `verify`.
AuthenticationFlowIT uses real services, BCrypt, persistence, templates and security
filters with a dedicated H2 fixture; one case uses a real embedded Tomcat HTTP session.
It does not prove PostgreSQL migration compatibility.

Provide opt-in `-Ppostgres-it` for MigrationIT, using an existing dedicated PostgreSQL
database with a name ending in `_test`. It requires explicit TEST_DB_* environment
variables and fails when configuration is missing. Each run creates and removes only
its own random schema. No Docker/Testcontainers dependency is required.

Testcontainers, JaCoCo, Checkstyle, Dependency-Check, Gitleaks and Trivy remain unconfigured.
No coverage percentage or security scan result is claimed. Their adoption and any
coverage threshold remain team decisions. PR merge strategy (D20) is unchanged.

## Consequences and follow-up

- Each authenticated request performs one account access query.
- Session revocation takes effect on the next request, not via push to an idle browser.
- C/D/E can consume SmartFixUserDetails.getUserId() without accepting a client actor ID.
- Shared Clock is UTC; displayed account timestamps use Asia/Singapore.
- CI and container changes should be reviewed separately from the authentication change,
  as requested by the Sprint plan.
- Revisit this decision if multiple application instances or different session limits are needed.

