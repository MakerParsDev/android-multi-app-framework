#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = [
    "amenerrasulu",
    "ayetelkursi",
    "bereketduasi",
    "esmaulhusna",
    "fetihsuresi",
    "insirahsuresi",
    "ismiazamduasi",
    "kenzularsduasi",
    "kible",
    "kuran_kerim",
    "mucizedualar",
    "namazsurelerivedualarsesli",
    "namazvakitleri",
    "nazarayeti",
    "vakiasuresi",
    "yasinsuresi",
    "zikirmatik",
]


class PerformanceProfileStructureTest(unittest.TestCase):
    def test_toolchain_and_module_are_pinned(self) -> None:
        catalog = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        self.assertIn('baselineProfile = "1.5.0-alpha07"', catalog)
        self.assertIn('benchmark = "1.4.1"', catalog)
        self.assertIn('profileInstaller = "1.4.1"', catalog)
        self.assertIn('uiautomator = "2.4.0"', catalog)
        self.assertIn(
            'androidx-baselineprofile = { id = "androidx.baselineprofile"',
            catalog,
        )
        self.assertIn('android-test = { id = "com.android.test"', catalog)
        self.assertTrue((ROOT / "performance/benchmark/build.gradle.kts").is_file())
        self.assertIn(
            'include(":performance:benchmark")',
            (ROOT / "settings.gradle.kts").read_text(encoding="utf-8"),
        )

    def test_prerelease_exception_is_narrow_and_expiring(self) -> None:
        policy = json.loads(
            (ROOT / "config/dependency-policy.json").read_text(encoding="utf-8")
        )
        entries = {
            entry["alias"]: entry
            for entry in policy["catalog_prerelease_allowlist"]
        }
        entry = entries["androidx-baselineprofile"]
        self.assertEqual("ci/android-performance", entry["owner"])
        self.assertEqual("2027-01-31", entry["expires_on"])
        self.assertGreaterEqual(len(entry["reason"]), 12)

    def test_app_consumer_preserves_all_release_task_validation(self) -> None:
        source = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("alias(libs.plugins.androidx.baselineprofile)", source)
        self.assertIn('baselineProfile(project(":performance:benchmark"))', source)
        self.assertIn('normalized.contains("Release")', source)
        self.assertIn('!isGeneratedPerformanceVariant(normalized)', source)
        self.assertNotIn('normalized.endsWith("Release")', source)

    def test_benchmark_build_declares_all_catalog_flavors(self) -> None:
        source = (ROOT / "performance/benchmark/build.gradle.kts").read_text(
            encoding="utf-8"
        )
        self.assertIn("AppFlavors.all.forEach", source)
        self.assertIn('dimension = "app"', source)
        self.assertIn('targetProjectPath = ":app"', source)
        catalog = (
            ROOT / "buildSrc/src/main/kotlin/FlavorConfig.kt"
        ).read_text(encoding="utf-8")
        actual = re.findall(r'name = "([a-z0-9_]+)"', catalog)
        self.assertEqual(EXPECTED, actual)


if __name__ == "__main__":
    unittest.main()
