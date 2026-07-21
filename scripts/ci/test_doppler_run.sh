#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WRAPPER="$REPO_ROOT/scripts/doppler-run.sh"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/doppler-run-test.XXXXXX")"
trap 'rm -rf -- "$tmp_dir"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_eq() {
  local expected="$1"
  local actual="$2"
  local message="$3"
  [[ "$actual" == "$expected" ]] || fail "$message: expected='$expected' actual='$actual'"
}

encode_file() {
  python3 - "$1" <<'PY'
import base64
import pathlib
import sys

print(base64.b64encode(pathlib.Path(sys.argv[1]).read_bytes()).decode("ascii"))
PY
}

fake_bin="$tmp_dir/bin"
mkdir -p "$fake_bin"
cat > "$fake_bin/doppler" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

while [[ "$#" -gt 0 ]]; do
  if [[ "$1" == "--" ]]; then
    shift
    exec "$@"
  fi
  shift
done

echo "fake doppler: missing -- command separator" >&2
exit 2
SH
chmod +x "$fake_bin/doppler"

keystore_fixture="$tmp_dir/fixture-keystore.bin"
printf '\x30\x82\x01\x00synthetic-keystore-fixture\n' > "$keystore_fixture"
keystore_base64="$(encode_file "$keystore_fixture")"

service_account_fixture="$tmp_dir/service-account.json"
cat > "$service_account_fixture" <<'JSON'
{
  "type": "service_account",
  "project_id": "makerpars-oaslananka-mobil",
  "private_key_id": "synthetic-key-id",
  "private_key": "-----BEGIN PRIVATE KEY-----\nSYNTHETIC\n-----END PRIVATE KEY-----\n",
  "client_email": "ci-test@makerpars-oaslananka-mobil.iam.gserviceaccount.com"
}
JSON
service_account_base64="$(encode_file "$service_account_fixture")"

invalid_service_account_fixture="$tmp_dir/invalid-service-account.json"
printf '{"type":"service_account"}\n' > "$invalid_service_account_fixture"
invalid_service_account_base64="$(encode_file "$invalid_service_account_fixture")"

probe="$tmp_dir/probe.sh"
cat > "$probe" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

[[ -f "$KEYSTORE_FILE" ]]
[[ -f "$PLAY_SERVICE_ACCOUNT_JSON" ]]
[[ "$KEYSTORE_FILE" != "/stale/release.jks" ]]
[[ "$PLAY_SERVICE_ACCOUNT_JSON" != "/stale/service-account.json" ]]

python3 - "$KEYSTORE_FILE" "$EXPECTED_KEYSTORE_FILE" "$PLAY_SERVICE_ACCOUNT_JSON" <<'PY'
import json
import pathlib
import sys

actual_keystore = pathlib.Path(sys.argv[1]).read_bytes()
expected_keystore = pathlib.Path(sys.argv[2]).read_bytes()
if actual_keystore != expected_keystore:
    raise SystemExit("keystore bytes do not match fixture")

payload = json.loads(pathlib.Path(sys.argv[3]).read_text(encoding="utf-8"))
if payload["type"] != "service_account":
    raise SystemExit("service account type mismatch")
if payload["project_id"] != "makerpars-oaslananka-mobil":
    raise SystemExit("service account project mismatch")
PY

{
  printf 'KEYSTORE_FILE=%s\n' "$KEYSTORE_FILE"
  printf 'PLAY_SERVICE_ACCOUNT_JSON=%s\n' "$PLAY_SERVICE_ACCOUNT_JSON"
  printf 'KEYSTORE_MODE=%s\n' "$(stat -c '%a' "$KEYSTORE_FILE")"
  printf 'SERVICE_ACCOUNT_MODE=%s\n' "$(stat -c '%a' "$PLAY_SERVICE_ACCOUNT_JSON")"
  printf 'TEMP_DIR_MODE=%s\n' "$(stat -c '%a' "$(dirname "$KEYSTORE_FILE")")"
} > "$RESULT_FILE"

exit "${WRAPPED_EXIT_CODE:-0}"
SH
chmod +x "$probe"

run_with_fixtures() {
  local result_file="$1"
  local wrapped_exit_code="${2:-0}"
  PATH="$fake_bin:$PATH" \
    TMPDIR="$tmp_dir/runtime" \
    KEYSTORE_BASE64="$keystore_base64" \
    PLAY_SERVICE_ACCOUNT_JSON_BASE64="$service_account_base64" \
    KEYSTORE_FILE="/stale/release.jks" \
    PLAY_SERVICE_ACCOUNT_JSON="/stale/service-account.json" \
    EXPECTED_KEYSTORE_FILE="$keystore_fixture" \
    RESULT_FILE="$result_file" \
    WRAPPED_EXIT_CODE="$wrapped_exit_code" \
    "$WRAPPER" -- "$probe"
}

mkdir -p "$tmp_dir/runtime"

success_result="$tmp_dir/success-result.env"
run_with_fixtures "$success_result"
# shellcheck disable=SC1090
source "$success_result"
assert_eq "600" "$KEYSTORE_MODE" "keystore mode"
assert_eq "600" "$SERVICE_ACCOUNT_MODE" "service account mode"
assert_eq "700" "$TEMP_DIR_MODE" "temporary directory mode"
[[ ! -e "$KEYSTORE_FILE" ]] || fail "keystore was not cleaned after success"
[[ ! -e "$PLAY_SERVICE_ACCOUNT_JSON" ]] || fail "service account was not cleaned after success"

failure_result="$tmp_dir/failure-result.env"
set +e
run_with_fixtures "$failure_result" 23
wrapped_rc=$?
set -e
assert_eq "23" "$wrapped_rc" "wrapped command exit status"
# shellcheck disable=SC1090
source "$failure_result"
[[ ! -e "$KEYSTORE_FILE" ]] || fail "keystore was not cleaned after command failure"
[[ ! -e "$PLAY_SERVICE_ACCOUNT_JSON" ]] || fail "service account was not cleaned after command failure"

malformed_marker="$tmp_dir/malformed-command-ran"
set +e
PATH="$fake_bin:$PATH" \
  TMPDIR="$tmp_dir/runtime" \
  KEYSTORE_BASE64='%%%not-valid-base64%%%' \
  PLAY_SERVICE_ACCOUNT_JSON_BASE64="$service_account_base64" \
  "$WRAPPER" -- sh -c "touch '$malformed_marker'" \
  >"$tmp_dir/malformed.out" 2>"$tmp_dir/malformed.err"
malformed_rc=$?
set -e
[[ "$malformed_rc" -ne 0 ]] || fail "malformed base64 unexpectedly succeeded"
[[ ! -e "$malformed_marker" ]] || fail "command ran after malformed base64"
grep -q 'KEYSTORE_BASE64 is not valid base64' "$tmp_dir/malformed.err" \
  || fail "malformed base64 error did not identify KEYSTORE_BASE64"

invalid_json_marker="$tmp_dir/invalid-json-command-ran"
set +e
PATH="$fake_bin:$PATH" \
  TMPDIR="$tmp_dir/runtime" \
  KEYSTORE_BASE64="$keystore_base64" \
  PLAY_SERVICE_ACCOUNT_JSON_BASE64="$invalid_service_account_base64" \
  "$WRAPPER" -- sh -c "touch '$invalid_json_marker'" \
  >"$tmp_dir/invalid-json.out" 2>"$tmp_dir/invalid-json.err"
invalid_json_rc=$?
set -e
[[ "$invalid_json_rc" -ne 0 ]] || fail "invalid service-account JSON unexpectedly succeeded"
[[ ! -e "$invalid_json_marker" ]] || fail "command ran after invalid service-account JSON"
grep -q 'PLAY_SERVICE_ACCOUNT_JSON_BASE64 is missing required field: project_id' \
  "$tmp_dir/invalid-json.err" \
  || fail "invalid JSON error did not identify the missing project_id field"

set +e
PATH="$fake_bin:$PATH" "$WRAPPER" >"$tmp_dir/usage.out" 2>"$tmp_dir/usage.err"
usage_rc=$?
set -e
assert_eq "2" "$usage_rc" "missing-command usage exit status"
grep -q 'Usage: scripts/doppler-run.sh -- <command>' "$tmp_dir/usage.err" \
  || fail "usage output is missing"

echo "Doppler run integration test passed"
