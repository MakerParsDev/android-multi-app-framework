#!/usr/bin/env bash
set -euo pipefail

flavors_csv="${RESOLVED_FLAVORS_CSV:-all}"
mode="${VALIDATION_MODE:-strict}"

collect_reports() {
  python3 ./scripts/ci/collect_quality_reports.py || true
}
trap collect_reports EXIT

python3 ./scripts/ci/validate_ci_apps_catalog.py --mode "$mode" --target-flavors "$flavors_csv"
python3 ./scripts/ci/validate_admob_inventory.py --mode "$mode" --target-flavors "$flavors_csv"
bash ./scripts/ci/verify_env_contract.sh

if [[ "${SKIP_SIDE_PROJECT_QUALITY:-false}" != "true" ]]; then
  bash ./scripts/ci/run_side_project_quality.sh --install
fi

mkdir -p build/reports/dependencies
warning_log="build/reports/dependencies/gradle-quality.log"
: > "$warning_log"
./gradlew qualityCheck --continue --stacktrace --warning-mode all --no-daemon --max-workers=1 2>&1 | tee "$warning_log"
./gradlew :app:assembleNamazsurelerivedualarsesliDebug \
  --stacktrace --warning-mode all --no-daemon --max-workers=1 2>&1 | tee -a "$warning_log"
bash scripts/ci/release_task_graph_dry_run.sh 2>&1 | tee -a "$warning_log"
python3 scripts/ci/validate_gradle_warning_report.py --log "$warning_log"
