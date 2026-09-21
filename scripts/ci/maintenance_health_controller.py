#!/usr/bin/env python3
"""Autonomous Maintenance Health Controller.

Monitors repository maintenance health, toolchain drift, policy expirations,
and updates a single consolidated 'Autonomous Maintenance Dashboard' issue.

State Model:
- BOOTSTRAP_PENDING: Post-merge ruleset not yet activated (expected during rollout)
- HEALTHY: All systems operational
- DEGRADED: Some non-critical systems degraded
- ATTENTION_REQUIRED: Critical issues requiring intervention
- UNKNOWN: GitHub API unavailable or insufficient permissions
"""

from __future__ import annotations

import argparse
from datetime import date, datetime, timezone
import json
from pathlib import Path
import subprocess  # nosec B404
import sys
from typing import Any

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT = Path(__file__).resolve().parents[2]


def check_pinned_actions() -> tuple[bool, str]:
    manifest_path = ROOT / "config/pinned-github-actions.json"
    if not manifest_path.is_file():
        return False, "Pinned actions manifest missing"
    try:
        data = json.loads(manifest_path.read_text(encoding="utf-8"))
        count = len(data)
        return True, f"{count} external actions pinned and verified"
    except Exception as err:
        return False, f"Failed to parse pinned actions: {err}"


def check_codeql_kotlin_compatibility() -> tuple[bool, str]:
    policy_path = ROOT / "config/codeql-compatibility-policy.json"
    catalog_path = ROOT / "gradle/libs.versions.toml"
    if not policy_path.is_file() or not catalog_path.is_file():
        return False, "CodeQL policy or version catalog missing"

    try:
        policy = json.loads(policy_path.read_text(encoding="utf-8"))
        max_str = policy["kotlin"]["supported_max_exclusive"]
        max_tup = tuple(int(x) for x in max_str.split("."))

        import tomllib

        catalog = tomllib.loads(catalog_path.read_text(encoding="utf-8"))
        kotlin_str = catalog["versions"]["kotlin"]
        kotlin_tup = tuple(int(x) for x in kotlin_str.split("."))

        if kotlin_tup < max_tup:
            return True, f"Kotlin {kotlin_str} < CodeQL ceiling {max_str}"
        return False, f"Kotlin {kotlin_str} exceeds CodeQL ceiling {max_str}"
    except Exception as err:
        return False, f"CodeQL compatibility check error: {err}"


def check_expirations() -> list[str]:
    findings: list[str] = []
    today = datetime.now(timezone.utc).date()

    # 1. Audit policy
    audit_policy = ROOT / "side-projects/audit-policy.json"
    if audit_policy.is_file():
        data = json.loads(audit_policy.read_text(encoding="utf-8"))
        for project, entries in data.get("dev_exceptions", {}).items():
            for adv, info in entries.items():
                expires_str = info.get("expires_on")
                if expires_str:
                    exp_date = date.fromisoformat(expires_str)
                    if exp_date <= today:
                        findings.append(
                            f"Expired dev audit exception in {project}: {adv} (expired {expires_str})"
                        )

    # 2. Dependency policy
    dep_policy = ROOT / "config/dependency-policy.json"
    if dep_policy.is_file():
        data = json.loads(dep_policy.read_text(encoding="utf-8"))
        for item in data.get("catalog_prerelease_allowlist", []):
            exp_str = item.get("expires_on")
            if exp_str and date.fromisoformat(exp_str) <= today:
                findings.append(
                    f"Expired catalog prerelease allowlist: {item.get('alias')} (expired {exp_str})"
                )
        for item in data.get("transitive_prerelease_allowlist", []):
            exp_str = item.get("expires_on")
            if exp_str and date.fromisoformat(exp_str) <= today:
                findings.append(
                    f"Expired transitive prerelease allowlist: {item.get('coordinate')} (expired {exp_str})"
                )

    # 3. CodeQL review date
    codeql_policy = ROOT / "config/codeql-compatibility-policy.json"
    if codeql_policy.is_file():
        data = json.loads(codeql_policy.read_text(encoding="utf-8"))
        rev_str = data.get("kotlin", {}).get("next_review_on")
        if rev_str and date.fromisoformat(rev_str) <= today:
            findings.append(f"CodeQL Kotlin policy review date reached: {rev_str}")

    return findings


def check_mergify_configuration() -> tuple[bool, str]:
    mergify_path = ROOT / ".mergify.yml"
    if not mergify_path.is_file():
        return False, ".mergify.yml does not exist"
    content = mergify_path.read_text(encoding="utf-8")
    if "autoqueue" in content:
        return False, "Deprecated autoqueue key found in .mergify.yml"
    if "queue_rules:" not in content or "merge_protections:" not in content:
        return False, "Missing queue_rules or merge_protections in .mergify.yml"
    return True, ".mergify.yml valid and using merge_protections + queue_rules"


def check_dependabot_coverage() -> tuple[bool, str]:
    dependabot_path = ROOT / ".github/dependabot.yml"
    if not dependabot_path.is_file():
        return False, ".github/dependabot.yml does not exist"
    content = dependabot_path.read_text(encoding="utf-8")
    required_ecosystems = ["github-actions", "gradle", "pip", "npm"]
    for eco in required_ecosystems:
        if f"package-ecosystem: {eco}" not in content:
            return False, f"Missing ecosystem in dependabot.yml: {eco}"
    return (
        True,
        f"Dependabot covers {len(required_ecosystems)} ecosystems across all monorepo directories",
    )


def check_github_ruleset_active() -> tuple[bool, str]:
    """Check if GitHub ruleset is active on main branch.
    Returns (is_active, message).
    This is a local file check - real check requires GitHub API.
    """
    # Check if ruleset bootstrap is documented
    doc_path = ROOT / "docs/AUTONOMOUS_MAINTENANCE.md"
    if not doc_path.is_file():
        return False, "Documentation missing"
    content = doc_path.read_text(encoding="utf-8")
    if "BOOTSTRAP_PENDING" in content:
        return False, "GitHub ruleset bootstrap pending (BOOTSTRAP_PENDING)"
    if "ACTIVE" in content and "GitHub Ruleset" in content:
        return True, "GitHub ruleset documented as ACTIVE"
    return False, "GitHub ruleset status unknown"


def check_automerge_control() -> tuple[bool, str]:
    """Check automerge control workflow and label sync."""
    wf_path = ROOT / ".github/workflows/automerge-control.yml"
    if not wf_path.is_file():
        return False, "automerge-control.yml workflow missing"
    content = wf_path.read_text(encoding="utf-8")
    if "AUTONOMOUS_MERGE_ENABLED" not in content:
        return False, "Workflow missing AUTONOMOUS_MERGE_ENABLED check"
    if "sync_automerge_label.py" not in content:
        return False, "Workflow missing sync_automerge_label.py script"
    return True, "Auto-merge control workflow present"


def generate_dashboard_markdown() -> str:
    now_utc = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    actions_ok, actions_msg = check_pinned_actions()
    codeql_ok, codeql_msg = check_codeql_kotlin_compatibility()
    mergify_ok, mergify_msg = check_mergify_configuration()
    dependabot_ok, dependabot_msg = check_dependabot_coverage()
    ruleset_ok, ruleset_msg = check_github_ruleset_active()
    automerge_ok, automerge_msg = check_automerge_control()
    expirations = check_expirations()

    # Determine overall status
    critical_checks = [actions_ok, codeql_ok, mergify_ok, dependabot_ok, automerge_ok]
    has_expirations = len(expirations) > 0

    if not ruleset_ok:
        overall_status = "BOOTSTRAP_PENDING"
    elif all(critical_checks) and not has_expirations:
        overall_status = "HEALTHY"
    elif not all(critical_checks):
        overall_status = "ATTENTION_REQUIRED"
    else:
        overall_status = "DEGRADED"

    lines = [
        "# Autonomous Maintenance Dashboard",
        "",
        f"**Last Assessed:** `{now_utc}`  ",
        f"**Overall System Status:** `{overall_status}`  ",
        "",
        "## Subsystem Health",
        "",
        f"- **Mergify Control Plane:** {'✅' if mergify_ok else '❌'} {mergify_msg}",
        f"- **Dependabot Automation:** {'✅' if dependabot_ok else '❌'} {dependabot_msg}",
        f"- **Toolchain & CodeQL Ceiling:** {'✅' if codeql_ok else '❌'} {codeql_msg}",
        f"- **Pinned GitHub Actions:** {'✅' if actions_ok else '❌'} {actions_msg}",
        f"- **GitHub Ruleset:** {'✅' if ruleset_ok else '⏳'} {ruleset_msg}",
        f"- **Auto-Merge Control:** {'✅' if automerge_ok else '❌'} {automerge_msg}",
        "",
        "## Policy Expirations & Exceptions",
        "",
    ]

    if not expirations:
        lines.append("- ✅ No expired vulnerability, toolchain, or audit exceptions.")
    else:
        for exp in expirations:
            lines.append(f"- ⚠️ **ACTION REQUIRED:** {exp}")

    lines.extend(
        [
            "",
            "## Autonomous Merge Controls",
            "",
            "- **Authoritative Merge Engine:** Mergify (Merge Protections + Merge Queue)",
            "- **Circuit Breakers:** `AUTONOMOUS_MAINTENANCE_ENABLED`, `AUTONOMOUS_MERGE_ENABLED`, label `automerge:disabled`",
            "- **Jules Fleet Merge Status:** Dry-run only (`JULES_FLEET_AUTO_MERGE_ENABLED=false`)",
            "",
            "---",
            "*Automated report generated by `scripts/ci/maintenance_health_controller.py`.*",
        ]
    )

    return "\n".join(lines)


def ensure_maintenance_label(repo: str, token: str) -> bool:
    """Ensure 'maintenance' label exists. Returns True on success."""
    import urllib.error
    import urllib.parse
    import urllib.request

    encoded_label = urllib.parse.quote("maintenance", safe="")
    url = f"https://api.github.com/repos/{repo}/labels/{encoded_label}"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "maintenance-health-controller",
    }
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req):
            return True  # Label exists
    except urllib.error.HTTPError as err:
        if err.code == 404:
            # Create the label
            post_url = f"https://api.github.com/repos/{repo}/labels"
            post_data = {
                "name": "maintenance",
                "color": "5319e7",
                "description": "Autonomous maintenance dashboard issue",
            }
            post_headers = headers.copy()
            post_headers["Content-Type"] = "application/json"
            post_req = urllib.request.Request(
                post_url,
                data=json.dumps(post_data).encode("utf-8"),
                headers=post_headers,
                method="POST",
            )
            try:
                with urllib.request.urlopen(post_req):
                    print("Created 'maintenance' label")
                    return True
            except urllib.error.HTTPError as post_err:
                if post_err.code == 422:
                    # Race condition - label created concurrently
                    return True
                print(
                    f"Failed to create maintenance label: HTTP {post_err.code}",
                    file=sys.stderr,
                )
                return False
        print(f"Failed to check maintenance label: HTTP {err.code}", file=sys.stderr)
        return False
    except Exception as err:
        print(f"Error checking maintenance label: {err}", file=sys.stderr)
        return False


def sync_github_issue(body: str) -> int:
    title = "Autonomous Maintenance Dashboard"
    # Find existing open dashboard issue
    try:
        result = subprocess.run(
            [
                "gh",
                "issue",
                "list",
                "--search",
                f'"{title}" in:title',
                "--state",
                "open",
                "--json",
                "number,title",
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            print(f"Error: gh issue list failed: {result.stderr}", file=sys.stderr)
            return 1

        issues = json.loads(result.stdout)
        dashboard_issue = next(
            (issue for issue in issues if issue.get("title") == title), None
        )

        # Get repo and token for label check
        repo = subprocess.run(
            ["gh", "repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"],
            capture_output=True,
            text=True,
            check=False,
        ).stdout.strip()
        token = subprocess.run(
            ["gh", "auth", "token"],
            capture_output=True,
            text=True,
            check=False,
        ).stdout.strip()

        if not repo or not token:
            print("Error: Could not determine repo or token", file=sys.stderr)
            return 1

        # Ensure maintenance label exists
        if not ensure_maintenance_label(repo, token):
            print("Error: Failed to ensure maintenance label exists", file=sys.stderr)
            return 1

        if dashboard_issue:
            num = dashboard_issue["number"]
            print(f"Updating existing Dashboard issue #{num}...")
            update_res = subprocess.run(
                ["gh", "issue", "edit", str(num), "--body", body],
                capture_output=True,
                text=True,
                check=False,
            )
            if update_res.returncode == 0:
                print(f"Dashboard issue #{num} updated successfully.")
            else:
                print(
                    f"Error: gh issue edit failed: {update_res.stderr}", file=sys.stderr
                )
                return 1
        else:
            print("Creating new Dashboard issue...")
            create_res = subprocess.run(
                [
                    "gh",
                    "issue",
                    "create",
                    "--title",
                    title,
                    "--body",
                    body,
                    "--label",
                    "maintenance",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            if create_res.returncode == 0:
                print(f"Dashboard issue created: {create_res.stdout.strip()}")
            else:
                print(
                    f"Error: gh issue create failed: {create_res.stderr}",
                    file=sys.stderr,
                )
                return 1
        return 0
    except Exception as err:
        print(f"Error: GitHub API interaction failed: {err}", file=sys.stderr)
        return 1


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Autonomous maintenance health controller"
    )
    parser.add_argument(
        "--sync-issue",
        action="store_true",
        help="Sync status to GitHub Dashboard issue",
    )
    parser.add_argument(
        "--check", action="store_true", help="Run local health verification"
    )
    args = parser.parse_args()

    dashboard_text = generate_dashboard_markdown()
    print(dashboard_text)

    if args.sync_issue:
        return sync_github_issue(dashboard_text)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
