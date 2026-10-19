#!/usr/bin/env bash
# Verifies the existing local teaching environment. Does not invoke AWS tools.
set -euo pipefail
cd "$(dirname "$0")/../.."
export BASE_URL=http://127.0.0.1:8080
core_count=$(docker compose -f compose.swe5001.yml ps --status running -q core | awk 'NF { count++ } END { print count+0 }')
[[ "$core_count" -gt 0 ]] || { echo 'Start the local environment before verification.'; exit 1; }
export EXPECTED_CORE_COUNT="$core_count"
printf 'Checking local gateway health...\n'
health=$(curl --fail --silent --show-error "$BASE_URL/healthz")
printf '%s' "$health" |
  python3 -c 'import json,sys; assert json.load(sys.stdin).get("status")=="UP", "Gateway reports unhealthy"'
printf 'Checking login and public boundaries...\n'
bash scripts/swe5001/smoke.sh
printf 'Checking workflow, shared session and attachment across %s Core instances...\n' "$core_count"
python3 scripts/swe5001/e2e.py
printf 'Checking real database/HTTP failure and concurrency scenarios...\n'
docker compose -f compose.swe5001.yml run --rm fault-tests
printf 'Checking all Core and Worker monitoring targets...\n'
curl --fail --silent --show-error 'http://127.0.0.1:9090/api/v1/query?query=up' |
  python3 -c 'import json,sys; r=json.load(sys.stdin); targets=r["data"]["result"]; assert r["status"]=="success" and targets and all(t["value"][1]=="1" for t in targets), "A monitoring target is down"; print("Monitoring targets UP:",len(targets))'
printf 'Checking Worker unit tests...\n'
(cd services/notification-worker && python3 -m unittest discover -s tests -v)
python3 - <<'PYREPORT'
import json,os,time
from pathlib import Path
record={'result':'PASS','timestamp':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),
        'environment':'local Docker only','expectedCoreCount':int(os.environ['EXPECTED_CORE_COUNT']),
        'checks':['gateway health','login and public route boundaries','authenticated workflow',
                  'shared sessions and attachments','real PostgreSQL and HTTP fault scenarios',
                  'Core/Worker monitoring targets','Worker unit tests'],
        'awsResourcesCreated':False}
Path('results/local-verification.json').write_text(json.dumps(record,indent=2)+'\n')
PYREPORT
printf 'Local verification passed. No AWS tools or resources were used.\n'
