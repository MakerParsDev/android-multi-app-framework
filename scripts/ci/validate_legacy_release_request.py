#!/usr/bin/env python3
"""Enforce non-bypassable publication prerequisites in the legacy release workflow."""

from __future__ import annotations

import json
import os
import re
import sys
from urllib.parse import urlencode
from urllib.request import Request, urlopen

REPO_PATTERN = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
SHA_PATTERN = re.compile(r"^[a-f0-9]{40}$")
REQUIRED_WORKFLOWS = ("ci-main.yml", "codeql.yml")


def boolean(env: dict[str, str], name: str) -> bool:
    value = env.get(name, "").lower()
    if value not in {"true", "false"}:
        raise ValueError(f"{name} must be true or false")
    return value == "true"


def validate_release_inputs(env: dict[str, str]) -> bool:
    do_build = boolean(env, "RELEASE_DO_BUILD")
    do_quality = boolean(env, "RELEASE_DO_QUALITY")
    internal = boolean(env, "RELEASE_DO_INTERNAL_TEST")
    production = boolean(env, "RELEASE_DO_PUBLISH")
    update_listing = boolean(env, "RELEASE_UPDATE_PLAY_LISTING")
    publishing = internal or production

    if internal and production:
        raise ValueError("internal and production publication cannot be selected together")
    if update_listing and not publishing:
        raise ValueError("Play listing changes require an explicit publication track")
    if publishing:
        if not do_build or not do_quality or env.get("RELEASE_BUILD_TYPE") != "Release":
            raise ValueError("publishing requires Release build and mandatory quality checks")
        if env.get("GITHUB_REF") != "refs/heads/main":
            raise ValueError("publishing is allowed only from protected main")
    return publishing


def fetch_runs(repository: str, workflow: str, sha: str, token: str) -> list[dict]:
    query = urlencode({"head_sha": sha, "event": "push", "per_page": 50})
    url = f"https://api.github.com/repos/{repository}/actions/workflows/{workflow}/runs?{query}"
    request = Request(url, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "release-source-verification",
    })
    with urlopen(request, timeout=20) as response:
        body = json.load(response)
    runs = body.get("workflow_runs")
    if not isinstance(runs, list):
        raise ValueError(f"GitHub returned no workflow runs list for {workflow}")
    return runs


def has_successful_run(runs: list[dict], sha: str) -> bool:
    return any(
        run.get("head_sha") == sha
        and run.get("head_branch") == "main"
        and run.get("event") == "push"
        and run.get("status") == "completed"
        and run.get("conclusion") == "success"
        for run in runs
    )


def verify_release_source(env: dict[str, str]) -> None:
    repository = env.get("GITHUB_REPOSITORY", "")
    sha = env.get("GITHUB_SHA", "")
    token = env.get("GITHUB_TOKEN", "")
    if not REPO_PATTERN.fullmatch(repository) or not SHA_PATTERN.fullmatch(sha) or not token:
        raise ValueError("missing or invalid GitHub source identity/credential")
    for workflow in REQUIRED_WORKFLOWS:
        if not has_successful_run(fetch_runs(repository, workflow, sha, token), sha):
            raise ValueError(f"{workflow} has no successful main push run for release SHA")
        print(f"PASS: {workflow} succeeded for the exact release SHA")


def main() -> int:
    try:
        publishing = validate_release_inputs(os.environ)
        if publishing:
            verify_release_source(os.environ)
        else:
            print("PASS: build-only request; publication checks do not apply")
        return 0
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as exc:
        print(f"::error::Unsafe or unverified release request: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
