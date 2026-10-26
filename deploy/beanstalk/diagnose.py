"""Explicitly authorized, same-role S3 delivery probe. Never deploys an application."""
from __future__ import annotations

import argparse
import base64
import os
from pathlib import Path
import re
import uuid

import ci

BUCKET = "elasticbeanstalk-ap-southeast-1-864292612983"
PREFIX = "resources/environments/e-x2ikzj8j3i/_runtime/_versions/smartfix/"
ROLE = "arn:aws:iam::864292612983:role/smartfix-github-deploy"
SOURCE = re.compile(r"smartfix/releases/(smartfix-[0-9a-f]{12}-[0-9]+-[0-9]+)/\1\.zip")


def run(source_key: str, output: Path) -> None:
    identity = ci.identity()
    if (os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch"
            or os.environ.get("GITHUB_REF") != "refs/heads/main"
            or os.environ.get("DIAGNOSTIC_REQUESTED") != "true"
            or os.environ.get("AWS_REGION") != "ap-southeast-1"
            or not SOURCE.fullmatch(source_key)):
        raise ValueError("Probe requires explicit manual main authorization and a SmartFix release ZIP")
    target = PREFIX + "smartfix-diagnostic-" + identity["run_id"] + "-" + uuid.uuid4().hex
    receipt = {**identity, "scope": "S3 delivery probe; no environment update or application deployment",
               "status": "FAIL", "source_key": source_key, "temporary_key": target, "checks": [],
               "cleanup": {"attempted": False, "status": "NOT_NEEDED"}}
    path = output / "result.json"
    copied = False

    def check(name, action):
        try:
            result = action()
        except Exception as error:
            # ci.aws deliberately omits raw stderr, credential/config values and bodies.
            receipt["checks"].append({"name": name, "status": "FAIL", "error": ci.redact(str(error))})
            ci.write_json(path, receipt)
            print("FAIL:", name, ci.redact(str(error)))
            raise
        receipt["checks"].append({"name": name, "status": "PASS"})
        ci.write_json(path, receipt)
        print("PASS:", name)
        return result

    try:
        caller = check("deployment_role_identity", lambda: ci.aws("sts", "get-caller-identity"))
        arn = caller.get("Arn", "")
        if not arn.startswith("arn:aws:sts::864292612983:assumed-role/smartfix-github-deploy/"):
            raise ValueError("Probe must use the existing deployment role, not administrator credentials")
        receipt["role_arn"] = ROLE
        source = check("source_metadata_read", lambda: ci.aws("s3api", "head-object", "--bucket", BUCKET,
                       "--key", source_key, "--checksum-mode", "ENABLED"))
        sha = source.get("Metadata", {}).get("zip-sha256", "")
        if not re.fullmatch(r"[0-9a-f]{64}", sha) or not source.get("ContentLength"):
            raise ValueError("Expected a non-empty CI ZIP with checksum metadata")
        checksum = base64.b64encode(bytes.fromhex(sha)).decode("ascii")
        if source.get("ChecksumSHA256") != checksum:
            raise ValueError("Source checksum does not match recorded CI ZIP SHA-256")
        receipt["zip_sha256"] = sha
        # Set before copy: even an interrupted copy gets a scoped cleanup attempt.
        copied = True
        check("copy_to_isolated_version_key", lambda: ci.aws("s3api", "copy-object", "--bucket", BUCKET,
              "--key", target, "--copy-source", BUCKET + "/" + source_key))
        destination = check("copied_metadata_read", lambda: ci.aws("s3api", "head-object", "--bucket", BUCKET,
                            "--key", target, "--checksum-mode", "ENABLED"))
        if (destination.get("ContentLength") != source["ContentLength"]
                or destination.get("ChecksumSHA256") != checksum):
            raise ValueError("Copied ZIP length/checksum differs from source")
        check("read_copied_acl", lambda: ci.aws("s3api", "get-object-acl", "--bucket", BUCKET, "--key", target))
        # Fixed non-public canned ACL, only on the random probe object.
        check("set_bucket_owner_full_control_acl", lambda: ci.aws("s3api", "put-object-acl", "--bucket", BUCKET,
              "--key", target, "--acl", "bucket-owner-full-control"))
        receipt["status"] = "PASS"
    finally:
        if copied:
            receipt["cleanup"] = {"attempted": True, "status": "FAIL"}
            try:
                ci.aws("s3api", "delete-object", "--bucket", BUCKET, "--key", target)
                try:
                    ci.aws("s3api", "head-object", "--bucket", BUCKET, "--key", target)
                except RuntimeError as error:
                    if not re.search(r"failed: (?:404|NoSuchKey|NotFound)$", str(error)):
                        raise
                else:
                    raise ValueError("Temporary probe object still exists after cleanup")
                receipt["cleanup"]["status"] = "PASS"
                print("PASS: temporary object removed; source ZIP unchanged")
            except Exception as error:
                receipt["status"] = "FAIL"
                receipt["cleanup"]["error"] = ci.redact(str(error))
                raise
            finally:
                ci.write_json(path, receipt)
        else:
            ci.write_json(path, receipt)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-key", required=True)
    parser.add_argument("--output", type=Path, default=Path("verification/deployment-diagnostics"))
    args = parser.parse_args()
    try:
        run(args.source_key, args.output)
    except Exception as error:
        print(type(error).__name__ + ": " + ci.redact(str(error)))
        raise SystemExit(1) from None
