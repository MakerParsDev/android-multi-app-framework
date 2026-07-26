# Emergency CI Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Fix four confirmed CI/release blockers while adding regression tests that reproduce each original failure.

**Architecture:** Keep each fix local to its current boundary: the side-project shell runner owns recursion protection, the Android SDK bootstrap owns Java parsing, the release workflow owns endpoint wiring, and Android modules consume the centralized toolchain manifest. Existing Python contract tests provide fast, deterministic coverage without requiring a full Android build.

**Tech Stack:** Bash, Python `unittest`, GitHub Actions YAML, Kotlin Gradle DSL, Android Gradle toolchain helpers.

## Global Constraints

- Work only on branch `fix/emergency-ci-foundations` in the isolated worktree.
- Do not change release behavior beyond wiring required smoke-test inputs.
- Do not add new dependencies.
- Every bug fix must have a failing regression test before implementation.
- Keep side-project Node/Firebase checks in the central runner; remove only recursive Python discovery.

---

### Task 1: Prevent recursive side-project quality execution

**Files:**
- Modify: `scripts/ci/side_project_quality_contract_test.py`
- Modify: `scripts/ci/run_side_project_quality.sh`

**Interfaces:**
- Consumes: `SIDE_PROJECT_QUALITY_ACTIVE` environment variable.
- Produces: immediate non-zero exit for nested runner invocation; central runner no longer discovers every `*_test.py` internally.

- [x] **Step 1: Write failing contract tests**

Add tests asserting the runner contains a recursion guard, excludes global Python test discovery, and the contract test does not execute the runner through `subprocess.run`.

- [x] **Step 2: Run the focused test and verify failure**

Run: `python3 -m unittest scripts.ci.side_project_quality_contract_test -v`
Expected: FAIL because the current runner still contains global discovery and no recursion sentinel.

- [x] **Step 3: Implement the minimal runner change**

Add an early `SIDE_PROJECT_QUALITY_ACTIVE` guard, export the sentinel, remove the `--skip-python` option and global `unittest discover` block, and update report metadata to state that Python helpers are independently gated.

- [x] **Step 4: Run the focused test and verify pass**

Run: `python3 -m unittest scripts.ci.side_project_quality_contract_test -v`
Expected: PASS.

- [x] **Step 5: Run a direct recursion smoke test**

Run: `SIDE_PROJECT_QUALITY_ACTIVE=1 bash scripts/ci/run_side_project_quality.sh`
Expected: non-zero exit with `Recursive side-project quality invocation detected` before dependency checks.

### Task 2: Make Java detection resilient to CodeQL launcher output

**Files:**
- Modify: `scripts/ci/android_toolchain_scripts_test.py`
- Modify: `scripts/ci/setup-android-sdk.sh`

**Interfaces:**
- Produces: `detect_java_major` returns the numeric major version when launcher messages precede the version line.

- [x] **Step 1: Write the failing shell-harness regression test**

Create a fake `java` executable that emits `Picked up JAVA_TOOL_OPTIONS: ...` followed by `openjdk version "21.0.11"`, source the setup script, and assert `detect_java_major` prints `21`.

- [x] **Step 2: Run the focused test and verify failure**

Run: `python3 -m unittest scripts.ci.android_toolchain_scripts_test.AndroidToolchainScriptsTest.test_java_detection_ignores_launcher_messages -v`
Expected: FAIL because the current parser reads only the first line.

- [x] **Step 3: Implement the minimal parser change**

Parse the complete `java -version` output with `sed`, then select the first numeric result.

- [x] **Step 4: Run all Android toolchain script tests**

Run: `python3 -m unittest scripts.ci.android_toolchain_scripts_test -v`
Expected: PASS.

### Task 3: Wire release smoke-test endpoints

**Files:**
- Modify: `scripts/ci/admin_backend_smoke_test.py`
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: GitHub `DOPPLER_TOKEN`; Doppler `PURCHASE_VERIFICATION_URL` and `PUSH_REGISTRATION_URL`; `github.sha`.
- Produces: Doppler-wrapped CLI invocation with `--purchase-url`, `--push-url`, and `--expected-git-sha` before Gradle execution.

- [x] **Step 1: Strengthen the workflow contract test**

Assert the release workflow contains both CLI flags and both endpoint environment names.

- [x] **Step 2: Run the focused test and verify failure**

Run: `python3 -m unittest scripts.ci.admin_backend_smoke_test.ReleaseIntegrationContractTest -v`
Expected: FAIL because the flags are absent.

- [x] **Step 3: Implement explicit endpoint wiring**

Install the pinned Doppler CLI, validate the bootstrap token, and run the smoke client inside a Doppler-injected inner shell with quoted endpoint and expected-SHA arguments.

- [x] **Step 4: Run backend smoke tests**

Run: `python3 -m unittest scripts.ci.admin_backend_smoke_test -v`
Expected: PASS.

### Task 4: Remove Android toolchain hardcodes

**Files:**
- Modify: `feature/dynamic_audio/build.gradle.kts`
- Modify: `feature/wear/build.gradle.kts`
- Modify: `feature/widget/build.gradle.kts`

**Interfaces:**
- Consumes: `requiredToolchainInt("toolchain.java.major")`.
- Produces: source and target compatibility resolved from the repository manifest.

- [x] **Step 1: Run validator and verify current failure**

Run: `python3 scripts/ci/validate_android_toolchain_config.py`
Expected: FAIL with six hardcoded JavaVersion findings.

- [x] **Step 2: Replace hardcoded compatibility values**

Use `JavaVersion.toVersion(requiredToolchainInt("toolchain.java.major"))` for both source and target compatibility in all three modules.

- [x] **Step 3: Run validator tests and repository validator**

Run: `python3 -m unittest scripts.ci.validate_android_toolchain_config_test -v && python3 scripts/ci/validate_android_toolchain_config.py`
Expected: PASS.

### Task 5: Final focused verification

**Files:**
- Verify all modified files.

**Interfaces:**
- Produces: evidence that the emergency package is internally consistent.

- [x] **Step 1: Run focused Python regression suite**

Run: `python3 -m unittest scripts.ci.side_project_quality_contract_test scripts.ci.android_toolchain_scripts_test scripts.ci.admin_backend_smoke_test scripts.ci.validate_android_toolchain_config_test -v`
Expected: PASS.

- [x] **Step 2: Run policy and syntax checks**

Run: `bash -n scripts/ci/run_side_project_quality.sh scripts/ci/setup-android-sdk.sh && python3 scripts/ci/validate_android_toolchain_config.py && python3 scripts/ci/professional_ci_workflows_test.py && git diff --check`
Expected: PASS.

- [x] **Step 3: Review diff and commit**

Run: `git status --short && git diff --stat && git diff`
Expected: only the design, plan, tests, and four focused fixes are present.


## Operational Follow-up Found During Verification

On 26 July 2026, the live Doppler-wrapped smoke reached the production backend and correctly failed the same-commit check: the Worker reported `74ff0a1077d07277f6a7a905665859ef95a351af`, while `main` was `515a4ac779c9a6c84e76300caf79d31f33367084`. Cloudflare deployment metadata shows the active deployment was created on 20 July 2026, before this GitHub repository was created on 21 July 2026, and the reported Worker SHA is not present in local or GitHub commit history. After this branch is merged, the verified admin API must be redeployed from the merge commit before Release can pass the intended same-commit smoke gate.
