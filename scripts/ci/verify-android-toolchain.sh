#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=/dev/null
source "$REPO_ROOT/scripts/ci/android-toolchain.sh"

SDK_ROOT="$(android_toolchain_resolve_sdk_root)"
JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 || true)"
JAVA_MAJOR_ACTUAL="$(printf '%s\n' "$JAVA_VERSION_LINE" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p')"

[[ -n "$JAVA_MAJOR_ACTUAL" ]] || { echo "Unable to detect Java version: $JAVA_VERSION_LINE" >&2; exit 1; }
(( JAVA_MAJOR_ACTUAL >= ANDROID_JAVA_MAJOR )) || {
  echo "Java $ANDROID_JAVA_MAJOR+ required; found $JAVA_VERSION_LINE" >&2
  exit 1
}

PLATFORM_DIR="$SDK_ROOT/platforms/android-$ANDROID_PLATFORM_PACKAGE"
BUILD_TOOLS_DIR="$SDK_ROOT/build-tools/$ANDROID_BUILD_TOOLS_VERSION"
CMDLINE_TOOLS_DIR="$SDK_ROOT/cmdline-tools/$ANDROID_CMDLINE_TOOLS_VERSION"
SDKMANAGER="$CMDLINE_TOOLS_DIR/bin/sdkmanager"

[[ -f "$PLATFORM_DIR/android.jar" ]] || {
  echo "Missing Android platform: $PLATFORM_DIR/android.jar" >&2
  exit 1
}
[[ -d "$BUILD_TOOLS_DIR" ]] || {
  echo "Missing Android build-tools: $BUILD_TOOLS_DIR" >&2
  exit 1
}
[[ -x "$SDK_ROOT/platform-tools/adb" ]] || {
  echo "Missing Android platform-tools: $SDK_ROOT/platform-tools/adb" >&2
  exit 1
}
[[ -x "$SDKMANAGER" ]] || {
  echo "Missing pinned cmdline-tools: $SDKMANAGER" >&2
  exit 1
}

android_toolchain_print_manifest
cat <<EOF
Installed toolchain:
  ANDROID_SDK_ROOT: $SDK_ROOT
  Java: $JAVA_VERSION_LINE
  sdkmanager: $SDKMANAGER
  sdkmanager version: $($SDKMANAGER --version 2>&1 | sed -n '1p')
  platform android.jar: $PLATFORM_DIR/android.jar
  build-tools directory: $BUILD_TOOLS_DIR
  adb: $SDK_ROOT/platform-tools/adb
EOF
