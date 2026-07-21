#!/bin/bash
# Pre-commit hook to check for secrets and style

echo "Starting pre-commit checks..."

# Check for secrets
FORBIDDEN="KEY_PASSWORD|KEY_ALIAS|KEYSTORE_PASSWORD"
if git diff --cached --name-only | xargs grep -E "$FORBIDDEN"; then
    echo "❌ ERROR: Potential secrets found in staged files."
    exit 1
fi

echo "✓ Pre-commit checks passed."
