"""CI artifact handling and opt-in Java SE Beanstalk deployment (stdlib only).

No Maven invocation, database credentials, environment dumps or application data
are needed by packaging/deployment. AWS mutations exist only in deploy().
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import sys
import time
from urllib.parse import urlsplit
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, Request, build_opener, urlopen
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CA_URL = "https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem"
FILES = (
    "Procfile", "config/application-prod.yml",
    ".platform/nginx/conf.d/01_upload.conf",
    ".platform/hooks/predeploy/00_validate_environment.sh",
    ".platform/hooks/predeploy/10_mount_efs.sh",
)


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def redact(text: str) -> str:
    for name, value in sorted(os.environ.items(), key=lambda item: -len(item[1])):
        if len(value) >= 4 and re.search(r"PASSWORD|SECRET|TOKEN|ACCESS_KEY|API_KEY|CREDENTIAL", name, re.I):
            text = text.replace(value, "[REDACTED]")
    text = re.sub(r"(?i)(authorization\s*[:=]\s*)(?:bearer|basic)\s+[^\s<]+", r"\1[REDACTED]", text)
    text = re.sub(r"(?i)((?:password|passwd|secret|token|(?:nvd)?api[_-]?key|jsessionid|username|displayname)[\w.-]*[\"']?\s*[=:]\s*)[\"']?[^\s<,;\"']+", r"\1[REDACTED]", text)
    text = re.sub(r"(://)[^/@\s]+:[^/@\s]+@", r"\1[REDACTED]@", text)
    text = re.sub(r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}", "[REDACTED_EMAIL]", text)
    return text


def identity() -> dict:
    sha = os.environ.get("GITHUB_SHA", "")
    run = os.environ.get("GITHUB_RUN_ID", "")
    attempt = os.environ.get("GITHUB_RUN_ATTEMPT", "")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or not run.isdigit() or not attempt.isdigit():
        raise ValueError("Missing or invalid GitHub SHA/run identity")
    if repo != "Lian9374/smartfix":
        raise ValueError("Unexpected source repository")
    return dict(commit_sha=sha, repository=repo, workflow=os.environ.get("GITHUB_WORKFLOW", ""),
                run_id=run, run_attempt=attempt,
                workflow_run=f"https://github.com/{repo}/actions/runs/{run}",
                application_version=f"smartfix-{sha[:12]}-{run}-{attempt}")


def validate_identity(metadata: dict) -> None:
    for key, value in identity().items():
        if metadata.get(key) != value:
            raise ValueError(f"Artifact identity mismatch: {key}")


def validate_jar(path: Path) -> None:
    with zipfile.ZipFile(path) as jar:
        manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8")
        if "Start-Class: com.smartfix.SmartFixApplication" not in manifest:
            raise ValueError("Expected the executable SmartFix Spring Boot JAR")


def build_metadata(jar_dir: Path, output: Path) -> None:
    jars = list(jar_dir.glob("smartfix-*.jar"))
    if len(jars) != 1:
        raise ValueError("Expected exactly one Maven-produced SmartFix JAR")
    validate_jar(jars[0])
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(jars[0], output / "app.jar")
    write_json(output / "build-metadata.json", {
        "schema_version": 1, **identity(),
        "jar": {"file": "app.jar", "sha256": digest(output / "app.jar")},
    })


def collect_reports(target: Path, log_dir: Path, output: Path) -> None:
    """Keep JUnit diagnostics but omit JVM properties and captured app output."""
    output.mkdir(parents=True, exist_ok=True)
    summary = {}
    invalid = []
    if log_dir.exists():
        for path in log_dir.glob("*.log"):
            (output / path.name).write_text(redact(path.read_text(encoding="utf-8", errors="replace")), encoding="utf-8")
    for kind in ("surefire", "failsafe"):
        destination = output / kind
        destination.mkdir(exist_ok=True)
        totals = dict(report_files=0, tests=0, failures=0, errors=0, skipped=0)
        for path in sorted((target / f"{kind}-reports").glob("TEST-*.xml")):
            try:
                root = ET.fromstring(path.read_bytes())
            except ET.ParseError:
                invalid.append(path.name)
                continue
            for suite in root.iter("testsuite"):
                for child in list(suite):
                    if child.tag in ("properties", "system-out", "system-err"):
                        suite.remove(child)
                for case in suite.findall("testcase"):
                    for child in list(case):
                        if child.tag in ("system-out", "system-err"):
                            case.remove(child)
                for key in ("tests", "failures", "errors", "skipped"):
                    totals[key] += int(suite.get(key, "0"))
            for element in root.iter():
                element.attrib = {key: redact(value) for key, value in element.attrib.items()}
                if element.text:
                    element.text = redact(element.text)
            (destination / path.name).write_text(ET.tostring(root, encoding="unicode"), encoding="utf-8")
            totals["report_files"] += 1
        summary[kind] = {**totals, "reports_present": totals["report_files"] > 0}
    # Never label a missing report PASS. The Maven step's exit code remains authoritative.
    write_json(output / "report-inventory.json", {**summary, "invalid_reports": invalid})
    if invalid:
        raise ValueError("Malformed JUnit reports; remaining reports and diagnostics were retained")


def package(jar_dir: Path, output: Path, ca_file: Path | None = None) -> dict:
    metadata = json.loads((jar_dir / "build-metadata.json").read_text(encoding="utf-8"))
    validate_identity(metadata)
    jar = jar_dir / "app.jar"
    validate_jar(jar)
    if digest(jar) != metadata["jar"]["sha256"]:
        raise ValueError("Downloaded JAR SHA-256 does not match build metadata")
    output.mkdir(parents=True, exist_ok=True)
    if ca_file is None:
        with urlopen(CA_URL, timeout=30) as response:
            ca = response.read(2_000_001)
    else:
        ca = ca_file.read_bytes()
    if len(ca) > 2_000_000 or b"PRIVATE KEY" in ca:
        raise ValueError("Invalid public RDS CA bundle")
    blocks = re.findall(rb"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", ca, re.S)
    if not blocks:
        raise ValueError("RDS CA bundle contains no public certificates")
    for block in blocks:
        ssl.PEM_cert_to_DER_cert(block.decode("ascii"))
    contents = {"app.jar": jar.read_bytes(), "certs/rds-global-bundle.pem": ca}
    for name in FILES:
        path = ROOT / "deploy/beanstalk" / name
        # Explicit allowlist: never recurse over a workspace, .env, target or uploads.
        contents[name] = path.read_bytes().replace(b"\r\n", b"\n")
        if name.startswith(".platform/hooks/"):
            contents[name.replace("/hooks/", "/confighooks/")] = contents[name]
    filename = f"{metadata['application_version']}.zip"
    archive_path = output / filename
    with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in sorted(contents.items()):
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = ((0o100755 if name.endswith(".sh") else 0o100644) << 16)
            archive.writestr(entry, content)
    with zipfile.ZipFile(archive_path) as archive:
        if set(archive.namelist()) != set(contents) or hashlib.sha256(archive.read("app.jar")).hexdigest() != metadata["jar"]["sha256"]:
            raise ValueError("ZIP contents or JAR identity changed")
    release = {**metadata, "zip": {"file": filename, "sha256": digest(archive_path)},
               "bundle_files": {name: hashlib.sha256(data).hexdigest() for name, data in sorted(contents.items())},
               "certificate_source": CA_URL}
    write_json(output / "release-metadata.json", release)
    return release


def load_release(directory: Path) -> tuple[dict, Path]:
    metadata = json.loads((directory / "release-metadata.json").read_text(encoding="utf-8"))
    validate_identity(metadata)
    filename = f"{metadata['application_version']}.zip"
    if metadata["zip"]["file"] != filename:
        raise ValueError("Unexpected release filename")
    path = directory / filename
    if digest(path) != metadata["zip"]["sha256"]:
        raise ValueError("Release ZIP SHA-256 mismatch")
    with zipfile.ZipFile(path) as archive:
        if hashlib.sha256(archive.read("app.jar")).hexdigest() != metadata["jar"]["sha256"]:
            raise ValueError("Release JAR SHA-256 mismatch")
    return metadata, path


def deployment_config() -> dict:
    required = ("AWS_REGION", "EB_APPLICATION", "EB_ENVIRONMENT", "EB_S3_BUCKET", "EB_BASE_URL")
    config = {key: os.environ.get(key, "") for key in required}
    if not all(config.values()):
        raise ValueError("Missing deployment configuration; see deploy/beanstalk/CI.md")
    url = urlsplit(config["EB_BASE_URL"])
    allow_http = os.environ.get("EB_ALLOW_HTTP_VERIFY", "false")
    if allow_http not in ("true", "false"):
        raise ValueError("EB_ALLOW_HTTP_VERIFY must explicitly be true or false")
    http_demo = url.scheme == "http" and allow_http == "true" and url.port in (None, 80)
    if (url.scheme != "https" and not http_demo) or not url.hostname or url.username or url.password or url.query or url.fragment or url.path not in ("", "/"):
        raise ValueError("Expected an HTTPS origin; existing HTTP needs explicit anonymous-verification authorization")
    config["EB_BASE_URL"] = config["EB_BASE_URL"].rstrip("/")
    return config


def require_existing_http_target(config: dict, environment: dict) -> None:
    # The HTTP exception sends no credentials and cannot target another host or
    # follow redirects. Compare with authenticated AWS metadata before any GET.
    url = urlsplit(config["EB_BASE_URL"])
    if url.scheme == "http" and url.hostname != environment.get("CNAME", "").lower():
        raise ValueError("Authorized HTTP checks must target the existing Beanstalk CNAME")


def aws(*args: str) -> dict:
    result = subprocess.run(["aws", *args, "--region", os.environ["AWS_REGION"],
                             "--no-cli-pager", "--output", "json",
                             "--cli-connect-timeout", "10", "--cli-read-timeout", "60"],
                            capture_output=True, text=True, encoding="utf-8", timeout=180)
    if result.returncode:
        # No raw AWS output: it can include request parameters or environment values.
        code = re.search(r"\(([A-Za-z0-9_.-]+)\)", result.stderr)
        detail = code.group(1) if code else str(result.returncode)
        denied = re.search(r"not authorized to perform:\s*([a-z0-9-]+:[A-Za-z0-9]+)", result.stderr)
        if denied:
            detail += "; denied_action=" + denied.group(1)
            # Fixed labels only: never export raw object keys, identities or messages.
            if "resources/_runtime/_embedded_extensions/smartfix/" in result.stderr:
                detail += "; resource_scope=smartfix_application_extensions"
            elif re.search(r"resources/environments/[a-z0-9-]+/_runtime/_embedded_extensions/smartfix/", result.stderr):
                detail += "; resource_scope=smartfix_environment_extensions"
        raise RuntimeError(f"AWS {args[0]}/{args[1]} failed: {detail}")
    return json.loads(result.stdout or "{}")


def trusted_manual_deployment() -> None:
    identity()
    if (os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch"
            or os.environ.get("GITHUB_REF") != "refs/heads/main"
            or os.environ.get("DEPLOY_REQUESTED") != "true"):
        raise ValueError("Deployment requires an explicit manual request on main")


def deploy(directory: Path, output: Path) -> None:
    trusted_manual_deployment()
    config = deployment_config()
    metadata, archive = load_release(directory)
    receipt = {**identity(), "status": "STARTED", "application": config["EB_APPLICATION"],
               "environment": config["EB_ENVIRONMENT"], "jar_sha256": metadata["jar"]["sha256"],
               "zip_sha256": metadata["zip"]["sha256"]}
    path = output / "deployment.json"
    write_json(path, receipt)
    try:
        environment = aws("elasticbeanstalk", "describe-environments", "--environment-names", config["EB_ENVIRONMENT"])["Environments"]
        if len(environment) != 1 or environment[0]["ApplicationName"] != config["EB_APPLICATION"]:
            raise ValueError("Existing Beanstalk environment/application mismatch")
        if environment[0]["Status"] != "Ready" or ":platform/Corretto 21 " not in environment[0].get("PlatformArn", ""):
            raise ValueError("Expected a Ready Java SE Corretto 21 environment")
        require_existing_http_target(config, environment[0])
        key = f"smartfix/releases/{metadata['application_version']}/{archive.name}"
        receipt["s3_key"] = key
        uploaded = aws("s3api", "put-object", "--bucket", config["EB_S3_BUCKET"], "--key", key,
                       "--body", str(archive), "--checksum-algorithm", "SHA256", "--metadata",
                       json.dumps({"commit-sha": metadata["commit_sha"], "run-id": metadata["run_id"],
                                   "jar-sha256": metadata["jar"]["sha256"], "zip-sha256": metadata["zip"]["sha256"]}))
        expected = base64.b64encode(bytes.fromhex(metadata["zip"]["sha256"])).decode("ascii")
        if uploaded.get("ChecksumSHA256") != expected:
            raise ValueError("S3 upload checksum was not confirmed")
        receipt["phase"] = "ZIP_UPLOADED"
        write_json(path, receipt)
        aws("elasticbeanstalk", "create-application-version", "--application-name", config["EB_APPLICATION"],
            "--version-label", metadata["application_version"], "--description", f"{metadata['commit_sha']} {metadata['workflow_run']}",
            "--source-bundle", json.dumps({"S3Bucket": config["EB_S3_BUCKET"], "S3Key": key}))
        receipt["phase"] = "VERSION_CREATED"
        write_json(path, receipt)
        aws("elasticbeanstalk", "update-environment", "--environment-name", config["EB_ENVIRONMENT"],
            "--version-label", metadata["application_version"])
        receipt.update(status="UPDATE_REQUESTED", phase="ENVIRONMENT_UPDATE_REQUESTED")
        write_json(path, receipt)
        print("Beanstalk update requested; deployment success requires the post-deploy job.")
    except Exception:
        receipt["status"] = "FAIL"
        write_json(path, receipt)
        raise


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def http_smoke(origin: str) -> list[dict]:
    results = []
    opener = build_opener(NoRedirect())
    for route in ("/actuator/health", "/login"):
        with opener.open(Request(origin + route, headers={"Accept": "application/json" if route.endswith("health") else "text/html"}), timeout=15) as response:
            body = response.read(2_000_001)
            if response.status != 200 or len(body) > 2_000_000:
                raise ValueError("HTTP smoke response is not a bounded HTTP 200")
            if route.endswith("health") and json.loads(body).get("status") != "UP":
                raise ValueError("Application health is not UP")
            if route == "/login" and ("text/html" not in response.headers.get("Content-Type", "") or b"<form" not in body.lower()):
                raise ValueError("Expected the login HTML form")
            results.append({"route": route, "http_status": response.status})
    return results  # No cookies, headers, CSRF tokens or response bodies are saved.


def deployment_events(config: dict, version: str) -> list[dict]:
    """Only events after this exact version was created; export no raw messages."""
    versions = aws("elasticbeanstalk", "describe-application-versions", "--application-name",
                   config["EB_APPLICATION"], "--version-labels", version).get("ApplicationVersions", [])
    if len(versions) != 1:
        return []
    start = versions[0].get("DateCreated", "")
    if not re.fullmatch(r"[0-9T:.+Z-]+", start):
        return []
    events = aws("elasticbeanstalk", "describe-events", "--environment-name", config["EB_ENVIRONMENT"],
                 "--start-time", start).get("Events", [])
    safe = []
    for event in events:
        message = event.get("Message", "")
        if event.get("Severity") not in ("ERROR", "FATAL"):
            continue
        request = event.get("RequestId", "")
        action = re.search(r"not authorized to perform: ([a-z0-9-]+:[A-Za-z0-9]+)", message)
        safe.append({"severity": event["Severity"],
                     "request_id": request if re.fullmatch(r"[0-9a-f-]{36}", request) else None,
                     "category": "deployment_failed" if message.strip() == "Failed to deploy application."
                                 else "access_denied" if action else "aws_error",
                     "denied_action": action.group(1) if action else None})
    return safe


def verify(directory: Path, output: Path, timeout: int = 600) -> None:
    trusted_manual_deployment()
    config = deployment_config()
    metadata, _ = load_release(directory)
    transport = urlsplit(config["EB_BASE_URL"]).scheme.upper()
    receipt = {**identity(), "status": "FAIL", "checks": [],
               "transport": transport, "https_transport_required": transport == "HTTPS",
               "https_transport_verified": False,
               "authenticated_http_checks": False,
               "scope": f"version, EB health, instance deployment, {transport} anonymous health and login-page smoke"}
    path = output / "post-deploy.json"
    try:
        deadline = time.monotonic() + timeout
        readiness = {"attempts": 0, "transient_http_statuses": [], "status": "WAITING"}

        def timeout_error() -> ValueError:
            if readiness["transient_http_statuses"]:
                return ValueError("Timed out waiting for version/Green health/HTTP startup readiness; last transient HTTP status "
                                  + str(readiness["transient_http_statuses"][-1]))
            return ValueError("Timed out waiting for expected version and Green environment health")

        first_poll = True
        while True:
            if not first_poll and time.monotonic() >= deadline:
                raise timeout_error()
            first_poll = False
            interval = 10
            environments = aws("elasticbeanstalk", "describe-environments", "--environment-names", config["EB_ENVIRONMENT"])["Environments"]
            if len(environments) != 1 or environments[0]["ApplicationName"] != config["EB_APPLICATION"]:
                raise ValueError("Beanstalk verification target mismatch")
            env = environments[0]
            require_existing_http_target(config, env)
            receipt["environment"] = {key: env.get(key) for key in ("EnvironmentName", "Status", "Health", "HealthStatus", "VersionLabel")}
            write_json(path, receipt)
            if env["Status"] == "Ready" and env.get("VersionLabel") == metadata["application_version"] and env.get("Health") == "Green":
                instances = aws("elasticbeanstalk", "describe-instances-health", "--environment-name", config["EB_ENVIRONMENT"], "--attribute-names", "Deployment")["InstanceHealthList"]
                if not instances or any(item.get("Deployment", {}).get("VersionLabel") != metadata["application_version"]
                                        or item.get("Deployment", {}).get("Status") != "Deployed" for item in instances):
                    raise ValueError("Not every instance has deployed the expected version")
                readiness["attempts"] += 1
                receipt["http_readiness"] = readiness
                try:
                    checks = http_smoke(config["EB_BASE_URL"])
                except HTTPError as error:
                    # Green/Info can precede Java/proxy readiness. Only temporary
                    # gateway/unavailable statuses retry; every assertion must
                    # eventually pass, under the original shared deadline.
                    if error.code not in (502, 503, 504):
                        raise
                    readiness["transient_http_statuses"].append(error.code)
                    print(f"HTTP startup not ready ({error.code}); rechecking version, health and endpoints")
                    interval = 5
                else:
                    receipt["checks"] = checks
                    readiness["status"] = "PASS"
                    receipt["https_transport_verified"] = transport == "HTTPS"
                    receipt["status"] = "PASS"
                    print(f"PASS: expected Beanstalk version, Green health and {transport} anonymous smoke checks")
                    break
            if env["Status"] == "Ready" and env.get("VersionLabel") != metadata["application_version"]:
                try:
                    receipt["aws_events"] = deployment_events(config, metadata["application_version"])
                except RuntimeError:
                    # A diagnostic lookup failure can never turn verification into PASS.
                    receipt["diagnostics_status"] = "UNAVAILABLE"
                else:
                    failed = next((event for event in receipt["aws_events"]
                                   if event["category"] == "deployment_failed"), None)
                    if failed:
                        raise ValueError("AWS reported application deployment failure; request "
                                         + (failed["request_id"] or "unavailable")
                                         + "; expected version is not running (see post-deploy.json)")
            if time.monotonic() >= deadline:
                raise timeout_error()
            write_json(path, receipt)
            time.sleep(min(interval, max(0, deadline - time.monotonic())))
    finally:
        write_json(path, receipt)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("redact", "reports", "build", "package", "check-config", "deploy", "verify"))
    parser.add_argument("--jar-dir", type=Path, default=Path("target"))
    parser.add_argument("--release-dir", type=Path, default=Path("release"))
    parser.add_argument("--target", type=Path, default=Path("target"))
    parser.add_argument("--log-dir", type=Path, default=Path("ci-logs"))
    parser.add_argument("--output", type=Path, default=Path("verification"))
    parser.add_argument("--timeout", type=int, default=600)
    args = parser.parse_args()
    try:
        if args.command == "redact":
            for line in sys.stdin:
                print(redact(line), end="", flush=True)
        elif args.command == "reports":
            collect_reports(args.target, args.log_dir, args.output)
        elif args.command == "build":
            build_metadata(args.jar_dir, args.output)
        elif args.command == "package":
            package(args.jar_dir, args.output)
        elif args.command == "check-config":
            trusted_manual_deployment()
            deployment_config()
        elif args.command == "deploy":
            deploy(args.release_dir, args.output)
        elif args.command == "verify":
            verify(args.release_dir, args.output, args.timeout)
        return 0
    except Exception as error:
        message = redact(f"{type(error).__name__}: {error}")
        if args.command != "redact":
            write_json(args.output / "failure.json", {"stage": args.command, "status": "FAIL", "error": message})
        print(message, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
