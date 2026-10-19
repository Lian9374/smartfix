#!/usr/bin/env bash
set -euo pipefail
mkdir -p results
python3 scripts/swe5001/e2e.py
for count in 1 2; do
  docker compose -f compose.swe5001.yml up -d --scale core="$count" --scale worker=1 --wait --wait-timeout 180
  python3 scripts/swe5001/sample-metrics.py "$count" > "results/resources-$count.csv" &
  sampler=$!
  trap 'kill "$sampler" 2>/dev/null || true' EXIT
  load_status=0
  docker compose -f compose.swe5001.yml run --rm --user "$(id -u):$(id -g)" -e STAGE_DURATION="${STAGE_DURATION:-20s}" k6 run --summary-export "/results/k6-$count.json" /scripts/load.js | tee "results/k6-$count.txt" || load_status=$?
  printf '%s\n' "$load_status" > "results/threshold-status-$count.txt"
  kill "$sampler" 2>/dev/null || true
  wait "$sampler" 2>/dev/null || true
  trap - EXIT
  sleep 10
done
[[ $(cat results/threshold-status-1.txt) == 0 && $(cat results/threshold-status-2.txt) == 0 ]] || { echo 'One or more runs failed thresholds; raw measurements retained.'; exit 1; }
printf 'Raw local measurements saved in results/. Do not describe these as AWS results.\n'
