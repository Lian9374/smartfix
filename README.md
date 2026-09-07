# SmartFix — Campus Facility Maintenance and Technician Dispatch System

SmartFix is a web-based system that centralises how facility faults on campus are
reported, tracked, dispatched and resolved. It is being developed by a five-member
team in the SWE5006 *Designing Modern Software Systems Practice* module (NUS-ISS)
using Agile development and Jira + GitHub.

> **Important:** this repository currently contains the **initial SmartFix
> architecture and development scaffold**. Business functionality will be
> implemented incrementally through Agile sprints following detailed analysis and
> design.

---

## 1. Business problem

Facility faults are today reported through disconnected channels — phone calls,
email and informal chat groups. Maintenance information is hard to track, technician
assignment is delayed, and there is limited visibility of repair progress.

SmartFix will centralise fault reporting, maintenance-request tracking, technician
assignment, repair handling, SLA monitoring, facility-status visibility, feedback and
operational reporting, providing a structured workflow from initial fault report to
final closure.

## 2. Project overview

| | |
|---|---|
| What | Campus facility maintenance & technician dispatch (web) |
| Status | **Initial scaffold only** — engineering foundation, no business functions |
| Team size | 5 (Agile, ~10 weeks, Jira + GitHub) |
| Repository | `smartfix/` (Maven, one Spring Boot application) |

## 3. Current scaffold status

The scaffold proves the engineering foundation:

- Single Spring Boot application that **compiles, starts and renders** a minimal home
  page at `http://localhost:8080/`.
- Health endpoint via Spring Boot Actuator (`/actuator/health`).
- PostgreSQL for local development (Docker Compose) + an **isolated H2 test profile**
  so `mvn test` needs no external database.
- Flyway migration infrastructure wired up, with an **intentionally empty** baseline —
  no premature schema.
- **Temporary** security baseline that permits the scaffold pages (see §9) — real
  authentication/RBAC arrive in Sprint 2.
- Infrastructure tests (context load + home page render).
- Minimal Jenkinsfile (Checkout / Build / Unit Test / Package) with TODO markers for
  future DevSecOps stages.

The following are **deliberately not implemented yet**: authentication/SSO, RBAC
rules, maintenance-request CRUD, file upload, comments, feedback, reopen workflow,
request status-transition engine, work-order workflow, technician smart matching /
ranking (and the Strategy pattern), SLA calculation/scheduling/escalation,
notifications/email, campus-map provider integration, real-time streaming,
public/private facility authorization, dashboards, reports, announcement management,
full audit trail and the final database schema. Their future home is documented in
`docs/architecture.md`.

## 4. Official system roles

Per the approved proposal, SmartFix has **three** roles:

1. **Requester** — reports a facility issue (typically a student or staff member).
2. **Technician** — receives assigned work, carries out and records repairs.
3. **Administrator** — user administration, request review/classification/priority,
   SLA setup, technician assignment, monitoring, facility-status administration.

A separate *Facility Officer* role is intentionally **not** introduced; role
decomposition changes would be handled as documented requirement changes.

## 5. Planned functional modules

The future system is expected to cover these areas (implemented module-by-module in
later sprints, not now):

- **User & access management** (secure sign-in, RBAC, account admin)
- **Maintenance request management** (submission, tracking, history, comments, feedback)
- **Technician dispatch** (review, classification, prioritisation, SLA targets,
  assignment/override)
- **Work order & repair tracking** (acceptance, diagnosis, work performed, materials,
  completion evidence, audit history)
- **SLA monitoring** (deadline tracking, reminders, overdue detection, escalation)
- **Campus facility map & public status** (map display, public/private statuses)
- **Notifications** (in-app/email)
- **Reporting** (backlog, overdue, resolution time, SLA compliance, recurring faults,
  workload)

## 6. Technology stack

| Concern | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x (Spring MVC) |
| View layer | Thymeleaf (server-rendered — no React/Vue/Angular/Node) |
| Persistence | Spring Data JPA + Flyway |
| Database | PostgreSQL 16 (dev), H2 (isolated tests) |
| Validation | Jakarta Bean Validation |
| Security | Spring Security (scaffold baseline only) |
| Testing | JUnit 5, Mockito, Spring Boot Test |
| Build | Maven |
| Ops / CI | Docker, Docker Compose, Jenkins |

## 7. Architecture overview

**Modular Monolith + Layered Architecture.**

- One Spring Boot application / one deployable unit / one primary relational database.
- Source is organised **by business module** (`auth`, `user`, `request`, `workorder`,
  `dispatch`, `sla`, `facility`, `notification`, `reporting`, `announcement`,
  `audit`, plus `common`) and by **layer inside each module** (`controller`,
  `service`, `domain`, `repository`, `dto`) where required.
- Dependency direction is one way: **Controller → Service → Domain → Repository**.
  Controllers stay thin; views contain no business logic; services coordinate;
  repositories persist.
- Microservices / Kubernetes / distributed messaging are intentionally **not** used.

Full rationale, the module/layer map, dependency rules and the relationship between
ECB analysis and the implementation are in **[docs/architecture.md](docs/architecture.md)**.
The baseline decision is recorded in
**[docs/decisions/ADR-001-architecture-baseline.md](docs/decisions/ADR-001-architecture-baseline.md)**.

## 8. Local development requirements

- JDK 21
- Maven 3.9+
- Docker Desktop (Docker + Compose v2)
- Git

No local PostgreSQL installation is required.

### Start PostgreSQL (Docker Compose)

```bash
docker compose up -d db
```

This creates database `smartfix` with the user/password from your `.env` file or the
safe local defaults. To override, copy `.env.example` to `.env` and edit it —
**never commit `.env`**.

> Port conflict? If port `5432` is already used on your machine (for example a
> locally installed PostgreSQL), pick another host port and tell the app where the
> database is:
>
> ```bash
> DB_PORT=5433 docker compose up -d db
> mvn spring-boot:run -Dspring-boot.run.profiles=dev \
>   -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5433/smartfix"
> ```

### Run the application

```bash
# with the dev profile (SQL + DEBUG logging):
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Open <http://localhost:8080/>. Health: <http://localhost:8080/actuator/health>.

> If `5432` is already used by another PostgreSQL, change the mapped port in
> `docker-compose.yml` and the matching `DB_URL`.

### Run the application inside Docker (optional)

```bash
docker compose --profile app up --build
```

### Tests

```bash
mvn test
```

Tests run against an **isolated in-memory H2 database** (the `test` profile) — no
external database or per-developer configuration is required.

### Build the jar

```bash
mvn clean package
java -jar target/smartfix-0.0.1-SNAPSHOT.jar
```

## 9. Security status (scaffold only)

Spring Security is included, but full authentication has not been built yet. The
current configuration (`com.smartfix.auth.config.SecurityConfig`) is a **temporary
development baseline**: every request is permitted so the team can verify the
foundation. It contains no real credentials and no production flow.

**TODO (Sprint 2):** replace with real sign-in, password encoding, CSRF/session
handling and role-based rules for REQUESTER / TECHNICIAN / ADMINISTRATOR. External NUS
SSO integration is not yet confirmed and is not implemented.

## 10. Documentation

- `docs/architecture.md` — modular monolith + layers, dependency rules, ECB mapping
- `docs/development-guide.md` — setup, workflow, adding a Jira feature, testing, PRs
- `docs/decisions/ADR-001-architecture-baseline.md` — ADR for the baseline decision
- `CONTRIBUTING.md` — branch model, process, definition of done
- `.github/pull_request_template.md` — PR template
- `.env.example` — example environment variables

## 11. Git collaboration guidance

- `main` is the stable branch; work on short-lived branches
  (`feature/SCRUM-XX-…`, `fix/SCRUM-XX-…`, `chore/SCRUM-XX-…`) and merge via PRs.
- Follow the template and checklist in every PR; request a teammate review.
- Full guidance in `CONTRIBUTING.md` and `docs/development-guide.md`.

## 12. Current limitations

- No business functionality (see §3 list).
- Security is a permissive scaffold baseline, not production authentication.
- No database schema yet — the Flyway baseline is intentionally empty.
- CI runs unit tests only; DevSecOps stages are TODO markers until tools are
  configured.

## 13. Future sprint development notes

- **Sprint 1:** requirements, backlog, use cases, architecture, UI prototype.
- **Sprint 2:** authentication, role management, request submission, attachments.
- **Sprint 3:** ticket workflow, requester dashboard, status history, comments,
  campus map, public/private facility status.
- **Sprint 4:** technician matching, dispatch, technician workbench, SLA notifications.
- **Sprint 5:** reports, integration, security & performance testing, deployment,
  documentation, demo.

The scaffold deliberately leaves analysis-driven seams (e.g. request lifecycle
responsibility, technician-matching strategy) open so the team can decide them during
use-case and class design rather than coding ahead of the analysis.

---

**License / module:** educational project for SWE5006 (NUS-ISS). For team use.
