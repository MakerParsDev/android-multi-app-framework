#!/usr/bin/env python3
"""Validate Cloudflare App Check vars against the canonical Firebase app catalog."""

from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
from dataclasses import dataclass
from pathlib import Path

APP_ID_PATTERN = re.compile(r"^1:(\d+):(android|ios|web):[A-Za-z0-9_-]+$")


@dataclass(frozen=True)
class ValidationResult:
    project_id: str
    project_number: str
    app_count: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", type=Path, default=Path("config/firebase-apps.json"))
    parser.add_argument(
        "--wrangler",
        type=Path,
        default=Path("side-projects/cloudflare/workers/admin-api/wrangler.toml"),
    )
    return parser.parse_args()


def validate(catalog_path: Path, wrangler_path: Path) -> ValidationResult:
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    wrangler = tomllib.loads(wrangler_path.read_text(encoding="utf-8"))
    if not isinstance(catalog, dict) or not catalog:
        raise ValueError("Firebase app catalog must be a non-empty object")

    project_ids: set[str] = set()
    project_numbers: set[str] = set()
    app_ids: set[str] = set()
    for flavor, raw_entry in catalog.items():
        if not isinstance(raw_entry, dict):
            raise ValueError(f"Firebase catalog entry must be an object: {flavor}")
        project_id = str(raw_entry.get("projectId", "")).strip()
        app_id = str(raw_entry.get("appId", "")).strip()
        match = APP_ID_PATTERN.fullmatch(app_id)
        if not project_id or not match:
            raise ValueError(f"Invalid Firebase catalog entry: {flavor}")
        project_ids.add(project_id)
        project_numbers.add(match.group(1))
        if app_id in app_ids:
            raise ValueError(f"Duplicate Firebase app ID: {app_id}")
        app_ids.add(app_id)

    if len(project_ids) != 1 or len(project_numbers) != 1:
        raise ValueError("Firebase app catalog must use exactly one project ID and project number")

    vars_config = wrangler.get("vars")
    if not isinstance(vars_config, dict):
        raise ValueError("wrangler.toml must contain a [vars] table")

    expected_project_id = next(iter(project_ids))
    expected_project_number = next(iter(project_numbers))
    actual_project_id = str(vars_config.get("FIREBASE_PROJECT_ID", "")).strip()
    actual_project_number = str(vars_config.get("FIREBASE_PROJECT_NUMBER", "")).strip()
    require_app_check = str(vars_config.get("VERIFY_PURCHASE_REQUIRE_APP_CHECK", "")).strip().lower()
    require_device_app_check = (
        str(vars_config.get("REGISTER_DEVICE_REQUIRE_APP_CHECK", "")).strip().lower()
    )
    raw_allowed_ids = str(vars_config.get("FIREBASE_APP_CHECK_ALLOWED_APP_IDS", ""))
    allowed_ids = {value.strip() for value in re.split(r"[;,\s]+", raw_allowed_ids) if value.strip()}

    errors: list[str] = []
    if actual_project_id != expected_project_id:
        errors.append(
            f"FIREBASE_PROJECT_ID mismatch: expected={expected_project_id!r} actual={actual_project_id!r}"
        )
    if actual_project_number != expected_project_number:
        errors.append(
            "FIREBASE_PROJECT_NUMBER mismatch: "
            f"expected={expected_project_number!r} actual={actual_project_number!r}"
        )
    if require_app_check != "true":
        errors.append("VERIFY_PURCHASE_REQUIRE_APP_CHECK must be true in source-controlled production config")
    if require_device_app_check != "true":
        errors.append("REGISTER_DEVICE_REQUIRE_APP_CHECK must be true in source-controlled production config")

    missing = sorted(app_ids - allowed_ids)
    unexpected = sorted(allowed_ids - app_ids)
    if missing:
        errors.append(f"App Check allowlist missing {len(missing)} Firebase app ID(s): {', '.join(missing)}")
    if unexpected:
        errors.append(f"App Check allowlist has {len(unexpected)} unexpected app ID(s): {', '.join(unexpected)}")

    if errors:
        raise ValueError("\n".join(errors))

    return ValidationResult(
        project_id=expected_project_id,
        project_number=expected_project_number,
        app_count=len(app_ids),
    )


def main() -> int:
    args = parse_args()
    try:
        result = validate(args.catalog, args.wrangler)
    except (OSError, json.JSONDecodeError, tomllib.TOMLDecodeError, ValueError) as error:
        print(f"Cloudflare App Check configuration validation failed:\n{error}", file=sys.stderr)
        return 1

    print(
        "Cloudflare App Check configuration passed: "
        f"project={result.project_id} number={result.project_number} apps={result.app_count}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
