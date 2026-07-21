#!/bin/bash

# ══════════════════════════════════════════════════════════════
# GitHub Repository Initial Setup
# https://github.com/oaslananka/android-multi-app-framework
# ══════════════════════════════════════════════════════════════

set -e

REPO_URL="https://github.com/oaslananka/android-multi-app-framework"
REPO_NAME="android-multi-app-framework"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  🔧 GitHub Repository Setup"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 1. Repository Settings (Do this manually on GitHub) ──
cat << 'EOF'

📌 MANUAL STEPS ON GITHUB:

1️⃣ Repository Settings → General:
   ✓ Description: "Professional multi-flavour Android framework for managing 16+ apps from single codebase"
   ✓ Website: (your website)
   ✓ Topics: android, kotlin, multi-flavour, jetpack-compose, firebase, template
   ✓ Features:
     ☑ Issues
     ☑ Discussions
     ☐ Wikis (optional)
     ☑ Projects

2️⃣ Settings → Actions → General:
   ✓ Actions permissions: Allow all actions and reusable workflows
   ✓ Workflow permissions: Read and write permissions
   ✓ Allow GitHub Actions to create and approve pull requests: ✓

3️⃣ Settings → Secrets and Variables → Actions:
   Add secrets:
   
   🔐 Secrets:
   - KEYSTORE_BASE64 = (base64 encoded keystore.jks)
   - KEYSTORE_PASSWORD = your_password
   - KEY_ALIAS = your_alias
   - KEY_PASSWORD = your_key_password
   - PLAY_SERVICE_ACCOUNT_JSON = (service-account.json full content)

4️⃣ Settings → Branches:
   Branch protection for 'main':
   ✓ Require pull request reviews (1 approver)
   ✓ Require status checks:
     - quality-gate / lint
     - quality-gate / detekt
     - quality-gate / ktlint
   ✓ Require conversation resolution
   ✓ Include administrators

5️⃣ Settings → Code security and analysis:
   ✓ Dependency graph: Enable
   ✓ Dependabot alerts: Enable
   ✓ Dependabot security updates: Enable
   ✓ Code scanning (CodeQL): Enable

EOF

echo ""
read -p "Press ENTER when you've completed the manual steps above..."

# ── 2. Create .env.template if missing ──
if [ ! -f ".env.template" ]; then
    cat > .env.template << 'EOF'
# ══════════════════════════════════════════════════════════════
# Environment Configuration Template
# Copy to .env and fill with your values
# NEVER commit .env to git!
# ══════════════════════════════════════════════════════════════

# ── Signing Configuration ──
KEYSTORE_FILE=/path/to/your/keystore.jks
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password

# ── Play Console Publishing ──
PLAY_SERVICE_ACCOUNT_JSON=/path/to/service-account.json

# ── AdMob (Optional override) ──
# ADMOB_APP_ID_DEBUG=ca-app-pub-3940256099942544~3347511713
# ADMOB_APP_ID_RELEASE=ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX

# ── Firebase (if using local config instead of download script) ──
# FIREBASE_PROJECT_ID=your-project-id
EOF
    echo "✓ Created .env.template"
fi

# ── 3. Create CONTRIBUTING.md ──
cat > CONTRIBUTING.md << 'EOF'
# Contributing Guide

## Development Workflow

### 1. Setup
```bash
# Clone
git clone https://github.com/oaslananka/android-multi-app-framework.git
cd android-multi-app-framework

# Configure
cp .env.template .env
# Edit .env with your values

# Download Firebase configs
./scripts/download-firebase-configs.sh
```

### 2. Before Committing
```bash
# Run quality checks
./gradlew qualityCheck

# Format code
./gradlew ktlintFormat

# Verify everything
./scripts/final-verification.sh
```

### 3. Creating PR
- Create feature branch: `git checkout -b feature/your-feature`
- Make changes
- Test locally
- Push and create PR
- Wait for CI checks
- Request review

## Code Standards

- **Kotlin:** Follow official style guide
- **Architecture:** Clean Architecture (core → feature)
- **DI:** Hilt only
- **Async:** Coroutines + Flow
- **UI:** Jetpack Compose

## Adding New Flavour

1. Edit `buildSrc/src/main/kotlin/FlavorConfig.kt`
2. Add to `AppFlavors.all` list
3. Download Firebase config: `./scripts/download-firebase-configs.sh`
4. Build: `./gradlew assembleYourFlavourDebug`
EOF

echo "✓ Created CONTRIBUTING.md"

# ── 4. Create LICENSE (if needed) ──
if [ ! -f "LICENSE" ]; then
    cat > LICENSE << 'EOF'
Proprietary License

Copyright (c) 2026 Parsfilo

All rights reserved. This code is proprietary and confidential.
Unauthorized copying, distribution, or use is strictly prohibited.
EOF
    echo "✓ Created LICENSE (Proprietary)"
fi

# ── 5. Create Pull Request Template ──
mkdir -p .github
cat > .github/pull_request_template.md << 'EOF'
## 📝 Description
<!-- Describe your changes -->

## 🎯 Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update
- [ ] Code refactoring

## ✅ Checklist
- [ ] Code follows project style guidelines
- [ ] Self-reviewed the code
- [ ] Commented complex logic
- [ ] Updated documentation
- [ ] No new warnings generated
- [ ] Added tests (if applicable)
- [ ] All tests pass locally
- [ ] `./gradlew qualityCheck` passes
- [ ] `./scripts/final-verification.sh` passes

## 📸 Screenshots (if UI changes)
<!-- Add screenshots -->

## 🔗 Related Issues
Closes #
EOF

echo "✓ Created PR template"

# ── 6. Create Issue Templates ──
mkdir -p .github/ISSUE_TEMPLATE

cat > .github/ISSUE_TEMPLATE/bug_report.md << 'EOF'
---
name: Bug Report
about: Report a bug
title: '[BUG] '
labels: bug
---

## 🐛 Bug Description
<!-- Clear description -->

## 📱 Affected Flavour(s)
- [ ] All flavours
- [ ] Specific: ___________

## 🔄 Steps to Reproduce
1. 
2. 
3. 

## ✅ Expected Behavior
<!-- What should happen -->

## ❌ Actual Behavior
<!-- What actually happens -->

## 📊 Environment
- Android version:
- Device:
- App version:

## 📎 Logs/Screenshots
<!-- Attach if available -->
EOF

cat > .github/ISSUE_TEMPLATE/feature_request.md << 'EOF'
---
name: Feature Request
about: Suggest a feature
title: '[FEATURE] '
labels: enhancement
---

## 💡 Feature Description
<!-- Clear description -->

## 🎯 Problem It Solves
<!-- What problem does this solve? -->

## 💻 Proposed Solution
<!-- How should it work? -->

## 🔄 Alternatives Considered
<!-- Other approaches -->

## 📱 Affected Modules
- [ ] core
- [ ] feature
- [ ] build system
- [ ] other: ___________
EOF

echo "✓ Created issue templates"

# ── 7. Initial Git Configuration ──
if [ ! -d ".git" ]; then
    echo "Initializing git..."
    git init
    git branch -M main
fi

# Configure .gitattributes for better diffs
cat > .gitattributes << 'EOF'
# Auto detect text files
* text=auto

# Source code
*.kt text diff=kotlin
*.kts text diff=kotlin
*.java text diff=java
*.xml text diff=xml
*.json text
*.gradle text
*.properties text

# Binary files
*.png binary
*.jpg binary
*.jpeg binary
*.gif binary
*.webp binary
*.jks binary
*.keystore binary
*.aab binary
*.apk binary

# Line endings
*.sh text eol=lf
gradlew text eol=lf
EOF

echo "✓ Created .gitattributes"

# ── 8. First Commit ──
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Ready to push to GitHub!"
echo ""
echo "Commands to run:"
echo ""
echo "  git add ."
echo "  git commit -m 'feat: initialize professional multi-flavour Android framework'"
echo "  git remote add origin $REPO_URL"
echo "  git push -u origin main"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
