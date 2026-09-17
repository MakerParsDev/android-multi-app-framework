#!/usr/bin/env bash
set -euo pipefail

if [ -n "${JULES_API_KEY:-}" ]; then
  echo "JULES_API_KEY is already set in environment."
  echo "::add-mask::${JULES_API_KEY}"
  if [ -n "${GITHUB_ENV:-}" ]; then
    echo "JULES_API_KEY=${JULES_API_KEY}" >> "$GITHUB_ENV"
  fi
  exit 0
fi

if [ -z "${DOPPLER_TOKEN:-}" ]; then
  echo "Error: DOPPLER_TOKEN is not set. Cannot fetch JULES_API_KEY from Doppler." >&2
  exit 1
fi

if ! command -v doppler &> /dev/null; then
  echo "Doppler CLI not found. Installing..."
  bash scripts/ci/install_doppler_cli.sh
fi

echo "Fetching JULES_API_KEY from Doppler (project: android-multi-app-framework, config: prod)..."
KEY_VAL=$(doppler secrets get JULES_API_KEY --project android-multi-app-framework --config prod --plain 2>/dev/null || true)

if [ -z "${KEY_VAL}" ]; then
  echo "Error: Failed to retrieve JULES_API_KEY from Doppler." >&2
  exit 1
fi

echo "::add-mask::${KEY_VAL}"
if [ -n "${GITHUB_ENV:-}" ]; then
  echo "JULES_API_KEY=${KEY_VAL}" >> "$GITHUB_ENV"
fi
export JULES_API_KEY="${KEY_VAL}"
echo "Successfully resolved JULES_API_KEY and masked it for workflow runner."
