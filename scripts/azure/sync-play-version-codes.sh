#!/usr/bin/env bash
set -euo pipefail

flavors_csv="${RESOLVED_FLAVORS_CSV:-all}"
branch="chore/sync-play-version-codes-${BUILD_BUILDID:-manual}"

: "${PLAY_SERVICE_ACCOUNT_JSON_BASE64:?Missing PLAY_SERVICE_ACCOUNT_JSON_BASE64}"
echo "$PLAY_SERVICE_ACCOUNT_JSON_BASE64" | tr -d '\r\n\t ' | base64 -d > "${AGENT_TEMPDIRECTORY:-/tmp}/service-account.json"
export PLAY_SERVICE_ACCOUNT_JSON="${AGENT_TEMPDIRECTORY:-/tmp}/service-account.json"

python3 ./scripts/ci/verify_play_service_account_project.py --expected-project-id makerpars-oaslananka-mobil
python3 -m pip install --quiet google-api-python-client google-auth
python3 ./scripts/ci/fetch_play_version_codes.py \
  --flavors "$flavors_csv" \
  --tracks all \
  --apply-to-app-versions \
  --sync-version-names

if git diff --quiet -- app-versions.properties; then
  echo "app-versions.properties is already in sync with Play."
  exit 0
fi

git config user.name "azure-pipelines[bot]"
git config user.email "azure-pipelines@users.noreply.local"
git checkout -b "$branch"
git add app-versions.properties
git commit -m "Sync Play version codes"
git push origin "$branch"

if command -v az >/dev/null 2>&1 && [[ -n "${SYSTEM_ACCESSTOKEN:-}" ]]; then
  export AZURE_DEVOPS_EXT_PAT="$SYSTEM_ACCESSTOKEN"
  az devops configure --defaults organization="${SYSTEM_COLLECTIONURI}" project="${SYSTEM_TEAMPROJECT}" >/dev/null
  az repos pr create \
    --repository "${BUILD_REPOSITORY_NAME}" \
    --source-branch "$branch" \
    --target-branch main \
    --title "Sync Play version codes" \
    --description "Automated Play Console versionCode/versionName sync.\n\nTarget flavors: ${flavors_csv}\nBuild: ${BUILD_BUILDURI:-unknown}" \
    --output table
else
  echo "Branch pushed: $branch"
  echo "SYSTEM_ACCESSTOKEN or az CLI missing; create the PR manually in Azure DevOps."
fi

rm -f "${AGENT_TEMPDIRECTORY:-/tmp}/service-account.json"
