#!/usr/bin/env python3
"""Validate Gitleaks pinning and deterministic historical exceptions."""

from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path
from typing import Optional, Sequence

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from secret_scan_policy import (
    load_policy,
    render_gitleaksignore,
    validate_ignore_file,
    validate_policy,
)


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", type=Path, default=Path("config/secret-scan-policy.json"))
    parser.add_argument("--ignore", type=Path, default=Path(".gitleaksignore"))
    parser.add_argument("--write-ignore", action="store_true")
    parser.add_argument(
        "--json-report",
        type=Path,
        default=Path("build/reports/security/secret-scan-policy.json"),
    )
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    try:
        policy = load_policy(args.policy)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Secret scan policy could not be loaded: {error}", file=sys.stderr)
        return 1
    tool, baseline, errors = validate_policy(policy, date.today())
    if args.write_ignore and not errors:
        args.ignore.write_text(render_gitleaksignore(baseline), encoding="utf-8")
    if not errors:
        errors.extend(validate_ignore_file(baseline, args.ignore))
    if errors:
        print("Secret scan policy validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    report = {
        "schemaVersion": policy["schema_version"],
        "gitleaks": tool,
        "baselineCount": len(baseline),
        "baseline": baseline,
    }
    args.json_report.parent.mkdir(parents=True, exist_ok=True)
    args.json_report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(
        f"Secret scan policy passed: Gitleaks {tool['version']}, "
        f"{len(baseline)} owned historical fingerprint(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
