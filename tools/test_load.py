"""Synthetic control tests; these do not claim measured performance."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import run_load as load


def summary():
    metrics = {
        "business_requests": {"values": {"count": 300, "rate": 2.5}},
        "business_latency": {"values": {"p(95)": 100, "p(99)": 200}},
        "business_errors": {"values": {"rate": 0}, "thresholds": {"rate<0.01": {"ok": True}}},
        "http_reqs": {"values": {"count": 321}},
        "auth_requests": {"values": {"count": 20}},
        "http_req_failed{kind:business}": {"values": {"rate": 0}},
        "errors_http_5xx": {"values": {"count": 0}},
    }
    for endpoint in ("map", "requests", "community"):
        for key in ("business_requests", "business_latency", "business_errors"):
            metrics[key + "{endpoint:" + endpoint + "}"] = copy.deepcopy(metrics[key])
    return {"metrics": metrics, "state": {"testRunDurationMs": 120000}}


class MetricsTests(unittest.TestCase):
    def test_actual_summary_is_required(self):
        with self.assertRaises(KeyError):
            load.normalize({}, 0)

    def test_native_failure_cannot_become_pass(self):
        self.assertEqual("FAIL", load.normalize(summary(), 99)["status"])

    def test_failed_threshold_cannot_become_pass(self):
        data = summary()
        data["metrics"]["business_errors"]["thresholds"]["rate<0.01"]["ok"] = False
        self.assertEqual("FAIL", load.normalize(data, 0)["status"])

    def test_missing_thresholds_fail_closed(self):
        data = summary()
        for metric in data["metrics"].values():
            metric.pop("thresholds", None)
        with self.assertRaises(ValueError):
            load.normalize(data, 0)

    def test_missing_endpoint_fails_closed(self):
        data = summary()
        del data["metrics"]["business_requests{endpoint:community}"]
        with self.assertRaises(KeyError):
            load.normalize(data, 0)

    def test_nonfinite_measurement_is_not_pass(self):
        data = summary()
        data["metrics"]["business_latency"]["values"]["p(99)"] = float("nan")
        with self.assertRaises(ValueError):
            load.normalize(data, 0)

    def test_auth_requests_are_not_business_requests(self):
        result = load.normalize(summary(), 0)
        self.assertEqual(300, result["business"]["requests"])
        self.assertEqual(321, result["all_http_requests"])
        self.assertEqual(20, result["auth_requests"])
        self.assertEqual(120, result["measured_duration_seconds"])

    def test_empty_samples_cannot_pass(self):
        data = summary()
        data["metrics"]["business_requests"]["values"]["count"] = 0
        with self.assertRaises(ValueError):
            load.normalize(data, 0)


class FixtureSafetyTests(unittest.TestCase):
    def test_linux_load_generator_can_read_private_owner_inputs(self):
        with patch.object(load.os, "getuid", return_value=1001, create=True), patch.object(load.os, "getgid", return_value=1002, create=True):
            self.assertEqual("1001:1002", load.loadgen_user())

    def test_relative_and_same_origin_absolute_redirect(self):
        for target in ("/login", "http://127.0.0.1:12345/login"):
            browser = Mock(base="http://127.0.0.1:12345")
            browser.request.return_value = (302, {"Location": target}, "")
            load.post(browser, "/register", {}, "/login")

    def test_cross_origin_redirect_is_rejected(self):
        browser = Mock(base="http://127.0.0.1:12345")
        browser.request.return_value = (302, {"Location": "http://elsewhere/login"}, "secret body")
        with self.assertRaisesRegex(RuntimeError, "Fixture operation") as caught:
            load.post(browser, "/register", {}, "/login")
        self.assertNotIn("secret body", str(caught.exception))

    def test_login_error_cannot_pass(self):
        browser = Mock(base="http://127.0.0.1:12345")
        browser.request.return_value = (302, {"Location": "/login?error"}, "")
        with self.assertRaises(RuntimeError):
            load.post(browser, "/login", {}, "/")

    def test_locations_come_from_actual_form(self):
        parser = load.Locations()
        parser.feed('<select name="other"><option value="999">x</option></select><select name="locationId"><option value="">x</option><option value="3">a</option><option value="7">b</option></select>')
        self.assertEqual(["3", "7"], parser.ids)

    def test_memory_conversion(self):
        self.assertEqual(512 * 1024**2, load.memory_bytes("512MiB"))
        with self.assertRaises(ValueError):
            load.memory_bytes("unavailable")


if __name__ == "__main__":
    unittest.main()
