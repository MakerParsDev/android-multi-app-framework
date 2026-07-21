#!/usr/bin/env python3
"""Fail when Git tracks environment files, signing keys, or credential JSON files."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import List, Optional, Sequence

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from tracked_sensitive_files import find_sensitive_paths


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path("."))
    parser.add_argument(
        "--json-report",
        type=Path,
        default=Path("build/reports/security/tracked-sensitive-files.json"),
    )
    return parser.parse_args(argv)


def tracked_paths(repo: Path) -> List[str]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=repo,
        check=True,
        capture_output=True,
    )
    return [item.decode("utf-8") for item in result.stdout.split(b"\0") if item]


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    try:
        paths = tracked_paths(args.repo)
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"Tracked file inventory failed: {error}", file=sys.stderr)
        return 1
    findings = find_sensitive_paths(paths)
    report = {
        "trackedFiles": len(paths),
        "findingCount": len(findings),
        "findings": findings,
    }
    report_path = args.json_report
    if not report_path.is_absolute():
        report_path = args.repo / report_path
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if findings:
        print("Tracked sensitive file validation failed:", file=sys.stderr)
        for finding in findings:
            print(f"  - {finding}", file=sys.stderr)
        return 1
    print(f"Tracked sensitive file validation passed: {len(paths)} tracked file(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
