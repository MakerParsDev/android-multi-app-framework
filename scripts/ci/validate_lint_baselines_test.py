#!/usr/bin/env python3
"""Regression tests for Android Lint baseline ratcheting."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import lint_baseline_inventory as MODULE
import validate_lint_baselines as CLI


def inventory(**files: dict[str, int]):
    return MODULE.BaselineInventory(
        {path: Counter(counts) for path, counts in files.items()}
    )


class ValidateLintBaselinesTest(unittest.TestCase):
    def test_budget_accepts_exact_inventory(self) -> None:
        current = inventory(**{"app/lint-baseline.xml": {"UnusedResources": 2}})
        budget = MODULE.budget_from_inventory(current)
        self.assertEqual([], MODULE.validate_against_budget(current, [], budget))

    def test_budget_blocks_issue_id_growth(self) -> None:
        base = inventory(**{"app/lint-baseline.xml": {"UnusedResources": 2}})
        current = inventory(
            **{"app/lint-baseline.xml": {"UnusedResources": 2, "RestrictedApi": 1}}
        )
        errors = MODULE.validate_against_budget(
            current,
            [],
            MODULE.budget_from_inventory(base),
        )
        self.assertTrue(any("RestrictedApi" in error for error in errors))

    def test_empty_baseline_is_rejected(self) -> None:
        current = inventory(**{"app/lint-baseline.xml": {"UnusedResources": 1}})
        errors = MODULE.validate_against_budget(
            current,
            ["core/common/lint-baseline.xml"],
            MODULE.budget_from_inventory(current),
        )
        self.assertTrue(any("Empty lint baseline" in error for error in errors))

    def test_branch_comparison_allows_reduction(self) -> None:
        base = inventory(**{"app/lint-baseline.xml": {"UnusedResources": 3}})
        current = inventory(**{"app/lint-baseline.xml": {"UnusedResources": 2}})
        self.assertEqual([], MODULE.compare_inventories(current, base, "origin/main"))

    def test_branch_comparison_blocks_file_and_id_growth(self) -> None:
        base = inventory(**{"app/lint-baseline.xml": {"UnusedResources": 1}})
        current = inventory(
            **{
                "app/lint-baseline.xml": {"UnusedResources": 2},
                "feature/new/lint-baseline.xml": {"SyntheticAccessor": 1},
            }
        )
        errors = MODULE.compare_inventories(current, base, "origin/main")
        self.assertTrue(any("UnusedResources" in error for error in errors))
        self.assertTrue(any("feature/new" in error for error in errors))

    def test_parser_reads_issue_counts(self) -> None:
        xml = """<?xml version='1.0'?><issues><issue id='A'/><issue id='A'/><issue id='B'/></issues>"""
        self.assertEqual(Counter({"A": 2, "B": 1}), MODULE.parse_baseline_xml(xml, "test"))

    def test_discovery_ignores_build_and_gradle_directories(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            valid = root / "app/lint-baseline.xml"
            generated = root / "app/build/intermediates/lint-baseline.xml"
            cached = root / "feature/sample/.gradle/lint-baseline.xml"
            valid.parent.mkdir(parents=True, exist_ok=True)
            valid.write_text("<issues><issue id='A'/></issues>", encoding="utf-8")
            for path in (generated, cached):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("not xml", encoding="utf-8")
            self.assertEqual([valid], MODULE.discover_baseline_paths(root))
            inventory, empty_files = MODULE.collect_inventory(root)
            self.assertEqual(1, inventory.total)
            self.assertEqual([], empty_files)

    def test_budget_loader_rejects_non_object_json(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "budget.json"
            path.write_text(json.dumps([]), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "expected an object"):
                MODULE.load_budget(path)

    def test_missing_report_returns_actionable_validation_error(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            missing = root / "docs/LINT_BASELINE_INVENTORY.md"
            errors = CLI.validate_report(missing, "expected", root)
            self.assertEqual(1, len(errors))
            self.assertIn("regenerate with --write-report", errors[0])

    def test_external_report_path_is_rendered_without_relative_path_error(self) -> None:
        with tempfile.TemporaryDirectory() as root_dir, tempfile.TemporaryDirectory() as report_dir:
            root = Path(root_dir)
            report = Path(report_dir) / "inventory.md"
            errors = CLI.validate_report(report, "expected", root)
            self.assertEqual(1, len(errors))
            self.assertIn(str(report), errors[0])


if __name__ == "__main__":
    unittest.main()
