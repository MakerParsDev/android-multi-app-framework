#!/usr/bin/env python3
"""Generate non-secret google-services.json placeholders for CI compilation only."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
from pathlib import Path
from typing import Any

GENERATED_BY = "generate_ci_google_services.py"
PLACEHOLDER_API_KEY = "ci-placeholder-not-a-real-api-key"
APP_ID_RE = re.compile(r"^1:(\d+):android:[0-9a-f]+$", re.IGNORECASE)
PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate CI-only Firebase placeholders from committed public catalogs"
    )
    parser.add_argument("--repo", type=Path, default=Path("."))
    parser.add_argument(
        "--flavors",
        required=True,
        help="Comma-separated flavor names or 'all'",
    )
    parser.add_argument(
        "--clean",
        action="store_true",
        help="Remove generated placeholders instead of creating them",
    )
    return parser.parse_args()


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise RuntimeError(f"missing required catalog: {path}") from exc
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"invalid JSON in {path}: {exc}") from exc


def load_catalogs(repo: Path) -> tuple[dict[str, dict[str, str]], dict[str, str]]:
    firebase_raw = load_json(repo / "config/firebase-apps.json")
    apps_raw = load_json(repo / ".ci/apps.json")
    if not isinstance(firebase_raw, dict):
        raise RuntimeError("config/firebase-apps.json must contain an object")
    if not isinstance(apps_raw, list):
        raise RuntimeError(".ci/apps.json must contain an array")

    firebase: dict[str, dict[str, str]] = {}
    for flavor, value in firebase_raw.items():
        if not isinstance(flavor, str) or not isinstance(value, dict):
            raise RuntimeError("invalid Firebase catalog entry")
        project_id = value.get("projectId")
        app_id = value.get("appId")
        if not isinstance(project_id, str) or not project_id:
            raise RuntimeError(f"[{flavor}] missing projectId")
        if not isinstance(app_id, str) or not APP_ID_RE.fullmatch(app_id):
            raise RuntimeError(f"[{flavor}] invalid appId")
        firebase[flavor] = {"projectId": project_id, "appId": app_id}

    packages: dict[str, str] = {}
    for value in apps_raw:
        if not isinstance(value, dict):
            raise RuntimeError("invalid .ci/apps.json entry")
        flavor = value.get("flavor")
        package = value.get("package")
        if not isinstance(flavor, str) or not isinstance(package, str):
            raise RuntimeError(".ci/apps.json entries require flavor and package")
        if not PACKAGE_RE.fullmatch(package):
            raise RuntimeError(f"[{flavor}] invalid package name")
        if flavor in packages:
            raise RuntimeError(f"duplicate flavor in .ci/apps.json: {flavor}")
        packages[flavor] = package

    return firebase, packages


def resolve_flavors(raw: str, available: set[str]) -> list[str]:
    normalized = raw.replace("\r", "").strip()
    if normalized == "all":
        return sorted(available)
    requested: list[str] = []
    for item in normalized.split(","):
        flavor = item.strip()
        if not flavor:
            continue
        if flavor not in available:
            raise RuntimeError(f"unknown flavor: {flavor}")
        if flavor not in requested:
            requested.append(flavor)
    if not requested:
        raise RuntimeError("no flavors selected")
    return requested


def is_generated_placeholder(path: Path) -> bool:
    try:
        value = load_json(path)
    except RuntimeError:
        return False
    return (
        isinstance(value, dict)
        and isinstance(value.get("ci_placeholder"), dict)
        and value["ci_placeholder"].get("generated_by") == GENERATED_BY
    )


def build_payload(project_id: str, app_id: str, package_name: str) -> dict[str, Any]:
    match = APP_ID_RE.fullmatch(app_id)
    if match is None:
        raise RuntimeError(f"invalid appId: {app_id}")
    project_number = match.group(1)
    return {
        "project_info": {
            "project_number": project_number,
            "project_id": project_id,
            "storage_bucket": f"{project_id}.appspot.com",
        },
        "client": [
            {
                "client_info": {
                    "mobilesdk_app_id": app_id,
                    "android_client_info": {"package_name": package_name},
                },
                "oauth_client": [
                    {
                        "client_id": (
                            f"{project_number}-ci-placeholder.apps.googleusercontent.com"
                        ),
                        "client_type": 3,
                    }
                ],
                "api_key": [{"current_key": PLACEHOLDER_API_KEY}],
                "services": {
                    "appinvite_service": {"other_platform_oauth_client": []}
                },
            }
        ],
        "configuration_version": "1",
        "ci_placeholder": {
            "generated_by": GENERATED_BY,
            "purpose": "secret-free CI compilation only",
        },
    }


def write_placeholder(path: Path, payload: dict[str, Any]) -> None:
    if path.exists() and not is_generated_placeholder(path):
        raise RuntimeError(
            f"refusing to overwrite existing non-placeholder config: {path}"
        )
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=".google-services.", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2, sort_keys=True)
            handle.write("\n")
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        temporary.unlink(missing_ok=True)


def remove_placeholder(path: Path) -> None:
    if not path.exists():
        return
    if not is_generated_placeholder(path):
        raise RuntimeError(
            f"refusing to remove existing non-placeholder config: {path}"
        )
    path.unlink()


def main() -> int:
    args = parse_args()
    repo = args.repo.resolve()
    try:
        firebase, packages = load_catalogs(repo)
        available = set(firebase) & set(packages)
        flavors = resolve_flavors(args.flavors, available)
        for flavor in flavors:
            target = repo / "app" / "src" / flavor / "google-services.json"
            if args.clean:
                remove_placeholder(target)
                print(f"Removed CI Firebase placeholder: {flavor}")
                continue
            entry = firebase[flavor]
            payload = build_payload(entry["projectId"], entry["appId"], packages[flavor])
            write_placeholder(target, payload)
            print(f"Generated CI Firebase placeholder: {flavor}")
        return 0
    except RuntimeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
