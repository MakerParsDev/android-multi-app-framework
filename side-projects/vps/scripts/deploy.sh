#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"

# Abort before any image pull/build, port binding or container restart if the
# production configuration is incomplete or still contains template values.
bash "$SCRIPT_DIR/preflight.sh"

docker compose up -d --build
docker compose ps

