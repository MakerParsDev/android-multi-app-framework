#!/usr/bin/env bash
set -euo pipefail
version='3.76.1'
asset="doppler_${version}_linux_amd64.tar.gz"
sha256='e35230bd21fdbd7e41ddcb24672ec61cecefdb22de244d0216ea6b59853f63f2'
url="https://github.com/DopplerHQ/cli/releases/download/${version}/${asset}"
if [[ "${1:-}" == '--print-config' ]]; then
  printf 'version=%s\nasset=%s\nsha256=%s\n' "$version" "$asset" "$sha256"
  exit 0
fi
bin_dir="${1:-${RUNNER_TEMP:-/tmp}/doppler-bin}"
mkdir -p "$bin_dir"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
curl -fsSL --retry 3 --retry-delay 2 "$url" -o "$tmp/$asset"
printf '%s  %s\n' "$sha256" "$tmp/$asset" | sha256sum -c - >&2
tar -xzf "$tmp/$asset" -C "$tmp"
install -m 0755 "$tmp/doppler" "$bin_dir/doppler"
"$bin_dir/doppler" --version >&2
printf 'DOPPLER_BIN=%q\n' "$bin_dir/doppler"
