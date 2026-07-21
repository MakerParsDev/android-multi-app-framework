#!/usr/bin/env python3
"""Regression tests for Android toolchain drift validation."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("validate_android_toolchain_config.py")
SPEC = importlib.util.spec_from_file_location("validate_android_toolchain_config", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load validator module: {MODULE_PATH}")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

VALID_VALUES = {
    MODULE.JAVA_MAJOR_KEY: "21",
    MODULE.COMPILE_SDK_KEY: "37",
    MODULE.PLATFORM_PACKAGE_KEY: "37.0",
    MODULE.TARGET_SDK_KEY: "36",
    MODULE.MIN_SDK_KEY: "24",
    MODULE.BUILD_TOOLS_KEY: "37.0.0",
    MODULE.CMDLINE_TOOLS_KEY: "21.0",
    MODULE.BOOTSTRAP_CMDLINE_TOOLS_REVISION_KEY: "14742923",
}


class ValidateAndroidToolchainConfigTest(unittest.TestCase):
    def test_comments_do_not_trigger_hardcode_findings(self) -> None:
        text = """
        // compileSdk = 37
        // jvmToolchain(21)
        android { // targetSdk = 36
        }
        """
        self.assertEqual([], MODULE.find_hardcoded_toolchain_values(text))

    def test_real_hardcodes_report_exact_lines(self) -> None:
        text = """android {
    compileSdk = 37 // must be detected
    defaultConfig { minSdk = 24 }
}
"""
        self.assertEqual(
            [(2, "compileSdk"), (3, "minSdk")],
            MODULE.find_hardcoded_toolchain_values(text),
        )

    def test_valid_manifest_has_no_errors(self) -> None:
        self.assertEqual([], MODULE.validate_manifest(VALID_VALUES.copy()))

    def test_invalid_version_prefix_is_reported_without_crashing(self) -> None:
        values = VALID_VALUES.copy()
        values[MODULE.PLATFORM_PACKAGE_KEY] = "preview"
        self.assertEqual(
            ["platformPackage must start with an integer: preview"],
            MODULE.validate_manifest(values),
        )

    def test_missing_manifest_keys_are_reported(self) -> None:
        values = VALID_VALUES.copy()
        del values[MODULE.BUILD_TOOLS_KEY]
        errors = MODULE.validate_manifest(values)
        self.assertEqual(1, len(errors))
        self.assertIn(MODULE.BUILD_TOOLS_KEY, errors[0])


if __name__ == "__main__":
    unittest.main()
