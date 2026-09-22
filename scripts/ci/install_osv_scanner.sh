#!/usr/bin/env bash
set -euo pipefail

OSV_SCANNER_VERSION="2.6.0"
OSV_SCANNER_SHA256="ca69b3d3cd08f889a49dc0a383122f71cc528b83803671df5fd874d97485b108"
OSV_SCANNER_URL="https://github.com/google/osv-scanner/releases/download/v${OSV_SCANNER_VERSION}/osv-scanner_linux_amd64"

bin_dir="${RUNNER_TEMP:-/tmp}/osv-scanner-bin"
bin_path="$bin_dir/osv-scanner"
tmp_path="$bin_path.download"

mkdir -p "$bin_dir"
rm -f "$tmp_path"

curl --fail --location --silent --show-error --retry 3 \
  --proto '=https' --tlsv1.2 \
  "$OSV_SCANNER_URL" \
  --output "$tmp_path"

printf '%s  %s\n' "$OSV_SCANNER_SHA256" "$tmp_path" | sha256sum --check --status
chmod 0755 "$tmp_path"
mv "$tmp_path" "$bin_path"

if [[ -n "${GITHUB_PATH:-}" ]]; then
  printf '%s\n' "$bin_dir" >> "$GITHUB_PATH"
fi
if [[ -n "${GITHUB_ENV:-}" ]]; then
  printf 'OSV_SCANNER_BIN=%s\n' "$bin_path" >> "$GITHUB_ENV"
fi

"$bin_path" --version
printf 'Installed pinned OSV-Scanner v%s at %s\n' "$OSV_SCANNER_VERSION" "$bin_path"
