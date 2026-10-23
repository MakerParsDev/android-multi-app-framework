#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"

if [[ ! -f ".env" ]]; then
  echo "ERROR: .env not found. Copy .env.template to .env."
  exit 1
fi

required=(
  PARSFILO_DOMAIN
  WWW_SUBDOMAIN
  ADMIN_SUBDOMAIN
  CADDY_ACME_EMAIL
  FIREBASE_FUNCTIONS_BASE_URL
  VITE_FIREBASE_API_KEY
  VITE_FIREBASE_AUTH_DOMAIN
  VITE_FIREBASE_PROJECT_ID
  VITE_FIREBASE_APP_ID
  VITE_FIREBASE_MESSAGING_SENDER_ID
  VITE_FIREBASE_STORAGE_BUCKET
  VITE_FIREBASE_FUNCTIONS_REGION
  VITE_FUNCTIONS_BASE_URL
  VITE_ADMIN_ALLOWED_EMAILS
)

for key in "${required[@]}"; do
  if ! grep -Eq "^${key}=[^[:space:]]+" .env; then
    echo "ERROR: Missing or empty ${key} in .env"
    exit 1
  fi
done

# Refuse example template destinations before Docker publishes public ports.
if grep -Eq '^PARSFILO_DOMAIN=(example\.(com|invalid)|localhost)$|^CADDY_ACME_EMAIL=admin@example\.com$|^FIREBASE_FUNCTIONS_BASE_URL=https://example\.invalid$' .env; then
  echo "ERROR: Replace placeholder domain, ACME email and API upstream in .env"
  exit 1
fi

# Validate the expanded configuration without starting or updating services.
docker compose config --quiet

echo "Preflight OK."

