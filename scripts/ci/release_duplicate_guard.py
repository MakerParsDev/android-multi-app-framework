#!/usr/bin/env python3
"""Prevent redispatching a completed publication for the same Git SHA/track/flavors.

Existing Actions concurrency serializes in-progress releases. This check makes a
successful publication idempotent across *subsequent* manual/agent dispatches.
It is intentionally independent of ChatGPT, Ops, and new storage services.
"""

from __future__ import annotations

import json
import re
from urllib.parse import urlencode
from urllib.request import Request, urlopen

RUN_TITLE = re.compile(
    r"^Release: (?P<flavors>[a-z0-9_,]+) \| "
    r"(?P<track>production|internal|build) \| "
    r"(?P<sha>[a-f0-9]{40})$"
)
FLAVOR_INPUT = re.compile(r"^(?:all|[a-z0-9_]+(?:,[a-z0-9_]+)*)$")


def normalize_flavors(raw: str) -> tuple[str, ...]:
    """Reject ambiguous input; treat differently ordered lists as one set."""
    if not FLAVOR_INPUT.fullmatch(raw):
        raise ValueError("publish flavors must be canonical lowercase IDs, CSV, or all")
    if raw == "all":
        return ("all",)
    values = raw.split(",")
    if len(values) != len(set(values)):
        raise ValueError("duplicate flavor IDs in publication request")
    return tuple(sorted(values))


def prior_successful_publication(
    runs: list[dict], *, current_id: int, sha: str, track: str,
    flavors: tuple[str, ...],
) -> int | None:
    for run in runs:
        try:
            if int(run.get("id", -1)) == current_id:
                continue
        except (ValueError, TypeError):
            continue
        if run.get("status") != "completed" or run.get("conclusion") != "success":
            continue
        title = RUN_TITLE.fullmatch(run.get("display_title") or "")
        if title is None or title["track"] != track or title["sha"] != sha:
            continue
        released_flavors = normalize_flavors(title["flavors"])
        # Publishing "all" includes each individual flavor. A multi-flavor
        # publication must also not republish any previously released flavor.
        if ("all" in released_flavors or "all" in flavors
                or not set(released_flavors).isdisjoint(flavors)):
            return int(run["id"])
    return None


def verify_no_successful_duplicate(env: dict[str, str]) -> None:
    sha = env["GITHUB_SHA"]
    repo = env["GITHUB_REPOSITORY"]
    token = env["GITHUB_TOKEN"]
    current_id = int(env["GITHUB_RUN_ID"])
    flavors = normalize_flavors(env["RELEASE_TARGET_FLAVORS"])
    track = "production" if env["RELEASE_DO_PUBLISH"].lower() == "true" else "internal"
    query = urlencode({"head_sha": sha, "event": "workflow_dispatch", "per_page": 100})
    url = f"https://api.github.com/repos/{repo}/actions/workflows/release.yml/runs?{query}"
    request = Request(url, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "release-duplicate-guard",
    })
    with urlopen(request, timeout=20) as response:
        payload = json.load(response)
    runs = payload.get("workflow_runs")
    if not isinstance(runs, list):
        raise ValueError("release runs response was invalid")
    if payload.get("total_count", len(runs)) > len(runs):
        raise ValueError("release history is paginated; refusing an incomplete duplicate check")
    prior = prior_successful_publication(
        runs, current_id=current_id, sha=sha, track=track, flavors=flavors,
    )
    if prior is not None:
        raise ValueError(
            f"publication already succeeded for this SHA/track/flavors in run {prior}; "
            "use a new reviewed main commit for a new release"
        )
    print("PASS: no completed duplicate publication for current SHA/track/flavors")
