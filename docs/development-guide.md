# SmartFix Development Guide

Practical guide for the five team members working on the repository.

---

## 1. Environment setup

Required tools (see README "Technology stack"):

| Tool          | Version / notes                                        |
|---------------|--------------------------------------------------------|
| JDK           | 21                                                     |
| Maven         | 3.9+                                                   |
| Docker        | Docker Desktop (with Docker Compose v2)                |
| Git           | any recent version                                     |
| IDE           | IntelliJ IDEA / Eclipse / VS Code (Java support)       |
| PostgreSQL    | not required locally — provided by Docker Compose      |

First-time checks:

```bash
java -version        # 21
mvn -version         # 3.9+
docker --version
docker compose version
```

No PostgreSQL install is needed: the H2 test profile covers `mvn test`, and Docker
Compose provides PostgreSQL for real local development runs.

## 2. Local development workflow

### Start the database

```bash
docker compose up -d db
```

This starts PostgreSQL 16 (`smartfix-db`) with the database/user/password from
`.env` (or the defaults in `docker-compose.yml` / `.env.example`). Port `5432` is
exposed on localhost.

### Run the application (dev profile)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Then open <http://localhost:8080/>. Health: <http://localhost:8080/actuator/health>.

No profile is required for basic local runs; the `dev` profile adds SQL logging and
`DEBUG` logging for `com.smartfix`.

### Run the full test suite

```bash
mvn test
```

Tests run against an **isolated in-memory H2 database** — no external database and no
per-developer configuration.

### Build a runnable jar

```bash
mvn clean package
# run it:
java -jar target/smartfix-0.0.1-SNAPSHOT.jar
```

### Run the application in Docker (optional)

```bash
docker compose --profile app up --build
```

## 3. Package responsibilities

See `docs/architecture.md` for the module/layer layout. Summary:

- **Business modules** under `com.smartfix.*`: `auth`, `user`, `request`, `workorder`,
  `dispatch`, `sla`, `facility`, `notification`, `reporting`, `announcement`, `audit`,
  plus `common` for shared, module-independent code.
- **Layers within a module:** `controller` (thin), `service` (application/business),
  `domain` (business concepts), `repository` (persistence), `dto` (boundary objects).
- Dependency direction is one way: Controller → Service → Domain → Repository.
  Views never contain business logic; controllers never touch the database.
- Create packages only when real code needs them.

Configuration lives in `src/main/resources/application*.yml`; database schema changes
are Flyway migrations in `src/main/resources/db/migration/V*.sql`.

## 4. How a new Jira feature (story) should be added

Follow this order — do not jump to implementation:

1. **Story → Use case.** Ensure the Jira story has a clear acceptance criterion.
2. **Analysis.** Produce/agree the ECB analysis, then the relevant sequence diagram(s).
3. **Design.** Produce the design class diagram / identify the domain model changes.
4. **Schema (if the domain changed).** Add a numbered Flyway migration, e.g.
   `V2__create_maintenance_request.sql`. Never hand-edit tables; migrations are the
   source of truth. Keep `ddl-auto: none`.
5. **Implement in the owning module** (or create a new module) using the layers above.
   Add DTOs at the presentation boundary rather than exposing entities.
6. **Unit tests.** Cover especially complex/stateful behaviour (transitions, rules,
   authorization). Keep infrastructure green with `mvn test`.
7. **Open a pull request** (see section 6) and link the Jira story.

Typical branch: `feature/SCRUM-XX-short-description`. Example:
`feature/SCRUM-21-request-submission`.

> Discipline: do **not** introduce design patterns before a concrete design problem
> exists (e.g. no Strategy pattern for technician matching yet), and do not solve
> stories from later sprints ahead of time.

## 5. Testing expectations

- Unit tests use **JUnit 5** + **Mockito**. Web-layer tests use MockMvc.
- The `test` Spring profile gives an isolated H2 database
  (`src/test/resources/application-test.yml`).
- Run the whole suite before opening/merging a PR:
  ```bash
  mvn test
  mvn clean package   # confirms the packaged jar builds
  ```
- Test naming: `@DisplayName("...")` in plain language describing expected behaviour.

Future unit testing will focus especially on: technician assignment rules, SLA
conditions, request state transitions, invalid transitions, and
authorization-sensitive behaviour.

## 6. Pull request workflow

The team uses Jira + GitHub with a simple branch model:

- `main` is the stable integration branch. Never commit directly to `main`.
- Short-lived branches: `feature/SCRUM-XX-...`, `fix/SCRUM-XX-...`,
  `chore/SCRUM-XX-...`.
- Open a PR using the template in `.github/pull_request_template.md`. Include the
  Jira issue key, summary, changes, testing performed and the checklist.
- Request review from at least one teammate; keep changes small and reviewable.

## 7. CI / Jenkins

The repository contains a minimal `Jenkinsfile` (Checkout → Build → Unit Test →
Package). Future stages (static analysis, dependency/security scanning, Docker build,
deployment) are added only once those tools are actually configured — see the TODO
comments in the file. Unit tests in CI use the H2 profile, so the CI agent needs no
database.
