#!/usr/bin/env bash
set -euo pipefail

if command -v doppler >/dev/null 2>&1; then
  doppler --version
  exit 0
fi

curl -fsSL --retry 3 --tlsv1.2 --proto '=https' https://packages.doppler.com/public/cli/gpg.DE2A7741A397C129.key \
  | sudo gpg --dearmor -o /usr/share/keyrings/doppler-archive-keyring.gpg

echo 'deb [signed-by=/usr/share/keyrings/doppler-archive-keyring.gpg] https://packages.doppler.com/public/cli/deb/debian any-version main' \
  | sudo tee /etc/apt/sources.list.d/doppler-cli.list >/dev/null

sudo apt-get update -y >/dev/null
sudo apt-get install -y doppler >/dev/null

doppler --version
