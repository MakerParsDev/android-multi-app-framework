#!/usr/bin/env bash
set -euo pipefail
version='3.76.1'
asset="doppler_${version}_linux_amd64.tar.gz"
sha256='e35230bd21fdbd7e41ddcb24672ec61cecefdb22de244d0216ea6b59853f63f2'
url="https://github.com/DopplerHQ/cli/releases/download/${version}/${asset}"
# Independent official Doppler package CDN; exact version and digest are pinned.
deb_asset="doppler_${version}_linux_amd64.deb"
deb_sha256='7dea74c3126c1b7f5d5dd69464a5a39aa33175f00872b5d0070c2398f22d15fa'
deb_url="https://packages.doppler.com/public/cli/deb/debian/pool/any-version/main/d/do/doppler_${version}/${deb_asset}"
if [[ "${1:-}" == '--print-config' ]]; then
  printf 'version=%s\nasset=%s\nsha256=%s\n' "$version" "$asset" "$sha256"
  exit 0
fi
bin_dir="${1:-${RUNNER_TEMP:-/tmp}/doppler-bin}"
mkdir -p "$bin_dir"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
if curl -fsSL --connect-timeout 10 --max-time 60 --retry 3 --retry-delay 2 "$url" -o "$tmp/$asset"; then
  printf '%s  %s\n' "$sha256" "$tmp/$asset" | sha256sum -c - >&2
  tar -xzf "$tmp/$asset" -C "$tmp"
  binary="$tmp/doppler"
else
  # Release-asset gateway occasionally returns 504 for hosted CI runners.
  # Do not use unpinned install scripts or skip checksum verification.
  echo 'Doppler GitHub release unavailable; checking official pinned Debian package' >&2
  curl -fsSL --connect-timeout 10 --max-time 60 --retry 2 --retry-delay 2 "$deb_url" -o "$tmp/$deb_asset"
  printf '%s  %s\n' "$deb_sha256" "$tmp/$deb_asset" | sha256sum -c - >&2
  dpkg-deb -x "$tmp/$deb_asset" "$tmp/deb-root"
  binary="$tmp/deb-root/usr/bin/doppler"
fi
install -m 0755 "$binary" "$bin_dir/doppler"
"$bin_dir/doppler" --version >&2
printf 'DOPPLER_BIN=%q\n' "$bin_dir/doppler"
if [[ -n "${GITHUB_PATH:-}" ]]; then
  echo "$bin_dir" >>"$GITHUB_PATH"
fi
