# GitHub Actions are intentionally paused

GitHub Actions are temporarily disabled for this repository. Do not add runnable
workflow YAML files under `.github/workflows` while the repository operates in
Azure-only automation mode.

Current authoritative automation surface:

- Azure DevOps organization: `https://dev.azure.com/oaslanankadev`
- Azure DevOps project: `OpenSource`
- Repository: `android-multi-app-framework`
- Pipeline definitions: `azure-pipelines/*.yml`

The previous GitHub workflow definitions were moved to `.github/workflows.disabled/`
for historical reference and rollback only. Re-enable them only through a deliberate
repository governance decision and after confirming that Azure and GitHub gates will
not run conflicting build, release, Play Console, AdMob, signing, or secret flows.

Related tracking issue: #18.
