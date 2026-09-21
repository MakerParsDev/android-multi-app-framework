# Jules Autonomous Maintenance & Fleet Plan

Jules automation operates as an autonomous orchestration layer (`@google/jules-fleet@0.0.1-experimental.35`) over the repository's existing GitHub Actions, Android/Gradle quality gates, and Doppler secret controls.

## Pinned Supply Chain & Runtime

- **Package**: `@google/jules-fleet@0.0.1-experimental.35` (pinned and verified against published npm registry artifacts).
- **Node.js**: `22` (conforms to package engine requirement `>=20.0.0` and upstream template).
- **Python**: `3.12` (used for contract checks, deterministic resource management, and risk evaluation).

## Operating Architecture

1. **Jules Fleet Analyze** (`fleet-analyze.yml`):
   - Trigger: Scheduled every 6 hours (`cron: '0 */6 * * *'`) and manual `workflow_dispatch`. Manual runs may supply one `.fleet/goals/*.md` path for a single-goal canary; invalid/out-of-directory paths fail closed.
   - Security Boundary: Runs only on trusted `refs/heads/main` when both `vars.AUTONOMOUS_MAINTENANCE_ENABLED == 'true'` and `vars.JULES_FLEET_ENABLED == 'true'`.
   - Permissions: Workflow top-level `contents: read`; job-level `contents: read`, `issues: write`, `pull-requests: read`.
   - Lifecycle: Resolves `JULES_API_KEY` from Doppler via `scripts/ci/resolve_jules_api_key.sh`, deterministically ensures the "Jules Fleet Maintenance" milestone and required labels exist via `scripts/ci/fleet_milestone.py --ensure --ensure-labels`, then runs either `jules-fleet analyze --goal "$FLEET_GOAL" --milestone "$FLEET_MILESTONE"` for a manual canary or the scheduled all-goals form `jules-fleet analyze --goals-dir=".fleet/goals" --milestone "$FLEET_MILESTONE"`.
   - Concurrency: `cancel-in-progress: false` to avoid terminating in-flight analysis sessions.

2. **Jules Fleet Dispatch** (`fleet-dispatch.yml`):
   - Trigger: Scheduled every 2 hours (`cron: '30 */2 * * *'`) and manual `workflow_dispatch`. Manual runs default to `dry_run=true`; creating Jules worker sessions requires an explicit `dry_run=false` selection.
   - Security Boundary: Runs only on trusted `refs/heads/main` when both `vars.AUTONOMOUS_MAINTENANCE_ENABLED == 'true'` and `vars.JULES_FLEET_ENABLED == 'true'`.
   - Permissions: Workflow top-level `contents: read`; manual preview job uses only `contents: read`, `issues: read`, `pull-requests: read`; the real dispatch job uses `contents: read`, `issues: write`, `pull-requests: read`.
   - Lifecycle: Manual `dry_run=true` never invokes Jules Fleet and never receives Doppler/Jules credentials; it runs the read-only `scripts/ci/fleet_dispatch_preview.py` against GitHub metadata only. This replaces the pinned Fleet CLI's `--dry-run`, which was proven by live canary to create a real Jules session and is therefore not a valid safety boundary. Scheduled runs and explicit manual `dry_run=false` runs resolve `JULES_API_KEY`, resolve the milestone, and may create worker sessions with `jules-fleet dispatch --milestone "$FLEET_MILESTONE"`.
   - Concurrency: `cancel-in-progress: false` to avoid interrupting worker dispatch cycles.

3. **Jules PR Risk Classification** (`fleet-classify.yml`):
   - Trigger: Standard `pull_request` event (`types: [opened, synchronize, reopened, ready_for_review]`). Never uses `pull_request_target`.
   - Security Boundary: The metadata-only job runs only for PRs targeting `main` when both fail-closed maintenance/Fleet switches are `true`. It checks out the trusted base branch (`ref: ${{ github.base_ref }}`), never executes PR code, and verifies same-repository Jules provenance inside trusted Python before any changed-file classification or label mutation. Forks, Dependabot, and unrelated human PRs exit without Fleet/risk label writes.
   - Permissions: Workflow top-level `contents: read`; job-level `contents: read`, `issues: read`, `pull-requests: write`. The write scope is limited to PR label synchronization; issue metadata is read-only.
   - Secrets: NEVER receives `DOPPLER_TOKEN` or `JULES_API_KEY`.
   - Lifecycle: Executes `scripts/ci/classify_pr.py`, which paginates all changed files, verifies same-repository Jules session provenance through trusted GitHub metadata, requires at least one closing issue labeled `fleet`, and evaluates risk via `scripts/ci/fleet_pr_risk.py`. Current-runtime provenance requires the branch suffix `-<10+ digit session id>` and a PR-body `jules.google.com/task/<same id>` URL to correlate exactly; legacy `jules/*` + `s-*` session URLs remain supported. Only a verified low-risk Fleet PR receives `fleet-merge-ready`; a verified protected Fleet PR receives `fleet-review-required`; unrelated low-risk PRs cannot gain Fleet readiness. Missing or mismatched metadata, empty file lists, or label failures fail closed.
   - Concurrency: `cancel-in-progress: true` to prioritize the newest PR commit.

4. **Jules Fleet Merge Audit** (`fleet-merge.yml`):
   - Trigger: Scheduled every 4 hours (`cron: '0 */4 * * *'`) and manual `workflow_dispatch`.
   - Security Boundary: Runs only on trusted `refs/heads/main` when both `vars.AUTONOMOUS_MAINTENANCE_ENABLED == 'true'` and `vars.JULES_FLEET_ENABLED == 'true'`.
   - Permissions: Workflow top-level and job-level are read-only: `contents: read`, `pull-requests: read`.
   - Execution: Never invokes `jules-fleet merge`, never resolves Jules/Doppler credentials, and never writes PR state. `scripts/ci/fleet_merge_preview.py` reports verified low-risk Fleet PRs that are ready for Mergify evaluation. If `JULES_FLEET_AUTO_MERGE_ENABLED` is ever set to `true`, the workflow fails closed with an error because Mergify is the sole automated merge authority.
   - Concurrency: `cancel-in-progress: true` to keep the audit current without concurrent runs.

## Secret Isolation & Doppler Bootstrap

- `DOPPLER_TOKEN` is used strictly as a bootstrap credential inside `scripts/ci/resolve_jules_api_key.sh`.
- Doppler binary is pinned (`v3.76.1`) with SHA-256 validation in `scripts/ci/install_doppler_cli.sh`.
- Only `JULES_API_KEY` is extracted from Doppler; it is immediately masked with `::add-mask::` and exposed via `$GITHUB_ENV`.
- `DOPPLER_TOKEN` is never forwarded to the Jules CLI or Jules session environments.
- High-privilege production credentials (keystores, Play Console JSON, Firebase credentials, Cloudflare tokens, AdMob secrets) are strictly isolated from Fleet workflows.

## Control Plane & Fail-Closed Defaults

- `AUTONOMOUS_MAINTENANCE_ENABLED`: Global repository variable. Write-capable Fleet jobs require this to be exactly `"true"` in addition to the Fleet-specific switch.
- `JULES_FLEET_ENABLED`: Repository variable. Default is fail-closed: must be explicitly set to `"true"` to enable Fleet execution. If unset or any other value, all Fleet workflows exit immediately without consuming secrets or compute.
- `JULES_FLEET_AUTO_MERGE_ENABLED`: Repository variable. Must remain `false`. `fleet-merge.yml` is permanently read-only and fails closed if this variable becomes `true`; Mergify is the sole automated merge authority.

## Live Canary Verification

On 2026-09-21 UTC, the single-goal Analyze canary for `.fleet/goals/docs-config-drift.md` completed successfully against `main` with the pinned Fleet runtime. It successfully resolved `JULES_API_KEY` from Doppler, ensured milestone `Jules Fleet Maintenance` (#5), and started numeric Jules session `14900125275473977334`. Note that this evidence does not enable Jules auto-merge; Mergify remains the sole merge authority.

## Local Validation

Run local contract and policy tests before pushing workflow changes:

```bash
python scripts/ci/workflow_policy_test.py
python scripts/ci/pinned_github_actions_test.py
python scripts/ci/professional_ci_workflows_test.py
python scripts/ci/jules_fleet_workflows_test.py
python scripts/ci/fleet_milestone_test.py
python scripts/ci/classify_pr_test.py
python scripts/ci/resolve_jules_api_key_test.py
```
