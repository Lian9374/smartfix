#!/usr/bin/env bash
set -euo pipefail
: "${BASE_URL:?Set BASE_URL to the deployment HTTPS URL}"
BASE_URL=${BASE_URL%/}
curl --fail --silent --show-error --retry 8 --retry-delay 5 "$BASE_URL/login" | grep -q 'name="username"'
for path in internal/deliveries/1/complete actuator/prometheus; do
  code=$(curl --silent --output /dev/null --write-out '%{http_code}' "$BASE_URL/$path")
  [[ "$code" == 403 ]] || { echo "Public boundary failed: $path ($code)"; exit 1; }
done
printf 'Login and public service boundaries verified. Run e2e.py for the authenticated workflow.\n'
