# Goal: Dependency Health & Version Management

Maintain dependencies, Gradle plugins, and toolchains in alignment with repository security and stability guidelines.

## Focus Areas
1. **Version Catalog & Plugins**: Audit `gradle/libs.versions.toml` and build plugins for stable non-breaking upgrades.
2. **Android & Kotlin Matrix Compatibility**: Verify AGP, Kotlin, KSP, Detekt, and Kover version alignment before bumping core dependencies.
3. **Side-Project Dependencies**: Maintain Node/npm package security under `side-projects/` and `scripts/package.json`.
4. **Dependabot Coexistence**: Do not create duplicate PRs or issues for dependencies that already have open Dependabot PRs.

## Verification Commands
- `python3 scripts/ci/audit_dependency_catalog.py`
- `python3 scripts/ci/validate_supply_chain_policy.py`

## Rules
- Do NOT upgrade to experimental or alpha pre-releases unless required for critical bug fixes.
- Respect pinned dependency policy definitions in `config/dependency-policy.json`.
