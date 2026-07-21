#!/usr/bin/env python3
"""Resolve the complete Android flavor matrix from committed public catalogs."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

FLAVOR_RE = re.compile(r"^[a-z][a-z0-9_]*$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path("."))
    return parser.parse_args()


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise RuntimeError(f"missing required catalog: {path}") from exc
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"invalid JSON in {path}: {exc}") from exc


def load_flavors(repo: Path) -> list[str]:
    apps = load_json(repo / ".ci/apps.json")
    firebase = load_json(repo / "config/firebase-apps.json")
    if not isinstance(apps, list):
        raise RuntimeError(".ci/apps.json must contain an array")
    if not isinstance(firebase, dict):
        raise RuntimeError("config/firebase-apps.json must contain an object")

    flavors: list[str] = []
    seen: set[str] = set()
    for index, raw in enumerate(apps):
        if not isinstance(raw, dict):
            raise RuntimeError(f"apps catalog entry {index} must be an object")
        flavor = raw.get("flavor")
        package_name = raw.get("package")
        if not isinstance(flavor, str) or not FLAVOR_RE.fullmatch(flavor):
            raise RuntimeError(f"apps catalog entry {index} has invalid flavor")
        if not isinstance(package_name, str) or not package_name.strip():
            raise RuntimeError(f"[{flavor}] package must be non-empty")
        if flavor in seen:
            raise RuntimeError(f"duplicate flavor: {flavor}")
        seen.add(flavor)
        flavors.append(flavor)

    firebase_flavors = set(firebase)
    if seen != firebase_flavors:
        missing_firebase = sorted(seen - firebase_flavors)
        missing_apps = sorted(firebase_flavors - seen)
        raise RuntimeError(
            "catalog mismatch: "
            f"missing Firebase entries={missing_firebase}; "
            f"missing app entries={missing_apps}"
        )
    if not flavors:
        raise RuntimeError("app catalog must contain at least one flavor")
    return flavors


def main() -> int:
    args = parse_args()
    try:
        flavors = load_flavors(args.repo.resolve())
    except RuntimeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(f"flavors={json.dumps(flavors, separators=(',', ':'))}")
    print(f"count={len(flavors)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
