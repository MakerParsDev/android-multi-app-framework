#!/usr/bin/env bash
# Shared Android/JVM toolchain manifest reader. Source this file; do not execute it.

ANDROID_TOOLCHAIN_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_TOOLCHAIN_PROPERTIES_FILE="${ANDROID_TOOLCHAIN_PROPERTIES_FILE:-$ANDROID_TOOLCHAIN_REPO_ROOT/gradle.properties}"

android_toolchain_read_property() {
  local key="$1"
  [[ -f "$ANDROID_TOOLCHAIN_PROPERTIES_FILE" ]] || {
    echo "Toolchain manifest not found: $ANDROID_TOOLCHAIN_PROPERTIES_FILE" >&2
    return 1
  }
  awk -F= -v key="$key" '
    {
      parsed_key = $1
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", parsed_key)
    }
    parsed_key == key {
      value = substr($0, index($0, "=") + 1)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      print value
      exit
    }
  ' "$ANDROID_TOOLCHAIN_PROPERTIES_FILE"
}

android_toolchain_require_property() {
  local key="$1" value
  value="$(android_toolchain_read_property "$key")"
  [[ -n "$value" ]] || {
    echo "Missing required toolchain property: $key" >&2
    return 1
  }
  printf '%s\n' "$value"
}

ANDROID_JAVA_MAJOR="$(android_toolchain_require_property toolchain.java.major)"
ANDROID_COMPILE_SDK="$(android_toolchain_require_property toolchain.android.compileSdk)"
ANDROID_PLATFORM_PACKAGE="$(android_toolchain_require_property toolchain.android.platformPackage)"
ANDROID_TARGET_SDK="$(android_toolchain_require_property toolchain.android.targetSdk)"
ANDROID_MIN_SDK="$(android_toolchain_require_property toolchain.android.minSdk)"
ANDROID_BUILD_TOOLS_VERSION_MANIFEST="$(android_toolchain_require_property toolchain.android.buildTools)"
ANDROID_CMDLINE_TOOLS_VERSION_MANIFEST="$(android_toolchain_require_property toolchain.android.cmdlineTools)"
ANDROID_BOOTSTRAP_CMDLINE_TOOLS_REVISION="$(android_toolchain_require_property toolchain.android.bootstrapCmdlineToolsRevision)"

android_toolchain_validate_manifest() {
  [[ "$ANDROID_JAVA_MAJOR" =~ ^[0-9]+$ ]] || { echo "Invalid Java major: $ANDROID_JAVA_MAJOR" >&2; return 1; }
  [[ "$ANDROID_COMPILE_SDK" =~ ^[0-9]+$ ]] || { echo "Invalid compileSdk: $ANDROID_COMPILE_SDK" >&2; return 1; }
  [[ "$ANDROID_PLATFORM_PACKAGE" =~ ^[0-9]+([.][0-9]+)?$ ]] || { echo "Invalid platform package: $ANDROID_PLATFORM_PACKAGE" >&2; return 1; }
  [[ "$ANDROID_TARGET_SDK" =~ ^[0-9]+$ ]] || { echo "Invalid targetSdk: $ANDROID_TARGET_SDK" >&2; return 1; }
  [[ "$ANDROID_MIN_SDK" =~ ^[0-9]+$ ]] || { echo "Invalid minSdk: $ANDROID_MIN_SDK" >&2; return 1; }
  [[ "$ANDROID_BUILD_TOOLS_VERSION_MANIFEST" =~ ^[0-9]+([.][0-9]+){2}$ ]] || {
    echo "Invalid build-tools version: $ANDROID_BUILD_TOOLS_VERSION_MANIFEST" >&2
    return 1
  }
  [[ "$ANDROID_CMDLINE_TOOLS_VERSION_MANIFEST" =~ ^[0-9]+([.][0-9]+)*$ ]] || {
    echo "Invalid cmdline-tools version: $ANDROID_CMDLINE_TOOLS_VERSION_MANIFEST" >&2
    return 1
  }
  [[ "$ANDROID_BOOTSTRAP_CMDLINE_TOOLS_REVISION" =~ ^[0-9]+$ ]] || {
    echo "Invalid bootstrap cmdline-tools revision: $ANDROID_BOOTSTRAP_CMDLINE_TOOLS_REVISION" >&2
    return 1
  }

  local platform_major="${ANDROID_PLATFORM_PACKAGE%%.*}"
  local build_tools_major="${ANDROID_BUILD_TOOLS_VERSION_MANIFEST%%.*}"
  [[ "$platform_major" == "$ANDROID_COMPILE_SDK" ]] || {
    echo "platformPackage major ($platform_major) must match compileSdk ($ANDROID_COMPILE_SDK)" >&2
    return 1
  }
  [[ "$build_tools_major" == "$ANDROID_COMPILE_SDK" ]] || {
    echo "buildTools major ($build_tools_major) must match compileSdk ($ANDROID_COMPILE_SDK)" >&2
    return 1
  }
  (( ANDROID_MIN_SDK <= ANDROID_TARGET_SDK && ANDROID_TARGET_SDK <= ANDROID_COMPILE_SDK )) || {
    echo "Expected minSdk <= targetSdk <= compileSdk" >&2
    return 1
  }
}

android_toolchain_validate_override() {
  local name="$1" expected="$2" actual=""
  if [[ -v "$name" ]]; then
    actual="${!name}"
  fi
  if [[ -n "$actual" && "$actual" != "$expected" ]]; then
    echo "$name=$actual conflicts with $ANDROID_TOOLCHAIN_PROPERTIES_FILE ($expected)" >&2
    return 1
  fi
}

android_toolchain_initialize() {
  android_toolchain_validate_manifest || return 1
  android_toolchain_validate_override ANDROID_API_LEVEL "$ANDROID_PLATFORM_PACKAGE" || return 1
  android_toolchain_validate_override \
    ANDROID_BUILD_TOOLS_VERSION \
    "$ANDROID_BUILD_TOOLS_VERSION_MANIFEST" || return 1
  android_toolchain_validate_override \
    ANDROID_CMDLINE_TOOLS_VERSION \
    "$ANDROID_CMDLINE_TOOLS_VERSION_MANIFEST" || return 1

  export ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-$ANDROID_PLATFORM_PACKAGE}"
  export ANDROID_BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-$ANDROID_BUILD_TOOLS_VERSION_MANIFEST}"
  export ANDROID_CMDLINE_TOOLS_VERSION="${ANDROID_CMDLINE_TOOLS_VERSION:-$ANDROID_CMDLINE_TOOLS_VERSION_MANIFEST}"
}

android_toolchain_sdk_dir_from_local_properties() {
  local file="${1:-$ANDROID_TOOLCHAIN_REPO_ROOT/local.properties}"
  [[ -f "$file" ]] || return 1
  awk -F= '/^[[:space:]]*sdk[.]dir[[:space:]]*=/ {
    value = substr($0, index($0, "=") + 1)
    gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
    print value
    exit
  }' "$file"
}

android_toolchain_resolve_sdk_root() {
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
    return 0
  fi
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    printf '%s\n' "$ANDROID_HOME"
    return 0
  fi
  local local_sdk
  local_sdk="$(android_toolchain_sdk_dir_from_local_properties || true)"
  if [[ -n "$local_sdk" ]]; then
    printf '%s\n' "$local_sdk"
    return 0
  fi
  printf '%s\n' "$HOME/android-sdk"
}

android_toolchain_print_manifest() {
  cat <<EOF
Toolchain manifest: $ANDROID_TOOLCHAIN_PROPERTIES_FILE
  Java major: $ANDROID_JAVA_MAJOR
  compileSdk: $ANDROID_COMPILE_SDK
  SDK platform package: platforms;android-$ANDROID_PLATFORM_PACKAGE
  targetSdk: $ANDROID_TARGET_SDK
  minSdk: $ANDROID_MIN_SDK
  build-tools: $ANDROID_BUILD_TOOLS_VERSION
  cmdline-tools: $ANDROID_CMDLINE_TOOLS_VERSION
EOF
}

android_toolchain_initialize
