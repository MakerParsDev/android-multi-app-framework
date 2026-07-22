#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$repo_root/scripts/ci/android-toolchain.sh"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$sdk_root" ]] || { echo 'ANDROID_SDK_ROOT or ANDROID_HOME is required' >&2; exit 1; }
manager="$sdk_root/cmdline-tools/$ANDROID_CMDLINE_TOOLS_VERSION/bin/sdkmanager"
[[ -x "$manager" ]] || { echo "Pinned sdkmanager is missing: $manager" >&2; exit 1; }
set +o pipefail
yes | "$manager" --sdk_root="$sdk_root" --licenses >/dev/null
license_rc=$?
set -o pipefail
[[ $license_rc -eq 0 ]] || exit "$license_rc"
"$manager" --sdk_root="$sdk_root" \
  emulator \
  'system-images;android-30;aosp_atd;x86_64'
echo 'Managed-device SDK packages installed.'
