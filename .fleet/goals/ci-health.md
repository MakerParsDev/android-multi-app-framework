# Goal: CI & Build Pipeline Health Maintenance

Continuously monitor, diagnose, and remediate build, workflow, and testing infrastructure issues across the repository.

## Focus Areas
1. **Failing & Intermittent CI Jobs**: Identify recurring Gradle daemon issues, cache misses, or toolchain configuration misalignments.
2. **Action Deprecation & Pinning**: Ensure all third-party GitHub Actions use full 40-character commit SHAs recorded in `config/pinned-github-actions.json`.
3. **Gradle & Android Toolchain Drift**: Maintain consistency across `gradle.properties`, `build.gradle.kts`, `settings.gradle.kts`, and `scripts/ci/android-toolchain.sh`.
4. **Lint Baseline & Quality Gate Drift**: Prune obsolete lint baseline entries using `scripts/ci/prune_lint_baseline.py` and enforce Detekt/ktlint rules without lowering standards.
5. **Workflow Security & Permissions**: Maintain least privilege workflow token permissions (`permissions: read-all` by default, job-level scopes).

## Verification Commands
- `python3 scripts/ci/workflow_policy_test.py`
- `python3 scripts/ci/professional_ci_workflows_test.py`
- `python3 scripts/ci/jules_fleet_workflows_test.py`
- `python3 scripts/ci/validate_android_toolchain_config.py`

## Rules
- NEVER fix CI by disabling tests, deleting quality gates, or adding broad `continue-on-error`.
