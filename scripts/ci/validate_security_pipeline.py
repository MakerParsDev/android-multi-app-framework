#!/usr/bin/env python3
"""Validate that Azure pipelines scan full Git history before loading secrets."""

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


def indentation(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def checkout_positions(lines: Sequence[str]) -> List[int]:
    return [index for index, line in enumerate(lines) if line.strip() == "- checkout: self"]


def next_step_index(lines: Sequence[str], checkout_index: int) -> int:
    step_indent = indentation(lines[checkout_index])
    for index in range(checkout_index + 1, len(lines)):
        line = lines[index]
        if line.strip() and indentation(line) == step_indent and line.lstrip().startswith("- "):
            return index
    return -1


def checkout_has_full_history(lines: Sequence[str], checkout_index: int) -> bool:
    step_indent = indentation(lines[checkout_index])
    for line in lines[checkout_index + 1 :]:
        cleaned = line.strip()
        if not cleaned or cleaned.startswith("#"):
            continue
        if indentation(line) <= step_indent:
            break
        key, separator, raw_value = cleaned.partition(":")
        value = raw_value.strip().strip("'\"")
        if separator and key.strip() == "fetchDepth" and value == "0":
            return True
    return False


def step_block(lines: Sequence[str], start_index: int) -> str:
    step_indent = indentation(lines[start_index])
    end_index = len(lines)
    for index in range(start_index + 1, len(lines)):
        line = lines[index]
        if line.strip() and indentation(line) == step_indent and line.lstrip().startswith("- "):
            end_index = index
            break
    return "\n".join(lines[start_index:end_index])


def is_security_first_step(lines: Sequence[str], checkout_index: int) -> bool:
    next_index = next_step_index(lines, checkout_index)
    if next_index < 0:
        return False
    step = step_block(lines, next_index)
    return "security-gate.yml" in step or "setup-android.yml" in step


def active_pipeline_paths(root: Path) -> List[Path]:
    return sorted(
        [*root.glob("azure-pipelines/*.yml"), *root.glob("pipelines/azure-pipelines*.yml")]
    )


def validate_pipeline_file(path: Path, root: Path) -> Tuple[int, List[str]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    errors: List[str] = []
    positions = checkout_positions(lines)
    for index in positions:
        line_number = index + 1
        relative = path.relative_to(root)
        if not checkout_has_full_history(lines, index):
            errors.append(f"{relative}:{line_number} checkout must set fetchDepth: 0")
        if not is_security_first_step(lines, index):
            errors.append(
                f"{relative}:{line_number} first post-checkout step must be security-gate.yml "
                "or setup-android.yml"
            )
    return len(positions), errors


def validate_setup_template(root: Path) -> List[str]:
    path = root / "pipelines/templates/steps/setup-android.yml"
    if not path.is_file():
        return ["Missing setup-android.yml template"]
    text = path.read_text(encoding="utf-8")
    first_step = next((line.strip() for line in text.splitlines() if line.strip().startswith("- ")), "")
    if first_step != "- template: security-gate.yml":
        return ["setup-android.yml must run security-gate.yml as its first step"]
    return []


def validate_security_audit(root: Path) -> List[str]:
    path = root / "azure-pipelines/security-audit.yml"
    if not path.is_file():
        return ["Missing scheduled Azure security audit pipeline"]
    text = path.read_text(encoding="utf-8")
    checks = (
        ("schedules:", "security audit pipeline must define a schedule"),
        ("fetchDepth: 0", "security audit pipeline must fetch full history"),
        ("selfTest: true", "security audit pipeline must run the synthetic leak self-test"),
        ("auditDependencyCatalog", "security audit pipeline must run dependency policy audit"),
    )
    return [message for needle, message in checks if needle not in text]


def validate_all(root: Path) -> Tuple[Dict[str, int], List[str]]:
    errors: List[str] = []
    paths = active_pipeline_paths(root)
    checkout_count = 0
    for path in paths:
        count, file_errors = validate_pipeline_file(path, root)
        checkout_count += count
        errors.extend(file_errors)
    errors.extend(validate_setup_template(root))
    errors.extend(validate_security_audit(root))
    return {"pipelineFiles": len(paths), "checkoutSteps": checkout_count}, errors


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    root = args.root.resolve()
    summary, errors = validate_all(root)
    report = {"summary": summary, "errors": errors}
    report_path = args.json_report if args.json_report.is_absolute() else root / args.json_report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if errors:
        print("Azure security pipeline validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print(
        f"Azure security pipeline validation passed: {summary['pipelineFiles']} file(s), "
        f"{summary['checkoutSteps']} checkout step(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
