#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

selection="${1:-all}"
suite="${2:-all}"
iterations_raw="${3:-10}"
out="${4:-build/physical-performance}"
adb_bin="${ADB:-adb}"
gradlew="${GRADLEW:-./gradlew}"

[[ "$iterations_raw" =~ ^[0-9]+$ ]] || {
  echo "ERROR: iterations must be an integer: $iterations_raw" >&2
  exit 2
}
iterations="$iterations_raw"
if (( iterations < 5 )); then
  iterations=5
elif (( iterations > 30 )); then
  iterations=30
fi

mapfile -t all_flavors < <(
  python3 scripts/ci/resolve_ci_flavor_matrix.py |
    sed -n 's/^flavors=//p' |
    python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)))'
)
[[ "${#all_flavors[@]}" -gt 0 ]] || {
  echo 'ERROR: no app flavors resolved' >&2
  exit 1
}

if [[ "$selection" == "all" ]]; then
  flavors=("${all_flavors[@]}")
elif printf '%s\n' "${all_flavors[@]}" | grep -Fxq "$selection"; then
  flavors=("$selection")
else
  echo "ERROR: unknown flavor: $selection" >&2
  exit 2
fi

cleanup() {
  if [[ "${#flavors[@]}" -gt 0 ]]; then
    joined="$(IFS=,; echo "${flavors[*]}")"
    python3 scripts/ci/generate_ci_google_services.py --clean --flavors "$joined" || true
  fi
}
trap cleanup EXIT

case "$suite" in
  startup) class_filter="StartupBenchmarks" ;;
  frames) class_filter="FrameBenchmarks" ;;
  all) class_filter="" ;;
  *)
    echo "ERROR: invalid suite: $suite" >&2
    exit 2
    ;;
esac

require_executable() {
  local value="$1" label="$2"
  if [[ "$value" == */* ]]; then
    [[ -x "$value" ]] || {
      echo "ERROR: $label is not executable: $value" >&2
      exit 1
    }
  else
    command -v "$value" >/dev/null 2>&1 || {
      echo "ERROR: $label is required on the physical performance runner" >&2
      exit 1
    }
  fi
}

require_executable "$adb_bin" adb
require_executable "$gradlew" Gradle-wrapper

mapfile -t devices < <("$adb_bin" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ "${#devices[@]}" -ne 1 ]]; then
  echo "ERROR: exactly one authorized Android device is required; found ${#devices[@]}" >&2
  "$adb_bin" devices -l >&2 || true
  exit 1
fi
export ANDROID_SERIAL="${devices[0]}"
case "$ANDROID_SERIAL" in
  emulator-*|localhost:*|127.0.0.1:*)
    echo "ERROR: emulator serial is not valid physical performance evidence: $ANDROID_SERIAL" >&2
    exit 1
    ;;
esac
qemu="$("$adb_bin" -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')"
hardware="$("$adb_bin" -s "$ANDROID_SERIAL" shell getprop ro.hardware | tr -d '\r')"
if [[ "$qemu" == "1" || "$hardware" =~ ^(ranchu|goldfish|cuttlefish|vbox86)$ ]]; then
  echo "ERROR: emulated Android target is not valid physical performance evidence: serial=$ANDROID_SERIAL hardware=$hardware" >&2
  exit 1
fi

mkdir -p "$out"
"$adb_bin" -s "$ANDROID_SERIAL" wait-for-device
"$adb_bin" -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
"$adb_bin" -s "$ANDROID_SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true

{
  echo "serial=$ANDROID_SERIAL"
  echo "model=$("$adb_bin" -s "$ANDROID_SERIAL" shell getprop ro.product.model | tr -d '\r')"
  echo "manufacturer=$("$adb_bin" -s "$ANDROID_SERIAL" shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "fingerprint=$("$adb_bin" -s "$ANDROID_SERIAL" shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "api=$("$adb_bin" -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "abi=$("$adb_bin" -s "$ANDROID_SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "iterations=$iterations"
  echo "selection=$selection"
  echo "suite=$suite"
  echo '--- battery ---'
  "$adb_bin" -s "$ANDROID_SERIAL" shell dumpsys battery
  echo '--- thermal ---'
  "$adb_bin" -s "$ANDROID_SERIAL" shell dumpsys thermalservice
  echo '--- storage ---'
  "$adb_bin" -s "$ANDROID_SERIAL" shell df /data
  echo '--- animation scales ---'
  for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    printf '%s=' "$setting"
    "$adb_bin" -s "$ANDROID_SERIAL" shell settings get global "$setting" | tr -d '\r'
  done
} > "$out/device-metadata.txt"

for flavor in "${flavors[@]}"; do
  token="${flavor^}"
  flavor_out="$out/$flavor"
  mkdir -p "$flavor_out"
  rm -rf \
    performance/benchmark/build/outputs/connected_android_test_additional_output \
    performance/benchmark/build/outputs/androidTest-results/connected \
    performance/benchmark/build/reports/androidTests/connected

  python3 scripts/ci/generate_ci_google_services.py --flavors "$flavor"
  "$adb_bin" -s "$ANDROID_SERIAL" logcat -c || true

  args=(
    "-PciSmoke=true"
    "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark"
    "-Pandroid.testInstrumentationRunnerArguments.benchmarkIterations=$iterations"
  )
  if [[ -n "$class_filter" ]]; then
    args+=("-Pandroid.testInstrumentationRunnerArguments.class=com.parsfilo.contentapp.performance.$class_filter")
  fi

  set +e
  "$gradlew" ":performance:benchmark:connected${token}BenchmarkReleaseAndroidTest" \
    "${args[@]}" \
    --no-daemon \
    --no-configuration-cache \
    --stacktrace \
    --max-workers=1
  gradle_rc=$?
  set -e

  cp -R performance/benchmark/build/outputs/connected_android_test_additional_output/. "$flavor_out/" 2>/dev/null || true
  cp -R performance/benchmark/build/outputs/androidTest-results/. "$flavor_out/androidTest-results/" 2>/dev/null || true
  cp -R performance/benchmark/build/reports/androidTests/. "$flavor_out/androidTest-reports/" 2>/dev/null || true
  "$adb_bin" -s "$ANDROID_SERIAL" logcat -d -v threadtime > "$flavor_out/logcat.txt" || true

  if [[ "$gradle_rc" -ne 0 ]]; then
    echo "ERROR: physical performance benchmark failed for $flavor" >&2
    exit "$gradle_rc"
  fi
done

echo "Physical performance run completed: $out"
