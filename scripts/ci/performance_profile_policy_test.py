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
    def test_macrobenchmarks_define_required_metrics(self) -> None:
        startup = (
            ROOT
            / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/StartupBenchmarks.kt"
        ).read_text(encoding="utf-8")
        frames = (
            ROOT
            / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/FrameBenchmarks.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("StartupTimingMetric", startup)
        self.assertIn("StartupMode.COLD", startup)
        self.assertIn("StartupMode.WARM", startup)
        self.assertIn("CompilationMode.None", startup)
        self.assertIn("BaselineProfileMode.Require", startup)
        self.assertIn("FrameTimingMetric", frames)
        self.assertIn("CriticalUserJourneys.run", frames)

    def test_generator_separates_startup_and_other_journeys(self) -> None:
        source = (
            ROOT
            / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/BaselineProfileGenerator.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("BaselineProfileRule", source)
        self.assertIn("includeInStartupProfile = true", source)
        self.assertIn("includeInStartupProfile = false", source)
        self.assertIn("CriticalUserJourneys.startup", source)
        self.assertIn("CriticalUserJourneys.run", source)

    def test_performance_config_maps_all_families_and_clamps_iterations(self) -> None:
        source = (
            ROOT
            / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/PerformanceConfig.kt"
        ).read_text(encoding="utf-8")
        for family in (
            '"content", "esma", "prayer_library" -> AUDIO_CONTENT',
            '"quran" -> QURAN',
            '"miracles" -> MIRACLES',
            '"prayer_times" -> PRAYER_TIMES',
            '"qibla" -> QIBLA',
            '"zikir_counter" -> COUNTER',
        ):
            self.assertIn(family, source)
        self.assertIn("coerceIn(5, 30)", source)
        self.assertIn("Missing performanceFlavor instrumentation argument", source)
        self.assertIn("Missing performanceFamily instrumentation argument", source)

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

    def test_benchmark_tag_constants_match_ui_contract(self) -> None:
        source = (
            ROOT
            / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/PerformanceTags.kt"
        ).read_text(encoding="utf-8")
        for tag in (
            "APP_ROOT", "PRIMARY_NAVIGATION", "CONTENT_LIST", "CONTENT_FIRST_ITEM",
            "CONTENT_DETAIL", "AUDIO_PLAY_PAUSE", "MIRACLES_LIST",
            "MIRACLES_FIRST_ITEM", "MIRACLES_DETAIL", "QURAN_LIST",
            "QURAN_FIRST_ITEM", "QURAN_DETAIL", "PRAYER_TIMES_READY",
            "QIBLA_READY", "COUNTER_ROOT", "COUNTER_VALUE", "COUNTER_INCREMENT",
        ):
            self.assertIn(f"const val {tag}", source)

    def test_required_cross_process_tags_are_present(self) -> None:
        required = {
            "app_root",
            "primary_navigation",
            "content_list",
            "content_first_item",
            "content_detail",
            "audio_play_pause",
            "miracles_list",
            "miracles_first_item",
            "miracles_detail",
            "quran_list",
            "quran_first_item",
            "quran_detail",
            "prayer_times_ready",
            "qibla_ready",
            "counter_root",
            "counter_value",
            "counter_increment",
        }
        source = "\n".join(
            path.read_text(encoding="utf-8")
            for path in ROOT.rglob("*.kt")
            if "/build/" not in path.as_posix()
        )
        missing = sorted(tag for tag in required if f'"{tag}"' not in source)
        self.assertEqual([], missing)
        content_app = (
            ROOT / "app/src/main/java/com/parsfilo/contentapp/ui/ContentApp.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("testTagsAsResourceId = true", content_app)

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
