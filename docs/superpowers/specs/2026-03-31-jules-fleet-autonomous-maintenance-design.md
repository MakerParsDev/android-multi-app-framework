# Jules Fleet Autonomous Maintenance System Architecture & Design

**Author:** Jules (Senior Staff/Principal DevOps + Android Platform + CI/CD + Security Automation Engineer)
**Date:** 2026-03-31
**Status:** Design Proposal & Implementation Plan
**Target Repository:** `MakerParsDev/android-multi-app-framework`

---

## 1. Executive Summary & Goals

This document specifies the design for a production-quality autonomous repository maintenance system centered around `@google/jules-fleet@0.0.1-experimental.35`.

The system operates as an orchestration layer *above* the repository's existing GitHub Actions, Android/Gradle quality gates, Doppler secret management, and release protections.

### Key Goals
1. **Autonomous Maintenance Lifecycle**: Analyze repository health -> Create deduplicated GitHub issues -> Dispatch Jules worker sessions -> Generate focused PRs -> Classify risk -> Auto-merge low-risk PRs / Require review for high-risk PRs.
2. **Strict Doppler Secret Isolation**: Jules workers and Fleet orchestration sessions MUST NOT receive broad production credentials (`DOPPLER_TOKEN`, Play keys, keystores, Cloudflare tokens, Firebase admin keys, AdMob credentials). Fleet workflow sessions fetch ONLY `JULES_API_KEY` from Doppler, immediately mask it with `::add-mask::`, and inject it strictly to the Fleet process.
3. **Deterministic PR Risk Classifier**: A trusted Python script (`scripts/ci/fleet_pr_risk.py`) runs in a metadata-only GitHub Actions workflow to classify PR risk. Worker agents cannot bypass or alter classification.
4. **Resilience & Kill Switch**: Control execution and auto-merges via repository variables `JULES_FLEET_ENABLED` and `JULES_FLEET_AUTO_MERGE_ENABLED`.
5. **No Supply-Chain or CI Degradation**: All third-party GitHub Actions are pinned to full commit SHAs in `config/pinned-github-actions.json`. Node dependencies are pinned to specific versions (`@google/jules-fleet@0.0.1-experimental.35`).

---

## 2. System Architecture

```
                    +-----------------------------+
                    |  GitHub Scheduled Triggers  |
                    +--------------+--------------+
                                   |
                                   v
                    +-----------------------------+
                    |   .github/workflows/        |
                    |   fleet-analyze.yml         |
                    +--------------+--------------+
                                   |
                    Resolves JULES_API_KEY via Doppler
                    (Masked, DOPPLER_TOKEN dropped)
                                   |
                                   v
                    +-----------------------------+
                    |    jules-fleet analyze      |
                    |    Reads .fleet/goals/*.md  |
                    +--------------+--------------+
                                   |
                         Creates Fleet Issues
                                   |
                                   v
                    +-----------------------------+
                    |   .github/workflows/        |
                    |   fleet-dispatch.yml        |
                    +--------------+--------------+
                                   |
                     Dispatches Jules Workers
                     (Worker session has NO Doppler
                      production secrets)
                                   |
                                   v
                    +-----------------------------+
                    |    Jules Worker Sessions    |
                    |    Creates Focused PRs      |
                    +--------------+--------------+
                                   |
                                   v
                    +-----------------------------+
                    |  Existing Repo CI Gate      |
                    |  (Lint, Detekt, Tests, etc) |
                    +--------------+--------------+
                                   |
                                   v
                    +-----------------------------+
                    |   .github/workflows/        |
                    |   fleet-classify.yml        |
                    |   (Trusted PR Metadata)     |
                    +--------------+--------------+
                                   |
                   +---------------+---------------+
                   |                               |
              [Low-Risk]                       [High-Risk]
          app/core/feature code              .github/, .fleet/,
            unit tests, docs                  scripts/, config/
                   |                               |
                   v                               v
         `fleet-merge-ready`              `fleet-review-required`
                   |                               |
                   v                               v
        +---------------------+          +-------------------+
        | fleet-merge.yml     |          | Human PR Review   |
        | jules-fleet merge   |          +-------------------+
        +---------------------+
```

---

## 3. Doppler Secret Trust Boundary & Isolation Flow

- **GitHub Secrets**: `DOPPLER_TOKEN` (Bootstrap secret with access ONLY to project `android-multi-app-framework`, config `prod`).
- **Doppler Secret Retrieval**: `scripts/ci/resolve_jules_api_key.sh` calls `doppler secrets get JULES_API_KEY --plain` (or `doppler run -- command`).
- **Secret Masking & Stripping**:
  1. `echo "::add-mask::${JULES_API_KEY}"` ensures raw key never appears in workflow output.
  2. `JULES_API_KEY` is exported ONLY to the Fleet execution step.
  3. `DOPPLER_TOKEN` is explicitly UNSET (`unset DOPPLER_TOKEN`) before calling `jules-fleet`.
  4. Jules worker sessions are dispatched through Jules API using `JULES_API_KEY`. Worker sessions execute in Google's Jules environment and NEVER receive `DOPPLER_TOKEN` or production Doppler secrets.

---

## 4. Operational Control Plane & Kill Switch

The system supports two independent repository variables (configured under GitHub Repository Variables):

1. `JULES_FLEET_ENABLED`: `true` | `false` (Default: `true`)
   - Controls whether `fleet-analyze`, `fleet-dispatch`, `fleet-classify`, and `fleet-merge` execute.
   - If `false`, all Fleet workflows exit immediately with a graceful `skipped` / `success` notice.
2. `JULES_FLEET_AUTO_MERGE_ENABLED`: `true` | `false` (Default: `false` during initial rollout)
   - Controls whether `fleet-merge.yml` will perform actual merges on `fleet-merge-ready` PRs.
   - If `false`, `fleet-merge` runs in `--dry-run` mode or exits without merging.

---

## 5. Fleet Configuration & Goal Specifications

Files located in `.fleet/`:

- `.fleet/config.yml`: Global repository preamble providing context on Android flavors, strict CI quality gates, Doppler secret boundaries, and SHA pinning rules.
- `.fleet/goals/ci-health.md`: CI workflows, build scripts, flaky test remediations, and toolchain checks.
- `.fleet/goals/code-quality.md`: Kotlin code cleanliness, detekt/ktlint baseline cleanup, coroutine lifecycle leaks, dead code.
- `.fleet/goals/dependency-health.md`: Gradle version catalog updates, plugin updates, without breaking AGP/Kotlin compatibility matrix.
- `.fleet/goals/security-hygiene.md`: Workflow permissions, SHA pinning, path traversal, shell injection, gitleaks rules.
- `.fleet/goals/docs-config-drift.md`: Documentation alignment with active CI scripts and Doppler configuration.
- `.fleet/goals/issue-triage.md`: Analyzing open issues, identifying target files and test verification scenarios.

---

## 6. PR Risk Classification Policy (`scripts/ci/fleet_pr_risk.py`)

Every Fleet PR is evaluated by `fleet-classify.yml` using `scripts/ci/fleet_pr_risk.py`.

### Classification Rules:

- **High-Risk / Protected Paths**:
  - `.github/**`
  - `.fleet/**`
  - `.agents/**`, `.claude/**`, `.codex/**`
  - `config/**`
  - `scripts/**`
  - Root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/**`, `buildSrc/**`
  - Secret ownership, supply chain, or security gate policy files.

- **Low-Risk Paths**:
  - `app/src/**`
  - `core/**/src/**`
  - `feature/**/src/**`
  - Unit/integration test additions/updates under `*/src/test/**` or `*/src/androidTest/**`
  - Non-sensitive markdown docs under `docs/` or `*.md` (excluding workflow/security docs).

- **Default / Fail-Closed**:
  - Any PR touching a mix of low-risk and protected paths, or any unclassified path, is marked `PROTECTED` (`fleet-review-required`).

---

## 7. Verification & Testing Strategy

1. **Python Contract Tests (`scripts/ci/jules_fleet_workflows_test.py`)**:
   - Asserts all Fleet workflow files exist and parse as valid YAML.
   - Asserts all third-party GitHub Actions are pinned to 40-character commit SHAs.
   - Asserts `pull_request_target` is NOT used, or if used, NEVER checks out PR code.
   - Asserts `fleet_pr_risk.py` correctly classifies test file diffs.
   - Asserts `resolve_jules_api_key.sh` applies GitHub secret masking.

2. **Integration with Existing Workflow Policy Tests**:
   - `scripts/ci/workflow_policy_test.py`
   - `scripts/ci/professional_ci_workflows_test.py`
   - `scripts/ci/validate_security_pipeline.py`
