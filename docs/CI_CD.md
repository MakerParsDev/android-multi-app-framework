# CI/CD operating model

As of 2026-07-23, this repository uses GitHub Actions as the authoritative CI/CD and
release automation surface. Azure Pipelines have been fully removed.

## Authoritative automation surface

- GitHub repository: `MakerParsDev/android-multi-app-framework`
- Primary workflow definitions: `.github/workflows/`
- GitHub account: MakerParsDev

## Workflow overview

| Workflow | Trigger | Responsibility |
| --- | --- | --- |
| `ci-pr.yml` | Pull request to main | PR quality gate: security, impact analysis, static analysis, tests, lint, coverage |
| `ci-main.yml` | Push to main | Full verification: quality gate + lint baseline budget + all-flavor lint + artifact uploads |
| `security.yml` | PR, schedule, manual | Secret scan, Semgrep SAST, workflow audit, dependency review |
| `dependency-audit.yml` | Weekly schedule | OSV-Scanner, Gradle dependency audit, dependency graph submission |
| `connected-tests.yml` | Manual | Instrumentation tests on KVM emulators |
| `performance.yml` | Weekly schedule, manual | Macrobenchmark baseline profile generation |
| `side-projects.yml` | PR, manual | Node.js quality for Firebase/Cloudflare side projects |
| `release.yml` | Manual (workflow_dispatch) | Signed release build + Play Console publish with environment protection |

## Local verification baseline

A local or VPS runner must first provide Android SDK and Firebase configuration.
Use the helper script so local maintenance follows the same sequence as CI:

```bash
scripts/ci/local-full-verification.sh --target-flavors all
```

The Android phase delegates to the blocking Gradle `qualityCheck` gate. It runs strict ktlint, Detekt defaults plus project-specific correctness rules, `validateFlavorVersions`, every application debug flavor unit-test/lint task, every Android library debug unit-test/lint task, and the Kover `quality` coverage variant. The gate validates the exact discovered task matrix before execution (17 app flavors and all Android library modules) and fails if a required task disappears or tests are disabled. CI and local runs collect root, app, core, and feature reports/test results into `build/quality-reports`.

Kover `0.9.8` is required for Android Gradle Plugin 9 variant and test-task support. The `quality` variant aggregates all 17 app debug flavors and every Android library `debug` variant without compiling release variants. Repository-wide line coverage is ratcheted at 8%, based on the first meaningful AGP 9 aggregate report (8.20%). `validateCriticalCoverage` additionally blocks missing, zero-measurement, or under-threshold class/package targets from `config/critical-coverage.json`. The runtime matrix and JVM/device boundary are documented in `docs/CRITICAL_TEST_COVERAGE.md`.

Android Lint baseline debt is separately ratcheted by `validateLintBaselines`. The committed budget, deterministic `docs/LINT_BASELINE_INVENTORY.md`, and branch-to-`origin/main` comparison block total, per-file, and per-lint-ID growth. Empty placeholder baseline files are forbidden.

Dependency lifecycle is governed by `config/dependency-policy.json` and `docs/DEPENDENCY_LIFECYCLE.md`. `auditDependencyCatalog` blocks dynamic/range versions, unapproved prereleases, inline catalog versions, missing version references, and expired exceptions. Quality runs retain `--warning-mode all` output under `build/reports/dependencies/gradle-quality.log`; `config/gradle-warning-policy.json` blocks unowned or expired Gradle/D8/ASM warnings, and the report collector publishes its audit with the deterministic catalog inventory. Every quality run assembles a representative Namaz Sureleri debug APK to exercise packaging/instrumentation warnings and performs a 17-flavor release bundle task-graph dry-run without signing or publishing.

Gradle and Kotlin heap limits are defined only in `gradle.properties`; CI jobs must not override `org.gradle.jvmargs` or `kotlin.daemon.jvmargs` through `GRADLE_OPTS`.
The hosted CI quality job pins `--max-workers=1` for its memory envelope; the local helper leaves worker selection to the centralized `org.gradle.workers.max` setting.

To bootstrap Java/Android SDK and run the complete public-source verification path on a fresh Linux runner:

```bash
scripts/ci/local-full-verification.sh --bootstrap-android-sdk --skip-firebase --target-flavors all
```

The authoritative versions are stored in `gradle.properties` under `toolchain.*`. The bootstrap installs the exact cmdline-tools, platform, build-tools, and platform-tools packages, writes the ignored machine-local `local.properties`, and prints the resolved/installed toolchain. `validateAndroidToolchainConfig` blocks hardcoded SDK/JDK versions or duplicated pipeline overrides. Omit `--skip-firebase` when Doppler or existing flavor Firebase files are available. The fresh-bootstrap verification path uses one Gradle worker and bounded static/lint/test/coverage phases, restarting Gradle between small flavor batches to stay deterministic on 8 GiB-class runners without reducing the 17-flavor/20-library gate. Normal local runs continue to use the single `qualityCheck` graph and `org.gradle.workers.max` from `gradle.properties`.

To run only non-Android validators while Firebase/Android config is not available:

```bash
scripts/ci/local-full-verification.sh --skip-android --skip-firebase
```

If an Android SDK check fails, run `scripts/ci/verify-android-toolchain.sh` for the exact missing package/path, then rerun the bootstrap command above.

## Secret and supply-chain gate

Every CI checkout uses full Git history and runs the security-gate job before Doppler, build, or publish steps. The gate:

- installs checksum-pinned Gitleaks `8.30.1`;
- validates an empty-by-default, owner/expiry-controlled baseline;
- blocks tracked environment, signing, service-account, and Firebase configuration files;
- scans all reachable history and PR merge-base diffs with fully redacted SARIF output;
- validates Gradle wrapper distribution and JAR checksums;
- validates secret ownership, rotation, and the GitHub legacy-mirror deadline.

```bash
bash scripts/ci/security_gate.sh --mode history --self-test
./gradlew staticQualityCheck
```

Operational ownership and incident response are documented in `docs/SECRET_OWNERSHIP_AND_ROTATION.md`.
