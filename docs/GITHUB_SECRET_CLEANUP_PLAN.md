# GitHub Repository Secret Cleanup Plan

## Current state

A read-only inventory on 10 July 2026 found **47 GitHub Actions repository secret names**. The exact name-only inventory is preserved in GitHub issue `#27`; values were never read or exported.

GitHub Actions is not the active Android delivery platform. `config/secret-ownership.json` marks these entries as `legacy_mirror_remove_after_migration` with a required review by **15 August 2026**.

## Migration groups

1. **Azure Secure Files:** upload keystore and Play service-account material. Remove base64 mirrors after release preflight succeeds.
2. **Azure secret variable groups:** Doppler bootstrap token plus signing passwords/alias. Project/config identifiers become ordinary Azure variables.
3. **Doppler:** AdMob, Cloudflare/R2, Firebase delivery material, reCAPTCHA, Sentry, backend endpoints, and runtime configuration.
4. **Revoke or replace:** long-lived GitHub personal access tokens, Cloudflare global-key flows, account-email metadata, and per-job file paths.

## Safe deletion sequence

1. Export GitHub secret **names only** and compare the count with the policy inventory.
2. Classify each name using `config/secret-ownership.json`.
3. Create or verify the canonical Azure/Doppler destination.
4. Rotate long-lived or high-impact credentials at the provider.
5. Run full verification and the affected release/health workflow.
6. Confirm provider audit logs and application health.
7. Delete the GitHub mirror.
8. Repeat the name-only inventory and record the reduced count in issue `#27`.

Deletion is an explicit operational action after destination verification. Pull requests must not automatically delete repository secrets.

## Completion criteria

- No production credential is canonical in GitHub repository secrets.
- GitHub contains no duplicate base64 files, runtime paths, or repository metadata as secrets.
- Release signing and Play credentials exist only in Azure Secure Files plus job-scoped paths.
- Runtime/provider credentials exist only in Doppler or an approved workload-identity flow.
- The legacy count is zero or every remaining item has a time-limited, owner-approved exception.
- Issue `#27` contains rotation and deletion evidence.
