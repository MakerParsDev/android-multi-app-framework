# GitHub Actions P0 Hardening Design

Date: 2026-07-21
Repository: `MakerParsDev/android-multi-app-framework`

## Goal

Establish a small, reliable, secret-free GitHub Actions baseline for pull requests and `main` while preventing the disabled legacy workflows from being activated before their security findings are fixed.

## Current state

- `.github/workflows` contains no runnable workflow YAML files.
- Seventeen legacy workflows are parked under `.github/workflows.disabled`.
- `actionlint` accepts the YAML syntax.
- `zizmor` reports high-confidence template-injection findings in local composite actions and legacy release/manual workflows.
- External actions use mutable major-version tags rather than immutable commit SHAs.
- Checkout credentials are generally persisted, job timeouts are absent, and several workflows rely on default token permissions.

## Approaches considered

### A. Re-enable all legacy workflows after bulk replacement

This restores broad coverage quickly but has the highest failure and security risk. It would activate large release, publishing, Cloudflare, Firebase and AdMob surfaces at once. Rejected.

### B. Activate only the existing quality-gate workflow

This is smaller than option A but still carries duplicated setup, mutable action tags, template-injection findings and excessive permissions. Rejected.

### C. Build a minimal secure baseline, then migrate legacy capabilities incrementally

Create two new workflows with no repository secrets: one pull-request CI workflow and one workflow-security workflow. Fix the shared composite-action injection points first. Keep release and deployment workflows disabled until they pass the same policy. Selected.

## Scope

### Included in P0

1. Fix high-confidence template injection in:
   - `.github/actions/resolve-flavors/action.yml`
   - `.github/actions/verify-env-contract/action.yml`
2. Add a repository policy script that rejects:
   - external actions not pinned to a 40-character commit SHA,
   - missing workflow/job permission baselines,
   - checkout steps without `persist-credentials: false`,
   - jobs without `timeout-minutes`,
   - direct `${{ ... }}` expansion inside shell `run` blocks for attacker-influenced values.
3. Add `.github/workflows/ci-pr.yml`:
   - triggers on pull requests and pushes to `main`,
   - uses no Doppler, Firebase, Cloudflare, signing or Play secrets,
   - runs repository policy checks, secret scan, env-contract structural checks, static analysis, unit tests and representative debug builds,
   - uses `ubuntu-24.04`, JDK 21 and bounded timeouts,
   - grants only `contents: read`.
4. Add `.github/workflows/security.yml`:
   - triggers on pull requests, pushes to `main`, weekly schedule and manual dispatch,
   - runs actionlint, zizmor, Gitleaks and dependency-policy checks,
   - uses no repository secrets,
   - grants only `contents: read` unless a specific GitHub security feature requires an isolated additional permission.
5. Pin every external action used by the two new workflows to an immutable full commit SHA with a human-readable version comment.
6. Add `.github/dependabot.yml` for GitHub Actions, Gradle and relevant npm workspaces so pinned SHAs and dependencies receive controlled update pull requests.
7. Add tests for the workflow policy script and the two composite-action fixes.

### Excluded from P0

- Enabling `release.yml`, `release-parallel.yml`, `manual-ops.yml` or deployment workflows.
- Play Console publishing.
- Doppler bootstrap inside GitHub Actions.
- CodeQL compiled Kotlin analysis; this is P1 after the baseline build is stable.
- Repository rulesets and environment approvals; these require repository-admin API access and are a separate operational step.
- Migrating all seventeen disabled workflows in one change.

## Architecture

### Workflow policy validator

A repository-owned Python validator will parse active workflow and local-action YAML files. It will produce file-and-line-oriented errors and return non-zero on policy violations. The validator is the canonical policy; actionlint and zizmor remain independent analyzers.

The validator will ignore `.github/workflows.disabled` for activation policy but can expose a separate audit mode for legacy migration work.

### Pull-request CI

The CI workflow is split into bounded jobs:

1. `workflow-policy`: fast YAML/security policy validation.
2. `repository-security`: tracked-sensitive-file validation, Gitleaks and env-contract structural validation.
3. `android-quality`: JDK/SDK setup, Gradle wrapper/setup, static analysis and unit tests.
4. `representative-builds`: a small matrix covering materially different product capabilities rather than all seventeen flavors on every PR.

Representative flavors:

- `kuran_kerim` — Quran/content/audio profile.
- `namazvakitleri` — prayer-time/location/alarm profile.
- `kible` — sensor/location profile.
- `zikirmatik` — counter/reminder/database profile.

A later nightly workflow will cover all seventeen flavors.

### Security workflow

The security workflow validates workflow syntax and supply-chain policy independently from Android compilation. Tool versions are exact and installation integrity is verified. Reports are uploaded only when they contain no credentials and use short retention.

### Secret boundary

Neither active P0 workflow receives repository secrets. Pull requests from forks therefore execute the same checks without privileged data. `pull_request_target` is prohibited.

## Data and execution flow

1. GitHub checks out the exact commit with persisted credentials disabled.
2. Workflow-policy checks run before expensive Android jobs.
3. Security checks reject leaked credentials or unsafe workflow constructs.
4. Gradle setup restores bounded caches and validates the wrapper.
5. Unit/static checks execute without `clean` to preserve cache usefulness.
6. Representative builds run with `fail-fast: false` and low parallelism.
7. Artifacts are uploaded only for failed diagnostic output or explicitly selected reports, with short retention.

## Error handling

- Every shell block uses `set -euo pipefail`.
- User/event values enter shell scripts through `env:` variables, never direct template interpolation inside `run:`.
- Every job has `timeout-minutes`.
- Concurrency cancels superseded PR runs.
- Cache failures do not bypass validation.
- Artifact upload uses `if-no-files-found: ignore` for optional diagnostics and `error` for required reports.
- Disabled release workflows remain unreachable until their own migration passes the validator and zizmor without high-confidence findings.

## Testing

### Local tests

- Unit tests for the workflow-policy validator using secure and intentionally insecure fixtures.
- Regression tests proving malicious-looking flavor/script inputs are treated as data, not shell code.
- `actionlint` over active workflows and local actions.
- `zizmor` over active workflows and local actions.
- Existing Gitleaks, env-contract and Gradle checks.

### GitHub verification

The feature branch will be pushed and the two workflows must complete successfully on the branch/PR before merge. Any failure is fixed on the feature branch; workflows are not bypassed.

## Rollout

1. Commit this design.
2. Write and approve the implementation plan.
3. Implement on `feature/github-actions-p0-hardening` with tests first.
4. Push the branch and observe real GitHub Actions runs.
5. Merge only after all active checks pass.
6. Start a separate P1 design for full-flavor nightly CI, CodeQL and protected release migration.

## Success criteria

- At least one active PR CI workflow and one active workflow-security workflow exist.
- Active workflows use no secrets and no `pull_request_target`.
- All external actions are pinned to immutable full SHAs.
- All jobs define timeouts and minimum permissions.
- All checkout steps disable persisted credentials.
- `actionlint` passes.
- `zizmor` reports no high-confidence findings in active workflows/local actions.
- Gitleaks and Android baseline checks pass locally and on GitHub.
- Legacy release/deployment workflows remain disabled.
