# Device Smoke Stabilization and First Attested Release Design

**Date:** 2026-07-22

## Goal

Make the managed-device smoke workflow deterministic without real Firebase traffic, restore release Firebase configurations from the existing protected source hierarchy, and prove one signed and attested `kuran_kerim` AAB on GitHub Actions.

## Current Failure

The managed-device build compiles with a CI-only `google-services.json`, but application startup invokes Firebase Performance/Installations and rejects the deliberately fake API key. The latest managed-device report therefore reaches test execution but fails before the root Compose assertion. Release workflows also require `FIREBASE_CONFIGS_ZIP_BASE64`, while the repository already models Cloudflare R2 as an approved primary source and base64 as a fallback.

## Selected Approach

### 1. Dedicated `ciSmoke` build type

Add a debuggable `ciSmoke` build type initialized from `debug`. It exposes `BuildConfig.CI_SMOKE=true`; all other build types expose `false`. A `src/ciSmoke/AndroidManifest.xml` overlay disables Firebase Performance, Analytics, Crashlytics, Messaging auto-init, and default Firebase data collection before `Application.onCreate`.

`App.onCreate` performs only local logging and root UI prerequisites in CI smoke mode, then returns before App Check, FCM, Remote Config/endpoints, billing connection, audio prefetch, alarms, and app-open ads. Production and ordinary debug behavior remain unchanged.

### 2. Managed-device workflow

Run `ciPixel2Api30Kuran_kerimCiSmokeAndroidTest`, keep the secret-free placeholder, migrate Compose test rules to the v2 API, and always upload JUnit/HTML reports. A regression test verifies the task name, Firebase deactivation metadata, and smoke guard.

### 3. Firebase release source resolver

Extend `materialize_firebase_configs.py` with a private `--zip-file` input. Add `restore_firebase_configs.sh` with this order:

1. Use `FIREBASE_CONFIGS_ZIP_BASE64` when present.
2. Otherwise require the complete R2 credential quartet, install the repository-locked Wrangler dependencies with `npm ci --ignore-scripts`, download the configured object to a `0700` temporary directory, and pass the archive to the existing allowlisted materializer.
3. Fail closed when neither source is complete.

The script never extracts a ZIP wholesale and always deletes downloaded archives. Both attested and Play internal build scripts use the same resolver.

### 4. First release proof

After merge, dispatch Device Smoke and require success. Then dispatch `Attested Release Artifact` for `kuran_kerim` with 14-day retention. Verify exactly one AAB, checksum, artifact, and attestation; do not publish to Play in this phase.

## Testing

- Python regression tests for build/workflow policy and Firebase ZIP source handling.
- Shell regression test for source precedence, incomplete R2 rejection, and cleanup.
- Gradle dry-runs for the new managed-device and release tasks.
- GitHub PR checks.
- Live Device Smoke run.
- Live attested release run and artifact metadata verification.

## Security Constraints

- No real Firebase API key in PR CI.
- No secrets printed or persisted.
- R2 download uses the committed npm lock and an exact package tree.
- Existing ZIP allowlist, package, project ID, app ID, and OAuth-client validation remain mandatory.
- Release remains manual, protected by the `production` environment, and does not publish to Play.
