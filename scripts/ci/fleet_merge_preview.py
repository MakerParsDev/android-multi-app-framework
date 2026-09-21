#!/usr/bin/env python3
"""
Read-only preview of Jules Fleet PRs that are ready for Mergify evaluation.

This repository intentionally uses Mergify as the sole automated merge
authority. The script never mutates pull requests and never invokes
@google/jules-fleet merge.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from typing import Any

from jules_provenance import has_verified_jules_session_provenance


def _make_request(url: str, token: str) -> tuple[int, Any]:
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "jules-fleet-merge-preview",
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


def fetch_open_prs(repo: str, token: str) -> list[dict[str, Any]]:
    prs: list[dict[str, Any]] = []
    page = 1
    while True:
        url = (
            f"https://api.github.com/repos/{repo}/pulls"
            f"?state=open&per_page=100&page={page}"
        )
        status, data = _make_request(url, token)
        if status != 200 or not isinstance(data, list):
            raise RuntimeError(
                f"Failed to list open pull requests for {repo}: "
                f"HTTP {status} {data}"
            )
        prs.extend(item for item in data if isinstance(item, dict))
        if len(data) < 100:
            return prs
        page += 1


def _label_names(pr: dict[str, Any]) -> set[str]:
    return {
        str(label.get("name"))
        for label in pr.get("labels", [])
        if isinstance(label, dict) and label.get("name")
    }


def is_fleet_ready_for_mergify(pr: dict[str, Any], repo: str) -> bool:
    labels = _label_names(pr)
    head = pr.get("head") if isinstance(pr.get("head"), dict) else {}
    head_repo = head.get("repo") if isinstance(head.get("repo"), dict) else {}
    head_ref = str(head.get("ref") or "")
    body = str(pr.get("body") or "")

    return (
        head_repo.get("full_name") == repo
        and has_verified_jules_session_provenance(head_ref, body)
        and "fleet-merge-ready" in labels
        and "risk:low" in labels
        and "fleet-review-required" not in labels
    )


def preview_ready_prs(repo: str, token: str) -> list[dict[str, Any]]:
    ready: list[dict[str, Any]] = []
    for pr in fetch_open_prs(repo, token):
        if not is_fleet_ready_for_mergify(pr, repo):
            continue
        ready.append(
            {
                "number": int(pr["number"]),
                "title": str(pr.get("title") or ""),
                "head": str((pr.get("head") or {}).get("ref") or ""),
                "url": str(pr.get("html_url") or ""),
            }
        )
    return ready


def write_github_output(name: str, value: str) -> None:
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as handle:
            handle.write(f"{name}={value}\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN"))
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
        ready = preview_ready_prs(args.repo, args.token)
    except RuntimeError as err:
        print(f"Error: {err}", file=sys.stderr)
        return 1

    print(
        f"Fleet merge audit: {len(ready)} PR(s) ready for Mergify evaluation. "
        "No merge action was performed."
    )
    for pr in ready:
        print(
            f"  READY #{pr['number']}: {pr['title']} "
            f"(head={pr['head']}, url={pr['url']})"
        )

    write_github_output("ready_count", str(len(ready)))
    write_github_output(
        "ready_numbers", ",".join(str(pr["number"]) for pr in ready)
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
