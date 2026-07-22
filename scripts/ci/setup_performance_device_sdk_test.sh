#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script="$repo_root/scripts/ci/setup-performance-device-sdk.sh"

grep -Fq "system-images;android-33;default;x86_64" "$script"
grep -Fq "cmdline-tools/\$ANDROID_CMDLINE_TOOLS_VERSION/bin/sdkmanager" "$script"
grep -Fq -- "--licenses" "$script"
grep -Fq "emulator" "$script"
bash -n "$script"
echo 'Performance managed-device SDK setup contract passed.'
