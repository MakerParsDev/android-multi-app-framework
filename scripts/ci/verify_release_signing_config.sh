#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

is_unresolved() {
  local value="${1:-}"
  [[ "$value" == '$('* || "$value" == '${'* ]]
}

require_var() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "$value" ]]; then
    fail "Missing $name"
  fi
  if is_unresolved "$value"; then
    fail "$name is unresolved"
  fi
}

require_var KEYSTORE_FILE
require_var KEYSTORE_PASSWORD
require_var KEY_ALIAS
require_var KEY_PASSWORD

[[ -f "$KEYSTORE_FILE" ]] || fail "KEYSTORE_FILE does not exist"
[[ -s "$KEYSTORE_FILE" ]] || fail "KEYSTORE_FILE is empty"
command -v keytool >/dev/null 2>&1 || fail "keytool is not available"

keytool -list \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$KEYSTORE_PASSWORD" \
  -alias "$KEY_ALIAS" \
  >/dev/null

echo "OK: Release keystore is readable and alias exists."
