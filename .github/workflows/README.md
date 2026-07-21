# Active GitHub Actions boundary

Only the following GitHub Actions workflows are approved for the active
`.github/workflows/` directory:

- `ci-pr.yml` — secret-free pull-request and `main` Android quality checks.
- `security.yml` — secret-free workflow, repository, and dependency security checks.

Representative Android builds generate marked, non-production `google-services.json`
files from committed public app catalogs and remove them after each matrix job.
They never download or expose production Firebase credentials.

Both active workflows must use immutable full commit SHAs for external actions,
`permissions: contents: read`, bounded job timeouts, checkout with
`persist-credentials: false`, and no direct GitHub template expansion inside
shell `run` blocks.

The historical workflows under `.github/workflows.disabled/` are reference
material only. Release, Play Console, Cloudflare, Firebase-secret, AdMob
credential, signing, manual operations, and deployment workflows must remain
disabled until each migrated workflow passes all of the following checks:

1. `python3 scripts/ci/workflow_policy.py --repo .`
2. `actionlint`
3. `zizmor` without high-confidence findings

Do not copy or rename a disabled workflow into `.github/workflows/` as a shortcut.
Secret-backed release and deployment automation requires a separate reviewed
migration with protected environments and minimum job-level permissions.
