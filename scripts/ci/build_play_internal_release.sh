#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

: "${RELEASE_FLAVOR:?RELEASE_FLAVOR is required}"
: "${RELEASE_CAPITALIZED:?RELEASE_CAPITALIZED is required}"
: "${PLAY_SERVICE_ACCOUNT_JSON:?PLAY_SERVICE_ACCOUNT_JSON is required}"

ad_inspector_gradle_args=()
case "${ENABLE_AD_INSPECTOR_DEBUG:-false}" in
  false)
    ;;
  true)
    case "$RELEASE_FLAVOR" in
      namazsurelerivedualarsesli|ismiazamduasi)
        ad_inspector_gradle_args+=("-PenableAdInspectorDebug=true")
        ;;
      *)
        echo "ERROR: Ad Inspector debug is only allowed for the two #337 test flavors" >&2
        exit 1
        ;;
    esac
    ;;
  *)
    echo "ERROR: ENABLE_AD_INSPECTOR_DEBUG must be true or false" >&2
    exit 1
    ;;
esac

cleanup_firebase() {
  rm -f -- "app/src/${RELEASE_FLAVOR}/google-services.json"
}
trap cleanup_firebase EXIT

python3 scripts/ci/verify_play_service_account_project.py \
  --expected-project-id makerpars-oaslananka-mobil

python3 scripts/ci/fetch_play_version_codes.py \
  --flavors "$RELEASE_FLAVOR" \
  --tracks all \
  --suggest-next \
  --apply-to-app-versions \
  --sync-version-names \
  --verify-greater-than-play

bash scripts/ci/restore_firebase_configs.sh "$RELEASE_FLAVOR"

python3 scripts/ci/verify_google_signin_config.py \
  --flavors "$RELEASE_FLAVOR" \
  --require-web-client-id

python3 scripts/ci/performance_profile_policy.py validate-source --flavor "$RELEASE_FLAVOR"

./gradlew ":app:validate${RELEASE_CAPITALIZED}ReleaseBaselineProfileInBundle" \
  "${ad_inspector_gradle_args[@]}" \
  --no-daemon \
  --no-configuration-cache \
  --stacktrace \
  --max-workers=2
