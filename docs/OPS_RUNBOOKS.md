# Ops Runbooks

## Secret contract

Canonical CI/CD secret storage is GitHub Actions repository or environment secrets.

Use `.env.template` as the committed placeholder contract and local `.env` for workstation-only values.

## Local validation

```bash
bash scripts/ci/verify_env_contract.sh
```

Windows PowerShell:

```powershell
bash scripts/ci/verify_env_contract.sh
```

## Firebase functions deploy

```bash
npm --prefix side-projects/firebase/functions run verify-env
npm --prefix side-projects/firebase/functions run build
npm --prefix side-projects/firebase/functions run deploy
```

## Android release/publish

Use GitHub Actions workflows:

- `.github/workflows/release.yml`
- `.github/workflows/release-parallel.yml`
- `.github/workflows/manual-ops.yml`

Required values must exist as GitHub Actions secrets before release/publish workflows run.
