# SmartFix Database Guide — PostgreSQL + Flyway

This guide covers local PostgreSQL, Docker, environment variables, and — most
importantly — how to evolve the schema **safely** with Flyway.

> The application currently has **no business schema**. The only migration,
> `V1__baseline.sql`, is intentionally empty. This guide explains the process you will
> use from the first real schema change onward.

---

## 1. Why Flyway and not JPA `ddl-auto`

`spring.jpa.hibernate.ddl-auto` is **`none`** everywhere. The database schema is created
and versioned **only by Flyway** migrations. Why?

- Schema changes are **reviewed** (they travel in PRs like code).
- The schema is **versioned and reproducible** on every developer's machine and in CI.
- Nobody can accidentally let Hibernate invent tables/columns that contradict the
  reviewed design.
- The full SmartFix schema will be introduced **only after domain modelling and class
  design**, story by story — never as a guess.

## 2. PostgreSQL for local development

### Docker (normal way)

```bash
docker compose up -d db
```

- Container `smartfix-db`, PostgreSQL 16.
- Database/user/password come from `.env` (copy `.env.example` → `.env`) or the safe
  defaults in `docker-compose.yml` (`smartfix` / `smartfix` / `smartfix`).
- The host port is configurable: `DB_PORT=5433 docker compose up -d db` if `5432` is busy.

### Environment variables

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | JDBC URL the app connects to | `jdbc:postgresql://localhost:5432/smartfix` |
| `DB_USERNAME` | Database user | `smartfix` |
| `DB_PASSWORD` | Database password | `smartfix` |
| `DB_PORT` | Host port for the Docker PostgreSQL | `5432` |

`application.yml` reads these with local defaults. On your own machine you may override
them in `.env` / the shell — never by committing secrets.

## 3. Flyway philosophy

- One migration = one versioned `.sql` file under
  `src/main/resources/db/migration/`.
- Migrations run **once, in version order**; Flyway records them in
  `flyway_schema_history`.
- Once a migration has been applied by **anyone** (a teammate, CI, or you), it is
  treated as **immutable history**.

## 4. Migration naming

```
V<version>__<snake_case_description>.sql
```

Examples:

```text
V1__baseline.sql                      # exists, intentionally empty
V2__create_user.sql                   # future
V3__create_maintenance_request.sql    # future
```

Rules:
- Version numbers are **integers, strictly increasing, never reused**.
- Two underscores separate the version from the description.
- Descriptions are short and meaningful (`create_user`, `add_feedback_table`,
  `alter_request_add_priority`).

## 5. Migration ownership and coordination

- **Migration owner:** the person whose story changes the data model creates the
  migration and owns it in their PR.
- **Before choosing the next version number:** `git pull` latest `main` and check
  `db/migration/`. If a teammate has already added `V3`, your new file is `V4`, not a
  second `V3`.
- Two PRs both introducing `V3__…` will **conflict on purpose**. Resolve by
  renumbering one to the next free version — never by merging two files with the same
  number.

## 6. How the schema evolves (normal case)

The only safe way to change an already-applied schema is a **new, additive migration**:

```text
V2__create_user.sql        # applied
V3__add_user_status.sql    # later change: ALTER TABLE ... ADD COLUMN status ...
```

**Never** go back and edit `V2__create_user.sql` after it has been applied elsewhere.
Hibernate never creates tables; the migration files are the single source of truth.

## 7. Common operations

### Add a new table (future)

```sql
-- V4__create_work_order.sql
CREATE TABLE work_order (
    id              BIGSERIAL PRIMARY KEY,
    request_id      BIGINT NOT NULL REFERENCES maintenance_request(id),
    technician_id   BIGINT NOT NULL REFERENCES users(id),
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
```

Keep names/schema aligned with the team's approved domain/class design — do not invent a
"final" schema ahead of design.

### Change a column safely

```sql
-- V5__alter_request_add_priority.sql
ALTER TABLE maintenance_request ADD COLUMN priority VARCHAR(16);

-- Review with a teammate before anything destructive:
-- ALTER TABLE x DROP COLUMN y;   <-- needs extra care and team review
```

Destructive SQL (`DROP`, `TRUNCATE`, `DROP COLUMN`) must be **reviewed** and only ever
shipped in its own clearly-described migration.

## 8. Migration conflict — what to do

Symptom: `git merge main` reports a conflict in a `V*.sql` file, or CI fails with a
duplicate-version error.

1. Do **not** delete or overwrite either migration.
2. Renumber one migration to the next free version (e.g. yours becomes `V4`).
3. If two migrations both create/alter the same table, reconcile their intent — talk to
   the other author.
4. Re-run `mvn test` (test profile) and, if you can, `docker compose up -d db` +
   `mvn spring-boot:run` against PostgreSQL to prove the migrations apply in order.

## 9. Local database reset (developer-only)

A **local-only** reset is sometimes the fastest way to recover from a mess on *your*
machine. It destroys **your local** database volume only.

```bash
docker compose down -v        # remove the container AND its data volume
docker compose up -d db       # fresh database, migrations re-run from V1
```

⚠️ This deletes local data. Never run `down -v` against a **shared** environment. And it
is **never** a substitute for writing a corrective migration when a teammate is affected:
deleting your DB resets *you*, but everyone else still needs a proper `V<n+1>` migration.

## 10. Shared-database safety

- There is no shared "production" database yet. When one exists later (deployment
  sprint), treat it as sacred: no destructive SQL without review, no resets, and
  migrations must always be **additive and backward compatible** enough to run against
  existing data.
- Every migration should be runnable from a clean checkout (`docker compose down -v`
  then `up`) **and** from an existing populated database. Test both where feasible.

## 11. Reviewing destructive SQL — checklist

Before merging any migration that drops or changes columns:

- [ ] Does it need to exist at all, or can it be deferred?
- [ ] Are dependent code/views/queries updated in the same PR?
- [ ] Does it lose data that matters? (Back it up / stage it first where applicable.)
- [ ] Has a teammate reviewed it?
- [ ] Does it run cleanly from a fresh DB *and* an existing DB?

## 12. H2 in tests vs PostgreSQL

Tests run against **H2 in PostgreSQL mode** (`src/test/resources/application-test.yml`)
so `mvn test` needs no database. Flyway is disabled in the test profile for now. This is
fast and isolated, but H2 is **not** PostgreSQL (README §29, `docs/testing-guide.md`).
As real schema/queries arrive, verify PostgreSQL-specific behaviour with a real
PostgreSQL (e.g. Testcontainers — added deliberately later, not now).
