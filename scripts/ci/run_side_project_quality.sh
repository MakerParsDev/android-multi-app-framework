#!/usr/bin/env bash
set -euo pipefail

install=false
skip_python=false
report_path="${SIDE_PROJECT_QUALITY_REPORT:-build/reports/side-projects/quality.json}"

usage() {
  cat <<'USAGE'
Usage: run_side_project_quality.sh [--install] [--skip-python] [--report PATH]

Runs blocking quality checks for five Node side projects, Firestore rules, critical
endpoint contracts, Python CI helpers, and a non-blocking live deployment drift report.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install) install=true; shift ;;
    --skip-python) skip_python=true; shift ;;
    --report)
      [[ $# -ge 2 ]] || { echo "--report requires a path" >&2; exit 2; }
      report_path="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

projects=(
  "side-projects/admin-notifications"
  "side-projects/cloudflare/workers/admin-api"
  "side-projects/cloudflare/workers/content-api"
  "side-projects/cloudflare/workers/ssv-callback"
  "side-projects/firebase/functions"
  "side-projects/firebase/rules-tests"
)

for project in "${projects[@]}"; do
  [[ -f "$project/package-lock.json" ]] || {
    echo "Missing lockfile: $project/package-lock.json" >&2
    exit 1
  }
  if [[ "$install" == "true" ]]; then
    npm --prefix "$project" ci
  elif [[ ! -d "$project/node_modules" ]]; then
    echo "Missing $project/node_modules; rerun with --install" >&2
    exit 1
  fi
done

python3 scripts/ci/validate_side_project_audits.py \
  --policy side-projects/audit-policy.json \
  --report build/reports/side-projects/npm-audit.json

npm --prefix side-projects/admin-notifications run verify
npm --prefix side-projects/cloudflare/workers/admin-api run verify
npm --prefix side-projects/cloudflare/workers/content-api run verify
npm --prefix side-projects/cloudflare/workers/ssv-callback run verify
npm --prefix side-projects/firebase/functions run verify
npm --prefix side-projects/firebase/rules-tests test
python3 scripts/ci/validate_side_project_endpoint_contracts.py

if [[ "$skip_python" != "true" ]]; then
  python3 -m unittest discover -s scripts/ci -p '*_test.py'
fi

sha="$(git rev-parse HEAD)"
endpoints_json="${SIDE_PROJECT_HEALTH_ENDPOINTS_JSON:-}"
# The single-quoted pattern intentionally matches an unexpanded Azure $(...) placeholder.
# shellcheck disable=SC2016
if [[ "$endpoints_json" == '$('* ]]; then endpoints_json=""; fi
python3 scripts/ci/check_side_project_deployment_drift.py \
  --expected-git-sha "$sha" \
  --endpoints-json "$endpoints_json" \
  --allow-unconfigured \
  --mode report \
  --report build/reports/side-projects/deployment-drift.json

mkdir -p "$(dirname "$report_path")"
python3 - "$report_path" "$install" "$skip_python" "$sha" <<'PY'
from datetime import datetime, timezone
import json
from pathlib import Path
import sys

path = Path(sys.argv[1])
checks = {
    "admin-notifications": ["npm-audit", "lint", "vitest", "build", "build-metadata"],
    "admin-api": ["npm-audit", "typecheck", "lint", "node-contract-tests", "wrangler-dry-run"],
    "content-api": ["npm-audit", "typecheck", "lint", "node-contract-tests", "wrangler-dry-run"],
    "ssv-callback": ["npm-audit", "typecheck", "lint", "node-contract-tests", "wrangler-dry-run"],
    "firebase-functions": ["npm-audit", "typecheck", "lint", "node-contract-tests"],
    "firestore-rules": ["npm-audit", "emulator-test"],
}
report = {
    "status": "passed",
    "gitSha": sys.argv[4],
    "completedAt": datetime.now(timezone.utc).isoformat(),
    "installedDependencies": sys.argv[2] == "true",
    "pythonHelpersIncluded": sys.argv[3] != "true",
    "projects": {
        project: {"status": "passed", "checks": project_checks}
        for project, project_checks in checks.items()
    },
}
path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(f"Side-project quality gate passed: projects=5 firestore=1 gitSha={sys.argv[4]}")
print(f"Side-project quality report={path}")
PY
