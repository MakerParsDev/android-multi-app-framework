#!/usr/bin/env python3
"""Compare deployed side-project health/build metadata with a repository git SHA."""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import sys
from typing import Any
from urllib.error import HTTPError
from urllib.parse import SplitResult, urlsplit
from urllib.request import Request, urlopen

GIT_SHA_RE = re.compile(r"^[0-9a-fA-F]{7,40}$")
MAX_BYTES = 64 * 1024
SERVICE_NAMES = {
    "admin-api": "cloudflare-admin-api",
    "content-api": "contentapp-content-api",
    "ssv-callback": "contentapp-ssv-callback",
    "firebase-functions": "firebase-functions",
    "admin-notifications": "admin-notifications",
}


@dataclass
class Result:
    project: str
    url: str
    status: str
    actual_git_sha: str | None = None
    expected_git_sha: str | None = None
    http_status: int | None = None
    error: str | None = None

    def to_report(self) -> dict[str, object | None]:
        return {
            "project": self.project,
            "url": self.url,
            "status": self.status,
            "actualGitSha": self.actual_git_sha,
            "expectedGitSha": self.expected_git_sha,
            "httpStatus": self.http_status,
            "error": self.error,
        }


def _allowed_endpoint(parsed_url: SplitResult) -> bool:
    is_https = parsed_url.scheme == "https" and bool(parsed_url.hostname)
    is_local_http = parsed_url.scheme == "http" and parsed_url.hostname == "127.0.0.1"
    return is_https or is_local_http


def _normalize_endpoint(project: str, value: Any) -> dict[str, str]:
    if project not in SERVICE_NAMES:
        raise ValueError(f"unsupported project: {project}")
    if isinstance(value, str):
        config = {"url": value, "method": "GET"}
    elif isinstance(value, dict):
        config = {str(key): str(item) for key, item in value.items()}
    else:
        raise ValueError(f"invalid endpoint config for {project}")

    url = config.get("url", "").strip()
    parsed_url = urlsplit(url)
    if not _allowed_endpoint(parsed_url):
        raise ValueError(f"{project}: endpoint must use HTTPS")
    if parsed_url.username or parsed_url.password:
        raise ValueError(f"{project}: endpoint must not contain credentials")
    if parsed_url.fragment:
        raise ValueError(f"{project}: endpoint must not contain a fragment")

    method = config.get("method", "GET").upper()
    if method not in {"GET", "POST"}:
        raise ValueError(f"{project}: method must be GET or POST")
    return {"url": url, "method": method}


def parse_endpoints(raw: str | None) -> dict[str, dict[str, str]]:
    if not raw:
        return {}
    parsed = json.loads(raw)
    if not isinstance(parsed, dict):
        raise ValueError("endpoints JSON must be an object")
    return {
        project: _normalize_endpoint(project, value)
        for project, value in parsed.items()
    }


def fetch_json(url: str, method: str, timeout: float) -> tuple[int, dict[str, object]]:
    if not _allowed_endpoint(urlsplit(url)):
        raise RuntimeError("unsafe-endpoint-url")
    body = b"{}" if method == "POST" else None
    request = Request(
        url,
        data=body,
        method=method,
        headers={"accept": "application/json", "content-type": "application/json"},
    )
    try:
        # The URL scheme and host are restricted immediately above.
        with urlopen(request, timeout=timeout) as response:  # nosec B310
            status = response.status
            raw = response.read(MAX_BYTES + 1)
    except HTTPError as error:
        status = error.code
        raw = error.read(MAX_BYTES + 1)
    except OSError as error:
        raise RuntimeError(error.__class__.__name__) from error
    if len(raw) > MAX_BYTES:
        raise RuntimeError("response-too-large")
    try:
        payload = json.loads(raw.decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        if status != 200:
            return status, {}
        raise RuntimeError("response-not-json") from error
    if not isinstance(payload, dict):
        raise RuntimeError("response-not-object")
    return status, payload


def _contract_error(project: str, payload: dict[str, object]) -> str | None:
    service_matches = payload.get("service") == SERVICE_NAMES[project]
    if project == "admin-notifications":
        return None if service_matches else "invalid-build-contract"
    health_matches = payload.get("ok") is True and service_matches
    return None if health_matches else "invalid-health-contract"


def evaluate(project: str, config: dict[str, str], expected_sha: str, timeout: float) -> Result:
    url = config["url"]
    try:
        status, payload = fetch_json(url, config["method"], timeout)
        if status != 200:
            return Result(project, url, "error", http_status=status, error="unexpected-http-status")
        contract_error = _contract_error(project, payload)
        if contract_error:
            return Result(project, url, "error", http_status=status, error=contract_error)
        actual = payload.get("gitSha")
        if not isinstance(actual, str) or not GIT_SHA_RE.fullmatch(actual):
            return Result(project, url, "error", http_status=status, error="untraceable-git-sha")
        match = actual.lower() == expected_sha.lower()
        return Result(
            project,
            url,
            "current" if match else "drift",
            actual_git_sha=actual,
            expected_git_sha=expected_sha,
            http_status=status,
        )
    except RuntimeError as error:
        return Result(project, url, "error", error=str(error))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected-git-sha", required=True)
    parser.add_argument("--endpoints-json")
    parser.add_argument("--mode", choices=("strict", "report"), default="strict")
    parser.add_argument("--allow-unconfigured", action="store_true")
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--report", type=Path, required=True)
    return parser.parse_args()


def _report_status(endpoints: dict[str, object], failures: list[Result]) -> str:
    if not endpoints:
        return "unconfigured"
    return "drift" if failures else "passed"


def main() -> int:
    args = parse_args()
    if not GIT_SHA_RE.fullmatch(args.expected_git_sha):
        print("invalid expected git SHA", file=sys.stderr)
        return 2
    try:
        endpoints = parse_endpoints(args.endpoints_json)
    except ValueError as error:
        print(f"invalid endpoint configuration: {error}", file=sys.stderr)
        return 2
    if not endpoints and not args.allow_unconfigured:
        print("no side-project health endpoints configured", file=sys.stderr)
        return 2
    results = [
        evaluate(name, config, args.expected_git_sha, args.timeout)
        for name, config in endpoints.items()
    ]
    failures = [result for result in results if result.status != "current"]
    report = {
        "status": _report_status(endpoints, failures),
        "mode": args.mode,
        "expectedGitSha": args.expected_git_sha,
        "checkedAt": datetime.now(timezone.utc).isoformat(),
        "results": [result.to_report() for result in results],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for result in results:
        print(f"DRIFT project={result.project} status={result.status} actual={result.actual_git_sha or '-'}")
    if not endpoints:
        print("DRIFT status=unconfigured")
    return 1 if args.mode == "strict" and failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
