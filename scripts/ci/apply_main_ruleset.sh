#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPO="${REPO:-MakerParsDev/android-multi-app-framework}"
POLICY_FILE="${POLICY_FILE:-$ROOT_DIR/config/main-ruleset.json}"
RULESET_NAME="Protect main"
API_VERSION="2026-03-10"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

command -v gh >/dev/null 2>&1 || fail "GitHub CLI (gh) bulunamadı"
command -v python3 >/dev/null 2>&1 || fail "python3 bulunamadı"
[[ -f "$POLICY_FILE" ]] || fail "Ruleset politika dosyası bulunamadı: $POLICY_FILE"
python3 -m json.tool "$POLICY_FILE" >/dev/null

login="$(gh api user --jq .login)"
[[ "$login" == "MakerParsDev" ]] || fail "Aktif GitHub hesabı MakerParsDev değil: $login"

ruleset_id="$(
  gh api \
    -H "X-GitHub-Api-Version: $API_VERSION" \
    "repos/$REPO/rulesets" \
    --jq ".[] | select(.name == \"$RULESET_NAME\" and .source_type == \"Repository\") | .id" \
    | head -n 1
)"

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

if [[ -n "$ruleset_id" ]]; then
  gh api \
    --method PUT \
    -H "X-GitHub-Api-Version: $API_VERSION" \
    "repos/$REPO/rulesets/$ruleset_id" \
    --input "$POLICY_FILE" > "$response_file"
else
  gh api \
    --method POST \
    -H "X-GitHub-Api-Version: $API_VERSION" \
    "repos/$REPO/rulesets" \
    --input "$POLICY_FILE" > "$response_file"
  ruleset_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$response_file")"
fi

gh api \
  -H "X-GitHub-Api-Version: $API_VERSION" \
  "repos/$REPO/rulesets/$ruleset_id" > "$response_file"

python3 - "$POLICY_FILE" "$response_file" <<'PY'
from __future__ import annotations

import json
from pathlib import Path
import sys

expected = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
actual = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))

for key in ("name", "target", "enforcement", "bypass_actors", "conditions"):
    if actual.get(key) != expected.get(key):
        raise SystemExit(f"ruleset verification failed for field: {key}")

expected_rules = {rule["type"]: rule for rule in expected["rules"]}
actual_rules = {rule["type"]: rule for rule in actual["rules"]}
for required_type in ("deletion", "non_fast_forward", "pull_request", "required_status_checks"):
    if required_type not in actual_rules:
        raise SystemExit(f"ruleset verification missing rule: {required_type}")

expected_pull = expected_rules["pull_request"]["parameters"]
actual_pull = actual_rules["pull_request"]["parameters"]
for key, value in expected_pull.items():
    if actual_pull.get(key) != value:
        raise SystemExit(f"ruleset verification failed for pull_request.{key}")

expected_status = expected_rules["required_status_checks"]["parameters"]
actual_status = actual_rules["required_status_checks"]["parameters"]
for key in ("do_not_enforce_on_create", "strict_required_status_checks_policy"):
    if actual_status.get(key) != expected_status[key]:
        raise SystemExit(f"ruleset verification failed for required_status_checks.{key}")
expected_contexts = {item["context"] for item in expected_status["required_status_checks"]}
actual_contexts = {item["context"] for item in actual_status["required_status_checks"]}
if actual_contexts != expected_contexts:
    raise SystemExit("ruleset verification failed for required status contexts")

print(f"Ruleset verified: id={actual['id']} enforcement={actual['enforcement']}")
print(actual.get("_links", {}).get("html", {}).get("href", ""))
PY
