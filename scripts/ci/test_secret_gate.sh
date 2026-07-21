#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
GITLEAKS_BIN="${GITLEAKS_BIN:-$(bash "$SCRIPT_DIR/install_gitleaks.sh")}"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/secret-gate-test.XXXXXX")"
trap 'rm -rf -- "$tmp_dir"' EXIT

initialize_repo() {
  local repo="$1"
  git -C "$repo" init -q
  git -C "$repo" config user.email security-gate@example.invalid
  git -C "$repo" config user.name security-gate
}

clean_repo="$tmp_dir/clean"
mkdir -p "$clean_repo"
initialize_repo "$clean_repo"
printf 'safe=value\n' > "$clean_repo/example.txt"
git -C "$clean_repo" add example.txt
git -C "$clean_repo" commit -qm 'clean fixture'
"$GITLEAKS_BIN" git --config "$REPO_ROOT/.gitleaks.toml" \
  --redact=100 --no-banner --no-color --log-opts='--all' "$clean_repo" >/dev/null

leaky_repo="$tmp_dir/leaky"
mkdir -p "$leaky_repo"
initialize_repo "$leaky_repo"
python3 - "$leaky_repo/credentials.txt" <<'PY'
import sys
from pathlib import Path
prefix = "gh" + "p_"
payload = "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"
Path(sys.argv[1]).write_text(f"token={prefix}{payload}\n", encoding="utf-8")
PY
git -C "$leaky_repo" add credentials.txt
git -C "$leaky_repo" commit -qm 'leaky fixture'

set +e
"$GITLEAKS_BIN" git --config "$REPO_ROOT/.gitleaks.toml" \
  --redact=100 --no-banner --no-color \
  --report-format sarif --report-path "$tmp_dir/leaky.sarif" \
  --log-opts='--all' "$leaky_repo" >/dev/null 2>&1
leak_rc=$?
set -e
if [ "$leak_rc" -ne 1 ]; then
  echo "Secret gate self-test expected a leak exit code of 1, got $leak_rc." >&2
  exit 1
fi
if grep -q 'ghp_' "$tmp_dir/leaky.sarif"; then
  echo "Secret gate self-test report was not fully redacted." >&2
  exit 1
fi

echo "Secret gate self-test passed: clean commit accepted, synthetic secret commit rejected"
