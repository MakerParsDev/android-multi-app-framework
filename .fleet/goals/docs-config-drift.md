# Goal: Documentation & Configuration Drift Alignment

Ensure documentation in `docs/`, `README.md`, and `.github/workflows/README.md` reflects current active scripts and CI/CD operations.

## Focus Areas
1. **Workflow & Script Alignment**: Detect obsolete references to retired Azure pipelines, legacy scripts, or disabled workflows.
2. **Secrets & Deployment Docs**: Verify `docs/SECRETS_SETUP.md` and `docs/ENV_SECRET_CONTRACT.md` accurately describe Doppler secrets and environment contracts.
3. **Jules Maintenance Plan**: Keep `docs/JULES_AUTOMATION_PLAN.md` aligned with Jules Fleet orchestration workflows.

## Verification Commands
- `python3 scripts/ci/workflow_policy_test.py`

## Rules
- Only update documentation when genuine drift exists.
