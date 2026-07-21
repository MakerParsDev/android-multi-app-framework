#!/bin/bash

# ══════════════════════════════════════════════════════════════
# Per-flavor version bumper (app-versions.properties)
# Usage:
#   ./scripts/bump-version.sh <flavor> [major|minor|patch|build]
# ══════════════════════════════════════════════════════════════

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
VERSIONS_FILE="$PROJECT_ROOT/app-versions.properties"

FLAVOR="${1:-}"
TYPE="${2:-build}"

if [[ -z "$FLAVOR" ]]; then
  echo "Usage: $0 <flavor> [major|minor|patch|build]"
  exit 1
fi

case "$TYPE" in
  major|minor|patch|build) ;;
  *)
    echo "Usage: $0 <flavor> [major|minor|patch|build]"
    exit 1
    ;;
esac

if [[ ! -f "$VERSIONS_FILE" ]]; then
  echo "Error: app-versions.properties not found!"
  exit 1
fi

code_key="${FLAVOR}.versionCode"
name_key="${FLAVOR}.versionName"

read_prop() {
  local key="$1"
  grep -E "^${key}=" "$VERSIONS_FILE" | head -n1 | cut -d'=' -f2- | tr -d '\r'
}

set_prop() {
  local key="$1"
  local value="$2"
  if grep -qE "^${key}=" "$VERSIONS_FILE"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$VERSIONS_FILE"
  else
    printf "\n%s=%s\n" "$key" "$value" >> "$VERSIONS_FILE"
  fi
}

current_code="$(read_prop "$code_key")"
current_name="$(read_prop "$name_key")"

if [[ -z "$current_code" ]]; then
  echo "Error: Missing $code_key in app-versions.properties"
  exit 1
fi
if ! [[ "$current_code" =~ ^[0-9]+$ ]]; then
  echo "Error: Invalid numeric value for $code_key: $current_code"
  exit 1
fi

if [[ -z "$current_name" ]]; then
  current_name="1.0.0"
fi

if [[ "$current_name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+) ]]; then
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  patch="${BASH_REMATCH[3]}"
else
  echo "Error: Invalid semantic version for $name_key: $current_name (expected x.y.z)"
  exit 1
fi

next_code=$((current_code + 1))
next_name="$current_name"

case "$TYPE" in
  major)
    major=$((major + 1))
    minor=0
    patch=0
    next_name="${major}.${minor}.${patch}"
    ;;
  minor)
    minor=$((minor + 1))
    patch=0
    next_name="${major}.${minor}.${patch}"
    ;;
  patch)
    patch=$((patch + 1))
    next_name="${major}.${minor}.${patch}"
    ;;
  build)
    ;;
esac

set_prop "$code_key" "$next_code"
set_prop "$name_key" "$next_name"

echo "✓ ${FLAVOR}: versionCode ${current_code} -> ${next_code}, versionName ${next_name}"
