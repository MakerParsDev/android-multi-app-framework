#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_FILE="$REPO_ROOT/config/workflow-security-tools.json"
BIN_ROOT="$REPO_ROOT/.tools/workflow-security"
PRINT_CONFIG="false"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --bin-dir)
      BIN_ROOT="${2:?missing path for --bin-dir}"
      shift 2
      ;;
    --print-config)
      PRINT_CONFIG="true"
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

read_tool_config() {
  local tool="$1"
  python3 - "$CONFIG_FILE" "$tool" <<'PY'
import json
import sys
from pathlib import Path

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
record = config[sys.argv[2]]
for key in ("version", "asset", "url", "sha256", "binary"):
    print(record[key])
PY
}

mapfile -t actionlint_config < <(read_tool_config actionlint)
mapfile -t zizmor_config < <(read_tool_config zizmor)

if [ "$PRINT_CONFIG" = "true" ]; then
  printf 'actionlint=%s\n' "${actionlint_config[0]}"
  printf 'zizmor=%s\n' "${zizmor_config[0]}"
  exit 0
fi

tmp_root="$(mktemp -d "${TMPDIR:-/tmp}/workflow-security-tools.XXXXXX")"
cleanup() {
  rm -rf -- "$tmp_root"
}
trap cleanup EXIT

verify_binary() {
  local tool="$1" binary="$2" version="$3" output
  [ -x "$binary" ] || return 1
  case "$tool" in
    actionlint)
      output="$("$binary" -version 2>&1 || true)"
      ;;
    zizmor)
      output="$("$binary" --version 2>&1 || true)"
      ;;
    *)
      return 1
      ;;
  esac
  [[ "$output" == *"$version"* ]]
}

install_tool() {
  local tool="$1"
  local -a config
  mapfile -t config < <(read_tool_config "$tool")

  local version="${config[0]}"
  local asset="${config[1]}"
  local url="${config[2]}"
  local expected_sha256="${config[3]}"
  local binary_name="${config[4]}"
  local install_dir="$BIN_ROOT/$tool/$version"
  local target="$install_dir/$binary_name"

  if verify_binary "$tool" "$target" "$version"; then
    printf '%s\n' "$target"
    return 0
  fi

  local work_dir="$tmp_root/$tool"
  local archive="$work_dir/$asset"
  mkdir -p "$work_dir" "$install_dir"

  printf 'Downloading pinned %s %s\n' "$tool" "$version" >&2
  curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \
    --connect-timeout 10 --max-time 300 \
    "$url" --output "$archive"
  printf '%s  %s\n' "$expected_sha256" "$archive" | sha256sum --check --status
  tar -xzf "$archive" -C "$work_dir"

  local extracted
  extracted="$(find "$work_dir" -type f -name "$binary_name" -print -quit)"
  if [ -z "$extracted" ] || [ ! -f "$extracted" ]; then
    echo "Pinned $tool archive did not contain $binary_name." >&2
    exit 1
  fi

  install -m 0755 "$extracted" "$target"
  if ! verify_binary "$tool" "$target" "$version"; then
    echo "Installed $tool binary did not report version $version." >&2
    exit 1
  fi

  printf '%s\n' "$target"
}

actionlint_bin="$(install_tool actionlint)"
zizmor_bin="$(install_tool zizmor)"
printf 'ACTIONLINT_BIN=%q\n' "$actionlint_bin"
printf 'ZIZMOR_BIN=%q\n' "$zizmor_bin"
