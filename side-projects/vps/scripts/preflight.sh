#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"

if [[ ! -f ".env" ]]; then
  echo "ERROR: .env not found. Copy .env.example to .env."
  exit 1
fi

required=(
  PARSFILO_DOMAIN
  ADMIN_SUBDOMAIN
  CADDY_ACME_EMAIL
  VITE_FIREBASE_API_KEY
  VITE_FIREBASE_AUTH_DOMAIN
  VITE_FIREBASE_PROJECT_ID
  VITE_FIREBASE_APP_ID
)

for key in "${required[@]}"; do
  if ! grep -q "^${key}=" .env; then
    echo "ERROR: Missing ${key} in .env"
    exit 1
  fi
done

echo "Preflight OK."

