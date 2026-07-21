#!/usr/bin/env python3
"""Validate the checked-in app-ads.txt seller file."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Iterable

DEFAULT_PATH = Path("side-projects/firebase/mobil_web/public/app-ads.txt")
REQUIRED_GOOGLE_SELLER = (
    "google.com",
    "pub-3312485084079132",
    "DIRECT",
    "f08c47fec0942fa0",
)


def _parse_seller_line(line: str) -> list[str]:
    return [part.strip() for part in line.split(",")]


def validate_content(lines: Iterable[str]) -> list[str]:
    errors: list[str] = []
    found_required_google_seller = False

    for line_number, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue

        if "ca-app-pub-" in line:
            errors.append(
                f"line {line_number}: app-ads.txt must use publisher IDs such as "
                "pub-3312485084079132, not ca-app-pub app IDs or ad unit IDs",
            )

        parts = _parse_seller_line(line)
        if len(parts) not in (3, 4):
            errors.append(
                f"line {line_number}: expected 3 or 4 comma-separated fields, got {len(parts)}",
            )
            continue

        if any(not part for part in parts):
            errors.append(f"line {line_number}: seller fields must not be blank")
            continue

        relationship = parts[2].upper()
        if relationship not in {"DIRECT", "RESELLER"}:
            errors.append(
                f"line {line_number}: relationship must be DIRECT or RESELLER, got {parts[2]!r}",
            )

        normalized = (
            parts[0].lower(),
            parts[1],
            relationship,
            parts[3].lower() if len(parts) == 4 else "",
        )
        if normalized == REQUIRED_GOOGLE_SELLER:
            found_required_google_seller = True

    if not found_required_google_seller:
        errors.append(
            "missing required seller line: "
            + ", ".join(REQUIRED_GOOGLE_SELLER),
        )

    return errors


def validate_file(path: Path) -> list[str]:
    if not path.exists():
        return [f"file not found: {path}"]
    return validate_content(path.read_text(encoding="utf-8").splitlines())


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", type=Path, default=DEFAULT_PATH)
    parser.add_argument("--mode", choices=("strict", "warn"), default="strict")
    args = parser.parse_args()

    errors = validate_file(args.file)
    if not errors:
        print(f"app-ads.txt validation passed: {args.file}")
        return 0

    annotation = "warning" if args.mode == "warn" else "error"
    for error in errors:
        print(f"::{annotation}::{error}")
    return 0 if args.mode == "warn" else 1


if __name__ == "__main__":
    raise SystemExit(main())
