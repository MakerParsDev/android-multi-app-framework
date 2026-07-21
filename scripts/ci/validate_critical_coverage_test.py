#!/usr/bin/env python3
"""Regression tests for critical Kover coverage validation."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import validate_critical_coverage as coverage


REPORT = """<?xml version="1.0" encoding="UTF-8"?>
<report name="test">
  <package name="com/example/critical">
    <class name="com/example/critical/DecisionKt" sourcefilename="Decision.kt">
      <counter type="BRANCH" missed="1" covered="9"/>
      <counter type="LINE" missed="2" covered="18"/>
    </class>
    <counter type="BRANCH" missed="6" covered="14"/>
    <counter type="LINE" missed="20" covered="20"/>
  </package>
</report>
"""


class ValidateCriticalCoverageTest(unittest.TestCase):
    def test_parses_class_and_package_counters(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            report = Path(temp_dir) / "coverage.xml"
            report.write_text(REPORT, encoding="utf-8")

            entities = coverage.parse_coverage_report(report)

        self.assertEqual(90.0, entities[("class", "com/example/critical/DecisionKt")]["LINE"].percentage)
        self.assertEqual(50.0, entities[("package", "com/example/critical")]["LINE"].percentage)

    def test_threshold_evaluation_passes_at_exact_boundary(self) -> None:
        targets = [
            coverage.CoverageTarget(
                name="Decision",
                entity="class",
                path="com/example/critical/DecisionKt",
                minimum_line=90.0,
                minimum_branch=90.0,
            )
        ]
        entities = {
            ("class", "com/example/critical/DecisionKt"): {
                "LINE": coverage.CounterValue(covered=9, missed=1),
                "BRANCH": coverage.CounterValue(covered=9, missed=1),
            }
        }

        results, errors = coverage.evaluate_targets(targets, entities)

        self.assertEqual(1, len(results))
        self.assertEqual([], errors)

    def test_threshold_evaluation_reports_undercoverage(self) -> None:
        targets = [
            coverage.CoverageTarget(
                name="Decision",
                entity="class",
                path="com/example/critical/DecisionKt",
                minimum_line=91.0,
                minimum_branch=None,
            )
        ]
        entities = {
            ("class", "com/example/critical/DecisionKt"): {
                "LINE": coverage.CounterValue(covered=9, missed=1),
            }
        }

        _, errors = coverage.evaluate_targets(targets, entities)

        self.assertEqual(1, len(errors))
        self.assertIn("90.00% is below 91.00%", errors[0])

    def test_missing_target_is_blocking(self) -> None:
        target = coverage.CoverageTarget(
            name="Missing",
            entity="package",
            path="com/example/missing",
            minimum_line=1.0,
            minimum_branch=None,
        )

        _, errors = coverage.evaluate_targets([target], {})

        self.assertEqual(1, len(errors))
        self.assertIn("Missing package coverage target", errors[0])

    def test_zero_denominator_is_blocking(self) -> None:
        target = coverage.CoverageTarget(
            name="Empty",
            entity="class",
            path="com/example/EmptyKt",
            minimum_line=1.0,
            minimum_branch=None,
        )
        entities = {
            ("class", "com/example/EmptyKt"): {
                "LINE": coverage.CounterValue(covered=0, missed=0),
            }
        }

        _, errors = coverage.evaluate_targets([target], entities)

        self.assertEqual(["Empty has no measurable line coverage"], errors)

    def test_load_targets_normalizes_dot_paths(self) -> None:
        raw = {
            "schema_version": 1,
            "targets": [
                {
                    "name": "Decision",
                    "entity": "class",
                    "path": "com.example.critical.DecisionKt",
                    "minimum_line": 80,
                }
            ],
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "config.json"
            config.write_text(json.dumps(raw), encoding="utf-8")

            targets = coverage.load_targets(config)

        self.assertEqual("com/example/critical/DecisionKt", targets[0].path)

    def test_load_targets_rejects_duplicate_entity_path(self) -> None:
        raw = {
            "schema_version": 1,
            "targets": [
                {
                    "name": "First",
                    "entity": "class",
                    "path": "com/example/DecisionKt",
                    "minimum_line": 80,
                },
                {
                    "name": "Second",
                    "entity": "class",
                    "path": "com.example.DecisionKt",
                    "minimum_line": 90,
                },
            ],
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "config.json"
            config.write_text(json.dumps(raw), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "Duplicate coverage target"):
                coverage.load_targets(config)

    def test_load_targets_rejects_invalid_threshold(self) -> None:
        raw = {
            "schema_version": 1,
            "targets": [
                {
                    "name": "Decision",
                    "entity": "class",
                    "path": "com/example/DecisionKt",
                    "minimum_line": 101,
                }
            ],
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            config = Path(temp_dir) / "config.json"
            config.write_text(json.dumps(raw), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "between 0 and 100"):
                coverage.load_targets(config)


if __name__ == "__main__":
    unittest.main()
