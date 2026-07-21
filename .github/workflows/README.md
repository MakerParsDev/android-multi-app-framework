# Active GitHub Actions boundary

Only the following GitHub Actions workflows are approved for the active
`.github/workflows/` directory:

- `ci-pr.yml` — secret-free pull-request and `main` Android quality checks.
- `security.yml` — secret-free workflow, repository, and dependency security checks.

Human pull requests and `main` pushes resolve the complete flavor matrix from
`.ci/apps.json`. Every cataloged Android app is linted and assembled in its own
matrix job. The current catalog contains 17 app flavors; the matrix count is not
hard-coded, so newly cataloged apps are included automatically.

Each app build generates a marked, non-production `google-services.json` from
committed public catalogs and removes it after the job. Production Firebase
credentials are never downloaded or exposed.

Dependabot version updates are consolidated into one monthly Android maintenance
pull request covering GitHub Actions and Gradle. Dependabot pull requests run the
workflow/security gates and a lightweight Gradle smoke check, but skip the full
Android quality and app-build matrix to avoid runner congestion.

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
