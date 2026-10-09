#!/usr/bin/env python3
"""Regression tests for the stable CodeQL/Kotlin compiler compatibility boundary."""

from __future__ import annotations

import json
import tomllib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERSION_CATALOG = ROOT / "gradle/libs.versions.toml"
CODEQL_WORKFLOW = ROOT / ".github/workflows/codeql.yml"
CODEQL_POLICY = ROOT / "config/codeql-compatibility-policy.json"


def numeric_version(value: str) -> tuple[int, ...]:
    return tuple(int(part) for part in value.split("."))


class CodeqlKotlinCompatibilityTest(unittest.TestCase):
    def test_kotlin_compiler_stays_below_live_codeql_ceiling(self) -> None:
        self.assertTrue(CODEQL_POLICY.is_file(), "config/codeql-compatibility-policy.json must exist")
        policy = json.loads(CODEQL_POLICY.read_text(encoding="utf-8"))
        self.assertEqual(policy.get("schema_version"), 2)
        max_exclusive_str = policy["kotlin"]["supported_max_exclusive"]
        max_exclusive = numeric_version(max_exclusive_str)

        catalog = tomllib.loads(VERSION_CATALOG.read_text(encoding="utf-8"))
        kotlin_version = catalog["versions"]["kotlin"]
        self.assertLess(
            numeric_version(kotlin_version),
            max_exclusive,
            f"Live CodeQL bundle {policy.get('codeql_bundle_version')} supports Kotlin versions below {max_exclusive_str}",
        )
        self.assertEqual(
            policy["kotlin"]["current_configured_version"],
            kotlin_version,
            "CodeQL compatibility policy must track the configured Kotlin version exactly",
        )

        self.assertGreaterEqual(
            numeric_version(kotlin_version),
            numeric_version("2.4.20"),
            "GHSA-r937-wjx7-w2jp is fixed only by Kotlin 2.4.20+ stable",
        )
        self.assertNotIn(
            "blocked_security_upgrade", policy["kotlin"],
            "A fixed advisory must not remain an active exception",
        )
        self.assertEqual(policy["codeql_bundle_version"], "2.27.1")
        self.assertEqual(policy["kotlin"]["checked_on"], "2026-10-09")

    def test_workflow_does_not_claim_an_ineffective_interceptor_bypass(self) -> None:
        workflow = CODEQL_WORKFLOW.read_text(encoding="utf-8")
        self.assertNotIn("disableKotlinInterceptor", workflow)
        self.assertNotIn("DISABLE_KOTLIN_INTERCEPTOR", workflow)


if __name__ == "__main__":
    unittest.main()
