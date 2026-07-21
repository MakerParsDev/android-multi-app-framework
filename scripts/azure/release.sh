#!/usr/bin/env bash
set -euo pipefail

lower() { echo "${1:-false}" | tr '[:upper:]' '[:lower:]'; }

build_type="${BUILD_TYPE:-Debug}"
do_quality="$(lower "${DO_QUALITY:-false}")"
do_build="$(lower "${DO_BUILD:-true}")"
do_internal_test="$(lower "${DO_INTERNAL_TEST:-false}")"
do_publish="$(lower "${DO_PUBLISH:-false}")"
update_play_listing="$(lower "${UPDATE_PLAY_LISTING:-false}")"
flavors_csv="${RESOLVED_FLAVORS_CSV:-}"

if [[ -z "$flavors_csv" ]]; then
  echo "RESOLVED_FLAVORS_CSV is required. Run scripts/azure/resolve-flavors.sh first." >&2
  exit 1
fi

if [[ "$do_build" == "false" && ( "$do_internal_test" == "true" || "$do_publish" == "true" ) ]]; then
  echo "do_build=false while publish/internal test is true. Build must run before publish." >&2
  exit 1
fi
if [[ ( "$do_internal_test" == "true" || "$do_publish" == "true" ) && "$build_type" != "Release" ]]; then
  echo "BUILD_TYPE must be Release when publishing." >&2
  exit 1
fi
if [[ "$do_internal_test" == "true" && "$do_publish" == "true" ]]; then
  echo "DO_INTERNAL_TEST and DO_PUBLISH cannot both be true in the same run." >&2
  exit 1
fi

if [[ ( "$do_internal_test" == "true" || "$do_publish" == "true" ) && "$do_quality" != "true" ]]; then
  echo "Publishing requires DO_QUALITY=true so side-project and Android quality gates cannot be bypassed." >&2
  exit 1
fi

if [[ "$do_quality" == "true" ]]; then
  # quality.sh runs scripts/ci/run_side_project_quality.sh before Android checks.
  VALIDATION_MODE=warn ./scripts/azure/quality.sh
fi

IFS=',' read -r -a flavors <<< "$flavors_csv"

if [[ "$build_type" == "Release" && ( "$do_build" == "true" || "$do_internal_test" == "true" || "$do_publish" == "true" ) ]]; then
  : "${PUSH_REGISTRATION_URL:?Missing PUSH_REGISTRATION_URL}"
  : "${PURCHASE_VERIFICATION_URL:?Missing PURCHASE_VERIFICATION_URL}"

  backend_smoke_args=(
    --purchase-url "$PURCHASE_VERIFICATION_URL"
    --push-url "$PUSH_REGISTRATION_URL"
  )
  if [[ -n "${EXPECTED_ADMIN_BACKEND_GIT_SHA:-}" ]]; then
    backend_smoke_args+=(--expected-git-sha "$EXPECTED_ADMIN_BACKEND_GIT_SHA")
  fi
  python3 ./scripts/ci/admin_backend_smoke.py "${backend_smoke_args[@]}"

  : "${KEYSTORE_BASE64:?Missing KEYSTORE_BASE64}"
  : "${KEYSTORE_PASSWORD:?Missing KEYSTORE_PASSWORD}"
  : "${KEY_ALIAS:?Missing KEY_ALIAS}"
  : "${KEY_PASSWORD:?Missing KEY_PASSWORD}"
  echo "$KEYSTORE_BASE64" | base64 -d > "${AGENT_TEMPDIRECTORY:-/tmp}/release.jks"
  export KEYSTORE_FILE="${AGENT_TEMPDIRECTORY:-/tmp}/release.jks"
fi

if [[ "$do_internal_test" == "true" || "$do_publish" == "true" ]]; then
  : "${PLAY_SERVICE_ACCOUNT_JSON_BASE64:?Missing PLAY_SERVICE_ACCOUNT_JSON_BASE64}"
  echo "$PLAY_SERVICE_ACCOUNT_JSON_BASE64" | tr -d '\r\n\t ' | base64 -d > "${AGENT_TEMPDIRECTORY:-/tmp}/service-account.json"
  export PLAY_SERVICE_ACCOUNT_JSON="${AGENT_TEMPDIRECTORY:-/tmp}/service-account.json"
  python3 ./scripts/ci/verify_play_service_account_project.py --expected-project-id makerpars-oaslananka-mobil
  python3 -m pip install --quiet google-api-python-client google-auth
  python3 ./scripts/ci/fetch_play_version_codes.py \
    --flavors "$flavors_csv" \
    --tracks all \
    --suggest-next \
    --apply-to-app-versions \
    --sync-version-names
fi

if [[ "$do_build" == "true" ]]; then
  tasks=()
  for flavor in "${flavors[@]}"; do
    [[ -z "$flavor" ]] && continue
    cap="$(tr '[:lower:]' '[:upper:]' <<< "${flavor:0:1}")${flavor:1}"
    if [[ "$build_type" == "Release" && ( "$do_internal_test" == "true" || "$do_publish" == "true" ) ]]; then
      tasks+=("bundle${cap}Release")
    else
      tasks+=("assemble${cap}${build_type}")
    fi
  done
  echo "Running Gradle tasks: ${tasks[*]}"
  ./gradlew "${tasks[@]}" --stacktrace --no-daemon --max-workers=2
fi

publish_tasks=()
if [[ "$do_internal_test" == "true" || "$do_publish" == "true" ]]; then
  for flavor in "${flavors[@]}"; do
    [[ -z "$flavor" ]] && continue
    cap="$(tr '[:lower:]' '[:upper:]' <<< "${flavor:0:1}")${flavor:1}"
    [[ "$update_play_listing" == "true" ]] && publish_tasks+=("publish${cap}ReleaseListing")
    publish_tasks+=("publish${cap}ReleaseBundle")
  done
  track="internal"
  [[ "$do_publish" == "true" ]] && track="production"
  ./gradlew "${publish_tasks[@]}" -PPLAY_TRACK="$track" --stacktrace --no-daemon --max-workers=2
fi

rm -f "${AGENT_TEMPDIRECTORY:-/tmp}/release.jks" "${AGENT_TEMPDIRECTORY:-/tmp}/service-account.json"
