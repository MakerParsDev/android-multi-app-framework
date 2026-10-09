#!/usr/bin/env python3
"""Prevent scanning GitHub's dependency graph before the matching Gradle submission.

Only a commit touching paths covered by dependency-submission.yml must wait.
Documentation-only commits intentionally do not launch that workflow.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from urllib.parse import urlencode
from urllib.request import Request, urlopen

REPOSITORY_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
WORKFLOW = "dependency-submission.yml"
API_VERSION = "2026-03-10"


class SubmissionError(RuntimeError):
    """An exact-source Gradle dependency graph cannot be trusted yet."""


def gradle_affected(paths: list[str]) -> bool:
    """Mirror dependency-submission.yml's push.path filters."""
    return any(
        path.endswith((".gradle", ".gradle.kts"))
        or path.startswith(("gradle/", "buildSrc/"))
        or path in {"gradle.properties", "settings.gradle.kts"}
        for path in paths
    )


def changed_paths() -> list[str]:
    """Workflow checkout must have fetch-depth >= 2 for a first-parent diff."""
    try:
        result = subprocess.run(
            ["git", "diff", "--name-only", "-z", "HEAD^", "HEAD"],
            capture_output=True, check=True,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        raise SubmissionError("Cannot verify changed paths for current commit") from error
    return [
        entry.decode("utf-8")
        for entry in result.stdout.split(b"\0")
        if entry
    ]


def workflow_runs(repo: str, sha: str, token: str) -> list[dict]:
    query = urlencode({"head_sha": sha, "branch": "main", "per_page": 50})
    url = f"https://api.github.com/repos/{repo}/actions/workflows/{WORKFLOW}/runs?{query}"
    request = Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": API_VERSION,
            "User-Agent": "android-maintenance-dependency-submission-gate",
        },
    )
    try:
        with urlopen(request, timeout=15) as response:
            payload = json.load(response)
    except (OSError, ValueError) as error:
        raise SubmissionError("GitHub dependency submission status API unavailable") from error
    runs = payload.get("workflow_runs") if isinstance(payload, dict) else None
    if not isinstance(runs, list) or not all(isinstance(x, dict) for x in runs):
        raise SubmissionError("Invalid GitHub dependency submission response")
    return runs


def classify_runs(runs: list[dict], sha: str) -> tuple[str, str]:
    matching = [
        run for run in runs
        if run.get("head_sha") == sha
        and run.get("head_branch") == "main"
        and run.get("event") in {"push", "workflow_dispatch"}
    ]
    if not matching:
        return "wait", "No matching Gradle submission run yet"
    # The most recent run attempt supersedes older, potentially stale results.
    latest = max(
        matching,
        key=lambda run: (run.get("created_at") or "", int(run.get("id") or 0)),
    )
    status = latest.get("status")
    conclusion = latest.get("conclusion")
    run_id = latest.get("id")
    if status == "completed":
        if conclusion == "success":
            return "ready", f"Dependency Submission run {run_id} succeeded"
        return "failed", f"Dependency Submission run {run_id} finished: {conclusion}"
    if status in {"queued", "in_progress", "waiting", "pending", "requested"}:
        return "wait", f"Dependency Submission run {run_id} still {status}"
    return "failed", f"Dependency Submission run {run_id} has unexpected state: {status}"


def await_submission(
    repo: str,
    sha: str,
    token: str,
    *,
    timeout_seconds: float = 480,
    poll_seconds: float = 10,
) -> str:
    if not REPOSITORY_RE.fullmatch(repo) or not SHA_RE.fullmatch(sha) or not token:
        raise SubmissionError("Missing/invalid repository, exact commit SHA or token")
    if timeout_seconds <= 0 or poll_seconds <= 0:
        raise SubmissionError("Polling timeout and interval must be positive")

    deadline = time.monotonic() + timeout_seconds
    while True:
        state, reason = classify_runs(workflow_runs(repo, sha, token), sha)
        if state == "ready":
            return reason
        if state == "failed":
            raise SubmissionError(reason)
        if time.monotonic() >= deadline:
            raise SubmissionError(f"Timed out waiting for exact-source submission: {reason}")
        print(f"Waiting for GitHub dependency graph: {reason}", flush=True)
        time.sleep(min(poll_seconds, max(0, deadline - time.monotonic())))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--sha", default=os.environ.get("GITHUB_SHA", ""))
    parser.add_argument("--timeout-seconds", type=float, default=480)
    args = parser.parse_args()
    try:
        if os.environ.get("GITHUB_REF", "refs/heads/main") != "refs/heads/main":
            raise SubmissionError("Maintenance dependency scan must target main")
        if not gradle_affected(changed_paths()):
            print("No Gradle dependency submission needed for this commit")
            return 0
        print(await_submission(
            args.repo,
            args.sha,
            os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN", ""),
            timeout_seconds=args.timeout_seconds,
        ))
        return 0
    except SubmissionError as error:
        print(f"::error::Dependency graph is not ready: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
