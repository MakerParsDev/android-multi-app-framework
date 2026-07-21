#!/usr/bin/env python3
"""
Summarize Android lint baseline XML to make technical debt visible in CI logs.

Outputs:
- baseline file size
- total issue count
- issue-type counts
- top files by issue count (best effort, based on <location file=...>)

Warn mode:
- Optionally emits CI warnings when baseline exceeds a byte threshold.
"""

from __future__ import annotations

import argparse
import pathlib
import xml.etree.ElementTree as ET
from collections import Counter


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Summarize lint baseline XML")
    p.add_argument("--file", default="app/lint-baseline.xml", help="Path to lint-baseline.xml")
    p.add_argument(
        "--warn-max-bytes",
        type=int,
        default=70000,
        help="Emit warning if baseline file size exceeds threshold (default: 70000)",
    )
    p.add_argument(
        "--top-files",
        type=int,
        default=10,
        help="Number of top files to print (default: 10)",
    )
    return p.parse_args()


def ci_warning(message: str) -> None:
    import os

    if os.environ.get("GITHUB_ACTIONS", "").lower() == "true":
        print(f"::warning::{message}")
    else:
        print(f"WARNING: {message}")


def main() -> int:
    args = parse_args()
    path = pathlib.Path(args.file)
    if not path.exists():
        ci_warning(f"Lint baseline file not found: {path}")
        return 0

    size = path.stat().st_size
    tree = ET.parse(path)
    root = tree.getroot()
    issues = list(root.findall(".//issue"))

    issue_counts = Counter()
    file_counts = Counter()
    for issue in issues:
        issue_id = issue.attrib.get("id", "<unknown>")
        issue_counts[issue_id] += 1
        for loc in issue.findall(".//location"):
            f = loc.attrib.get("file")
            if f:
                file_counts[f] += 1

    print("Lint baseline summary")
    print(f"- file: {path}")
    print(f"- size_bytes: {size}")
    print(f"- total_issues: {len(issues)}")
    print("- issue_types:")
    for issue_id, count in issue_counts.most_common():
        print(f"  - {issue_id}: {count}")

    if file_counts:
        print(f"- top_files (max {args.top_files}):")
        for f, count in file_counts.most_common(args.top_files):
            print(f"  - {f}: {count}")

    if size > args.warn_max_bytes:
        ci_warning(
            f"Lint baseline is {size} bytes (> {args.warn_max_bytes}). "
            "Treat as technical debt inventory; avoid further growth."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
