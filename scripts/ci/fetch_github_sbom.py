#!/usr/bin/env python3
"""Fetch the repository SPDX SBOM through GitHub's asynchronous SBOM API.

The legacy synchronous dependency-graph SBOM endpoint is being retired in
November 2026. This helper uses the generate-report/fetch-report flow and
writes the final raw SPDX JSON document for scanners such as OSV-Scanner.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.request

API_VERSION = "2026-03-10"
REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")


class SbomFetchError(RuntimeError):
    pass


def _request_json(
    url: str,
    *,
    token: str | None = None,
    timeout: float = 30.0,
) -> tuple[int, object]:
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": API_VERSION,
        "User-Agent": "android-multi-app-framework-sbom-health",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = int(getattr(response, "status", response.getcode()))
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace") if error.fp else ""
        raise SbomFetchError(
            f"GitHub SBOM API HTTP {error.code}: {body[:500]}"
        ) from error
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise SbomFetchError(f"GitHub SBOM API request failed: {error}") from error

    if not body.strip():
        return status, {}
    try:
        return status, json.loads(body)
    except json.JSONDecodeError as error:
        raise SbomFetchError(
            f"GitHub SBOM API returned invalid JSON (HTTP {status})"
        ) from error


def _validate_report_url(repo: str, report_url: object) -> str:
    if not isinstance(report_url, str) or not report_url:
        raise SbomFetchError("GitHub SBOM generation response has no sbom_url")
    expected = f"https://api.github.com/repos/{repo}/dependency-graph/sbom/fetch-report/"
    if not report_url.startswith(expected):
        raise SbomFetchError("GitHub SBOM report URL is outside the expected API path")
    return report_url


def fetch_sbom(
    repo: str,
    output: Path,
    *,
    token: str | None = None,
    poll_seconds: float = 2.0,
    timeout_seconds: float = 90.0,
) -> dict:
    if not REPO_RE.fullmatch(repo):
        raise SbomFetchError("Repository must be in owner/name form")

    generate_url = (
        f"https://api.github.com/repos/{repo}/dependency-graph/sbom/generate-report"
    )
    status, generated = _request_json(generate_url, token=token)
    if status not in {200, 201} or not isinstance(generated, dict):
        raise SbomFetchError(
            f"Unexpected SBOM generation response: HTTP {status}"
        )

    report_url = _validate_report_url(repo, generated.get("sbom_url"))
    deadline = time.monotonic() + timeout_seconds

    while True:
        # The repository is public. Fetch the report without Authorization so a
        # GitHub redirect to temporary object storage can never receive a token.
        status, report = _request_json(report_url, token=None)
        if status == 202:
            if time.monotonic() >= deadline:
                raise SbomFetchError("Timed out waiting for GitHub SBOM report")
            time.sleep(poll_seconds)
            continue
        if status != 200 or not isinstance(report, dict):
            raise SbomFetchError(f"Unexpected SBOM report response: HTTP {status}")
        if report.get("spdxVersion") not in {"SPDX-2.2", "SPDX-2.3"}:
            raise SbomFetchError("GitHub SBOM report is not a supported SPDX JSON document")
        packages = report.get("packages")
        if not isinstance(packages, list) or not packages:
            raise SbomFetchError("GitHub SBOM report contains no packages")

        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(report, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo",
        default=os.environ.get("GITHUB_REPOSITORY"),
        help="GitHub repository in owner/name form",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/reports/dependencies/repository.spdx.json"),
    )
    parser.add_argument("--timeout-seconds", type=float, default=90.0)
    parser.add_argument("--poll-seconds", type=float, default=2.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.repo:
        print("Error: --repo or GITHUB_REPOSITORY is required", file=sys.stderr)
        return 1
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    try:
        report = fetch_sbom(
            args.repo,
            args.output,
            token=token,
            poll_seconds=args.poll_seconds,
            timeout_seconds=args.timeout_seconds,
        )
    except SbomFetchError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1

    print(
        f"GitHub SPDX SBOM written to {args.output} "
        f"({len(report['packages'])} packages)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
