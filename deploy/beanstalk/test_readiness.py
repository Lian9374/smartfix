"""Bounded HTTP readiness retries; synthetic AWS state and no real waiting."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

import ci


IDENTITY = {
    "GITHUB_SHA": "a" * 40,
    "GITHUB_RUN_ID": "12345",
    "GITHUB_RUN_ATTEMPT": "1",
    "GITHUB_REPOSITORY": "Lian9374/smartfix",
    "GITHUB_WORKFLOW": "readiness-fixture",
    "GITHUB_EVENT_NAME": "workflow_dispatch",
    "GITHUB_REF": "refs/heads/main",
    "DEPLOY_REQUESTED": "true",
    "AWS_REGION": "ap-southeast-1",
    "EB_APPLICATION": "fixture-app",
    "EB_ENVIRONMENT": "fixture-env",
    "EB_S3_BUCKET": "fixture-release-bucket",
    "EB_BASE_URL": "https://fixture.example.test",
}
VERSION = "smartfix-aaaaaaaaaaaa-12345-1"
CHECKS = [
    {"route": "/actuator/health", "http_status": 200},
    {"route": "/login", "http_status": 200},
]


class FakeClock:
    def __init__(self, budget):
        self.now = 100.0
        self.deadline = self.now + budget
        self.sleeps = []

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        if not 0 < seconds <= 5:
            raise AssertionError("Readiness sleep must be positive and at most five seconds")
        if self.now + seconds > self.deadline + 1e-9:
            raise AssertionError("Readiness sleep exceeded the shared timeout budget")
        self.sleeps.append(seconds)
        self.now += seconds

    def advance(self, seconds):
        """Account for time spent in an AWS or HTTP call without actually waiting."""
        self.now += seconds


def environment(version=VERSION, health="Green", status="Ready"):
    return {
        "ApplicationName": "fixture-app",
        "EnvironmentName": "fixture-env",
        "Status": status,
        "Health": health,
        "HealthStatus": "Info",
        "VersionLabel": version,
        "CNAME": "fixture.example.test",
    }


def deployed(version=VERSION):
    return {"Deployment": {"VersionLabel": version, "Status": "Deployed"}}


def transient(code):
    return HTTPError(
        "https://fixture.example.test/actuator/health?private-query-do-not-export",
        code,
        "fixture upstream response",
        {"Set-Cookie": "private-cookie-do-not-export"},
        io.BytesIO(b"private-response-body-do-not-export"),
    )


class ReadinessTests(unittest.TestCase):
    """Independent fixtures: do not inherit the release/provenance test suite."""

    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.output = self.root / "verification"

    def receipt(self):
        return json.loads((self.output / "post-deploy.json").read_text(encoding="utf-8"))

    @contextlib.contextmanager
    def fixture(self, http_effect, *, environments=None, instances=None, timeout=7):
        clock = FakeClock(timeout)
        environments = environments or [environment()]
        instances = instances or [[deployed()]]
        counts = {"environment": 0, "instances": 0, "http": 0}
        trace = []
        http_started = []
        snapshots = []

        def aws(*args):
            if sum(counts.values()) > 100:
                raise AssertionError("Verification did not make bounded progress")
            operation = args[1]
            if operation == "describe-environments":
                index = counts["environment"]
                counts["environment"] += 1
                trace.append("environment")
                return {"Environments": [environments[min(index, len(environments) - 1)]]}
            if operation == "describe-instances-health":
                index = counts["instances"]
                counts["instances"] += 1
                trace.append("instances")
                return {"InstanceHealthList": instances[min(index, len(instances) - 1)]}
            if operation == "describe-application-versions":
                return {"ApplicationVersions": [{"DateCreated": "2026-10-26T00:00:00Z"}]}
            if operation == "describe-events":
                return {"Events": []}
            raise AssertionError("Unexpected AWS operation: " + operation)

        def http(*args, **kwargs):
            index = counts["http"]
            counts["http"] += 1
            trace.append("http")
            http_started.append(clock.now)
            snapshots.append(self.receipt())
            self.assertEqual(snapshots[-1]["status"], "FAIL")
            return http_effect(clock, index)

        with patch.dict(os.environ, IDENTITY, clear=True), \
                patch.object(ci, "load_release", return_value=({"application_version": VERSION}, self.root / "fixture.zip")), \
                patch.object(ci, "aws", side_effect=aws), \
                patch.object(ci, "http_smoke", side_effect=http) as smoke, \
                patch.object(ci.time, "monotonic", side_effect=clock.monotonic), \
                patch.object(ci.time, "sleep", side_effect=clock.sleep), \
                patch.object(ci.subprocess, "run", side_effect=AssertionError("No real process may run")), \
                contextlib.redirect_stdout(io.StringIO()):
            yield clock, smoke, counts, trace, http_started, snapshots

    def test_transient_502_then_success_rechecks_environment_and_all_instances(self):
        def http(clock, index):
            if index == 0:
                raise transient(502)
            return CHECKS

        with self.fixture(http) as (clock, smoke, counts, trace, started, snapshots):
            ci.verify(self.root, self.output, timeout=7)
            self.assertEqual(trace, ["environment", "instances", "http"] * 2)
            self.assertEqual(smoke.call_count, 2)
            self.assertTrue(all(moment < clock.deadline for moment in started))
        receipt = self.receipt()
        self.assertEqual(receipt["status"], "PASS")
        self.assertEqual(receipt["checks"], CHECKS)
        self.assertEqual(receipt["http_readiness"], {
            "attempts": 2, "transient_http_statuses": [502], "status": "PASS",
        })
        self.assertFalse(receipt["authenticated_http_checks"])
        self.assertNotIn("private-", json.dumps(receipt))

    def test_503_and_504_are_also_bounded_transient_responses(self):
        for code in (503, 504):
            with self.subTest(code=code):
                def http(clock, index):
                    if index == 0:
                        raise transient(code)
                    return CHECKS

                with self.fixture(http):
                    ci.verify(self.root, self.output, timeout=7)
                self.assertEqual(self.receipt()["http_readiness"], {
                    "attempts": 2, "transient_http_statuses": [code], "status": "PASS",
                })

    def test_persistent_502_exhausts_one_budget_without_pass(self):
        def http(clock, index):
            raise transient(502)

        with self.fixture(http) as (clock, smoke, counts, trace, started, snapshots):
            with self.assertRaises((ValueError, TimeoutError)):
                ci.verify(self.root, self.output, timeout=7)
            self.assertGreater(smoke.call_count, 1)
            self.assertEqual(counts["environment"], counts["instances"])
            self.assertTrue(all(moment < clock.deadline for moment in started))
            self.assertAlmostEqual(clock.now, clock.deadline)
        receipt = self.receipt()
        self.assertEqual(receipt["status"], "FAIL")
        self.assertEqual(receipt["http_readiness"], {
            "attempts": smoke.call_count,
            "transient_http_statuses": [502] * smoke.call_count,
            "status": "WAITING",
        })
        self.assertNotIn("private-", json.dumps(receipt))

    def test_500_and_403_fail_immediately_without_retry(self):
        for code in (500, 403):
            with self.subTest(code=code):
                def http(clock, index):
                    raise transient(code)

                with self.fixture(http) as (clock, smoke, counts, trace, started, snapshots):
                    with self.assertRaises(HTTPError) as error:
                        ci.verify(self.root, self.output, timeout=7)
                    self.assertEqual(error.exception.code, code)
                    self.assertEqual(smoke.call_count, 1)
                    self.assertEqual(clock.sleeps, [])
                self.assertEqual(self.receipt()["status"], "FAIL")
                self.assertEqual(self.receipt()["http_readiness"], {
                    "attempts": 1, "transient_http_statuses": [], "status": "WAITING",
                })

    def test_down_health_value_error_fails_immediately(self):
        def http(clock, index):
            raise ValueError("Application health is not UP")

        with self.fixture(http) as (clock, smoke, counts, trace, started, snapshots):
            with self.assertRaisesRegex(ValueError, "not UP"):
                ci.verify(self.root, self.output, timeout=7)
            self.assertEqual(smoke.call_count, 1)
            self.assertEqual(clock.sleeps, [])
        self.assertEqual(self.receipt()["status"], "FAIL")
        self.assertEqual(self.receipt()["http_readiness"]["transient_http_statuses"], [])

    def test_version_change_during_retry_cannot_pass(self):
        def http(clock, index):
            if index == 0:
                raise transient(502)
            return CHECKS

        with self.fixture(http, environments=[environment(), environment(version="different-version")]) as state:
            clock, smoke, counts, trace, started, snapshots = state
            with self.assertRaises((ValueError, TimeoutError)):
                ci.verify(self.root, self.output, timeout=7)
            self.assertGreaterEqual(counts["environment"], 2)
            self.assertEqual(smoke.call_count, 1)
        self.assertEqual(self.receipt()["status"], "FAIL")
        self.assertEqual(self.receipt()["http_readiness"]["status"], "WAITING")

    def test_instance_regression_during_retry_cannot_pass(self):
        def http(clock, index):
            if index == 0:
                raise transient(502)
            return CHECKS

        with self.fixture(http, instances=[[deployed()], [deployed(), deployed("different-version")]]) as state:
            clock, smoke, counts, trace, started, snapshots = state
            with self.assertRaises((ValueError, TimeoutError)):
                ci.verify(self.root, self.output, timeout=7)
            self.assertGreaterEqual(counts["instances"], 2)
            self.assertEqual(smoke.call_count, 1)
        self.assertEqual(self.receipt()["status"], "FAIL")

    def test_time_spent_in_http_does_not_start_a_new_timeout_budget(self):
        def http(clock, index):
            if index == 0:
                clock.advance(7)
                raise transient(502)
            return CHECKS

        with self.fixture(http) as (clock, smoke, counts, trace, started, snapshots):
            with self.assertRaises((ValueError, TimeoutError)):
                ci.verify(self.root, self.output, timeout=7)
            self.assertEqual(smoke.call_count, 1)
            self.assertEqual(clock.sleeps, [])
            self.assertAlmostEqual(clock.now, clock.deadline)
        self.assertEqual(self.receipt()["status"], "FAIL")
        self.assertEqual(self.receipt()["http_readiness"], {
            "attempts": 1, "transient_http_statuses": [502], "status": "WAITING",
        })


if __name__ == "__main__":
    unittest.main()
