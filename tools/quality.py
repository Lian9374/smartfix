"""Fail-closed validation of JaCoCo, SpotBugs and Dependency-Check reports.

This is a report/credential guard, not a second scanner. No secret value is
printed, serialized or passed on a Maven command line.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone, timedelta
import importlib.util
import json
import math
import os
from pathlib import Path, PurePosixPath
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def save(path: Path, data: dict) -> dict:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return data


def counter(node: ET.Element, name: str) -> dict:
    value = node.find(f"counter[@type='{name}']")
    if value is None:
        raise ValueError(f"Missing JaCoCo {name} counter")
    covered, missed = int(value.get("covered")), int(value.get("missed"))
    if covered < 0 or missed < 0 or covered + missed == 0:
        raise ValueError("Empty or invalid coverage counter")
    return dict(covered=covered, missed=missed, percent=100 * covered / (covered + missed))


def coverage(report: Path, policy: Path) -> dict:
    root = ET.parse(report).getroot()
    if root.tag != "report" or not root.findall("sessioninfo"):
        raise ValueError("JaCoCo execution sessions are missing")
    config = json.loads(policy.read_text(encoding="utf-8"))
    rules = config["core_packages"]
    if not rules:
        raise ValueError("Core coverage policy is empty")
    packages = {item.get("name"): item for item in root.findall("package")}
    required = {name for name in packages if name.endswith("/service") or name == "com/smartfix/auth/security"}
    if required - rules.keys():
        raise ValueError("A business service package has no coverage gate; review the policy")
    result = dict(gate="coverage", status="PASS", project={kind: counter(root, kind) for kind in ("LINE", "BRANCH")}, core_packages={}, violations=[])
    if set(config.get("project", {})) != {"LINE", "BRANCH"}:
        raise ValueError("Project line and branch coverage limits are required")
    for kind, minimum in config["project"].items():
        current = result["project"][kind]
        if not 0 < minimum <= 100:
            raise ValueError("Invalid project coverage limit")
        if current["covered"] * 100 < minimum * (current["covered"] + current["missed"]):
            result["violations"].append(dict(package="PROJECT", counter=kind, minimum_percent=minimum, actual_percent=current["percent"]))
    for package, minima in rules.items():
        if not minima:
            raise ValueError("A business package has no coverage limits")
        if package not in packages:
            raise ValueError(f"Required business package missing: {package}")
        metrics = {kind: counter(packages[package], kind) for kind in minima}
        result["core_packages"][package] = metrics
        for kind, minimum in minima.items():
            if kind not in ("LINE", "BRANCH") or not 0 < minimum <= 100:
                raise ValueError("Invalid coverage policy limit")
            current = metrics[kind]
            if current["covered"] * 100 < minimum * (current["covered"] + current["missed"]):
                result["violations"].append(dict(package=package, counter=kind, minimum_percent=minimum, actual_percent=current["percent"]))
    if result["violations"]:
        result["status"] = "FAIL"
    return result


def static_report(report: Path) -> dict:
    root = ET.parse(report).getroot()
    if root.tag != "BugCollection" or root.find("FindBugsSummary") is None:
        raise ValueError("SpotBugs analysis report/summary missing")
    summary = root.find("FindBugsSummary")
    if int(summary.get("total_classes", "0")) == 0:
        raise ValueError("SpotBugs analyzed zero classes")
    errors = root.find("Errors")
    if errors is None or int(errors.get("errors", "0")) or int(errors.get("missingClasses", "0")) or list(errors):
        raise ValueError("SpotBugs reported analysis errors or missing classes")
    bugs = []
    for item in root.findall("BugInstance"):
        priority = int(item.get("priority"))
        location = item.find("SourceLine")
        method = item.find("Method")
        bugs.append(dict(pattern=item.get("type"), priority=priority,
                         classname=item.find("Class").get("classname"),
                         method=method.get("name") if method is not None else None,
                         source=location.get("sourcepath") if location is not None else None,
                         line=location.get("start") if location is not None else None))
    blocking = sum(item["priority"] <= 2 for item in bugs)
    return dict(gate="static", status="FAIL" if blocking else "PASS", scan_status="COMPLETED", analyzed_classes=int(summary.get("total_classes")), blocking=blocking, findings=bugs)


def security_preflight() -> dict:
    if not os.environ.get("NVD_API_KEY", "").strip():
        return dict(gate="security", status="FAIL", scan_status="NOT_RUN", reason="Missing required NVD_API_KEY; no secret value is logged or archived")
    return dict(gate="security", status="READY", scan_status="NOT_RUN")


def security_report(report: Path, now: datetime | None = None) -> dict:
    data = json.loads(report.read_text(encoding="utf-8"))
    info = data.get("scanInfo", {})
    if not info.get("engineVersion") or info.get("analysisExceptions"):
        raise ValueError("Dependency scanner did not complete without errors")
    dependencies = data.get("dependencies")
    if not isinstance(dependencies, list) or not dependencies:
        raise ValueError("Dependency scan contains no analyzed dependencies")
    stamps = [item["timestamp"] for item in info.get("dataSource", []) if item.get("name") in ("NVD API Last Checked", "NVD Cache Last Checked")]
    if not stamps:
        raise ValueError("NVD database freshness evidence missing")
    now = now or datetime.now(timezone.utc)
    times = [datetime.fromisoformat(stamp.replace("Z", "+00:00")) for stamp in stamps]
    if any(stamp.tzinfo is None for stamp in times):
        raise ValueError("NVD timestamp has no timezone")
    age = now - max(times)
    if age > timedelta(hours=4) or age < -timedelta(minutes=5):
        raise ValueError("NVD database is stale or its timestamp is invalid")
    blocking = []
    for dependency in dependencies:
        for vulnerability in dependency.get("vulnerabilities", []):
            scores = []
            for metric, field in (("cvssv4", "baseScore"), ("cvssv3", "baseScore"), ("cvssv2", "score")):
                if field in vulnerability.get(metric, {}):
                    score = float(vulnerability[metric][field])
                    if not math.isfinite(score) or not 0 <= score <= 10:
                        raise ValueError("Invalid CVSS score")
                    scores.append(score)
            if scores and max(scores) >= 7 or vulnerability.get("knownExploitedVulnerability"):
                blocking.append(dict(dependency=dependency.get("fileName"), vulnerability=vulnerability.get("name"), cvss=max(scores) if scores else None))
            elif not scores:
                blocking.append(dict(dependency=dependency.get("fileName"), vulnerability=vulnerability.get("name"), reason="High/unknown severity without a usable CVSS score"))
    return dict(gate="security", status="FAIL" if blocking else "PASS", scan_status="COMPLETED", dependencies=len(dependencies), nvd_last_checked=max(times).isoformat(), blocking=blocking)


def unpack_classes(directory: Path, output: Path) -> dict:
    # Reuse the exact same provenance checks as release packaging.
    spec = importlib.util.spec_from_file_location("release_ci", ROOT / "deploy/beanstalk/ci.py")
    ci = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(ci)
    metadata = json.loads((directory / "build-metadata.json").read_text(encoding="utf-8"))
    ci.validate_identity(metadata)
    jar = directory / "app.jar"
    if ci.digest(jar) != metadata["jar"]["sha256"]:
        raise ValueError("Static analysis JAR provenance mismatch")
    output.mkdir(parents=True, exist_ok=True)
    count = 0
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            prefix = "BOOT-INF/classes/"
            if not name.startswith(prefix) or not name.endswith(".class"):
                continue
            relative_name = name[len(prefix):]
            relative = PurePosixPath(relative_name)
            if relative.is_absolute() or ".." in relative.parts or "\\" in relative_name or ":" in relative_name:
                raise ValueError("Unsafe class path in JAR")
            path = output.joinpath(*relative.parts)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(archive.read(name))
            count += 1
    if count == 0:
        raise ValueError("No application classes to analyze")
    return dict(status="PASS", classes=count, jar_sha256=metadata["jar"]["sha256"])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("coverage", "static", "security-preflight", "security", "unpack-classes"))
    parser.add_argument("--report", type=Path)
    parser.add_argument("--policy", type=Path, default=ROOT / "config/quality/coverage-policy.json")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--jar-dir", type=Path, default=Path("incoming-jar"))
    args = parser.parse_args()
    try:
        if args.command == "coverage":
            result = coverage(args.report, args.policy)
        elif args.command == "static":
            result = static_report(args.report)
        elif args.command == "security-preflight":
            result = security_preflight()
        elif args.command == "security":
            result = security_report(args.report)
        else:
            result = unpack_classes(args.jar_dir, args.output)
        scanner_outcome = os.environ.get("QUALITY_SCANNER_OUTCOME")
        if args.command in ("static", "security") and scanner_outcome:
            result["scanner_outcome"] = scanner_outcome
            if scanner_outcome != "success":
                result["status"] = "FAIL"
        save(args.output if args.command != "unpack-classes" else args.output.parent / "class-provenance.json", result)
        print(f"{args.command}: {result['status']}")
        return 1 if result["status"] == "FAIL" else 0
    except Exception as error:
        # Avoid echoing arbitrary scanner output, environment values or bodies.
        save(args.output if args.command != "unpack-classes" else args.output.parent / "class-provenance.json",
             dict(gate=args.command, status="FAIL", scan_status="ERROR", error_type=type(error).__name__, reason="Required report/configuration is missing, invalid or incomplete; see scanner diagnostics"))
        print(f"{args.command}: FAIL ({type(error).__name__})", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
