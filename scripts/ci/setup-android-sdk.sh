#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=/dev/null
source "$REPO_ROOT/scripts/ci/android-toolchain.sh"

SDK_ROOT="${ANDROID_SDK_ROOT:-$(android_toolchain_resolve_sdk_root)}"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"

run_pkg_manager() {
  if command -v sudo >/dev/null 2>&1; then
    sudo "$@"
    return 0
  fi
  if [[ "$(id -u)" == "0" ]]; then
    "$@"
    return 0
  fi
  echo "Cannot run package manager command without sudo/root: $*" >&2
  exit 1
}

detect_java_major() {
  local version_line
  version_line="$(java -version 2>&1 | head -n 1 || true)"
  printf '%s\n' "$version_line" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p'
}

select_installed_java() {
  local candidate
  candidate="$(find /usr/lib/jvm -maxdepth 3 -type f -path "*/java-${ANDROID_JAVA_MAJOR}-openjdk-*/bin/java" -print -quit 2>/dev/null || true)"
  if [[ -n "$candidate" ]]; then
    local java_home
    java_home="$(cd "$(dirname "$candidate")/.." && pwd)"
    export JAVA_HOME="$java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
}

ensure_java() {
  local major
  major="$(detect_java_major)"
  if [[ -n "$major" && "$major" -ge "$ANDROID_JAVA_MAJOR" ]]; then
    return 0
  fi

  command -v apt-get >/dev/null 2>&1 || {
    echo "Java $ANDROID_JAVA_MAJOR+ is required and apt-get is unavailable." >&2
    exit 1
  }

  echo "Java $ANDROID_JAVA_MAJOR+ not detected (found: ${major:-none}). Installing OpenJDK $ANDROID_JAVA_MAJOR..."
  run_pkg_manager apt-get update -y
  run_pkg_manager apt-get install -y "openjdk-${ANDROID_JAVA_MAJOR}-jdk-headless"
  select_installed_java

  major="$(detect_java_major)"
  [[ -n "$major" && "$major" -ge "$ANDROID_JAVA_MAJOR" ]] || {
    echo "Java installation completed but Java $ANDROID_JAVA_MAJOR+ is still unavailable." >&2
    exit 1
  }
}

ensure_prerequisites() {
  local missing=()
  command -v curl >/dev/null 2>&1 || missing+=(curl)
  command -v unzip >/dev/null 2>&1 || missing+=(unzip)
  command -v find >/dev/null 2>&1 || missing+=(findutils)
  command -v git >/dev/null 2>&1 || missing+=(git)
  command -v python3 >/dev/null 2>&1 || missing+=(python3)
  command -v update-ca-certificates >/dev/null 2>&1 || missing+=(ca-certificates)
  if (( ${#missing[@]} == 0 )); then
    return 0
  fi

  command -v apt-get >/dev/null 2>&1 || {
    echo "Missing prerequisites and apt-get is unavailable: ${missing[*]}" >&2
    exit 1
  }
  run_pkg_manager apt-get update -y
  run_pkg_manager apt-get install -y "${missing[@]}"
}

pinned_sdkmanager() {
  printf '%s\n' "$SDK_ROOT/cmdline-tools/$ANDROID_CMDLINE_TOOLS_VERSION/bin/sdkmanager"
}

find_bootstrap_sdkmanager() {
  local candidates=(
    "$(pinned_sdkmanager)"
    "$SDK_ROOT/cmdline-tools/bootstrap/bin/sdkmanager"
    "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
    "$SDK_ROOT/cmdline-tools/bin/sdkmanager"
    "/usr/local/lib/android/sdk/cmdline-tools/latest/bin/sdkmanager"
    "/usr/local/lib/android/sdk/cmdline-tools/bin/sdkmanager"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

bootstrap_cmdline_tools_seed() {
  mkdir -p "$SDK_ROOT/cmdline-tools"
  local tmp url cleanup_command
  tmp="$(mktemp -d)"
  url="https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_BOOTSTRAP_CMDLINE_TOOLS_REVISION}_latest.zip"
  printf -v cleanup_command 'rm -rf -- %q' "$tmp"
  # Expand now so the EXIT trap does not reference a function-local variable after scope exit.
  # shellcheck disable=SC2064
  trap "$cleanup_command" EXIT

  echo "Downloading bootstrap Android cmdline-tools revision $ANDROID_BOOTSTRAP_CMDLINE_TOOLS_REVISION..."
  curl -fsSL "$url" -o "$tmp/cmdline-tools.zip"
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp/unzipped"
  rm -rf "$SDK_ROOT/cmdline-tools/bootstrap"
  mv "$tmp/unzipped/cmdline-tools" "$SDK_ROOT/cmdline-tools/bootstrap"
  rm -rf -- "$tmp"
  trap - EXIT
}

accept_licenses() {
  local manager="$1" status
  set +o pipefail
  yes | "$manager" --sdk_root="$SDK_ROOT" --licenses >/dev/null
  status=$?
  set -o pipefail
  if [[ $status -ne 0 ]]; then
    echo "Failed to accept Android SDK licenses (exit=$status)." >&2
    exit "$status"
  fi
}

ensure_pinned_cmdline_tools() {
  local manager="$1" pinned
  pinned="$(pinned_sdkmanager)"
  if [[ -x "$pinned" ]]; then
    return 0
  fi

  echo "Installing pinned cmdline-tools;$ANDROID_CMDLINE_TOOLS_VERSION..."
  accept_licenses "$manager"
  "$manager" --sdk_root="$SDK_ROOT" "cmdline-tools;$ANDROID_CMDLINE_TOOLS_VERSION" >/dev/null
  [[ -x "$pinned" ]] || {
    echo "Pinned sdkmanager was not installed at $pinned" >&2
    exit 1
  }
}

install_required_packages() {
  local manager="$1"
  accept_licenses "$manager"
  echo "Installing Android SDK packages from manifest:"
  echo "  platforms;android-$ANDROID_PLATFORM_PACKAGE"
  echo "  build-tools;$ANDROID_BUILD_TOOLS_VERSION"
  echo "  platform-tools"
  "$manager" --sdk_root="$SDK_ROOT" \
    "platform-tools" \
    "platforms;android-$ANDROID_PLATFORM_PACKAGE" \
    "build-tools;$ANDROID_BUILD_TOOLS_VERSION" >/dev/null
}

write_local_properties() {
  [[ -f "$REPO_ROOT/settings.gradle.kts" ]] || return 0
  local file="$REPO_ROOT/local.properties" tmp
  tmp="$(mktemp)"
  if [[ -f "$file" ]]; then
    grep -vE '^[[:space:]]*sdk[.]dir[[:space:]]*=' "$file" > "$tmp" || true
  fi
  printf 'sdk.dir=%s\n' "$SDK_ROOT" >> "$tmp"
  if ! cat "$tmp" > "$file"; then
    rm -f -- "$tmp"
    echo "Failed to update local.properties: $file" >&2
    return 1
  fi
  rm -f -- "$tmp"
  echo "Updated local.properties sdk.dir=$SDK_ROOT"
}

emit_ci_environment() {
  local sdkmanager_dir="$SDK_ROOT/cmdline-tools/$ANDROID_CMDLINE_TOOLS_VERSION/bin"
  export PATH="$sdkmanager_dir:$SDK_ROOT/platform-tools:$PATH"

  if [[ -n "${TF_BUILD:-}" ]]; then
    echo "##vso[task.setvariable variable=ANDROID_SDK_ROOT]$SDK_ROOT"
    echo "##vso[task.setvariable variable=ANDROID_HOME]$SDK_ROOT"
    echo "##vso[task.prependpath]$SDK_ROOT/platform-tools"
    echo "##vso[task.prependpath]$sdkmanager_dir"
    if [[ -n "${JAVA_HOME:-}" ]]; then
      echo "##vso[task.setvariable variable=JAVA_HOME]$JAVA_HOME"
      echo "##vso[task.prependpath]$JAVA_HOME/bin"
    fi
  fi
}

main() {
  ensure_prerequisites
  ensure_java
  mkdir -p "$SDK_ROOT"

  local manager
  manager="$(find_bootstrap_sdkmanager || true)"
  if [[ -z "$manager" ]]; then
    bootstrap_cmdline_tools_seed
    manager="$(find_bootstrap_sdkmanager)"
  fi

  ensure_pinned_cmdline_tools "$manager"
  manager="$(pinned_sdkmanager)"
  install_required_packages "$manager"
  write_local_properties
  emit_ci_environment

  echo "Android SDK bootstrap completed."
  ANDROID_SDK_ROOT="$SDK_ROOT" ANDROID_HOME="$SDK_ROOT" \
    bash "$REPO_ROOT/scripts/ci/verify-android-toolchain.sh"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
