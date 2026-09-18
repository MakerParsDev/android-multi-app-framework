#!/usr/bin/env bash
set -euo pipefail

# resolve_jules_api_key.sh
# Safely resolves JULES_API_KEY from Doppler, masks it for GitHub Actions,
# exports JULES_API_KEY into GITHUB_ENV or current shell, and unsets DOPPLER_TOKEN.

if [ -n "${JULES_API_KEY:-}" ]; then
  echo "JULES_API_KEY is already set in environment."
  echo "::add-mask::${JULES_API_KEY}"
  if [ -n "${GITHUB_ENV:-}" ]; then
    echo "JULES_API_KEY=${JULES_API_KEY}" >> "$GITHUB_ENV"
  fi
  exit 0
fi

if [ -z "${DOPPLER_TOKEN:-}" ]; then
  echo "[DIAGNOSTIC ERROR] DOPPLER_TOKEN secret is not set in environment." >&2
  echo "[OPERATOR ACTION REQUIRED] Please configure the DOPPLER_TOKEN secret in GitHub Repository Secrets." >&2
  exit 1
fi

# Ensure Doppler token itself is masked in logs
echo "::add-mask::${DOPPLER_TOKEN}"

if ! command -v doppler &> /dev/null; then
  echo "Doppler CLI not found. Installing Doppler CLI..."
  bash scripts/ci/install_doppler_cli.sh
fi

echo "Attempting Doppler authentication and secret resolution..."

ME_ERR_FILE=$(mktemp)
GET_ERR_FILE=$(mktemp)
trap 'rm -f "$ME_ERR_FILE" "$GET_ERR_FILE"' EXIT

if ! doppler me --plain > /dev/null 2> "$ME_ERR_FILE"; then
  ME_ERR=$(cat "$ME_ERR_FILE" | sed "s/${DOPPLER_TOKEN}/[REDACTED_TOKEN]/g")
  echo "[DIAGNOSTIC ERROR] Doppler authentication failed." >&2
  echo "Raw Doppler response: ${ME_ERR}" >&2
  if echo "${ME_ERR}" | grep -iq -e "invalid" -e "unauthorized" -e "401" -e "403" -e "token"; then
    echo "[OPERATOR ACTION REQUIRED] DOPPLER_TOKEN is invalid, expired, or revoked. Please update the DOPPLER_TOKEN secret in GitHub repository settings with a valid Service Token for project 'android-multi-app-framework', config 'prod'." >&2
  fi
  exit 1
fi

KEY_VAL=""

# First try explicit project and config flags
echo "Fetching JULES_API_KEY from Doppler (project: android-multi-app-framework, config: prod)..."
KEY_VAL=$(doppler secrets get JULES_API_KEY --project android-multi-app-framework --config prod --plain 2> "$GET_ERR_FILE" || true)

# Fall back to implicit service-token scoping if explicit project/config flags failed
if [ -z "${KEY_VAL}" ]; then
  echo "Explicit project/config fetch failed. Attempting implicit service-token scoped fetch..."
  KEY_VAL=$(doppler secrets get JULES_API_KEY --plain 2>> "$GET_ERR_FILE" || true)
fi

if [ -z "${KEY_VAL}" ]; then
  RAW_ERR=$(cat "$GET_ERR_FILE" | sed "s/${DOPPLER_TOKEN}/[REDACTED_TOKEN]/g")
  echo "[DIAGNOSTIC ERROR] Failed to retrieve JULES_API_KEY from Doppler." >&2
  echo "Doppler output: ${RAW_ERR}" >&2

  if echo "${RAW_ERR}" | grep -iq -e "not found" -e "JULES_API_KEY"; then
    echo "[OPERATOR ACTION REQUIRED] JULES_API_KEY was not found in Doppler project 'android-multi-app-framework' / config 'prod'. Please add the JULES_API_KEY secret in Doppler UI under project 'android-multi-app-framework' / config 'prod'." >&2
  elif echo "${RAW_ERR}" | grep -iq -e "project" -e "config" -e "access" -e "denied" -e "scope"; then
    echo "[OPERATOR ACTION REQUIRED] DOPPLER_TOKEN lacks access to project 'android-multi-app-framework' / config 'prod'. Ensure the Service Token in Doppler is granted read access to 'android-multi-app-framework / prod'." >&2
  else
    echo "[OPERATOR ACTION REQUIRED] Check Doppler service token permissions and project/config scope." >&2
  fi
  exit 1
fi

echo "::add-mask::${KEY_VAL}"

if [ -n "${GITHUB_ENV:-}" ]; then
  echo "JULES_API_KEY=${KEY_VAL}" >> "$GITHUB_ENV"
fi

export JULES_API_KEY="${KEY_VAL}"

# Unset DOPPLER_TOKEN to enforce secret isolation
unset DOPPLER_TOKEN

echo "Successfully resolved JULES_API_KEY and masked it for workflow runner. DOPPLER_TOKEN unset."
