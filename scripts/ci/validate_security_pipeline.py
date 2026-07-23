#!/usr/bin/env python3
"""Validate that CI pipelines scan full Git history before loading secrets."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument(
        "--json-report",
        type=Path,
        default=Path("build/reports/security/security-pipeline.json"),
    )
    return parser.parse_args(argv)


def validate_github_workflow(path: Path, root: Path) -> List[str]:
    """Validate a GitHub Actions workflow file for security best practices."""
    text = path.read_text(encoding="utf-8")
    errors: List[str] = []
    relative = path.relative_to(root)

    # Check for persist-credentials: false on checkout actions
    if "actions/checkout@" in text:
        lines = text.splitlines()
        for i, line in enumerate(lines):
            if "actions/checkout@" in line:
                # Look for persist-credentials in nearby lines (within 5 lines)
                block = "\n".join(lines[max(0, i - 2) : i + 5])
                if "persist-credentials: false" not in block:
                    errors.append(
                        f"{relative}:{i + 1} checkout should set persist-credentials: false"
                    )

    # Check for full history (fetch-depth: 0) where security scanning is performed
    has_security_scan = "security_gate" in text or "run_secret_scan" in text
    has_fetch_depth_0 = "fetch-depth: 0" in text
    if has_security_scan and not has_fetch_depth_0:
        errors.append(
            f"{relative} workflows with security scanning should fetch full history"
        )

    return errors


def validate_security_workflow(root: Path) -> List[str]:
    """Validate that a security.yml workflow exists with required checks."""
    path = root / ".github/workflows/security.yml"
    if not path.is_file():
        return ["Missing .github/workflows/security.yml"]
    text = path.read_text(encoding="utf-8")
    checks = (
        ("run_secret_scan", "security workflow must run secret scanning"),
        ("upload-sarif", "security workflow must upload SARIF reports"),
    )
    return [message for needle, message in checks if needle not in text]


def active_workflow_paths(root: Path) -> List[Path]:
    """Return all GitHub Actions workflow files."""
    workflows_dir = root / ".github/workflows"
    if not workflows_dir.is_dir():
        return []
    return sorted(workflows_dir.glob("*.yml"))


def validate_all(root: Path) -> Tuple[Dict[str, int], List[str]]:
    errors: List[str] = []
    paths = active_workflow_paths(root)
    workflow_count = 0
    for path in paths:
        workflow_count += 1
        errors.extend(validate_github_workflow(path, root))
    errors.extend(validate_security_workflow(root))
    return {"workflowFiles": workflow_count}, errors


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    root = args.root.resolve()
    summary, errors = validate_all(root)
    report = {"summary": summary, "errors": errors}
    report_path = (
        args.json_report if args.json_report.is_absolute() else root / args.json_report
    )
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    if errors:
        print("Security workflow validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print(
        f"Security workflow validation passed: {summary['workflowFiles']} workflow(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
