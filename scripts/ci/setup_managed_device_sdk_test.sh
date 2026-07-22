#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
mkdir -p "$tmp/sdk/cmdline-tools/21.0/bin" "$tmp/bin"
log="$tmp/sdkmanager.log"
cat > "$tmp/sdk/cmdline-tools/21.0/bin/sdkmanager" <<SH
#!/usr/bin/env bash
printf '%s\n' "\$@" >> "$log"
SH
chmod +x "$tmp/sdk/cmdline-tools/21.0/bin/sdkmanager"
cat > "$tmp/bin/yes" <<'SH'
#!/usr/bin/env bash
printf 'y\n'
SH
chmod +x "$tmp/bin/yes"

PATH="$tmp/bin:$PATH" \
ANDROID_SDK_ROOT="$tmp/sdk" \
ANDROID_HOME="$tmp/sdk" \
bash "$repo_root/scripts/ci/setup_managed_device_sdk.sh"

grep -Fx -- '--sdk_root='"$tmp/sdk" "$log"
grep -Fx 'emulator' "$log"
grep -Fx 'system-images;android-30;aosp_atd;x86_64' "$log"
echo 'PASS setup_managed_device_sdk_test'
