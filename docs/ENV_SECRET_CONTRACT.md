# Environment Secret Contract

Canonical secret storage is split by trust boundary:

- Azure Secure Files for the upload keystore and Play service-account JSON;
- Azure secret variable groups for the Doppler bootstrap token and signing passwords/alias;
- Doppler project `android-multi-app-framework`, config `prod`, for runtime/provider credentials and application configuration.

GitHub Actions repository secrets are legacy mirrors scheduled for migration and deletion; they are not canonical. See `docs/SECRET_OWNERSHIP_AND_ROTATION.md`, `docs/GITHUB_SECRET_CLEANUP_PLAN.md`, and `config/secret-ownership.json`.

Local development uses `.env` copied from `.env.template`. Local values are never committed.

Azure pipelines validate the committed contract and run full-history secret scanning before any secret-consuming step.

## Required release/publish secrets

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `PLAY_SERVICE_ACCOUNT_JSON_BASE64`
- `PUSH_REGISTRATION_URL`
- `PURCHASE_VERIFICATION_URL`
- `FIREBASE_WEB_CLIENT_ID`
- `FIREBASE_CONFIGS_ZIP_BASE64`
- `CF_R2_ACCOUNT_ID`
- `CF_API_TOKEN`
- `CF_R2_BUCKET`
- `CF_R2_FIREBASE_OBJECT`
- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_API_TOKEN`
- `ADMIN_ALLOWED_EMAILS`
- `ADMOB_CLIENT_ID`
- `ADMOB_CLIENT_SECRET`
- `ADMOB_REFRESH_TOKEN`
- `ADMOB_PUBLISHER_ID`

## Validation

Run:

```bash
bash scripts/ci/verify_env_contract.sh
```

Windows PowerShell:

```powershell
bash scripts/ci/verify_env_contract.sh
```


## Firebase Config Delivery

`FIREBASE_CONFIGS_ZIP_BASE64` must be a base64-encoded zip consumed by `scripts/ci/materialize_firebase_configs.py`.
The script only accepts whitelisted google-services paths and validates JSON, package name, Firebase appId, and projectId before writing files into `app/src/<flavor>/google-services.json`.

`FIREBASE_WEB_CLIENT_ID` is required for release/internal verification and is cross-checked by `scripts/ci/verify_google_signin_config.py --require-web-client-id`.
