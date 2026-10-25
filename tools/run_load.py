"""Repeatable read-only k6 benchmark in a disposable local Compose environment.

Fixtures are synthetic, created BEFORE measurement through actual CSRF forms.
Only the uniquely labelled project is destroyed. No AWS/production target mode.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import secrets
import tempfile
import threading
import time
from urllib.parse import urljoin, urlsplit
import zipfile

import container_verify as cv

ROOT = cv.ROOT
K6_IMAGE = "grafana/k6:1.8.2@sha256:bd6ae8607c0a05d0dc0dc3f5753c464b0929baf083ef0f20b7eed845a3d9e6b4"
MARKER = "smartfix-load-fixture"


def loadgen_user():
    # Linux runners bind-mount a private (0700) input directory. Run k6 with
    # its owner UID/GID, rather than exposing credentials via chmod 0755.
    return f"{os.getuid()}:{os.getgid()}" if hasattr(os, "getuid") else "12345:12345"


def now():
    return datetime.now(timezone.utc).isoformat()


class Locations(HTMLParser):
    def __init__(self):
        super().__init__()
        self.inside = False
        self.ids = []

    def handle_starttag(self, tag, attrs):
        data = dict(attrs)
        if tag == "select":
            self.inside = data.get("name") == "locationId"
        if self.inside and tag == "option" and re.fullmatch(r"[1-9][0-9]*", data.get("value", "")):
            self.ids.append(data["value"])

    def handle_endtag(self, tag):
        if tag == "select":
            self.inside = False


def post(browser, path, fields, prefix):
    code, headers, _ = browser.request(path, fields)
    raw = headers.get("Location", "")
    target = urlsplit(urljoin(browser.base, raw))
    same_origin = target.netloc == urlsplit(browser.base).netloc
    location = target.path + ("?" + target.query if target.query else "")
    # No response bodies or values are emitted on a fixture failure.
    match = location == prefix if prefix in ("/", "/login") else location.startswith(prefix)
    if code != 302 or not raw or not same_origin or not match:
        raise RuntimeError(f"Fixture operation {path}: expected redirect, HTTP {code}")


def seed(base, count, sensitive):
    accounts = []
    for index in range(count):
        browser = cv.Browser(base, sensitive)
        username = "load." + secrets.token_hex(8)
        password = "Sf9_" + secrets.token_urlsafe(24)
        display = "Load fixture " + secrets.token_hex(4)
        sensitive.extend([username, password, display])
        code, _, page = browser.request("/register")
        if code != 200:
            raise RuntimeError("Registration fixture form unavailable")
        post(browser, "/register", dict(username=username, displayName=display, password=password, confirmPassword=password, _csrf=browser.csrf(page)), "/login")
        _, _, page = browser.request("/login")
        post(browser, "/login", dict(username=username, password=password, accountType="REQUESTER", _csrf=browser.csrf(page)), "/")
        # Confirm authentication instead of accepting /login?error as success.
        code, _, page = browser.request("/requests/new")
        parser = Locations()
        parser.feed(page)
        if code != 200 or not parser.ids:
            raise RuntimeError("Authenticated request form/location catalogue unavailable")
        location = parser.ids[-1]
        for item in range(10):
            if item:
                _, _, page = browser.request("/requests/new")
            post(browser, "/requests", dict(locationId=location, title=f"{MARKER} repair {index}-{item}", description=f"Synthetic isolated load test repair {index}-{item}", category="ELECTRICAL", urgencyLevel="MEDIUM", _csrf=browser.csrf(page)), "/requests/")
        for item in range(3):
            _, _, page = browser.request("/community/questions/new")
            post(browser, "/community/questions", dict(title=f"{MARKER} question {index}-{item}", body=f"Synthetic isolated hardware question number {index}-{item}; safe fixture content only.", category="HARDWARE", _csrf=browser.csrf(page)), "/community/questions/")
        accounts.append(dict(username=username, password=password))
        print(f"Prepared isolated fixture account {index + 1}/{count}", flush=True)
    return accounts


def sql(h, query, name):
    r = h.compose(["exec", "-T", "db", "psql", "-U", "smartfix_verify", "-d", "smartfix_container_test", "-tAXc", query], name, timeout=15)
    return json.loads(r.stdout.strip())


def memory_bytes(value):
    match = re.fullmatch(r"([0-9.]+)([KMGT]?i?B)", value.strip())
    if not match:
        raise ValueError("Unknown Docker memory unit")
    powers = {"B": 1, "kB": 1000, "KB": 1000, "MB": 1000**2, "GB": 1000**3,
              "KiB": 1024, "MiB": 1024**2, "GiB": 1024**3, "TiB": 1024**4}
    return round(float(match[1]) * powers[match[2]])


class Sampler:
    def __init__(self, h, output, ids):
        self.h, self.output, self.ids = h, output, ids
        self.stop = threading.Event()
        self.samples, self.errors = [], []
        self.thread = threading.Thread(target=self.loop, daemon=True)

    def loop(self):
        while not self.stop.is_set():
            sample = {"time_utc": now()}
            try:
                r = self.h.command(["docker", "stats", "--no-stream", "--format", "{{json .}}", *self.ids.values()], "resource-latest", timeout=15)
                stats = {}
                for line in r.stdout.splitlines():
                    item = json.loads(line)
                    service = next(k for k, v in self.ids.items() if v.startswith(item["ID"]))
                    stats[service] = dict(cpu_percent=float(item["CPUPerc"].rstrip("%")), memory_bytes=memory_bytes(item["MemUsage"].split("/")[0]))
                if set(stats) != set(self.ids):
                    raise ValueError("Incomplete container resource sample")
                sample["containers"] = stats
                sample["database"] = sql(self.h, "SELECT json_build_object('total',count(*),'active',count(*) FILTER (WHERE state='active'),'idle',count(*) FILTER (WHERE state='idle')) FROM pg_stat_activity WHERE datname=current_database() AND backend_type='client backend' AND pid<>pg_backend_pid()", "connections-latest")
                self.samples.append(sample)
            except Exception as error:
                self.errors.append(dict(time_utc=now(), error=type(error).__name__))
            if self.stop.wait(5):
                break

    def finish(self):
        self.stop.set()
        self.thread.join(35)
        if self.thread.is_alive():
            self.errors.append({"error": "Sampler did not finish"})
        result = dict(status=("PARTIAL" if self.errors else "AVAILABLE") if self.samples else "UNAVAILABLE", interval="approximately 5s plus collection time", samples=self.samples, errors=self.errors)
        if self.samples:
            result["sampled_maxima"] = {
                service: dict(cpu_percent=max(x["containers"][service]["cpu_percent"] for x in self.samples), memory_bytes=max(x["containers"][service]["memory_bytes"] for x in self.samples)) for service in self.ids
            }
            result["max_database_connections"] = max(x["database"]["total"] for x in self.samples)
        cv.save(self.output / "resources.json", result)
        return result


def normalize(summary, exit_code):
    """Fail closed on missing/non-finite measurements or absent thresholds."""
    metrics = summary["metrics"]
    def values(key):
        return metrics[key]["values"]
    def group(suffix=""):
        count = values("business_requests" + suffix)["count"]
        latency = values("business_latency" + suffix)
        error = values("business_errors" + suffix)["rate"]
        if count <= 0 or any(not isinstance(x, (int, float)) or not __import__('math').isfinite(x) for x in (count, latency["p(95)"], latency["p(99)"], error)):
            raise ValueError("Missing or invalid measured samples")
        return dict(requests=count, requests_per_second=values("business_requests" + suffix)["rate"], p95_ms=latency["p(95)"], p99_ms=latency["p(99)"], error_rate=error)
    thresholds = {name + ":" + rule: bool(outcome["ok"]) for name, metric in metrics.items() for rule, outcome in metric.get("thresholds", {}).items()}
    if not thresholds:
        raise ValueError("No threshold decisions produced")
    return dict(status="PASS" if exit_code == 0 and all(thresholds.values()) else "FAIL", exit_code=exit_code,
                measured_duration_seconds=summary["state"]["testRunDurationMs"] / 1000,
                business=group(), endpoints={name: group("{endpoint:" + name + "}") for name in ("map", "requests", "community")},
                all_http_requests=values("http_reqs")["count"], auth_requests=values("auth_requests")["count"],
                protocol_error_rate=values("http_req_failed{kind:business}")["rate"],
                error_types={key.removeprefix("errors_"): metric["values"]["count"] for key, metric in metrics.items() if key.startswith("errors_")}, thresholds=thresholds)


def render(result, output):
    lines = ["# SmartFix local load test", "", f"Status: **{result['status']}**. Local disposable Docker only; **not AWS performance**.", "",
             "Metadata and exact input SHA-256: `result.json`; predefined targets: `policy.json`; raw k6 points and summaries: each phase directory.", "",
             "Targets fixed before execution: formal load, global AND each endpoint p95 ≤1000ms, p99 ≤2000ms; business/protocol errors <1%; ≥100 samples/endpoint; zero authentication failures.", "",
             "Closed model: one REQUESTER session per VU; map → own requests → community search, then configured think time. Authentication/setup are excluded from business latency/error metrics; business throughput uses full phase elapsed time including login. This is HTTP testing, not browser rendering.", "",
             f"Started UTC: {result['started_utc']}; finished UTC: {result.get('finished_utc', 'incomplete')}",
             f"Load configuration: {result['configuration']}", ""]
    if result.get("input"):
        lines += ["Version/provenance: " + result.get("application_version", "UNKNOWN") + "; " + json.dumps(result["input"]) + ".", ""]
    if result.get("dataset"):
        lines += ["Actual fixture counts: " + json.dumps(result["dataset"]) + ".", ""]
    if result.get("environment"):
        env = result["environment"]
        lines += [f"Environment: {env.get('OperatingSystem')}, kernel {env.get('KernelVersion')}, Docker {env.get('ServerVersion')}; shared engine {env.get('NCPU')} CPUs, {env.get('MemTotal', 0)/1024**3:.2f}GiB. App2CPU/1GiB; DB1CPU/512MiB; generator1CPU/512MiB; Hikari10.", ""]
    for name in ("smoke", "warmup", "load"):
        phase = result.get("phases", {}).get(name, {"status": "NOT_RUN"})
        lines += [f"## {name}: {phase['status']}", ""]
        if "business" in phase:
            b = phase["business"]
            lines += [f"Measured {phase['measured_duration_seconds']:.2f}s; business requests {b['requests']}; throughput {b['requests_per_second']:.2f}/s; p95 {b['p95_ms']:.2f}ms; p99 {b['p99_ms']:.2f}ms; errors {b['error_rate']:.3%}.", ""]
            for endpoint, stats in phase["endpoints"].items():
                lines.append(f"- {endpoint}: {stats['requests']} requests, {stats['requests_per_second']:.2f}/s, p95 {stats['p95_ms']:.2f}ms, p99 {stats['p99_ms']:.2f}ms, errors {stats['error_rate']:.3%}.")
            lines += ["", "Error types: " + json.dumps(phase["error_types"]) + ".", ""]
        resources = phase.get("resources", {})
        lines += ["Resource collection: " + resources.get("status", "UNAVAILABLE") + ".", ""]
        for service, stats in resources.get("sampled_maxima", {}).items():
            lines += [f"- {service} sampled maximum: CPU {stats['cpu_percent']:.2f}% (100%=one CPU), memory {stats['memory_bytes']/1024**2:.2f}MiB."]
        if resources.get("samples"):
            lines += [f"- Database client connections sampled max {resources['max_database_connections']} (monitor excluded); {len(resources['samples'])} samples. Short active queries may fall between samples.", ""]
    if result.get("failure"):
        lines += ["Failure: " + result["failure"], ""]
    lines += ["## Scope and limitations", "", "Synthetic fixture sizes and host/container limits are recorded in result.json. Shared Docker Desktop host; small data set; fixed closed concurrency and query mix; no capacity limit or production SLA conclusion. DB connections sampled via pg_stat_activity, CPU/memory via Docker stats. Sampling misses short peaks. No external email, AWS traffic or production data.", "", "Cleanup: " + json.dumps(result.get("cleanup", {"status": "NOT_COMPLETED"})), ""]
    (output / "report.md").write_text("\n".join(lines), encoding="utf-8")


def run(args):
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if (output / "result.json").exists() or (output / "state.json").exists():
        raise ValueError("Use a new output directory for each run; never overwrite evidence")
    if not 5 <= args.vus <= 50 or not 60 <= args.duration_seconds <= 600:
        raise ValueError("Formal load requires 5..50 VUs and 60..600 seconds")
    if not 0 <= args.think_seconds <= 10:
        raise ValueError("Think time must be within 0..10 seconds")
    policy_path = ROOT / "perf/policy.json"
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    policy_hash = cv.ci.digest(policy_path)
    (output / "policy.json").write_bytes(policy_path.read_bytes())
    jar, metadata = cv.input_jar(args)
    token = secrets.token_hex(6)
    project = "sf-verify-" + token
    # input_jar uses commit_sha in build metadata.
    sha = metadata["commit_sha"]
    image = f"smartfix-verification:sha-{sha}-{token}"
    h = cv.Harness(output, project, image, [])
    h.extra_compose = [ROOT / "perf/compose.load.yml"]
    state = dict(project=project, image=image, resources_attempted=True, load_overlay=True)
    cv.save(output / "state.json", state)
    result = dict(status="FAIL", started_utc=now(), origin="LOCAL_DOCKER_NOT_AWS" if not os.environ.get("GITHUB_ACTIONS") else "TEMPORARY_CI_RUNNER_NOT_AWS", input=metadata, jar_sha256=cv.ci.digest(jar), policy_sha256=policy_hash, configuration=dict(vus=args.vus, duration_seconds=args.duration_seconds, think_seconds=args.think_seconds, model="closed constant-vus", requests_per_user=10, questions_per_user=3), phases={})
    # Save the plan before build, fixture writes or any measured requests.
    cv.save(output / "plan.json", result)
    try:
        print("Building isolated runtime from existing tested JAR", flush=True)
        result["image"] = cv.build_image(h, jar, metadata)
        info = h.command(["docker", "info", "--format", '{"NCPU":{{json .NCPU}},"MemTotal":{{json .MemTotal}},"OperatingSystem":{{json .OperatingSystem}},"KernelVersion":{{json .KernelVersion}},"ServerVersion":{{json .ServerVersion}}}'], "host-environment")
        # Request selected fields only, never registry/account/proxy details.
        host = json.loads(info.stdout)
        result["environment"] = {key: host.get(key) for key in ("NCPU", "MemTotal", "OperatingSystem", "KernelVersion", "ServerVersion")}
        result["environment"].update(app_cpus=2, app_memory_bytes=1024**3, db_cpus=1, db_memory_bytes=512*1024**2, hikari_pool=10, k6_cpus=1, k6_memory_bytes=512*1024**2, k6_image=K6_IMAGE, shared_host=True)
        with zipfile.ZipFile(jar) as archive:
            manifest = archive.read("META-INF/MANIFEST.MF").decode()
            result["application_version"] = next((line.split(": ", 1)[1] for line in manifest.splitlines() if line.startswith("Implementation-Version: ")), "UNKNOWN")
        h.compose(["up", "-d", "--no-build", "app"], "start", timeout=180)
        port = h.compose(["port", "app", "8080"], "app-port").stdout.strip()
        browser = cv.Browser("http://" + port, h.sensitive)
        cv.wait_ready(browser, 180, result)
        ids = {service: h.compose(["ps", "-q", service], service + "-id").stdout.strip() for service in ("app", "db")}
        # Assert the actual running JAR, not just a Docker build label.
        runtime_hash = h.compose(["exec", "-T", "app", "sha256sum", "/app/app.jar"], "running-jar").stdout.split()[0]
        if runtime_hash != result["jar_sha256"]:
            raise RuntimeError("Running JAR checksum mismatch")
        result["running_jar_sha256"] = runtime_hash
        accounts = seed(browser.base, args.vus, h.sensitive)
        result["dataset"] = sql(h, "SELECT json_build_object('users',(SELECT count(*) FROM users),'requests',(SELECT count(*) FROM maintenance_requests),'community_questions',(SELECT count(*) FROM community_questions),'locations',(SELECT count(*) FROM locations))", "fixture-counts")
        if result["dataset"]["requests"] != args.vus * 10 or result["dataset"]["community_questions"] != args.vus * 3:
            raise RuntimeError("Fixture count mismatch")
        with tempfile.TemporaryDirectory(prefix="smartfix-k6-input-") as temp:
            inputs = Path(temp)
            cv.save(inputs / "accounts.json", accounts)
            (inputs / "policy.json").write_bytes(policy_path.read_bytes())
            for profile in ("smoke", "warmup", "load"):
                phase_dir = output / profile
                phase_dir.mkdir()
                phase_dir.chmod(0o777)
                cfg = dict(base_url="http://app:8080", allowed_hosts=["app"], profile=profile, vus=args.vus, duration_seconds=args.duration_seconds, think_seconds=args.think_seconds, fixture_marker=MARKER, search_term=MARKER)
                cv.save(inputs / "config.json", cfg)
                print(f"Starting {profile} profile", flush=True)
                phase_started = now()
                sampler = Sampler(h, phase_dir, ids)
                sampler.thread.start()
                try:
                    response = h.command(["docker", "run", "--rm", "--user", loadgen_user(), "--name", project + "-k6", "--network", project + "_default", "--cpus", "1", "--memory", "512m", "--mount", f"type=bind,src={ROOT / 'perf'},dst=/scripts,readonly", "--mount", f"type=bind,src={inputs},dst=/input,readonly", "--mount", f"type=bind,src={phase_dir},dst=/results", K6_IMAGE, "run", "--quiet", "--no-color", "--out", "json=/results/points.json", "/scripts/read_queries.js"], profile + "-k6", timeout=args.duration_seconds + 150, required=False)
                finally:
                    resources = sampler.finish()
                phase = dict(status="FAIL", started_utc=phase_started, finished_utc=now(), exit_code=response.returncode, resources=resources)
                result["phases"][profile] = phase
                if (phase_dir / "summary.json").exists():
                    phase.update(normalize(json.loads((phase_dir / "summary.json").read_text()), response.returncode))
                cv.save(phase_dir / "result.json", phase)
                print(f"{profile}: {phase['status']}", flush=True)
                if phase["status"] != "PASS":
                    raise RuntimeError(profile + " profile failed; later profiles were not run")
        if cv.ci.digest(policy_path) != policy_hash:
            raise RuntimeError("Policy changed during execution")
        result["status"] = "PASS"
    except Exception as error:
        result["failure"] = cv.scrub(str(error), h.sensitive)
        print("Load test failed: " + result["failure"], flush=True)
    finally:
        h.command(["docker", "rm", "-f", project + "-k6"], "cleanup-loadgen", required=False)
        try:
            result["diagnostics"] = h.collect()
        except Exception as error:
            result["diagnostics"] = dict(status="FAIL", error_type=type(error).__name__)
        try:
            result["cleanup"] = h.cleanup()
        except Exception as error:
            result["cleanup"] = dict(status="FAIL", error_type=type(error).__name__)
        if result["cleanup"]["status"] != "PASS" or result["diagnostics"]["status"] != "PASS":
            result["status"] = "FAIL"
        result["finished_utc"] = now()
        cv.save(output / "result.json", result)
        render(result, output)
        h.temp.cleanup()
    return 0 if result["status"] == "PASS" else 1


def cleanup(args):
    state_path = args.output / "state.json"
    if state_path.exists():
        state = json.loads(state_path.read_text())
        h = cv.Harness(args.output, state["project"], state["image"], [])
        try:
            h.command(["docker", "rm", "-f", h.project + "-k6"], "cleanup-loadgen-final", required=False)
        finally:
            h.temp.cleanup()
    return cv.cleanup(args)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    execute = sub.add_parser("run")
    execute.add_argument("--local-jar", type=Path)
    execute.add_argument("--jar-dir", type=Path, default=Path("incoming-jar"))
    execute.add_argument("--output", type=Path, required=True)
    execute.add_argument("--vus", type=int, default=10)
    execute.add_argument("--duration-seconds", type=int, default=120)
    execute.add_argument("--think-seconds", type=float, default=1)
    remove = sub.add_parser("cleanup")
    remove.add_argument("--output", type=Path, required=True)
    parsed = parser.parse_args()
    raise SystemExit(run(parsed) if parsed.command == "run" else cleanup(parsed))
