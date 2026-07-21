#!/usr/bin/env python3
"""Block new Gradle, D8, and ASM warning classes outside the owned policy."""

from __future__ import annotations

import argparse
import sys
from datetime import date
from pathlib import Path

from gradle_warning_audit import (
    build_report,
    extract_warnings,
    load_policy,
    match_warnings,
    validate_policy,
    write_reports,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument(
        "--policy",
        type=Path,
        default=Path("config/gradle-warning-policy.json"),
    )
    parser.add_argument(
        "--json-report",
        type=Path,
        default=Path("build/reports/dependencies/gradle-warning-audit.json"),
    )
    parser.add_argument(
        "--markdown-report",
        type=Path,
        default=Path("build/reports/dependencies/gradle-warning-audit.md"),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        text = args.log.read_text(encoding="utf-8", errors="replace")
        policy = load_policy(args.policy)
    except (OSError, ValueError) as exc:
        print(f"Gradle warning audit failed: {exc}", file=sys.stderr)
        return 1

    policy_entries, policy_errors = validate_policy(policy, date.today())
    warnings = extract_warnings(text)
    observed, warning_errors = match_warnings(warnings, policy_entries)
    report = build_report(args.log, observed, policy_entries)
    write_reports(report, args.json_report, args.markdown_report)

    errors = policy_errors + warning_errors
    if errors:
        print("Gradle warning audit failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    summary = report["summary"]
    print(
        "Gradle warning audit passed: "
        f"{summary['observedWarnings']} observed warning(s), "
        f"{summary['policyEntries']} owned policy entries"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
