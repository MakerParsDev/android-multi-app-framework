# Jules Autonomous Maintenance & Fleet Plan

Jules automation operates as an autonomous orchestration layer (`@google/jules-fleet@0.0.1-experimental.35`) over the repository's existing GitHub Actions, Android/Gradle quality gates, and Doppler secret controls.

## Pinned Supply Chain & Runtime

- **Package**: `@google/jules-fleet@0.0.1-experimental.35` (pinned and verified against published npm registry artifacts).
- **Node.js**: `22` (conforms to package engine requirement `>=20.0.0` and upstream template).
- **Python**: `3.12` (used for contract checks, deterministic resource management, and risk evaluation).

## Operating Architecture

1. **Jules Fleet Analyze** (`fleet-analyze.yml`):
   - Trigger: Scheduled every 6 hours (`cron: '0 */6 * * *'`) and manual `workflow_dispatch`.
   - Security Boundary: Runs only on trusted `refs/heads/main` when `vars.JULES_FLEET_ENABLED == 'true'`.
   - Permissions: Workflow top-level `contents: read`; job-level `contents: read`, `issues: write`, `pull-requests: read`.
   - Lifecycle: Resolves `JULES_API_KEY` from Doppler via `scripts/ci/resolve_jules_api_key.sh`, deterministically ensures the "Jules Fleet Maintenance" milestone and required labels exist via `scripts/ci/fleet_milestone.py --ensure --ensure-labels`, and runs `jules-fleet analyze --goals-dir=".fleet/goals" --milestone "$FLEET_MILESTONE"`.
   - Concurrency: `cancel-in-progress: false` to avoid terminating in-flight analysis sessions.

2. **Jules Fleet Dispatch** (`fleet-dispatch.yml`):
   - Trigger: Scheduled every 2 hours (`cron: '30 */2 * * *'`) and manual `workflow_dispatch`.
   - Security Boundary: Runs only on trusted `refs/heads/main` when `vars.JULES_FLEET_ENABLED == 'true'`.
   - Permissions: Workflow top-level `contents: read`; job-level `contents: read`, `issues: write`.
   - Lifecycle: Resolves `JULES_API_KEY` from Doppler, resolves the "Jules Fleet Maintenance" milestone number via `scripts/ci/fleet_milestone.py --resolve`, and dispatches worker sessions with `jules-fleet dispatch --milestone "$FLEET_MILESTONE"`.
   - Concurrency: `cancel-in-progress: false` to avoid interrupting worker dispatch cycles.

3. **Jules PR Risk Classification** (`fleet-classify.yml`):
   - Trigger: Standard `pull_request` event (`types: [opened, synchronize, reopened, ready_for_review]`). Never uses `pull_request_target`.
   - Security Boundary: Checks out trusted base branch (`ref: ${{ github.base_ref }}`). Evaluates metadata only; never executes PR code with write permissions.
   - Permissions: Workflow top-level `contents: read`; job-level `contents: read`, `issues: write`, `pull-requests: read`.
   - Secrets: NEVER receives `DOPPLER_TOKEN` or `JULES_API_KEY`.
   - Lifecycle: Executes `scripts/ci/classify_pr.py`, which paginates all changed files from GitHub API, evaluates risk via `scripts/ci/fleet_pr_risk.py`, applies `fleet-merge-ready` (low-risk) or `fleet-review-required` (protected), and removes the opposing label. Fails closed if changed file list is empty or label update fails.
   - Concurrency: `cancel-in-progress: true` to prioritize the newest PR commit.

4. **Jules Fleet Merge** (`fleet-merge.yml`):
   - Trigger: Scheduled every 4 hours (`cron: '0 */4 * * *'`) and manual `workflow_dispatch`.
   - Security Boundary: Runs only on trusted `refs/heads/main` when `vars.JULES_FLEET_ENABLED == 'true'`.
   - Permissions: Workflow top-level `contents: read`; job-level `contents: write`, `pull-requests: write`, `issues: write`.
   - Execution:
     - When `vars.JULES_FLEET_AUTO_MERGE_ENABLED == 'true'`: fetches `JULES_API_KEY` and runs `jules-fleet merge --mode="label" --redispatch`.
     - When disabled (default): runs `jules-fleet merge --mode="label" --dry-run` without fetching Doppler secrets or executing writes.
   - Concurrency: `cancel-in-progress: true` to prevent concurrent merge operations.

## Secret Isolation & Doppler Bootstrap

- `DOPPLER_TOKEN` is used strictly as a bootstrap credential inside `scripts/ci/resolve_jules_api_key.sh`.
- Doppler binary is pinned (`v3.76.1`) with SHA-256 validation in `scripts/ci/install_doppler_cli.sh`.
- Only `JULES_API_KEY` is extracted from Doppler; it is immediately masked with `::add-mask::` and exposed via `$GITHUB_ENV`.
- `DOPPLER_TOKEN` is never forwarded to the Jules CLI or Jules session environments.
- High-privilege production credentials (keystores, Play Console JSON, Firebase credentials, Cloudflare tokens, AdMob secrets) are strictly isolated from Fleet workflows.

## Control Plane & Fail-Closed Defaults

- `JULES_FLEET_ENABLED`: Repository variable. Default is fail-closed: must be explicitly set to `"true"` to enable Fleet execution. If unset or any other value, all Fleet workflows exit immediately without consuming secrets or compute.
- `JULES_FLEET_AUTO_MERGE_ENABLED`: Repository variable. Default is fail-closed: must be explicitly set to `"true"` to allow merging.

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
