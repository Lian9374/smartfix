# SmartFix Troubleshooting

Common problems and how to diagnose them. Commands below match the actual project.
If a symptom is not here, capture the exact error and ask the team with the log output.

---

## 1. Environment problems

### Wrong / missing Java version
Symptom: `java -version` is not 21, or Maven reports "invalid target release: 21".
Fix: install/select JDK 21 and make sure it is on `PATH`:
```bash
java -version      # expect 21.x
mvn -version       # the "Java version" line should show 21
```

### Maven not installed
Symptom: `mvn: command not found`.
Fix: install Maven 3.9+ and verify `mvn -version`.

### Docker not running
Symptom: `docker: Cannot connect to the Docker daemon`, or `docker compose up` hangs.
Fix: start **Docker Desktop**, wait for the engine (whale icon steady), then:
```bash
docker info
```

### PostgreSQL not ready
Symptom: the app fails at startup with a connection error (or Flyway
`Unable to obtain connection`).
Diagnosis:
```bash
docker compose ps              # db should be "healthy"
```
If `db` is not up: `docker compose up -d db`, wait, re-check. The app must start
**after** the database is healthy.

### Port 5432 occupied
Symptom: `docker compose up` reports the port is already allocated, or the app reaches a
different PostgreSQL than the container.
Fix: run the container's database on another host port and point the app at it:
```bash
DB_PORT=5433 docker compose up -d db
DB_URL=jdbc:postgresql://localhost:5433/smartfix \
DB_USERNAME=smartfix DB_PASSWORD=smartfix \
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Port 8080 occupied
Symptom: `APPLICATION FAILED TO START … Port 8080 was already in use.`
Fix: run on another port:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--server.port=8081"
```

### Database credentials mismatch
Symptom: `password authentication failed for user "smartfix"` (or similar).
Fix: make sure the app's `DB_USERNAME`/`DB_PASSWORD`/`DB_URL` match the database created
by `docker-compose.yml`/`.env`. Do not guess — compare `.env` (if present) with
`.env.example` and `docker-compose.yml` defaults. Check whether a locally installed
PostgreSQL on `5432` is shadowing the container (see "Port 5432 occupied").

---

## 2. Flyway / migration problems

### Flyway migration failure at startup
Symptom: startup aborts with a Flyway error; the log names a `V*.sql` file.
Fix:
1. Read the exact error (SQL error, constraint, duplicate version…).
2. It may mean a teammate's migration conflicts — pull `main` and renumber your migration
   to the next free version (`docs/database-guide.md` §8).
3. **Do not** delete or edit an already-applied migration to "fix" your local DB.

### Reset a messy local database (your machine only)
```bash
docker compose down -v     # removes the local data volume (destructive to LOCAL data)
docker compose up -d db
```
Never run this against a shared environment, and never to avoid writing a proper
corrective migration.

---

## 3. Application / test problems

### Spring context fails to start
Symptom: `APPLICATION FAILED TO START` with a cause (bean, datasource, Flyway).
Diagnosis: read the `Description:` / `Action:` block at the end of the log — Spring Boot
states the cause and the fix in plain text. For a full report, rerun with `--debug`.

### Tests pass locally but CI fails
The H2 test profile isolates tests from your database, so a local green ≠ CI green if:
- a test is order/time-dependent,
- code is committed without running `mvn test`,
- the CI agent has a different environment (JDK/Maven),
- a migration or config file was changed but not committed.
Fix: reproduce with a clean build and compare — `mvn clean test` locally; check the CI
log for the first failing step; never merge a red build "because it works locally".

### `mvn test` fails after a merge conflict
You may have resolved the conflict incorrectly or left `<<<<<<<` markers. Search:
```bash
grep -rn "^<<<<<<<\|^=======\|^>>>>>>>" src/ || echo "no markers left"
```
Re-check the resolution, re-run `mvn test`.

---

## 4. Docker Compose problems

### Compose file invalid or services not starting
```bash
docker compose config          # validates and prints effective config
docker compose ps              # status
docker compose logs db         # database logs
docker compose logs app        # application container logs (if used)
```

### The optional `app` container fails to build
Symptom: `docker compose --profile app up --build` errors.
Diagnosis: the multi-stage `Dockerfile` runs `mvn package` inside the image — check the
build log for Maven/dependency errors. A local `mvn clean package` succeeding is a good
first check that the Java build itself is sound.

---

## 5. Configuration problems

### `application.yml` / profile not as expected
- Base config: `src/main/resources/application.yml`.
- Dev profile: `src/main/resources/application-dev.yml` (SQL + DEBUG logging).
- Test profile: `src/test/resources/application-test.yml` (H2, Flyway off).
Check the active profile at startup — the log prints
`The following N profile(s) are active: …`. If the app connects to the wrong database,
re-check `DB_URL` and the active profile.

---

## 6. Git problems

### Merge conflict (README §23, `docs/git-safety-guide.md` §7)
Resolve in the editor to the correct combined result, remove conflict markers, then
`git add <files>` and `git commit`. Never blindly accept one side. Pay extra attention
to `pom.xml`, Flyway migrations and security/config files.

### Accidentally worked on `main`
```bash
git checkout -b feature/SCRUM-XX-description   # carry your work onto a branch
git push -u origin feature/SCRUM-XX-description
```

### Accidentally committed an unwanted file
```bash
git restore --staged <file>    # keep change, remove from staging
# or, to fully remove a wrongly committed file and add it to .gitignore:
git rm --cached <file>
```
If the file is a **secret** and was pushed: rotate/revoke it and tell the team
(README §21).

### Branch behind `main`
```bash
git checkout main && git pull origin main
git checkout feature/SCRUM-XX-short-description
git merge main
```

### Lost work / not sure what happened
```bash
git reflog          # journal of HEAD movements - find your commit
git branch backup/lost <sha>   # recreate a branch at that commit
```
Do not panic-run destructive commands.

---

## 7. When to escalate

If a problem touches **shared** things — `pom.xml`, Flyway history, security
configuration, CI, or anything on `main` — stop and ask the team rather than applying a
local workaround. Protecting shared history beats a quick fix.
