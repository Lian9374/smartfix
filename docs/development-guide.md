# SmartFix Development Guide — Daily Developer Manual

This is the beginner-friendly manual for working on the SmartFix repository day to day.
It assumes you may still be learning Git, Spring Boot and collaborative development, so
commands are explicit and dangerous operations are called out. For the *rules* in
depth, see the linked guides; this page is about *what to type*.

- Architecture and where code lives → [`docs/architecture.md`](architecture.md) and
  [`docs/module-guide.md`](module-guide.md)
- Git safety (how not to destroy the repo) → [`docs/git-safety-guide.md`](git-safety-guide.md)
- Database / Flyway → [`docs/database-guide.md`](database-guide.md)
- Testing → [`docs/testing-guide.md`](testing-guide.md)
- Problems → [`docs/troubleshooting.md`](troubleshooting.md)
- Jira → GitHub process → [`docs/team-workflow.md`](team-workflow.md)

---

## 1. First-time setup

### 1.1 Install and check prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | Docker Desktop (Compose v2) | `docker --version`, `docker compose version` |
| Git | any recent | `git --version` |

If a check fails, install the missing tool first (see `docs/troubleshooting.md` for
common setup problems). You do **not** need to install PostgreSQL — Docker Compose
provides it.

### 1.2 Clone

```bash
git clone <repository-url>   # URL from the GitHub "Code ▾ → Clone" button
cd smartfix
```

### 1.3 Create your local environment file (never shared)

```bash
cp .env.example .env
```

Edit `.env` only if you need different values. `.env` is git-ignored and **must never be
committed**. The committed `.env.example` shows which variables exist.

### 1.4 Start the database

```bash
docker compose up -d db
```

PostgreSQL starts in a container named `smartfix-db`, healthy on host port `5432`. If
`5432` is already used on your machine, run:

```bash
DB_PORT=5433 docker compose up -d db
```

and point the app at that port when you run it (see §1.5). Verify:

```bash
docker compose ps          # db should show "healthy"
```

### 1.5 Run the application

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- Home page: <http://localhost:8080/>
- Health: <http://localhost:8080/actuator/health>

Port `8080` occupied? Run on another port:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--server.port=8081"
```

When the database was started on another host port, also tell the app:

```bash
DB_URL=jdbc:postgresql://localhost:5433/smartfix \
DB_USERNAME=smartfix DB_PASSWORD=smartfix \
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 1.6 Test and package

```bash
mvn test             # all tests, isolated H2 profile - needs no database
mvn clean package    # compile + test + produce runnable jar
java -jar target/smartfix-0.0.1-SNAPSHOT.jar   # optional: run the packaged app
```

---

## 2. Understand the project before changing code

- Read the **README** first — it is the handbook.
- `src/main/java/com/smartfix/<module>/` holds each module. Today: `auth`,
  `common`, `user`. Layers inside a module: `controller`, `service`, `domain`,
  `repository`, `dto` — only where needed.
- Config: `src/main/resources/application*.yml`. Schema: Flyway migrations in
  `src/main/resources/db/migration/`. Tests: `src/test/java`, with an isolated H2
  profile in `src/test/resources/application-test.yml`.
- Dependency direction is one-way and branch-like (README §6, `docs/architecture.md`).
- **Where a piece of code goes:** use the decision guide in README §9 and
  `docs/module-guide.md` before you create files.

---

## 3. Your normal daily loop

Start every working session from an up-to-date `main`:

```bash
git checkout main
git pull origin main
```

Create a short-lived branch for the story:

```bash
git checkout -b feature/SCRUM-XX-short-description
```

Develop. Run the tests whenever you finish a meaningful chunk:

```bash
mvn test
```

Commit in small, meaningful steps (see §5). Push and open a Pull Request:

```bash
git add <files>                       # stage only related files
git commit -m "feat(request): add request submission validation"
git push -u origin feature/SCRUM-XX-short-description
```

Open the PR on GitHub using the template, ask for a review, keep CI green, then merge
per `docs/team-workflow.md`. After your PR merges, sync `main` again (see §8).

---

## 4. How a feature becomes code (the order matters)

Do **not** skip straight to coding, especially for stateful or rule-heavy behaviour.

```text
Jira story → use case → analysis (ECB) → sequence diagram → class design
→ implementation → unit tests → PR
```

For each story: confirm the owning module, check whether the domain/schema changes, add
a numbered Flyway migration **only if** the data model changes (never edit the empty
`V1__baseline.sql`), implement within the module, and test. See README §11 and
`docs/team-workflow.md`.

---

## 5. Committing safely

Review before committing:

```bash
git status        # what changed / what is staged
git diff          # unstaged changes, in detail
git diff --staged # staged changes
```

Rules:

- Stage only related files (`git add <specific file>`), not `git add .` by habit.
- Never stage `.env`, `target/`, IDE files or logs (`.gitignore` already excludes most).
- Write a meaningful message (README §16):
  `feat(module): short description` — e.g. `feat(workorder): prevent duplicate assignment`.

If you accidentally stage something wrong:

```bash
git restore --staged <file>    # unstage, keep the change (safe)
git restore <file>             # discard uncommitted changes to a file (careful - destructive)
```

---

## 6. When your branch falls behind `main`

Two safe options. Prefer merge (no history rewriting):

```bash
git checkout main && git pull origin main
git checkout feature/SCRUM-XX-short-description
git merge main          # resolve conflicts if any, then test
```

or, if the team agrees on rebasing short-lived branches (optional, not the default):

```bash
git fetch origin
git rebase origin/main   # only on your own, unshared branch
```

See `docs/git-safety-guide.md` before doing anything with `--force` or `reset`.

---

## 7. Handling a merge conflict

Conflicts are normal. Do **not** blindly pick "Accept Current"/"Accept Incoming".

1. `git status` — which files conflict.
2. Open each conflicted file and find the `<<<<<<<`, `=======`, `>>>>>>>` markers.
3. Understand both sides' intent (both authors wrote those lines for a reason).
4. Resolve by editing to the correct combined result and remove the markers.
5. Special care for `pom.xml`, Flyway migrations and security/config files — get a
   teammate to double-check these.
6. After resolving:
   ```bash
   git add <resolved files>
   git commit
   ```
7. Run `mvn test` to prove the resolution is correct.

---

## 8. Syncing `main` after your PR merges

```bash
git checkout main
git pull origin main
```

Delete your finished feature branch locally (and on GitHub after merge):

```bash
git branch -d feature/SCRUM-XX-short-description   # safe; -D would force-delete
```

---

## 9. Database changes (summary)

Full detail: [`docs/database-guide.md`](database-guide.md). The essentials:

- The schema is managed **only by Flyway** (`spring.jpa.hibernate.ddl-auto: none`).
- To change the schema, add a new migration file
  `src/main/resources/db/migration/V<N>__<description>.sql` where `<N>` is the next free
  number **after** `git pull`.
- **Never edit a migration that teammates/main/CI have already applied.**
- Never delete a migration because your local DB failed — write a corrective migration.

---

## 10. When you are finished with a story

Check the **Definition of Done** (README §18). At minimum:

- `mvn test` passes.
- `mvn clean package` passes.
- Acceptance criteria are met.
- PR has review, CI is green, Jira is updated.
