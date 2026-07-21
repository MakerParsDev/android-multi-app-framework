#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f ".env.template" ]]; then
  echo "::error::Missing .env.template"
  exit 1
fi

required_template_keys=(
  ADMIN_ALLOWED_EMAILS
  ADMOB_CLIENT_ID
  ADMOB_CLIENT_SECRET
  ADMOB_PUBLISHER_ID
  ADMOB_REFRESH_TOKEN
  CF_API_TOKEN
  CF_R2_ACCOUNT_ID
  CF_R2_BUCKET
  CF_R2_FIREBASE_OBJECT
  FIREBASE_CONFIGS_ZIP_BASE64
  FIREBASE_WEB_CLIENT_ID
  KEY_ALIAS
  KEY_PASSWORD
  KEYSTORE_BASE64
  KEYSTORE_FILE
  KEYSTORE_PASSWORD
  PLAY_SERVICE_ACCOUNT_JSON
  PLAY_SERVICE_ACCOUNT_JSON_BASE64
  PURCHASE_VERIFICATION_URL
  PUSH_REGISTRATION_URL
  VITE_ADMIN_ALLOWED_EMAILS
  VITE_FIREBASE_API_KEY
  VITE_FIREBASE_APP_ID
  VITE_FIREBASE_AUTH_DOMAIN
  VITE_FIREBASE_FUNCTIONS_REGION
  VITE_FIREBASE_MESSAGING_SENDER_ID
  VITE_FIREBASE_PROJECT_ID
  VITE_FIREBASE_STORAGE_BUCKET
  VITE_FUNCTIONS_BASE_URL
)

for key in "${required_template_keys[@]}"; do
  if ! grep -Eq "^${key}=" .env.template; then
    echo "::error::.env.template missing key: ${key}"
    exit 1
  fi
done

echo "::notice::Env contract is valid for GitHub Actions runtime values."
