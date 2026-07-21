# Codex Audit Report - 2026-04-29

Repo: `MakerPars/android-multi-app-framework`

## Repo Snapshot

- Android monorepo with modules under `app`, `core/*`, `feature/*`, and `buildSrc`.
- Current product flavor count: 17.
- Flavor names from `buildSrc/src/main/kotlin/FlavorConfig.kt`:
  - `amenerrasulu`
  - `ayetelkursi`
  - `bereketduasi`
  - `esmaulhusna`
  - `fetihsuresi`
  - `insirahsuresi`
  - `ismiazamduasi`
  - `kenzularsduasi`
  - `kible`
  - `kuran_kerim`
  - `mucizedualar`
  - `namazsurelerivedualarsesli`
  - `namazvakitleri`
  - `nazarayeti`
  - `vakiasuresi`
  - `yasinsuresi`
  - `zikirmatik`
- `compileSdk` / `targetSdk`: 36.
- Debug ads flag: `USE_TEST_ADS=true`.
- Release ads flag: `USE_TEST_ADS=false`.
- Manifest AdMob metadata includes `APPLICATION_ID`, `DELAY_APP_MEASUREMENT_INIT=true`, `OPTIMIZE_INITIALIZATION=true`, and `OPTIMIZE_AD_LOADING=true`.

## Dependency Snapshot

Read from `gradle/libs.versions.toml` and Gradle wrapper:

| Component | Current |
| --- | --- |
| Gradle wrapper | 9.5.0 |
| Android Gradle Plugin | 9.2.0 |
| Kotlin | 2.3.21 |
| KSP | 2.3.7 |
| Compose BOM | 2026.04.01 |
| Firebase BOM | 34.12.0 |
| Google Mobile Ads SDK | 25.2.0 |
| UMP | 4.0.0 |
| Billing | 8.3.0 |
| OkHttp | 5.3.2 |

KSP note:

- Previous assumptions that KSP must use the old `2.x.y-1.0.z` format are not valid for this repo state.
- Maven Central metadata for `com.google.devtools.ksp.gradle.plugin` reports stable `2.3.7`.
- Official KSP release notes include `2.3.7`.
- The repo was already on the new `2.3.x` format; this audit updated `2.3.6` to stable `2.3.7`.

## Existing Workflows Before Jules

- `admob-daily-latest-health.yml`
- `admob-weekly-health.yml`
- `auto-debug-ops.yml`
- `dependency-review.yml`
- `dependency-submission.yml`
- `deploy-admin-notifications.yml`
- `gradle-wrapper-validation.yml`
- `manual-ops.yml`
- `manual-stacktrace-diagnostics-parallel.yml`
- `manual-stacktrace-diagnostics.yml`
- `quality-gate.yml`
- `release-parallel.yml`
- `release.yml`
- `sync-play-version-codes.yml`
- `verify-secrets-redacted.yml`

No `.github/jules` directory or Jules workflow existed before this change.

## AdMob, UMP, and Consent Classes

- Cold start path: `MainActivity -> AdOrchestrator.initialize -> AdManager.initialize`.
- `AdManager.initialize()` calls `requestConsentInfoUpdate()` on app launch.
- `loadAndShowConsentFormIfRequired()` remains after successful consent info update.
- `AdsConsentRuntimeState` is the process-level ad serving gate.
- `ConsentSyncIdProvider` already existed and uses App Set ID.
- Settings contains a privacy options entry point when UMP reports it is required.
- Revenue telemetry central class: `AdRevenueLogger`.
- Format managers reviewed: `BannerAd`, `NativeAdManager`, `AppOpenAdManager`, `InterstitialAdManager`, `RewardedAdManager`, `RewardedInterstitialAdManager`.

## Remote Config Ad Policy Keys

The canonical interstitial cap key is present and preserved:

```text
ads_interstitial_frequency_cap_ms
```

No duplicate `interstitial_min_interval_seconds` key was added.

Updated template/profile coverage now includes runtime policy keys that were previously only in Kotlin defaults:

- `ads_rewarded_max_per_session`
- `ads_reward_offer_routes_csv`
- `ads_native_banner_fallback_enabled`
- `ads_native_banner_fallback_packages_csv`
- `ads_report_freshness_max_hours`
- `ads_consent_retry_backoff_minutes`

## Corrected Prior Prompt Assumptions

- KSP `2.3.6` was not invalid because of the old KSP versioning format. It was stable-format compatible, but current stable metadata showed `2.3.7`, so the repo was updated to `2.3.7`.
- `app-ads.txt` must use `pub-3312485084079132`, not `ca-app-pub-...`; the checked-in file already had the correct Google line and no `ca-app-pub-` misuse.
- The existing Remote Config key `ads_interstitial_frequency_cap_ms` was preserved.
- `ConsentSyncIdProvider` and `setConsentSyncId(...)` already existed; this audit hardened validation instead of recreating the flow.
- Existing CI/release workflows were inspected before adding Jules workflows. Jules workflows were added without issue/comment triggers.

## Implemented Changes

Code:

- Moved `MobileAds.initialize()` into an IO-backed init scope with a 10 second app-level soft timeout.
- Added `MobileAdsInitializationGate` to prevent duplicate initialization, preserve ready state, and allow retry after timeout/failure.
- Kept UI state updates and ready callback invocation on `Dispatchers.Main.immediate`.
- Removed `initScope.cancel()` from successful initialization so timeout/failure does not make retry impossible.
- Hardened consent sync ID validation to enforce length and allowed character rules before calling `setConsentSyncId(...)`.
- Added rewarded and rewarded-interstitial show intent, not-loaded, started, impression, dismissed, and failed-show telemetry where it was lighter than other full-screen formats.
- Added `org.gradle.workers.max=2` to reduce local multi-flavor D8 memory pressure without changing dependency or app versions.
- Fixed two current Compose lint errors by replacing composable `Locale.getDefault()` reads with `LocalLocale.current.platformLocale`.

Tests:

- Added `MobileAdsInitializationGateTest`.
- Added `ConsentSyncIdProviderTest`.
- Added `AdsConsentRuntimeStateTest`.
- Added `AdRevenueLoggerTest`.
- Expanded `AdsPolicyProviderTest` for session caps, cooldowns, route blocklists, fallback keys, freshness, and consent retry backoff.
- Updated `ContentViewModelTest` fixture data for the current `UserPreferencesData.developerModeEnabled` contract.

Automation:

- Added strict/warn `app-ads.txt` validator and tests.
- Wired app-ads validation into `qualityCheck`, `quality-gate.yml`, `release.yml`, `manual-ops.yml`, and `release-parallel.yml`.
- Added explicit minimal permissions to `quality-gate.yml` and `auto-debug-ops.yml`.
- Replaced `Quality Gate` PR flavor-specific `test<Flavor>DebugUnitTest` and `lint<Flavor>Debug` calls with `testDebugUnitTest` and `lintDebug`; the flavor-specific tasks require untracked `google-services.json` files and are not safe for secretless PR validation.
- Added Jules scheduled/manual workflows and one same-repo CI failure fixer workflow.
- Added `.github/jules/prompts/*.md` policy prompts.

Docs:

- Added `docs/JULES_AUTOMATION_PLAN.md`.
- Added `docs/CONSENT_SYNC_SETUP.md`.
- Updated `docs/ADS_COMPLIANCE_RELEASE_CHECKLIST.md`.
- Updated `docs/APP_ADS_TXT_CHECKLIST.md`.
- Updated README version/workflow metadata.

## Verification Results

Completed locally:

Post-rebase refresh:

- The PR branch was rebased onto `origin/main` at `f6b091c`.
- Rebase conflicts were resolved in `AdManager.kt`, `ConsentSyncIdProvider.kt`, and `gradle/libs.versions.toml`.
- A rebase-only duplicate `developerModeEnabled` test fixture argument was removed from `ContentViewModelTest`.
- `:feature:ads:ktlintCheck` was rerun after cleaning the touched `AdManager.kt` import order.
- GitHub `Quality Gate` run `25116611511` then exposed two PR-only workflow problems: flavor-specific unit/lint tasks required untracked Firebase `google-services.json`, and changed-file ktlint consumed SARIF findings for `SettingsScreen.kt` import ordering. The workflow now uses secretless aggregate debug tasks for PRs and the touched settings import order was cleaned.

| Command | Result |
| --- | --- |
| `python scripts\ci\validate_app_ads_txt.py --mode strict` | Passed |
| `python scripts\ci\validate_app_ads_txt_test.py` | Passed, 5 tests |
| `python -m json.tool remoteconfig.template.json` | Passed |
| `python -m json.tool config\remote-config\ads-aggressive-profile.json` | Passed |
| `python -c "import pathlib, yaml; ..."` for Jules workflows | Passed |
| `.\gradlew.bat :feature:ads:testDebugUnitTest --continue --no-daemon --stacktrace` | Passed |
| `python scripts\ci\validate_ci_apps_catalog.py --mode warn --target-flavors all` | Passed, 17 flavors |
| `python scripts\ci\validate_admob_inventory.py --mode warn --target-flavors all` | Passed, 17 flavors |
| `.\gradlew.bat --version` | Passed, Gradle 9.5.0 |
| `.\gradlew.bat tasks --all --no-daemon` with KSP filtering | Passed, KSP tasks discovered |
| `.\gradlew.bat validateFlavorVersions --no-daemon --stacktrace` | Passed; Play service account not configured locally |
| `.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace` | First run failed with D8 Java heap OOM; passed after limiting Gradle workers to 2 |
| `.\gradlew.bat lintDebug --continue --no-daemon --stacktrace` | First run found two `NonObservableLocale` errors; passed after code fixes |
| `.\gradlew.bat detekt --continue --no-daemon --stacktrace` | First run found one too-generic Mobile Ads init catch; passed after code fix |
| `.\gradlew.bat :feature:content:testDebugUnitTest --continue --no-daemon --stacktrace` | Passed after the rebase fixture cleanup |
| `.\gradlew.bat testDebugUnitTest --continue --no-daemon --stacktrace` | First run found one stale/rebase test fixture compile error; passed after fixture fix |
| `.\gradlew.bat :feature:ads:ktlintCheck --no-daemon --stacktrace` | Passed after touched import order cleanup |
| `.\gradlew.bat qualityCheck -PdisableTests=true --no-daemon --stacktrace` | Passed; task logs existing ktlint style findings but does not fail the build |
| `python scripts/ci/verify_changed_ktlint.py --repo-root . --base-ref main` | Passed; no ktlint findings in changed files after refreshing ktlint reports |
| `.\gradlew.bat testDebugUnitTest --continue --stacktrace --no-daemon --max-workers=2` | Passed after Quality Gate PR task alignment |
| `.\gradlew.bat lintDebug --continue --stacktrace --no-daemon --max-workers=2` | Passed after Quality Gate PR task alignment |
| `.\gradlew.bat detekt ktlintCheck --stacktrace --no-daemon --max-workers=2` | Passed |
| `.\gradlew.bat :app:assembleDebug --stacktrace --no-daemon --max-workers=2` | Passed |
| `.\gradlew.bat qualityCheck -PdisableTests=true --stacktrace --no-daemon --max-workers=2` | Passed |
| `git diff --check` | Passed with only existing CRLF warning on `gradle/wrapper/gradle-wrapper.properties` |
| `Select-String` for `ca-app-pub-` in `app-ads.txt` | Passed; no `ca-app-pub-` lines, required publisher line found |
| `Select-String` for issue/comment/pull_request_target Jules triggers | Passed; no unsafe issue/comment/`pull_request_target` triggers found |

## Manual Console Checks

Not code-verifiable:

- `JULES_API_KEY` must be added as a GitHub Actions secret.
- GitHub Dependency Graph must be enabled for the repository; after enabling it, set repository variable `DEPENDENCY_REVIEW_ENABLED=true` so `.github/workflows/dependency-review.yml` runs `actions/dependency-review-action`.
- AdMob Privacy and Messaging GDPR/US states messages must be checked in AdMob.
- Consent syncing must be enabled in AdMob for eligible apps.
- app-ads.txt crawl status must be checked in AdMob after deploy.
- Play Console Data Safety declarations must be checked for ads, analytics, consent, and app set ID use.
- Ad Inspector and EEA debug geography must be checked on a real test device.
- Release candidate must be smoke-tested to confirm test ads are off.

## Open Risks

- Live hosted app-ads.txt and AdMob crawl status were not checked from the repository.
- Play Console and AdMob console state cannot be proven by code.
- Jules action behavior depends on `google-labs-code/jules-invoke@v1` and a valid `JULES_API_KEY` secret.
- GitHub Dependency Review currently depends on repository-side Dependency Graph availability; the workflow warns and skips until `DEPENDENCY_REVIEW_ENABLED=true` is configured after enabling the graph.

## Source References

- KSP releases: https://github.com/google/ksp/releases/tag/2.3.7
- Maven Central KSP metadata: https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml
- Google Mobile Ads Android optimize initialization/loading: https://developers.google.com/ad-manager/mobile-ads-sdk/android/optimize-initialization
- Google AdMob Android privacy/UMP: https://developers.google.com/admob/android/privacy
- Google Mobile Ads consent syncing: https://developers.google.com/ad-manager/mobile-ads-sdk/android/privacy/sync-consent
- Google AdMob app-ads.txt help: https://support.google.com/admob/answer/9363762
- GitHub Actions workflow syntax and permissions: https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions
- GitHub Actions workflow_run event: https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows#workflow_run
