# Local CD execution evidence — 2026-10-23

- **PASS** actual CI artifact selection/download from https://github.com/Lian9374/smartfix/actions/runs/39914425412 . Event push, main commit `7e91ce0a2fdc11279aa4f15218facf986abcb41a`, both SWE5006 jobs successful, run attempt 1, artifact 12483813348.
- **PASS** JAR SHA-256 `cb9bb07d72666f034da44a4d310c65a76b959e4bc82903272093adcacc7d419a`; Spring Boot manifest verified before building. No Maven rebuild or skipped tests used for deployment.
- **PASS** Docker 29.1.3 runtime image `smartfix-swe5006-demo:39914425412-1` built from the verified JAR.
- **PASS** dedicated PostgreSQL 16 container healthy; application container running on `127.0.0.1:18080`.
- **PASS** GET http://localhost:18080/actuator/health -> HTTP 200 and status UP; GET /login -> HTTP 200. Recorded deployment time: 2026-10-23 16:51:04 +08:00.
- **PASS** Compose configuration validation using generated local environment, no credentials printed.
- **PASS** three Python security-gate tests, including six invalid-event/branch/SHA/status/conclusion/workflow subcases and fork-source refusal.
- **PASS** repeat execution printed `UNCHANGED: verified release 39914425412-1`; no duplicate build or container replacement.
- **PASS** local `--watch` agent started and observed logging the unchanged release. Watch log: `$HOME/.smartfix-local-cd/agent.log`. PID recorded in agent.lock/watch-process.txt; the process stops when the PC shuts down and is not installed as a Windows startup service. `python deploy/local/deploy.py --stop` requests a clean stop; containers remain running.
- **NOT RUN** deployment caused by a subsequent main merge after this new CD branch. A new approved main change and its successful CI are needed to demonstrate the complete next-change cycle. Do not equate this PR's hosted configuration test with a local deployment.
- **NOT RUN** database schema downgrade/automatic rollback, application-wide browser acceptance, and machine-reboot recovery. Health/login-page checks are deployment smoke checks, not a replacement for CI tests or full functional acceptance.

Docker initially failed to start. The supported `docker desktop start` command eventually brought up the engine. No Docker factory reset, container removal, volume deletion, production database access or cloud resource creation was performed.

Machine-readable local evidence: `$HOME/.smartfix-local-cd/current.json` and `releases/39914425412-1/deployment.json`. Synthetic credentials are kept in demo.env outside the repository and are intentionally absent here. Deployment source, configuration, evidence documentation and trust tests are submitted in a separate PR; main is not merged automatically.
