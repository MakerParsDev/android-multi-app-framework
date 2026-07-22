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
case "${1:-}" in
  devices)
    printf 'List of devices attached\nphysical-serial\tdevice product:test model:Pixel_Test device:test transport_id:1\n'
    ;;
  wait-for-device)
    ;;
  shell)
    shift
    case "$*" in
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
    if [[ "${2:-}" == "-d" ]]; then
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
test -s "$out/kuran_kerim/logcat.txt"
test ! -e "$repo_root/app/src/kuran_kerim/google-services.json"
echo 'Physical performance harness contract passed.'
