#!/usr/bin/env python3
"""Validate the source-controlled AdMob account and Android flavor inventory."""

from __future__ import annotations

import argparse
import sys
from datetime import date
from pathlib import Path
from typing import Mapping

from admob_inventory_common import load_json, parse_ads_xml, write_json
from admob_inventory_governance import render_markdown, validate_inventory

__all__ = ["parse_ads_xml", "validate_inventory"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument(
        "--inventory",
        type=Path,
        default=Path("config/admob-inventory.json"),
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("docs/ADMOB_INVENTORY.md"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/reports/admob/inventory.json"),
    )
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today())
    parser.add_argument("--mode", choices=["warn", "strict"], default="strict")
    parser.add_argument("--target-flavors", default="all")
    parser.add_argument("--write", action="store_true")
    return parser.parse_args()


def resolve(root: Path, path: Path) -> Path:
    return path if path.is_absolute() else root / path


def validate_report_file(
    root: Path,
    report_path: Path,
    markdown: str,
    write: bool,
) -> list[str]:
    if write:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(markdown, encoding="utf-8")
    if not report_path.exists():
        return [f"Missing generated report: {report_path.relative_to(root)}"]
    if report_path.read_text(encoding="utf-8") != markdown:
        return [
            "AdMob inventory report is stale; run "
            "validate_admob_inventory.py --write"
        ]
    return []


def print_result(
    errors: list[str],
    report: Mapping[str, object],
    mode: str,
) -> int:
    for warning in report.get("warnings", []):
        print(f"WARN: {warning}")
    if errors:
        message = (
            "AdMob inventory validation failed:"
            if mode == "strict"
            else "AdMob inventory validation warnings:"
        )
        print(message, file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        if mode == "strict":
            return 1
        print(f"WARN: validated with {len(errors)} error(s) in warn mode")
        return 0

    print(
        "AdMob inventory validation passed: "
        f"{report['framework_app_count']} framework apps, "
        f"{report['framework_primary_ad_unit_count']} primary ad units, "
        f"{report['action_required_app_count']} cleanup candidates, "
        f"{report['pending_manual_cleanup_count']} pending console actions"
    )
    return 0


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    inventory_path = resolve(root, args.inventory)
    report_path = resolve(root, args.report)
    output_path = resolve(root, args.output)

    if not inventory_path.exists():
        print(f"Missing AdMob inventory: {inventory_path}", file=sys.stderr)
        return 1
    inventory = load_json(inventory_path)
    if not isinstance(inventory, Mapping):
        print("AdMob inventory root must be an object", file=sys.stderr)
        return 1

    errors, report = validate_inventory(
        root,
        inventory,
        today=args.as_of,
        target_flavors=args.target_flavors,
    )
    errors.extend(
        validate_report_file(
            root,
            report_path,
            render_markdown(report),
            args.write,
        )
    )
    write_json(output_path, report)
    return print_result(errors, report, args.mode)


if __name__ == "__main__":
    raise SystemExit(main())
