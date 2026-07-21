# Side-project quality, deploy, and drift gate

## Blocking matrix

The canonical gate covers five deployable Node projects plus Firestore rules:

| Project | Blocking checks |
|---|---|
| Admin Notifications | ESLint, Vitest API/helper contracts, TypeScript/Vite production build, `build-metadata.json` |
| Cloudflare Admin API | TypeScript, ESLint, 18 App Check/device/health contract tests, Wrangler dry-run |
| Cloudflare Content API | TypeScript, ESLint, health/reCAPTCHA/audio route tests, Wrangler dry-run |
| Cloudflare SSV Callback | TypeScript, ESLint, health/method/required-parameter tests, Wrangler dry-run |
| Firebase Functions | generated git metadata, TypeScript, ESLint, reset/health contract tests |
| Firestore rules | emulator-backed rules tests |

Run the same gate locally and in CI:

```bash
bash scripts/ci/run_side_project_quality.sh --install
```

A successful run writes:

- `build/reports/side-projects/quality.json`
- `build/reports/side-projects/deployment-drift.json`
- `build/reports/side-projects/npm-audit.json`

The quality report records the full git SHA and per-project checks. A deploy is
rejected unless the report is successful, matches `HEAD`, and is newer than six
hours.


## Dependency audit policy

Every lockfile is audited twice after `npm ci`:

- Production dependencies must report **zero vulnerabilities**. No production
  exception is permitted.
- The full development tree must contain no high or critical finding.
- Low or moderate dev-only findings are allowed only when the exact advisory is
  recorded in `side-projects/audit-policy.json` with an owner, tracking issue,
  rationale, upgrade plan, and expiry date.
- Unused or expired exceptions fail CI so stale debt cannot remain hidden.
- `npm audit fix --force` is prohibited. Updates must remain semver-compatible or
  be handled as an explicitly tested breaking upgrade.

The current upstream-only dev exceptions are tracked by GitHub issue #124 and
expire on 15 August 2026. The policy validator will block the pipeline after that
date unless the dependency tree is upgraded and the entries are removed.

## Supported deploy path

Direct `wrangler deploy`, `wrangler pages deploy`, and `firebase deploy` commands
are not the supported production path. Use each package's `npm run deploy` after
the repository gate. The deploy wrapper injects Cloudflare build metadata and
runs strict post-deploy SHA verification.

Required post-deploy health variables:

| Project | Variable |
|---|---|
| Admin Notifications | `ADMIN_NOTIFICATIONS_METADATA_URL` |
| Admin API | `ADMIN_API_HEALTH_URL` |
| Content API | `CONTENT_API_HEALTH_URL` |
| SSV Callback | `SSV_CALLBACK_HEALTH_URL` |
| Firebase Functions | `FIREBASE_FUNCTIONS_HEALTH_URL` |

Example:

```bash
bash scripts/ci/run_side_project_quality.sh --install
CONTENT_API_HEALTH_URL="https://contentapp-content-api.<subdomain>.workers.dev/health" \
  npm --prefix side-projects/cloudflare/workers/content-api run deploy
```

No deploy command prints or accepts secret values on its command line.

## Health and drift

Cloudflare Workers and Firebase Functions expose `gitSha` and `builtAt`; the
admin frontend publishes `/build-metadata.json`. The admin System Health panel
shows service SHAs and flags a mismatch against the panel build. CI can receive
public health endpoints through `SIDE_PROJECT_HEALTH_ENDPOINTS_JSON`; drift is
reported without failing a pull request. The verified deploy wrapper reruns the
same check in strict mode and fails after a mismatch.

Example endpoint configuration:

```json
{
  "admin-notifications": {"url": "https://admin.example/build-metadata.json"},
  "admin-api": {"url": "https://admin-api.example/health"},
  "content-api": {"url": "https://content-api.example/health"},
  "ssv-callback": {"url": "https://ssv.example/health"},
  "firebase-functions": {"url": "https://example.cloudfunctions.net/healthCheck", "method": "POST"}
}
```

## Secret and binding validation

- Admin API requires Firebase project/App Check vars and its existing Firebase,
  AdMob, and service-account secrets. Wrangler dry-run validates declared
  bindings without exposing secret values.
- Content API requires `AUDIO_BUCKET`, `OTHER_APPS_JSON_URL`, and the
  `GOOGLE_RECAPTCHA_SECRET_KEY` secret.
- SSV Callback requires `SSV_DEDUP` KV.
- Firebase Functions relies on Firebase runtime identity plus the existing
  Android Publisher/Remote Config environment contract.
- Admin Notifications requires its documented `VITE_*` build variables.

Secrets remain in the existing secret stores. They must never be committed,
placed in report JSON, or passed through PR logs.

## Rollback

Before mutation, record the current Cloudflare deployment/version, Pages
production deployment, or Firebase Functions release. Roll back by restoring the
previous verified deployment/version; do not weaken App Check, auth, Firestore,
SSV signature, or sender restrictions. After rollback, run the strict drift
checker against the rollback commit SHA and archive its JSON report.
