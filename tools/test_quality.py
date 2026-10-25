"""Policy guard tests; all reports and credentials below are synthetic fixtures."""
from datetime import datetime, timezone, timedelta
import json
import hashlib
import importlib.util
import os
import sys
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import patch

import quality


class QualityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.report = self.root / "report"
        self.policy = self.root / "policy.json"
        self.policy.write_text(json.dumps({"project": {"LINE": 90, "BRANCH": 80}, "core_packages": {"business/service": {"LINE": 90, "BRANCH": 80}}}))

    def jacoco(self, line="90", branch="80", session=True, package=True):
        line_xml = f'<counter type="LINE" covered="{line}" missed="{100-int(line)}"/>'
        branch_xml = f'<counter type="BRANCH" covered="{branch}" missed="{100-int(branch)}"/>'
        self.report.write_text('<report>' + ('<sessioninfo id="fixture"/>' if session else '') +
                               (f'<package name="business/service">{line_xml}{branch_xml}</package>' if package else '') +
                               '<counter type="LINE" covered="100" missed="0"/><counter type="BRANCH" covered="100" missed="0"/></report>')

    def test_measured_counters_pass_at_threshold(self):
        self.jacoco()
        self.assertEqual(quality.coverage(self.report, self.policy)["status"], "PASS")

    def test_core_regression_fails_even_with_good_project_total(self):
        self.jacoco(line="89")
        self.assertEqual(quality.coverage(self.report, self.policy)["status"], "FAIL")

    def test_missing_execution_session_is_not_coverage_pass(self):
        self.jacoco(session=False)
        with self.assertRaises(ValueError):
            quality.coverage(self.report, self.policy)

    def test_missing_core_package_cannot_shrink_gate_scope(self):
        self.jacoco(package=False)
        with self.assertRaises(ValueError):
            quality.coverage(self.report, self.policy)

    def test_empty_policy_is_rejected(self):
        self.jacoco()
        self.policy.write_text('{"core_packages":{}}')
        with self.assertRaises(ValueError):
            quality.coverage(self.report, self.policy)

    def test_empty_core_limits_cannot_disable_a_package_gate(self):
        self.jacoco()
        policy = json.loads(self.policy.read_text())
        policy["core_packages"]["business/service"] = {}
        self.policy.write_text(json.dumps(policy))
        with self.assertRaises(ValueError):
            quality.coverage(self.report, self.policy)

    def spotbugs(self, priority=None, error=False, classes=10):
        self.report.write_text(f'<BugCollection><FindBugsSummary total_classes="{classes}"/><Errors errors="{1 if error else 0}" missingClasses="0"/>' +
                              (f'<BugInstance type="FIXTURE" priority="{priority}"><Class classname="fixture.Business"/></BugInstance>' if priority else '') + '</BugCollection>')

    def test_static_medium_and_high_are_blocking(self):
        for priority in (1, 2):
            with self.subTest(priority=priority):
                self.spotbugs(priority)
                self.assertEqual(quality.static_report(self.report)["status"], "FAIL")

    def test_static_low_is_reported_but_not_blocking(self):
        self.spotbugs(3)
        result = quality.static_report(self.report)
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(len(result["findings"]), 1)

    def test_static_engine_error_and_zero_classes_are_not_pass(self):
        for args in ({"error": True}, {"classes": 0}):
            with self.subTest(args=args):
                self.spotbugs(**args)
                with self.assertRaises(ValueError):
                    quality.static_report(self.report)

    def test_missing_secret_fails_without_disclosing_value(self):
        with patch.dict(os.environ, {"NVD_API_KEY": ""}):
            result = quality.security_preflight()
        self.assertEqual(result["status"], "FAIL")
        self.assertEqual(result["scan_status"], "NOT_RUN")

    def test_present_secret_is_ready_not_scan_pass(self):
        with patch.dict(os.environ, {"NVD_API_KEY": "fixture-key-do-not-archive"}):
            result = quality.security_preflight()
        self.assertEqual(result["status"], "READY")
        self.assertNotIn("fixture-key", json.dumps(result))

    def dependency_report(self, hours=0, exceptions=False, vulnerability=None):
        self.now = datetime(2026, 10, 24, tzinfo=timezone.utc)
        info = {"engineVersion": "fixture", "dataSource": [{"name": "NVD API Last Checked", "timestamp": (self.now-timedelta(hours=hours)).isoformat()}]}
        if exceptions:
            info["analysisExceptions"] = [{"exception": {"message": "fixture database unavailable"}}]
        dep = {"fileName": "fixture.jar"}
        if vulnerability:
            dep["vulnerabilities"] = [vulnerability]
        self.report.write_text(json.dumps({"scanInfo": info, "dependencies": [dep]}))

    def test_completed_fresh_empty_findings_report_passes(self):
        self.dependency_report()
        self.assertEqual(quality.security_report(self.report, self.now)["status"], "PASS")

    def test_stale_database_and_analysis_errors_do_not_pass(self):
        for args in ({"hours": 5}, {"exceptions": True}):
            with self.subTest(args=args):
                self.dependency_report(**args)
                with self.assertRaises(ValueError):
                    quality.security_report(self.report, self.now)

    def test_cvss_seven_and_known_exploited_are_blocking(self):
        for issue in ({"name": "FIXTURE-1", "cvssv3": {"baseScore": 7.0}}, {"name": "FIXTURE-2", "cvssv3": {"baseScore": 2}, "knownExploitedVulnerability": {"Name": "fixture"}}):
            with self.subTest(issue=issue):
                self.dependency_report(vulnerability=issue)
                self.assertEqual(quality.security_report(self.report, self.now)["status"], "FAIL")

    def test_unscored_vulnerability_requires_review(self):
        self.dependency_report(vulnerability={"name": "FIXTURE-UNKNOWN"})
        self.assertEqual(quality.security_report(self.report, self.now)["status"], "FAIL")

    def test_invalid_cvss_is_not_a_clean_scan(self):
        self.dependency_report(vulnerability={"name": "FIXTURE-INVALID", "cvssv3": {"baseScore": "NaN"}})
        with self.assertRaises(ValueError):
            quality.security_report(self.report, self.now)

    def test_missing_report_is_not_empty_clean_scan(self):
        with self.assertRaises(FileNotFoundError):
            quality.security_report(self.report)

    def class_artifact(self, entry):
        spec = importlib.util.spec_from_file_location("release_ci", quality.ROOT / "deploy/beanstalk/ci.py")
        ci = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(ci)
        source = self.root / "artifact"
        source.mkdir()
        with zipfile.ZipFile(source / "app.jar", "w") as archive:
            archive.writestr(entry, b"synthetic class fixture")
        data = {**ci.identity(), "jar": {"sha256": hashlib.sha256((source / "app.jar").read_bytes()).hexdigest()}}
        (source / "build-metadata.json").write_text(json.dumps(data))
        return source

    def test_static_reuses_verified_jar_classes_without_compiling(self):
        env = {"GITHUB_SHA": "a"*40, "GITHUB_RUN_ID": "1", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_REPOSITORY": "Lian9374/smartfix"}
        with patch.dict(os.environ, env):
            source = self.class_artifact("BOOT-INF/classes/fixture/Example.class")
            result = quality.unpack_classes(source, self.root / "classes")
        self.assertEqual(result["classes"], 1)
        self.assertTrue((self.root / "classes/fixture/Example.class").exists())

    def test_jar_class_path_traversal_is_rejected(self):
        env = {"GITHUB_SHA": "a"*40, "GITHUB_RUN_ID": "1", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_REPOSITORY": "Lian9374/smartfix"}
        with patch.dict(os.environ, env):
            source = self.class_artifact("BOOT-INF/classes/../outside.class")
            with self.assertRaises(ValueError):
                quality.unpack_classes(source, self.root / "classes")

    def test_failed_scanner_cannot_turn_green_from_an_existing_clean_report(self):
        self.spotbugs()
        output = self.root / "status.json"
        with patch.dict(os.environ, {"QUALITY_SCANNER_OUTCOME": "failure"}), patch.object(sys, "argv", ["quality.py", "static", "--report", str(self.report), "--output", str(output)]):
            self.assertEqual(quality.main(), 1)
        self.assertEqual(json.loads(output.read_text())["status"], "FAIL")


if __name__ == "__main__":
    unittest.main()
