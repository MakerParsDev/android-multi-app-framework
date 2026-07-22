#!/usr/bin/env bash
set -euo pipefail

artifact_root="${1:?artifact root is required}"
branch="automation/baseline-profiles"
: "${GH_TOKEN:?GH_TOKEN is required}"
: "${AUTOMATION_APP_SLUG:?AUTOMATION_APP_SLUG is required}"
: "${AUTOMATION_APP_USER_ID:?AUTOMATION_APP_USER_ID is required}"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

git fetch --prune origin main "$branch" 2>/dev/null || git fetch --prune origin main
git checkout -B "$branch" origin/main

mapfile -t flavors < <(
  python3 scripts/ci/resolve_ci_flavor_matrix.py |
    sed -n 's/^flavors=//p' |
    python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)))'
)
[[ "${#flavors[@]}" -gt 0 ]] || {
  echo 'ERROR: no app flavors resolved' >&2
  exit 1
}

for flavor in "${flavors[@]}"; do
  source_dir="$artifact_root/$flavor"
  target_dir="app/src/${flavor}Release/generated/baselineProfiles"
  test -s "$source_dir/baseline-prof.txt"
  test -s "$source_dir/startup-prof.txt"
  mkdir -p "$target_dir"
  install -m 0644 "$source_dir/baseline-prof.txt" "$target_dir/baseline-prof.txt"
  install -m 0644 "$source_dir/startup-prof.txt" "$target_dir/startup-prof.txt"
done

python3 scripts/ci/performance_profile_policy.py validate-all
if git diff --quiet -- app/src; then
  echo 'No Baseline Profile changes'
  exit 0
fi

git config user.name "${AUTOMATION_APP_SLUG}[bot]"
git config user.email "${AUTOMATION_APP_USER_ID}+${AUTOMATION_APP_SLUG}[bot]@users.noreply.github.com"
git add -- app/src/*Release/generated/baselineProfiles
git commit -m 'perf: refresh 17-flavor Baseline Profiles'

askpass="$(mktemp)"
cleanup() {
  rm -f -- "$askpass"
}
trap cleanup EXIT
cat > "$askpass" <<'ASKPASS'
#!/usr/bin/env sh
case "$1" in
  *Username*) printf '%s\n' 'x-access-token' ;;
  *) printf '%s\n' "$GH_TOKEN" ;;
esac
ASKPASS
chmod 0700 "$askpass"
GIT_ASKPASS="$askpass" GIT_TERMINAL_PROMPT=0 git push --force-with-lease origin "$branch"

pr_number="$(gh pr list --head "$branch" --base main --state open --json number --jq '.[0].number // empty')"
if [[ -n "$pr_number" ]]; then
  gh pr edit "$pr_number" \
    --title 'perf: refresh 17-flavor Baseline Profiles' \
    --body-file .github/baseline-profile-pr-body.md
else
  gh pr create \
    --base main \
    --head "$branch" \
    --title 'perf: refresh 17-flavor Baseline Profiles' \
    --body-file .github/baseline-profile-pr-body.md
fi
