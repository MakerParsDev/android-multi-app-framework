# 17-Flavor Baseline Profile and Macrobenchmark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add isolated Baseline and Startup Profiles plus reusable Macrobenchmarks for all 17 Android flavors, with managed-device generation, physical-device evidence, profile packaging verification, and controlled automated profile pull requests.

**Architecture:** One `:performance:benchmark` `com.android.test` module mirrors the application flavor dimension and targets `:app`. Shared UIAutomator journeys are reused by `BaselineProfileRule` and Macrobenchmark tests. GitHub-hosted Gradle Managed Devices generate profiles, while only a labeled physical Android runner may produce authoritative timing comparisons.

**Tech Stack:** AGP 9.2.1, Gradle 9.5.1, JDK 21, Kotlin 2.3.21, AndroidX Baseline Profile Gradle Plugin 1.5.0-alpha07, AndroidX Benchmark 1.4.1, ProfileInstaller 1.4.1, UIAutomator 2.4.0, Python 3, GitHub Actions.

## Global Constraints

- Cover exactly these flavors: `amenerrasulu`, `ayetelkursi`, `bereketduasi`, `esmaulhusna`, `fetihsuresi`, `insirahsuresi`, `ismiazamduasi`, `kenzularsduasi`, `kible`, `kuran_kerim`, `mucizedualar`, `namazsurelerivedualarsesli`, `namazvakitleri`, `nazarayeti`, `vakiasuresi`, `yasinsuresi`, `zikirmatik`.
- Use one `:performance:benchmark` module; do not create 17 modules.
- Pin `androidx.baselineprofile` to `1.5.0-alpha07`. Stable `1.4.1` fails against the repository's AGP 9.2 new DSL with `Module :app is not a supported android module`.
- Add an expiring catalog prerelease allowlist entry for plugin alias `androidx-baselineprofile`, owned by `ci/android-performance`, expiring `2027-01-31`.
- Keep Macrobenchmark and ProfileInstaller on stable `1.4.1`; keep UIAutomator on stable `2.4.0`.
- Profile generation uses Pixel 6, API 33, `aosp`, x86_64, and SwiftShader on GitHub-hosted runners.
- Emulator timing is informational. Only `self-hosted, android-performance` physical runs may become release-blocking.
- Do not use production signing, Doppler, Play, Firebase release, billing, account, ad, push, live location, or live sensor credentials in performance jobs.
- Do not use `pull_request_target`.
- Do not add a repository-local `max-parallel` limit to the 17-flavor profile matrix.
- All external actions must be full-SHA pinned and present in `config/pinned-github-actions.json`.
- Existing CI, Security, CodeQL, Device Smoke, attested release, and Play Internal behavior must remain green.
- Every task starts with a failing test, ends with focused verification, and is committed independently.

---

## File Map

### New production/test files

- `performance/benchmark/build.gradle.kts` — test-module variants, managed device, Baseline Profile producer configuration.
- `performance/benchmark/src/main/AndroidManifest.xml` — benchmark test manifest.
- `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/PerformanceConfig.kt` — target package, family, and iteration input resolution.
- `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/PerformanceTags.kt` — stable cross-process tag names.
- `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/UiAutomatorActions.kt` — bounded wait/click/scroll primitives.
- `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/CriticalUserJourneys.kt` — shared and family-specific CUJs.
- `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/BaselineProfileGenerator.kt` — Baseline and Startup Profile collection.
- `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/StartupBenchmarks.kt` — cold/warm profile/no-profile measurements.
- `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/FrameBenchmarks.kt` — deterministic frame-timing journeys.

### New CI/policy files

- `scripts/ci/performance_profile_policy.py` — flavor parity, generated-profile, task-name, and AAB metadata validation.
- `scripts/ci/performance_profile_policy_test.py` — policy unit tests.
- `scripts/ci/update_baseline_profile_pr.sh` — aggregate generated profiles and update one automation PR.
- `scripts/ci/run_physical_performance.sh` — serial physical-device benchmark harness.
- `.github/workflows/baseline-profiles.yml` — weekly/manual 17-flavor GMD profile generation.
- `.github/baseline-profile-pr-body.md` — fixed body for the generated profile pull request.
- `.github/workflows/physical-performance.yml` — manual physical benchmark workflow.
- `docs/PERFORMANCE_TESTING.md` — local commands, runner contract, artifacts, and threshold rollout.

### Existing files to modify

- `gradle/libs.versions.toml` — plugins and dependencies.
- `config/dependency-policy.json` — expiring prerelease exception.
- `settings.gradle.kts` — include benchmark module.
- `build.gradle.kts` — declare Android test and Baseline Profile plugins.
- `app/build.gradle.kts` — consumer plugin, ProfileInstaller, producer dependency, non-production release-task exclusion.
- `app/src/main/java/com/parsfilo/contentapp/ui/ContentApp.kt` — expose cross-process Compose tags.
- `app/src/main/java/com/parsfilo/contentapp/ui/AppNavigation.kt` — tag family roots.
- `feature/content/...` — tag lists, first deterministic items, details, and audio control.
- `feature/quran/...` — tag surah list, first item, and detail.
- `feature/prayertimes/...` — tag deterministic ready/fallback surface.
- `feature/qibla/...` — tag deterministic ready/fallback surface.
- `feature/counter/...` — tag root, count, and increment action.
- `scripts/ci/professional_ci_workflows_test.py` — workflow contracts.
- `scripts/ci/workflow_policy_test.py` — workflow permissions/triggers.
- `scripts/ci/pinned_github_actions_test.py` — new official action pins.
- `config/pinned-github-actions.json` — `download-artifact` and `create-github-app-token` pins.
- `.github/workflows/ci-pr.yml` — performance contract job.
- `.github/workflows/README.md` — workflow documentation.

---

### Task 1: Pin the performance toolchain and create the benchmark module

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `config/dependency-policy.json`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `performance/benchmark/build.gradle.kts`
- Create: `performance/benchmark/src/main/AndroidManifest.xml`
- Create: `scripts/ci/performance_profile_policy_test.py`

**Interfaces:**
- Consumes: `AppFlavors.all`, `requiredToolchainInt(...)`, and repository version catalog aliases.
- Produces: `:performance:benchmark`, 17 `BenchmarkRelease` and `NonMinifiedRelease` variants, and `:app:generate<Flavor>ReleaseBaselineProfile` tasks.

- [ ] **Step 1: Write the failing module/toolchain test**

Create `scripts/ci/performance_profile_policy_test.py`:

```python
#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = [
    "amenerrasulu", "ayetelkursi", "bereketduasi", "esmaulhusna",
    "fetihsuresi", "insirahsuresi", "ismiazamduasi", "kenzularsduasi",
    "kible", "kuran_kerim", "mucizedualar",
    "namazsurelerivedualarsesli", "namazvakitleri", "nazarayeti",
    "vakiasuresi", "yasinsuresi", "zikirmatik",
]


class PerformanceProfileStructureTest(unittest.TestCase):
    def test_toolchain_and_module_are_pinned(self) -> None:
        catalog = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        self.assertIn('baselineProfile = "1.5.0-alpha07"', catalog)
        self.assertIn('benchmark = "1.4.1"', catalog)
        self.assertIn('profileInstaller = "1.4.1"', catalog)
        self.assertIn('uiautomator = "2.4.0"', catalog)
        self.assertIn('androidx-baselineprofile = { id = "androidx.baselineprofile"', catalog)
        self.assertIn('android-test = { id = "com.android.test"', catalog)
        self.assertTrue((ROOT / "performance/benchmark/build.gradle.kts").is_file())
        self.assertIn('include(":performance:benchmark")', (ROOT / "settings.gradle.kts").read_text())

    def test_prerelease_exception_is_narrow_and_expiring(self) -> None:
        policy = json.loads((ROOT / "config/dependency-policy.json").read_text())
        entries = {entry["alias"]: entry for entry in policy["catalog_prerelease_allowlist"]}
        entry = entries["androidx-baselineprofile"]
        self.assertEqual("ci/android-performance", entry["owner"])
        self.assertEqual("2027-01-31", entry["expires_on"])
        self.assertGreaterEqual(len(entry["reason"]), 12)

    def test_benchmark_build_declares_all_catalog_flavors(self) -> None:
        source = (ROOT / "performance/benchmark/build.gradle.kts").read_text()
        self.assertIn("AppFlavors.all.forEach", source)
        self.assertIn('dimension = "app"', source)
        self.assertIn('targetProjectPath = ":app"', source)
        catalog = (ROOT / "buildSrc/src/main/kotlin/FlavorConfig.kt").read_text()
        actual = re.findall(r'name = "([a-z0-9_]+)"', catalog)
        self.assertEqual(EXPECTED, actual)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the test and verify the expected failure**

Run:

```bash
python3 scripts/ci/performance_profile_policy_test.py
```

Expected: FAIL because the versions, module, and include are absent.

- [ ] **Step 3: Add exact toolchain aliases and the prerelease exception**

Add to `[versions]` in `gradle/libs.versions.toml`:

```toml
baselineProfile = "1.5.0-alpha07"
benchmark = "1.4.1"
profileInstaller = "1.4.1"
uiautomator = "2.4.0"
```

Add to `[libraries]`:

```toml
androidx-benchmark-macro-junit4 = { group = "androidx.benchmark", name = "benchmark-macro-junit4", version.ref = "benchmark" }
androidx-profileinstaller = { group = "androidx.profileinstaller", name = "profileinstaller", version.ref = "profileInstaller" }
androidx-test-uiautomator = { group = "androidx.test.uiautomator", name = "uiautomator", version.ref = "uiautomator" }
```

Add to `[plugins]`:

```toml
android-test = { id = "com.android.test", version.ref = "agp" }
androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "baselineProfile" }
```

Set `catalog_prerelease_allowlist` in `config/dependency-policy.json` to:

```json
[
  {
    "alias": "androidx-baselineprofile",
    "expires_on": "2027-01-31",
    "owner": "ci/android-performance",
    "reason": "AGP 9.2 new DSL support is available in Baseline Profile Gradle Plugin 1.5 while stable 1.4.1 rejects the application module."
  }
]
```

Keep all existing transitive exceptions unchanged.

- [ ] **Step 4: Register the plugins and module**

Add to root `build.gradle.kts` inside `plugins`:

```kotlin
alias(libs.plugins.android.test) apply false
alias(libs.plugins.androidx.baselineprofile) apply false
```

Add to `settings.gradle.kts`:

```kotlin
include(":performance:benchmark")
```

Create `performance/benchmark/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

Create `performance/benchmark/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.parsfilo.contentapp.performance"
    compileSdk = requiredToolchainInt("toolchain.android.compileSdk")
    targetProjectPath = ":app"

    defaultConfig {
        minSdk = requiredToolchainInt("toolchain.android.minSdk")
        targetSdk = requiredToolchainInt("toolchain.android.targetSdk")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "app"
    productFlavors {
        AppFlavors.all.forEach { config ->
            create(config.name) {
                dimension = "app"
                testInstrumentationRunnerArguments["performanceFamily"] = config.contentFamily
                testInstrumentationRunnerArguments["performanceFlavor"] = config.name
            }
        }
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel6Api33") {
                    device = "Pixel 6"
                    apiLevel = 33
                    systemImageSource = "aosp"
                    testedAbi = "x86_64"
                }
            }
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api33"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.runner)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
}
```

- [ ] **Step 5: Add the app consumer without weakening production release validation**

Add `alias(libs.plugins.androidx.baselineprofile)` to the `app` plugin block.

Add to `app` dependencies:

```kotlin
implementation(libs.androidx.profileinstaller)
baselineProfile(project(":performance:benchmark"))
```

Add after `android { ... }`:

```kotlin
baselineProfile {
    saveInSrc = true
    automaticGenerationDuringBuild = false
    mergeIntoMain = false
}
```

Replace `isReleaseBuildLikeTask` with:

```kotlin
fun isGeneratedPerformanceVariant(normalized: String): Boolean =
    normalized.contains("BenchmarkRelease") || normalized.contains("NonMinifiedRelease")

fun isReleaseBuildLikeTask(taskName: String): Boolean =
    normalizedTaskName(taskName).let { normalized ->
        (normalized.startsWith("assemble") ||
            normalized.startsWith("bundle") ||
            normalized.startsWith("publish")) &&
            normalized.contains("Release") &&
            !isGeneratedPerformanceVariant(normalized)
    }
```

This keeps signing/endpoint validation on true production `Release` tasks and prevents generated benchmark variants from demanding production keys.

- [ ] **Step 6: Run policy and Gradle task discovery**

Run:

```bash
python3 scripts/ci/performance_profile_policy_test.py
python3 scripts/ci/dependency_catalog_audit_test.py
python3 scripts/ci/dependency_catalog_audit.py --repo .
./gradlew :performance:benchmark:tasks :app:tasks --all --no-daemon --no-configuration-cache
```

Expected:

- all Python tests PASS;
- Gradle configuration succeeds;
- output includes `pixel6Api33Kuran_kerimBenchmarkReleaseAndroidTest`;
- output includes all 17 `generate<Flavor>ReleaseBaselineProfile` tasks.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml config/dependency-policy.json settings.gradle.kts \
  build.gradle.kts app/build.gradle.kts performance/benchmark \
  scripts/ci/performance_profile_policy_test.py
git commit -m "build: add 17-flavor Baseline Profile module"
```

---

### Task 2: Add the stable cross-process UI performance contract

**Files:**
- Create: `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/PerformanceTags.kt`
- Modify: `app/src/main/java/com/parsfilo/contentapp/ui/ContentApp.kt`
- Modify: `app/src/main/java/com/parsfilo/contentapp/ui/AppNavigation.kt`
- Modify: `feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/ContentScreen.kt`
- Modify: `feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/prayer/PrayerListScreen.kt`
- Modify: `feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/prayer/PrayerDetailScreen.kt`
- Modify: `feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/miracles/MiraclesListScreen.kt`
- Modify: `feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/miracles/MiraclesDetailScreen.kt`
- Modify: `feature/audio/src/main/java/com/parsfilo/contentapp/feature/audio/ui/MiniAudioPlayer.kt`
- Modify: `feature/quran/src/main/java/com/parsfilo/contentapp/feature/quran/ui/surelist/QuranSuraListScreen.kt`
- Modify: `feature/quran/src/main/java/com/parsfilo/contentapp/feature/quran/ui/suradetail/QuranSuraDetailScreen.kt`
- Modify: `feature/prayertimes/src/main/java/com/parsfilo/contentapp/feature/prayertimes/ui/PrayerTimesScreen.kt`
- Modify: `feature/qibla/src/main/java/com/parsfilo/contentapp/feature/qibla/QiblaScreen.kt`
- Modify: `feature/counter/src/main/java/com/parsfilo/contentapp/feature/counter/ui/CounterScreen.kt`
- Modify: `scripts/ci/performance_profile_policy_test.py`

**Interfaces:**
- Consumes: Compose semantics and existing `app_root` tag.
- Produces: resource-addressable tags used by UIAutomator in Task 3.

- [ ] **Step 1: Add a failing tag contract test**

Append to `PerformanceProfileStructureTest`:

```python
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
        content_app = (ROOT / "app/src/main/java/com/parsfilo/contentapp/ui/ContentApp.kt").read_text()
        self.assertIn("testTagsAsResourceId = true", content_app)
```

- [ ] **Step 2: Run the test and verify missing tags**

```bash
python3 scripts/ci/performance_profile_policy_test.py
```

Expected: FAIL with a list of missing tags.

- [ ] **Step 3: Create benchmark-side tag constants**

Create `PerformanceTags.kt`:

```kotlin
package com.parsfilo.contentapp.performance

internal object PerformanceTags {
    const val APP_ROOT = "app_root"
    const val PRIMARY_NAVIGATION = "primary_navigation"
    const val CONTENT_LIST = "content_list"
    const val CONTENT_FIRST_ITEM = "content_first_item"
    const val CONTENT_DETAIL = "content_detail"
    const val AUDIO_PLAY_PAUSE = "audio_play_pause"
    const val MIRACLES_LIST = "miracles_list"
    const val MIRACLES_FIRST_ITEM = "miracles_first_item"
    const val MIRACLES_DETAIL = "miracles_detail"
    const val QURAN_LIST = "quran_list"
    const val QURAN_FIRST_ITEM = "quran_first_item"
    const val QURAN_DETAIL = "quran_detail"
    const val PRAYER_TIMES_READY = "prayer_times_ready"
    const val QIBLA_READY = "qibla_ready"
    const val COUNTER_ROOT = "counter_root"
    const val COUNTER_VALUE = "counter_value"
    const val COUNTER_INCREMENT = "counter_increment"
}
```

- [ ] **Step 4: Make Compose tags visible to UIAutomator**

In `ContentApp.kt`, import:

```kotlin
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
```

Change the root modifier to:

```kotlin
Modifier
    .semantics { testTagsAsResourceId = true }
    .testTag("app_root")
    .fillMaxSize()
    .background(MaterialTheme.colorScheme.background)
```

Apply `.testTag("primary_navigation")` to both the `NavigationSuiteScaffold` and compact bottom-navigation container.

- [ ] **Step 5: Tag deterministic family surfaces**

Use the following exact rules:

```kotlin
// Lists
Modifier.testTag("content_list")
Modifier.testTag("miracles_list")
Modifier.testTag("quran_list")

// First item only
Modifier.testTag(if (index == 0) "content_first_item" else "content_item_$index")
Modifier.testTag(if (index == 0) "miracles_first_item" else "miracles_item_$index")
Modifier.testTag(if (index == 0) "quran_first_item" else "quran_item_$index")

// Detail roots
Modifier.testTag("content_detail")
Modifier.testTag("miracles_detail")
Modifier.testTag("quran_detail")

// Feature roots/actions
Modifier.testTag("audio_play_pause")
Modifier.testTag("prayer_times_ready")
Modifier.testTag("qibla_ready")
Modifier.testTag("counter_root")
Modifier.testTag("counter_value")
Modifier.testTag("counter_increment")
```

Add tags to existing root/list/button modifiers; do not add invisible controls or alter content descriptions.

- [ ] **Step 6: Run tag, ktlint, and smoke tests**

```bash
python3 scripts/ci/performance_profile_policy_test.py
./gradlew :app:compileKuran_kerimDebugKotlin \
  :app:compileNamazvakitleriDebugKotlin \
  :app:compileKibleDebugKotlin \
  :app:compileZikirmatikDebugKotlin \
  ktlintCheck --no-daemon
```

Expected: PASS without changing visible application behavior.

- [ ] **Step 7: Commit**

```bash
git add app/src/main feature/content/src/main feature/audio/src/main \
  feature/quran/src/main feature/prayertimes/src/main feature/qibla/src/main \
  feature/counter/src/main performance/benchmark/src/main \
  scripts/ci/performance_profile_policy_test.py
git commit -m "test: expose stable performance journey tags"
```

---

### Task 3: Implement variant-aware configuration and reusable UIAutomator journeys

**Files:**
- Create: `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/PerformanceConfig.kt`
- Create: `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/UiAutomatorActions.kt`
- Create: `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/CriticalUserJourneys.kt`
- Modify: `scripts/ci/performance_profile_policy_test.py`

**Interfaces:**
- Produces: `PerformanceConfig.current()`, `PerformanceFamily`, `CriticalUserJourneys.run(scope, config)`, and bounded UI helpers.

- [ ] **Step 1: Write the failing configuration source-contract test**

Append to `PerformanceProfileStructureTest` in `scripts/ci/performance_profile_policy_test.py`:

```python
    def test_performance_config_maps_all_families_and_clamps_iterations(self) -> None:
        source = (ROOT / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/PerformanceConfig.kt").read_text()
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
        self.assertIn('Missing performanceFlavor instrumentation argument', source)
        self.assertIn('Missing performanceFamily instrumentation argument', source)
```

`com.android.test` does not expose a JVM unit-test variant in this repository. Keep the pure mapping under a Python source-contract test and use Android variant compilation as the type/API check.

- [ ] **Step 2: Verify the contract test fails**

```bash
python3 scripts/ci/performance_profile_policy_test.py
```

Expected: FAIL because `PerformanceConfig.kt` does not exist.

- [ ] **Step 3: Implement configuration**

Create `PerformanceConfig.kt`:

```kotlin
package com.parsfilo.contentapp.performance

import androidx.test.platform.app.InstrumentationRegistry

internal enum class PerformanceFamily {
    AUDIO_CONTENT,
    QURAN,
    MIRACLES,
    PRAYER_TIMES,
    QIBLA,
    COUNTER;

    companion object {
        fun from(raw: String): PerformanceFamily =
            when (raw) {
                "content", "esma", "prayer_library" -> AUDIO_CONTENT
                "quran" -> QURAN
                "miracles" -> MIRACLES
                "prayer_times" -> PRAYER_TIMES
                "qibla" -> QIBLA
                "zikir_counter" -> COUNTER
                else -> throw IllegalArgumentException("Unsupported performance family: $raw")
            }
    }
}

internal data class PerformanceConfig(
    val packageName: String,
    val flavor: String,
    val family: PerformanceFamily,
    val iterations: Int,
) {
    companion object {
        fun current(): PerformanceConfig {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val args = InstrumentationRegistry.getArguments()
            val flavor = requireNotNull(args.getString("performanceFlavor")) {
                "Missing performanceFlavor instrumentation argument"
            }
            val family = requireNotNull(args.getString("performanceFamily")) {
                "Missing performanceFamily instrumentation argument"
            }
            return PerformanceConfig(
                packageName = instrumentation.targetContext.packageName,
                flavor = flavor,
                family = PerformanceFamily.from(family),
                iterations = parseIterations(args.getString("benchmarkIterations")),
            )
        }
    }
}

internal fun parseIterations(raw: String?): Int =
    raw?.toIntOrNull()?.coerceIn(5, 30) ?: 10
```

- [ ] **Step 4: Implement bounded UIAutomator actions**

Create `UiAutomatorActions.kt`:

```kotlin
package com.parsfilo.contentapp.performance

import android.graphics.Point
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

private const val READY_TIMEOUT_MS = 15_000L

internal fun MacrobenchmarkScope.waitForTag(config: PerformanceConfig, tag: String) {
    check(device.wait(Until.hasObject(By.res(config.packageName, tag)), READY_TIMEOUT_MS)) {
        "Timed out waiting for tag=$tag flavor=${config.flavor} package=${config.packageName}"
    }
}

internal fun MacrobenchmarkScope.clickTag(config: PerformanceConfig, tag: String) {
    waitForTag(config, tag)
    requireNotNull(device.findObject(By.res(config.packageName, tag))) {
        "Missing object after wait: $tag"
    }.click()
}

internal fun MacrobenchmarkScope.scrollTag(config: PerformanceConfig, tag: String) {
    waitForTag(config, tag)
    val objectUnderTest = requireNotNull(device.findObject(By.res(config.packageName, tag)))
    val bounds = objectUnderTest.visibleBounds
    val centerX = bounds.centerX()
    val start = Point(centerX, bounds.bottom - bounds.height() / 5)
    val end = Point(centerX, bounds.top + bounds.height() / 5)
    device.swipe(start.x, start.y, end.x, end.y, 12)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.launchRoot(config: PerformanceConfig) {
    pressHome()
    startActivityAndWait()
    waitForTag(config, PerformanceTags.APP_ROOT)
}
```

- [ ] **Step 5: Implement reusable journeys**

Create `CriticalUserJourneys.kt`:

```kotlin
package com.parsfilo.contentapp.performance

import androidx.benchmark.macro.MacrobenchmarkScope

internal object CriticalUserJourneys {
    fun startup(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        scope.launchRoot(config)
        scope.waitForTag(config, PerformanceTags.PRIMARY_NAVIGATION)
    }

    fun run(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        startup(scope, config)
        when (config.family) {
            PerformanceFamily.AUDIO_CONTENT -> audioContent(scope, config)
            PerformanceFamily.QURAN -> quran(scope, config)
            PerformanceFamily.MIRACLES -> miracles(scope, config)
            PerformanceFamily.PRAYER_TIMES -> scope.waitForTag(config, PerformanceTags.PRAYER_TIMES_READY)
            PerformanceFamily.QIBLA -> scope.waitForTag(config, PerformanceTags.QIBLA_READY)
            PerformanceFamily.COUNTER -> counter(scope, config)
        }
    }

    private fun audioContent(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        scope.waitForTag(config, PerformanceTags.CONTENT_LIST)
        scope.scrollTag(config, PerformanceTags.CONTENT_LIST)
        scope.clickTag(config, PerformanceTags.CONTENT_FIRST_ITEM)
        scope.waitForTag(config, PerformanceTags.CONTENT_DETAIL)
        scope.clickTag(config, PerformanceTags.AUDIO_PLAY_PAUSE)
        scope.device.waitForIdle()
        scope.clickTag(config, PerformanceTags.AUDIO_PLAY_PAUSE)
        scope.device.pressBack()
    }

    private fun quran(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        scope.waitForTag(config, PerformanceTags.QURAN_LIST)
        scope.scrollTag(config, PerformanceTags.QURAN_LIST)
        scope.clickTag(config, PerformanceTags.QURAN_FIRST_ITEM)
        scope.waitForTag(config, PerformanceTags.QURAN_DETAIL)
        scope.device.pressBack()
    }

    private fun miracles(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        scope.waitForTag(config, PerformanceTags.MIRACLES_LIST)
        scope.scrollTag(config, PerformanceTags.MIRACLES_LIST)
        scope.clickTag(config, PerformanceTags.MIRACLES_FIRST_ITEM)
        scope.waitForTag(config, PerformanceTags.MIRACLES_DETAIL)
        scope.device.pressBack()
    }

    private fun counter(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        scope.waitForTag(config, PerformanceTags.COUNTER_ROOT)
        repeat(3) { scope.clickTag(config, PerformanceTags.COUNTER_INCREMENT) }
        scope.waitForTag(config, PerformanceTags.COUNTER_VALUE)
    }
}
```

- [ ] **Step 6: Run the source contract and variant compilation tests**

```bash
python3 scripts/ci/performance_profile_policy_test.py
./gradlew \
  :performance:benchmark:compileKuran_kerimBenchmarkReleaseKotlin \
  :performance:benchmark:compileNamazvakitleriBenchmarkReleaseKotlin \
  :performance:benchmark:compileZikirmatikBenchmarkReleaseKotlin \
  --no-daemon --no-configuration-cache
```

Expected: the Python contract and all three representative Android variant compilations PASS.

- [ ] **Step 7: Commit**

```bash
git add performance/benchmark scripts/ci/performance_profile_policy_test.py
git commit -m "test: add reusable performance journeys"
```

---

### Task 4: Generate Baseline and Startup Profiles for every variant

**Files:**
- Create: `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/BaselineProfileGenerator.kt`
- Modify: `scripts/ci/performance_profile_policy_test.py`

**Interfaces:**
- Consumes: `PerformanceConfig.current()` and `CriticalUserJourneys`.
- Produces: variant-scoped `baseline-prof.txt` and `startup-prof.txt`.

- [ ] **Step 1: Add a failing generator contract test**

Append:

```python
    def test_generator_separates_startup_and_other_journeys(self) -> None:
        source = (ROOT / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/BaselineProfileGenerator.kt").read_text()
        self.assertIn("BaselineProfileRule", source)
        self.assertIn("includeInStartupProfile = true", source)
        self.assertIn("CriticalUserJourneys.startup", source)
        self.assertIn("CriticalUserJourneys.run", source)
```

- [ ] **Step 2: Verify failure**

```bash
python3 scripts/ci/performance_profile_policy_test.py
```

Expected: FAIL because the generator file is absent.

- [ ] **Step 3: Implement the generator**

Create `BaselineProfileGenerator.kt`:

```kotlin
package com.parsfilo.contentapp.performance

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupProfile() {
        val config = PerformanceConfig.current()
        baselineProfileRule.collect(
            packageName = config.packageName,
            includeInStartupProfile = true,
            maxIterations = 15,
            stableIterations = 3,
        ) {
            CriticalUserJourneys.startup(this, config)
        }
    }

    @Test
    fun baselineJourneys() {
        val config = PerformanceConfig.current()
        baselineProfileRule.collect(
            packageName = config.packageName,
            includeInStartupProfile = false,
            maxIterations = 15,
            stableIterations = 3,
        ) {
            CriticalUserJourneys.run(this, config)
        }
    }
}
```

- [ ] **Step 4: Verify generator compilation and task graph**

```bash
python3 scripts/ci/performance_profile_policy_test.py
python3 scripts/ci/generate_ci_google_services.py --flavors kuran_kerim
./gradlew :app:generateKuran_kerimReleaseBaselineProfile \
  -PciSmoke=true \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  --dry-run --no-daemon --no-configuration-cache
python3 scripts/ci/generate_ci_google_services.py --clean --flavors kuran_kerim
```

Expected: task graph resolves without production signing validation.

- [ ] **Step 5: Run one real representative GMD generation**

```bash
python3 scripts/ci/generate_ci_google_services.py --flavors kuran_kerim
./gradlew :app:generateKuran_kerimReleaseBaselineProfile \
  -PciSmoke=true \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  --no-daemon --no-configuration-cache --stacktrace --max-workers=2
python3 scripts/ci/generate_ci_google_services.py --clean --flavors kuran_kerim
```

Expected:

- instrumentation succeeds;
- `app/src/kuran_kerimRelease/generated/baselineProfiles/baseline-prof.txt` exists and is non-empty;
- `startup-prof.txt` exists and is non-empty.

- [ ] **Step 6: Remove representative generated output and commit generator code**

The representative run is execution evidence, not a partial source update. Remove its generated files before committing so the feature PR cannot contain only one of 17 profile pairs:

```bash
rm -rf app/src/kuran_kerimRelease/generated/baselineProfiles
python3 scripts/ci/generate_ci_google_services.py --clean --flavors kuran_kerim
git status --short
```

Expected: no generated Firebase file and no partial profile source set remain.

```bash
git add performance/benchmark/src/main scripts/ci/performance_profile_policy_test.py
git commit -m "perf: generate variant-scoped Baseline Profiles"
```

The complete 17-flavor profile set is applied only through the aggregation path in Task 8.

---

### Task 5: Add cold/warm startup and frame Macrobenchmarks

**Files:**
- Create: `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/StartupBenchmarks.kt`
- Create: `performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/FrameBenchmarks.kt`
- Modify: `scripts/ci/performance_profile_policy_test.py`

**Interfaces:**
- Produces: four startup comparisons and one family journey frame benchmark per flavor.

- [ ] **Step 1: Add failing benchmark source assertions**

Append:

```python
    def test_macrobenchmarks_define_required_metrics(self) -> None:
        startup = (ROOT / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/StartupBenchmarks.kt").read_text()
        frames = (ROOT / "performance/benchmark/src/main/java/com/parsfilo/contentapp/performance/FrameBenchmarks.kt").read_text()
        self.assertIn("StartupTimingMetric", startup)
        self.assertIn("StartupMode.COLD", startup)
        self.assertIn("StartupMode.WARM", startup)
        self.assertIn("CompilationMode.None", startup)
        self.assertIn("BaselineProfileMode.Require", startup)
        self.assertIn("FrameTimingMetric", frames)
        self.assertIn("CriticalUserJourneys.run", frames)
```

- [ ] **Step 2: Verify failure**

```bash
python3 scripts/ci/performance_profile_policy_test.py
```

- [ ] **Step 3: Implement startup benchmarks**

Create `StartupBenchmarks.kt`:

```kotlin
package com.parsfilo.contentapp.performance

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

class StartupBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test fun coldBaselineProfile() = run(StartupMode.COLD, profiled())
    @Test fun coldNoProfile() = run(StartupMode.COLD, CompilationMode.None())
    @Test fun warmBaselineProfile() = run(StartupMode.WARM, profiled())
    @Test fun warmNoProfile() = run(StartupMode.WARM, CompilationMode.None())

    private fun profiled(): CompilationMode =
        CompilationMode.Partial(BaselineProfileMode.Require)

    private fun run(startupMode: StartupMode, compilationMode: CompilationMode) {
        val config = PerformanceConfig.current()
        benchmarkRule.measureRepeated(
            packageName = config.packageName,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = config.iterations,
            setupBlock = { pressHome() },
            measureBlock = {
                startActivityAndWait()
                waitForTag(config, PerformanceTags.APP_ROOT)
            },
        )
    }
}
```

- [ ] **Step 4: Implement frame benchmarks**

Create `FrameBenchmarks.kt`:

```kotlin
package com.parsfilo.contentapp.performance

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

class FrameBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun primaryJourneyFrames() {
        val config = PerformanceConfig.current()
        benchmarkRule.measureRepeated(
            packageName = config.packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = config.iterations,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForTag(config, PerformanceTags.APP_ROOT)
            },
            measureBlock = {
                CriticalUserJourneys.run(this, config)
            },
        )
    }
}
```

- [ ] **Step 5: Compile benchmark variants and run policy tests**

```bash
python3 scripts/ci/performance_profile_policy_test.py
./gradlew :performance:benchmark:compileAmenerrasuluBenchmarkReleaseKotlin \
  :performance:benchmark:compileKuran_kerimBenchmarkReleaseKotlin \
  :performance:benchmark:compileMucizedualarBenchmarkReleaseKotlin \
  :performance:benchmark:compileNamazvakitleriBenchmarkReleaseKotlin \
  :performance:benchmark:compileKibleBenchmarkReleaseKotlin \
  :performance:benchmark:compileZikirmatikBenchmarkReleaseKotlin \
  --no-daemon --no-configuration-cache
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add performance/benchmark/src/main scripts/ci/performance_profile_policy_test.py
git commit -m "perf: add startup and frame Macrobenchmarks"
```

---

### Task 6: Validate generated profiles and release AAB packaging

**Files:**
- Create: `scripts/ci/performance_profile_policy.py`
- Modify: `scripts/ci/performance_profile_policy_test.py`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces CLI:
  - `python3 scripts/ci/performance_profile_policy.py validate-source --flavor <name>`
  - `python3 scripts/ci/performance_profile_policy.py validate-all`
  - `python3 scripts/ci/performance_profile_policy.py validate-aab --flavor <name> --aab <path>`
  - `python3 scripts/ci/performance_profile_policy.py task --flavor <name>`

- [ ] **Step 1: Add failing policy unit tests**

Add imports:

```python
import tempfile
import zipfile
from performance_profile_policy import (
    expected_profile_dir,
    gradle_flavor_token,
    validate_profile_pair,
    validate_aab,
)
```

Add tests:

```python
    def test_gradle_flavor_token_preserves_underscore(self) -> None:
        self.assertEqual("Kuran_kerim", gradle_flavor_token("kuran_kerim"))

    def test_profile_pair_requires_startup_subset(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            directory = expected_profile_dir(root, "kuran_kerim")
            directory.mkdir(parents=True)
            (directory / "baseline-prof.txt").write_text("Lcom/parsfilo/A;\nLcom/parsfilo/B;\n")
            (directory / "startup-prof.txt").write_text("Lcom/parsfilo/A;\n")
            self.assertEqual([], validate_profile_pair(root, "kuran_kerim", {}))
            (directory / "startup-prof.txt").write_text("Lcom/parsfilo/C;\n")
            self.assertTrue(validate_profile_pair(root, "kuran_kerim", {}))

    def test_aab_requires_compiled_profile_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            aab = Path(tmp) / "app.aab"
            with zipfile.ZipFile(aab, "w") as archive:
                archive.writestr("BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof", b"profile")
                archive.writestr("BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm", b"metadata")
            self.assertEqual([], validate_aab(aab))
```

- [ ] **Step 2: Verify failure**

```bash
python3 scripts/ci/performance_profile_policy_test.py
```

- [ ] **Step 3: Implement the policy CLI**

Create `scripts/ci/performance_profile_policy.py` with these public functions and command behavior:

```python
#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

from resolve_ci_flavor_matrix import load_flavors

ROOT = Path(__file__).resolve().parents[2]


def gradle_flavor_token(flavor: str) -> str:
    if not flavor or not flavor[0].islower():
        raise ValueError(f"Invalid flavor: {flavor}")
    return flavor[0].upper() + flavor[1:]


def expected_profile_dir(repo: Path, flavor: str) -> Path:
    return repo / "app" / "src" / f"{flavor}Release" / "generated" / "baselineProfiles"


def load_packages(repo: Path) -> dict[str, str]:
    data = json.loads((repo / ".ci/apps.json").read_text(encoding="utf-8"))
    return {entry["flavor"]: entry["package"] for entry in data}


def normalized_rules(path: Path) -> set[str]:
    return {
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }


def validate_profile_pair(repo: Path, flavor: str, packages: dict[str, str]) -> list[str]:
    errors: list[str] = []
    directory = expected_profile_dir(repo, flavor)
    baseline = directory / "baseline-prof.txt"
    startup = directory / "startup-prof.txt"
    for path in (baseline, startup):
        if not path.is_file() or path.stat().st_size == 0:
            errors.append(f"[{flavor}] missing or empty profile: {path}")
    if errors:
        return errors
    baseline_rules = normalized_rules(baseline)
    startup_rules = normalized_rules(startup)
    if not startup_rules.issubset(baseline_rules):
        errors.append(f"[{flavor}] startup profile is not a subset of baseline profile")
    for other, package_name in packages.items():
        if other == flavor:
            continue
        descriptor = package_name.replace(".", "/")
        if any(descriptor in rule for rule in baseline_rules | startup_rules):
            errors.append(f"[{flavor}] profile references other flavor package: {other}")
    return errors


def validate_aab(aab: Path) -> list[str]:
    required = {
        "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof",
        "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm",
    }
    with zipfile.ZipFile(aab) as archive:
        names = set(archive.namelist())
    return [f"AAB missing compiled profile metadata: {name}" for name in sorted(required - names)]


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("validate-source", "task"):
        command = sub.add_parser(name)
        command.add_argument("--flavor", required=True)
    sub.add_parser("validate-all")
    aab = sub.add_parser("validate-aab")
    aab.add_argument("--flavor", required=True)
    aab.add_argument("--aab", type=Path, required=True)
    args = parser.parse_args()

    flavors = load_flavors(ROOT)
    packages = load_packages(ROOT)
    selected = getattr(args, "flavor", None)
    if selected is not None and selected not in flavors:
        print(f"ERROR: unknown flavor: {selected}", file=sys.stderr)
        return 2
    if args.command == "task":
        print(f"generate{gradle_flavor_token(selected)}ReleaseBaselineProfile")
        return 0
    if args.command == "validate-aab":
        errors = validate_aab(args.aab)
    else:
        targets = flavors if args.command == "validate-all" else [selected]
        errors = [error for flavor in targets for error in validate_profile_pair(ROOT, flavor, packages)]
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors), file=sys.stderr)
        return 1
    print("Performance profile validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 4: Wire AAB profile validation into production bundle tasks**

In `app/build.gradle.kts`, register a per-flavor verification task after `androidComponents` variant configuration. Use `SingleArtifact.BUNDLE` and depend on the bundle task:

```kotlin
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val flavorName = variant.productFlavors.single { it.first == "app" }.second
        val token = variant.name.replaceFirstChar { it.titlecase() }
        tasks.register<Exec>("validate${token}BaselineProfileInBundle") {
            group = "verification"
            dependsOn("bundle$token")
            val bundle = variant.artifacts.get(SingleArtifact.BUNDLE)
            commandLine(
                pythonExecutable.get(),
                "scripts/ci/performance_profile_policy.py",
                "validate-aab",
                "--flavor",
                flavorName,
                "--aab",
                bundle.get().asFile.absolutePath,
            )
        }
    }
}
```

If the existing `androidComponents` block already exists, add this `onVariants` registration inside it rather than creating a second unrelated block.

- [ ] **Step 5: Run tests and representative AAB verification**

```bash
python3 scripts/ci/performance_profile_policy_test.py
python3 scripts/ci/performance_profile_policy.py validate-source --flavor kuran_kerim
./gradlew :app:validateKuran_kerimReleaseBaselineProfileInBundle \
  --no-daemon --no-configuration-cache
```

For the release bundle command, use the repository's existing Doppler release wrapper if signing validation is active. Expected: the AAB contains both compiled profile metadata entries.

- [ ] **Step 6: Commit**

```bash
git add scripts/ci/performance_profile_policy.py \
  scripts/ci/performance_profile_policy_test.py app/build.gradle.kts
git commit -m "ci: verify profiles in source and release bundles"
```

---

### Task 7: Add lightweight pull-request performance contracts

**Files:**
- Modify: `.github/workflows/ci-pr.yml`
- Modify: `scripts/ci/professional_ci_workflows_test.py`
- Modify: `scripts/ci/workflow_policy_test.py`

**Interfaces:**
- Produces check name `Performance Contract`.

- [ ] **Step 1: Add failing workflow tests**

Add to `professional_ci_workflows_test.py`:

```python
def test_ci_has_lightweight_performance_contract() -> None:
    workflow = load(".github/workflows/ci-pr.yml")
    job = workflow["jobs"]["performance-contract"]
    assert job["name"] == "Performance Contract"
    runs = "\n".join(step.get("run", "") for step in job["steps"])
    assert "performance_profile_policy_test.py" in runs
    assert ":performance:benchmark:tasks" in runs
    assert "connectedAndroidTest" not in runs
    assert "generateBaselineProfile" not in runs
```

Add a workflow-policy assertion that the job has only inherited workflow-level `contents: read` permission and no secrets.

- [ ] **Step 2: Verify failure**

```bash
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy_test.py
```

- [ ] **Step 3: Add `performance-contract` to `ci-pr.yml`**

Use the existing pinned checkout/JDK/Gradle setup pattern:

```yaml
  performance-contract:
    name: Performance Contract
    needs: [workflow-policy, repository-security]
    if: github.actor != 'dependabot[bot]'
    runs-on: ubuntu-24.04
    timeout-minutes: 25
    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          fetch-depth: 1
          persist-credentials: false

      - name: Set up JDK 21
        uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: '21'
          check-latest: false

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0
        with:
          cache-read-only: true

      - name: Validate performance contracts
        run: |
          set -euo pipefail
          python3 scripts/ci/performance_profile_policy_test.py
          python3 scripts/ci/dependency_catalog_audit.py --repo .
          ./gradlew :performance:benchmark:tasks \
            --all --no-daemon --no-configuration-cache
```

Add `performance-contract` to the final `CI Required` dependency/result aggregation.

- [ ] **Step 4: Run all static workflow gates**

```bash
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy_test.py
python3 scripts/ci/pinned_github_actions_test.py
bash scripts/ci/security_gate.sh
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci-pr.yml scripts/ci/professional_ci_workflows_test.py \
  scripts/ci/workflow_policy_test.py
git commit -m "ci: add performance contract checks"
```

---

### Task 8: Add the full-speed Baseline Profiles workflow and aggregation PR

**Files:**
- Create: `.github/workflows/baseline-profiles.yml`
- Create: `scripts/ci/update_baseline_profile_pr.sh`
- Modify: `config/pinned-github-actions.json`
- Modify: `scripts/ci/pinned_github_actions_test.py`
- Modify: `scripts/ci/professional_ci_workflows_test.py`
- Modify: `scripts/ci/workflow_policy_test.py`
- Modify: `.github/workflows/README.md`

**Interfaces:**
- Uses official action pins:
  - `actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c` (`v8.0.1`)
  - `actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1` (`v3.2.0`)
- Consumes repository variable `PERFORMANCE_AUTOMATION_CLIENT_ID` and secret `PERFORMANCE_AUTOMATION_PRIVATE_KEY`.
- Produces artifacts `baseline-profile-<flavor>-<sha>` and branch `automation/baseline-profiles`.

- [ ] **Step 1: Add failing action/workflow tests**

Extend the pinned-action manifest test to require both actions and exact SHAs.

Add workflow assertions:

```python
def test_baseline_profiles_workflow_is_full_speed_and_safe() -> None:
    workflow = load(".github/workflows/baseline-profiles.yml")
    assert "schedule" in workflow["on"]
    assert "workflow_dispatch" in workflow["on"]
    matrix_job = workflow["jobs"]["generate"]
    assert "max-parallel" not in matrix_job["strategy"]
    assert matrix_job["strategy"]["fail-fast"] is False
    assert matrix_job["permissions"] == {"contents": "read"}
    aggregate = workflow["jobs"]["aggregate"]
    assert aggregate["permissions"] == {"contents": "read"}
```

- [ ] **Step 2: Verify failure**

```bash
python3 scripts/ci/pinned_github_actions_test.py
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy_test.py
```

- [ ] **Step 3: Add immutable action pins**

Add to `config/pinned-github-actions.json`:

```json
"actions/create-github-app-token": {
  "sha": "bcd2ba49218906704ab6c1aa796996da409d3eb1",
  "version": "v3.2.0"
},
"actions/download-artifact": {
  "sha": "3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c",
  "version": "v8.0.1"
}
```

Keep JSON keys sorted.

- [ ] **Step 4: Implement the profile-update script**

Create `scripts/ci/update_baseline_profile_pr.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

artifact_root="${1:?artifact root is required}"
branch="automation/baseline-profiles"

git checkout -B "$branch" origin/main
mapfile -t flavors < <(python3 scripts/ci/resolve_ci_flavor_matrix.py | sed -n 's/^flavors=//p' | python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)))')
for flavor in "${flavors[@]}"; do
  source_dir="$artifact_root/$flavor"
  target_dir="app/src/${flavor}Release/generated/baselineProfiles"
  test -s "$source_dir/baseline-prof.txt"
  test -s "$source_dir/startup-prof.txt"
  mkdir -p "$target_dir"
  install -m 0644 "$source_dir/baseline-prof.txt" "$target_dir/baseline-prof.txt"
  install -m 0644 "$source_dir/startup-prof.txt" "$target_dir/startup-prof.txt"
done

python3 scripts/ci/performance_profile_policy.py validate-all
if git diff --quiet -- app/src; then
  echo "No Baseline Profile changes"
  exit 0
fi

git config user.name "${AUTOMATION_APP_SLUG}[bot]"
git config user.email "${AUTOMATION_APP_USER_ID}+${AUTOMATION_APP_SLUG}[bot]@users.noreply.github.com"
git add app/src/*Release/generated/baselineProfiles
git commit -m "perf: refresh 17-flavor Baseline Profiles"
git push --force-with-lease origin "$branch"

pr_number="$(gh pr list --head "$branch" --base main --state open --json number --jq '.[0].number // empty')"
if [[ -n "$pr_number" ]]; then
  gh pr edit "$pr_number" --title "perf: refresh 17-flavor Baseline Profiles" --body-file .github/baseline-profile-pr-body.md
else
  gh pr create --base main --head "$branch" \
    --title "perf: refresh 17-flavor Baseline Profiles" \
    --body-file .github/baseline-profile-pr-body.md
fi
```

Also create `.github/baseline-profile-pr-body.md` containing the generated-profile verification commands and explaining that timing values are not emulator release gates.

- [ ] **Step 5: Create `.github/workflows/baseline-profiles.yml`**

The workflow must contain:

```yaml
name: Baseline Profiles

on:
  schedule:
    - cron: '17 3 * * 1'
  workflow_dispatch:
    inputs:
      flavor:
        description: Flavor name or all
        required: true
        default: all
        type: string
      open_pull_request:
        description: Update the automation profile PR after a full run
        required: true
        default: true
        type: boolean

permissions:
  contents: read

concurrency:
  group: baseline-profiles-${{ github.ref }}
  cancel-in-progress: false
```

Add `resolve` job using `scripts/ci/resolve_ci_flavor_matrix.py`. Validate a single requested flavor against the resolved list and emit a JSON matrix.

Add `generate` job with:

```yaml
    permissions:
      contents: read
    strategy:
      fail-fast: false
      matrix:
        flavor: ${{ fromJson(needs.resolve.outputs.flavors) }}
```

Use existing checkout, JDK 21, setup-gradle, Android SDK, and hardened KVM steps. Run:

```bash
flavor='${{ matrix.flavor }}'
task="$(python3 scripts/ci/performance_profile_policy.py task --flavor "$flavor")"
python3 scripts/ci/generate_ci_google_services.py --flavors "$flavor"
./gradlew ":app:$task" \
  -PciSmoke=true \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  --no-daemon --no-configuration-cache --stacktrace --max-workers=2
python3 scripts/ci/performance_profile_policy.py validate-source --flavor "$flavor"
mkdir -p "performance-artifacts/$flavor"
cp "app/src/${flavor}Release/generated/baselineProfiles/baseline-prof.txt" "performance-artifacts/$flavor/"
cp "app/src/${flavor}Release/generated/baselineProfiles/startup-prof.txt" "performance-artifacts/$flavor/"
```

Always remove CI-only Firebase files and upload:

- `performance-artifacts/<flavor>/`
- `performance/benchmark/build/outputs/`
- managed-device Android test reports;
- logcat when present.

Use 14-day retention.

Add `aggregate` job only for a complete 17-flavor run where `open_pull_request` is true. It must:

1. create a GitHub App token with explicit `permission-contents: write` and `permission-pull-requests: write`;
2. checkout `main` using that token;
3. download all `baseline-profile-*` artifacts with `merge-multiple: true`;
4. resolve the app bot user ID;
5. run `update_baseline_profile_pr.sh` with `GH_TOKEN` set to the app token.

- [ ] **Step 6: Run static workflow and shell checks**

```bash
chmod +x scripts/ci/update_baseline_profile_pr.sh
bash -n scripts/ci/update_baseline_profile_pr.sh
python3 scripts/ci/pinned_github_actions_test.py
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy_test.py
bash scripts/ci/security_gate.sh
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/baseline-profiles.yml .github/workflows/README.md \
  .github/baseline-profile-pr-body.md config/pinned-github-actions.json \
  scripts/ci/update_baseline_profile_pr.sh scripts/ci/pinned_github_actions_test.py \
  scripts/ci/professional_ci_workflows_test.py scripts/ci/workflow_policy_test.py
git commit -m "ci: generate all Baseline Profiles at full speed"
```

---

### Task 9: Add the physical-device performance harness

**Files:**
- Create: `scripts/ci/run_physical_performance.sh`
- Create: `.github/workflows/physical-performance.yml`
- Create: `docs/PERFORMANCE_TESTING.md`
- Modify: `scripts/ci/professional_ci_workflows_test.py`
- Modify: `scripts/ci/workflow_policy_test.py`
- Modify: `.github/workflows/README.md`

**Interfaces:**
- Consumes runner labels `self-hosted`, `android-performance`.
- Produces benchmark JSON, Perfetto traces, logcat, device metadata, and summary artifacts.

- [ ] **Step 1: Add failing physical workflow tests**

Add:

```python
def test_physical_performance_is_manual_and_serial() -> None:
    workflow = load(".github/workflows/physical-performance.yml")
    assert set(workflow["on"]) == {"workflow_dispatch"}
    job = workflow["jobs"]["benchmark"]
    assert job["runs-on"] == ["self-hosted", "android-performance"]
    assert "strategy" not in job
    assert job["permissions"] == {"contents": "read"}
    runs = "\n".join(step.get("run", "") for step in job["steps"])
    assert "run_physical_performance.sh" in runs
    assert "DOPPLER_TOKEN" not in str(job)
```

- [ ] **Step 2: Verify failure**

```bash
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy_test.py
```

- [ ] **Step 3: Implement the serial physical harness**

Create `scripts/ci/run_physical_performance.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

selection="${1:-all}"
suite="${2:-all}"
iterations="${3:-10}"
out="${4:-build/physical-performance}"
mkdir -p "$out"

mapfile -t all_flavors < <(python3 scripts/ci/resolve_ci_flavor_matrix.py | sed -n 's/^flavors=//p' | python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)))')
if [[ "$selection" == "all" ]]; then
  flavors=("${all_flavors[@]}")
elif printf '%s\n' "${all_flavors[@]}" | grep -Fxq "$selection"; then
  flavors=("$selection")
else
  echo "ERROR: unknown flavor: $selection" >&2
  exit 2
fi

case "$suite" in startup) class_filter="StartupBenchmarks" ;;
  frames) class_filter="FrameBenchmarks" ;;
  all) class_filter="" ;;
  *) echo "ERROR: invalid suite: $suite" >&2; exit 2 ;;
esac

serial="$(adb get-serialno)"
[[ -n "$serial" && "$serial" != "unknown" ]] || { echo "ERROR: no authorized Android device" >&2; exit 1; }
{
  echo "serial=$serial"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "fingerprint=$(adb shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  adb shell dumpsys battery
  adb shell dumpsys thermalservice
  adb shell df /data
} > "$out/device-metadata.txt"

for flavor in "${flavors[@]}"; do
  token="${flavor^}"
  python3 scripts/ci/generate_ci_google_services.py --flavors "$flavor"
  args=(
    "-PciSmoke=true"
    "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark"
    "-Pandroid.testInstrumentationRunnerArguments.benchmarkIterations=$iterations"
  )
  if [[ -n "$class_filter" ]]; then
    args+=("-Pandroid.testInstrumentationRunnerArguments.class=com.parsfilo.contentapp.performance.$class_filter")
  fi
  ./gradlew ":performance:benchmark:connected${token}BenchmarkReleaseAndroidTest" \
    "${args[@]}" --no-daemon --no-configuration-cache --stacktrace --max-workers=1
  mkdir -p "$out/$flavor"
  cp -R performance/benchmark/build/outputs/connected_android_test_additional_output/. "$out/$flavor/" 2>/dev/null || true
  cp -R performance/benchmark/build/outputs/androidTest-results/. "$out/$flavor/androidTest-results/" 2>/dev/null || true
  adb logcat -d -v threadtime > "$out/$flavor/logcat.txt"
  adb logcat -c
  python3 scripts/ci/generate_ci_google_services.py --clean --flavors "$flavor"
done
```

Immediately after the `flavors` array is resolved, install this exact cleanup trap so every selected flavor is cleaned even when Gradle fails:

```bash
cleanup() {
  if [[ "${#flavors[@]}" -gt 0 ]]; then
    joined="$(IFS=,; echo "${flavors[*]}")"
    python3 scripts/ci/generate_ci_google_services.py --clean --flavors "$joined" || true
  fi
}
trap cleanup EXIT
```

- [ ] **Step 4: Create the manual workflow**

Create `.github/workflows/physical-performance.yml` with:

```yaml
name: Physical Performance

on:
  workflow_dispatch:
    inputs:
      flavor:
        description: Flavor name or all
        required: true
        default: all
        type: string
      suite:
        description: Benchmark suite
        required: true
        default: all
        type: choice
        options: [all, startup, frames]
      iterations:
        description: Iterations per benchmark, clamped to 5..30
        required: true
        default: '10'
        type: string

permissions:
  contents: read

concurrency:
  group: physical-performance
  cancel-in-progress: false

jobs:
  benchmark:
    name: Physical Performance
    runs-on: [self-hosted, android-performance]
    timeout-minutes: 360
    permissions:
      contents: read
```

Steps must:

- checkout with pinned action and no persisted credentials;
- set up JDK and Gradle read-only cache;
- verify exactly one authorized device;
- run `scripts/ci/run_physical_performance.sh`;
- upload `build/physical-performance/` and benchmark output directories for 30 days using pinned `actions/upload-artifact`;
- upload artifacts under `if: always()`.

- [ ] **Step 5: Document the runner and measurement contract**

Create `docs/PERFORMANCE_TESTING.md` documenting:

- exact local profile-generation commands;
- exact connected benchmark commands;
- runner labels;
- one-device requirement;
- fixed brightness, thermal stabilization, animations, battery, and background-process policy;
- artifact locations;
- why emulator timing is not a release gate;
- three-run observation phase;
- threshold activation as a reviewed config change;
- no personal user data on the benchmark device.

- [ ] **Step 6: Run validation**

```bash
chmod +x scripts/ci/run_physical_performance.sh
bash -n scripts/ci/run_physical_performance.sh
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy_test.py
bash scripts/ci/security_gate.sh
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add scripts/ci/run_physical_performance.sh \
  .github/workflows/physical-performance.yml .github/workflows/README.md \
  docs/PERFORMANCE_TESTING.md scripts/ci/professional_ci_workflows_test.py \
  scripts/ci/workflow_policy_test.py
git commit -m "perf: add physical Android benchmark harness"
```

---

### Task 10: Full verification, PR rollout, and first 17-flavor generation

**Files:**
- Verify: every file created or modified in Tasks 1–9.
- No source modification is planned in this task. A verification failure must be diagnosed and fixed in a new focused TDD commit before rollout continues.

**Interfaces:**
- Produces a reviewable PR and live workflow evidence.

- [ ] **Step 1: Run the complete local static suite**

```bash
python3 scripts/ci/performance_profile_policy_test.py
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy_test.py
python3 scripts/ci/pinned_github_actions_test.py
python3 scripts/ci/dependency_catalog_audit_test.py
python3 scripts/ci/dependency_catalog_audit.py --repo .
python3 scripts/ci/generate_ci_google_services_test.py
bash scripts/ci/setup_managed_device_sdk_test.sh
bash scripts/ci/security_gate.sh
```

Expected: all PASS.

- [ ] **Step 2: Run Gradle verification**

```bash
./gradlew :performance:benchmark:tasks \
  :app:tasks \
  qualityCheck \
  --all --no-daemon --no-configuration-cache
```

Expected: all 17 performance variants/tasks exist and quality gates pass.

- [ ] **Step 3: Verify representative real device generation**

Run the Task 4 real `kuran_kerim` GMD command again from a clean build and validate source output.

Expected: profile generation succeeds, CI-only Firebase cleanup succeeds, and no untracked `google-services.json` remains.

- [ ] **Step 4: Verify a profiled release artifact**

Run the protected attested-release build path for `kuran_kerim`, then resolve the single AAB deterministically:

```bash
mapfile -t release_aabs < <(find app/build/outputs/bundle/kuran_kerimRelease -maxdepth 1 -type f -name '*.aab' -print)
[[ "${#release_aabs[@]}" -eq 1 ]] || { printf 'Expected one AAB, found %s\n' "${#release_aabs[@]}" >&2; exit 1; }
python3 scripts/ci/performance_profile_policy.py validate-aab \
  --flavor kuran_kerim \
  --aab "${release_aabs[0]}"
```

Expected: PASS with `baseline.prof` and `baseline.profm` present.

- [ ] **Step 5: Review repository diff and sensitive-file state**

```bash
git diff --check
git status --short
git ls-files | grep -E 'google-services\.json|keystore|service-account' && exit 1 || true
python3 scripts/ci/validate_tracked_sensitive_files.py
```

Expected: only intended files; no sensitive artifacts.

- [ ] **Step 6: Push branch and open the PR**

```bash
git push -u origin feature/17-flavor-performance-profiles
```

Create PR title:

```text
perf: add 17-flavor Baseline Profiles and Macrobenchmarks
```

PR body must list:

- one benchmark module and 17 variants;
- stable UI journey contract;
- managed-device profile generation;
- physical benchmark authority;
- prerelease plugin exception rationale and expiry;
- representative real profile-generation evidence;
- local test commands.

- [ ] **Step 7: Wait for every required check and review failures systematically**

Required successful checks include:

- Workflow Policy;
- Repository Security;
- Performance Contract;
- Android Quality;
- all 17 app builds;
- CodeQL;
- Dependency Review;
- GitGuardian/other configured external checks.

Do not merge with a cancelled, skipped-required, pending, or stale required check.

- [ ] **Step 8: Merge, synchronize main, and run the full profile workflow**

After a green merge:

1. synchronize canonical `main`;
2. dispatch `Baseline Profiles` with `flavor=all` and `open_pull_request=true`;
3. verify 17 successful matrix jobs;
4. verify exactly one profile pair per flavor;
5. verify the automation PR or an explicit no-change result;
6. retain the run URL and artifact inventory in the final report.

- [ ] **Step 9: Validate the automation PR**

The generated profile PR must pass normal CI and contain only:

```text
app/src/*Release/generated/baselineProfiles/baseline-prof.txt
app/src/*Release/generated/baselineProfiles/startup-prof.txt
```

Reject any PR containing Firebase, signing, service-account, build-output, or benchmark trace files.

- [ ] **Step 10: Clean merged worktree and branches**

After final verification:

```bash
git -C /srv/repolar/MakerParsDev/android-multi-app-framework worktree remove \
  /srv/repolar/MakerParsDev/android-multi-app-framework/.worktrees/17-flavor-performance-profiles
git -C /srv/repolar/MakerParsDev/android-multi-app-framework branch -d \
  feature/17-flavor-performance-profiles
git -C /srv/repolar/MakerParsDev/android-multi-app-framework fetch --prune
```

Preserve any dirty/unmerged worktree discovered at cleanup time and report it instead of deleting it.

---

## Final Verification Checklist

- [ ] One `:performance:benchmark` module exists.
- [ ] All 17 app flavors have matching performance variants.
- [ ] `androidx.baselineprofile` 1.5.0-alpha07 is narrowly allowlisted and expiring.
- [ ] Stable Macrobenchmark/ProfileInstaller/UIAutomator versions are used.
- [ ] Startup-only paths use `includeInStartupProfile = true`.
- [ ] Non-startup CUJs remain outside Startup Profile collection.
- [ ] Profiles are variant-scoped and non-empty.
- [ ] Startup rules are a subset of Baseline rules.
- [ ] Release AABs contain compiled profile metadata.
- [ ] PR checks perform no authoritative emulator timing comparison.
- [ ] The weekly/manual profile matrix has no `max-parallel` cap.
- [ ] Profile PR automation uses a repository-scoped GitHub App token only.
- [ ] Physical performance runs are manual, serial, and restricted to `android-performance` runners.
- [ ] No production secret is available to performance jobs.
- [ ] Existing CI, Device Smoke, attested release, and Play Internal flows remain green.
