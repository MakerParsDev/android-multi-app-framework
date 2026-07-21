#!/bin/bash

# ══════════════════════════════════════════════════════════════
# Firebase Config Downloader
# Downloads google-services.json for all product flavours
# ══════════════════════════════════════════════════════════════

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
APP_SRC_DIR="$PROJECT_ROOT/app/src"
FIREBASE_APPS_JSON="$PROJECT_ROOT/config/firebase-apps.json"

# Color output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Firebase Config Downloader"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Check if firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    echo -e "${RED}❌ Firebase CLI not found${NC}"
    echo "Install: npm install -g firebase-tools"
    exit 1
fi

# Check if logged in
if ! firebase projects:list &> /dev/null; then
    echo -e "${YELLOW}⚠️  Not logged in to Firebase${NC}"
    echo "Run: firebase login"
    exit 1
fi

# Flavours array (Dynamic)
echo "Fetching flavors from Gradle..."
FLAVOURS_JSON=$(./gradlew -q printFlavors)
mapfile -t FLAVOURS < <(echo "$FLAVOURS_JSON" | tr -d '[]"' | tr ',' '\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | sed '/^$/d')

download_config() {
    local flavour=$1
    local output_dir="$APP_SRC_DIR/$flavour"
    
    echo -e "\n${YELLOW}▸ Processing: $flavour${NC}"
    
    # Create directory if not exists
    mkdir -p "$output_dir"
    
    local app_id
    app_id=$(python3 - <<PY
import json
from pathlib import Path
cfg = json.loads(Path(r"$FIREBASE_APPS_JSON").read_text(encoding="utf-8"))
entry = cfg.get("$flavour", {})
print(entry.get("appId", ""))
PY
)

    local project_id
    project_id=$(python3 - <<PY
import json
from pathlib import Path
cfg = json.loads(Path(r"$FIREBASE_APPS_JSON").read_text(encoding="utf-8"))
entry = cfg.get("$flavour", {})
print(entry.get("projectId", ""))
PY
)

    if [[ -z "$app_id" || -z "$project_id" ]]; then
        echo -e "${RED}✗ Missing appId/projectId in config/firebase-apps.json for $flavour${NC}"
        return
    fi

    # Download google-services.json using Firebase app id
    if firebase apps:sdkconfig ANDROID "$app_id" \
        --project="$project_id" \
        --out="$output_dir/google-services.json" 2>/dev/null; then
        echo -e "${GREEN}✓ Downloaded: $output_dir/google-services.json${NC}"
    else
        echo -e "${RED}✗ Failed: Check Firebase project and app id${NC}"
    fi
}

# Download for all flavours
for flavour in "${FLAVOURS[@]}"; do
    download_config "$flavour"
done

echo -e "\n${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✓ Config download complete${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
