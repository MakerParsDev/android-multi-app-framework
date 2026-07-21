#!/bin/bash

# ══════════════════════════════════════════════════════════════
# Build All Flavours
# Builds/publishes all product flavours using a single Gradle invocation
# Usage: ./scripts/build-all-flavours.sh [Debug|Release] [assemble|bundle|publish]
# ══════════════════════════════════════════════════════════════

set -e

BUILD_TYPE=${1:-Debug}
ACTION=${2:-assemble}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  All Flavours — ${ACTION} ${BUILD_TYPE}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

cd "$PROJECT_ROOT"

# Fetch flavors dynamically from Gradle
echo "Fetching flavors from Gradle..."
FLAVOURS_JSON=$(./gradlew -q printFlavors)
# Convert JSON array into one flavour per line safely
mapfile -t FLAVOURS < <(
  echo "$FLAVOURS_JSON" | tr -d '[]"' | tr ',' '\n' | sed '/^[[:space:]]*$/d'
)

# Build the full list of Gradle tasks
TASKS=()
for flavour in "${FLAVOURS[@]}"; do
    # Capitalize first letter
    FLAVOUR_CAP="$(tr '[:lower:]' '[:upper:]' <<< ${flavour:0:1})${flavour:1}"
    
    case "$ACTION" in
        publish)
            TASKS+=("publish${FLAVOUR_CAP}ReleaseBundle")
        ;;
        bundle)
            TASKS+=("bundle${FLAVOUR_CAP}${BUILD_TYPE}")
        ;;
        *)
            TASKS+=("assemble${FLAVOUR_CAP}${BUILD_TYPE}")
        ;;
    esac
done

echo "Running ${#TASKS[@]} tasks in parallel..."
echo ""

# Single Gradle invocation — Gradle runs tasks in parallel via --parallel
./gradlew "${TASKS[@]}" --stacktrace

echo -e "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✓ All ${ACTION} tasks completed successfully"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
