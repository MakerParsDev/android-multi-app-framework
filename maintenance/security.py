"""Machine-owned security tracking for active-exception projection and lifecycle."""

import json
from typing import Any, Dict, List
from maintenance.policy import AutonomyPolicy

def render_security_issue_body(policy: AutonomyPolicy) -> str:
    """Renders deterministic body for current policy exceptions based on canonical policy state."""
    active_canaries = policy.active_canaries_and_exceptions

    body = "## Objective\n"
    body += "Track explicit, time-bounded security exceptions. "
    body += "An empty exception list does not prove a clean current vulnerability scan.\n\n"

    body += "## Active Blocked Advisories\n\n"

    if not active_canaries:
        body += "None recorded. Verify current SBOM/OSV advisory results independently.\n\n"

    for entry in active_canaries:
        body += f"### {entry.get('id')} — {entry.get('advisory')} / {entry.get('cve')}\n"
        if entry.get("tracking_issue"):
            body += f"- **Tracking Issue**: {entry['tracking_issue']}\n"
        body += f"- **Owner**: `{entry.get('owner')}`\n"
        body += f"- **Review Expiry**: `{entry.get('review_expires_on')}`\n"
        body += f"- **Rationale**: {entry.get('rationale')}\n\n"

    body += "## Closure Criteria\n"
    body += "- GitHub SPDX + pinned OSV scan reports no instances of these advisory groups, and\n"
    body += "- The corresponding time-bounded exception/policy entries in `config/autonomy-policy.yaml` are removed or updated with new evidence.\n\n"

    body += "## Safety\n"
    body += "Do not suppress these findings globally. Do not weaken required CI/CodeQL/Mergify gates to make an upgrade pass.\n\n"
    body += "---\n"
    body += "*Machine-rendered by `python3 -m maintenance security-issue-sync`.*"

    return body

def evaluate_security_issue_state(policy: AutonomyPolicy) -> Dict[str, Any]:
    """Determines whether an active exception needs review based on remaining active blockers."""
    blockers = policy.active_canaries_and_exceptions
    should_be_open = len(blockers) > 0
    return {
        "should_be_open": should_be_open,
        "active_blocker_count": len(blockers),
        "blocker_ids": [b.get("id") for b in blockers]
    }
