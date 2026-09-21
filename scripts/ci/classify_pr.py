#!/usr/bin/env python3
"""
PR Risk Classifier and Label Synchronizer for Jules Fleet.

Fetches ALL changed files from a GitHub Pull Request using pagination,
evaluates risk using fleet_pr_risk.py, and synchronizes risk/Fleet labels.
Fleet readiness requires same-repository Jules session provenance plus a closing
issue labeled 'fleet'. Low-risk non-Fleet PRs never receive fleet-merge-ready.
Fails closed if metadata is unavailable, the file list is empty, or labels fail.
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

from fleet_pr_risk import classify_changed_files

# Jules Fleet 0.0.1-experimental.35 emits numeric Jules session IDs and its
# conflict resolver extracts them from branch names ending in -<10+ digits>.
# Retain s-* support for Fleet metadata produced by older SDK paths.
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


def fetch_pr_provenance(repo: str, pr_number: int, token: str) -> dict[str, Any]:
    """Fetch trusted PR provenance and closing-issue labels via GitHub GraphQL."""
    try:
        owner, name = repo.split("/", 1)
    except ValueError as err:
        raise RuntimeError(f"Invalid repository name {repo!r}") from err

    query = """
    query($owner: String!, $name: String!, $number: Int!) {
      repository(owner: $owner, name: $name) {
        pullRequest(number: $number) {
          headRefName
          headRepository { nameWithOwner }
          body
          closingIssuesReferences(first: 20) {
            nodes {
              number
              labels(first: 100) { nodes { name } }
            }
          }
        }
      }
    }
    """
    status, payload = _make_request(
        "https://api.github.com/graphql",
        token,
        method="POST",
        data={
            "query": query,
            "variables": {"owner": owner, "name": name, "number": pr_number},
        },
    )
    if status != 200 or not isinstance(payload, dict) or payload.get("errors"):
        raise RuntimeError(
            f"Failed to fetch PR #{pr_number} provenance: HTTP {status} {payload}"
        )

    repository = payload.get("data", {}).get("repository")
    pr = repository.get("pullRequest") if isinstance(repository, dict) else None
    if not isinstance(pr, dict):
        raise RuntimeError(f"PR #{pr_number} provenance response is incomplete")
    return pr


def _has_jules_session_marker(head_ref: str, body: str) -> bool:
    if head_ref.startswith("jules/"):
        last_segment = head_ref.rsplit("/", 1)[-1]
        if re.fullmatch(JULES_SESSION_ID_PATTERN, last_segment):
            return True
        if re.search(rf"-{JULES_SESSION_ID_PATTERN}$", head_ref):
            return True

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


def is_verified_fleet_pr(repo: str, provenance: dict[str, Any]) -> bool:
    """Require same-repo Jules provenance plus a closing issue labeled fleet."""
    head_repo = provenance.get("headRepository")
    head_repo_name = (
        head_repo.get("nameWithOwner") if isinstance(head_repo, dict) else None
    )
    if head_repo_name != repo:
        return False

    head_ref = str(provenance.get("headRefName") or "")
    body = str(provenance.get("body") or "")
    if not head_ref.startswith("jules/") or not _has_jules_session_marker(head_ref, body):
        return False

    refs = provenance.get("closingIssuesReferences")
    nodes = refs.get("nodes", []) if isinstance(refs, dict) else []
    for issue in nodes:
        if not isinstance(issue, dict):
            continue
        labels = issue.get("labels")
        label_nodes = labels.get("nodes", []) if isinstance(labels, dict) else []
        if any(
            isinstance(label, dict) and label.get("name") == "fleet"
            for label in label_nodes
        ):
            return True
    return False


def update_pr_labels(
    repo: str,
    pr_number: int,
    token: str,
    risk: str,
    fleet_verified: bool = False,
) -> None:
    if risk == "LOW_RISK":
        to_add = ["risk:low"]
        to_remove = ["risk:protected", "fleet-review-required"]
        if fleet_verified:
            to_add.insert(0, "fleet-merge-ready")
        else:
            to_remove.append("fleet-merge-ready")
    else:
        to_add = ["risk:protected"]
        to_remove = ["fleet-merge-ready", "risk:low"]
        if fleet_verified:
            to_add.insert(0, "fleet-review-required")
        else:
            to_remove.append("fleet-review-required")

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

    provenance = fetch_pr_provenance(args.repo, args.pr_number, args.token)
    fleet_verified = is_verified_fleet_pr(args.repo, provenance)
    print(f"Verified Jules Fleet provenance: {fleet_verified}")

    risk = classify_changed_files(changed_files)
    print(f"Evaluated risk classification: {risk}")
    write_github_output("RISK_LEVEL", risk)
    write_github_output("FLEET_VERIFIED", str(fleet_verified).lower())

    update_pr_labels(
        args.repo,
        args.pr_number,
        args.token,
        risk,
        fleet_verified=fleet_verified,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
