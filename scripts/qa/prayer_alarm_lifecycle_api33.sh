#!/usr/bin/env bash
# Explicit opt-in isolated AVD acceptance; never run against production/user packages.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ADB="$HOME/Android/Sdk/platform-tools/adb"
S="emulator-5556"
P="com.parsfilo.contentapp.feature.prayertimes.test"
C="com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmLifecycleProbeDeviceTest"
APK="$ROOT/feature/prayertimes/build/outputs/apk/androidTest/debug/prayertimes-debug-androidTest.apk"
TZ_ORIG=""
INSTALLED=0
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
device() { "$ADB" -s "$S" "$@"; }

test "$(device emu avd name | head -1 | tr -d '\r')" = AMF_API33 || fail "Wrong AVD"
test "$(device shell getprop ro.build.version.sdk | tr -d '\r')" = 33 || fail "Expected API33"
pgrep -af -- '-avd AMF_API33' | grep -F -- '-port 5556' \
    | grep -Fq -- '-read-only' || fail "AVD must be launched with -read-only"
test -s "$APK" || fail "Build test APK first"
if device shell pm list packages "$P" | grep -q "^package:$P$"; then
    fail "Refusing to overwrite a preinstalled probe package"
fi
TZ_ORIG="$(device shell getprop persist.sys.timezone | tr -d '\r')"
test -n "$TZ_ORIG" || fail "Unknown starting timezone"

run_test() {
    local output
    output="$(device shell am instrument -w -r -e amf497LifecycleManual true -e class "$C#$1" "$P/androidx.test.runner.AndroidJUnitRunner")"
    if ! grep -Fq 'OK (1 test)' <<<"$output"; then
        printf '%s\n' "$output" >&2
        fail "Instrumentation $1 failed"
    fi
}
probe_logs() { device logcat -d -s AMF497_LIFECYCLE:I; }
alarm_queued() { device shell dumpsys alarm | grep -E "RTC_WAKEUP.*$P" >/dev/null; }
await_log() {
    for _ in $(seq 1 90); do
        if probe_logs | grep -Fq "$1"; then return 0; fi
        sleep 1
    done
    return 1
}
# shellcheck disable=SC2317 # Invoked indirectly by the EXIT/INT/TERM trap.
cleanup() {
    local status=$?
    trap - EXIT INT TERM
    if [ -n "$TZ_ORIG" ]; then
        device shell cmd alarm set-timezone "$TZ_ORIG" >/dev/null 2>&1 || true
    fi
    if [ "$INSTALLED" -eq 1 ]; then
        ( run_test cleanupIsolatedSystemLifecycleCheck ) >/dev/null 2>&1 || true
        device uninstall "$P" >/dev/null 2>&1 || true
    fi
    printf 'CLEANUP: original timezone=%s; test package removal attempted=%s\n' "$TZ_ORIG" "$INSTALLED"
    exit "$status"
}
trap cleanup EXIT INT TERM

device install "$APK" >/dev/null
INSTALLED=1
run_test armForIsolatedSystemLifecycleCheck
alarm_queued || fail "Initial RTC_WAKEUP alarm absent"
echo 'PASS initial alarm registered'

device reboot
for _ in $(seq 1 100); do
    if [ "$(device shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then break; fi
    sleep 2
done
test "$(device shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 || fail "Reboot incomplete"
await_log 'LIFECYCLE_RECEIVED action=android.intent.action.BOOT_COMPLETED' || fail "Boot broadcast missing"
alarm_queued || fail "No alarm after reboot"
echo 'PASS real boot broadcast and rearm'

device shell cmd alarm set-timezone Europe/London >/dev/null
await_log 'LIFECYCLE_RECEIVED action=android.intent.action.TIMEZONE_CHANGED' || fail "Timezone broadcast missing"
alarm_queued || fail "No alarm after timezone switch"
echo 'PASS real timezone broadcast and rearm'
device shell cmd alarm set-timezone "$TZ_ORIG" >/dev/null

device logcat -c
run_test armForActualBroadcastDeliveryCheck
await_log 'FIRE_RECEIVED testPackage=' || fail "One-shot alarm not delivered"
echo 'PASS real AlarmManager one-shot broadcast delivery'
echo 'PASS all isolated Android 13 lifecycle acceptance gates'

# Preserve the successful acceptance result when EXIT cleanup completes.
exit 0
