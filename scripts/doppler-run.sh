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
