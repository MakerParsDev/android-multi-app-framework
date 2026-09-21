# Active GitHub Actions boundary

Only reviewed workflows belong in the active `.github/workflows/` directory.
Historical workflows under `.github/workflows.disabled/` are reference material
and must not be re-enabled by copying or renaming them.

| Workflow | Trigger | Purpose | Expensive path |
|---|---|---|---|
| CI | PR, `main` push | policy, secrets, quality, 17-flavor lint/assemble | skipped for Dependabot |
| Security | PR, `main` push, weekly, manual | actionlint, zizmor, Gitleaks, dependency review | dependency review only on PR |
| Dependency Submission | dependency/build changes on `main`, manual | submit resolved Gradle dependency graph | trusted `main` only |
| CodeQL | human PR, `main`, weekly, manual | Java/Kotlin manual-build analysis | one representative flavor |
| Device Smoke | nightly, manual | two instrumentation smoke tests on a Gradle Managed Device | one flavor, one ATD |
| Baseline Profiles | weekly, manual | generate variant-scoped profiles for all 17 flavors and update one automation PR | full-speed managed-device matrix |
| Physical Performance | manual | serial startup/frame benchmarks on one dedicated Android device | self-hosted `android-performance` runner |
| Attested Release Artifact | manual | signed AAB, checksum, upload, provenance | protected `production` environment |

Human pull requests and `main` pushes resolve the complete flavor matrix from
`.ci/apps.json` and `config/firebase-apps.json`. Every cataloged Android app is
linted and assembled in its own matrix job. The current catalog contains 17 app
flavors; the count is not hard-coded, so newly cataloged apps are included
automatically.

The flavor matrix does not define `max-parallel`. All 17 jobs become runnable
after the short policy, security, and catalog gates. GitHub starts as many jobs
as the account's currently available hosted-runner concurrency permits and
queues the rest. Dependabot never enters this matrix.

Each secret-free app build generates a marked, non-production
`google-services.json` from committed public catalogs and removes it after the
job. Production Firebase credentials are never exposed to pull-request jobs.

Dependabot version updates are consolidated into one monthly Android maintenance
pull request covering GitHub Actions and Gradle. Dependabot pull requests run the
workflow/security gates and a lightweight Gradle smoke check, but skip Android
quality, CodeQL, and the full app-build matrix to avoid runner congestion.

All active workflows must use immutable action SHAs approved in
`config/pinned-github-actions.json`, workflow-level `permissions: contents: read`,
bounded job timeouts, checkout with `persist-credentials: false`, and no direct
GitHub template expansion inside shell `run` blocks. Write permissions are
limited to the exact trusted job that needs them.

The manual attested release workflow uses the protected GitHub environment
`production`. GitHub stores only the Doppler bootstrap token; signing, Firebase,
and release values remain in Doppler and are materialized temporarily by
`scripts/doppler-run.sh`. The workflow produces one signed AAB, one SHA-256
checksum, and one provenance attestation. It does not publish to Google Play.

Every active workflow must pass:

1. `python3 scripts/ci/workflow_policy.py --repo .`
2. `python3 scripts/ci/pinned_github_actions.py --repo .`
3. `actionlint`
4. `zizmor` without high-confidence findings
5. the repository secret scan

Secret-backed Play, Cloudflare, AdMob, Firebase administration, and deployment
workflows require separate reviewed migrations with protected environments and
minimum job-level permissions.

## Required aggregate check

The `CI Required` job is the single branch-protection context for the complete CI workflow. It succeeds only when policy/security pass and either the full human/main Android path or the lightweight Dependabot path completes with the expected results.

## Android performance automation

`Baseline Profiles` resolves the live app catalog, launches every selected
flavor without a repository-level `max-parallel` cap, and produces one
`baseline-prof.txt` / `startup-prof.txt` pair per release source set. A complete
17-flavor run can update the single `automation/baseline-profiles` pull request
through a repository-scoped GitHub App. PR aggregation is enabled only when the
repository variable `PERFORMANCE_AUTOMATION_ENABLED` is exactly `true`; the App
client ID must be stored in `PERFORMANCE_AUTOMATION_CLIENT_ID` and its private
key in `PERFORMANCE_AUTOMATION_PRIVATE_KEY`. Without that explicit enable flag,
scheduled runs still generate and retain all profile artifacts but skip the
write-capable aggregation job. The matrix has read-only repository permissions
and receives no production secret.

Managed-device timing is diagnostic. Release performance comparisons remain the
responsibility of the serial physical-device workflow. See
`docs/PERFORMANCE_TESTING.md` for the device, measurement, and artifact contract.


## Jules Fleet Maintenance Workflows

The repository uses Jules Fleet (`@google/jules-fleet@0.0.1-experimental.35`) for autonomous repository maintenance:

| Workflow | Trigger | Purpose | Risk Control |
|---|---|---|---|
| `fleet-analyze.yml` | Scheduled (6h), manual (`workflow_dispatch`) | Analyzes goals in `.fleet/goals/` and creates issues | Trusted `main` only, fail-closed `JULES_FLEET_ENABLED == 'true'`, isolated `JULES_API_KEY` from Doppler, job-level write scopes |
| `fleet-dispatch.yml` | Scheduled (2h), manual (`workflow_dispatch`) | Dispatches Jules worker sessions for Fleet issues | Trusted `main` only, fail-closed `JULES_FLEET_ENABLED == 'true'`, isolated `JULES_API_KEY`, no production secrets |
| `fleet-classify.yml` | `pull_request` (`opened`, `synchronize`, `reopened`, `ready_for_review`) | Classifies PR changed files via `fleet_pr_risk.py` | Metadata-only, trusted base checkout, never executes PR code, no Doppler or Jules secrets |
| `fleet-merge.yml` | Scheduled (4h), manual (`workflow_dispatch`) | Merges `fleet-merge-ready` PRs when CI passes | Trusted `main` only, fail-closed `JULES_FLEET_ENABLED == 'true'`, guarded by `JULES_FLEET_AUTO_MERGE_ENABLED == 'true'` (otherwise dry-run) |

Control plane variables:
- `JULES_FLEET_ENABLED`: Master switch (`true` / `false`, default fail-closed). Must be explicitly set to `true` to enable execution.
- `JULES_FLEET_AUTO_MERGE_ENABLED`: Auto-merge switch (`true` / `false`, default fail-closed). Kept `false` as Mergify is authoritative.

## Autonomous Maintenance & Health Monitoring

The repository uses **Mergify** as the single authoritative merge engine and `.github/workflows/maintenance-health.yml` for automated controller monitoring:

| Workflow | Trigger | Purpose | Risk Control |
|---|---|---|---|
| `maintenance-health.yml` | Scheduled (daily 06:00 UTC), manual (`workflow_dispatch`) | Evaluates toolchain drift, policy expirations, pinned action integrity, and updates the consolidated Dashboard issue | Least privilege (`contents: read`, `issues: write`), deduplicated issue sync, fail-closed |
