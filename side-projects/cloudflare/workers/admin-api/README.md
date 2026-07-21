# Cloudflare Admin API Worker

Phase 1 replacement for Firebase Functions endpoints that block when billing is disabled.

Current routes:

- `POST /adminAccessCheck`
- `POST /healthCheck` (also accepts `GET /health`)
- `POST /sendTestNotification`
- `POST /deviceCoverageReport`
- `GET /adminGetRemoteConfig`
- `POST /adminUpdateRemoteConfig`
- `POST /adminGetFlavorHubSummary`
- `POST /adminGetAnalyticsSummary`
- `POST /adminGetRevenueSummary`
- `POST /adPerformance`

Cron jobs (Cloudflare Scheduler):

- `0 * * * *` → `dispatchNotifications` (hourly)
- `15 7 * * 1` → weekly AdMob ad performance generation/persistence

## Local dev

```bash
npm install
npm run dev
```

## Required secrets/vars

Required:

- `FIREBASE_PROJECT_ID` (wrangler var)
- `FIREBASE_WEB_API_KEY` (wrangler secret)
- `FIREBASE_PROJECT_NUMBER` (required var; App Check issuer/audience validation)
- `FIREBASE_APP_CHECK_ALLOWED_APP_IDS` (required var; comma-separated Firebase App IDs)
- `VERIFY_PURCHASE_REQUIRE_APP_CHECK=true` (required production var)
- `REGISTER_DEVICE_REQUIRE_APP_CHECK=true` (required production var)
- `SERVICE_ENVIRONMENT=production` (required production var)
- `SERVICE_GIT_SHA` (required deploy-time var; exact merged repository commit)
- `CF_VERSION_METADATA` (Wrangler version metadata binding configured in `wrangler.toml`)

Optional but recommended:

- `FIREBASE_SERVICE_ACCOUNT_JSON` (wrangler secret, enables Firestore `/admins/{uid}` lookup + allowlist upsert)
- `ADMIN_ALLOWED_EMAILS` (wrangler var)
- `ALLOWED_ADMIN_ORIGINS` (wrangler var)
- `ADMOB_PUBLISHER_ID` (wrangler var, optional preferred account)
- `AD_HEALTH_MIN_REQUESTS` (wrangler var)
- `AD_HEALTH_FILL_RATE_THRESHOLD` (wrangler var)
- `AD_HEALTH_SHOW_RATE_THRESHOLD` (wrangler var)
- `ADMOB_CLIENT_ID` (wrangler secret)
- `ADMOB_CLIENT_SECRET` (wrangler secret)
- `ADMOB_REFRESH_TOKEN` (wrangler secret)

Secret commands:

```bash
npx wrangler secret put FIREBASE_WEB_API_KEY
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON
npx wrangler secret put ADMOB_CLIENT_ID
npx wrangler secret put ADMOB_CLIENT_SECRET
npx wrangler secret put ADMOB_REFRESH_TOKEN
```

## Deploy

Inject the exact merged source commit. Do not use a moving branch name or a dirty checkout:

```bash
GIT_COMMIT_SHA="$(git rev-parse HEAD)"
npx wrangler deploy --var "SERVICE_GIT_SHA:${GIT_COMMIT_SHA}"
```

`GET /health` returns the service environment, `gitSha`, and Cloudflare Worker version metadata. After deployment, run the secret-free contract smoke test:

```bash
python3 ../../../../scripts/ci/admin_backend_smoke.py \
  --purchase-url https://contentapp-admin-api.oaslananka.workers.dev/verifyPurchase \
  --push-url https://contentapp-admin-api.oaslananka.workers.dev/registerDevice \
  --expected-git-sha "$GIT_COMMIT_SHA"
```

Before deployment, record `wrangler deployments list` output. Roll back by redeploying the previous verified source commit/version, then rerun the same smoke contract. Never relax App Check or Firestore device-write rules as a rollback shortcut.

## Device registration

- `POST /registerDevice` (alias: `/register-device`)
- Requires `X-Firebase-AppCheck` in production and writes only the approved device schema through the service-account Firestore REST API.
- Android clients must not write `/devices` directly.
