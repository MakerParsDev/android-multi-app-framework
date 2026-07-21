#!/usr/bin/env python3
"""Verify the production admin backend without printing response bodies or secrets."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from http.client import HTTPConnection, HTTPException, HTTPResponse, HTTPSConnection
from typing import Mapping
from urllib.parse import SplitResult, urlsplit, urlunsplit

MAX_RESPONSE_BYTES = 64 * 1024
GIT_SHA_PATTERN = re.compile(r"^[0-9a-fA-F]{7,40}$")
LOCAL_HOSTS = {"127.0.0.1", "localhost", "::1"}


class SmokeFailure(RuntimeError):
    pass


@dataclass(frozen=True)
class HttpResult:
    status: int
    body: bytes


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--purchase-url", required=True)
    parser.add_argument("--push-url", required=True)
    parser.add_argument("--expected-git-sha")
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--allow-http-localhost", action="store_true")
    return parser.parse_args()


def validate_endpoint(raw: str, label: str, allow_http_localhost: bool) -> SplitResult:
    parsed = urlsplit(raw)
    if parsed.scheme not in {"https", "http"} or not parsed.hostname:
        raise SmokeFailure(f"{label} URL must be an absolute HTTP(S) URL")
    if parsed.username or parsed.password:
        raise SmokeFailure(f"{label} URL must not contain credentials")
    if parsed.query or parsed.fragment:
        raise SmokeFailure(f"{label} URL must not contain query or fragment data")
    if parsed.scheme != "https" and not (
        allow_http_localhost and parsed.hostname.lower() in LOCAL_HOSTS
    ):
        raise SmokeFailure(f"{label} URL must use HTTPS")
    if not parsed.path or parsed.path == "/":
        raise SmokeFailure(f"{label} URL must include an endpoint path")
    return parsed


def normalized_origin(parsed: SplitResult) -> tuple[str, str, int]:
    scheme = parsed.scheme.lower()
    port = parsed.port
    if port is None:
        port = 443 if scheme == "https" else 80
    return scheme, (parsed.hostname or "").lower(), port


def origin_url(parsed: SplitResult, path: str) -> str:
    return urlunsplit((parsed.scheme, parsed.netloc, path, "", ""))


def bounded_body(response: HTTPResponse) -> bytes:
    return response.read(MAX_RESPONSE_BYTES + 1)[:MAX_RESPONSE_BYTES]


def http_request(
    url: str,
    *,
    method: str,
    timeout: float,
    headers: Mapping[str, str] | None = None,
) -> HttpResult:
    parsed = urlsplit(url)
    if parsed.scheme not in {"https", "http"} or not parsed.hostname:
        raise SmokeFailure("network request failed: invalid URL")

    request_headers = {
        "accept": "application/json",
        "user-agent": "contentapp-production-smoke/1",
    }
    if headers:
        request_headers.update(headers)
    data = None
    if method == "POST":
        data = b"{}"
        request_headers["content-type"] = "application/json"

    connection_type = HTTPSConnection if parsed.scheme == "https" else HTTPConnection
    connection = connection_type(
        parsed.hostname,
        port=normalized_origin(parsed)[2],
        timeout=timeout,
    )
    target = urlunsplit(("", "", parsed.path or "/", parsed.query, ""))
    try:
        connection.request(method, target, body=data, headers=request_headers)
        response = connection.getresponse()
        return HttpResult(status=response.status, body=bounded_body(response))
    except (HTTPException, TimeoutError, OSError) as error:
        raise SmokeFailure(f"network request failed: {error.__class__.__name__}") from error
    finally:
        connection.close()


def require_status(result: HttpResult, expected: int, label: str) -> None:
    if result.status != expected:
        raise SmokeFailure(f"{label} returned HTTP {result.status}; expected {expected}")


def parse_health(result: HttpResult, expected_git_sha: str | None) -> dict[str, object]:
    require_status(result, 200, "health endpoint")
    try:
        payload = json.loads(result.body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise SmokeFailure("health endpoint returned invalid JSON") from error
    if not isinstance(payload, dict):
        raise SmokeFailure("health endpoint returned a non-object payload")
    if payload.get("ok") is not True:
        raise SmokeFailure("health endpoint did not report ok=true")
    if payload.get("service") != "cloudflare-admin-api":
        raise SmokeFailure("health endpoint reported an unexpected service")

    environment = payload.get("environment")
    if not isinstance(environment, str) or not environment.strip() or environment == "unknown":
        raise SmokeFailure("health environment is not traceable")

    git_sha = payload.get("gitSha")
    if not isinstance(git_sha, str) or not GIT_SHA_PATTERN.fullmatch(git_sha):
        raise SmokeFailure("health git SHA is not traceable")
    if expected_git_sha:
        if not GIT_SHA_PATTERN.fullmatch(expected_git_sha):
            raise SmokeFailure("expected git SHA has an invalid format")
        if git_sha.lower() != expected_git_sha.lower():
            raise SmokeFailure(
                f"health git SHA mismatch: actual={git_sha} expected={expected_git_sha}"
            )

    worker_version_id = payload.get("workerVersionId")
    if not isinstance(worker_version_id, str) or not worker_version_id.strip():
        raise SmokeFailure("health Worker version ID is not traceable")
    return payload


def run_smoke(args: argparse.Namespace) -> None:
    if not 0.1 <= args.timeout <= 30:
        raise SmokeFailure("timeout must be between 0.1 and 30 seconds")

    purchase = validate_endpoint(
        args.purchase_url, "purchase", args.allow_http_localhost
    )
    push = validate_endpoint(args.push_url, "push", args.allow_http_localhost)
    if normalized_origin(purchase) != normalized_origin(push):
        raise SmokeFailure("purchase and push URLs must use the same backend origin")

    health_url = origin_url(purchase, "/health")
    health_result = http_request(
        health_url,
        method="GET",
        timeout=args.timeout,
    )
    health = parse_health(health_result, args.expected_git_sha)
    print(f"HEALTH_HTTP={health_result.status}")
    print(f"HEALTH_SERVICE={health['service']}")
    print(f"HEALTH_ENVIRONMENT={health['environment']}")
    print(f"HEALTH_GIT_SHA={health['gitSha']}")
    print(f"HEALTH_WORKER_VERSION_ID={health['workerVersionId']}")

    purchase_result = http_request(
        args.purchase_url,
        method="POST",
        timeout=args.timeout,
    )
    require_status(purchase_result, 401, "unauthenticated purchase request")
    print(f"PURCHASE_UNAUTHENTICATED_HTTP={purchase_result.status}")

    missing_app_check = http_request(
        args.push_url,
        method="POST",
        timeout=args.timeout,
    )
    require_status(missing_app_check, 401, "missing App Check request")
    print(f"REGISTER_MISSING_APPCHECK_HTTP={missing_app_check.status}")

    invalid_app_check = http_request(
        args.push_url,
        method="POST",
        timeout=args.timeout,
        headers={"x-firebase-appcheck": "invalid-production-smoke-token"},
    )
    require_status(invalid_app_check, 401, "invalid App Check request")
    print(f"REGISTER_INVALID_APPCHECK_HTTP={invalid_app_check.status}")
    print("ADMIN_BACKEND_SMOKE=PASS")


def main() -> int:
    args = parse_args()
    try:
        run_smoke(args)
    except SmokeFailure as error:
        print(f"ADMIN_BACKEND_SMOKE=FAIL reason={error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
