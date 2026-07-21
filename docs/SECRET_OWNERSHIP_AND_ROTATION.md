# Secret Ownership and Rotation Runbook

## Canonical stores

GitHub Actions repository secrets are legacy mirrors, not canonical storage.

| Material | Canonical store | Owner | Rotation |
|---|---|---|---|
| Runtime/provider credentials and application configuration | Doppler project `android-multi-app-framework`, config `prod` | `platform/security` | 90 days and after suspected disclosure |
| Doppler bootstrap token and signing passwords/alias | Azure secret variable group | `release/platform` | Token 90 days; signing passwords yearly or after incident |
| Upload keystore and Play service-account JSON | Azure Secure Files | `release/platform` | Quarterly access review; replace on compromise or role change |
| Per-job secure-file paths | Azure runtime variables | `release/platform` | Generated and removed per job |
| Repository/project identifiers | Azure non-secret variables | `release/platform` | On change |
| CI repository identity | Azure `System.AccessToken`, workload identity, or OIDC | `platform/security` | Short-lived; long-lived personal tokens prohibited |

The machine-readable classification and migration deadline are in `config/secret-ownership.json`.

## Trust boundaries

1. Every Azure checkout uses `fetchDepth: 0`.
2. `pipelines/templates/steps/security-gate.yml` is the first post-checkout step.
3. Gitleaks scans all reachable history before Doppler, Secure Files, build, or publish activity. Pull-request runs also scan merge-base-to-HEAD changes.
4. SARIF reports use full redaction.
5. Signing and Play files are downloaded only in release jobs and deleted in `condition: always()` cleanup steps.
6. Fork or untrusted PR jobs must not receive production variable groups, Secure Files, service accounts, or Doppler tokens.

## Rotation procedure

1. Create a least-privilege replacement at the provider.
2. Store it in the canonical store without deleting the old credential.
3. Run full verification or release preflight.
4. Confirm runtime health and provider audit logs.
5. Revoke the old credential.
6. Remove the GitHub mirror.
7. Record owner, date, evidence, and next rotation in the operational issue.

Never print decoded files, bearer headers, refresh tokens, signing passwords, or base64 payloads in CI logs.

## Credential guidance

- **Upload keystore:** keep only in Azure Secure Files. A compromise requires Play upload-key reset, password rotation, Secure File replacement, and release verification.
- **Play service account:** keep JSON only in Azure Secure Files, grant least privilege, rotate every 90 days, and prefer workload identity when supported end to end.
- **Doppler token:** use a project/config-scoped service token in an Azure secret variable group; rotate every 90 days.
- **Firebase:** keep Android configuration untracked and materialize it from controlled delivery. Privileged server/service material remains in Doppler.
- **Cloudflare, AdMob, Sentry, reCAPTCHA:** prefer scoped API tokens; rotate OAuth/service credentials every 90 days and revoke before deleting the final copy.

## Incident response

1. Stop affected release/publish pipelines and disable the exposed identity.
2. Revoke/rotate at the provider before rewriting history.
3. Scan full history and pipeline artifacts with the pinned scanner.
4. Review Azure, Doppler, GitHub, Google/Play, Cloudflare, AdMob, Firebase, and Sentry audit logs as applicable.
5. Reissue dependent credentials and test least privilege.
6. Keep a historical finding visible until revocation is proven; history rewriting does not replace rotation.
7. Document impact, timeline, owners, and prevention actions in a security issue.

## Baseline exceptions

The default baseline is empty. A historical fingerprint may be added only in `config/secret-scan-policy.json` with exact fingerprint, owner, meaningful reason, and expiration. `.gitleaksignore` is generated from that policy. An active credential must be revoked, not allowlisted.

## Verification

```bash
bash scripts/ci/security_gate.sh --mode history --self-test
python3 scripts/ci/validate_secret_ownership.py
python3 scripts/ci/validate_supply_chain_policy.py
python3 scripts/ci/validate_security_pipeline.py
```
