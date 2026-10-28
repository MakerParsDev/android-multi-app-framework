#!/usr/bin/env python3
"""Fail closed if built release Google Sign-In resources omit the configured web client.

Read-only: examines Gradle's release resource output; never reads the APK,
commits secrets or communicates with Play Console.
"""
from __future__ import annotations

import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

RESOURCE = Path("core/auth/build/generated/res/resValues/release/values/gradleResValues.xml")
WEB_CLIENT_PATTERN = re.compile(r"^[0-9]+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$")


def verify_resource(path: Path, expected: str) -> None:
    if not WEB_CLIENT_PATTERN.fullmatch(expected):
        raise ValueError("authorized release Google web client is absent or invalid")
    if not path.is_file():
        raise ValueError("generated release auth resources missing")
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        raise ValueError("generated release auth resources are invalid XML") from exc
    values = [
        (node.text or "").strip()
        for node in root.iter("string")
        if node.get("name") == "web_client_id"
    ]
    if len(values) != 1 or not WEB_CLIENT_PATTERN.fullmatch(values[0]):
        raise ValueError("generated release web client is absent, empty or invalid")
    if values[0] != expected:
        raise ValueError("generated release web client differs from authorized configuration")


def main() -> int:
    # core/auth's precedence: explicit WEB_CLIENT_ID, then FIREBASE_WEB_CLIENT_ID.
    expected = (
        os.environ.get("WEB_CLIENT_ID", "").strip()
        or os.environ.get("FIREBASE_WEB_CLIENT_ID", "").strip()
    )
    try:
        verify_resource(RESOURCE, expected)
    except ValueError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    print("PASS: release Google Sign-In resource matches authorized configuration")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
