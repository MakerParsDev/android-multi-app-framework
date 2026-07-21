#!/usr/bin/env python3
"""Validate Android Lint baseline debt and block baseline growth."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from collections import Counter
from pathlib import Path
from typing import Optional

from lint_baseline_inventory import (
    SEARCH_ROOTS,
    BaselineInventory,
    budget_from_inventory,
    collect_inventory,
    compare_inventories,
    load_budget,
    parse_baseline_xml,
    render_report,
    validate_against_budget,
)

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_BUDGET = ROOT / "config/lint-baseline-budget.json"
DEFAULT_REPORT = ROOT / "docs/LINT_BASELINE_INVENTORY.md"


def git(*args: str, root: Path = ROOT) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        text=True,
        capture_output=True,
        check=False,
    )


def git_ref_exists(ref: str, root: Path) -> bool:
    return git("rev-parse", "--verify", "--quiet", ref, root=root).returncode == 0


def fetch_azure_target(branch: str, root: Path) -> str:
    candidate = f"origin/{branch}"
    if git_ref_exists(candidate, root):
        return candidate
    fetch = git(
        "fetch",
        "--no-tags",
        "origin",
        f"{branch}:refs/remotes/origin/{branch}",
        root=root,
    )
    if fetch.returncode != 0 or not git_ref_exists(candidate, root):
        raise ValueError(f"Unable to resolve Azure PR target {candidate}: {fetch.stderr.strip()}")
    return candidate


def resolve_base_ref(explicit: Optional[str], root: Path) -> Optional[str]:
    if explicit:
        return explicit
    azure_target = os.getenv("SYSTEM_PULLREQUEST_TARGETBRANCH", "")
    if azure_target.startswith("refs/heads/"):
        return fetch_azure_target(azure_target[len("refs/heads/"):], root)
    branch_result = git("branch", "--show-current", root=root)
    branch = branch_result.stdout.strip()
    if branch and branch != "main" and git_ref_exists("origin/main", root):
        return "origin/main"
    return None


def inventory_from_git(ref: str, root: Path) -> BaselineInventory:
    listing = git("ls-tree", "-r", "--name-only", ref, "--", *SEARCH_ROOTS, root=root)
    if listing.returncode != 0:
        raise ValueError(f"Unable to list lint baselines from {ref}: {listing.stderr.strip()}")
    by_file: dict[str, Counter[str]] = {}
    paths = sorted(
        line for line in listing.stdout.splitlines() if line.endswith("/lint-baseline.xml")
    )
    for path in paths:
        content = git("show", f"{ref}:{path}", root=root)
        if content.returncode != 0:
            raise ValueError(f"Unable to read {ref}:{path}: {content.stderr.strip()}")
        counts = parse_baseline_xml(content.stdout, f"{ref}:{path}")
        if counts:
            by_file[path] = counts
    return BaselineInventory(by_file)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--budget", type=Path, default=DEFAULT_BUDGET)
    parser.add_argument("--base-ref")
    parser.add_argument("--no-base-comparison", action="store_true")
    parser.add_argument("--write-budget", action="store_true")
    parser.add_argument("--write-report", type=Path)
    parser.add_argument("--check-report", type=Path, default=DEFAULT_REPORT)
    return parser.parse_args()


def resolve_path(path: Path, root: Path) -> Path:
    return path if path.is_absolute() else root / path


def write_budget_if_requested(
    requested: bool,
    path: Path,
    inventory: BaselineInventory,
) -> None:
    if not requested:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(budget_from_inventory(inventory), indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def write_report_if_requested(requested: Optional[Path], root: Path, report: str) -> None:
    if requested is None:
        return
    path = resolve_path(requested, root)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(report, encoding="utf-8")


def validate_report(path: Path, report: str, root: Path) -> list[str]:
    try:
        if path.read_text(encoding="utf-8") == report:
            return []
    except FileNotFoundError:
        pass
    try:
        display_path = path.relative_to(root)
    except ValueError:
        display_path = path
    return [
        f"Lint baseline inventory report is stale: {display_path}; "
        "regenerate with --write-report"
    ]


def run_validation(args: argparse.Namespace) -> tuple[BaselineInventory, Optional[str], list[str]]:
    root = args.root.resolve()
    budget_path = resolve_path(args.budget, root)
    report_path = resolve_path(args.check_report, root)
    inventory, empty_files = collect_inventory(root)
    write_budget_if_requested(args.write_budget, budget_path, inventory)
    budget = load_budget(budget_path)
    errors = validate_against_budget(inventory, empty_files, budget)

    base_ref = None if args.no_base_comparison else resolve_base_ref(args.base_ref, root)
    if base_ref:
        errors.extend(compare_inventories(inventory, inventory_from_git(base_ref, root), base_ref))

    report = render_report(inventory)
    write_report_if_requested(args.write_report, root, report)
    errors.extend(validate_report(report_path, report, root))
    return inventory, base_ref, errors


def print_success(inventory: BaselineInventory, base_ref: Optional[str]) -> None:
    print(
        f"Lint baseline validation passed: {inventory.total} issues across "
        f"{len(inventory.by_file)} files"
    )
    if base_ref:
        print(f"  ratchet base: {base_ref}")
    for path, counts in sorted(inventory.by_file.items()):
        print(f"  {path}: {sum(counts.values())}")


def main() -> int:
    try:
        inventory, base_ref, errors = run_validation(parse_args())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Lint baseline validation failed: {exc}", file=sys.stderr)
        return 1
    if errors:
        print("Lint baseline validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print_success(inventory, base_ref)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
