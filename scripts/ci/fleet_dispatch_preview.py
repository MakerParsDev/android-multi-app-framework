#!/usr/bin/env python3
"""
Read-only Jules Fleet dispatch preview.

Reproduces the dispatch eligibility checks needed for a safe manual preview
without invoking @google/jules-fleet or creating Jules sessions. The pinned
Fleet CLI's --dry-run behavior is not trusted as a safety boundary.

A candidate must be:
- an open GitHub issue (not a pull request) in the requested milestone,
- labeled "fleet",
- not labeled "status: ignore",
- without a prior Fleet Dispatch Event comment, and
- without an open PR whose body references the issue.

This script performs GET requests only.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

DISPATCH_MARKER = "🤖 **Fleet Dispatch Event**"


def _make_request(url: str, token: str) -> tuple[int, Any]:
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "jules-fleet-dispatch-preview",
    }
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, json.loads(body) if body else None
    except urllib.error.HTTPError as err:
        error_body = err.read().decode("utf-8") if err.fp else ""
        try:
            parsed_err = json.loads(error_body)
        except Exception:
            parsed_err = error_body
        return err.code, parsed_err


def _paged_get(base_url: str, token: str) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    page = 1
    separator = "&" if "?" in base_url else "?"
    while True:
        url = f"{base_url}{separator}per_page=100&page={page}"
        status, data = _make_request(url, token)
        if status != 200 or not isinstance(data, list):
            raise RuntimeError(f"GitHub API request failed: HTTP {status} {data}")
        page_items = [item for item in data if isinstance(item, dict)]
        items.extend(page_items)
        if len(data) < 100:
            return items
        page += 1


def _label_names(issue: dict[str, Any]) -> set[str]:
    labels: set[str] = set()
    for label in issue.get("labels", []):
        if isinstance(label, str):
            labels.add(label)
        elif isinstance(label, dict) and label.get("name"):
            labels.add(str(label["name"]))
    return labels


def is_target_fleet_issue(issue: dict[str, Any]) -> bool:
    if "pull_request" in issue:
        return False
    labels = _label_names(issue)
    return "fleet" in labels and "status: ignore" not in labels


def linked_pr_numbers(issue_number: int, open_prs: list[dict[str, Any]]) -> list[int]:
    needles = (f"#{issue_number}", f"Fixes #{issue_number}", f"Closes #{issue_number}")
    linked: list[int] = []
    for pr in open_prs:
        body = str(pr.get("body") or "")
        if any(needle in body for needle in needles):
            number = pr.get("number")
            if isinstance(number, int):
                linked.append(number)
    return linked


def has_dispatch_event(comments: list[dict[str, Any]]) -> bool:
    return any(
        DISPATCH_MARKER in str(comment.get("body") or "")
        for comment in comments
        if isinstance(comment, dict)
    )


def fetch_open_fleet_issues(
    repo: str, milestone: int, token: str
) -> list[dict[str, Any]]:
    encoded_labels = urllib.parse.quote("fleet", safe="")
    url = (
        f"https://api.github.com/repos/{repo}/issues"
        f"?state=open&milestone={milestone}&labels={encoded_labels}"
    )
    return [issue for issue in _paged_get(url, token) if is_target_fleet_issue(issue)]


def fetch_open_prs(repo: str, token: str) -> list[dict[str, Any]]:
    return _paged_get(
        f"https://api.github.com/repos/{repo}/pulls?state=open",
        token,
    )


def fetch_issue_comments(
    repo: str, issue_number: int, token: str
) -> list[dict[str, Any]]:
    return _paged_get(
        f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments",
        token,
    )


def preview_candidates(
    repo: str,
    milestone: int,
    token: str,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    issues = fetch_open_fleet_issues(repo, milestone, token)
    open_prs = fetch_open_prs(repo, token)
    candidates: list[dict[str, Any]] = []
    skipped: list[dict[str, Any]] = []

    for issue in issues:
        number = int(issue["number"])
        comments = fetch_issue_comments(repo, number, token)
        if has_dispatch_event(comments):
            skipped.append(
                {"number": number, "title": issue.get("title", ""), "reason": "already dispatched"}
            )
            continue

        linked = linked_pr_numbers(number, open_prs)
        if linked:
            skipped.append(
                {
                    "number": number,
                    "title": issue.get("title", ""),
                    "reason": f"open linked PR(s): {','.join(str(n) for n in linked)}",
                }
            )
            continue

        candidates.append(
            {"number": number, "title": issue.get("title", "")}
        )

    return candidates, skipped


def write_github_output(name: str, value: str) -> None:
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as handle:
            handle.write(f"{name}={value}\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN"))
    parser.add_argument("--milestone", type=int, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.repo:
        print("Error: repository is required.", file=sys.stderr)
        return 1
    if not args.token:
        print("Error: GitHub token is required.", file=sys.stderr)
        return 1

    try:
        candidates, skipped = preview_candidates(
            args.repo, args.milestone, args.token
        )
    except RuntimeError as err:
        print(f"Error: {err}", file=sys.stderr)
        return 1

    print(
        f"Fleet dispatch preview for milestone {args.milestone}: "
        f"{len(candidates)} candidate(s), {len(skipped)} skipped."
    )
    for item in candidates:
        print(f"  CANDIDATE #{item['number']}: {item['title']}")
    for item in skipped:
        print(f"  SKIP #{item['number']}: {item['title']} ({item['reason']})")

    write_github_output("candidate_count", str(len(candidates)))
    write_github_output(
        "candidate_numbers",
        ",".join(str(item["number"]) for item in candidates),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
