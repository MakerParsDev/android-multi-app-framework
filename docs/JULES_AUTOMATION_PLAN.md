# Jules Autonomous Maintenance & Fleet Plan

Jules automation operates as an autonomous orchestration layer (`@google/jules-fleet@0.0.1-experimental.35`) over the repository's existing GitHub Actions, Android/Gradle quality gates, and Doppler secret controls.

## Operating Architecture

1. **Jules Fleet Analyze** (`fleet-analyze.yml`):
   - Runs every 6 hours on trusted `main`.
   - Reads `.fleet/config.yml` and `.fleet/goals/*.md`.
   - Creates deduplicated GitHub issues scoped under the "Jules Fleet Maintenance" milestone.
2. **Jules Fleet Dispatch** (`fleet-dispatch.yml`):
   - Runs every 2 hours on trusted `main`.
   - Polls undispatched Fleet issues and fires Jules worker sessions via `JULES_API_KEY`.
3. **Jules PR Risk Classification** (`fleet-classify.yml`):
   - Trusted metadata-only workflow triggered on PR events.
   - Runs `scripts/ci/fleet_pr_risk.py` to evaluate changed files.
   - Low-risk PRs receive label `fleet-merge-ready`.
   - High-risk / protected PRs (touching `.github/`, `.fleet/`, `scripts/`, `config/`, build files) receive label `fleet-review-required`.
4. **Jules Fleet Merge** (`fleet-merge.yml`):
   - Runs every 4 hours.
   - Merges `fleet-merge-ready` PRs if all CI quality gates pass.
   - Enforces automatic conflict recovery (`--redispatch`).

## Doppler Secret Isolation

- Jules workers NEVER receive `DOPPLER_TOKEN` or broad production secrets (Play Console JSON, signing keystores, Firebase private credentials, Cloudflare tokens, AdMob keys).
- `scripts/ci/resolve_jules_api_key.sh` fetches ONLY `JULES_API_KEY` from Doppler, applies GitHub secret masking (`::add-mask::`), and passes ONLY `JULES_API_KEY` to the Fleet execution process.

## Operational Control Plane / Kill Switch

- `JULES_FLEET_ENABLED`: `true` | `false` (Master kill switch for all Fleet workflows).
- `JULES_FLEET_AUTO_MERGE_ENABLED`: `true` | `false` (Auto-merge switch, default `false` during staged rollout).
