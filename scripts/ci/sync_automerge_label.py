#!/usr/bin/env python3
"""
Sync Auto-Merge Authorization Label.

Reads AUTONOMOUS_MERGE_ENABLED repository variable and synchronizes the
'automerge:enabled' label only for explicitly authorized PR classes.

Eligible positive-authorization classes are Dependabot PRs and verified-shape
Jules Fleet PRs already marked fleet-merge-ready + risk:low. Fleet candidates
must be same-repository jules/* branches with a Jules session marker. Explicit
hold/disable labels and drafts fail closed. Mergify remains the final
package/path/check policy engine.

Fail-closed: if the variable is missing, not 'true', the PR is not an eligible
class, or API access fails, positive authorization is not granted.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

# Jules Fleet 0.0.1-experimental.35 currently uses numeric session IDs and
# branch names ending in -<10+ digits>; older Fleet metadata may use s-* IDs.
JULES_SESSION_ID_PATTERN = r"(?:s-[A-Za-z0-9][A-Za-z0-9._-]*|[0-9]{10,})"


def _make_request(
    url: str,
    token: str,
    method: str = "GET",
    data: dict[str, Any] | None = None,
) -> tuple[int, Any]:
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "automerge-control",
    }
    encoded_data = None
    if data is not None:
        encoded_data = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=encoded_data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            status = resp.status
            body = resp.read().decode("utf-8")
            return status, json.loads(body) if body else None
    except urllib.error.HTTPError as err:
        error_body = err.read().decode("utf-8") if err.fp else ""
        try:
            parsed_err = json.loads(error_body)
        except Exception:
            parsed_err = error_body
        return err.code, parsed_err


def get_open_prs(repo: str, token: str) -> list[dict[str, Any]]:
    """Fetch all open PRs for the repository."""
    all_prs: list[dict[str, Any]] = []
    page = 1

    while True:
        url = f"https://api.github.com/repos/{repo}/pulls?state=open&per_page=100&page={page}"
        status, data = _make_request(url, token)
        if status != 200 or not isinstance(data, list):
            raise RuntimeError(
                f"Failed to fetch open PRs for {repo}: HTTP {status} {data}"
            )

        all_prs.extend(data)

        if len(data) < 100:
            break
        page += 1

    return all_prs


def ensure_label_on_pr(repo: str, pr_number: int, token: str, label: str) -> None:
    """Add label to PR. Fails on any error except 404 on removal."""
    url_add = f"https://api.github.com/repos/{repo}/issues/{pr_number}/labels"
    status_add, data_add = _make_request(
        url_add,
        token,
        method="POST",
        data={"labels": [label]},
    )
    if status_add not in (200, 201):
        raise RuntimeError(
            f"Failed to add label '{label}' to PR #{pr_number}: HTTP {status_add} {data_add}"
        )
    print(f"Applied label '{label}' to PR #{pr_number}")


def remove_label_from_pr(repo: str, pr_number: int, token: str, label: str) -> None:
    """Remove label from PR. 404 is allowed (label wasn't present)."""
    encoded_label = urllib.parse.quote(label, safe="")
    url_del = (
        f"https://api.github.com/repos/{repo}/issues/{pr_number}/labels/{encoded_label}"
    )
    status_del, data_del = _make_request(url_del, token, method="DELETE")
    if status_del in (200, 204):
        print(f"Removed label '{label}' from PR #{pr_number}")
    elif status_del == 404:
        pass
    else:
        raise RuntimeError(
            f"Failed to remove label '{label}' from PR #{pr_number}: HTTP {status_del} {data_del}"
        )


def _label_names(pr: dict[str, Any]) -> set[str]:
    return {
        str(label.get("name"))
        for label in pr.get("labels", [])
        if isinstance(label, dict) and label.get("name")
    }


def _has_verified_jules_shape(pr: dict[str, Any], repo: str) -> bool:
    """Require a same-repository Jules branch with a session provenance marker."""
    head = pr.get("head") if isinstance(pr.get("head"), dict) else {}
    head_repo = head.get("repo") if isinstance(head.get("repo"), dict) else {}
    if head_repo.get("full_name") != repo:
        return False

    head_ref = str(head.get("ref") or "")
    if not head_ref.startswith("jules/"):
        return False

    last_segment = head_ref.rsplit("/", 1)[-1]
    if re.fullmatch(JULES_SESSION_ID_PATTERN, last_segment):
        return True
    if re.search(rf"-{JULES_SESSION_ID_PATTERN}$", head_ref):
        return True

    body = str(pr.get("body") or "")
    if re.search(
        rf"https://jules\.google\.com/session/{JULES_SESSION_ID_PATTERN}(?=$|[/?#\s)\]])",
        body,
    ):
        return True

    return bool(
        re.search(
            rf"(?:source\s*[:=]\s*|source:\s*)jules:session:{JULES_SESSION_ID_PATTERN}(?=$|\s)",
            body,
            flags=re.IGNORECASE,
        )
    )


def is_positive_authorization_candidate(pr: dict[str, Any], repo: str) -> bool:
    """Return True only for PR classes allowed to receive automerge:enabled.

    Mergify still enforces the final dependency-type/package/path/check policy.
    This controller deliberately avoids placing a positive authorization label
    on unrelated human PRs or PRs carrying an explicit hold/disable signal.
    """
    labels = _label_names(pr)
    if labels.intersection({"automerge:disabled", "hold", "do-not-merge"}):
        return False
    if bool(pr.get("draft")):
        return False

    user = pr.get("user") if isinstance(pr.get("user"), dict) else {}
    login = str(user.get("login") or "")
    if login in {"dependabot[bot]", "app/dependabot"}:
        return True

    return (
        "fleet-merge-ready" in labels
        and "risk:low" in labels
        and "fleet-review-required" not in labels
        and _has_verified_jules_shape(pr, repo)
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Sync automerge:enabled label based on AUTONOMOUS_MERGE_ENABLED variable"
    )
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN"))
    parser.add_argument("--enabled", default=os.environ.get("AUTONOMOUS_MERGE_ENABLED"))
    args, _ = parser.parse_known_args()

    if not args.repo:
        print(
            "Error: Repository not specified (set GITHUB_REPOSITORY or --repo).",
            file=sys.stderr,
        )
        return 1
    if not args.token:
        print(
            "Error: GitHub token not specified (set GITHUB_TOKEN or --token).",
            file=sys.stderr,
        )
        return 1

    # Fail-closed: variable must be explicitly 'true'
    enabled = args.enabled == "true"
    print(
        f"AUTONOMOUS_MERGE_ENABLED = {args.enabled!r} => auto-merge authorized: {enabled}"
    )

    try:
        open_prs = get_open_prs(args.repo, args.token)
        print(f"Found {len(open_prs)} open PR(s)")

        failures: list[tuple[int, str]] = []
        for pr in open_prs:
            pr_number = pr["number"]
            existing_labels = _label_names(pr)
            candidate = enabled and is_positive_authorization_candidate(pr, args.repo)

            try:
                if candidate:
                    if "automerge:enabled" not in existing_labels:
                        ensure_label_on_pr(
                            args.repo, pr_number, args.token, "automerge:enabled"
                        )
                    else:
                        print(f"PR #{pr_number} already has automerge:enabled")
                else:
                    # Fail-closed removal pass: disabled globally OR not in an
                    # explicitly authorized PR class. Attempt every PR even after
                    # individual API failures so stale positive labels are cleared.
                    if "automerge:enabled" in existing_labels:
                        remove_label_from_pr(
                            args.repo, pr_number, args.token, "automerge:enabled"
                        )
                    else:
                        reason = (
                            "global auto-merge disabled"
                            if not enabled
                            else "PR is not an authorized auto-merge candidate"
                        )
                        print(
                            f"PR #{pr_number} does not have automerge:enabled "
                            f"({reason})"
                        )
            except RuntimeError as err:
                failures.append((pr_number, str(err)))
                print(f"PR #{pr_number} label synchronization failed: {err}", file=sys.stderr)

        if failures:
            failed_numbers = ", ".join(f"#{number}" for number, _ in failures)
            print(
                f"Auto-merge label synchronization failed for {len(failures)} PR(s): {failed_numbers}",
                file=sys.stderr,
            )
            return 1
        return 0
    except Exception as err:
        print(f"Error: {err}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
