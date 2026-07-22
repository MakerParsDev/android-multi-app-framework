#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
config="$(bash "$repo_root/scripts/ci/install_doppler_cli.sh" --print-config)"
grep -Fx 'version=3.76.1' <<<"$config"
grep -Fx 'asset=doppler_3.76.1_linux_amd64.tar.gz' <<<"$config"
grep -Fx 'sha256=e35230bd21fdbd7e41ddcb24672ec61cecefdb22de244d0216ea6b59853f63f2' <<<"$config"
echo 'PASS install_doppler_cli_test'
