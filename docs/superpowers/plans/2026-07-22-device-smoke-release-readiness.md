# Device Smoke Stabilization and First Attested Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Device Smoke pass without real Firebase traffic and complete one protected signed/attested `kuran_kerim` release.

**Architecture:** A dedicated `ciSmoke` build type disables remote Firebase initialization at manifest and application levels. Release Firebase configs are restored through one fail-closed resolver that prefers the existing base64 secret and otherwise downloads the R2 archive using repository-locked Wrangler dependencies.

**Tech Stack:** Kotlin/AGP 9.2, Gradle 9.5.1, Python 3, Bash, Firebase Android SDK, Gradle Managed Devices, GitHub Actions, Doppler, Cloudflare R2.

## Global Constraints

- Ordinary debug and release runtime behavior must not change.
- PR and Device Smoke workflows must remain secret-free.
- No workflow may print or persist secret values.
- Firebase ZIP entries must continue to pass the existing path/package/project/app/OAuth validation.
- Release workflows remain manual and `production`-environment protected.

---

### Task 1: CI smoke build contract

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/ciSmoke/AndroidManifest.xml`
- Modify: `app/src/main/java/com/parsfilo/contentapp/App.kt`
- Modify: `scripts/ci/professional_ci_workflows_test.py`

**Interfaces:**
- Produces: build type `ciSmoke`, `BuildConfig.CI_SMOKE`, task suffix `CiSmokeAndroidTest`.

- [ ] Add failing assertions for the build type, manifest metadata, and startup guard.
- [ ] Run `python3 scripts/ci/professional_ci_workflows_test.py`; expect failure.
- [ ] Add `CI_SMOKE=false` default, a debug-derived `ciSmoke` build type with `CI_SMOKE=true`, the manifest overlay, and an early local-only startup return.
- [ ] Re-run the regression test; expect all tests to pass.
- [ ] Commit as `feat: add Firebase-safe CI smoke build`.

### Task 2: Managed-device workflow and tests

**Files:**
- Modify: `.github/workflows/device-smoke.yml`
- Modify: `app/src/androidTest/java/com/parsfilo/contentapp/AppLaunchSmokeTest.kt`
- Modify: `app/src/androidTest/java/com/parsfilo/contentapp/SimpleInteractionSmokeTest.kt`
- Modify: `scripts/ci/professional_ci_workflows_test.py`

**Interfaces:**
- Consumes: `ciPixel2Api30Kuran_kerimCiSmokeAndroidTest`.

- [ ] Add a failing workflow assertion for the `CiSmoke` task and Compose v2 imports.
- [ ] Run the regression test; expect failure.
- [ ] Update the workflow and test imports.
- [ ] Run the regression test and `./gradlew ciPixel2Api30Kuran_kerimCiSmokeAndroidTest --dry-run --no-daemon --no-configuration-cache`; expect success.
- [ ] Commit as `test: run managed device smoke in isolated build`.

### Task 3: Protected Firebase config source resolver

**Files:**
- Modify: `scripts/ci/materialize_firebase_configs.py`
- Create: `scripts/ci/restore_firebase_configs.sh`
- Create: `scripts/ci/restore_firebase_configs_test.py`
- Modify: `scripts/ci/build_attested_release.sh`
- Modify: `scripts/ci/build_play_internal_release.sh`
- Modify: `.github/workflows/release-attested.yml`
- Modify: `.github/workflows/play-internal.yml`

**Interfaces:**
- Produces: `materialize_firebase_configs.py --zip-file PATH`; `restore_firebase_configs.sh FLAVOR`.

- [ ] Write tests covering base64 precedence, ZIP-file validation, incomplete R2 rejection, mocked R2 success, and cleanup.
- [ ] Run the tests; expect missing-interface failures.
- [ ] Implement `--zip-file` and the fail-closed resolver.
- [ ] Add pinned Node setup and locked `npm ci --ignore-scripts` support to release workflows.
- [ ] Replace direct materializer calls in both release build scripts.
- [ ] Run resolver tests, workflow policy tests, actionlint, and Gitleaks; expect success.
- [ ] Commit as `feat: restore release Firebase configs from protected sources`.

### Task 4: Pull request rollout

**Files:**
- Modify: `docs/RELEASE.md`
- Modify: `docs/SECRETS_SETUP.md`

- [ ] Document the `ciSmoke` isolation and R2/base64 source order.
- [ ] Run all focused tests, `git diff --check`, actionlint, zizmor high-confidence scan, and Gitleaks.
- [ ] Push `feature/device-smoke-release-readiness` and open a PR.
- [ ] Wait for required checks; fix failures on the same branch.
- [ ] Squash merge only when required checks are green.

### Task 5: Live release proof

- [ ] Dispatch Device Smoke on merged `main`; require success.
- [ ] Dispatch `Attested Release Artifact` with `flavor=kuran_kerim`, `retention_days=14`.
- [ ] Verify the workflow completes successfully and exposes one signed AAB plus `.sha256`.
- [ ] Verify the attestation step succeeds and no Firebase/keystore/service-account temporary file is uploaded.
- [ ] Record run IDs and final `main` SHA in the completion report.
