"""Regression tests for smoke assertions, redaction and scoped cleanup.

All credentials/responses are synthetic; these tests are not Docker runtime proof.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

import container_verify as verify


class ContainerVerificationTests(unittest.TestCase):
    def setUp(self):
        self.report = {"checks": []}

    def test_login_redirect_cannot_pass_as_request_list(self):
        with self.assertRaises(RuntimeError):
            verify.expect(self.report, "request-list", (302, {"Location":"/login"}, "My Requests"), 200, markers=("My Requests",))
        self.assertEqual("FAIL", self.report["checks"][0]["status"])

    def test_wrong_response_content_fails_even_with_200(self):
        with self.assertRaises(RuntimeError):
            verify.expect(self.report, "request-list", (200, {}, "Welcome back"), 200, markers=("My Requests",))

    def test_external_redirect_fails(self):
        with self.assertRaises(RuntimeError):
            verify.expect(self.report, "login", (302, {"Location":"https://example.invalid/"}, ""), 302, location="/")

    def test_missing_redirect_fails(self):
        with self.assertRaises(RuntimeError):
            verify.expect(self.report, "login", (302, {}, ""), 302, location="/")

    def test_expected_business_response_passes_without_recording_body(self):
        verify.expect(self.report, "list", (200, {}, "My Requests private-fixture"), 200, markers=("My Requests",))
        self.assertEqual("PASS", self.report["checks"][0]["status"])
        self.assertNotIn("private-fixture", json.dumps(self.report))

    def test_health_retries_until_http_200_and_up(self):
        browser = Mock()
        browser.request.side_effect = [(503, {}, '{"status":"DOWN"}'), (200, {}, '{"status":"DOWN"}'), (200, {}, '{"status":"UP"}')]
        with patch.object(verify.time, "sleep"):
            verify.wait_ready(browser, 10, self.report)
        self.assertEqual(3, self.report["readiness"]["attempts"])

    def test_health_has_a_hard_deadline_and_reports_failure(self):
        browser = Mock()
        browser.request.return_value = (200, {}, '{"status":"DOWN"}')
        with patch.object(verify.time, "monotonic", side_effect=[0, 0, 0, 2, 2]), patch.object(verify.time, "sleep"):
            with self.assertRaises(TimeoutError):
                verify.wait_ready(browser, 1, self.report)
        self.assertEqual("FAIL", self.report["readiness"]["status"])

    def test_csrf_required_and_never_written_to_result(self):
        browser = verify.Browser("http://127.0.0.1:12345", [])
        with self.assertRaises(ValueError):
            browser.csrf("<html>missing</html>")
        self.assertEqual("fixture-csrf", browser.csrf('<input value="fixture-csrf" name="_csrf">'))
        self.assertIn("fixture-csrf", browser.sensitive)

    def test_credentials_cookies_and_tokens_are_redacted(self):
        text = verify.scrub('fixture-account fixture-pass Set-Cookie: JSESSIONID=fixture-cookie\n_csrf=fixture-token\npassword=other-fixture', ["fixture-account", "fixture-pass", "fixture-cookie", "fixture-token"])
        for secret in ("fixture-account", "fixture-pass", "fixture-cookie", "fixture-token", "other-fixture"):
            self.assertNotIn(secret, text)

    def test_remote_http_target_is_refused(self):
        with self.assertRaises(ValueError):
            verify.Browser("https://example.invalid:443", [])

    def test_unknown_cleanup_project_is_refused(self):
        with self.assertRaises(ValueError):
            verify.Harness(Path("."), "smartfix-db", "irrelevant", [])

    def test_tampered_ci_jar_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "app.jar").write_bytes(b"tampered")
            (root / "build-metadata.json").write_text(json.dumps({"jar":{"sha256":"0"*64}}))
            with patch.object(verify.ci, "validate_identity") as identity:
                with self.assertRaisesRegex(ValueError, "checksum"):
                    verify.input_jar(argparse.Namespace(local_jar=None, jar_dir=root))
                identity.assert_called_once()

    def test_cleanup_fails_if_labeled_resources_remain(self):
        with tempfile.TemporaryDirectory() as temp:
            harness = verify.Harness(Path(temp), "sf-verify-123456789abc", "smartfix-verification:sha-"+"a"*40+"-123456789abc", [])
            self.addCleanup(harness.temp.cleanup)
            harness.compose = Mock(return_value=subprocess.CompletedProcess([],0,"",""))
            def command(args, name, **kwargs):
                return subprocess.CompletedProcess(args, 0, "remaining-network" if name == "cleanup-networks" else "", "")
            harness.command = Mock(side_effect=command)
            self.assertEqual("FAIL", harness.cleanup()["status"])
            invoked = harness.compose.call_args.args[0]
            self.assertIn("--volumes", invoked)
            self.assertNotIn("prune", invoked)

    def test_cleanup_does_not_treat_failed_image_query_as_image_absence(self):
        with tempfile.TemporaryDirectory() as temp:
            harness = verify.Harness(Path(temp), "sf-verify-123456789abc", "smartfix-verification:sha-"+"a"*40+"-123456789abc", [])
            self.addCleanup(harness.temp.cleanup)
            harness.compose = Mock(return_value=subprocess.CompletedProcess([],0,"",""))
            harness.command = Mock(side_effect=lambda args,name,**kwargs: subprocess.CompletedProcess(args,1 if name=='cleanup-image-check' else 0,"",""))
            self.assertEqual("FAIL", harness.cleanup()["status"])

    def test_native_operation_timeout_returns_failure_and_diagnostics(self):
        with tempfile.TemporaryDirectory() as temp:
            harness = verify.Harness(Path(temp), "sf-verify-123456789abc", "smartfix-verification:sha-"+"a"*40+"-123456789abc", [])
            self.addCleanup(harness.temp.cleanup)
            result = harness.command([sys.executable, "-c", "import time; time.sleep(5)"], "bounded-command", timeout=0.1, required=False)
            self.assertEqual(124, result.returncode)
            self.assertIn("timed out", (Path(temp)/'bounded-command.log').read_text())


if __name__ == "__main__":
    unittest.main()
