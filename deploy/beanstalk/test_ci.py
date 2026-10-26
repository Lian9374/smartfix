"""Release provenance, trust boundaries and fail-closed verification tests.

AWS/HTTP are mocked: these tests never deploy or use real credentials/data.
"""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

import ci

IDENTITY = {
    "GITHUB_SHA": "a" * 40, "GITHUB_RUN_ID": "12345", "GITHUB_RUN_ATTEMPT": "1",
    "GITHUB_REPOSITORY": "Lian9374/smartfix", "GITHUB_WORKFLOW": "fixture-only",
    "GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REF": "refs/heads/main",
    "DEPLOY_REQUESTED": "true", "AWS_REGION": "ap-southeast-1",
    "EB_APPLICATION": "fixture-app", "EB_ENVIRONMENT": "fixture-env",
    "EB_S3_BUCKET": "fixture-release-bucket", "EB_BASE_URL": "https://fixture.example.test",
}


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = patch.dict(os.environ, IDENTITY, clear=True)
        self.env.start()
        self.addCleanup(self.env.stop)
        self.jar_dir = self.root / "target"
        self.jar_dir.mkdir()
        self.jar = self.jar_dir / "smartfix-fixture.jar"
        with zipfile.ZipFile(self.jar, "w") as jar:
            jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nStart-Class: com.smartfix.SmartFixApplication\n")
            jar.writestr("BOOT-INF/classes/fixture.txt", "synthetic fixture, not an application build")
        self.build = self.root / "build"
        self.release = self.root / "release"
        self.out = self.root / "verification"
        self.ca = self.root / "fixture-ca.pem"
        self.ca.write_text("-----BEGIN CERTIFICATE-----\nAQID\n-----END CERTIFICATE-----\n")

    def make_release(self):
        ci.build_metadata(self.jar_dir, self.build)
        return ci.package(self.build, self.release, self.ca)

    def test_package_uses_identical_jar_without_maven(self):
        with patch.object(ci.subprocess, "run", side_effect=AssertionError("No process should run")):
            metadata = self.make_release()
        with zipfile.ZipFile(self.release / metadata["zip"]["file"]) as archive:
            self.assertEqual(archive.read("app.jar"), self.jar.read_bytes())
            self.assertIn(".platform/confighooks/predeploy/10_mount_efs.sh", archive.namelist())
            self.assertTrue(all(not name.endswith(".env") for name in archive.namelist()))
            self.assertEqual(archive.getinfo(".platform/hooks/predeploy/10_mount_efs.sh").external_attr >> 16, 0o100755)
        self.assertEqual(metadata["commit_sha"], "a" * 40)
        self.assertEqual(metadata["workflow_run"], "https://github.com/Lian9374/smartfix/actions/runs/12345")
        self.assertEqual(metadata["jar"]["sha256"], ci.digest(self.jar))
        self.assertEqual(metadata["zip"]["sha256"], ci.digest(self.release / metadata["zip"]["file"]))

    def test_rejects_missing_or_multiple_maven_jars(self):
        self.jar.unlink()
        with self.assertRaises(ValueError):
            ci.build_metadata(self.jar_dir, self.build)
        self.jar.write_bytes(b"one")
        (self.jar_dir / "smartfix-other.jar").write_bytes(b"two")
        with self.assertRaises(ValueError):
            ci.build_metadata(self.jar_dir, self.build)

    def test_rejects_non_smartfix_jar(self):
        with zipfile.ZipFile(self.jar, "w") as jar:
            jar.writestr("META-INF/MANIFEST.MF", "Start-Class: other.Application\n")
        with self.assertRaises(ValueError):
            ci.build_metadata(self.jar_dir, self.build)

    def test_changed_jar_cannot_be_packaged(self):
        ci.build_metadata(self.jar_dir, self.build)
        with zipfile.ZipFile(self.build / "app.jar", "a") as jar:
            jar.writestr("tampered", "not the verified bytes")
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            ci.package(self.build, self.release, self.ca)

    def test_cross_commit_run_attempt_and_repository_are_rejected(self):
        ci.build_metadata(self.jar_dir, self.build)
        for key, value in {"GITHUB_SHA": "b" * 40, "GITHUB_RUN_ID": "987", "GITHUB_RUN_ATTEMPT": "2", "GITHUB_REPOSITORY": "fork/smartfix"}.items():
            with self.subTest(key=key), patch.dict(os.environ, {key: value}), self.assertRaises(ValueError):
                ci.package(self.build, self.release, self.ca)

    def test_private_key_is_never_bundled(self):
        ci.build_metadata(self.jar_dir, self.build)
        self.ca.write_text("-----BEGIN PRIVATE KEY-----\nfixture\n")
        with self.assertRaises(ValueError):
            ci.package(self.build, self.release, self.ca)

    def test_changed_zip_cannot_be_deployed(self):
        metadata = self.make_release()
        with (self.release / metadata["zip"]["file"]).open("ab") as stream:
            stream.write(b"modified")
        with patch.object(ci, "aws") as aws, self.assertRaisesRegex(ValueError, "SHA-256"):
            ci.deploy(self.release, self.out)
        aws.assert_not_called()

    def test_release_filename_cannot_escape_artifact_directory(self):
        self.make_release()
        path = self.release / "release-metadata.json"
        metadata = json.loads(path.read_text())
        metadata["zip"]["file"] = "../other.zip"
        ci.write_json(path, metadata)
        with self.assertRaises(ValueError):
            ci.load_release(self.release)

    def test_pr_push_non_main_and_missing_opt_in_never_call_aws(self):
        self.make_release()
        for changes in ({"GITHUB_EVENT_NAME": "pull_request"}, {"GITHUB_EVENT_NAME": "push"},
                        {"GITHUB_REF": "refs/heads/feature"}, {"DEPLOY_REQUESTED": "false"},
                        {"GITHUB_REPOSITORY": "fork/smartfix"}):
            with self.subTest(changes=changes), patch.dict(os.environ, changes), patch.object(ci, "aws") as aws:
                with self.assertRaises(ValueError):
                    ci.deploy(self.release, self.out)
                aws.assert_not_called()

    def test_missing_config_http_origin_and_userinfo_are_rejected(self):
        for origin in ("", "http://fixture.example.test", "https://user:password@fixture.example.test", "https://fixture.example.test/path", "https://fixture.example.test?token=value"):
            with self.subTest(origin=origin), patch.dict(os.environ, {"EB_BASE_URL": origin}), self.assertRaises(ValueError):
                ci.deployment_config()

    def test_http_needs_explicit_authorization_and_still_rejects_unsafe_origins(self):
        with patch.dict(os.environ, {"EB_BASE_URL": "http://fixture.example.test", "EB_ALLOW_HTTP_VERIFY": "true"}):
            self.assertEqual(ci.deployment_config()["EB_BASE_URL"], "http://fixture.example.test")
            for origin in ("http://user:password@fixture.example.test", "http://fixture.example.test:8080",
                           "http://fixture.example.test/path", "http://fixture.example.test?token=value"):
                with self.subTest(origin=origin), patch.dict(os.environ, {"EB_BASE_URL": origin}), self.assertRaises(ValueError):
                    ci.deployment_config()
        with patch.dict(os.environ, {"EB_ALLOW_HTTP_VERIFY": "yes"}), self.assertRaises(ValueError):
            ci.deployment_config()

    def test_http_cannot_target_a_host_other_than_authenticated_aws_cname(self):
        self.make_release()
        env = {"ApplicationName": "fixture-app", "Status": "Ready", "CNAME": "expected.example.test",
               "PlatformArn": "arn:aws:elasticbeanstalk:ap-southeast-1::platform/Corretto 21 Linux"}
        with patch.dict(os.environ, {"EB_BASE_URL": "http://other.example.test", "EB_ALLOW_HTTP_VERIFY": "true"}), \
                patch.object(ci, "aws", return_value={"Environments": [env]}) as aws:
            with self.assertRaises(ValueError):
                ci.deploy(self.release, self.out)
            self.assertEqual(aws.call_count, 1)  # Read only: no ZIP upload or environment update.

    def test_http_verification_cannot_be_reported_as_https(self):
        metadata = self.make_release()
        env = {"ApplicationName": "fixture-app", "EnvironmentName": "fixture-env", "Status": "Ready",
               "Health": "Green", "VersionLabel": metadata["application_version"], "CNAME": "fixture.example.test"}
        instances = {"InstanceHealthList": [{"Deployment": {"Status": "Deployed", "VersionLabel": metadata["application_version"]}}]}
        with patch.dict(os.environ, {"EB_BASE_URL": "http://fixture.example.test", "EB_ALLOW_HTTP_VERIFY": "true"}), \
                patch.object(ci, "aws", side_effect=[{"Environments": [env]}, instances]), \
                patch.object(ci, "http_smoke", return_value=[{"http_status": 200}]), \
                contextlib.redirect_stdout(io.StringIO()):
            ci.verify(self.release, self.out, timeout=0)
        receipt = json.loads((self.out / "post-deploy.json").read_text())
        self.assertEqual(receipt["status"], "PASS")
        self.assertEqual(receipt["transport"], "HTTP")
        self.assertFalse(receipt["https_transport_verified"])
        self.assertFalse(receipt["authenticated_http_checks"])

    def test_wrong_environment_does_not_upload(self):
        self.make_release()
        with patch.object(ci, "aws", return_value={"Environments": [{"ApplicationName": "other"}]}) as aws:
            with self.assertRaises(ValueError):
                ci.deploy(self.release, self.out)
        self.assertEqual(aws.call_count, 1)
        self.assertEqual(json.loads((self.out / "deployment.json").read_text())["status"], "FAIL")

    def test_deploy_records_requested_update_but_does_not_claim_pass(self):
        metadata = self.make_release()
        calls = []

        def fake(*args):
            calls.append(args)
            if args[1] == "describe-environments":
                return {"Environments": [{"ApplicationName": "fixture-app", "Status": "Ready", "PlatformArn": "arn:aws:elasticbeanstalk:ap-southeast-1::platform/Corretto 21 running on 64bit Amazon Linux 2023/4.13.0"}]}
            if args[0] == "s3api":
                return {"ChecksumSHA256": ci.base64.b64encode(bytes.fromhex(metadata["zip"]["sha256"])).decode()}
            return {}

        with patch.object(ci, "aws", side_effect=fake), contextlib.redirect_stdout(io.StringIO()):
            ci.deploy(self.release, self.out)
        self.assertEqual([x[1] for x in calls], ["describe-environments", "put-object", "create-application-version", "update-environment"])
        receipt = json.loads((self.out / "deployment.json").read_text())
        self.assertEqual(receipt["status"], "UPDATE_REQUESTED")
        self.assertEqual(receipt["jar_sha256"], metadata["jar"]["sha256"])

    def test_s3_checksum_failure_cannot_update_environment(self):
        self.make_release()
        responses = [{"Environments": [{"ApplicationName": "fixture-app", "Status": "Ready", "PlatformArn": "arn:aws:elasticbeanstalk:ap-southeast-1::platform/Corretto 21 Linux"}]}, {"ChecksumSHA256": "wrong"}]
        with patch.object(ci, "aws", side_effect=responses) as aws, self.assertRaises(ValueError):
            ci.deploy(self.release, self.out)
        self.assertEqual(aws.call_count, 2)

    def test_green_matching_version_and_instances_are_required(self):
        metadata = self.make_release()
        env = {"ApplicationName": "fixture-app", "EnvironmentName": "fixture-env", "Status": "Ready", "Health": "Green", "VersionLabel": metadata["application_version"]}
        instances = {"InstanceHealthList": [{"Deployment": {"Status": "Deployed", "VersionLabel": metadata["application_version"]}}]}
        with patch.object(ci, "aws", side_effect=[{"Environments": [env]}, instances]), patch.object(ci, "http_smoke", return_value=[{"http_status": 200}]), contextlib.redirect_stdout(io.StringIO()):
            ci.verify(self.release, self.out, timeout=0)
        self.assertEqual(json.loads((self.out / "post-deploy.json").read_text())["status"], "PASS")

    def test_yellow_or_wrong_version_fails_even_when_ready(self):
        metadata = self.make_release()
        for health, version in (("Yellow", metadata["application_version"]), ("Green", "old-version")):
            env = {"ApplicationName": "fixture-app", "Status": "Ready", "Health": health, "VersionLabel": version}
            with self.subTest(health=health), patch.object(ci, "aws", return_value={"Environments": [env]}), patch.object(ci, "http_smoke") as http:
                with self.assertRaises(ValueError):
                    ci.verify(self.release, self.out, timeout=0)
                http.assert_not_called()
                self.assertEqual(json.loads((self.out / "post-deploy.json").read_text())["status"], "FAIL")

    def test_http_failure_leaves_failure_receipt(self):
        metadata = self.make_release()
        env = {"ApplicationName": "fixture-app", "Status": "Ready", "Health": "Green", "VersionLabel": metadata["application_version"]}
        instances = {"InstanceHealthList": [{"Deployment": {"Status": "Deployed", "VersionLabel": metadata["application_version"]}}]}
        with patch.object(ci, "aws", side_effect=[{"Environments": [env]}, instances]), patch.object(ci, "http_smoke", side_effect=ValueError("HTTP failed")):
            with self.assertRaises(ValueError):
                ci.verify(self.release, self.out, timeout=0)
        self.assertEqual(json.loads((self.out / "post-deploy.json").read_text())["status"], "FAIL")

    def test_explicit_aws_failure_stops_without_waiting_or_leaking_event_messages(self):
        self.make_release()
        env = {"ApplicationName": "fixture-app", "Status": "Ready", "Health": "Red", "VersionLabel": "old"}
        events = [{"Severity": "ERROR", "Message": "Failed to deploy application.",
                   "RequestId": "12345678-1234-1234-1234-123456789abc"},
                  {"Severity": "ERROR", "Message": "password=do-not-export not authorized to perform: s3:PutObjectAcl"}]
        with patch.object(ci, "aws", side_effect=[{"Environments": [env]},
                          {"ApplicationVersions": [{"DateCreated": "2026-10-26T02:08:30Z"}]},
                          {"Events": events}]), patch.object(ci.time, "sleep") as sleep:
            with self.assertRaisesRegex(ValueError, "AWS reported application deployment failure"):
                ci.verify(self.release, self.out)
            sleep.assert_not_called()
        text = (self.out / "post-deploy.json").read_text()
        self.assertNotIn("do-not-export", text)
        self.assertIn("s3:PutObjectAcl", text)
        self.assertEqual(json.loads(text)["status"], "FAIL")

    def test_reports_preserve_failure_but_remove_properties_and_app_output(self):
        directory = self.jar_dir / "surefire-reports"
        directory.mkdir()
        (directory / "TEST-fixture.xml").write_text('''<testsuite tests="1" failures="1" errors="0" skipped="0"><properties><property name="db.password" value="do-not-store"/></properties><system-out>private application data</system-out><testcase name="fixture"><failure message="password=fixture-secret">fixture@example.test\nstacktrace</failure><system-err>private request body</system-err></testcase></testsuite>''')
        with patch.dict(os.environ, {"TEST_DB_PASSWORD": "fixture-secret"}):
            ci.collect_reports(self.jar_dir, self.root / "absent-logs", self.out)
        text = (self.out / "surefire/TEST-fixture.xml").read_text()
        self.assertNotIn("do-not-store", text)
        self.assertNotIn("private", text)
        self.assertNotIn("fixture-secret", text)
        self.assertNotIn("fixture@example.test", text)
        self.assertIsNotNone(ET.fromstring(text).find("testcase/failure"))
        inventory = json.loads((self.out / "report-inventory.json").read_text())
        self.assertEqual(inventory["surefire"]["failures"], 1)
        self.assertFalse(inventory["failsafe"]["reports_present"])

    def test_console_redacts_secrets_bearer_jdbc_and_email(self):
        with patch.dict(os.environ, {"AWS_SESSION_TOKEN": "fixture-token", "NVD_API_KEY": "fixture-nvd-key"}):
            text = ci.redact('fixture-token fixture-nvd-key nvdApiKey=labelled-fixture Authorization: Bearer xyz jdbc:postgresql://user:pass@host/db password=abc fixture@example.test {"password":"json-secret"}')
        for value in ("fixture-token", "fixture-nvd-key", "labelled-fixture", "xyz", "user:pass", "password=abc", "fixture@example.test", "json-secret"):
            self.assertNotIn(value, text)

    def test_aws_failure_does_not_echo_raw_stderr(self):
        result = ci.subprocess.CompletedProcess([], 1, "", "An error occurred (AccessDenied): password=do-not-log")
        with patch.object(ci.subprocess, "run", return_value=result), self.assertRaises(RuntimeError) as error:
            ci.aws("elasticbeanstalk", "update-environment")
        self.assertIn("AccessDenied", str(error.exception))
        self.assertNotIn("do-not-log", str(error.exception))

    def test_malformed_report_does_not_discard_other_diagnostics(self):
        directory = self.jar_dir / "surefire-reports"
        directory.mkdir()
        (directory / "TEST-broken.xml").write_text("<testsuite>")
        (directory / "TEST-valid.xml").write_text('<testsuite tests="1" failures="1" errors="0" skipped="0"/>')
        logs = self.root / "logs"
        logs.mkdir()
        (logs / "maven.log").write_text("BUILD FAILURE password=fixture-secret")
        with self.assertRaises(ValueError):
            ci.collect_reports(self.jar_dir, logs, self.out)
        self.assertTrue((self.out / "surefire/TEST-valid.xml").is_file())
        self.assertNotIn("fixture-secret", (self.out / "maven.log").read_text())
        self.assertEqual(json.loads((self.out / "report-inventory.json").read_text())["invalid_reports"], ["TEST-broken.xml"])

    def test_wrong_instance_version_is_not_accepted(self):
        metadata = self.make_release()
        env = {"ApplicationName": "fixture-app", "Status": "Ready", "Health": "Green", "VersionLabel": metadata["application_version"]}
        instances = {"InstanceHealthList": [{"Deployment": {"Status": "Deployed", "VersionLabel": "older"}}]}
        with patch.object(ci, "aws", side_effect=[{"Environments": [env]}, instances]), patch.object(ci, "http_smoke") as http:
            with self.assertRaises(ValueError):
                ci.verify(self.release, self.out, timeout=0)
            http.assert_not_called()

    def test_http_smoke_saves_no_response_data(self):
        def response(body, content_type):
            value = unittest.mock.MagicMock()
            value.__enter__.return_value = value
            value.status = 200
            value.read.return_value = body
            value.headers = {"Content-Type": content_type, "Set-Cookie": "sensitive-cookie"}
            return value

        opener = unittest.mock.MagicMock()
        opener.open.side_effect = [response(b'{"status":"UP"}', "application/json"), response(b'<form><input value="private-csrf"></form>', "text/html")]
        with patch.object(ci, "build_opener", return_value=opener):
            results = ci.http_smoke("https://fixture.example.test")
        self.assertEqual(len(results), 2)
        self.assertNotIn("private", json.dumps(results))
        self.assertNotIn("cookie", json.dumps(results))

    def test_http_200_with_down_health_is_not_pass(self):
        response = unittest.mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.read.return_value = b'{"status":"DOWN"}'
        opener = unittest.mock.MagicMock()
        opener.open.return_value = response
        with patch.object(ci, "build_opener", return_value=opener), self.assertRaises(ValueError):
            ci.http_smoke("https://fixture.example.test")

    def test_http_redirect_is_never_followed_to_another_origin(self):
        self.assertIsNone(ci.NoRedirect().redirect_request(None, None, 302, "redirect", {}, "http://other.example.test"))


if __name__ == "__main__":
    unittest.main()
