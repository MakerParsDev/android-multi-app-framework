#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
POLICY_FILE="${SECRET_SCAN_POLICY:-$REPO_ROOT/config/secret-scan-policy.json}"
INSTALL_ROOT="${GITLEAKS_INSTALL_ROOT:-$REPO_ROOT/.tools/gitleaks}"

readarray -t tool_config < <(
  python3 - "$POLICY_FILE" <<'PY'
import json
import sys
from pathlib import Path
policy = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
tool = policy["gitleaks"]
for key in ("version", "asset", "download_url", "sha256"):
    print(tool[key])
PY
)

version="${tool_config[0]}"
asset="${tool_config[1]}"
download_url="${tool_config[2]}"
expected_sha256="${tool_config[3]}"
install_dir="$INSTALL_ROOT/$version"
binary="$install_dir/gitleaks"

verify_installed_binary() {
  local candidate="$1"
  [ -x "$candidate" ] || return 1
  local output
  output="$($candidate version 2>/dev/null || true)"
  [[ "$output" == *"$version"* ]]
}

if verify_installed_binary "$binary"; then
  printf '%s\n' "$binary"
  exit 0
fi

mkdir -p "$INSTALL_ROOT"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/gitleaks-install.XXXXXX")"
trap 'rm -rf -- "$tmp_dir"' EXIT
archive="$tmp_dir/$asset"

printf 'Downloading pinned Gitleaks %s\n' "$version" >&2
curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \
  --connect-timeout 10 --max-time 300 \
  "$download_url" --output "$archive"
printf '%s  %s\n' "$expected_sha256" "$archive" | sha256sum --check --status

tar -xzf "$archive" -C "$tmp_dir"
extracted="$tmp_dir/gitleaks"
if [ ! -x "$extracted" ]; then
  extracted="$(find "$tmp_dir" -type f -name gitleaks -perm -u+x -print -quit)"
fi
if [ -z "${extracted:-}" ] || [ ! -f "$extracted" ]; then
  echo "Pinned Gitleaks archive did not contain an executable." >&2
  exit 1
fi

mkdir -p "$install_dir"
install -m 0755 "$extracted" "$binary"
if ! verify_installed_binary "$binary"; then
  echo "Installed Gitleaks binary did not report pinned version $version." >&2
  exit 1
fi
printf '%s\n' "$binary"
