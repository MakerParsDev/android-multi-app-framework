#!/usr/bin/env python3
"""
PR Risk Classifier and Label Synchronizer for Jules Fleet.

Fetches ALL changed files from a GitHub Pull Request using pagination,
evaluates risk using fleet_pr_risk.py, and applies or removes fleet labels.
Fails closed if the file list is empty or label assignment fails.
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

from fleet_pr_risk import classify_changed_files


def _make_request(
    url: str,
    token: str,
    method: str = "GET",
    data: dict[str, Any] | None = None,
) -> tuple[int, Any]:
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "jules-fleet-classifier",
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


def fetch_all_pr_files(repo: str, pr_number: int, token: str) -> list[str]:
    all_files: list[str] = []
    page = 1

    while True:
        url = f"https://api.github.com/repos/{repo}/pulls/{pr_number}/files?per_page=100&page={page}"
        status, data = _make_request(url, token)
        if status != 200 or not isinstance(data, list):
            raise RuntimeError(
                f"Failed to fetch PR #{pr_number} files (page {page}): HTTP {status} {data}"
            )

        for item in data:
            if isinstance(item, dict) and "filename" in item:
                all_files.append(item["filename"])

        if len(data) < 100:
            break
        page += 1

    return all_files


def update_pr_labels(
    repo: str,
    pr_number: int,
    token: str,
    risk: str,
) -> None:
    if risk == "LOW_RISK":
        to_add = ["fleet-merge-ready", "risk:low"]
        to_remove = ["fleet-review-required", "risk:protected"]
    else:
        to_add = ["fleet-review-required", "risk:protected"]
        to_remove = ["fleet-merge-ready", "risk:low"]

    # Add new labels (must succeed, failure fails the job)
    url_add = f"https://api.github.com/repos/{repo}/issues/{pr_number}/labels"
    for label in to_add:
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

    # Remove opposite labels (404 is allowed if label was not present, all other errors fail)
    for label in to_remove:
        encoded_remove = urllib.parse.quote(label, safe="")
        url_del = f"https://api.github.com/repos/{repo}/issues/{pr_number}/labels/{encoded_remove}"
        status_del, data_del = _make_request(url_del, token, method="DELETE")
        if status_del in (200, 204):
            print(f"Removed label '{label}' from PR #{pr_number}")
        elif status_del == 404:
            # Expected when label wasn't assigned
            pass
        else:
            raise RuntimeError(
                f"Failed to remove label '{label}' from PR #{pr_number}: HTTP {status_del} {data_del}"
            )


def write_github_output(name: str, value: str) -> None:
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a", encoding="utf-8") as out:
            out.write(f"{name}={value}\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--pr-number", type=int, default=os.environ.get("PR_NUMBER"))
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.repo:
        print(
            "Error: Repository not specified (set GITHUB_REPOSITORY or --repo).",
            file=sys.stderr,
        )
        return 1
    if not args.pr_number:
        print(
            "Error: PR number not specified (set PR_NUMBER or --pr-number).",
            file=sys.stderr,
        )
        return 1
    if not args.token:
        print(
            "Error: GitHub token not specified (set GITHUB_TOKEN or --token).",
            file=sys.stderr,
        )
        return 1

    changed_files = fetch_all_pr_files(args.repo, args.pr_number, args.token)
    print(f"Retrieved {len(changed_files)} changed file(s) for PR #{args.pr_number}")

    risk = classify_changed_files(changed_files)
    print(f"Evaluated risk classification: {risk}")
    write_github_output("RISK_LEVEL", risk)

    update_pr_labels(args.repo, args.pr_number, args.token, risk)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
