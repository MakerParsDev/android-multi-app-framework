#!/usr/bin/env bash
set -euo pipefail

input="${1:-${TARGET_FLAVORS:-all}}"
input="$(echo "$input" | tr -d '[:space:]')"

if [[ -z "$input" || "$input" == "all" ]]; then
  mapfile -t flavors < <(./gradlew -q printFlavors --no-daemon | sed '/^$/d')
else
  IFS=',' read -r -a flavors <<< "$input"
fi

if [[ "${#flavors[@]}" -eq 0 ]]; then
  echo "No flavors resolved from input: $input" >&2
  exit 1
fi

csv="$(IFS=,; echo "${flavors[*]}")"
json="["
for f in "${flavors[@]}"; do
  [[ -z "$f" ]] && continue
  if [[ "$json" != "[" ]]; then json+=","; fi
  json+="\"$f\""
done
json+="]"

echo "Resolved flavors: $csv"
echo "##vso[task.setvariable variable=RESOLVED_FLAVORS_CSV]$csv"
echo "##vso[task.setvariable variable=RESOLVED_FLAVORS_JSON]$json"
