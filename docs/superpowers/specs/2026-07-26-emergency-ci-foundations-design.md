# Emergency CI Foundations Design

## Goal

Restore deterministic CI and release validation for the four confirmed blocking defects found on 26 July 2026: recursive side-project quality execution, CodeQL Java detection failure, missing release smoke-test endpoints, and three Android modules that bypass the centralized Java toolchain manifest.

## Scope

This change is intentionally limited to the smallest safe fixes and regression coverage. It does not consolidate release workflows, rotate secrets, change GitHub repository settings, or refactor unrelated build logic.

## Design

### Side-project quality execution

The central side-project runner will remain responsible for Node, Firebase Rules, endpoint-contract, audit-policy, and deployment-drift checks. It will no longer run the complete `scripts/ci/*_test.py` suite from inside itself because one of those tests invokes the runner and creates unbounded recursion. Python CI helper tests remain independently executable by the existing Gradle and workflow gates.

The contract test that currently launches the full runner merely to verify a release-policy guard will be replaced with a static contract assertion. The runner will also export a recursion sentinel and fail immediately on nested invocation, protecting future callers from reintroducing the same failure mode.

### Java version detection

`setup-android-sdk.sh` will parse all lines emitted by `java -version` and select the first line containing a Java version. This keeps detection correct when `JAVA_TOOL_OPTIONS` or another JVM launcher message is printed before the version line. A shell-harness regression test will reproduce the exact CodeQL environment behavior.

### Release backend smoke invocation

The release workflow will pass purchase and push endpoint URLs to `admin_backend_smoke.py` through environment variables and explicit CLI flags. The workflow contract test will require both flags and will continue to require that the smoke step appears before any Gradle execution.

### Centralized Java toolchain

`feature/dynamic_audio`, `feature/wear`, and `feature/widget` will resolve source and target compatibility through `requiredToolchainInt("toolchain.java.major")`, matching the other Android modules. The existing repository validator is the regression test for this change.

## Error handling

Nested side-project runner execution exits with a clear error before package installation or test execution. Missing release endpoint variables remain a hard failure because `admin_backend_smoke.py` validates required absolute HTTPS URLs. Java detection returns an empty value only when no parseable Java version exists, preserving the existing installation fallback.

## Verification

Each defect is verified through a red-green regression cycle. Final verification runs the focused Python tests, Android toolchain validator, workflow policy tests relevant to edited files, shell syntax checks, and `git diff --check`.
