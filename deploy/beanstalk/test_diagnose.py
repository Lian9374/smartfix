"""Synthetic tests only; no AWS credentials or real S3 operations."""
import base64
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import diagnose

KEY = "smartfix/releases/smartfix-aaaaaaaaaaaa-12345-8/smartfix-aaaaaaaaaaaa-12345-8.zip"
ENV = {"GITHUB_SHA": "a" * 40, "GITHUB_RUN_ID": "12345", "GITHUB_RUN_ATTEMPT": "1",
       "GITHUB_REPOSITORY": "Lian9374/smartfix", "GITHUB_EVENT_NAME": "workflow_dispatch",
       "GITHUB_REF": "refs/heads/main", "DIAGNOSTIC_REQUESTED": "true", "AWS_REGION": "ap-southeast-1"}


class ProbeTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.output = Path(temp.name)
        env = patch.dict(os.environ, ENV, clear=True)
        env.start()
        self.addCleanup(env.stop)
        self.operations = []
        self.heads = 0

    def aws(self, *args):
        self.operations.append(args)
        api = args[1]
        if api == "get-caller-identity":
            return {"Arn": "arn:aws:sts::864292612983:assumed-role/smartfix-github-deploy/test"}
        if api == "head-object":
            self.heads += 1
            if self.heads == 3:
                raise RuntimeError("AWS s3api/head-object failed: 404")
            return {"ContentLength": 123, "Metadata": {"zip-sha256": "a" * 64},
                    "ChecksumSHA256": base64.b64encode(bytes.fromhex("a" * 64)).decode()}
        return {}

    def test_success_uses_same_role_and_only_deletes_its_random_probe(self):
        with patch.object(diagnose.ci, "aws", side_effect=self.aws):
            diagnose.run(KEY, self.output)
        receipt = json.loads((self.output / "result.json").read_text())
        self.assertEqual(receipt["status"], "PASS")
        self.assertEqual(receipt["cleanup"]["status"], "PASS")
        for args in self.operations:
            self.assertNotIn("update-environment", args)
            self.assertNotIn("get-object", args)
            if args[1] in ("delete-object", "put-object-acl"):
                self.assertTrue(args[args.index("--key") + 1].startswith(diagnose.PREFIX + "smartfix-diagnostic-"))

    def test_refuses_unauthorized_ref_or_non_release_source_before_any_aws_call(self):
        for key, updates in ((KEY, {"GITHUB_REF": "refs/heads/topic"}),
                             (KEY, {"DIAGNOSTIC_REQUESTED": "false"}),
                             ("other/path.zip", {})):
            with self.subTest(key=key, updates=updates), patch.dict(os.environ, updates):
                with patch.object(diagnose.ci, "aws") as aws, self.assertRaises(ValueError):
                    diagnose.run(key, self.output)
                aws.assert_not_called()

    def test_refuses_administrator_identity_before_copy(self):
        with patch.object(diagnose.ci, "aws", return_value={"Arn": "arn:aws:iam::864292612983:user/admin"}), self.assertRaises(ValueError):
            diagnose.run(KEY, self.output)

    def test_copy_failure_still_cleans_only_probe_and_stays_failed(self):
        def aws(*args):
            if args[1] == "copy-object":
                self.heads = 2
                raise RuntimeError("AWS s3api/copy-object failed: AccessDenied")
            return self.aws(*args)
        with patch.object(diagnose.ci, "aws", side_effect=aws), self.assertRaises(RuntimeError):
            diagnose.run(KEY, self.output)
        receipt = json.loads((self.output / "result.json").read_text())
        self.assertEqual(receipt["status"], "FAIL")
        self.assertEqual(receipt["cleanup"]["status"], "PASS")
        self.assertEqual(receipt["checks"][-1]["status"], "FAIL")

    def test_cleanup_failure_never_reports_success(self):
        def aws(*args):
            if args[1] == "delete-object":
                raise RuntimeError("AWS s3api/delete-object failed: AccessDenied")
            return self.aws(*args)
        with patch.object(diagnose.ci, "aws", side_effect=aws), self.assertRaises(RuntimeError):
            diagnose.run(KEY, self.output)
        receipt = json.loads((self.output / "result.json").read_text())
        self.assertEqual(receipt["status"], "FAIL")
        self.assertEqual(receipt["cleanup"]["status"], "FAIL")


if __name__ == "__main__":
    unittest.main()
