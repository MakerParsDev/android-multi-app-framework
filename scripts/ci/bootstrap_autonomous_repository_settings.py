#!/usr/bin/env python3
"""Safely bootstrap repository settings for autonomous maintenance.

The helper is read-only unless --apply is explicitly supplied. It keeps GitHub
native auto-merge disabled, creates missing fail-closed variables and labels,
and creates or updates the repository ruleset idempotently.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import subprocess  # nosec B404
import sys
from typing import Any

API_VERSION = "2026-03-10"
RULESET_NAME = "main-branch-protection"
RULESET_PAYLOAD = Path(__file__).with_name("github-ruleset-payload.json")
VARIABLE_DEFAULTS = {
    "AUTONOMOUS_MAINTENANCE_ENABLED": "false",
    "AUTONOMOUS_MERGE_ENABLED": "false",
    "JULES_FLEET_ENABLED": "false",
    "JULES_FLEET_SCHEDULED_ANALYZE_ENABLED": "false",
    "JULES_FLEET_AUTO_MERGE_ENABLED": "false",
}
LABEL_SPECS = {
    "fleet": ("1d76db", "Fleet-managed issue"),
    "fleet-merge-ready": ("0e8a16", "Ready for fleet sequential merge"),
    "fleet-review-required": ("d93f0b", "Protected or ambiguous changes require human review"),
    "risk:low": ("0e8a16", "Low-risk change eligible for autonomous merge"),
    "risk:protected": ("d93f0b", "Protected change requiring human review"),
    "automerge:enabled": ("1d76db", "Global autonomous merge authorized by control plane"),
    "automerge:disabled": ("d93f0b", "Temporarily disables autonomous merge"),
    "maintenance": ("5319e7", "Autonomous maintenance dashboard issue"),
}


@dataclass(frozen=True)
class GhApiResult:
    """Structured gh api result that never conflates data and errors."""

    returncode: int
    data: Any | None
    stderr: str

    @property
    def ok(self) -> bool:
        return self.returncode == 0


def run_gh_api(
    method: str,
    endpoint: str,
    input_data: dict[str, Any] | None = None,
    *,
    paginate: bool = False,
) -> GhApiResult:
    """Run gh api with the current GitHub REST API version.

    Paginated mode uses ``gh api --paginate --slurp`` so GitHub's server-
    provided pagination links are followed without implementing link handling
    in this repository. It is opt-in to preserve existing response shapes.
    """
    cmd = [
        "gh", "api", "--method", method,
        "-H", "Accept: application/vnd.github+json",
        "-H", f"X-GitHub-Api-Version: {API_VERSION}",
        endpoint,
    ]
    if paginate:
        if method.upper() != "GET" or input_data is not None:
            return GhApiResult(
                2,
                None,
                "Pagination is supported only for GET requests without input data",
            )
        cmd.extend(["--paginate", "--slurp"])
    if input_data is not None:
        cmd.extend(["--input", "-"])
    result = subprocess.run(
        cmd,
        input=json.dumps(input_data) if input_data is not None else None,
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        return GhApiResult(result.returncode, None, result.stderr.strip())
    stdout = result.stdout.strip()
    if not stdout:
        return GhApiResult(0, None, "")
    try:
        return GhApiResult(0, json.loads(stdout), "")
    except json.JSONDecodeError:
        return GhApiResult(0, stdout, "")


def load_ruleset_payload() -> dict[str, Any]:
    """Load the version-controlled desired ruleset."""
    return json.loads(RULESET_PAYLOAD.read_text(encoding="utf-8"))


def _canonical_rule(rule: dict[str, Any]) -> dict[str, Any]:
    """Return only rule fields intentionally controlled by this repository."""
    rule_type = rule.get("type")
    result: dict[str, Any] = {"type": rule_type}
    params = dict(rule.get("parameters") or {})

    if rule_type == "pull_request":
        keys = (
            "allowed_merge_methods",
            "dismiss_stale_reviews_on_push",
            "require_code_owner_review",
            "require_last_push_approval",
            "required_approving_review_count",
            "required_review_thread_resolution",
        )
        result["parameters"] = {key: params.get(key) for key in keys}
    elif rule_type == "required_status_checks":
        checks = sorted(
            (
                {
                    "context": item.get("context"),
                    **(
                        {"integration_id": item["integration_id"]}
                        if item.get("integration_id") is not None
                        else {}
                    ),
                }
                for item in params.get("required_status_checks", [])
            ),
            key=lambda item: (str(item.get("context")), str(item.get("integration_id", ""))),
        )
        result["parameters"] = {
            "do_not_enforce_on_create": bool(params.get("do_not_enforce_on_create", False)),
            "required_status_checks": checks,
            "strict_required_status_checks_policy": bool(
                params.get("strict_required_status_checks_policy", False)
            ),
        }
    return result


def canonical_ruleset(data: dict[str, Any]) -> dict[str, Any]:
    """Canonicalize relevant ruleset fields for idempotent comparison."""
    return {
        "name": data.get("name"),
        "target": data.get("target"),
        "enforcement": data.get("enforcement"),
        "conditions": data.get("conditions") or {},
        "rules": sorted(
            (_canonical_rule(rule) for rule in data.get("rules", [])),
            key=lambda item: str(item.get("type")),
        ),
        "bypass_actors": data.get("bypass_actors") or [],
    }


def fetch_ruleset_detail(repo: str, summaries: list[dict[str, Any]]) -> tuple[dict[str, Any] | None, str | None]:
    """Fetch the full desired ruleset definition, if it already exists."""
    match = next((item for item in summaries if item.get("name") == RULESET_NAME), None)
    if not match:
        return None, None
    ruleset_id = match.get("id")
    if ruleset_id is None:
        return None, f"Ruleset {RULESET_NAME!r} summary has no id"
    result = run_gh_api("GET", f"repos/{repo}/rulesets/{ruleset_id}")
    if not result.ok or not isinstance(result.data, dict):
        return None, result.stderr or f"Unable to fetch ruleset {ruleset_id}"
    return result.data, None


def check_repo_settings(repo: str) -> dict[str, Any]:
    """Read repository settings without changing them."""
    settings: dict[str, Any] = {"errors": []}

    repo_result = run_gh_api("GET", f"repos/{repo}")
    if repo_result.ok and isinstance(repo_result.data, dict):
        settings["allow_auto_merge"] = bool(repo_result.data.get("allow_auto_merge", False))
        settings["delete_branch_on_merge"] = bool(repo_result.data.get("delete_branch_on_merge", False))
        settings["permissions"] = repo_result.data.get("permissions", {})
    else:
        settings["errors"].append(repo_result.stderr or "Unable to read repository settings")

    protection = run_gh_api("GET", f"repos/{repo}/branches/main/protection")
    if protection.ok:
        settings["legacy_branch_protection"] = True
    elif protection.returncode != 0 and "404" in protection.stderr:
        settings["legacy_branch_protection"] = False
    else:
        settings["legacy_branch_protection"] = None

    rulesets = run_gh_api("GET", f"repos/{repo}/rulesets")
    summaries = rulesets.data if rulesets.ok and isinstance(rulesets.data, list) else []
    settings["rulesets"] = summaries
    if not rulesets.ok:
        settings["errors"].append(rulesets.stderr or "Unable to list repository rulesets")
    detail, detail_error = fetch_ruleset_detail(repo, summaries)
    settings["ruleset_detail"] = detail
    if detail_error:
        settings["errors"].append(detail_error)

    variables = run_gh_api("GET", f"repos/{repo}/actions/variables")
    if variables.ok and isinstance(variables.data, dict):
        items = variables.data.get("variables", [])
        settings["variables"] = {
            item["name"]: str(item.get("value", ""))
            for item in items
            if isinstance(item, dict) and item.get("name")
        }
    else:
        settings["variables"] = {}
        settings["errors"].append(variables.stderr or "Unable to list repository variables")

    return settings


def check_required_labels(repo: str) -> tuple[dict[str, bool], str | None]:
    """Return required label presence and an optional API error."""
    result = run_gh_api("GET", f"repos/{repo}/labels?per_page=100")
    if not result.ok or not isinstance(result.data, list):
        return {name: False for name in LABEL_SPECS}, result.stderr or "Unable to list labels"
    existing = {str(label.get("name")) for label in result.data if isinstance(label, dict)}
    return {name: name in existing for name in LABEL_SPECS}, None


def ruleset_drift(current: dict[str, Any] | None, desired: dict[str, Any]) -> bool:
    """Return True when the current full ruleset differs from desired state."""
    return current is None or canonical_ruleset(current) != canonical_ruleset(desired)


def plan_changes(current: dict[str, Any], labels: dict[str, bool], desired_ruleset: dict[str, Any]) -> list[str]:
    """Generate an explicit change plan."""
    changes: list[str] = []
    if current.get("allow_auto_merge") is True:
        changes.append("DISABLE_NATIVE_AUTO_MERGE")
    if current.get("delete_branch_on_merge") is False:
        changes.append("ENABLE_DELETE_BRANCH_ON_MERGE")

    detail = current.get("ruleset_detail")
    summaries = current.get("rulesets", [])
    summary = next((item for item in summaries if item.get("name") == RULESET_NAME), None)
    if detail is None and summary is None:
        changes.append("CREATE_RULESET")
    elif detail is None:
        changes.append("RULESET_READ_ERROR")
    elif ruleset_drift(detail, desired_ruleset):
        changes.append(f"UPDATE_RULESET:{detail.get('id')}")

    existing_vars = current.get("variables", {})
    for name, value in VARIABLE_DEFAULTS.items():
        if name not in existing_vars:
            changes.append(f"CREATE_VARIABLE:{name}:{value}")

    for label, exists in labels.items():
        if not exists:
            changes.append(f"CREATE_LABEL:{label}")

    return changes


def apply_changes(repo: str, plan: list[str], desired_ruleset: dict[str, Any]) -> bool:
    """Apply an explicit plan. Missing variables are created fail-closed only."""
    success = True

    if "RULESET_READ_ERROR" in plan:
        print("Refusing to apply because an existing ruleset could not be read.", file=sys.stderr)
        return False

    if "DISABLE_NATIVE_AUTO_MERGE" in plan:
        result = run_gh_api("PATCH", f"repos/{repo}", {"allow_auto_merge": False})
        if result.ok:
            print("? Disabled GitHub native auto-merge")
        else:
            print(f"Failed to disable native auto-merge: {result.stderr}", file=sys.stderr)
            success = False

    if "ENABLE_DELETE_BRANCH_ON_MERGE" in plan:
        result = run_gh_api("PATCH", f"repos/{repo}", {"delete_branch_on_merge": True})
        if result.ok:
            print("? Enabled delete_branch_on_merge")
        else:
            print(f"Failed to enable delete_branch_on_merge: {result.stderr}", file=sys.stderr)
            success = False

    if "CREATE_RULESET" in plan:
        result = run_gh_api("POST", f"repos/{repo}/rulesets", desired_ruleset)
        if result.ok:
            print("? Created main-branch-protection ruleset")
        else:
            print(f"Failed to create ruleset: {result.stderr}", file=sys.stderr)
            success = False

    for item in plan:
        if item.startswith("UPDATE_RULESET:"):
            ruleset_id = item.split(":", 1)[1]
            result = run_gh_api("PUT", f"repos/{repo}/rulesets/{ruleset_id}", desired_ruleset)
            if result.ok:
                print(f"? Updated ruleset {ruleset_id}")
            else:
                print(f"Failed to update ruleset {ruleset_id}: {result.stderr}", file=sys.stderr)
                success = False

    for item in plan:
        if item.startswith("CREATE_VARIABLE:"):
            _, name, value = item.split(":", 2)
            result = run_gh_api(
                "POST",
                f"repos/{repo}/actions/variables",
                {"name": name, "value": value},
            )
            if result.ok:
                print(f"? Created variable {name}={value}")
            else:
                print(f"Failed to create variable {name}: {result.stderr}", file=sys.stderr)
                success = False

    for item in plan:
        if item.startswith("CREATE_LABEL:"):
            label = item.split(":", 1)[1]
            color, description = LABEL_SPECS[label]
            result = run_gh_api(
                "POST",
                f"repos/{repo}/labels",
                {"name": label, "color": color, "description": description},
            )
            if result.ok:
                print(f"? Created label {label!r}")
            else:
                print(f"Failed to create label {label!r}: {result.stderr}", file=sys.stderr)
                success = False

    return success


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--check", action="store_true", help="Read and print current state")
    group.add_argument("--plan", action="store_true", help="Read and print planned changes")
    group.add_argument("--apply", action="store_true", help="Apply planned changes")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"), help="owner/name")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.repo:
        print("Error: Repository not specified (set GITHUB_REPOSITORY or --repo)", file=sys.stderr)
        return 1
    if not (args.check or args.plan or args.apply):
        args.check = True

    desired = load_ruleset_payload()
    current = check_repo_settings(args.repo)
    labels, label_error = check_required_labels(args.repo)
    if label_error:
        current.setdefault("errors", []).append(label_error)

    print(f"Repository: {args.repo}")
    print(f"Mode: {'CHECK' if args.check else 'PLAN' if args.plan else 'APPLY'}")
    print("\n=== Current Repository Settings ===")
    print(f"  allow_auto_merge: {current.get('allow_auto_merge', 'UNKNOWN')}")
    print(f"  delete_branch_on_merge: {current.get('delete_branch_on_merge', 'UNKNOWN')}")
    print(f"  legacy_branch_protection: {current.get('legacy_branch_protection', 'UNKNOWN')}")
    print(f"  rulesets count: {len(current.get('rulesets', []))}")
    detail = current.get("ruleset_detail")
    if isinstance(detail, dict):
        print(f"  {RULESET_NAME}: id={detail.get('id')} enforcement={detail.get('enforcement')} drift={ruleset_drift(detail, desired)}")
    else:
        print(f"  {RULESET_NAME}: NOT PRESENT")

    print("\n=== Circuit Breaker Variables ===")
    for name in VARIABLE_DEFAULTS:
        print(f"  {name}: {current.get('variables', {}).get(name, 'NOT SET')}")

    print("\n=== Required Labels ===")
    for label, exists in labels.items():
        print(f"  {'OK' if exists else 'MISSING'} {label}")

    errors = current.get("errors", [])
    if errors:
        print("\n=== Read Errors ===", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        if args.apply:
            print("Refusing --apply because repository state could not be read reliably.", file=sys.stderr)
            return 1

    if args.check:
        return 1 if errors else 0

    plan = plan_changes(current, labels, desired)
    print("\n=== Planned Changes ===")
    if plan:
        for index, item in enumerate(plan, start=1):
            print(f"  {index}. {item}")
    else:
        print("  No changes needed.")

    if args.plan:
        return 1 if errors else 0

    repo_result = run_gh_api("GET", f"repos/{args.repo}")
    if not repo_result.ok or not isinstance(repo_result.data, dict):
        print(f"Cannot verify administrator permission: {repo_result.stderr}", file=sys.stderr)
        return 1
    if not repo_result.data.get("permissions", {}).get("admin"):
        print(
            "Repository administrator permission is required. For fine-grained credentials, "
            "ruleset writes require Administration: write.",
            file=sys.stderr,
        )
        return 1

    if not apply_changes(args.repo, plan, desired):
        return 1

    # Idempotence verification: re-read after writes and ensure no further plan remains.
    post = check_repo_settings(args.repo)
    post_labels, post_label_error = check_required_labels(args.repo)
    if post.get("errors") or post_label_error:
        print("Post-apply verification failed to read repository state.", file=sys.stderr)
        return 1
    residual = plan_changes(post, post_labels, desired)
    if residual:
        print(f"Post-apply verification found residual drift: {residual}", file=sys.stderr)
        return 1
    print("\n? Apply completed and idempotence verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
