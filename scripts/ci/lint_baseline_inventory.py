#!/usr/bin/env python3
"""Android Lint baseline inventory, budget, comparison, and reporting helpers."""

from __future__ import annotations

import json
import os
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

SEARCH_ROOTS = ("app", "core", "feature")

RISK_BY_ID = {
    "DefaultLocale": "correctness",
    "InlinedApi": "correctness",
    "InvalidPackage": "correctness",
    "MissingTranslation": "localization",
    "RestrictedApi": "correctness",
    "DiscouragedApi": "correctness",
    "SyntheticAccessor": "performance",
    "Overdraw": "performance",
    "ConvertToWebp": "performance",
    "UnusedResources": "resource-hygiene",
    "IconLocation": "resource-hygiene",
    "IconExpectedSize": "resource-hygiene",
    "IconDuplicates": "resource-hygiene",
    "DuplicateStrings": "localization",
    "PluralsCandidate": "localization",
    "TypographyQuotes": "localization",
    "TypographyEllipsis": "localization",
    "TypographyFractions": "localization",
    "ComposableLambdaParameterNaming": "compose-api",
    "ComposableLambdaParameterPosition": "compose-api",
    "ModifierParameter": "compose-api",
    "MemberExtensionConflict": "maintainability",
    "UseKtx": "maintainability",
    "NewerVersionAvailable": "dependency-lifecycle",
    "GradleDependency": "dependency-lifecycle",
    "AndroidGradlePluginVersion": "dependency-lifecycle",
}


@dataclass(frozen=True)
class BaselineInventory:
    by_file: dict[str, Counter[str]]

    @property
    def total(self) -> int:
        return sum(sum(counts.values()) for counts in self.by_file.values())

    @property
    def by_id(self) -> Counter[str]:
        total: Counter[str] = Counter()
        for counts in self.by_file.values():
            total.update(counts)
        return total


def parse_baseline_xml(text: str, source: str) -> Counter[str]:
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        raise ValueError(f"Malformed lint baseline {source}: {exc}") from exc
    if root.tag != "issues":
        raise ValueError(f"Unexpected lint baseline root in {source}: {root.tag}")
    return Counter(issue.attrib.get("id", "UNKNOWN") for issue in root.findall("issue"))


def discover_baseline_paths(root: Path) -> list[Path]:
    paths: set[Path] = set()
    for search_root in SEARCH_ROOTS:
        base = root / search_root
        if base.exists():
            for dirpath, dirnames, filenames in os.walk(base):
                dirnames[:] = [name for name in dirnames if name not in {"build", ".gradle"}]
                if "lint-baseline.xml" in filenames:
                    paths.add(Path(dirpath) / "lint-baseline.xml")
    return sorted(paths)


def collect_inventory(root: Path) -> tuple[BaselineInventory, list[str]]:
    by_file: dict[str, Counter[str]] = {}
    empty_files: list[str] = []
    for path in discover_baseline_paths(root):
        relative = path.relative_to(root).as_posix()
        counts = parse_baseline_xml(path.read_text(encoding="utf-8"), relative)
        if counts:
            by_file[relative] = counts
        else:
            empty_files.append(relative)
    return BaselineInventory(by_file), empty_files


def load_budget(path: Path) -> dict[str, object]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"Malformed budget JSON in {path}: expected an object")
    if data.get("schema_version") != 1:
        raise ValueError(f"Unsupported lint baseline budget schema: {data.get('schema_version')}")
    return data


def budget_from_inventory(inventory: BaselineInventory) -> dict[str, object]:
    files: dict[str, object] = {}
    for path, counts in sorted(inventory.by_file.items()):
        files[path] = {
            "maximum_issues": sum(counts.values()),
            "maximum_by_id": dict(sorted(counts.items())),
        }
    return {
        "schema_version": 1,
        "maximum_total": inventory.total,
        "files": files,
    }


def validate_empty_files(empty_files: Iterable[str]) -> list[str]:
    empty = sorted(empty_files)
    if not empty:
        return []
    return ["Empty lint baseline files are not allowed: " + ", ".join(empty)]


def validate_budget_paths(
    inventory: BaselineInventory,
    budget_files: dict[str, object],
) -> list[str]:
    errors: list[str] = []
    actual_paths = set(inventory.by_file)
    expected_paths = set(budget_files)
    for path in sorted(actual_paths - expected_paths):
        errors.append(f"Unbudgeted lint baseline file: {path}")
    for path in sorted(expected_paths - actual_paths):
        errors.append(f"Budget references missing/non-populated lint baseline: {path}")
    return errors


def validate_issue_budgets(
    path: str,
    actual_counts: Counter[str],
    maximum_by_id: object,
) -> list[str]:
    if not isinstance(maximum_by_id, dict):
        return [f"Budget maximum_by_id must be an object: {path}"]
    errors: list[str] = []
    for issue_id, actual_count in sorted(actual_counts.items()):
        allowed = int(maximum_by_id.get(issue_id, 0))
        if actual_count > allowed:
            errors.append(
                f"Lint baseline issue grew for {path} [{issue_id}]: "
                f"{actual_count} > budget {allowed}"
            )
    return errors


def validate_file_budget(
    path: str,
    actual_counts: Counter[str],
    raw_rules: object,
) -> tuple[int, list[str]]:
    if not isinstance(raw_rules, dict):
        return 0, [f"Budget entry must be an object: {path}"]
    maximum_issues = int(raw_rules.get("maximum_issues", -1))
    errors = validate_issue_budgets(path, actual_counts, raw_rules.get("maximum_by_id"))
    actual_total = sum(actual_counts.values())
    if actual_total > maximum_issues:
        errors.append(
            f"Lint baseline grew for {path}: {actual_total} > budget {maximum_issues}"
        )
    return maximum_issues, errors


def validate_budget_totals(
    inventory: BaselineInventory,
    configured_total: int,
    summed_budget: int,
) -> list[str]:
    errors: list[str] = []
    if configured_total != summed_budget:
        errors.append(
            f"Budget maximum_total mismatch: {configured_total} != file budget sum {summed_budget}"
        )
    if inventory.total > configured_total:
        errors.append(f"Total lint baseline grew: {inventory.total} > budget {configured_total}")
    return errors


def validate_against_budget(
    inventory: BaselineInventory,
    empty_files: Iterable[str],
    budget: dict[str, object],
) -> list[str]:
    errors = validate_empty_files(empty_files)
    budget_files = budget.get("files")
    if not isinstance(budget_files, dict):
        return [*errors, "Budget field 'files' must be an object"]

    errors.extend(validate_budget_paths(inventory, budget_files))
    summed_budget = 0
    for path, raw_rules in sorted(budget_files.items()):
        maximum, file_errors = validate_file_budget(
            path,
            inventory.by_file.get(path, Counter()),
            raw_rules,
        )
        summed_budget += maximum
        errors.extend(file_errors)

    configured_total = int(budget.get("maximum_total", -1))
    errors.extend(validate_budget_totals(inventory, configured_total, summed_budget))
    return errors


def compare_file_counts(
    path: str,
    current_counts: Counter[str],
    base_counts: Counter[str],
    base_ref: str,
) -> list[str]:
    errors: list[str] = []
    current_total = sum(current_counts.values())
    base_total = sum(base_counts.values())
    if current_total > base_total:
        errors.append(
            f"Lint baseline grew vs {base_ref} for {path}: {current_total} > {base_total}"
        )
    for issue_id, current_count in sorted(current_counts.items()):
        base_count = base_counts.get(issue_id, 0)
        if current_count > base_count:
            errors.append(
                f"Lint baseline issue grew vs {base_ref} for {path} [{issue_id}]: "
                f"{current_count} > {base_count}"
            )
    return errors


def compare_inventories(
    current: BaselineInventory,
    base: BaselineInventory,
    base_ref: str,
) -> list[str]:
    errors: list[str] = []
    for path in sorted(set(current.by_file) | set(base.by_file)):
        errors.extend(
            compare_file_counts(
                path,
                current.by_file.get(path, Counter()),
                base.by_file.get(path, Counter()),
                base_ref,
            )
        )
    if current.total > base.total:
        errors.append(f"Total lint baseline grew vs {base_ref}: {current.total} > {base.total}")
    return errors


def risk_summary(inventory: BaselineInventory) -> Counter[str]:
    summary: Counter[str] = Counter()
    for issue_id, count in inventory.by_id.items():
        summary[RISK_BY_ID.get(issue_id, "other")] += count
    return summary


def render_report(inventory: BaselineInventory) -> str:
    lines = [
        "# Android Lint Baseline Inventory",
        "",
        "This file is generated by `scripts/ci/validate_lint_baselines.py`.",
        "The committed budget and branch-to-main comparison prevent baseline growth.",
        "",
        f"**Current baseline debt: {inventory.total} issues across {len(inventory.by_file)} files.**",
        "",
        "## Baseline files",
        "",
        "| Baseline | Issues |",
        "|---|---:|",
    ]
    for path, counts in sorted(inventory.by_file.items()):
        lines.append(f"| `{path}` | {sum(counts.values())} |")

    lines.extend(["", "## Risk categories", "", "| Category | Issues |", "|---|---:|"])
    for category, count in sorted(risk_summary(inventory).items()):
        lines.append(f"| {category} | {count} |")

    lines.extend(["", "## Issue IDs", "", "| Lint ID | Issues |", "|---|---:|"])
    for issue_id, count in inventory.by_id.most_common():
        lines.append(f"| `{issue_id}` | {count} |")

    lines.extend(
        [
            "",
            "## Ratchet policy",
            "",
            "- A PR may reduce baseline debt, but may not increase total, file-level, or per-lint-ID counts.",
            "- New non-empty baseline files are blocked until explicitly budgeted and reviewed.",
            "- Empty baseline files are forbidden; modules without debt must not carry placeholder baselines.",
            "- Current warnings must be fixed or justified; they must not be hidden by refreshing baselines.",
            "",
        ]
    )
    return "\n".join(lines)
