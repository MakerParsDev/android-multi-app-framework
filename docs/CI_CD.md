# CI/CD operating model

As of 2026-07-09, this repository uses Azure DevOps as the authoritative CI/CD and
release automation surface. GitHub Actions are intentionally paused and must remain
non-runnable until a future governance decision reverses this policy.

## Authoritative automation surface

- Azure DevOps organization: `https://dev.azure.com/oaslanankadev`
- Azure DevOps project: `OpenSource`
- Repository: `android-multi-app-framework`
- Primary pipeline definitions: `azure-pipelines/*.yml`

## GitHub Actions status

Runnable GitHub workflow definitions must not live under `.github/workflows` while
Azure-only mode is active. Historical workflow definitions are stored under
`.github/workflows.disabled/` for rollback/reference only.

GitHub Actions may still show historical disabled workflows in the GitHub UI until
GitHub refreshes repository workflow metadata. Treat Azure DevOps as the source of
truth for build, test, release, Play Console, AdMob, signing, and secret flows.

## Required Azure gates

The following checks should be represented in Azure pipelines before production
release:

1. App catalog validation for all 17 flavors.
2. AdMob inventory validation.
3. `app-ads.txt` validation.
4. Environment/secret contract validation.
5. Firebase/Google Sign-In configuration validation.
6. Gradle flavor version validation.
7. Unit tests.
8. Detekt.
9. ktlint.
10. Android lint.
11. Kover XML/HTML coverage reporting.
12. Stable dependency catalog audit and full Gradle warning artifact.
13. Release task-graph dry-run and Play version-code guard.

## Pipeline responsibilities

| Pipeline | Responsibility |
| --- | --- |
| `azure-pipelines/ci.yml` | Normal PR/main quality gate. |
| `azure-pipelines/full-verification.yml` | Manual heavy validation across selected/all flavors. |
| `azure-pipelines/release.yml` | Build, internal test, Play publish, and release guard orchestration. |
| `azure-pipelines/sync-play-version-codes.yml` | Sync live Play version codes into `app-versions.properties`. |
| `azure-pipelines/admob-health.yml` | Scheduled/manual AdMob health validation. |
| `azure-pipelines/dependency-audit.yml` | Weekly stable-policy and resolved dependency graph audit. |

## Local verification baseline

A local or VPS runner must first provide Android SDK and Firebase configuration.
Use the helper script so local maintenance follows the same sequence as Azure CI:

```bash
scripts/ci/local-full-verification.sh --target-flavors all
```

The Android phase delegates to the blocking Gradle `qualityCheck` gate. It runs strict ktlint, Detekt defaults plus project-specific correctness rules, `validateFlavorVersions`, every application debug flavor unit-test/lint task, every Android library debug unit-test/lint task, and the Kover `quality` coverage variant. The gate validates the exact discovered task matrix before execution (17 app flavors and all Android library modules) and fails if a required task disappears or tests are disabled. CI and local runs collect root, app, core, and feature reports/test results into `build/quality-reports`.

Kover `0.9.8` is required for Android Gradle Plugin 9 variant and test-task support. The `quality` variant aggregates all 17 app debug flavors and every Android library `debug` variant without compiling release variants. Repository-wide line coverage is ratcheted at 8%, based on the first meaningful AGP 9 aggregate report (8.20%). `validateCriticalCoverage` additionally blocks missing, zero-measurement, or under-threshold class/package targets from `config/critical-coverage.json`. The runtime matrix and JVM/device boundary are documented in `docs/CRITICAL_TEST_COVERAGE.md`.

Android Lint baseline debt is separately ratcheted by `validateLintBaselines`. The committed budget, deterministic `docs/LINT_BASELINE_INVENTORY.md`, and branch-to-`origin/main` comparison block total, per-file, and per-lint-ID growth. Empty placeholder baseline files are forbidden.

Dependency lifecycle is governed by `config/dependency-policy.json` and `docs/DEPENDENCY_LIFECYCLE.md`. `auditDependencyCatalog` blocks dynamic/range versions, unapproved prereleases, inline catalog versions, missing version references, and expired exceptions. Quality runs retain `--warning-mode all` output under `build/reports/dependencies/gradle-quality.log`; `config/gradle-warning-policy.json` blocks unowned or expired Gradle/D8/ASM warnings, and the report collector publishes its audit with the deterministic catalog inventory. Every quality run assembles a representative Namaz Sureleri debug APK to exercise packaging/instrumentation warnings and performs a 17-flavor release bundle task-graph dry-run without signing or publishing.

Gradle and Kotlin heap limits are defined only in `gradle.properties`; active Azure pipelines must not override `org.gradle.jvmargs` or `kotlin.daemon.jvmargs` through `GRADLE_OPTS`.
The hosted Azure quality job pins `--max-workers=1` for its memory envelope; the local helper leaves worker selection to the centralized `org.gradle.workers.max` setting.

To bootstrap Java/Android SDK and run the complete public-source verification path on a fresh Linux runner:

```bash
scripts/ci/local-full-verification.sh --bootstrap-android-sdk --skip-firebase --target-flavors all
```

The authoritative versions are stored in `gradle.properties` under `toolchain.*`. The bootstrap installs the exact cmdline-tools, platform, build-tools, and platform-tools packages, writes the ignored machine-local `local.properties`, exports Azure variables when applicable, and prints the resolved/installed toolchain. `validateAndroidToolchainConfig` blocks hardcoded SDK/JDK versions or duplicated pipeline overrides. Omit `--skip-firebase` when Doppler or existing flavor Firebase files are available. The fresh-bootstrap verification path uses one Gradle worker and bounded static/lint/test/coverage phases, restarting Gradle between small flavor batches to stay deterministic on 8 GiB-class runners without reducing the 17-flavor/20-library gate. Normal local runs continue to use the single `qualityCheck` graph and `org.gradle.workers.max` from `gradle.properties`.

To run only non-Android validators while Firebase/Android config is not available:

```bash
scripts/ci/local-full-verification.sh --skip-android --skip-firebase
```

If an Android SDK check fails, run `scripts/ci/verify-android-toolchain.sh` for the exact missing package/path, then rerun the bootstrap command above.

## Re-enabling GitHub Actions

Re-enable GitHub Actions only after all of the following are true:

- A repository governance issue approves the change.
- GitHub and Azure gates have non-conflicting responsibilities.
- Secrets are reviewed and scoped for the chosen automation surface.
- Release, Play Console, signing, Firebase, AdMob, and Cloudflare flows are not duplicated.
- `.github/workflows/README.md` and this document are updated.

Related issues: #18, #19, #20, #21, #22, #27.

## Secret and supply-chain gate

Every active Azure checkout uses full Git history and runs `pipelines/templates/steps/security-gate.yml` before Doppler, Secure Files, build, or publish steps. The gate:

- installs checksum-pinned Gitleaks `8.30.1`;
- validates an empty-by-default, owner/expiry-controlled baseline;
- blocks tracked environment, signing, service-account, and Firebase configuration files;
- scans all reachable history and PR merge-base diffs with fully redacted SARIF output;
- validates Gradle wrapper distribution and JAR checksums;
- validates secret ownership, rotation, and the GitHub legacy-mirror deadline.

`azure-pipelines/security-audit.yml` runs the full-history scan, synthetic leak self-test, dependency audit, and wrapper policy weekly.

```bash
bash scripts/ci/security_gate.sh --mode history --self-test
./gradlew staticQualityCheck
```

Operational ownership and incident response are documented in `docs/SECRET_OWNERSHIP_AND_ROTATION.md`.
