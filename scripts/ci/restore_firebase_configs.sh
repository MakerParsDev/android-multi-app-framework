#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

flavor="${1:?usage: restore_firebase_configs.sh <flavor>}"

if [[ -n "${FIREBASE_CONFIGS_ZIP_BASE64:-}" ]]; then
  python3 scripts/ci/materialize_firebase_configs.py \
    --flavors "$flavor" \
    --mode strict
  exit 0
fi

account_id="${CF_R2_ACCOUNT_ID:-${CLOUDFLARE_ACCOUNT_ID:-}}"
api_token="${CF_API_TOKEN:-${CLOUDFLARE_API_TOKEN:-}}"
bucket="${CF_R2_BUCKET:-}"
object_key="${CF_R2_FIREBASE_OBJECT:-}"

present=0
for value in "$account_id" "$api_token" "$bucket" "$object_key"; do
  [[ -n "$value" ]] && present=$((present + 1))
done
if (( present != 4 )); then
  printf '%s\n' \
    'ERROR: Firebase config source is incomplete; set FIREBASE_CONFIGS_ZIP_BASE64 or the complete R2 credential quartet.' >&2
  exit 1
fi

wrangler_dir="$repo_root/side-projects/cloudflare/workers/content-api"
wrangler_config="$wrangler_dir/wrangler.toml"
npm_bin="${NPM_BIN:-npm}"

[[ -f "$wrangler_dir/package-lock.json" ]] || {
  echo "ERROR: missing locked Wrangler package-lock.json" >&2
  exit 1
}
[[ -f "$wrangler_config" ]] || {
  echo "ERROR: missing Wrangler configuration: $wrangler_config" >&2
  exit 1
}

(
  cd "$wrangler_dir"
  "$npm_bin" ci --ignore-scripts --no-audit --no-fund
)

wrangler_bin="${WRANGLER_BIN:-$wrangler_dir/node_modules/.bin/wrangler}"
[[ -x "$wrangler_bin" ]] || {
  echo "ERROR: verified Wrangler binary is unavailable after npm ci" >&2
  exit 1
}

umask 077
tmp_dir="$(mktemp -d "${RUNNER_TEMP:-/tmp}/firebase-configs.XXXXXX")"
chmod 700 "$tmp_dir"
archive="$tmp_dir/firebase-configs.zip"
cleanup() {
  rm -rf -- "$tmp_dir"
}
trap cleanup EXIT

export CLOUDFLARE_ACCOUNT_ID="$account_id"
export CLOUDFLARE_API_TOKEN="$api_token"
"$wrangler_bin" r2 object get \
  "$bucket/$object_key" \
  --file "$archive" \
  --remote \
  --config "$wrangler_config"

[[ -s "$archive" ]] || {
  echo "ERROR: R2 Firebase config archive is missing or empty" >&2
  exit 1
}
chmod 600 "$archive"

python3 scripts/ci/materialize_firebase_configs.py \
  --flavors "$flavor" \
  --zip-file "$archive" \
  --mode strict
