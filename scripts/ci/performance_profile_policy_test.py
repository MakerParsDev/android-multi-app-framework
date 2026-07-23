#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path

from performance_profile_policy import (
    expected_profile_dir,
    gradle_flavor_token,
    validate_aab,
    validate_profile_pair,
)

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


class PerformanceProfilePolicyTest(unittest.TestCase):
    def test_gradle_flavor_token_preserves_underscore(self) -> None:
        self.assertEqual("Kuran_kerim", gradle_flavor_token("kuran_kerim"))

    def test_profile_pair_requires_startup_subset(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            directory = expected_profile_dir(root, "kuran_kerim")
            directory.mkdir(parents=True)
            (directory / "baseline-prof.txt").write_text(
                "Lcom/parsfilo/A;\nLcom/parsfilo/B;\n", encoding="utf-8"
            )
            (directory / "startup-prof.txt").write_text(
                "Lcom/parsfilo/A;\n", encoding="utf-8"
            )
            self.assertEqual([], validate_profile_pair(root, "kuran_kerim", {}))
            (directory / "startup-prof.txt").write_text(
                "Lcom/parsfilo/C;\n", encoding="utf-8"
            )
            self.assertTrue(validate_profile_pair(root, "kuran_kerim", {}))

    def test_aab_requires_compiled_profile_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            aab = Path(tmp) / "app.aab"
            with zipfile.ZipFile(aab, "w") as archive:
                archive.writestr(
                    "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof",
                    b"profile",
                )
                archive.writestr(
                    "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm",
                    b"metadata",
                )
            self.assertEqual([], validate_aab(aab))

    def test_profile_pair_rejects_comment_only_duplicate_and_malformed_rules(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            directory = expected_profile_dir(root, "kuran_kerim")
            directory.mkdir(parents=True)
            baseline = directory / "baseline-prof.txt"
            startup = directory / "startup-prof.txt"

            baseline.write_text("# generated only\n", encoding="utf-8")
            startup.write_text("# generated only\n", encoding="utf-8")
            errors = validate_profile_pair(root, "kuran_kerim", {})
            self.assertTrue(any("no profile rules" in error for error in errors), errors)

            baseline.write_text("Lcom/parsfilo/A;\nLcom/parsfilo/A;\n", encoding="utf-8")
            startup.write_text("Lcom/parsfilo/A;\n", encoding="utf-8")
            errors = validate_profile_pair(root, "kuran_kerim", {})
            self.assertTrue(any("duplicate profile rule" in error for error in errors), errors)

            baseline.write_text("not-a-profile-rule\nLcom/parsfilo/A;\n", encoding="utf-8")
            startup.write_text("Lcom/parsfilo/A;\n", encoding="utf-8")
            errors = validate_profile_pair(root, "kuran_kerim", {})
            self.assertTrue(any("malformed profile rule" in error for error in errors), errors)

    def test_aab_rejects_empty_compiled_profile_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            aab = Path(tmp) / "app.aab"
            with zipfile.ZipFile(aab, "w") as archive:
                archive.writestr(
                    "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof",
                    b"",
                )
                archive.writestr(
                    "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm",
                    b"metadata",
                )
            errors = validate_aab(aab)
            self.assertEqual(1, len(errors), errors)
            self.assertIn("empty compiled profile metadata", errors[0])


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
        self.assertIn("CriticalUserJourneys.runFromRoot", frames)
        self.assertNotIn("CriticalUserJourneys.run(this, config)", frames)

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

    def test_performance_config_uses_explicit_target_package_argument(self) -> None:
        config = (
            ROOT
            / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/PerformanceConfig.kt"
        ).read_text(encoding="utf-8")
        gradle = (ROOT / "performance/benchmark/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("Missing performancePackage instrumentation argument", config)
        self.assertIn('args.getString("performancePackage")', config)
        self.assertNotIn("instrumentation.targetContext.packageName", config)
        self.assertEqual(
            1,
            gradle.count(
                'testInstrumentationRunnerArguments["performancePackage"] = config.packageName'
            ),
        )

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

    def test_generated_performance_variants_are_firebase_safe(self) -> None:
        gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('variant.buildType == "benchmarkRelease"', gradle)
        self.assertIn('variant.buildType == "nonMinifiedRelease"', gradle)
        self.assertIn('BuildConfigField("boolean", true', gradle)
        self.assertIn('requireNotNull(variant.buildConfigFields).put', gradle)
        self.assertIn('requireNotNull(variant.buildConfigFields).put', gradle)
        self.assertIn('variant.manifestPlaceholders.put("ciSmokeFirebaseDisabled", "true")', gradle)
        self.assertIn('variant.manifestPlaceholders.put("ciSmokeFirebaseEnabled", "false")', gradle)
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        for key in (
            "firebase_performance_collection_deactivated",
            "firebase_analytics_collection_deactivated",
            "firebase_crashlytics_collection_enabled",
            "firebase_messaging_auto_init_enabled",
            "firebase_data_collection_default_enabled",
        ):
            self.assertIn(f'android:name="{key}"', manifest)
        self.assertFalse((ROOT / "app/src/debug/AndroidManifest.xml").exists())

    def test_release_variants_register_aab_profile_validation(self) -> None:
        source = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("SingleArtifact.BUNDLE", source)
        self.assertIn("BaselineProfileInBundle", source)
        self.assertIn("performance_profile_policy.py", source)
        self.assertIn('"validate-aab"', source)

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

    def test_performance_tags_mark_loaded_surfaces(self) -> None:
        content = (
            ROOT / "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/ContentScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertGreaterEqual(content.count('.testTag("content_list")'), 2)

        prayer = (
            ROOT / "feature/prayertimes/src/main/java/com/parsfilo/contentapp/feature/prayertimes/ui/PrayerTimesScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("if (!uiState.isRefreshing)", prayer)
        self.assertIn('.testTag("prayer_times_ready")', prayer)

        qibla = (
            ROOT / "feature/qibla/src/main/java/com/parsfilo/contentapp/feature/qibla/QiblaScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("if (!uiState.isLocationRefreshing)", qibla)
        self.assertIn('.testTag("qibla_ready")', qibla)

    def test_app_gradle_reuses_python_provider(self) -> None:
        source = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("pythonExecutable.set(appPythonExecutable)", source)

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

    def test_ui_automator_uses_compose_test_tag_resource_name_without_package(self) -> None:
        source = (
            ROOT
            / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/UiAutomatorActions.kt"
        ).read_text(encoding="utf-8")
        self.assertNotIn("By.res(config.packageName, tag)", source)
        self.assertEqual(3, source.count("By.res(tag)"))

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
