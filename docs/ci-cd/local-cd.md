# SWE5006 local Docker CD

## What this completes

Main push -> SmartFix SWE5006 CI -> both jobs succeed -> local pull agent downloads that exact CI JAR -> builds a Java 21 runtime image -> Docker Compose starts application + PostgreSQL 16 -> HTTP health and login smoke checks -> deployment.json evidence.

This is pull-based continuous deployment. No inbound connection, cloud resources, registry publication, self-hosted GitHub runner, or long-lived GitHub secret is installed in the repository. The logged-in local GitHub CLI is used for read-only API calls. PR code never runs on the local agent. GitHub-hosted runners still execute all PR tests. The GitHub CD configuration check validates the local deployment gates/Compose; it does NOT claim to deploy to your laptop.

## Start/stop the demo

Requirements: Docker Desktop Linux engine, Python 3.10+, GitHub CLI authenticated with repository Actions read access; port 18080 available.

From this checkout:

```powershell
# Deploy the current main version once, using its successful CI artifact.
python deploy/local/deploy.py

# During the classroom demonstration, keep this terminal open.
python deploy/local/deploy.py --watch

# From another terminal, stop watching; running containers remain available.
python deploy/local/deploy.py --stop
```

Open http://localhost:18080/login . The synthetic local administrator username is `demo.admin`; its generated password is in `$HOME/.smartfix-local-cd/demo.env`. Never commit or paste that file into the chat. Only the local deployment uses these credentials.

State and deployment evidence are in `$HOME/.smartfix-local-cd/`; the repository contains no passwords. `current.json` records the deployed CI run, SHA, image, JAR checksum, health/login checks and time. Each release retains its deployment.json, source artifact and JAR. On failure, failure.log contains container logs, and current.json is not advanced. Preserve release files for your classroom evidence; they are not automatically pruned.

The two named volumes `smartfix-swe5006-demo_database` and `smartfix-swe5006-demo_uploads` persist demo PostgreSQL data and attachments across application updates. Only application port 18080 is bound, on 127.0.0.1; PostgreSQL is not exposed on the host. The application runs as a non-root user. Existing development compose containers/volumes are untouched.

## Classroom trigger demonstration

1. Start Docker Desktop and `--watch`; keep the PC awake.
2. Make a small change on a feature branch and open a PR. Show CI; the local agent must NOT deploy the PR.
3. After review and your approval, merge to main. The agent waits while main CI is queued/running or fails.
4. When both main CI jobs pass, the agent deploys automatically on its next poll (30 seconds) and prints PASS only after health/login checks pass.
5. Refresh localhost:18080 and show current.json: its commit must match the merge commit and its ci_url must link to the successful main push CI.
6. Running once again with the same healthy release prints UNCHANGED and avoids rebuilding. If the app is down it reconciles the verified release instead of falsely reporting success.

This CD branch has not been merged automatically. The local agent can already deploy an existing main CI artifact; the new configuration-check workflow becomes permanent only after the PR is approved and merged. The next main merge cannot be demonstrated without the user's merge action.

## Trust gates and failure handling

The agent requires the exact workflow path, push event, source repository, current main SHA, completed/success conclusion, both fixed CI job names, and an exact non-expired artifact name including run attempt. It rechecks main immediately before deployment. Other coursework workflows, fork/PR runs, failures and obsolete main versions cannot deploy. The local controller/script is reviewed local code; it never downloads or executes shell scripts from CI artifacts.

Flyway runs normally on the demo database. No clean/repair, checksum changes, destructive volume operations or test skips are used. There is brief downtime during application replacement. A failed deployment is NOT a successful release. The previous image/metadata are retained, but there is no automatic database rollback: old application images may be incompatible with newer schemas. Back up database and attachments before upgrades with valuable demo data. Plan a forward fix or restore a verified backup when necessary.

If Docker fails to start, do not reset it or delete existing volumes. Fix its startup first. If a killed agent leaves agent.lock behind, inspect its PID and verify no agent is running before removing only that lock. Expired CI artifacts require a new main CI run; do not substitute an unverified local JAR. After source changes, restart the local controller from the reviewed checkout to use the updated controller code.

To stop containers without deleting data, set SMARTFIX_DEMO_IMAGE to the image in current.json and run:

```powershell
docker compose --env-file "$HOME/.smartfix-local-cd/demo.env" -f deploy/local/compose.yml stop
```

## Validation

`python -m unittest discover -s deploy/local -p test_deploy.py -v` exercises the trust boundary: current successful main accepted; PR, wrong branch/SHA/workflow, in-progress/failing run and fork source refused. `docker compose ... config --quiet` validates required configuration without printing generated credentials. Real deployment results are in local-cd-evidence.md.

References: [Docker Compose up/wait](https://docs.docker.com/reference/cli/docker/compose/up/), [GitHub self-hosted runner security](https://docs.github.com/en/actions/reference/security/secure-use).
