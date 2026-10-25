"""Private GHCR publication and digest-only ephemeral test deployment.

No image builds in this file. Reuse container_verify's tested local image,
HTTP checks, redaction, isolated Compose project and scoped cleanup.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import secrets
import tempfile
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, build_opener

import container_verify as cv

REQUIRED_JOBS = (
    "SWE5006 Build and Test", "SWE5006 PostgreSQL Integration",
    "SWE5006 SpotBugs Gate", "SWE5006 Dependency Security Gate",
)
DIGEST = r"sha256:[0-9a-f]{64}"


class Blocked(RuntimeError):
    def __init__(self, reason, http_status=None):
        super().__init__(reason)
        self.reason, self.http_status = reason, http_status


def repository() -> str:
    value = os.environ.get("CONTAINER_IMAGE", "")
    if not re.fullmatch(r"ghcr\.io/lian9374/[a-z0-9]+(?:[._/-][a-z0-9]+)*", value):
        raise Blocked("MISSING_OR_INVALID_EXISTING_PRIVATE_GHCR_IMAGE")
    return value


def api(path: str) -> dict:
    token = os.environ.get("CONTAINER_GITHUB_TOKEN", "")
    if not token:
        raise Blocked("MISSING_GITHUB_PACKAGE_TOKEN")
    request = Request("https://api.github.com" + path, headers={
        "Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "smartfix-container-test-release",
    })
    try:
        with build_opener(cv.NoRedirect()).open(request, timeout=20) as response:
            body = response.read(2_000_001)
            if len(body) > 2_000_000:
                raise Blocked("GITHUB_RESPONSE_TOO_LARGE")
            return json.loads(body)
    except HTTPError as error:
        raise Blocked("GITHUB_API_PERMISSION_OR_RESOURCE_FAILURE", error.code) from None


def private_package(repo: str) -> dict:
    # Never auto-create a package, infer visibility from a public source repo,
    # or change visibility. Missing/403/public/internal all fail before push.
    name = repo.removeprefix("ghcr.io/lian9374/")
    data = api("/users/Lian9374/packages/container/" + quote(name, safe=""))
    if data.get("visibility") != "private" or data.get("package_type") != "container" or data.get("name") != name or data.get("owner", {}).get("login", "").lower() != "lian9374":
        raise Blocked("EXISTING_PACKAGE_MUST_BE_PRIVATE")
    return dict(name=name, visibility="private", id=data["id"])


def authorize() -> dict:
    if os.environ.get("GITHUB_REPOSITORY") != "Lian9374/smartfix" or os.environ.get("GITHUB_REF") != "refs/heads/main" or os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch":
        raise Blocked("ONLY_EXPLICIT_MAIN_CONTAINER_RELEASE_DISPATCH_ALLOWED")
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    inputs = event.get("inputs", {})
    if str(inputs.get("container_release", "false")).lower() != "true" or str(inputs.get("deploy", "false")).lower() != "false":
        raise Blocked("CONTAINER_RELEASE_AND_BEANSTALK_DEPLOY_ARE_MUTUALLY_EXCLUSIVE")
    return cv.ci.identity()


def checked_jobs(run: dict, jobs: dict, identity: dict) -> list:
    if any(run.get(key) != value for key, value in dict(head_sha=identity["commit_sha"], head_branch="main", event="workflow_dispatch", run_attempt=int(identity["run_attempt"]), path=".github/workflows/smartfix-ci.yml").items()):
        raise Blocked("WORKFLOW_RUN_PROVENANCE_MISMATCH")
    if jobs.get("total_count", 0) > 100 or jobs.get("total_count") != len(jobs.get("jobs", [])):
        raise Blocked("JOB_LIST_INCOMPLETE")
    proof = []
    for name in REQUIRED_JOBS:
        matching = [item for item in jobs.get("jobs", []) if item.get("name") == name]
        if len(matching) != 1 or matching[0].get("status") != "completed" or matching[0].get("conclusion") != "success" or matching[0].get("head_sha") != identity["commit_sha"]:
            raise Blocked("REQUIRED_TEST_OR_QUALITY_JOB_NOT_SUCCESSFUL")
        proof.append(dict(name=name, job_id=matching[0]["id"], conclusion="success"))
    return proof


def required_checks(identity: dict) -> list:
    base = "/repos/Lian9374/smartfix/actions/runs/" + identity["run_id"]
    run = api(base)
    jobs = api(base + "/attempts/" + identity["run_attempt"] + "/jobs?per_page=100")
    return checked_jobs(run, jobs, identity)


def preflight(args) -> int:
    report = dict(status="FAIL", purpose="CONTAINER_RELEASE_PREREQUISITES_ONLY")
    try:
        identity = authorize()
        report.update(identity)
        report["required_checks"] = required_checks(identity)
        report["package"] = private_package(repository())
        report["status"] = "READY"
    except Exception as error:
        failure(report, error, "authorization-checks-private-package")
    cv.save(args.output / "preflight.json", report)
    print("container release prerequisites: " + report["status"])
    return 0 if report["status"] == "READY" else 1


def verified_image(data: dict, identity: dict) -> None:
    if data.get("status") != "PASS" or data.get("build_status") != "PASS" or data.get("runtime_status") != "PASS" or data.get("diagnostics", {}).get("status") != "PASS" or data.get("cleanup", {}).get("status") != "PASS":
        raise Blocked("LOCAL_IMAGE_VERIFICATION_NOT_PASSED")
    checks = data.get("checks", [])
    if len(checks) != len(cv.SMOKE_CHECKS) or {item.get("name") for item in checks} != set(cv.SMOKE_CHECKS) or any(check.get("status") != "PASS" or not check.get("assertions") or any(value is not True for value in check["assertions"].values()) or check.get("http_status") != check.get("expected_status") for check in checks):
        raise Blocked("REQUIRED_BUSINESS_SMOKE_ASSERTIONS_NOT_PASSED")
    if data.get("readiness", {}).get("health_status") != "UP" or data["readiness"].get("http_status") != 200 or data.get("cleanup", {}).get("remaining") != dict(containers=0, networks=0, volumes=0) or not data["cleanup"].get("image_retained"):
        raise Blocked("HEALTH_OR_RETAINED_IMAGE_CLEANUP_PROOF_MISSING")
    source = data.get("input", {})
    if source.get("origin") != "GITHUB_ACTIONS_BUILD_ARTIFACT" or any(source.get(key) != identity[key] for key in ("commit_sha", "run_id", "run_attempt", "workflow_run", "application_version")):
        raise Blocked("VERIFIED_IMAGE_SOURCE_IS_NOT_THIS_CI_BUILD")
    if not re.fullmatch(r"[0-9a-f]{64}", source.get("jar_sha256", "")) or data.get("running_jar_sha256") != source["jar_sha256"]:
        raise Blocked("VERIFIED_JAR_HASH_MISMATCH")


def push_digest(text: str) -> str:
    values = set(re.findall(r"\bdigest:\s*(" + DIGEST + r")\b", text))
    if len(values) != 1:
        raise Blocked("PUSH_DID_NOT_RETURN_ONE_REGISTRY_DIGEST")
    return values.pop()


def remote_digest(harness: cv.Harness, reference: str) -> str:
    result = harness.command(["docker", "buildx", "imagetools", "inspect", reference, "--format", "{{json .Manifest}}"], "registry-manifest", timeout=120)
    value = json.loads(result.stdout).get("digest", "")
    if not re.fullmatch(DIGEST, value):
        raise Blocked("REMOTE_MANIFEST_DIGEST_MISSING")
    return value


def login(harness: cv.Harness):
    token = os.environ.get("CONTAINER_GITHUB_TOKEN", "")
    actor = os.environ.get("GITHUB_ACTOR", "")
    if not token or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9-]*(?:\[bot\])?", actor):
        raise Blocked("MISSING_PACKAGE_AUTHENTICATION")
    harness.sensitive.append(token)
    # Credential config exists only in the harness's temporary directory.
    harness.env["DOCKER_CONFIG"] = str(Path(harness.temp.name) / "docker-auth")
    harness.command(["docker", "login", "ghcr.io", "--username", actor, "--password-stdin"], "registry-login", stdin=token, timeout=60)


def identity_release(data: dict, identity: dict, repo: str):
    cv.ci.validate_identity(data)
    digest = data.get("registry_digest", "")
    if data.get("status") != "PUBLISHED" or data.get("purpose") != "CONTAINER_RELEASE_AND_EPHEMERAL_TEST_ONLY" or not re.fullmatch(DIGEST, digest):
        raise Blocked("NO_SUCCESSFUL_PUBLICATION_RECEIPT")
    if data.get("image_tag") != repo + ":sha-" + identity["commit_sha"] or data.get("image_ref") != repo + "@" + digest:
        raise Blocked("RELEASE_IMAGE_REFERENCE_MISMATCH")
    if not re.fullmatch(r"[0-9a-f]{64}", data.get("jar_sha256", "")):
        raise Blocked("RELEASE_JAR_HASH_MISSING")
    proof = data.get("required_checks", [])
    if len(proof) != len(REQUIRED_JOBS) or {item.get("name") for item in proof} != set(REQUIRED_JOBS) or any(item.get("conclusion") != "success" or not isinstance(item.get("job_id"), int) for item in proof):
        raise Blocked("RELEASE_REQUIRED_CHECK_PROOF_MISSING")
    if not re.fullmatch(DIGEST, data.get("verified_image_id", "")) or not re.fullmatch(r"[0-9a-f]{64}", data.get("verification_receipt_sha256", "")):
        raise Blocked("RELEASE_VERIFICATION_PROVENANCE_MISSING")


def failure(report: dict, error: Exception, phase: str):
    report["status"] = "FAIL"
    report["failure"] = dict(phase=phase, error_type=type(error).__name__, reason=error.reason if isinstance(error, Blocked) else "REQUIRED_OPERATION_FAILED")
    if isinstance(error, Blocked) and error.http_status:
        report["failure"]["http_status"] = error.http_status


def publish(args) -> int:
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    report = dict(status="FAIL", purpose="CONTAINER_RELEASE_AND_EPHEMERAL_TEST_ONLY", push_observed=False)
    harness, tagged = None, False
    phase = "authorization-and-required-quality-checks"
    try:
        identity = authorize()
        report.update(identity)
        report["required_checks"] = required_checks(identity)
        repo = repository()
        report["package"] = private_package(repo)
        phase = "same-verified-image"
        data = json.loads((args.verified / "result.json").read_text(encoding="utf-8"))
        verified_image(data, identity)
        harness = cv.Harness(output, data["project"], data["image_tag"], [])
        actual = cv.image_details(harness, data["image_tag"])
        if actual["id"] != data["image"]["id"] or actual["revision"] != identity["commit_sha"] or actual["jar_sha256"] != data["input"]["jar_sha256"]:
            raise Blocked("IMAGE_CHANGED_AFTER_VERIFICATION")
        phase = "private-registry-login-and-push"
        login(harness)
        tag = repo + ":sha-" + identity["commit_sha"]
        harness.command(["docker", "tag", actual["id"], tag], "registry-tag")
        tagged = True
        pushed = harness.command(["docker", "push", tag], "registry-push", timeout=600)
        report["push_observed"] = True
        digest = push_digest(pushed.stdout + pushed.stderr)
        report["candidate_digest"] = digest
        phase = "registry-digest-and-visibility-verification"
        if remote_digest(harness, repo + "@" + digest) != digest:
            raise Blocked("PUSH_AND_REGISTRY_DIGEST_MISMATCH")
        report["package"] = private_package(repo)
        report.update(status="PUBLISHED", image_tag=tag, image_ref=repo + "@" + digest, registry_digest=digest, jar_sha256=data["input"]["jar_sha256"], verified_image_id=actual["id"], verification_receipt_sha256=cv.ci.digest(args.verified / "result.json"))
    except Exception as error:
        failure(report, error, phase)
    finally:
        if harness:
            if tagged:
                removed = harness.command(["docker", "image", "rm", tag], "remove-publication-local-tag", required=False)
                report["publication_local_tag_removed"] = removed.returncode == 0
                if removed.returncode:
                    report["status"] = "FAIL"
            try:
                harness.temp.cleanup()
            except Exception:
                report["status"], report["auth_config_cleanup"] = "FAIL", "FAIL"
        report["finished_at"] = datetime.now(timezone.utc).isoformat()
        cv.save(output / "publish-result.json", report)
        if report["status"] == "PUBLISHED":
            cv.save(output / "release.json", report)
    print("container publication: " + report["status"])
    return 0 if report["status"] == "PUBLISHED" else 1


def test_deploy(args) -> int:
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    report = dict(status="FAIL", purpose="DEPLOYMENT_VERIFICATION_IN_TEMPORARY_CONTAINER_ENVIRONMENT", environment="EPHEMERAL_CI_RUNNER_NOT_PERSISTENT_NOT_PRODUCTION", environment_label="临时容器环境中的部署验证", build_status="NOT_APPLICABLE_NO_REBUILD", runtime_status="NOT_RUN", checks=[])
    harness, phase, preserve = None, "authorization-and-release-provenance", False
    try:
        identity = authorize()
        report.update(identity)
        repo = repository()
        release = json.loads(args.release.read_text(encoding="utf-8"))
        identity_release(release, identity, repo)
        report.update(image_tag=release["image_tag"], registry_digest=release["registry_digest"], image_ref=release["image_ref"], jar_sha256=release["jar_sha256"], publication_receipt_sha256=cv.ci.digest(args.release))
        report["package"] = private_package(repo)
        project = "sf-verify-" + secrets.token_hex(6)
        harness = cv.Harness(output, project, release["image_ref"], [], digest_image=True)
        # Record before pull so always-cleanup can recover from interruption.
        preserve = harness.has_image(release["image_ref"])
        cv.save(output / "state.json", dict(project=project, image=release["image_ref"], digest_image=True, resources_attempted=True, preserve_preexisting_image=preserve))
        phase = "private-registry-digest-pull"
        login(harness)
        if remote_digest(harness, release["image_ref"]) != release["registry_digest"]:
            raise Blocked("PUBLISHED_DIGEST_NOT_FOUND_IN_REGISTRY")
        harness.command(["docker", "pull", release["image_ref"]], "pull-by-digest", timeout=600)
        details = cv.image_details(harness, release["image_ref"])
        if details["revision"] != identity["commit_sha"] or details["jar_sha256"] != release["jar_sha256"] or details["user"] in ("", "root", "0"):
            raise Blocked("PULLED_IMAGE_COMMIT_OR_JAR_LABEL_MISMATCH")
        report["image"] = details
        phase = "ephemeral-test-deployment-and-business-smoke"
        cv.exercise(harness, report, release["jar_sha256"], args.readiness_timeout)
        report["status"] = "PASS"
    except Exception as error:
        if report["runtime_status"] == "IN_PROGRESS":
            report["runtime_status"] = "FAIL"
        failure(report, error, phase)
    finally:
        if harness:
            for name, operation in (("diagnostics", harness.collect), ("cleanup", lambda: harness.cleanup(remove_image=not preserve))):
                try:
                    report[name] = operation()
                except Exception as error:
                    report[name] = dict(status="FAIL", error_type=type(error).__name__)
                if report[name]["status"] != "PASS":
                    report["status"] = "FAIL"
            try:
                harness.temp.cleanup()
            except Exception:
                report["status"], report["auth_config_cleanup"] = "FAIL", "FAIL"
        report["finished_at"] = datetime.now(timezone.utc).isoformat()
        cv.save(output / "test-deployment.json", report)
    print("temporary-container deployment verification: " + report["status"])
    return 0 if report["status"] == "PASS" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("preflight", "publish", "test-deploy"))
    parser.add_argument("--verified", type=Path, default=Path("verification/container"))
    parser.add_argument("--release", type=Path, default=Path("incoming-release/release.json"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--readiness-timeout", type=int, default=180)
    args = parser.parse_args()
    if not 1 <= args.readiness_timeout <= 600:
        parser.error("readiness timeout must be1..600 seconds")
    if args.command == "preflight":
        return preflight(args)
    return publish(args) if args.command == "publish" else test_deploy(args)


if __name__ == "__main__":
    raise SystemExit(main())
