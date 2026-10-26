# Beanstalk delivery diagnostics without deployment

Run #27 attempt 8 (40205510988, aad157d763bc) passed CI but its environment
update failed before any new instance deployment. The environment still reported
smartfix-20261021-104814. Its environment events only said "Failed to deploy
application"; the available CloudTrail management events did not name another
denied API. These facts do not prove that every required permission is present.

The same attempt's ZIP passed local ZIP integrity and JAR checksum checks. A
separate application version, smartfix-diagnostic-aad157d-40205510988-8, completed
AWS preprocessing with status PROCESSED on 26 October 2026. This validates bundle
preprocessing, not instance hooks, application startup or deployment. No
UpdateEnvironment call was made for that diagnostic version. Version lifecycle
auto-deletion was confirmed disabled before registering it.

## Same-role S3 probe

The separate `SmartFix Beanstalk Delivery Diagnostics (No Deployment)` workflow
is manual-only, main-only, and uses the existing protected `beanstalk` environment
and deployment role through OIDC. It intentionally does not repeat Maven tests,
build the application, register application versions, update the environment or
create running resources. It is a troubleshooting entry point, not a release gate.

The owner explicitly authorized copying the existing attempt 8 ZIP to a random
temporary object, checking metadata and ACL, and cleaning that object. Use:

```
source_key = smartfix/releases/smartfix-aad157d763bc-40205510988-8/smartfix-aad157d763bc-40205510988-8.zip
confirm_probe = true
```

The script refuses other repositories, non-main dispatches, unconfirmed probes,
unrelated source keys, regions and administrator credentials. Source and copied
ZIP sizes/checksums must agree. It reads no object body. It tests ACL changes with
the fixed non-public `bucket-owner-full-control` ACL. Delete/ACL operations are
limited in code to its random `smartfix-diagnostic-*` object under the existing
environment's `_runtime/_versions/smartfix/` prefix. Cleanup confirms a 404;
cleanup failure fails the run. The source ZIP and real version objects are never
deleted. A runner termination can interrupt cleanup; the report records the exact
temporary key for narrowly scoped recovery.

Actual results are saved as `swe5006-delivery-diagnostics-RUN_ID-ATTEMPT/result.json`,
including failed operation and cleanup status, without bodies, credentials, raw
AWS responses or application data. A successful S3 probe proves only those S3
operations under that role; it cannot establish successful Beanstalk deployment.

The formal verifier now fails early when version-scoped AWS events explicitly
report a failed deployment while the old version remains Ready. It saves only
event categories, request IDs and denied action names, never raw event messages.
All existing version, Green health, instance and HTTP assertions remain required.
