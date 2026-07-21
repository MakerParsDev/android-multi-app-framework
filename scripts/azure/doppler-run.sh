#!/usr/bin/env bash
set -euo pipefail

project="${DOPPLER_PROJECT:-android-multi-app-framework}"
config="${DOPPLER_CONFIG:-prod}"

if [[ -z "${DOPPLER_TOKEN:-}" ]]; then
  echo "Missing DOPPLER_TOKEN. Add it as a secret variable in the Azure DevOps variable group." >&2
  exit 1
fi

exec doppler run --project "$project" --config "$config" -- "$@"
