"""Pull-based local CD: execute only a verified current-main CI artifact."""
import argparse
import hashlib
import json
import io
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time
from urllib.request import urlopen
import zipfile

REPO = "Lian9374/smartfix"
WORKFLOW = ".github/workflows/smartfix-ci.yml"
SOURCE = Path(__file__).resolve().parent


def command(*args, capture=False, env=None, timeout=120):
    result = subprocess.run(args, check=True, text=True, capture_output=capture, env=env, timeout=timeout)
    return result.stdout.strip() if capture else None


def api(path):
    return json.loads(command("gh", "api", f"repos/{REPO}/{path}", capture=True))


def require_trusted_run(run, main_sha):
    expected = {
        "event": "push", "head_branch": "main", "head_sha": main_sha,
        "status": "completed", "conclusion": "success", "path": WORKFLOW,
    }
    for key, value in expected.items():
        if run.get(key) != value:
            raise ValueError(f"Not deployable: CI {key} does not match trusted current main")
    if run.get("head_repository", {}).get("full_name") != REPO:
        raise ValueError("Not deployable: unexpected source repository")


def application_healthy():
    try:
        with urlopen("http://127.0.0.1:18080/actuator/health", timeout=5) as response:
            return response.status == 200 and json.load(response).get("status") == "UP"
    except Exception:
        return False


def deploy(state):
    main_sha = api("branches/main")["commit"]["sha"]
    runs = api("actions/workflows/smartfix-ci.yml/runs?branch=main&event=push&per_page=1")["workflow_runs"]
    if not runs:
        print("WAIT: no main push CI run", flush=True)
        return
    run = runs[0]
    try:
        require_trusted_run(run, main_sha)
    except ValueError as error:
        print(f"WAIT: {error}", flush=True)
        return
    release = f"{run['id']}-{run['run_attempt']}"
    current_file = state / "current.json"
    if current_file.exists() and json.loads(current_file.read_text())["release"] == release and application_healthy():
        print(f"UNCHANGED: verified release {release}", flush=True)
        return
    jobs = api(f"actions/runs/{run['id']}/attempts/{run['run_attempt']}/jobs?per_page=100")["jobs"]
    for name in ("SWE5006 Build and Test", "SWE5006 PostgreSQL Integration"):
        matching = [job for job in jobs if job["name"] == name]
        if len(matching) != 1 or matching[0]["conclusion"] != "success":
            raise RuntimeError(f"Required CI job has not passed: {name}")
    artifact_name = f"swe5006-jar-{release}"
    artifacts = api(f"actions/runs/{run['id']}/artifacts?per_page=100")["artifacts"]
    matching = [a for a in artifacts if a["name"] == artifact_name and not a["expired"]]
    if len(matching) != 1:
        raise RuntimeError("Exactly one non-expired verified JAR artifact is required")
    directory = state / "releases" / release
    directory.mkdir(parents=True, exist_ok=True)
    archive = directory / "artifact.zip"
    with archive.open("wb") as output:
        subprocess.run(["gh", "api", f"repos/{REPO}/actions/artifacts/{matching[0]['id']}/zip"],
                       stdout=output, check=True, timeout=600)
    with zipfile.ZipFile(archive) as bundle:
        jars = [entry for entry in bundle.infolist() if entry.filename.endswith(".jar")]
        if len(jars) != 1:
            raise RuntimeError("Expected exactly one JAR in the CI artifact")
        jar = bundle.read(jars[0])
    with zipfile.ZipFile(io.BytesIO(jar)) as boot:
        manifest = boot.read("META-INF/MANIFEST.MF").decode()
        if "Start-Class: com.smartfix.SmartFixApplication" not in manifest:
            raise RuntimeError("Artifact is not the SmartFix Spring Boot application")
    (directory / "app.jar").write_bytes(jar)
    (directory / "Dockerfile").write_bytes((SOURCE / "Dockerfile").read_bytes())
    envfile = state / "demo.env"
    if not envfile.exists():
        envfile.write_text(f"DEMO_DB_PASSWORD={secrets.token_urlsafe(32)}\n"
                           f"DEMO_ADMIN_PASSWORD=Demo9_{secrets.token_urlsafe(20)}\n")
    image = f"smartfix-swe5006-demo:{release}"
    environment = dict(os.environ, SMARTFIX_DEMO_IMAGE=image)
    compose = ["docker", "compose", "--env-file", str(envfile), "-f", str(SOURCE / "compose.yml")]
    command("docker", "info", capture=True)
    command("docker", "build", "--label", f"org.opencontainers.image.revision={main_sha}",
            "-t", image, str(directory), timeout=900)
    # CI completion alone is insufficient if main advanced while downloading/building.
    if api("branches/main")["commit"]["sha"] != main_sha:
        raise RuntimeError("Main advanced during preparation; retry after its CI succeeds")
    previous = json.loads(current_file.read_text()) if current_file.exists() else None
    if previous:
        (state / "previous.json").write_text(json.dumps(previous, indent=2))
    try:
        command(*compose, "up", "-d", "--wait", "--wait-timeout", "120", env=environment, timeout=180)
        deadline = time.monotonic() + 180
        while True:
            try:
                with urlopen("http://127.0.0.1:18080/actuator/health", timeout=5) as response:
                    if response.status == 200 and json.load(response).get("status") == "UP":
                        break
            except Exception:
                pass
            if time.monotonic() >= deadline:
                raise RuntimeError("Application failed HTTP health check")
            time.sleep(3)
        with urlopen("http://127.0.0.1:18080/login", timeout=10) as response:
            if response.status != 200:
                raise RuntimeError("Login page smoke check failed")
    except Exception:
        with (directory / "failure.log").open("w") as log:
            subprocess.run([*compose, "logs", "--no-color", "--tail", "200"],
                           stdout=log, stderr=subprocess.STDOUT, env=environment)
        raise
    evidence = dict(release=release, commit=main_sha, image=image, artifact_id=matching[0]["id"],
                    jar_sha256=hashlib.sha256(jar).hexdigest(), ci_url=run["html_url"],
                    deployed_at=time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                    health="PASS", login="PASS", url="http://localhost:18080")
    (directory / "deployment.json").write_text(json.dumps(evidence, indent=2))
    pending = state / "current.pending"
    pending.write_text(json.dumps(evidence, indent=2))
    pending.replace(current_file)
    print(f"PASS: deployed {main_sha[:7]} to http://localhost:18080", flush=True)
    print(f"Local demo login: demo.admin; password is in {envfile} (never commit it)", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stop", action="store_true", help="request a clean stop of the local watch agent")
    parser.add_argument("--watch", action="store_true", help="poll every 30 seconds until Ctrl+C")
    parser.add_argument("--state-dir", type=Path, default=Path.home() / ".smartfix-local-cd")
    args = parser.parse_args()
    args.state_dir.mkdir(parents=True, exist_ok=True)
    stop_file = args.state_dir / "stop.requested"
    if args.stop:
        stop_file.touch()
        return 0
    lock = args.state_dir / "agent.lock"
    try:
        descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except FileExistsError:
        raise SystemExit(f"Another agent may be running. Inspect {lock} before removing a stale lock.")
    os.write(descriptor, str(os.getpid()).encode())
    os.close(descriptor)
    stop_file.unlink(missing_ok=True)
    try:
        while True:
            try:
                deploy(args.state_dir)
            except Exception as error:
                print(f"FAIL: {error}", file=sys.stderr, flush=True)
                if not args.watch:
                    return 1
            if not args.watch:
                return 0
            for _ in range(30):
                if stop_file.exists():
                    stop_file.unlink(missing_ok=True)
                    return 0
                time.sleep(1)
    except KeyboardInterrupt:
        return 0
    finally:
        lock.unlink(missing_ok=True)


if __name__ == "__main__":
    sys.exit(main())
