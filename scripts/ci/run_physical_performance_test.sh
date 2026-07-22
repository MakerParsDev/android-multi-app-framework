#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp="$(mktemp -d)"
cleanup() {
  rm -rf -- "$tmp"
  python3 "$repo_root/scripts/ci/generate_ci_google_services.py" \
    --repo "$repo_root" --clean --flavors kuran_kerim >/dev/null 2>&1 || true
}
trap cleanup EXIT

cat > "$tmp/adb" <<'ADB'
#!/usr/bin/env bash
set -euo pipefail
args=("$@")
if [[ "${args[0]:-}" == "-s" ]]; then
  serial="${args[1]:-}"
  args=("${args[@]:2}")
else
  serial="${MOCK_SERIAL:-physical-serial}"
fi
case "${args[0]:-}" in
  devices)
    printf 'List of devices attached\n%s\tdevice product:test model:Pixel_Test device:test transport_id:1\n' "${MOCK_SERIAL:-physical-serial}"
    ;;
  wait-for-device)
    ;;
  shell)
    args=("${args[@]:1}")
    case "${args[*]}" in
      'getprop ro.kernel.qemu') echo "${MOCK_QEMU:-0}" ;;
      'getprop ro.hardware') echo "${MOCK_HARDWARE:-tensor}" ;;
      'getprop ro.product.model') echo 'Pixel Test' ;;
      'getprop ro.product.manufacturer') echo 'Google' ;;
      'getprop ro.build.fingerprint') echo 'test/fingerprint' ;;
      'getprop ro.build.version.sdk') echo '35' ;;
      'getprop ro.product.cpu.abi') echo 'arm64-v8a' ;;
      'dumpsys battery') echo 'level: 80' ;;
      'dumpsys thermalservice') echo 'status: 0' ;;
      'df /data') echo '/data 100 10 90' ;;
      'settings get global '*) echo '0.0' ;;
      *) ;;
    esac
    ;;
  logcat)
    if [[ "${args[1]:-}" == "-d" ]]; then
      echo 'mock logcat'
    fi
    ;;
  *)
    ;;
esac
ADB
chmod +x "$tmp/adb"

cat > "$tmp/gradlew" <<'GRADLE'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" > "${GRADLE_LOG:?}"
GRADLE
chmod +x "$tmp/gradlew"

out="$tmp/out"
GRADLE_LOG="$tmp/gradle.log" \
ADB="$tmp/adb" \
GRADLEW="$tmp/gradlew" \
bash "$repo_root/scripts/ci/run_physical_performance.sh" \
  kuran_kerim startup 3 "$out"

grep -Fq ':performance:benchmark:connectedKuran_kerimBenchmarkReleaseAndroidTest' "$tmp/gradle.log"
grep -Fq 'benchmarkIterations=5' "$tmp/gradle.log"
grep -Fq 'StartupBenchmarks' "$tmp/gradle.log"
grep -Fq -- '--max-workers=1' "$tmp/gradle.log"
test -s "$out/device-metadata.txt"
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  grep -Fq "$setting=0.0" "$out/device-metadata.txt"
done
test -s "$out/kuran_kerim/logcat.txt"
test ! -e "$repo_root/app/src/kuran_kerim/google-services.json"

if MOCK_QEMU=1 GRADLE_LOG="$tmp/emulator-gradle.log" ADB="$tmp/adb" GRADLEW="$tmp/gradlew" \
  bash "$repo_root/scripts/ci/run_physical_performance.sh" kuran_kerim startup 5 "$tmp/emulator-out"; then
  echo 'ERROR: emulator target was accepted as physical evidence' >&2
  exit 1
fi
test ! -e "$tmp/emulator-gradle.log"
test ! -e "$repo_root/app/src/kuran_kerim/google-services.json"
echo 'Physical performance harness contract passed.'
