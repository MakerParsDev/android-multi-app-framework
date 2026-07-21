#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MODE="history"
BASE_REF=""
RUN_SELF_TEST="false"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mode) MODE="${2:?missing mode}"; shift 2 ;;
    --base-ref) BASE_REF="${2:?missing base ref}"; shift 2 ;;
    --self-test) RUN_SELF_TEST="true"; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

cd "$REPO_ROOT"
args=(--mode "$MODE" --report-dir "$REPO_ROOT/build/reports/security")
if [ -n "$BASE_REF" ]; then args+=(--base-ref "$BASE_REF"); fi
bash scripts/ci/run_secret_scan.sh "${args[@]}"
if [ "$RUN_SELF_TEST" = "true" ]; then bash scripts/ci/test_secret_gate.sh; fi
