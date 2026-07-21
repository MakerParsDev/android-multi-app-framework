#!/usr/bin/env python3
"""Regression tests for Gradle warning ownership and expiry policy."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from gradle_warning_audit import (
    build_report,
    extract_warnings,
    match_warnings,
    validate_policy,
    write_reports,
)


VALID_POLICY = {
    "schema_version": 1,
    "known_warnings": [
        {
            "id": "detekt-deprecation",
            "category": "gradle-deprecation",
            "match": "The ReportingExtension.file(String) method has been deprecated.",
            "owner": "build/quality",
            "source": "detekt-gradle-plugin:1.23.8",
            "reason": "Owned upstream Gradle 10 compatibility warning under active review.",
            "expires_on": "2027-01-31",
        },
        {
            "id": "asset-delivery-d8",
            "category": "d8",
            "match": "asset-delivery-ktx-2.3.0-runtime.jar: D8: Invalid stack map table",
            "owner": "feature/audio",
            "source": "asset-delivery-ktx:2.3.0",
            "reason": "Official current dependency emits a known compiler warning.",
            "expires_on": "2027-01-31",
        },
        {
            "id": "asm-tooling",
            "category": "asm-unresolved-classes",
            "match": "ASM Instrumentation process wasn't able to resolve some classes",
            "owner": "build/android",
            "source": "AGP instrumentation classpath",
            "reason": "Tooling and provided classes remain visible for every toolchain review.",
            "expires_on": "2027-01-31",
        },
    ],
}


class GradleWarningAuditTest(unittest.TestCase):
    def policy_entries(self, policy=None):
        entries, errors = validate_policy(
            VALID_POLICY if policy is None else policy,
            date(2026, 7, 11),
        )
        self.assertEqual([], errors)
        return entries

    def test_known_gradle_and_d8_warnings_pass(self) -> None:
        text = """
The ReportingExtension.file(String) method has been deprecated. This is scheduled to be removed.
WARNING: /cache/asset-delivery-ktx-2.3.0-runtime.jar: D8: Invalid stack map table at instruction index 18
"""
        warnings = extract_warnings(text)
        observed, errors = match_warnings(warnings, self.policy_entries())
        self.assertEqual([], errors)
        self.assertEqual(
            ["detekt-deprecation", "asset-delivery-d8"],
            [warning["policyId"] for warning in observed],
        )

    def test_unknown_warning_fails(self) -> None:
        warnings = extract_warnings("WARNING: example-1.0.jar: D8: New optimizer failure")
        _, errors = match_warnings(warnings, self.policy_entries())
        self.assertEqual(1, len(errors))
        self.assertIn("Unapproved d8 warning", errors[0])

    def test_asm_warning_preserves_visible_class_details(self) -> None:
        text = """
ASM Instrumentation process wasn't able to resolve some classes, this means that
Classes that weren't resolved:
> androidx.compose.animation.tooling.ComposeAnimation
> sun.misc.Unsafe
-- 3 more classes --

BUILD SUCCESSFUL
"""
        warnings = extract_warnings(text)
        observed, errors = match_warnings(warnings, self.policy_entries())
        self.assertEqual([], errors)
        self.assertEqual(
            [
                "androidx.compose.animation.tooling.ComposeAnimation",
                "sun.misc.Unsafe",
                "-- 3 more classes --",
            ],
            observed[0]["details"],
        )

    def test_duplicate_repeated_warning_is_deduplicated(self) -> None:
        line = "The ReportingExtension.file(String) method has been deprecated."
        warnings = extract_warnings(f"{line}\n{line}\n")
        self.assertEqual(1, len(warnings))

    def test_expired_policy_entry_fails(self) -> None:
        policy = json.loads(json.dumps(VALID_POLICY))
        policy["known_warnings"][0]["expires_on"] = "2026-01-01"
        _, errors = validate_policy(policy, date(2026, 7, 11))
        self.assertTrue(any("expired on 2026-01-01" in error for error in errors))

    def test_incomplete_policy_entry_fails(self) -> None:
        policy = json.loads(json.dumps(VALID_POLICY))
        del policy["known_warnings"][0]["owner"]
        _, errors = validate_policy(policy, date(2026, 7, 11))
        self.assertTrue(any("must define non-empty owner" in error for error in errors))

    def test_report_output_is_deterministic(self) -> None:
        warnings = extract_warnings(
            "The ReportingExtension.file(String) method has been deprecated.\n"
        )
        entries = self.policy_entries()
        observed, errors = match_warnings(warnings, entries)
        self.assertEqual([], errors)
        report = build_report(Path("gradle.log"), observed, entries)
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            json_path = root / "warning-audit.json"
            markdown_path = root / "warning-audit.md"
            write_reports(report, json_path, markdown_path)
            first_json = json_path.read_text(encoding="utf-8")
            first_markdown = markdown_path.read_text(encoding="utf-8")
            write_reports(report, json_path, markdown_path)
            self.assertEqual(first_json, json_path.read_text(encoding="utf-8"))
            self.assertEqual(first_markdown, markdown_path.read_text(encoding="utf-8"))

    def test_asm_details_stop_at_first_non_detail_line(self) -> None:
        text = """
ASM Instrumentation process wasn't able to resolve some classes, this means that
Classes that weren't resolved:
> androidx.compose.animation.tooling.ComposeAnimation
BUILD SUCCESSFUL
> unrelated.later.LogLine
"""
        warnings = extract_warnings(text)
        self.assertEqual(
            ["androidx.compose.animation.tooling.ComposeAnimation"],
            warnings[0]["details"],
        )


if __name__ == "__main__":
    unittest.main()
