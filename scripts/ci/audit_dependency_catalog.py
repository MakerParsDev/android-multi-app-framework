#!/usr/bin/env python3
"""Validate dependency lifecycle policy and emit deterministic CI reports."""

from __future__ import annotations

import argparse
import sys
from datetime import date
from pathlib import Path

from dependency_catalog_audit import (
    build_inventory,
    load_policy,
    parse_catalog,
    validate_policy,
    write_reports,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", type=Path, default=Path("gradle/libs.versions.toml"))
    parser.add_argument("--policy", type=Path, default=Path("config/dependency-policy.json"))
    parser.add_argument(
        "--json-report",
        type=Path,
        default=Path("build/reports/dependencies/catalog-audit.json"),
    )
    parser.add_argument(
        "--markdown-report",
        type=Path,
        default=Path("build/reports/dependencies/catalog-audit.md"),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    errors = []
    try:
        catalog, catalog_errors = parse_catalog(args.catalog)
        errors.extend(catalog_errors)
        policy = load_policy(args.policy)
    except (OSError, ValueError) as exc:
        print(f"Dependency catalog audit failed: {exc}", file=sys.stderr)
        return 1

    allowlists, policy_errors = validate_policy(policy, date.today())
    errors.extend(policy_errors)
    inventory, inventory_errors = build_inventory(catalog, allowlists)
    errors.extend(inventory_errors)
    write_reports(inventory, args.json_report, args.markdown_report)

    if errors:
        print("Dependency catalog audit failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    summary = inventory["summary"]
    print(
        "Dependency catalog audit passed: "
        f"{summary['libraries']} libraries, {summary['plugins']} plugins, "
        f"{summary['inlineVersions']} inline versions, "
        f"{summary['catalogPrereleases']} catalog prereleases"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
