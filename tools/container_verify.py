"""Disposable Docker verification of the exact tested JAR; never publishes/deploys.

Only Python's standard library and Docker Compose >=2.24.4 are needed. HTTP
bodies, session cookies, CSRF tokens and generated credentials stay in memory.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from html.parser import HTMLParser
from http.cookiejar import CookieJar
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin, urlsplit
from urllib.request import (HTTPRedirectHandler, HTTPCookieProcessor, ProxyHandler,
                            Request, build_opener)

ROOT = Path(__file__).resolve().parents[1]
SMOKE_CHECKS = (
    "anonymous_request_list_is_protected", "registration_form", "register_isolated_requester",
    "login_form", "incorrect_password_is_rejected", "login_requires_csrf", "fresh_login_form",
    "requester_login", "authenticated_request_list", "filtered_request_query",
    "requester_cannot_read_admin_queue", "logout", "request_list_protected_after_logout",
)
spec = importlib.util.spec_from_file_location("release_ci", ROOT / "deploy/beanstalk/ci.py")
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


def save(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class CsrfParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tokens = []

    def handle_starttag(self, tag, attrs):
        data = dict(attrs)
        if tag == "input" and data.get("name") == "_csrf" and data.get("value"):
            self.tokens.append(data["value"])


class Browser:
    def __init__(self, base: str, sensitive: list[str]):
        url = urlsplit(base)
        if url.scheme != "http" or url.hostname != "127.0.0.1" or not url.port:
            raise ValueError("Only the disposable loopback application is permitted")
        self.base = base
        self.sensitive = sensitive
        self.cookies = CookieJar()
        self.opener = build_opener(ProxyHandler({}), HTTPCookieProcessor(self.cookies), NoRedirect())

    def request(self, path: str, fields: dict | None = None, timeout: float = 10):
        # No redirects: a login page cannot masquerade as a successful business page.
        target = urljoin(self.base, path)
        if urlsplit(target).netloc != urlsplit(self.base).netloc:
            raise ValueError("Cross-origin smoke request refused")
        data = urlencode(fields).encode() if fields is not None else None
        request = Request(target, data=data)
        try:
            response = self.opener.open(request, timeout=timeout)
        except HTTPError as response_error:
            response = response_error
        with response:
            body = response.read(2_000_001)
            if len(body) > 2_000_000:
                raise ValueError("Unexpected oversized smoke response")
            for cookie in self.cookies:
                self.sensitive.append(cookie.value)
            return response.code, response.headers, body.decode("utf-8", errors="replace")

    def csrf(self, body: str) -> str:
        parser = CsrfParser()
        parser.feed(body)
        if not parser.tokens:
            raise ValueError("Required CSRF form token missing")
        self.sensitive.extend(parser.tokens)
        return parser.tokens[0]


def expect(report: dict, name: str, response, status: int, *, markers=(), location=None):
    code, headers, body = response
    assertions = {"http_status": code == status}
    for marker in markers:
        # Store assertion identifiers, never the body or a credential-derived marker.
        assertions[f"content_{len(assertions)}"] = marker in body
    if location is not None:
        actual = urlsplit(urljoin("http://127.0.0.1", headers.get("Location", "")))
        assertions["redirect_target"] = bool(headers.get("Location")) and actual.hostname == "127.0.0.1" and actual.path + ("?" + actual.query if actual.query else "") == location
    passed = all(assertions.values())
    report["checks"].append(dict(name=name, status="PASS" if passed else "FAIL", http_status=code, expected_status=status, assertions=assertions))
    if not passed:
        raise RuntimeError("Smoke assertion failed")
    return body


def wait_ready(browser: Browser, timeout: int, report: dict) -> None:
    deadline = time.monotonic() + timeout
    attempts = 0
    last_status = None
    while time.monotonic() < deadline:
        attempts += 1
        try:
            code, _, body = browser.request("/actuator/health", timeout=max(0.1, min(5, deadline - time.monotonic())))
            last_status = code
            if code == 200 and json.loads(body).get("status") == "UP":
                report["readiness"] = dict(status="PASS", attempts=attempts, timeout_seconds=timeout, http_status=code, health_status="UP")
                return
        except (OSError, URLError, ValueError):
            pass
        time.sleep(max(0, min(2, deadline - time.monotonic())))
    report["readiness"] = dict(status="FAIL", attempts=attempts, timeout_seconds=timeout, last_http_status=last_status)
    raise TimeoutError("Application readiness deadline exceeded")


def smoke(browser: Browser, report: dict, sensitive: list[str]) -> None:
    username = "verify." + secrets.token_hex(6)
    password = "Sf9_" + secrets.token_urlsafe(24)
    display = "Container verification " + secrets.token_hex(4)
    wrong_password = "Wrong9_" + secrets.token_urlsafe(20)
    sensitive.extend([username, password, display, wrong_password])
    expect(report, "anonymous_request_list_is_protected", browser.request("/requests/mine"), 302, location="/login")
    page = expect(report, "registration_form", browser.request("/register"), 200, markers=("Create a requester account",))
    expect(report, "register_isolated_requester", browser.request("/register", dict(username=username, displayName=display, password=password, confirmPassword=password, _csrf=browser.csrf(page))), 302, location="/login")
    page = expect(report, "login_form", browser.request("/login"), 200, markers=('name="username"', 'name="password"'))
    expect(report, "incorrect_password_is_rejected", browser.request("/login", dict(username=username, password=wrong_password, accountType="REQUESTER", _csrf=browser.csrf(page))), 302, location="/login?error&accountType=REQUESTER")
    expect(report, "login_requires_csrf", browser.request("/login", dict(username=username, password=password, accountType="REQUESTER")), 403)
    page = expect(report, "fresh_login_form", browser.request("/login"), 200)
    expect(report, "requester_login", browser.request("/login", dict(username=username, password=password, accountType="REQUESTER", _csrf=browser.csrf(page))), 302, location="/")
    page = expect(report, "authenticated_request_list", browser.request("/requests/mine"), 200, markers=('id="my-requests-title"', "No requests yet", "0 requests"))
    expect(report, "filtered_request_query", browser.request("/requests/mine?status=SUBMITTED&page=0&size=5"), 200, markers=('id="my-requests-title"', "No requests match this page or filter.", "0 requests"))
    expect(report, "requester_cannot_read_admin_queue", browser.request("/admin/requests"), 403)
    expect(report, "logout", browser.request("/logout", dict(_csrf=browser.csrf(page))), 302, location="/login?logout")
    expect(report, "request_list_protected_after_logout", browser.request("/requests/mine"), 302, location="/login")


def scrub(value: str, sensitive: list[str]) -> str:
    for secret in sorted(set(sensitive), key=len, reverse=True):
        if secret:
            value = value.replace(secret, "[REDACTED]")
    value = re.sub(r"(?i)((?:set-cookie|cookie)\s*:\s*)[^\r\n]+", r"\1[REDACTED]", value)
    value = re.sub(r"(?i)([_-]?csrf[\w-]*[\"']?\s*[=:]\s*)[^\s<,;]+", r"\1[REDACTED]", value)
    return ci.redact(value)


class Harness:
    def __init__(self, output: Path, project: str, image: str, sensitive: list[str], digest_image=False):
        if not re.fullmatch(r"sf-verify-[0-9a-f]{12}", project):
            raise ValueError("Unrecognized verification project")
        pattern = r"ghcr\.io/lian9374/[a-z0-9][a-z0-9._/-]*@sha256:[0-9a-f]{64}" if digest_image else r"smartfix-verification:sha-[0-9a-f]{40}-[0-9a-f]{12}"
        if not re.fullmatch(pattern, image):
            raise ValueError("Unrecognized verification image")
        self.output, self.project, self.image, self.sensitive = output, project, image, sensitive
        self.digest_image = digest_image
        self.extra_compose = []
        # An explicit empty env file and restricted interpolation inputs prevent
        # importing the developer's .env, DB settings or bootstrap credentials.
        self.temp = tempfile.TemporaryDirectory(prefix="smartfix-verify-")
        self.env_file = Path(self.temp.name) / "empty.env"
        self.env_file.write_text("", encoding="utf-8")
        self.env = {k:v for k,v in os.environ.items() if not k.startswith(("SMARTFIX_", "POSTGRES_", "DB_", "SPRING_", "COMPOSE_"))}
        password = secrets.token_urlsafe(32)
        sensitive.append(password)
        self.env.update(SMARTFIX_VERIFY_IMAGE=image, SMARTFIX_VERIFY_DB_PASSWORD=password, DOCKER_BUILDKIT="1")

    def command(self, args: list[str], name: str, timeout=60, required=True, stdin=None) -> subprocess.CompletedProcess:
        # File-backed output avoids waiting indefinitely for inherited pipe handles
        # after killing a timed-out Windows CLI. Raw files are anonymous/temporary;
        # only redacted text leaves this scope.
        with tempfile.TemporaryFile() as stdout, tempfile.TemporaryFile() as stderr:
            note = ""
            try:
                native = subprocess.run(args, cwd=ROOT, env=self.env, stdout=stdout, stderr=stderr, input=stdin, text=True, encoding="utf-8", timeout=timeout)
                code = native.returncode
            except subprocess.TimeoutExpired:
                code, note = 124, "\nOperation timed out.\n"
            except OSError as error:
                code, note = 127, "\nCommand could not be started: " + type(error).__name__ + "\n"
            stdout.seek(0)
            stderr.seek(0)
            result = subprocess.CompletedProcess(args, code, stdout.read().decode("utf-8", errors="replace"), stderr.read().decode("utf-8", errors="replace"))
            text = result.stdout + result.stderr + note
        (self.output / (name + ".log")).write_text(scrub(text, self.sensitive), encoding="utf-8")
        if required and result.returncode:
            raise RuntimeError("Docker command failed: " + name)
        return result

    def compose(self, args: list[str], name: str, timeout=60, required=True):
        extra = [value for path in self.extra_compose for value in ("-f", str(path))]
        return self.command(["docker", "compose", "--env-file", str(self.env_file), "--project-name", self.project, "-f", str(ROOT / "docker-compose.yml"), "-f", str(ROOT / "deploy/docker/compose.verify.yml"), *extra, "--profile", "app", *args], name, timeout, required)

    def collect(self) -> dict:
        logs = self.compose(["logs", "--no-color", "--timestamps", "--tail", "2000"], "containers", required=False)
        state = self.compose(["ps", "--all", "--format", "json"], "container-state", required=False)
        # Only selected state is requested; never archive docker inspect env/config.
        return dict(status="PASS" if logs.returncode == state.returncode == 0 else "FAIL", logs_exit=logs.returncode, state_exit=state.returncode)

    def has_image(self, reference: str) -> bool:
        result = self.command(["docker", "image", "inspect", "--format", "{{.Id}}", reference], "image-presence", required=False)
        if result.returncode == 0 and re.fullmatch(r"sha256:[0-9a-f]{64}", result.stdout.strip()):
            return True
        if result.returncode != 0 and re.search(r"no such (?:image|object)", result.stderr, re.I):
            return False
        raise RuntimeError("Unable to establish image presence")

    def cleanup(self, remove_image=True) -> dict:
        result = self.compose(["down", "--volumes", "--remove-orphans", "--timeout", "10"], "cleanup", timeout=90, required=False)
        remaining = {}
        for kind, args in {
            "containers": ["ps", "-aq"], "networks": ["network", "ls", "-q"], "volumes": ["volume", "ls", "-q"]
        }.items():
            check = self.command(["docker", *args, "--filter", "label=com.docker.compose.project=" + self.project], "cleanup-" + kind, required=False)
            remaining[kind] = len(check.stdout.split()) if check.returncode == 0 else None
        # Remove only this run's exact generated tag, never prune shared resources.
        removed = False
        if remove_image:
            if self.digest_image:
                exists, removed = self.has_image(self.image), True
            else:
                image = self.command(["docker", "image", "ls", "--quiet", "--no-trunc", self.image], "cleanup-image-check", required=False)
                exists, removed = bool(image.stdout.strip()), image.returncode == 0
            if removed and exists:
                removed = self.command(["docker", "image", "rm", self.image], "cleanup-image", required=False).returncode == 0
        passed = result.returncode == 0 and all(value == 0 for value in remaining.values()) and (removed or not remove_image)
        return dict(status="PASS" if passed else "FAIL", compose_exit=result.returncode, remaining=remaining, temporary_image_removed=removed, image_retained=not remove_image)


def image_details(harness: Harness, image: str) -> dict:
    return json.loads(harness.command(["docker", "image", "inspect", "--format", '{"id":{{json .Id}},"repo_digests":{{json .RepoDigests}},"revision":{{json (index .Config.Labels "org.opencontainers.image.revision")}},"jar_sha256":{{json (index .Config.Labels "com.smartfix.jar-sha256")}},"user":{{json .Config.User}}}', image], "image-identity").stdout)


def build_image(harness: Harness, jar: Path, metadata: dict) -> dict:
    with tempfile.TemporaryDirectory(prefix="smartfix-jar-context-") as directory:
        context = Path(directory)
        shutil.copyfile(jar, context / "app.jar")
        harness.command(["docker", "build", "--target", "ci-runtime", "--file", str(ROOT / "Dockerfile"), "--build-arg", "COMMIT_SHA=" + metadata["commit_sha"], "--build-arg", "JAR_SHA256=" + metadata["jar_sha256"], "--tag", harness.image, str(context)], "image-build", timeout=600)
    details = image_details(harness, harness.image)
    if details["revision"] != metadata["commit_sha"] or details["jar_sha256"] != metadata["jar_sha256"] or details["user"] in ("", "root", "0"):
        raise ValueError("Image identity/user assertion failed")
    return details


def exercise(harness: Harness, report: dict, jar_sha256: str, timeout: int) -> None:
    """Shared HTTP/runtime checks for local verification and digest-only test deploy."""
    report["runtime_status"] = "IN_PROGRESS"
    harness.compose(["up", "-d", "--no-build", "--pull", "missing"], "compose-start", timeout=180)
    port = harness.compose(["port", "app", "8080"], "app-port").stdout.strip()
    if not re.fullmatch(r"127\.0\.0\.1:\d+", port):
        raise ValueError("Unexpected application binding")
    browser = Browser("http://" + port, harness.sensitive)
    wait_ready(browser, timeout, report)
    actual_hash = harness.compose(["exec", "-T", "app", "sha256sum", "/app/app.jar"], "running-jar-hash").stdout.split()[0]
    uid = harness.compose(["exec", "-T", "app", "id", "-u"], "runtime-user").stdout.strip()
    if actual_hash != jar_sha256 or not uid.isdigit() or uid == "0":
        raise ValueError("Runtime JAR/user assertion failed")
    report["running_jar_sha256"], report["runtime_uid"] = actual_hash, int(uid)
    smoke(browser, report, harness.sensitive)
    report["runtime_status"] = "PASS"


def input_jar(args) -> tuple[Path, dict]:
    if args.local_jar:
        jar = args.local_jar.resolve()
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip())
        metadata = dict(commit_sha=commit, origin="LOCAL_WORKTREE_NOT_CI", worktree_dirty=dirty)
    else:
        jar = args.jar_dir / "app.jar"
        metadata = json.loads((args.jar_dir / "build-metadata.json").read_text(encoding="utf-8"))
        ci.validate_identity(metadata)
        if ci.digest(jar) != metadata["jar"]["sha256"]:
            raise ValueError("Input CI JAR checksum mismatch")
        metadata = {key:metadata[key] for key in ("commit_sha", "workflow_run", "run_id", "run_attempt", "application_version")}
        metadata["origin"] = "GITHUB_ACTIONS_BUILD_ARTIFACT"
    if not re.fullmatch(r"[0-9a-f]{40}", metadata["commit_sha"]):
        raise ValueError("Invalid source commit")
    ci.validate_jar(jar)
    return jar, dict(**metadata, jar_sha256=ci.digest(jar))


def run(args) -> int:
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    report = dict(schema_version=1, status="FAIL", purpose="VERIFICATION_ONLY_NOT_PRODUCTION_DEPLOYMENT", started_at=datetime.now(timezone.utc).isoformat(), checks=[], build_status="NOT_RUN", runtime_status="NOT_RUN", published=False, registry_digest=None)
    harness = None
    engine_ready = False
    phase = "input-provenance"
    try:
        jar, report["input"] = input_jar(args)
        suffix = secrets.token_hex(6)
        project = "sf-verify-" + suffix
        image = "smartfix-verification:sha-" + report["input"]["commit_sha"] + "-" + suffix
        state = dict(project=project, image=image, resources_attempted=False)
        save(output / "state.json", state)
        report.update(project=project, image_tag=image)
        sensitive = []
        harness = Harness(output, project, image, sensitive)
        phase = "docker-preflight"
        info = harness.command(["docker", "version", "--format", "{{json .Server}}"], "docker-version", timeout=20)
        report["docker_server"] = json.loads(info.stdout)["Version"]
        engine_ready = True
        version = harness.command(["docker", "compose", "version", "--short"], "compose-version").stdout.strip()
        numbers = re.match(r"v?(\d+)\.(\d+)\.(\d+)", version)
        if not numbers or tuple(map(int, numbers.groups())) < (2,24,4):
            raise ValueError("Compose >=2.24.4 required")
        report["compose_version"] = version
        # Quiet validation does not print an interpolated configuration/password.
        harness.compose(["config", "--quiet"], "compose-config")
        phase = "image-build"
        report["build_status"] = "IN_PROGRESS"
        state["resources_attempted"] = True
        save(output / "state.json", state)
        details = build_image(harness, jar, report["input"])
        report["build_status"] = "PASS"
        report["image"] = details
        phase = "runtime-business-smoke"
        exercise(harness, report, report["input"]["jar_sha256"], args.readiness_timeout)
        report["status"] = "PASS"
    except Exception as error:
        if report["build_status"] == "IN_PROGRESS":
            report["build_status"] = "FAIL"
        if report["runtime_status"] == "IN_PROGRESS":
            report["runtime_status"] = "FAIL"
        report["failure"] = dict(phase=phase, error_type=type(error).__name__, reason="Required operation or assertion failed; see sanitized diagnostics")
        print("container verification: FAIL (" + phase + ")", file=sys.stderr)
    finally:
        if harness and engine_ready:
            try:
                report["diagnostics"] = harness.collect()
            except Exception as error:
                report["diagnostics"] = dict(status="FAIL", error_type=type(error).__name__)
            try:
                report["cleanup"] = harness.cleanup(remove_image=not (getattr(args, "retain_image", False) and report["status"] == "PASS"))
            except Exception as error:
                report["cleanup"] = dict(status="FAIL", error_type=type(error).__name__)
            if report["diagnostics"]["status"] != "PASS" or report["cleanup"]["status"] != "PASS":
                report["status"] = "FAIL"
        elif harness:
            report["diagnostics"] = dict(status="FAIL", reason="Docker preflight failed; see docker-version.log; no container logs exist")
            report["cleanup"] = dict(status="NOT_RUN", reason="Docker preflight failed before any resource creation")
        if harness:
            harness.temp.cleanup()
        report["finished_at"] = datetime.now(timezone.utc).isoformat()
        save(output / "result.json", report)
    print("container verification: " + report["status"])
    return 0 if report["status"] == "PASS" else 1


def cleanup(args) -> int:
    output = args.output.resolve()
    if not (output / "state.json").exists():
        # No generated identity means the runner never reached resource creation.
        save(output / "cleanup-final.json", dict(status="NOT_RUN", reason="No verification resource identity was created"))
        return 0
    state = json.loads((output / "state.json").read_text())
    if not state.get("resources_attempted"):
        save(output / "cleanup-final.json", dict(status="NOT_RUN", reason="Verification never attempted to create Docker resources"))
        return 0
    harness = Harness(output, state["project"], state["image"], [], digest_image=state.get("digest_image", False))
    if state.get("load_overlay"):
        harness.extra_compose = [ROOT / "perf/compose.load.yml"]
    try:
        result = harness.cleanup(remove_image=not state.get("preserve_preexisting_image", False))
        save(output / "cleanup-final.json", result)
        return 0 if result["status"] == "PASS" else 1
    finally:
        harness.temp.cleanup()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("run", "cleanup"))
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--jar-dir", type=Path, default=Path("incoming-jar"))
    source.add_argument("--local-jar", type=Path)
    parser.add_argument("--output", type=Path, default=Path("verification/container"))
    parser.add_argument("--readiness-timeout", type=int, default=180)
    parser.add_argument("--retain-image", action="store_true", help="Retain only a passing image until the authorized publish step; always run cleanup afterwards")
    args = parser.parse_args()
    if not 1 <= args.readiness_timeout <= 600:
        parser.error("readiness timeout must be 1..600 seconds")
    return run(args) if args.command == "run" else cleanup(args)


if __name__ == "__main__":
    sys.exit(main())
