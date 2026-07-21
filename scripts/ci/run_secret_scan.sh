#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MODE="history"
BASE_REF=""
REPORT_DIR="$REPO_ROOT/build/reports/security"
GITLEAKS_BIN="${GITLEAKS_BIN:-}"

usage() {
  cat <<'EOF'
Usage: run_secret_scan.sh [--mode history|range|dir] [--base-ref REF] [--report-dir DIR]
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mode) MODE="${2:?missing mode}"; shift 2 ;;
    --base-ref) BASE_REF="${2:?missing base ref}"; shift 2 ;;
    --report-dir) REPORT_DIR="${2:?missing report dir}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$MODE" in history|range|dir) ;; *) echo "Unsupported secret scan mode: $MODE" >&2; exit 2 ;; esac

cd "$REPO_ROOT"
python3 scripts/ci/validate_secret_scan_policy.py
python3 scripts/ci/validate_tracked_sensitive_files.py

if [ -z "$GITLEAKS_BIN" ]; then
  GITLEAKS_BIN="$(bash scripts/ci/install_gitleaks.sh)"
fi
[ -x "$GITLEAKS_BIN" ] || { echo "Gitleaks binary is not executable: $GITLEAKS_BIN" >&2; exit 1; }

mkdir -p "$REPORT_DIR"
report_path="$REPORT_DIR/gitleaks-${MODE}.sarif"
rm -f -- "$report_path"
common_args=(
  --config "$REPO_ROOT/.gitleaks.toml"
  --gitleaks-ignore-path "$REPO_ROOT/.gitleaksignore"
  --report-format sarif
  --report-path "$report_path"
  --redact=100
  --no-banner
  --no-color
)

set +e
case "$MODE" in
  history)
    "$GITLEAKS_BIN" git "${common_args[@]}" --log-opts="--all" "$REPO_ROOT"
    scan_rc=$?
    ;;
  range)
    if [ -z "$BASE_REF" ]; then echo "--base-ref is required for range mode" >&2; exit 2; fi
    if ! git rev-parse --verify "$BASE_REF^{commit}" >/dev/null 2>&1; then
      echo "Secret scan base ref is unavailable: $BASE_REF" >&2; exit 1
    fi
    merge_base="$(git merge-base "$BASE_REF" HEAD)"
    "$GITLEAKS_BIN" git "${common_args[@]}" --log-opts="$merge_base..HEAD" "$REPO_ROOT"
    scan_rc=$?
    ;;
  dir)
    "$GITLEAKS_BIN" dir "${common_args[@]}" "$REPO_ROOT"
    scan_rc=$?
    ;;
esac
set -e

if [ ! -f "$report_path" ]; then
  printf '{"version":"2.1.0","runs":[]}\n' > "$report_path"
fi
if [ "$scan_rc" -eq 1 ]; then
  echo "Secret scan found one or more unapproved findings. Redacted report: $report_path" >&2
  exit 1
fi
if [ "$scan_rc" -ne 0 ]; then
  echo "Secret scanner failed with exit code $scan_rc." >&2
  exit "$scan_rc"
fi

echo "Secret scan passed: mode=$MODE report=$report_path"
