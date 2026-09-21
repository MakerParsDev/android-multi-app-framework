#!/usr/bin/env python3
"""
Repository Settings Bootstrap Helper.

Safely configures repository settings for autonomous maintenance.
Supports --check (read-only), --plan (show what would change), and --apply (execute changes).

NEVER defaults to writes. Requires explicit --apply flag.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess  # nosec B404
import sys
from pathlib import Path
from typing import Any


def run_gh_api(
    method: str, endpoint: str, input_data: dict | None = None
) -> tuple[int, Any]:
    """Run gh api command."""
    cmd = ["gh", "api", "--method", method, endpoint]
    if input_data:
        cmd.extend(["--input", "-"])
        result = subprocess.run(
            cmd,
            input=json.dumps(input_data),
            capture_output=True,
            text=True,
            check=False,
        )
    else:
        result = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        return result.returncode, result.stderr
    try:
        return result.returncode, json.loads(
            result.stdout
        ) if result.stdout.strip() else None
    except json.JSONDecodeError:
        return result.returncode, result.stdout


def check_repo_settings(repo: str) -> dict[str, Any]:
    """Check current repository settings."""
    settings = {}

    # Check general settings
    _, data = run_gh_api("GET", f"repos/{repo}")
    if data:
        settings["allow_auto_merge"] = data.get("allow_auto_merge", False)
        settings["delete_branch_on_merge"] = data.get("delete_branch_on_merge", False)
        settings["has_issues"] = data.get("has_issues", True)
        settings["has_wiki"] = data.get("has_wiki", False)
        settings["archived"] = data.get("archived", False)

    # Check branch protection (legacy)
    _, bp_data = run_gh_api("GET", f"repos/{repo}/branches/main/protection")
    settings["legacy_branch_protection"] = bp_data is not None and "message" not in str(
        bp_data
    )

    # Check rulesets
    _, rs_data = run_gh_api("GET", f"repos/{repo}/rulesets")
    settings["rulesets"] = rs_data if isinstance(rs_data, list) else []

    # Check variables
    _, var_data = run_gh_api("GET", f"repos/{repo}/variables")
    settings["variables"] = (
        {v["name"]: v["value"] for v in var_data} if isinstance(var_data, list) else {}
    )

    return settings


def check_required_labels(repo: str) -> dict[str, bool]:
    """Check if required labels exist."""
    required = [
        "fleet",
        "fleet-merge-ready",
        "fleet-review-required",
        "risk:low",
        "risk:protected",
        "automerge:enabled",
        "automerge:disabled",
        "maintenance",
    ]
    _, data = run_gh_api("GET", f"repos/{repo}/labels")
    existing = {label["name"] for label in data} if isinstance(data, list) else set()
    return {label: label in existing for label in required}


def plan_changes(current: dict[str, Any], labels: dict[str, bool]) -> list[str]:
    """Generate plan of changes needed."""
    changes = []

    # Repository settings
    # NOTE: allow_auto_merge should remain FALSE.
    # Mergify is the sole merge authority; GitHub native auto-merge would create
    # a second merge mechanism and race conditions. Mergify Merge Queue works
    # independently and does not require GitHub's allow_auto_merge setting.
    if current.get("allow_auto_merge"):
        changes.append(
            "WARNING: allow_auto_merge is TRUE - should be FALSE (Mergify is sole merge authority)"
        )
    if not current.get("delete_branch_on_merge"):
        changes.append("Enable delete_branch_on_merge")

    # Ruleset
    rulesets = current.get("rulesets", [])
    has_main_ruleset = any(
        rs.get("conditions", {}).get("ref_name", {}).get("include", [])
        == ["~DEFAULT_BRANCH"]
        for rs in rulesets
    )
    if not has_main_ruleset:
        changes.append(
            "Create GitHub ruleset for main branch (see scripts/ci/github-ruleset-payload.json)"
        )

    # Variables
    vars_needed = {
        "AUTONOMOUS_MAINTENANCE_ENABLED": "false",
        "AUTONOMOUS_MERGE_ENABLED": "false",
        "JULES_FLEET_ENABLED": "false",
        "JULES_FLEET_AUTO_MERGE_ENABLED": "false",
    }
    current_vars = current.get("variables", {})
    for var, default in vars_needed.items():
        if var not in current_vars:
            changes.append(f"Create variable {var}={default} (fail-closed default)")

    # Labels
    for label, exists in labels.items():
        if not exists:
            changes.append(f"Create label '{label}'")

    return changes


def apply_changes(repo: str, plan: list[str]) -> bool:
    """Apply the planned changes."""
    success = True

    # Apply repository settings
    # NOTE: allow_auto_merge is intentionally NOT enabled.
    # Mergify is the sole merge authority; GitHub native auto-merge would create
    # a second merge mechanism. Mergify Merge Queue works independently.
    if "WARNING: allow_auto_merge is TRUE" in "\n".join(plan):
        _, err = run_gh_api("PATCH", f"repos/{repo}", {"allow_auto_merge": False})
        if err:
            print(f"Failed to disable allow_auto_merge: {err}", file=sys.stderr)
            success = False
        else:
            print("✓ Disabled allow_auto_merge (Mergify is sole merge authority)")

    if "Enable delete_branch_on_merge" in "\n".join(plan):
        _, err = run_gh_api("PATCH", f"repos/{repo}", {"delete_branch_on_merge": True})
        if err:
            print(f"Failed to enable delete_branch_on_merge: {err}", file=sys.stderr)
            success = False
        else:
            print("✓ Enabled delete_branch_on_merge")

    # Apply ruleset
    if any("Create GitHub ruleset" in p for p in plan):
        payload_path = Path(__file__).parent / "github-ruleset-payload.json"
        if payload_path.exists():
            _, err = run_gh_api(
                "POST", f"repos/{repo}/rulesets", json.loads(payload_path.read_text())
            )
            if err:
                print(f"Failed to create ruleset: {err}", file=sys.stderr)
                success = False
            else:
                print("✓ Created GitHub ruleset for main branch")
        else:
            print("Ruleset payload not found", file=sys.stderr)
            success = False

    # Apply variables
    for item in plan:
        if item.startswith("Create variable "):
            # Parse: Create variable NAME=VALUE
            parts = item.replace("Create variable ", "").split("=")
            if len(parts) == 2:
                name, value = parts
                _, err = run_gh_api(
                    "POST", f"repos/{repo}/variables", {"name": name, "value": value}
                )
                if err:
                    print(f"Failed to create variable {name}: {err}", file=sys.stderr)
                    success = False
                else:
                    print(f"✓ Created variable {name}={value}")

    # Apply labels
    for item in plan:
        if item.startswith("Create label '"):
            label = item.replace("Create label '", "").replace("'", "")
            label_specs = {
                "fleet": {
                    "name": "fleet",
                    "color": "1d76db",
                    "description": "Fleet-managed issue",
                },
                "fleet-merge-ready": {
                    "name": "fleet-merge-ready",
                    "color": "0e8a16",
                    "description": "Ready for fleet sequential merge",
                },
                "fleet-review-required": {
                    "name": "fleet-review-required",
                    "color": "d93f0b",
                    "description": "Protected or ambiguous changes require human review",
                },
                "risk:low": {
                    "name": "risk:low",
                    "color": "0e8a16",
                    "description": "Low-risk change eligible for autonomous merge",
                },
                "risk:protected": {
                    "name": "risk:protected",
                    "color": "d93f0b",
                    "description": "Protected change requiring human review",
                },
                "automerge:enabled": {
                    "name": "automerge:enabled",
                    "color": "1d76db",
                    "description": "Global autonomous merge authorized by control plane",
                },
                "automerge:disabled": {
                    "name": "automerge:disabled",
                    "color": "d93f0b",
                    "description": "Global autonomous merge disabled by control plane",
                },
                "maintenance": {
                    "name": "maintenance",
                    "color": "5319e7",
                    "description": "Autonomous maintenance dashboard issue",
                },
            }
            spec = label_specs.get(label)
            if spec:
                _, err = run_gh_api("POST", f"repos/{repo}/labels", spec)
                if err and "already_exists" not in str(err).lower():
                    print(f"Failed to create label {label}: {err}", file=sys.stderr)
                    success = False
                else:
                    print(f"✓ Created label '{label}'")

    return success


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Repository settings bootstrap helper for autonomous maintenance",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Check current state (read-only, safe)
  python3 scripts/ci/bootstrap_autonomous_repository_settings.py --check

  # Show what would change (read-only, safe)
  python3 scripts/ci/bootstrap_autonomous_repository_settings.py --plan

  # Apply changes (requires admin:repo_hook permission)
  python3 scripts/ci/bootstrap_autonomous_repository_settings.py --apply
""",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Check current repository settings (read-only)",
    )
    parser.add_argument(
        "--plan",
        action="store_true",
        help="Show planned changes without applying (read-only)",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply changes (requires admin permissions)",
    )
    parser.add_argument(
        "--repo",
        default=os.environ.get("GITHUB_REPOSITORY"),
        help="Repository (owner/name)",
    )

    args = parser.parse_args()

    if not args.repo:
        print(
            "Error: Repository not specified (set GITHUB_REPOSITORY or --repo)",
            file=sys.stderr,
        )
        return 1

    # Default to --check if no action specified
    if not any([args.check, args.plan, args.apply]):
        args.check = True

    # Prevent conflicting flags
    if sum([args.check, args.plan, args.apply]) > 1:
        print("Error: Specify only one of --check, --plan, or --apply", file=sys.stderr)
        return 1

    print(f"Repository: {args.repo}")
    print(f"Mode: {'CHECK' if args.check else 'PLAN' if args.plan else 'APPLY'}")
    print()

    # Check current state
    print("Checking current repository state...")
    current = check_repo_settings(args.repo)
    labels = check_required_labels(args.repo)

    # Print current state
    print("\n=== Current Repository Settings ===")
    print(f"  allow_auto_merge: {current.get('allow_auto_merge')}")
    print(f"  delete_branch_on_merge: {current.get('delete_branch_on_merge')}")
    print(f"  legacy_branch_protection: {current.get('legacy_branch_protection')}")
    print(f"  rulesets count: {len(current.get('rulesets', []))}")

    print("\n=== Circuit Breaker Variables ===")
    for var in [
        "AUTONOMOUS_MAINTENANCE_ENABLED",
        "AUTONOMOUS_MERGE_ENABLED",
        "JULES_FLEET_ENABLED",
        "JULES_FLEET_AUTO_MERGE_ENABLED",
    ]:
        val = current.get("variables", {}).get(var, "NOT SET")
        print(f"  {var}: {val}")

    print("\n=== Required Labels ===")
    for label, exists in labels.items():
        status = "OK" if exists else "MISSING"
        print(f"  {status} {label}")

    if args.check:
        return 0

    # Generate plan
    plan = plan_changes(current, labels)
    print("\n=== Planned Changes ===")
    if plan:
        for i, change in enumerate(plan, 1):
            print(f"  {i}. {change}")
    else:
        print("  No changes needed - repository is fully configured!")

    if args.plan:
        return 0

    # Apply changes
    if args.apply:
        print("\n=== Applying Changes ===")
        # Verify admin permission
        _, test = run_gh_api("GET", f"repos/{args.repo}")
        if isinstance(test, dict) and test.get("permissions", {}).get("admin"):
            print("Admin permission confirmed")
        else:
            print(
                "Warning: Admin permission not confirmed. Changes may fail.",
                file=sys.stderr,
            )

        if apply_changes(args.repo, plan):
            print("\n✓ All changes applied successfully")
            return 0
        else:
            print("\n✗ Some changes failed", file=sys.stderr)
            return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
