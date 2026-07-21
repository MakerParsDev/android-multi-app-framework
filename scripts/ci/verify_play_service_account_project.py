#!/usr/bin/env python3
"""
Verify Play service account identity before publish/sync workflows.

Checks:
- PLAY_SERVICE_ACCOUNT_JSON is present (path or inline JSON)
- project_id matches expected project
- client_email and private_key exist
- known deleted/legacy project identifiers are blocked
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import sys

FORBIDDEN_IDENTIFIERS = {
    "mobil-oaslananka-firebase",
    "949743839540",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify Play service account project")
    parser.add_argument(
        "--expected-project-id",
        default="makerpars-oaslananka-mobil",
        help="Expected GCP project_id in service account JSON",
    )
    return parser.parse_args()


def try_read_from_dotenv(repo_root: pathlib.Path) -> str:
    env_path = repo_root / ".env"
    if not env_path.exists():
        return ""

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() != "PLAY_SERVICE_ACCOUNT_JSON":
            continue
        v = value.strip()
        if len(v) >= 2 and v[0] == '"' and v[-1] == '"':
            v = v[1:-1]
        return v.strip()
    return ""


def load_service_account(raw_value: str) -> dict:
    raw = (raw_value or "").strip()
    if not raw:
        raise RuntimeError("PLAY_SERVICE_ACCOUNT_JSON is missing/empty")

    maybe_path = pathlib.Path(raw)
    if maybe_path.exists() and maybe_path.is_file():
        return json.loads(maybe_path.read_text(encoding="utf-8"))

    if raw.startswith("{") and raw.endswith("}"):
        return json.loads(raw)

    raise RuntimeError("PLAY_SERVICE_ACCOUNT_JSON must be a file path or inline JSON content")


def mask_email_domain(email: str) -> str:
    if "@" not in email:
        return "unknown"
    return email.split("@", 1)[1]


def contains_forbidden(text: str) -> str | None:
    lowered = text.lower()
    for item in FORBIDDEN_IDENTIFIERS:
        if item.lower() in lowered:
            return item
    return None


def main() -> int:
    args = parse_args()
    repo_root = pathlib.Path(__file__).resolve().parents[2]
    is_ci = os.environ.get("CI", "").strip().lower() == "true"

    raw_sa = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw_sa and not is_ci:
        raw_sa = try_read_from_dotenv(repo_root)

    try:
        sa = load_service_account(raw_sa)
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}")
        return 1

    project_id = str(sa.get("project_id") or "").strip()
    client_email = str(sa.get("client_email") or "").strip()
    private_key = str(sa.get("private_key") or "").strip()

    if not project_id:
        print("ERROR: service account JSON is missing project_id")
        return 1
    if not client_email:
        print("ERROR: service account JSON is missing client_email")
        return 1
    if not private_key:
        print("ERROR: service account JSON is missing private_key")
        return 1

    if project_id != args.expected_project_id:
        print(
            "ERROR: project_id mismatch: "
            f"expected={args.expected_project_id} actual={project_id}"
        )
        return 1

    forbidden_in_project = contains_forbidden(project_id)
    forbidden_in_email = contains_forbidden(client_email)
    if forbidden_in_project or forbidden_in_email:
        hit = forbidden_in_project or forbidden_in_email
        print(f"ERROR: forbidden/deleted project identifier detected: {hit}")
        return 1

    domain = mask_email_domain(client_email)
    print(
        "OK: Play service account validated "
        f"(project_id={project_id}, email_domain={domain})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
