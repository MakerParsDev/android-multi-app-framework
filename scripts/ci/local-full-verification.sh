#!/usr/bin/env bash
set -euo pipefail

TARGET_FLAVORS="all"
BOOTSTRAP_ANDROID_SDK=false
SKIP_ANDROID=false
SKIP_FIREBASE=false
declare -a FIREBASE_GRADLE_EXCLUSIONS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-flavors) TARGET_FLAVORS="${2:?Missing value}"; shift 2 ;;
    --bootstrap-android-sdk) BOOTSTRAP_ANDROID_SDK=true; shift ;;
    --skip-android) SKIP_ANDROID=true; shift ;;
    --skip-firebase) SKIP_FIREBASE=true; shift ;;
    -h|--help) sed -n '1,32p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"
GRADLE_WARNING_LOG="build/reports/dependencies/gradle-quality.log"
mkdir -p "$(dirname "$GRADLE_WARNING_LOG")"
: > "$GRADLE_WARNING_LOG"
# shellcheck source=/dev/null
source scripts/ci/android-toolchain.sh

collect_reports() {
  if command -v python3 >/dev/null 2>&1; then
    python3 scripts/ci/collect_quality_reports.py || true
  fi
}
trap collect_reports EXIT

section() { printf '\n=== %s ===\n' "$*"; }
need_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "Missing command: $1" >&2; exit 127; }; }

if [[ "$BOOTSTRAP_ANDROID_SDK" == "true" ]]; then
  section "Linux/JDK/Android bootstrap"
  bash scripts/ci/setup-android-sdk.sh
fi

section "Repository state"
need_cmd git
git status --short --branch

section "Toolchain"
need_cmd python3
need_cmd bash
need_cmd java
java -version
./gradlew --version --no-daemon
python3 scripts/ci/validate_android_toolchain_config.py

section "Catalog and operational validators"
python3 scripts/ci/validate_ci_apps_catalog.py --mode strict --target-flavors "$TARGET_FLAVORS"
python3 scripts/ci/validate_admob_inventory.py --mode strict --target-flavors "$TARGET_FLAVORS"
python3 scripts/ci/validate_app_ads_txt.py --mode strict
bash scripts/ci/verify_env_contract.sh

if [[ "$SKIP_FIREBASE" != "true" ]]; then
  section "Firebase / Google Sign-In config"
  python3 scripts/ci/materialize_firebase_configs.py --flavors "$TARGET_FLAVORS" --mode strict --allow-existing
  python3 scripts/ci/verify_google_signin_config.py --flavors "$TARGET_FLAVORS" --require-web-client-id
fi

if [[ "$SKIP_ANDROID" == "true" ]]; then
  section "Android Gradle checks skipped"
  exit 0
fi

section "Installed Android toolchain"
bash scripts/ci/verify-android-toolchain.sh

run_gradle_with_warning_report() {
  ./gradlew "$@" "${FIREBASE_GRADLE_EXCLUSIONS[@]}" \
    --stacktrace --warning-mode all --no-daemon 2>&1 | tee -a "$GRADLE_WARNING_LOG"
}

run_gradle_fresh_phase() {
  run_gradle_with_warning_report "$@" --max-workers=1
}

capitalize_flavor() {
  local value="$1"
  printf '%s\n' "${value^}"
}

fresh_flavors() {
  ./gradlew -q printFlavors --no-daemon --max-workers=1 |
    python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)))'
}

configure_firebase_gradle_exclusions() {
  [[ "$SKIP_FIREBASE" == "true" ]] || return 0

  local flavor variant_task
  while IFS= read -r flavor; do
    [[ -n "$flavor" ]] || continue
    variant_task="$(capitalize_flavor "$flavor")"
    FIREBASE_GRADLE_EXCLUSIONS+=(
      "-x"
      ":app:process${variant_task}DebugGoogleServices"
      "-x"
      ":app:process${variant_task}ReleaseGoogleServices"
    )
  done < <(fresh_flavors)

  echo "Firebase Gradle tasks excluded: ${#FIREBASE_GRADLE_EXCLUSIONS[@]} arguments"
}

android_library_task_paths() {
  local task_name="$1"
  find core feature -mindepth 2 -maxdepth 2 -name build.gradle.kts -print |
    sed -E "s#^([^/]+)/([^/]+)/build[.]gradle[.]kts#:\\1:\\2:${task_name}#" |
    sort
}

run_tasks_in_batches() {
  local label="$1" batch_size="$2"
  shift 2
  local -a batch=()
  local task
  local batch_number=1

  for task in "$@"; do
    batch+=("$task")
    if (( ${#batch[@]} == batch_size )); then
      echo "--- $label batch $batch_number: ${batch[*]} ---"
      run_gradle_fresh_phase "${batch[@]}"
      batch=()
      ((batch_number += 1))
    fi
  done

  if (( ${#batch[@]} > 0 )); then
    echo "--- $label batch $batch_number: ${batch[*]} ---"
    run_gradle_fresh_phase "${batch[@]}"
  fi
}

run_fresh_bootstrap_quality_gate() {
  local flavor variant_task
  local -a app_lint_tasks=()
  local -a app_test_tasks=()
  local -a library_lint_tasks=()
  local -a library_test_tasks=()

  while IFS= read -r flavor; do
    [[ -n "$flavor" ]] || continue
    variant_task="$(capitalize_flavor "$flavor")"
    app_lint_tasks+=(":app:lint${variant_task}Debug")
    app_test_tasks+=(":app:test${variant_task}DebugUnitTest")
  done < <(fresh_flavors)

  mapfile -t library_lint_tasks < <(android_library_task_paths lintDebug)
  mapfile -t library_test_tasks < <(android_library_task_paths testDebugUnitTest)

  [[ ${#app_lint_tasks[@]} -eq 17 ]] || {
    echo "Expected 17 application lint tasks, found ${#app_lint_tasks[@]}" >&2
    return 1
  }
  [[ ${#app_test_tasks[@]} -eq 17 ]] || {
    echo "Expected 17 application test tasks, found ${#app_test_tasks[@]}" >&2
    return 1
  }
  [[ ${#library_lint_tasks[@]} -eq 20 ]] || {
    echo "Expected 20 library lint tasks, found ${#library_lint_tasks[@]}" >&2
    return 1
  }
  [[ ${#library_test_tasks[@]} -eq 20 ]] || {
    echo "Expected 20 library test tasks, found ${#library_test_tasks[@]}" >&2
    return 1
  }

  section "Fresh runner static quality phase"
  run_gradle_fresh_phase staticQualityCheck

  section "Fresh runner application lint phases"
  run_tasks_in_batches "application lint" 3 "${app_lint_tasks[@]}"

  section "Fresh runner library lint phase"
  run_gradle_fresh_phase "${library_lint_tasks[@]}"

  section "Fresh runner application unit-test phases"
  run_tasks_in_batches "application unit-test" 4 "${app_test_tasks[@]}"

  section "Fresh runner library unit-test phase"
  run_gradle_fresh_phase "${library_test_tasks[@]}"

  section "Fresh runner coverage phase"
  run_gradle_fresh_phase koverVerify koverXmlReport koverHtmlReport
}

configure_firebase_gradle_exclusions

section "Gradle checks"
if [[ "$BOOTSTRAP_ANDROID_SDK" == "true" ]]; then
  # A single 17-flavor lint graph retains enough compiler/lint state to exhaust an
  # 8 GiB runner even with one worker. Preserve the exact gate coverage while
  # resetting the Gradle/Kotlin JVM between bounded phases.
  run_fresh_bootstrap_quality_gate
else
  run_gradle_with_warning_report qualityCheck --continue
fi

section "Representative debug package and instrumentation smoke"
run_gradle_with_warning_report :app:assembleNamazsurelerivedualarsesliDebug --max-workers=1

section "Release task-graph dry-run"
bash scripts/ci/release_task_graph_dry_run.sh 2>&1 | tee -a "$GRADLE_WARNING_LOG"
python3 scripts/ci/validate_gradle_warning_report.py --log "$GRADLE_WARNING_LOG"
