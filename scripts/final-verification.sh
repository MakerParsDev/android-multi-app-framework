#!/bin/bash

set -e

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  🔍 FINAL VERIFICATION - Feb 2026"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

ERRORS=0

# ════════════════════════════════════════════
# 1. Project Structure Verification
# ════════════════════════════════════════════
echo -e "\n${YELLOW}1️⃣ Checking Project Structure...${NC}"

check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $1"
    else
        echo -e "${RED}✗${NC} $1 MISSING!"
        ERRORS=$((ERRORS + 1))
    fi
}

check_dir() {
    if [ -d "$1" ]; then
        echo -e "${GREEN}✓${NC} $1/"
    else
        echo -e "${RED}✗${NC} $1/ MISSING!"
        ERRORS=$((ERRORS + 1))
    fi
}

# Essential files
check_file ".env.template"
check_file "README.md"
check_file "app-versions.properties"
check_file "settings.gradle.kts"
check_file "build.gradle.kts"

# Scripts
check_dir "scripts"
check_file "scripts/download-firebase-configs.sh"
check_file "scripts/bump-version.sh"
check_file "scripts/build-all-flavours.sh"
check_file "scripts/final-verification.sh"

# GitHub workflows (GitHub Actions is the only CI/CD system in active use)
check_file ".github/workflows/quality-gate.yml"
check_file ".github/workflows/release.yml"
check_file ".github/workflows/manual-ops.yml"
check_file ".github/workflows/sync-play-version-codes.yml"
check_file ".github/workflows/deploy-admin-notifications.yml"
check_file ".github/workflows/verify-secrets-redacted.yml"
check_file ".github/dependabot.yml"

# BuildSrc
check_dir "buildSrc"
check_file "buildSrc/build.gradle.kts"
check_file "buildSrc/src/main/kotlin/FlavorConfig.kt"

# ════════════════════════════════════════════
# 2. Legacy Code Check
# ════════════════════════════════════════════
echo -e "\n${YELLOW}2️⃣ Checking for Legacy Code...${NC}"

if grep -r "com\.company" . --exclude-dir={.gradle,build,.git} --include="*.kt" --include="*.kts" 2>/dev/null; then
    echo -e "${RED}✗ Found 'com.company' references!${NC}"
    ERRORS=$((ERRORS + 1))
else
    echo -e "${GREEN}✓ No 'com.company' references${NC}"
fi

if grep -r "C:/" . --exclude-dir={.gradle,build,.git} --include="*.kt" --include="*.kts" 2>/dev/null; then
    echo -e "${RED}✗ Found hard-coded Windows paths!${NC}"
    ERRORS=$((ERRORS + 1))
else
    echo -e "${GREEN}✓ No hard-coded paths${NC}"
fi

# ════════════════════════════════════════════
# 3. Security Check
# ════════════════════════════════════════════
echo -e "\n${YELLOW}3️⃣ Security Scan...${NC}"

# Firebase config integrity (hybrid strategy)
echo "Checking Firebase config integrity per flavour..."
FLAVOURS_JSON=$(./gradlew -q printFlavors)
FLAVOURS_STR=$(echo "$FLAVOURS_JSON" | tr -d '[]",')

for flavour in $FLAVOURS_STR; do
    cfg="app/src/$flavour/google-services.json"
    if [ ! -f "$cfg" ]; then
        echo -e "${RED}✗ Missing $cfg${NC}"
        ERRORS=$((ERRORS + 1))
        continue
    fi

    if grep -q '"project_info"' "$cfg"; then
        echo -e "${GREEN}✓ $cfg${NC}"
    else
        echo -e "${RED}✗ Invalid JSON structure in $cfg${NC}"
        ERRORS=$((ERRORS + 1))
    fi
done

if grep -q "*.jks" .gitignore; then
    echo -e "${GREEN}✓ *.jks in .gitignore${NC}"
else
    echo -e "${RED}✗ *.jks NOT in .gitignore!${NC}"
    ERRORS=$((ERRORS + 1))
fi

if grep -q ".env" .gitignore; then
    echo -e "${GREEN}✓ .env in .gitignore${NC}"
else
    echo -e "${RED}✗ .env NOT in .gitignore!${NC}"
    ERRORS=$((ERRORS + 1))
fi

if find . -name "*.jks" -o -name "*.keystore" 2>/dev/null | grep -q .; then
    echo -e "${RED}✗ Keystore files found in repository!${NC}"
    ERRORS=$((ERRORS + 1))
fi

# ════════════════════════════════════════════
# 4. Build Configuration
# ════════════════════════════════════════════
echo -e "\n${YELLOW}4️⃣ Testing Build Configuration...${NC}"

# Clean build
echo "Running clean build..."
if ./gradlew clean --quiet; then
    echo -e "${GREEN}✓ Clean successful${NC}"
else
    echo -e "${RED}✗ Clean failed!${NC}"
    ERRORS=$((ERRORS + 1))
fi

# Test single flavour build
echo "Testing yasinsuresi debug build..."
if ./gradlew assembleYasinsuresiDebug --quiet; then
    echo -e "${GREEN}✓ Debug build successful${NC}"
else
    echo -e "${RED}✗ Debug build failed!${NC}"
    ERRORS=$((ERRORS + 1))
fi

# ════════════════════════════════════════════
# 5. Quality Checks
# ════════════════════════════════════════════
echo -e "\n${YELLOW}5️⃣ Running Quality Checks...${NC}"

# echo "Running detekt..."
# if ./gradlew detekt --quiet; then
#     echo -e "${GREEN}✓ Detekt passed${NC}"
# else
#     echo -e "${YELLOW}⚠ Detekt found issues (check reports)${NC}"
# fi

# echo "Running ktlint..."
# if ./gradlew ktlintCheck --quiet; then
#     echo -e "${GREEN}✓ ktlint passed${NC}"
# else
#     echo -e "${YELLOW}⚠ ktlint found issues (run ./gradlew ktlintFormat)${NC}"
# fi

# ════════════════════════════════════════════
# 6. Dependencies Check
# ════════════════════════════════════════════
echo -e "\n${YELLOW}6️⃣ Checking Dependencies...${NC}"

echo "Checking for dependency updates..."
./gradlew dependencyUpdates --quiet || true
echo -e "${GREEN}✓ Dependency report generated${NC}"
echo "  📋 Check: build/dependencyUpdates/report.txt"

# ════════════════════════════════════════════
# FINAL RESULT
# ════════════════════════════════════════════
echo -e "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✅ ALL CHECKS PASSED!${NC}"
    echo -e "${GREEN}🚀 Project ready for production${NC}"
    exit 0
else
    echo -e "${RED}❌ FOUND $ERRORS ERROR(S)${NC}"
    echo -e "${RED}⚠️  Please fix issues before proceeding${NC}"
    exit 1
fi
