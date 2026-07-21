#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mapfile -t flavors < <(
  ./gradlew -q printFlavors --no-daemon --max-workers=1 |
    python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)))'
)

if [[ ${#flavors[@]} -ne 17 ]]; then
  echo "Expected 17 application flavors for release task-graph dry-run, found ${#flavors[@]}" >&2
  exit 1
fi

release_tasks=()
for flavor in "${flavors[@]}"; do
  [[ -n "$flavor" ]] || continue
  capitalized="${flavor^}"
  release_tasks+=(":app:bundle${capitalized}Release")
done

printf 'Release task-graph dry-run: %s\n' "${release_tasks[*]}"
./gradlew \
  :app:validateFlavorVersions \
  "${release_tasks[@]}" \
  --dry-run \
  --stacktrace \
  --warning-mode all \
  --no-daemon \
  --max-workers=1
