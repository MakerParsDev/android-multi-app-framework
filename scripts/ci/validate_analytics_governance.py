#!/usr/bin/env python3
"""Validate the Kotlin analytics contract and GA4 governance manifest."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from analytics_governance import (
    build_report,
    collect_source_inventory,
    load_json,
    parse_object_constants,
    parse_schema_version,
    production_kotlin_files,
    render_markdown,
    validate_contract,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--config", type=Path, default=Path("config/analytics-governance.json"))
    parser.add_argument("--write-report", type=Path)
    parser.add_argument("--check-report", type=Path, default=Path("docs/ANALYTICS_GOVERNANCE.md"))
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/reports/analytics/contract.json"),
    )
    return parser.parse_args()


def resolve(root: Path, path: Path) -> Path:
    return path if path.is_absolute() else root / path


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    config_path = resolve(root, args.config)
    contract_path = (
        root
        / "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/AnalyticsContract.kt"
    )
    config = load_json(config_path)
    contract_text = contract_path.read_text(encoding="utf-8")
    schema_version = parse_schema_version(contract_text)
    event_constants = parse_object_constants(contract_text, "AnalyticsEventName")
    param_constants = parse_object_constants(contract_text, "AnalyticsParamKey")
    user_property_constants = parse_object_constants(contract_text, "AnalyticsUserPropertyKey")
    files = production_kotlin_files(root)
    inventory = collect_source_inventory(root, files)
    errors = validate_contract(
        root,
        config,
        schema_version,
        event_constants,
        param_constants,
        user_property_constants,
        inventory,
    )
    report = build_report(
        config,
        event_constants,
        param_constants,
        user_property_constants,
        inventory,
    )
    markdown = render_markdown(report)

    if args.write_report is not None:
        report_path = resolve(root, args.write_report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(markdown, encoding="utf-8")

    check_path = resolve(root, args.check_report)
    if not check_path.exists():
        errors.append("Missing deterministic analytics report: %s" % check_path.relative_to(root))
    elif check_path.read_text(encoding="utf-8") != markdown:
        errors.append(
            "Analytics report is stale: run validate_analytics_governance.py --write-report "
            + check_path.relative_to(root).as_posix()
        )

    output_path = resolve(root, args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    if errors:
        print("Analytics governance validation failed:", file=sys.stderr)
        for error in errors:
            print("  - " + error, file=sys.stderr)
        return 1

    print(
        "Analytics governance validation passed: %d events, %d parameters, %d dimensions, %d metrics"
        % (
            len(report["events"]),
            len(report["parameters"]),
            len(report["custom_dimensions"]),
            len(report["custom_metrics"]),
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
