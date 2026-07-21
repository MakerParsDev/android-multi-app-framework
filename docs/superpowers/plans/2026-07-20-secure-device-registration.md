# Secure Device Registration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route all Android device registration through an App Check-protected server endpoint and deny every direct client write to Firestore `/devices`.

**Architecture:** The Cloudflare admin Worker exposes `/registerDevice` and verifies Firebase App Check with the existing cryptographic verifier before validating and upserting a strict device record through the service-account Firestore REST path. Android uses only `HttpPushRegistrationSender`, obtains a fresh App Check token, sends the complete `PushRegistrationPayload`, and never falls back to Firestore client writes. Firestore rules become admin-read/server-write-only and emulator tests enforce the boundary.

**Tech Stack:** TypeScript ES2022, Cloudflare Workers/WebCrypto, Firebase App Check/JWKS, Firestore REST API, Kotlin/Android/Hilt/OkHttp, Firebase Rules Unit Testing, Node 22 test runner, Python CI validators, Azure Pipelines.

## Global Constraints

- App Check verification must fail closed in production and accept only the 17 app IDs in `config/firebase-apps.json`.
- No Firebase Auth requirement may be added to device registration because anonymous app users must register for notifications.
- Direct Android Firestore writes and insecure fallback behavior are prohibited.
- Device registration logs must not contain installation IDs, FCM tokens, exact device identifiers, or raw exception messages.
- Live deployment and rules rollout remain blocked until #103 provides protected production credentials and smoke-test evidence.
- Every behavior change follows red-green-refactor and is committed separately.

---

### Task 1: Cloudflare device registration contract

**Files:**
- Create: `side-projects/cloudflare/workers/admin-api/src/deviceRegistration.ts`
- Create: `side-projects/cloudflare/workers/admin-api/test/deviceRegistration.test.mjs`
- Modify: `side-projects/cloudflare/workers/admin-api/tsconfig.test.json`

**Interfaces:**
- Produces: `handleDeviceRegistration(request, dependencies): Promise<Response>`.
- Produces: `DeviceRegistrationDependencies` with `verifyAppCheck(token)`, `upsertDevice(id, record)`, and `nowEpochMs()`.
- Produces: strict `DeviceRegistrationRecord` containing only approved fields plus `appCheckAppId` and `updatedAt`.

- [ ] **Step 1: Write failing Node tests** for method/content-type validation, missing/invalid App Check, invalid installation ID/FCM token/package, full payload sanitization, successful upsert, and failed upsert.
- [ ] **Step 2: Run `npm --prefix side-projects/cloudflare/workers/admin-api test`** and verify failure because `deviceRegistration.ts` does not exist.
- [ ] **Step 3: Implement the minimal parser and dependency-injected handler** with strict regexes, bounded text/maps/timestamps, default `REGISTER_DEVICE_REQUIRE_APP_CHECK=true`, and no sensitive logging.
- [ ] **Step 4: Re-run the Node tests** and verify all handler cases pass.
- [ ] **Step 5: Commit** with `feat(push): add App Check protected device registration handler`.

### Task 2: Wire `/registerDevice` into the Worker

**Files:**
- Modify: `side-projects/cloudflare/workers/admin-api/src/index.ts`
- Modify: `side-projects/cloudflare/workers/admin-api/wrangler.toml`
- Modify: `side-projects/cloudflare/workers/admin-api/README.md`
- Modify: `scripts/ci/validate_cloudflare_app_check_config.py`
- Modify: `scripts/ci/cloudflare_app_check_config_test.py`

**Interfaces:**
- Consumes: `handleDeviceRegistration` from Task 1.
- Consumes: `verifyFirebaseAppCheckToken`, `upsertFirestoreDoc`, and `toFirestoreValue`.
- Produces: POST `/registerDevice` and `/register-device` returning 200/400/401/415/500/503 without leaking secrets.

- [ ] **Step 1: Extend the config validator test** to require `REGISTER_DEVICE_REQUIRE_APP_CHECK = "true"`.
- [ ] **Step 2: Run the config test** and verify it fails against current `wrangler.toml`.
- [ ] **Step 3: Add the router adapter**, pass the verified app ID into the record, convert the approved record to Firestore REST fields, and require App Check in `wrangler.toml`.
- [ ] **Step 4: Run `npm run verify`, the config validator, and `wrangler deploy --dry-run`** and verify clean output.
- [ ] **Step 5: Commit** with `feat(push): expose secure Worker registration endpoint`.

### Task 3: Make Android HTTP registration complete and App Check-protected

**Files:**
- Modify: `core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/push/HttpPushRegistrationSender.kt`
- Modify: `core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/di/PushRegistrationModule.kt`
- Delete: `core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/push/FirestorePushRegistrationSender.kt`
- Create: `core/firebase/src/test/java/com/parsfilo/contentapp/core/firebase/push/PushRegistrationRequestTest.kt`

**Interfaces:**
- Consumes: `FirebaseAppCheck.getAppCheckToken(false)`.
- Produces: `buildPushRegistrationRequest(url, payload, appCheckToken): Request` for direct unit testing.
- Produces: JSON containing every `PushRegistrationPayload` field and `X-Firebase-AppCheck` header.

- [ ] **Step 1: Write failing Kotlin tests** asserting the App Check header, complete JSON (including nullable telemetry fields/maps), and rejection of a blank App Check token.
- [ ] **Step 2: Run `:core:firebase:testDebugUnitTest`** and verify the tests fail against the current partial payload/no-header implementation.
- [ ] **Step 3: Inject `FirebaseAppCheck`, retrieve the token before network I/O, build the complete request, bind Hilt to `HttpPushRegistrationSender`, and delete the Firestore sender**.
- [ ] **Step 4: Run `:core:firebase:testDebugUnitTest`, `:core:firebase:lintDebug`, and a representative app Kotlin compile excluding Google Services**.
- [ ] **Step 5: Commit** with `fix(push): require App Check for HTTP device registration`.

### Task 4: Close Firestore client writes with emulator tests

**Files:**
- Modify: `side-projects/firebase/firestore.rules`
- Modify: `side-projects/firebase/firebase.json`
- Create: `side-projects/firebase/rules-tests/package.json`
- Create: `side-projects/firebase/rules-tests/package-lock.json`
- Create: `side-projects/firebase/rules-tests/firestore.rules.test.mjs`

**Interfaces:**
- Produces: `/devices/{deviceId}` admin read only; all client create/update/delete denied.
- Produces: `npm run test:emulator` as the canonical rules test command.

- [ ] **Step 1: Write emulator tests** for unauthenticated/authenticated create, update, delete denial; non-admin read denial; and admin read success.
- [ ] **Step 2: Run the emulator test** and verify the current permissive rules fail the denial assertions.
- [ ] **Step 3: Change `/devices` rules to `allow read: if isAdmin(); allow write: if false;`** and add the Firestore emulator configuration.
- [ ] **Step 4: Re-run the emulator tests** and verify all cases pass.
- [ ] **Step 5: Commit** with `fix(security): deny direct device writes in Firestore rules`.

### Task 5: Add repository and Azure guardrails

**Files:**
- Create: `scripts/ci/validate_secure_device_registration.py`
- Create: `scripts/ci/secure_device_registration_test.py`
- Modify: `build.gradle.kts`
- Modify: `azure-pipelines/ci.yml`

**Interfaces:**
- Produces: a static validator that fails if Hilt binds the Firestore sender, a production Firestore sender exists, `/devices` permits writes, the Worker route disappears, or App Check enforcement is disabled.
- Produces: Azure Node 22 rules-emulator and Worker tests on every relevant change.

- [ ] **Step 1: Write failing Python validator tests** covering each prohibited regression.
- [ ] **Step 2: Run the tests** and verify failure before the validator exists.
- [ ] **Step 3: Implement the validator, wire it to `staticQualityCheck`/`qualityCheck`, and add deterministic `npm ci` + emulator execution to Azure CI**.
- [ ] **Step 4: Run the Python suite, `staticQualityCheck`, Worker verify, rules emulator, Android unit/lint/compile, and secret scan**.
- [ ] **Step 5: Commit** with `ci(security): enforce backend-only device registration`.

### Task 6: Deployment handoff and issue evidence

**Files:**
- Modify: `docs/CLOUDFLARE_FIREBASE_MIGRATION.md`
- Modify: `.env.template` only if endpoint naming changes.

**Interfaces:**
- Produces: exact rollout/rollback order and smoke-test matrix for #103/#105.

- [ ] **Step 1: Document rollout order:** deploy Worker, verify endpoint, ship Android HTTP binding, verify registrations, deploy Firestore rules, observe, then remove legacy Firebase function code.
- [ ] **Step 2: Document rollback:** restore prior rules only together with rollback Android build; never re-enable anonymous writes as a standalone hotfix.
- [ ] **Step 3: Run a final full local verification with `--skip-firebase` plus all side-project tests**.
- [ ] **Step 4: Add issue comments with commit SHAs, test counts, remaining credential blocker, and live acceptance checklist**.
- [ ] **Step 5: Keep #105 open with `status/implemented` + `needs-manual-verification` until production deployment and App Check enforcement evidence exists.**
