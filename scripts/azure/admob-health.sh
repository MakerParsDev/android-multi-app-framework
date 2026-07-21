#!/usr/bin/env bash
set -euo pipefail

mode="${ADMOB_HEALTH_MODE:-latest}"
python3 -m pip install --quiet google-auth google-auth-oauthlib google-api-python-client requests

case "$mode" in
  latest)
    python3 ./scripts/ci/check_admob_today_latest.py
    ;;
  today)
    python3 ./scripts/ci/check_admob_today.py
    ;;
  weekly)
    python3 ./scripts/ci/check_admob_weekly_optimization.py \
      --out-json "${ADMOB_WEEKLY_JSON:-TEMP_OUT/admob_weekly_optimization.json}" \
      --out-markdown "${ADMOB_WEEKLY_MARKDOWN:-TEMP_OUT/admob_weekly_optimization.md}"
    ;;
  *)
    echo "Unknown ADMOB_HEALTH_MODE: $mode" >&2
    exit 1
    ;;
esac
