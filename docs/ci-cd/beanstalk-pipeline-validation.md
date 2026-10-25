# Beanstalk pipeline change — local validation, 2026-10-24

Worktree: C:/Users/zhour/smartfix-beanstalk-pipeline
Branch: ci/beanstalk-artifacts
Base: main 7e91ce0a2fdc11279aa4f15218facf986abcb41a

This records actual local checks of the new files. It is not a GitHub Actions
run or AWS deployment result. Application sources, POM and existing Java tests
were not modified. No workflow was dispatched/pushed and no AWS mutation was run.

## Executed: PASS

- Python 3.12 `python -m unittest discover -s deploy/beanstalk -p test_ci.py -v`:
  24 tests passed. Generated fixture JARs, AWS/HTTP mocks only. Cases include
  tampered JAR/ZIP, different commit/run/attempt/repository, PR/push/non-main/no
  opt-in refusal, wrong environment, upload checksum mismatch, exact instance
  version/Green health, HTTP DOWN/redirects, safe receipts and report redaction.
- actionlint 1.7.12: no workflow errors, using
  `actionlint -shellcheck= -pyflakes= .github/workflows/smartfix-ci.yml`.
  Official Windows release ZIP was verified against its published SHA-256.
  External shellcheck/pyflakes integrations were disabled (not installed).
- YAML parsing and Python AST parsing: PASS.
- Git Bash synthetic failure injection: a command exiting 7 passed through
  ci.py redact and tee still returned 7; fixture password was absent from output.
  This is a pipeline failure-propagation test, not a failed real Maven suite.
- Java 21.0.9 / Maven 3.9.16 default verification:
  `mvn -B --no-transfer-progress clean verify` → BUILD SUCCESS, exit 0,
  elapsed 04:19. Fresh original XML and exported report inventory agree:
  Surefire 526 tests / 64 report files, default Failsafe 277 / 16 report files;
  failures=0, errors=0, skipped=0 in both. Do not interpret all Surefire tests as
  pure isolated unit tests: existing Spring/repository slices are included.
- Real-JAR local package check: the just-built executable JAR was wrapped with
  the existing prod config/hooks and a freshly downloaded official public RDS CA;
  ZIP entries/modes, release metadata, and original/downloaded/embedded JAR hash
  equality were verified. The generated context uses run 0 and a workflow name
  explicitly marked LOCAL PACKAGING CHECK — it is a fixture, not a GitHub run.
  No AWS CLI call, upload, version registration or deployment was involved.
- `git diff --check`: PASS.

Local packaging hashes (not an AWS release):

- JAR SHA-256: 00934461bdc7a98840cc76e76c6f133f8cf7e22ab95cb0ac7c28f17d8a7d63ff
- ZIP SHA-256: 12a4e17da307857fac586fdf975f2827d5fc0f8d8bc3ced42fc8a9de650de0a0

Generated evidence is ignored by Git:

- ci-logs/local-default-verify.log; ci-logs/local-maven-exit.txt
- target/local-package-check/validation-result.json
- target/local-package-check/verification/report-inventory.json and sanitized XML/log
- target/local-package-check/build/ and release/ (LOCAL FIXTURE context)
- target/pipefail-check.log (synthetic fixture only)

The first restricted-sandbox Maven attempt stalled during Mockito agent setup
and was stopped; its partial log is not a successful run. The completed normal
host-permission rerun above is the reported result. Git Bash likewise required
normal host permission after sandbox initialization failed.

## Requires GitHub Actions / authorized cloud validation: NOT RUN

- New workflow's actual PR/main/dispatch events, skip behavior and artifact
  upload/download across separate hosted jobs. actionlint is not execution proof.
- The PostgreSQL service and unchanged full postgres-it profile in this revised
  workflow. No new dedicated PostgreSQL server was started locally for this task.
- First-party action execution, artifact retention and an actual failed-hosted
  run showing sanitized reports remain downloadable.
- GitHub environment variables/branch restrictions, AWS OIDC trust and IAM
  permissions. No repository settings, GitHub environment or AWS role was changed.
- Real S3 checksum/metadata, Application Version registration and EB update.
- Real post-deploy version/instance/Green health and final HTTPS smoke checks.
  Existing AWS Warning/HTTP-only findings have not been fixed or bypassed.
- Authenticated business acceptance, rollback, EFS replacement persistence,
  quality scanners, coverage gates, Docker publication and load testing remain
  outside this workflow-structure/artifact change; no success is claimed.
