# Doppler Secret Materialization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one repository-wide Doppler wrapper that materializes `KEYSTORE_BASE64` and `PLAY_SERVICE_ACCOUNT_JSON_BASE64` into private temporary files, exports valid file paths, executes a requested command, preserves its exit status, and always cleans up.

**Architecture:** `scripts/doppler-run.sh` is the public entry point. The outer process validates the CLI invocation, creates a private temporary directory, and starts an internal mode through `doppler run`; the internal mode reads secrets only from the environment, validates and writes files with restrictive permissions, exports `KEYSTORE_FILE` and `PLAY_SERVICE_ACCOUNT_JSON`, then executes the requested command. A self-contained Bash integration test uses a fake Doppler binary and synthetic secret fixtures so every security and lifecycle behavior is testable without real credentials.

**Tech Stack:** Bash 4+, Python 3 standard library, Doppler CLI v3, GNU coreutils (`mktemp`, `stat`), existing repository validation scripts.

## Global Constraints

- Public interface is exactly `scripts/doppler-run.sh -- <command> [arguments...]`.
- Secret values and decoded contents must never be printed or passed as command-line arguments.
- Temporary directories must be outside the repository and have mode `0700`.
- Materialized files must have mode `0600`.
- Base64 decoding must use strict validation and fail closed.
- Service-account JSON must require `type=service_account`, `project_id`, `client_email`, and `private_key`.
- Generated paths override stale `KEYSTORE_FILE` and `PLAY_SERVICE_ACCOUNT_JSON` values only when their corresponding base64 values are present.
- Existing file-path variables remain untouched when the corresponding base64 value is absent.
- Wrapped-command exit status must be preserved.
- Cleanup must run on success, command failure, `HUP`, `INT`, and `TERM`.
- This change must not remove or alter Doppler secret values.
- This change must not refactor unrelated Azure or GitHub pipeline scripts.

---

### Task 1: Implement and integration-test the canonical wrapper

**Files:**
- Create: `scripts/ci/test_doppler_run.sh`
- Create: `scripts/doppler-run.sh`

**Interfaces:**
- Consumes: Doppler environment variables `KEYSTORE_BASE64`, `PLAY_SERVICE_ACCOUNT_JSON_BASE64`, optional `KEYSTORE_FILE`, optional `PLAY_SERVICE_ACCOUNT_JSON`, optional `DOPPLER_PROJECT`, and optional `DOPPLER_CONFIG`.
- Produces: executable command `scripts/doppler-run.sh -- <command> [arguments...]`; generated environment variables `KEYSTORE_FILE` and `PLAY_SERVICE_ACCOUNT_JSON` visible only to the wrapped command.

- [ ] **Step 1: Create the failing integration test**

Create `scripts/ci/test_doppler_run.sh` with this complete content:

```bash
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
```

- [ ] **Step 2: Make the test executable and run it to verify failure**

Run:

```bash
chmod +x scripts/ci/test_doppler_run.sh
scripts/ci/test_doppler_run.sh
```

Expected: FAIL before any fixture command runs because `scripts/doppler-run.sh` does not exist.

- [ ] **Step 3: Implement the canonical wrapper**

Create `scripts/doppler-run.sh` with this complete content:

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_PATH="$SCRIPT_DIR/$(basename "${BASH_SOURCE[0]}")"
INTERNAL_MODE="__materialize"

usage() {
  echo "Usage: scripts/doppler-run.sh -- <command> [arguments...]" >&2
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

materialize_secret() {
  local secret_name="$1"
  local output_path="$2"
  local material_kind="$3"

  command -v python3 >/dev/null 2>&1 || fail "python3 is required to materialize $secret_name"

  SECRET_NAME="$secret_name" \
  OUTPUT_PATH="$output_path" \
  MATERIAL_KIND="$material_kind" \
  python3 <<'PY'
import base64
import binascii
import json
import os
import pathlib

secret_name = os.environ["SECRET_NAME"]
output_path = pathlib.Path(os.environ["OUTPUT_PATH"])
material_kind = os.environ["MATERIAL_KIND"]
encoded = os.environ.get(secret_name, "")
normalized = "".join(encoded.split())

try:
    raw = base64.b64decode(normalized, validate=True)
except (binascii.Error, ValueError):
    raise SystemExit(f"ERROR: {secret_name} is not valid base64")

if not raw:
    raise SystemExit(f"ERROR: {secret_name} decoded to an empty file")

if material_kind == "service_account":
    try:
        payload = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise SystemExit(f"ERROR: {secret_name} is not valid UTF-8 JSON")

    if payload.get("type") != "service_account":
        raise SystemExit(f"ERROR: {secret_name} must contain type=service_account")

    for field in ("project_id", "client_email", "private_key"):
        value = payload.get(field)
        if not isinstance(value, str) or not value.strip():
            raise SystemExit(f"ERROR: {secret_name} is missing required field: {field}")

output_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
fd = os.open(output_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, "wb") as stream:
    stream.write(raw)
os.chmod(output_path, 0o600)
PY
}

run_internal() {
  local temp_dir="${1:-}"
  shift || true

  [[ -n "$temp_dir" ]] || fail "internal temporary directory is missing"
  [[ "${1:-}" == "--" ]] || fail "internal command separator is missing"
  shift
  [[ "$#" -gt 0 ]] || fail "internal wrapped command is missing"
  [[ -d "$temp_dir" ]] || fail "internal temporary directory does not exist"

  chmod 700 "$temp_dir"
  umask 077

  if [[ -n "${KEYSTORE_BASE64:-}" ]]; then
    local keystore_path="$temp_dir/release-keystore.bin"
    materialize_secret "KEYSTORE_BASE64" "$keystore_path" "binary"
    export KEYSTORE_FILE="$keystore_path"
  fi

  if [[ -n "${PLAY_SERVICE_ACCOUNT_JSON_BASE64:-}" ]]; then
    local service_account_path="$temp_dir/play-service-account.json"
    materialize_secret \
      "PLAY_SERVICE_ACCOUNT_JSON_BASE64" \
      "$service_account_path" \
      "service_account"
    export PLAY_SERVICE_ACCOUNT_JSON="$service_account_path"
  fi

  exec "$@"
}

if [[ "${1:-}" == "$INTERNAL_MODE" ]]; then
  shift
  run_internal "$@"
fi

command -v doppler >/dev/null 2>&1 || fail "Doppler CLI is not installed or not on PATH"

if [[ "${1:-}" != "--" ]]; then
  usage
  exit 2
fi
shift

if [[ "$#" -eq 0 ]]; then
  usage
  exit 2
fi

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/android-multi-app-doppler.XXXXXX")"
chmod 700 "$temp_dir"

cleanup() {
  rm -rf -- "$temp_dir"
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

doppler_args=(run)
if [[ -n "${DOPPLER_PROJECT:-}" ]]; then
  doppler_args+=(--project "$DOPPLER_PROJECT")
fi
if [[ -n "${DOPPLER_CONFIG:-}" ]]; then
  doppler_args+=(--config "$DOPPLER_CONFIG")
fi

set +e
doppler "${doppler_args[@]}" -- \
  "$SCRIPT_PATH" "$INTERNAL_MODE" "$temp_dir" -- "$@"
command_rc=$?
set -e

exit "$command_rc"
```

- [ ] **Step 4: Make the wrapper executable and run the integration test**

Run:

```bash
chmod +x scripts/doppler-run.sh
scripts/ci/test_doppler_run.sh
```

Expected:

```text
Doppler run integration test passed
```

- [ ] **Step 5: Run static shell syntax checks**

Run:

```bash
bash -n scripts/doppler-run.sh
bash -n scripts/ci/test_doppler_run.sh
```

Expected: both commands exit `0` with no output.

- [ ] **Step 6: Commit the wrapper and test**

Run:

```bash
git add scripts/doppler-run.sh scripts/ci/test_doppler_run.sh
git commit -m "feat: materialize Doppler file secrets safely"
```

Expected: one commit containing only the wrapper and its integration test.

---

### Task 2: Document the canonical workflow and verify it with real Doppler data

**Files:**
- Modify: `docs/SECRETS_SETUP.md:180-225`
- Test: `scripts/ci/test_doppler_run.sh`
- Test: `scripts/ci/verify_env_contract.sh`
- Test: `scripts/ci/verify_release_signing_config.sh`
- Test: `scripts/ci/verify_play_service_account_project.py`

**Interfaces:**
- Consumes: the `scripts/doppler-run.sh -- <command>` interface from Task 1 and the directory-scoped Doppler `android-multi-app-framework/prod` configuration.
- Produces: documented repository-wide command examples and evidence that real Doppler values materialize correctly without persisting secret files.

- [ ] **Step 1: Replace the secret-source-order and architecture section**

In `docs/SECRETS_SETUP.md`, replace the existing `## Env Contract Kaynak Sırası` and `## Mimari Şema` sections with:

```markdown
## Env Contract Kaynak Sırası

Kanonik secret sözleşmesi şu kaynak sırasıyla düşünülmelidir:

1. Doppler `android-multi-app-framework/prod` — paylaşılan secret ve ortam değerlerinin kanonik kaynağı
2. `.env.template` — repo içindeki isim/contract referansı; gerçek değer içermez
3. Lokal dosya yolları — yalnızca geliştiricinin bilerek kullandığı gitignored dosyalar
4. CI bootstrap secret store — yalnızca Doppler'a erişmek için gereken `DOPPLER_TOKEN`

`KEYSTORE_BASE64` ve `PLAY_SERVICE_ACCOUNT_JSON_BASE64` Doppler'da tutulur. `KEYSTORE_FILE` ve `PLAY_SERVICE_ACCOUNT_JSON` kalıcı, makineye özel Doppler yolları olarak kullanılmaz; `scripts/doppler-run.sh` bunları her komut için özel geçici dosyalara dönüştürür.

## Repository-wide Doppler Wrapper

Herhangi bir build, doğrulama veya yayın komutunu Doppler ortamıyla çalıştırmak için:

```bash
scripts/doppler-run.sh -- <command> [arguments...]
```

Örnekler:

```bash
scripts/doppler-run.sh -- ./gradlew tasks
scripts/doppler-run.sh -- scripts/ci/verify_release_signing_config.sh
scripts/doppler-run.sh -- python3 scripts/ci/verify_play_service_account_project.py
```

Wrapper aşağıdaki işlemleri otomatik yapar:

- `KEYSTORE_BASE64` değerini `0600` izinli geçici dosyaya dönüştürür ve `KEYSTORE_FILE` olarak export eder.
- `PLAY_SERVICE_ACCOUNT_JSON_BASE64` değerini doğrular, `0600` izinli geçici dosyaya dönüştürür ve `PLAY_SERVICE_ACCOUNT_JSON` olarak export eder.
- Geçici dizini `0700` izinle oluşturur.
- Komutun gerçek exit code'unu korur.
- Başarı, hata veya sinyal durumunda geçici dosyaları siler.
- Secret değerlerini veya dosya içeriklerini loglamaz.

## Mimari Şema

```text
Doppler prod
  KEYSTORE_BASE64
  PLAY_SERVICE_ACCOUNT_JSON_BASE64
  diğer environment değerleri
          │
          ▼
scripts/doppler-run.sh
  ├─ private temp dir (0700)
  ├─ release-keystore.bin (0600) → KEYSTORE_FILE
  ├─ play-service-account.json (0600) → PLAY_SERVICE_ACCOUNT_JSON
  └─ wrapped command
          │
          ▼
cleanup trap → tüm geçici secret dosyaları silinir
```

Lokal geliştirmede bilerek kullanılan mevcut dosya yolları desteklenmeye devam eder. İlgili base64 değeri boşsa wrapper mevcut `KEYSTORE_FILE` veya `PLAY_SERVICE_ACCOUNT_JSON` değerini değiştirmez.
```

- [ ] **Step 2: Run repository-local regression checks**

Run:

```bash
scripts/ci/test_doppler_run.sh
scripts/ci/verify_env_contract.sh
bash -n scripts/doppler-run.sh
bash -n scripts/ci/test_doppler_run.sh
```

Expected:

```text
Doppler run integration test passed
::notice::Env contract is valid for GitHub Actions runtime values.
```

The two `bash -n` commands must exit `0` with no output.

- [ ] **Step 3: Run the real Doppler smoke test without printing secret values**

Run as the user whose directory-scoped Doppler login is configured for `/srv/repolar/MakerParsDev/android-multi-app-framework`:

```bash
before_count="$(find "${TMPDIR:-/tmp}" -maxdepth 1 -type d -name 'android-multi-app-doppler.*' | wc -l)"

scripts/doppler-run.sh -- bash -c '
  set -euo pipefail
  test -f "$KEYSTORE_FILE"
  test -f "$PLAY_SERVICE_ACCOUNT_JSON"
  test "$(stat -c %a "$KEYSTORE_FILE")" = "600"
  test "$(stat -c %a "$PLAY_SERVICE_ACCOUNT_JSON")" = "600"
  test "$(stat -c %a "$(dirname "$KEYSTORE_FILE")")" = "700"
  scripts/ci/verify_release_signing_config.sh
  python3 scripts/ci/verify_play_service_account_project.py
'

after_count="$(find "${TMPDIR:-/tmp}" -maxdepth 1 -type d -name 'android-multi-app-doppler.*' | wc -l)"
test "$before_count" = "$after_count"
```

Expected output contains only validation summaries similar to:

```text
OK: Release keystore is readable and alias exists.
OK: Play service account validated (project_id=makerpars-oaslananka-mobil, email_domain=makerpars-oaslananka-mobil.iam.gserviceaccount.com)
```

Expected: the final `test` exits `0`, proving no wrapper temporary directory remains.

- [ ] **Step 4: Run the repository secret scan**

Run:

```bash
scripts/ci/run_secret_scan.sh --mode dir --report-dir /tmp/android-multi-app-doppler-wrapper-scan
```

Expected:

```text
Secret scan passed: mode=dir report=/tmp/android-multi-app-doppler-wrapper-scan/gitleaks-dir.sarif
```

- [ ] **Step 5: Review the exact diff**

Run:

```bash
git diff -- scripts/doppler-run.sh scripts/ci/test_doppler_run.sh docs/SECRETS_SETUP.md
git status --short
```

Expected: no generated keystore, service-account JSON, `.env`, ZIP, or other secret material appears. Only the wrapper, test, and documentation are modified by this implementation.

- [ ] **Step 6: Commit documentation and verified workflow**

Run:

```bash
git add docs/SECRETS_SETUP.md
git commit -m "docs: standardize Doppler-backed build commands"
```

Expected: one documentation commit after all local and real-Doppler verification passes.

## Plan Self-Review

- Spec coverage: public interface, private permissions, strict decode, service-account validation, stale-path override, fallback path preservation, exit-code propagation, signal cleanup, integration tests, real Doppler smoke test, documentation, and secret scan are all assigned to explicit steps.
- Placeholder scan: no unresolved marker, deferred implementation instruction, or unspecified error-handling step remains.
- Interface consistency: every task uses `scripts/doppler-run.sh -- <command> [arguments...]`; generated variable names remain exactly `KEYSTORE_FILE` and `PLAY_SERVICE_ACCOUNT_JSON`.
