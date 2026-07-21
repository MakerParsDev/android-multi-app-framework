# Cloudflare Migration Plan (Spark / No Billing)

## 1) Problem Statement

Current blockers when Firebase/GCP billing is disabled:

- Firebase Functions (Gen2) endpoints return 5xx and become unusable.
- Admin panel depends on these endpoints for auth policy + reports + test push.
- Mobile app still depends on backend endpoint for purchase verification.

This document defines a low-risk migration path to keep production running on free tiers.

## Current Status (2026-07-20)

- `workers/admin-api` implementation is complete in the repository, including:
  - Firebase Auth protected admin endpoints.
  - Cryptographic Firebase App Check verification for `/verifyPurchase`.
  - App Check-protected `POST /registerDevice` (alias `/register-device`).
  - Backend-owned Firestore device upserts through service-account REST access.
- Android device registration is backend-only:
  - `HttpPushRegistrationSender` is the sole Hilt binding.
  - Every request includes `X-Firebase-AppCheck` and the complete registration payload.
  - Direct client writes to Firestore `/devices` are denied by rules.
- Repository quality gates enforce this boundary and Firestore emulator tests cover client write denial.
- `contentapp-admin-api` is live at `https://contentapp-admin-api.oaslananka.workers.dev` and the protected execution identity is available through the production Doppler/VPS runbook.
- Worker deployments must inject the exact merged source commit through `SERVICE_GIT_SHA`; Wrangler version metadata is exposed through `CF_VERSION_METADATA`.
- Android Release build/publish automation runs `scripts/ci/admin_backend_smoke.py` before signing or Gradle publication work. The gate requires traceable `/health` metadata plus `401` responses for unauthenticated purchase, missing App Check, and invalid App Check requests.
- Deployment IDs, valid purchase evidence, Android canary evidence, and rollback references remain tracked in #103/#105.

## 2) Keep vs Move

Keep on Firebase (no-cost friendly components):

- Firebase Auth
- FCM
- Remote Config
- Crashlytics / Analytics

Move from Firebase Functions to Cloudflare Workers:

- Admin HTTP endpoints
- Public utility endpoints (already partially migrated)
- Scheduled jobs currently running on Cloud Scheduler + Functions

## 3) Endpoint Inventory and Migration Priority

### Already on Cloudflare Worker (`workers/content-api`)

- `GET /api/other-apps`
- `GET /api/audio-manifest`
- `GET /api/audio/:key`
- `POST /api/recaptcha-verify`

### Critical (migrate first)

1. `POST /adminAccessCheck`
2. `POST /healthCheck`
3. `POST /sendTestNotification`
4. `POST /deviceCoverageReport`

Reason: Admin login and day-to-day operations depend on these directly.

### Second wave

5. `GET /adminGetRemoteConfig`
6. `POST /adminUpdateRemoteConfig`
7. `POST /adminGetFlavorHubSummary`
8. `POST /adminGetAnalyticsSummary`
9. `POST /adminGetRevenueSummary`
10. `POST /adPerformance` (manual + today + cached weekly report)

### Third wave

11. `POST /verifyPurchase` (mobile runtime dependency)
12. `POST /registerDevice` (mobile notification registration dependency)

## 4) Architecture Options (Free-tier focused)

## Option A: Recommended Hybrid (lowest migration risk)

- Cloudflare Workers for HTTP endpoints + cron orchestration.
- Keep Firebase Auth, Firestore, FCM, Remote Config.
- Workers call Google APIs using service account credentials (secret in Worker).

Pros:

- Minimal Android and admin panel changes.
- Reuses existing Firebase data model.
- Fastest recovery from current outage.

Cons:

- Still coupled to Google APIs (but not Firebase Functions hosting).

## Option B: Supabase-centric

- Move admin auth policy + reports + schedule storage to Supabase.
- Keep only FCM/Remote Config on Firebase (or migrate push provider too).

Pros:

- Cleaner backend ownership in one place.

Cons:

- Higher migration cost and bigger code/data changes.

## Option C: OneSignal for push orchestration

- Offload dispatch scheduling/segmentation to OneSignal.
- Keep Android app + admin panel integration through OneSignal APIs.

Pros:

- Less custom scheduler maintenance.

Cons:

- Vendor migration complexity, payload model changes.

## 5) Implementation Plan (Option A)

## Phase 0 - Safety

1. Freeze Firebase Functions changes.
2. Set admin panel to explicit backend base URL via `VITE_FUNCTIONS_BASE_URL`.
3. Keep current Cloudflare content worker untouched.

## Phase 1 - Admin Core Worker

Create new worker: `side-projects/cloudflare/workers/admin-api`

Initial routes:

- `POST /adminAccessCheck`
- `POST /healthCheck`
- `POST /sendTestNotification`
- `POST /deviceCoverageReport`

Security requirements:

- Validate Firebase ID token (JWT verification against Google certs).
- Enforce admin allowlist from Firestore `admins/{uid}` or env allowlist fallback.
- CORS allow only admin domains.
- Add request rate limits and structured logs.

## Phase 2 - Reporting + Remote Config

Add routes:

- `GET /adminGetRemoteConfig`
- `POST /adminUpdateRemoteConfig`
- `POST /adminGetFlavorHubSummary`
- `POST /adminGetAnalyticsSummary`
- `POST /adminGetRevenueSummary`
- `POST /adPerformance`

Use cached responses (KV) for expensive report calls.

## Phase 3 - Mobile Runtime Security Endpoints

Add routes:

- `POST /verifyPurchase`
- `POST /registerDevice` (alias: `/register-device`)

Requirements:

- `/verifyPurchase` verifies Firebase ID token and Firebase App Check before calling Google Play Android Publisher API.
- `/registerDevice` verifies Firebase App Check, validates a strict bounded payload, and writes through service-account Firestore REST access.
- App Check JWT verification must validate signature, `RS256`, token type, issuer, expiration, audience/project number, and the 17-app Firebase App ID allowlist.
- Persist purchase verification results to `purchase_verifications`; persist only the approved device schema to `devices/{installationId}`.
- Do not log FCM tokens, installation IDs, purchase tokens, raw exception messages, or exact device identifiers.

Android changes:

- Set `PURCHASE_VERIFICATION_URL` to the verified Worker `/verifyPurchase` URL.
- Set `PUSH_REGISTRATION_URL` to the verified Worker `/registerDevice` URL.
- Use only `HttpPushRegistrationSender`; do not restore a client Firestore sender or insecure fallback.

## Phase 4 - Cron Migration

Cloudflare Cron triggers:

- Hourly dispatch replacement for `dispatchNotifications`.
- Weekly ad report generation replacement.

Use idempotency markers in Firestore to avoid duplicate sends.

## 6) Required Secrets / Vars Mapping

Current Firebase Functions env -> Cloudflare Worker secrets:

- `ADMIN_ALLOWED_EMAILS`
- `ADMOB_CLIENT_ID`
- `ADMOB_CLIENT_SECRET`
- `ADMOB_REFRESH_TOKEN`
- `ADMOB_PUBLISHER_ID`
- `GOOGLE_RECAPTCHA_SECRET_KEY`
- `PLAY_ANDROID_PUBLISHER_SERVICE_ACCOUNT_JSON`
- `VERIFY_PURCHASE_REQUIRE_APP_CHECK=true`
- `REGISTER_DEVICE_REQUIRE_APP_CHECK=true`
- `FIREBASE_PROJECT_NUMBER`
- `FIREBASE_APP_CHECK_ALLOWED_APP_IDS`

Worker runtime vars:

- `FIREBASE_PROJECT_ID`
- `FIREBASE_WEB_API_KEY` (only if needed for specific REST paths)
- `ALLOWED_ADMIN_ORIGINS`

## 7) Client Change Points

Admin panel (`side-projects/admin-notifications`):

- Keep code as-is, set:
- `VITE_FUNCTIONS_BASE_URL=https://<admin-api-worker-domain>`

Android app:

- `PUSH_REGISTRATION_URL`: point to the verified Worker `/registerDevice` endpoint.
- `PURCHASE_VERIFICATION_URL`: point to the verified Worker `/verifyPurchase` endpoint.
- Both URLs are release secrets/configuration and must be smoke-tested before publishing an Android build.
- Direct Firestore writes from Android are prohibited and blocked by repository validation.

## 8) Production Cutover Checklist

Execute in this order; record deployment IDs, timestamps, and evidence in #103 and #105.

1. Deploy `contentapp-admin-api` with all required vars/secrets through a protected identity.
2. Verify `GET /health` returns 200 with `service=cloudflare-admin-api`, `environment=production`, the exact merged `gitSha`, and a non-empty Cloudflare Worker version ID; unauthenticated protected calls must return 401—not 404/500.
3. Verify App Check rejection cases for both mobile endpoints: missing, tampered, expired, wrong project/audience, and unapproved app ID.
4. Verify one valid test purchase follows the expected Google Play verification result.
5. Verify one valid `/registerDevice` request creates/updates only the approved Firestore device fields.
6. Publish an internal Android build using the Worker URLs and confirm token-refresh/manual-sync registration succeeds without direct Firestore writes.
7. Observe Crashlytics, push registration success/failure counters, FCM delivery, and Firestore document shape during the canary window.
8. Deploy Firestore rules that deny every client write to `/devices` only after steps 1–7 pass.
9. Re-run emulator tests and production smoke tests after the rules deployment.
10. Expand rollout; then retire the legacy Firebase `registerDevice` source/export path and stale secrets.

## 9) Rollback Strategy

Rollback must preserve the security boundary.

- Preferred rollback: deploy the previous healthy Worker version while keeping App Check enforcement and server-owned `/devices` writes.
- Android rollback: restore the last Android build whose `PUSH_REGISTRATION_URL` points to a healthy verified backend.
- Firestore rules rollback may be paired only with an Android/backend rollback reviewed as one change. **Never re-enable anonymous or authenticated client writes to `/devices` as a standalone hotfix.**
- Keep Worker deployment IDs and Android release revisions in the issue evidence so rollback targets are deterministic.
- If no healthy backend exists, disable/suppress registration retries through controlled configuration rather than opening Firestore writes.
- Re-run `/health`, App Check negative tests, valid registration, and push-delivery smoke tests after rollback.

## 10) Success Criteria

- Admin login works without Firebase Functions billing.
- Test push and coverage reports are operational.
- Purchase verification and device registration paths run from the verified Worker endpoints.
- Firestore `/devices` permits admin reads and rejects every client write.
- Hourly dispatch and weekly report run from Cloudflare cron.
- No user-visible regression in app monetization and notifications.
