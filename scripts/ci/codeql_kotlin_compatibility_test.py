#!/usr/bin/env python3
"""Regression tests for the stable CodeQL/Kotlin compiler compatibility boundary."""

from __future__ import annotations

import tomllib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERSION_CATALOG = ROOT / "gradle/libs.versions.toml"
CODEQL_WORKFLOW = ROOT / ".github/workflows/codeql.yml"
CODEQL_KOTLIN_MAX_EXCLUSIVE = (2, 4, 10)


def numeric_version(value: str) -> tuple[int, ...]:
    return tuple(int(part) for part in value.split("."))


class CodeqlKotlinCompatibilityTest(unittest.TestCase):
    def test_kotlin_compiler_stays_below_stable_codeql_ceiling(self) -> None:
        catalog = tomllib.loads(VERSION_CATALOG.read_text(encoding="utf-8"))
        kotlin_version = catalog["versions"]["kotlin"]
        self.assertLess(
            numeric_version(kotlin_version),
            CODEQL_KOTLIN_MAX_EXCLUSIVE,
            "CodeQL bundle 2.26.1 supports Kotlin versions below 2.4.10",
        )

    def test_workflow_does_not_claim_an_ineffective_interceptor_bypass(self) -> None:
        workflow = CODEQL_WORKFLOW.read_text(encoding="utf-8")
        self.assertNotIn("disableKotlinInterceptor", workflow)
        self.assertNotIn("DISABLE_KOTLIN_INTERCEPTOR", workflow)


if __name__ == "__main__":
    unittest.main()
