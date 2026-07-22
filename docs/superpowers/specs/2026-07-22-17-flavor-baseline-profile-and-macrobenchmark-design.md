# 17-Flavor Baseline Profile and Macrobenchmark Design

**Date:** 2026-07-22  
**Status:** Approved for implementation planning  
**Repository:** `MakerParsDev/android-multi-app-framework`  
**Branch:** `feature/17-flavor-performance-profiles`

## 1. Purpose

Add a production-grade Android performance system that covers every application flavor in the repository.

The system must:

- generate a distinct Baseline Profile for all 17 flavors;
- keep flavor-specific profile output isolated;
- exercise shared and flavor-specific critical user journeys;
- measure startup and rendering performance with Macrobenchmark;
- generate profiles on deterministic Gradle Managed Devices;
- reserve authoritative performance comparisons for physical Android hardware;
- integrate with the existing full-speed GitHub Actions model without slowing normal pull-request feedback unnecessarily;
- prevent a release artifact from silently shipping without its expected profile;
- retain enough evidence to diagnose regressions without treating emulator timing as production truth.

This work extends the existing CI, Device Smoke, attested AAB, and Play Internal release pipeline. It does not replace those controls.

## 2. Current State

The repository currently has:

- one Android application module, `:app`;
- 17 product flavors generated from `buildSrc/src/main/kotlin/FlavorConfig.kt`;
- AGP 9.2.1 and Kotlin 2.3.21;
- Gradle Managed Device support for a Pixel 2 API 30 ATD image;
- two instrumentation smoke tests covering app launch and activity recreation;
- a daily/manual Device Smoke workflow;
- no Baseline Profile plugin;
- no benchmark module;
- no Macrobenchmark tests;
- no startup profile;
- no physical-device performance gate.

The existing `-PciSmoke=true` mode disables Firebase-dependent startup behavior for deterministic CI execution. Performance tests will reuse that isolation principle while keeping benchmark builds non-debuggable and profileable.

## 3. Scope

### 3.1 Included

- A single `:performance:benchmark` Android test module.
- A flavor dimension matching all 17 application flavors.
- Baseline Profile generation for every flavor.
- Startup Profile generation for startup-only code paths.
- Shared critical-user-journey abstractions.
- Flavor-family-specific journeys.
- Cold and warm startup Macrobenchmarks.
- Frame timing measurements for deterministic navigation and scrolling journeys.
- Managed-device profile-generation workflow.
- Physical-device benchmark workflow contract.
- Artifact reports and machine-readable summaries.
- Profile packaging verification in release builds.
- Performance regression policy and staged threshold rollout.
- Tests that prevent flavor/profile drift.

### 3.2 Excluded from this change

- Production-track promotion.
- Play Vitals ingestion.
- Firebase Performance dashboard ingestion.
- Automated rollback based on benchmark output.
- Benchmarking network latency or backend availability.
- Rewriting application navigation architecture.
- Building separate benchmark modules for each flavor.
- Treating emulator timing numbers as release-blocking performance evidence.

These may be added in later changes after the local and physical benchmark system is stable.

## 4. Flavor Coverage

The performance system covers the exact catalog below:

1. `amenerrasulu`
2. `ayetelkursi`
3. `bereketduasi`
4. `esmaulhusna`
5. `fetihsuresi`
6. `insirahsuresi`
7. `ismiazamduasi`
8. `kenzularsduasi`
9. `kible`
10. `kuran_kerim`
11. `mucizedualar`
12. `namazsurelerivedualarsesli`
13. `namazvakitleri`
14. `nazarayeti`
15. `vakiasuresi`
16. `yasinsuresi`
17. `zikirmatik`

The benchmark module must derive this list from the same repository catalog or validate its own mirror against the catalog. A newly added application flavor must fail CI until benchmark coverage is declared.

## 5. Architecture

### 5.1 Module layout

```text
:app
:performance:benchmark
```

`settings.gradle.kts` will include `:performance:benchmark`.

The benchmark module will use:

- `com.android.test`;
- Kotlin Android support compatible with the repository toolchain;
- AndroidX Baseline Profile Gradle plugin;
- AndroidX Benchmark Macro JUnit4;
- AndroidX Test Runner;
- UIAutomator for cross-process interaction;
- the repository version catalog for all versions.

It will target `:app` and define the same `app` flavor dimension and flavor names as the application.

### 5.2 Why one module

A single module is selected instead of 17 modules because it:

- keeps shared journeys in one place;
- avoids duplicating Gradle setup;
- lets variant-aware Gradle tasks express all combinations;
- makes matrix generation and artifact collection predictable;
- keeps package, flavor, and profile validation centralized;
- reduces maintenance while retaining variant isolation.

Every output remains flavor-specific even though the implementation module is shared.

### 5.3 App integration

The application module will apply the Baseline Profile consumer plugin.

For each release flavor:

- the matching benchmark variant generates profile rules;
- the generated Baseline Profile is stored in a variant-scoped source set;
- the generated Startup Profile is limited to startup paths;
- release packaging verifies that the expected profile is present;
- profile generation does not change version code, signing, Firebase configuration, or Play publication behavior.

Expected source layout:

```text
app/src/<flavor>Release/generated/baselineProfiles/baseline-prof.txt
app/src/<flavor>Release/generated/baselineProfiles/startup-prof.txt
```

The implementation will use the plugin-supported generated source location and add regression tests that assert each flavor resolves to its own directory. No profile may be reused implicitly by another flavor.

### 5.4 Build characteristics

Performance target builds must be:

- non-debuggable;
- profileable by shell;
- based on release optimization behavior;
- isolated from production analytics and remote side effects;
- deterministic with respect to permissions and initial storage state;
- signed only as required by the benchmark tooling, never with production release keys.

A benchmark-specific build behavior will be enabled through the Baseline Profile/Macrobenchmark build integration rather than by weakening the production `release` build type.

## 6. Critical User Journeys

### 6.1 Shared journey library

The module will expose small, named journey components rather than one large test method:

```text
AppStartupJourney
RootReadyJourney
PrimaryNavigationJourney
ContentListJourney
ContentDetailJourney
AudioPlaybackJourney
PrayerTimesJourney
QiblaJourney
CounterJourney
```

Each component has one responsibility and provides:

- deterministic setup;
- one interaction sequence;
- an explicit ready condition;
- cleanup or return-to-home behavior;
- diagnostic context when an expected node is absent.

### 6.2 Shared journeys for every flavor

Every flavor executes:

1. Force-stop and cold start.
2. Wait for the root Compose surface.
3. Confirm primary navigation is ready.
4. Open the flavor's first deterministic content or primary feature.
5. Perform a bounded scroll or equivalent state transition.
6. Open a detail or primary action screen.
7. Return to the previous screen.

The benchmark will not depend on remote responses. Where a feature normally requires network, location, sensor, account, billing, ads, or push state, the journey must use a deterministic local/fallback state.

### 6.3 Flavor-family journeys

#### Audio/content applications

Applies to:

- `amenerrasulu`
- `ayetelkursi`
- `bereketduasi`
- `esmaulhusna`
- `fetihsuresi`
- `insirahsuresi`
- `ismiazamduasi`
- `kenzularsduasi`
- `namazsurelerivedualarsesli`
- `nazarayeti`
- `vakiasuresi`
- `yasinsuresi`

Additional journey:

- open the primary content;
- expose the audio control;
- start playback using local packaged content;
- verify the player enters a non-idle state;
- stop or pause before cleanup.

#### Qur'an application

Applies to `kuran_kerim`.

Additional journey:

- open the surah/content list;
- perform a bounded list scroll;
- open the first deterministic surah/detail;
- wait for initial content rendering;
- return to the list.

#### Miracle-prayer library

Applies to `mucizedualar`.

Additional journey:

- open the library list;
- open one deterministic item;
- exercise the local favorite or equivalent persisted interaction when available;
- restore state during cleanup.

#### Prayer times

Applies to `namazvakitleri`.

Additional journey:

- start with location permission denied or unset;
- open the prayer-times surface;
- verify the deterministic permission/fallback state;
- avoid live location and network timing in benchmark measurements.

#### Qibla

Applies to `kible`.

Additional journey:

- start without sensor/location assumptions;
- open the qibla surface;
- verify the deterministic unsupported/permission/fallback state;
- exclude physical sensor accuracy from Macrobenchmark scope.

#### Counter

Applies to `zikirmatik`.

Additional journey:

- open the counter;
- increment a bounded number of times;
- verify the displayed count;
- recreate or relaunch once to exercise persisted state;
- restore the original state after measurement.

## 7. Stable Test Interface

Performance tests require stable identifiers.

The application will expose test-only semantic tags or resource identifiers for:

- root ready state;
- primary navigation container;
- first deterministic content item;
- content/detail ready state;
- audio play/pause control;
- prayer-times fallback state;
- qibla fallback state;
- counter value and increment control.

Identifiers are part of the internal test contract. Tests must not locate controls by translated visible strings when a stable tag can be used.

Production UI behavior and accessibility semantics must not be degraded to support benchmarking.

## 8. Baseline Profile Generation

### 8.1 Profile generator

Each flavor has a generated benchmark variant containing a `BaselineProfileGenerator` test.

The generator will:

- resolve the target package from the variant rather than a hardcoded global package;
- start from clean app data;
- run startup collection with `includeInStartupProfile = true`;
- run non-startup critical journeys with startup inclusion disabled;
- collect rules for the current flavor only;
- fail when no rules are generated;
- fail when output is written outside the expected flavor directory.

### 8.2 Startup Profile policy

Startup Profile content is restricted to:

- application process creation;
- `MainActivity` startup;
- dependency injection required before first frame;
- root Compose setup;
- primary navigation initialization;
- work required to reach the first stable display.

Scrolling, audio playback, detail rendering, counter interaction, qibla fallback, and prayer-times fallback are Baseline Profile journeys but are not added to the Startup Profile.

### 8.3 Profile quality checks

For every flavor, CI validates:

- both profile files exist where expected;
- the Baseline Profile is not empty;
- the Startup Profile is not empty;
- the Startup Profile is smaller than or equal to the Baseline Profile rule set;
- rules do not contain another flavor's package name;
- duplicate and malformed rules are rejected;
- generated-file changes are visible in Git;
- release artifacts contain the expected compiled profile metadata.

Profile size will be reported. The first implementation will warn, not fail, on size growth until three successful generations establish a normal range.

## 9. Macrobenchmark Measurements

### 9.1 Startup benchmarks

Every flavor will provide:

- cold startup benchmark using `StartupMode.COLD`;
- warm startup benchmark using `StartupMode.WARM`;
- `StartupTimingMetric` output;
- comparison runs with and without profile compilation where supported by the test environment.

The benchmark iteration count will be fixed in code and may be overridden only by a documented instrumentation argument for local diagnosis.

### 9.2 Rendering benchmarks

Deterministic journeys will use frame metrics for:

- primary list scrolling;
- detail-screen opening;
- returning to the list/root;
- counter interaction for `zikirmatik`;
- local content/audio control presentation where applicable.

Collected metrics include:

- frame duration percentiles;
- overrun/jank information exposed by the AndroidX benchmark result;
- trace files;
- benchmark device and build metadata.

### 9.3 Measurement authority

Gradle Managed Devices are authoritative for:

- whether a profile generator executes;
- whether each flavor launches;
- whether journeys complete;
- whether output is valid and isolated;
- whether the benchmark module and variants remain buildable.

Physical devices are authoritative for:

- startup regression comparisons;
- frame timing comparisons;
- release-blocking performance thresholds;
- trend baselines.

No release is blocked solely because a GitHub-hosted emulator produced a slower timing number.

## 10. Device Strategy

### 10.1 Managed profile-generation device

Profile generation uses one pinned Gradle Managed Device definition:

```text
Device family: Pixel 6 class
API level: 33
System image: aosp
ABI: x86_64
GPU: SwiftShader on GitHub-hosted runners
```

The exact device identifier and SDK packages are pinned in repository configuration. KVM availability is validated using the existing hardened Device Smoke pattern.

One device definition is shared across all flavors. Flavors run as separate matrix jobs to avoid a single long serial emulator session.

### 10.2 Physical benchmark device contract

The physical benchmark workflow targets a self-hosted runner label:

```text
self-hosted
android-performance
```

The runner contract requires:

- one connected, authorized Android device;
- charging disabled or controlled where supported;
- stable thermal state before measurement;
- screen brightness fixed;
- animations and background activity policy documented;
- no competing emulator;
- adb and required Android SDK tools pinned;
- device model, build fingerprint, API level, battery, temperature, and available storage recorded in each artifact.

Until such a runner is registered, physical benchmark execution remains manual and non-required. Profile generation and packaging validation remain fully automated.

## 11. GitHub Actions Design

### 11.1 Pull-request checks

Normal PR CI adds lightweight performance contract checks:

- benchmark module configuration tests;
- flavor parity test for all 17 flavors;
- benchmark variant task discovery;
- compile/assemble checks for the affected benchmark variants;
- stable-tag contract tests;
- profile path and packaging policy tests;
- no physical timing comparison;
- no automatic profile regeneration.

A change to the flavor catalog, benchmark module, navigation/test tags, performance workflow, or profile files triggers the relevant checks.

### 11.2 Scheduled and manual profile workflow

A new workflow, `Baseline Profiles`, runs:

- weekly;
- by manual dispatch;
- optionally for one selected flavor during diagnosis;
- all 17 flavors by default.

The flavor matrix has no local `max-parallel` cap. GitHub account capacity controls scheduling.

Each matrix job:

1. checks out the exact commit;
2. sets up pinned JDK, Gradle, Android SDK, KVM, and managed-device packages;
3. creates CI-safe Firebase placeholders only for its flavor;
4. generates that flavor's Baseline and Startup Profiles;
5. validates profile contents and package isolation;
6. uploads profile files, Android test results, logcat, and traces;
7. cleans temporary Firebase files in an `always()` step.

Matrix artifacts are retained for 14 days.

### 11.3 Automated profile update PR

A final aggregation job runs only after all 17 matrix jobs succeed.

It:

- downloads the 17 profile artifacts;
- verifies that there is exactly one expected profile pair per flavor;
- applies them to a clean checkout;
- runs profile validation again;
- creates or updates one branch named `automation/baseline-profiles`;
- creates one pull request containing all profile changes;
- does nothing when generated profiles are unchanged.

The job uses a dedicated repository-scoped GitHub App credential with only:

- contents: write;
- pull requests: write;
- metadata: read.

It must not use `DOPPLER_TOKEN`, production signing credentials, Play service-account credentials, or a broad personal access token.

The automation PR is subject to the normal repository ruleset and required checks.

### 11.4 Physical benchmark workflow

A second workflow, `Physical Performance`, runs by manual dispatch and may later run for release candidates.

Inputs:

- flavor or `all`;
- benchmark suite (`startup`, `frames`, or `all`);
- comparison baseline reference;
- diagnostic iteration override within a bounded range.

The workflow:

- verifies the physical runner contract;
- runs each selected flavor serially on the attached device to avoid device contention;
- captures benchmark JSON, traces, logcat, device metadata, and summary Markdown;
- uploads artifacts for 30 days;
- does not use release signing or Play publication credentials.

## 12. Regression Policy

### 12.1 Initial observation phase

The first three successful physical runs per flavor establish the baseline distribution.

During this phase:

- results are reported but do not fail release;
- failed journeys, crashes, missing traces, or invalid measurements still fail the workflow;
- emulator timing is informational;
- outliers are retained rather than silently discarded.

### 12.2 Enforced phase

After three accepted baselines for a flavor, the repository may enforce:

- cold startup p50 and p95 regression limits;
- warm startup p50 and p95 regression limits;
- frame-duration/jank regression limits;
- maximum profile size growth;
- minimum improvement compared with no-profile compilation where stable.

The initial proposed regression threshold is greater than 10 percent versus the accepted rolling baseline, with an absolute tolerance to prevent noise on small values. Threshold activation is a separate reviewed configuration change based on measured variance.

### 12.3 Failure behavior

The performance system fails immediately for:

- benchmark app crash;
- journey timeout;
- missing target package;
- empty or cross-flavor profile;
- profile generation outside the expected source set;
- profile missing from a release artifact;
- missing required trace/result files;
- malformed benchmark JSON;
- physical runner contract violation.

A timing regression is blocking only after the enforced phase is activated for that flavor and device baseline.

## 13. Diagnostics and Artifacts

Each profile-generation job stores:

- Baseline Profile;
- Startup Profile;
- instrumentation XML/results;
- managed-device reports;
- logcat;
- profile-validation report;
- task and variant metadata.

Each physical benchmark job stores:

- AndroidX benchmark JSON;
- trace files;
- logcat;
- device metadata;
- commit and flavor metadata;
- Markdown summary;
- comparison result when a baseline is supplied.

Artifacts must not contain:

- Firebase production configuration;
- keystores;
- service-account JSON;
- Doppler tokens;
- user data from a personal physical device.

## 14. Testing Strategy

Implementation follows test-driven development.

### 14.1 Repository policy tests

Tests will assert:

- `:performance:benchmark` is included;
- all 17 application flavors have matching benchmark flavors;
- no unknown benchmark flavor exists;
- profile workflow has no local parallel cap;
- physical workflow is manual-only until runner rollout is approved;
- workflow permissions are minimal;
- all actions are immutable SHA-pinned and listed in the approved action manifest;
- automation PR credentials are isolated from production secrets.

### 14.2 Gradle structure tests

Tests will assert:

- benchmark variants and tasks exist for every flavor;
- the benchmark module targets `:app`;
- release build behavior remains unchanged;
- generated source paths are flavor-scoped;
- Baseline Profile consumer configuration is active;
- package resolution is variant-aware.

### 14.3 Journey tests

Journey helpers will be unit-tested where logic can be separated from Android APIs.

On-device contract tests will verify:

- each flavor launches in performance mode;
- the shared root tag exists;
- each flavor maps to one valid family journey;
- deterministic fallback states are reachable without production services.

### 14.4 End-to-end verification

Before merge:

- all existing CI/security tests pass;
- benchmark module assembles for all variants;
- one representative profile generation completes on GMD;
- task discovery confirms all 17 generation tasks;
- existing Device Smoke remains green;
- one release AAB is inspected for compiled profile metadata.

After merge:

- manually run the full 17-flavor `Baseline Profiles` workflow;
- verify all artifacts;
- verify the automation PR is correctly generated or reports no changes;
- run one physical startup benchmark when the runner is available.

## 15. Security and Permissions

- Profile and benchmark workflows use `contents: read` by default.
- The aggregation job alone receives the dedicated GitHub App token.
- No `pull_request_target` trigger is allowed.
- Untrusted pull requests never receive automation, production, Doppler, signing, Firebase-release, or Play credentials.
- Generated Firebase placeholders remain CI-only and are deleted in `always()` cleanup.
- Actions remain pinned to full commit SHAs.
- New dependencies pass the existing repository/supply-chain policy.
- Performance artifacts are scanned for forbidden sensitive filenames before upload.

## 16. Rollout

### Phase 1: Foundation

- Add plugins, dependencies, module, flavor parity, and shared journey abstractions.
- Add deterministic performance build behavior and stable identifiers.
- Add Gradle and policy tests.

### Phase 2: Profile generation

- Add 17 Baseline Profile generators.
- Add GMD generation workflow.
- Validate and commit initial profile set through the automation PR mechanism.

### Phase 3: Macrobenchmark evidence

- Add cold/warm startup and frame benchmarks.
- Add manual physical-device workflow and reporting.
- Record the first three accepted baselines per flavor.

### Phase 4: Enforcement

- Add reviewed per-device/per-flavor thresholds.
- Make profile packaging a release requirement.
- Promote stable physical regression checks into the release-candidate gate.

## 17. Acceptance Criteria

The design is implemented when:

1. The repository contains one benchmark module targeting `:app`.
2. All 17 app flavors have matching benchmark variants.
3. Every flavor can generate its own non-empty Baseline and Startup Profile.
4. No generated profile contains another flavor's application package.
5. Shared and family-specific journeys execute without production network, sensor, account, billing, ad, or push dependencies.
6. Cold and warm startup benchmarks exist for all flavors.
7. Deterministic frame benchmarks exist for all applicable flavor families.
8. PR checks validate module, task, flavor, profile, and workflow contracts without running authoritative timing comparisons.
9. The scheduled/manual profile workflow runs all 17 flavors without a repository-local parallel limit.
10. A successful full run produces one validated profile pair and diagnostic artifact set per flavor.
11. The aggregation job creates or updates a single profile PR only when files changed.
12. Release artifact validation fails when the expected profile is absent.
13. The physical workflow records device metadata, traces, JSON, logcat, and summaries.
14. Existing CI, Security, CodeQL, Device Smoke, attested release, and Play Internal workflows remain green.
15. No production secret appears in benchmark source, logs, or artifacts.

## 18. Future Extensions

After the system has stable data, later changes may add:

- Firebase Performance and Play Vitals correlation;
- multiple physical device classes;
- low-RAM and tablet benchmarks;
- production rollout pause based on verified regression;
- release-note performance summaries;
- historical trend storage and dashboarding;
- profile-guided app-size analysis.

These extensions are intentionally outside the first implementation plan.
