# Operating Contract for Autonomous Agents

This document defines the permanent operating invariants, policy boundaries, and environment rules for autonomous agents working within this repository (`MakerParsDev/android-multi-app-framework`).

## Absolute Invariants

1. **Sole Merge Authority**:
   - `Mergify` is the sole authoritative merge engine.
   - **NEVER** merge directly to `main`, force-push `main`, bypass Mergify, or enable GitHub native auto-merge or Jules/Fleet auto-merge.

2. **Protections & Gates**:
   - Do not weaken required CI checks, Security gates, CodeQL, SonarCloud, Dependency Review, Secret Scan, Workflow Audit, side-project quality gates, supply-chain policies, or ruleset branch protections.

3. **Firebase & Secrets Boundary**:
   - Pull-request jobs must never receive production Firebase credentials or live API keys.
   - PR CI uses deterministic, non-production Firebase placeholders (`scripts/ci/generate_ci_google_services.py`, `scripts/ci/materialize_firebase_configs.py`).
   - Never write secrets, private tokens, service-account JSONs, or private certificates to repository files.

4. **Environment & Toolchains**:
   - Required runtimes: **Java 21**, **Node 22**, **Python 3.12**, **Android SDK 37**.
   - Use repository-owned setup and verification scripts (`scripts/ci/setup-android-sdk.sh`, `scripts/ci/verify-android-toolchain.sh`).

5. **Protected Dependencies & Canaries**:
   - Toolchains (Kotlin, AGP, KSP), core crypto/auth, and sensitive packages require canonical policy validation and candidate canary verification before updating.
   - Do not "solve" dependency or network resolution failures by arbitrarily downgrading unrelated dependencies.

6. **Repository Cleanliness**:
   - Never commit generated `google-services.json` files, test reports, scratch files, downloaded binaries, or temporary artifacts.
   - Preserve line endings and git permissions.

7. **Autonomous Maintenance Architecture**:
   - GitHub Issue #245 defines the unified autonomous maintenance architecture control plane.
   - Machine-owned policies, canary testing, security tracking (#183), and dispatch session safety are governed via repository-owned scripts and `config/autonomy-policy.yaml`.
