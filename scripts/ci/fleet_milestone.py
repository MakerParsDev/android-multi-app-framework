#!/usr/bin/env python3
"""
Deterministic Milestone and Label Management for Jules Fleet.

Idempotently ensures that the Jules Fleet milestone ("Jules Fleet Maintenance")
and required labels exist on the repository, exposing the resolved milestone
number via GITHUB_OUTPUT.
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

DEFAULT_MILESTONE_TITLE = "Jules Fleet Maintenance"
DEFAULT_MILESTONE_DESCRIPTION = "Autonomous maintenance issues managed by Jules Fleet"

REQUIRED_FLEET_LABELS = [
    {
        "name": "fleet",
        "color": "1d76db",
        "description": "Fleet-managed issue",
    },
    {
        "name": "fleet-merge-ready",
        "color": "0e8a16",
        "description": "Ready for fleet sequential merge",
    },
    {
        "name": "fleet-review-required",
        "color": "d93f0b",
        "description": "Protected or ambiguous changes require human review",
    },
]


def _make_request(
    url: str,
    token: str,
    method: str = "GET",
    data: dict[str, Any] | None = None,
) -> tuple[int, Any]:
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "jules-fleet-automation",
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


def resolve_open_milestone(
    repo: str,
    token: str,
    title: str = DEFAULT_MILESTONE_TITLE,
) -> int | None:
    page = 1
    while True:
        url = f"https://api.github.com/repos/{repo}/milestones?state=open&per_page=100&page={page}"
        status, data = _make_request(url, token)
        if status != 200 or not isinstance(data, list):
            raise RuntimeError(f"Failed to list milestones for {repo}: HTTP {status} {data}")
        for m in data:
            if m.get("title") == title:
                return int(m["number"])
        if len(data) < 100:
            break
        page += 1
    return None


def ensure_milestone(
    repo: str,
    token: str,
    title: str = DEFAULT_MILESTONE_TITLE,
    description: str = DEFAULT_MILESTONE_DESCRIPTION,
) -> int:
    existing_num = resolve_open_milestone(repo, token, title)
    if existing_num is not None:
        return existing_num

    url = f"https://api.github.com/repos/{repo}/milestones"
    status, data = _make_request(
        url,
        token,
        method="POST",
        data={"title": title, "description": description, "state": "open"},
    )
    if status == 201 and isinstance(data, dict) and "number" in data:
        return int(data["number"])

    if status == 422:
        # Race condition or exists in closed state; resolve again across all states
        existing_any = resolve_open_milestone(repo, token, title)
        if existing_any is not None:
            return existing_any

    raise RuntimeError(f"Failed to create milestone '{title}' for {repo}: HTTP {status} {data}")


def ensure_labels(repo: str, token: str) -> None:
    for label_spec in REQUIRED_FLEET_LABELS:
        name = label_spec["name"]
        encoded_name = urllib.parse.quote(name, safe="")
        get_url = f"https://api.github.com/repos/{repo}/labels/{encoded_name}"
        status, _ = _make_request(get_url, token)
        if status == 200:
            continue
        if status == 404:
            post_url = f"https://api.github.com/repos/{repo}/labels"
            post_status, post_data = _make_request(
                post_url,
                token,
                method="POST",
                data=label_spec,
            )
            if post_status == 201:
                print(f"Created label: {name}")
                continue
            if post_status == 422:
                # Label was created concurrently
                continue
            raise RuntimeError(f"Failed to create label '{name}': HTTP {post_status} {post_data}")
        raise RuntimeError(f"Failed to inspect label '{name}': HTTP {status}")


def write_github_output(name: str, value: str) -> None:
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a", encoding="utf-8") as out:
            out.write(f"{name}={value}\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN"))
    parser.add_argument("--title", default=DEFAULT_MILESTONE_TITLE)
    parser.add_argument("--ensure", action="store_true", help="Ensure milestone exists, creating if missing")
    parser.add_argument("--resolve", action="store_true", help="Resolve milestone number without creating")
    parser.add_argument("--ensure-labels", action="store_true", help="Ensure required Fleet labels exist")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.repo:
        print("Error: Repository not specified (set GITHUB_REPOSITORY or --repo).", file=sys.stderr)
        return 1
    if not args.token:
        print("Error: GitHub token not specified (set GITHUB_TOKEN or --token).", file=sys.stderr)
        return 1

    if args.ensure_labels:
        ensure_labels(args.repo, args.token)
        print("Required Fleet labels verified.")

    milestone_number = None
    if args.ensure:
        milestone_number = ensure_milestone(args.repo, args.token, title=args.title)
        print(f"Milestone '{args.title}' ensured with number: {milestone_number}")
    elif args.resolve:
        milestone_number = resolve_open_milestone(args.repo, args.token, title=args.title)
        if milestone_number is None:
            print(f"Error: Milestone '{args.title}' not found in {args.repo}.", file=sys.stderr)
            return 1
        print(f"Milestone '{args.title}' resolved to number: {milestone_number}")

    if milestone_number is not None:
        write_github_output("number", str(milestone_number))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
