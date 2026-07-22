#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

: "${RELEASE_FLAVOR:?RELEASE_FLAVOR is required}"
: "${RELEASE_CAPITALIZED:?RELEASE_CAPITALIZED is required}"

cleanup_firebase() {
  rm -f -- "app/src/${RELEASE_FLAVOR}/google-services.json"
}
trap cleanup_firebase EXIT

bash scripts/ci/restore_firebase_configs.sh "$RELEASE_FLAVOR"

python3 scripts/ci/verify_google_signin_config.py \
  --flavors "$RELEASE_FLAVOR" \
  --require-web-client-id

./gradlew ":app:bundle${RELEASE_CAPITALIZED}Release" \
  --no-daemon \
  --stacktrace \
  --max-workers=2
