# Azure DevOps pipelines

GitHub Actions for this repository are disabled. CI/CD should run from Azure DevOps under:

- Organization: `https://dev.azure.com/oaslanankadev`
- Project: `OpenSource`
- Repository: `android-multi-app-framework`

## Required Azure variable group

Create/authorize a Library variable group named:

```txt
android-multi-app-framework-prod
```

Required variables:

```txt
DOPPLER_PROJECT=android-multi-app-framework
DOPPLER_CONFIG=prod
DOPPLER_TOKEN=<secret Doppler service token for prod config>
```

`DOPPLER_TOKEN` must be marked secret. The pipeline reads all Android, Play Console, Firebase, AdMob, signing, and Cloudflare values from Doppler at runtime.

## Pipeline files

```txt
azure-pipelines/ci.yml
azure-pipelines/full-verification.yml
azure-pipelines/release.yml
azure-pipelines/sync-play-version-codes.yml
azure-pipelines/admob-health.yml
azure-pipelines/admob-weekly-optimization.yml
```

## Intended use

### CI

Runs on `main` and PRs:

```txt
azure-pipelines/ci.yml
```

Runs catalog validation, AdMob inventory validation, app-ads.txt validation, env contract, unit tests, detekt, ktlint, Android lint, and Kover reports.

### Full Verification

Manual heavy verification:

```txt
azure-pipelines/full-verification.yml
```

Parameter:

```txt
targetFlavors=all
```

### Release

Manual build/publish pipeline:

```txt
azure-pipelines/release.yml
```

Parameters:

```txt
targetFlavors=all or comma-separated flavor list
buildType=Debug|Release
doQuality=true|false
doBuild=true|false
doInternalTest=true|false
doPublish=true|false
updatePlayListing=true|false
```

Safety rules:

- `doInternalTest` and `doPublish` cannot both be true in the same run.
- Publishing requires `buildType=Release`.
- Publishing auto-syncs/bump-checks Play versionCodes before upload.

### Sync Play Version Codes

Manual Play Console version sync:

```txt
azure-pipelines/sync-play-version-codes.yml
```

Reads live Play track versionCodes, updates `app-versions.properties`, pushes a sync branch, and creates an Azure Repos PR when `System.AccessToken` is available.

### AdMob Health

Scheduled daily and manual AdMob health:

```txt
azure-pipelines/admob-health.yml
```

Modes:

```txt
latest
today
weekly
```

### AdMob Weekly Optimization

Scheduled every Monday and available for manual runs:

```txt
azure-pipelines/admob-weekly-optimization.yml
```

The pipeline compares the two most recent completed weeks by app/flavor, format, platform, app version, ad unit and country. It publishes deterministic JSON and Markdown artifacts with match rate, show rate, CTR, eCPM, revenue trends, bounded experiment hypotheses and rollback conditions. It does not change ad frequency or publish Remote Config values.

## Azure pipeline creation commands

After the Azure Repos repository contains this code, create the pipeline definitions:

```bash
az pipelines create --name android-ci --repository android-multi-app-framework --branch main --yml-path azure-pipelines/ci.yml --repository-type tfsgit
az pipelines create --name android-full-verification --repository android-multi-app-framework --branch main --yml-path azure-pipelines/full-verification.yml --repository-type tfsgit
az pipelines create --name android-release --repository android-multi-app-framework --branch main --yml-path azure-pipelines/release.yml --repository-type tfsgit
az pipelines create --name android-sync-play-version-codes --repository android-multi-app-framework --branch main --yml-path azure-pipelines/sync-play-version-codes.yml --repository-type tfsgit
az pipelines create --name android-admob-health --repository android-multi-app-framework --branch main --yml-path azure-pipelines/admob-health.yml --repository-type tfsgit
az pipelines create --name android-admob-weekly-optimization --repository android-multi-app-framework --branch main --yml-path azure-pipelines/admob-weekly-optimization.yml --repository-type tfsgit
```

For `sync-play-version-codes.yml`, allow scripts to access the OAuth token so the pipeline can push the sync branch and create an Azure Repos PR.
