# CI Professional Actions and Full-Speed Matrix Design

**Date:** 2026-07-22  
**Status:** Approved for implementation  
**Repository:** `MakerParsDev/android-multi-app-framework`

## 1. Objective

Extend the current secure GitHub Actions baseline with the standard professional layers expected from a modern Android repository while reducing elapsed CI time.

The implementation will:

- use all GitHub-hosted runner capacity available to the account instead of enforcing a repository-local three-job ceiling;
- keep all 17 cataloged Android application flavors covered;
- add dependency review and Gradle dependency submission;
- enforce and publish Kover coverage reports;
- add Java/Kotlin CodeQL scanning;
- run Android instrumented smoke tests through Gradle build-managed devices;
- upload useful test, lint, coverage, and release artifacts;
- create provenance attestations for manually produced signed release AAB files;
- preserve the lightweight Dependabot path so automated dependency maintenance cannot trigger the expensive 17-flavor matrix.

## 2. Constraints and invariants

1. The repository is public and currently uses standard GitHub-hosted Linux runners.
2. GitHub Free currently allows up to 20 concurrent standard GitHub-hosted jobs across the account. The workflow must not assume all 20 are always free; GitHub remains responsible for queueing jobs when account capacity is occupied.
3. The Android app catalog contains 17 flavors and remains the single source of truth. No workflow will maintain a second handwritten flavor list.
4. Pull requests created by `dependabot[bot]` remain on a lightweight smoke path.
5. Pull requests from forks or untrusted actors receive no production secrets.
6. Every external action is pinned to a full immutable commit SHA with a human-readable release comment.
7. Default workflow permissions remain `contents: read`. Write permissions are granted only to the exact job that requires them.
8. Existing repository-native scripts remain preferred over unnecessary third-party actions.
9. `gradle/actions/setup-gradle` remains the only Gradle cache manager. `actions/cache` will not be added.
10. CI-generated Firebase placeholder files are temporary, flavor-scoped, and always removed.
11. Release signing material remains in Doppler and is materialized only through `scripts/doppler-run.sh`.

## 3. Full-speed execution model

### 3.1 Remove the fixed matrix ceiling

The current Android flavor matrix contains:

```yaml
max-parallel: 3
```

This field will be removed. All 17 jobs will become runnable as soon as their short prerequisite gates pass. GitHub will start as many jobs as the account's remaining concurrency allows and queue the rest automatically.

No hardcoded replacement such as `max-parallel: 17` will be used. Omitting the field is more robust when the account plan or other repository activity changes.

### 3.2 Parallelize independent work

The 17-flavor build matrix will no longer wait for the long `Android Quality` job. The dependency graph will be:

```text
Workflow Policy ─┐
Repository Security ─┼─> Android Quality
Resolve App Matrix ─┘

Workflow Policy ─┐
Repository Security ─┼─> 17 flavor build jobs
Resolve App Matrix ─┘
```

This allows the quality suite and application builds to use runners concurrently. Quick security and policy checks still fail before expensive builds begin.

### 3.3 Cache-write discipline

Running 17 jobs simultaneously must not cause 17 competing Gradle cache uploads.

- `Android Quality` may write the Gradle cache on trusted `main` pushes.
- Every matrix app-build job uses `cache-read-only: true` on both PR and `main` events.
- CodeQL, managed-device tests, and dependency submission use read-only caching unless a single canonical cache writer is explicitly selected.
- Pull request jobs remain read-only.

This design preserves parallel read performance while preventing cache upload contention and unnecessary cache churn.

## 4. Workflow architecture

### 4.1 Existing `ci-pr.yml`

The active CI workflow will be enhanced rather than replaced.

Changes:

- remove `max-parallel: 3`;
- allow `Android Quality` and the 17-flavor matrix to run concurrently after short gates;
- run the existing Kover quality tasks in `Android Quality`;
- validate critical coverage using the existing repository script/task;
- upload Kover XML/HTML, unit-test reports, and lint reports with `actions/upload-artifact`;
- keep artifacts for 14 days;
- use `if: always()` for diagnostic report upload;
- keep app-build artifacts disabled on ordinary PRs to avoid artifact-storage exhaustion;
- retain the lightweight Dependabot smoke job and skip the expensive quality/matrix path for Dependabot PRs.

### 4.2 Dependency review in `security.yml`

`actions/dependency-review-action` will run only on pull requests.

Policy:

- fail on newly introduced `high` or `critical` vulnerabilities;
- display patched-version information;
- surface OpenSSF package score information when available;
- avoid PR-comment write permissions unless a later requirement explicitly needs comments;
- preserve the existing actionlint, zizmor, Gitleaks, policy, and supply-chain checks.

Dependency Review complements, rather than replaces, the repository's existing custom dependency catalog and supply-chain validators.

### 4.3 New `dependency-submission.yml`

A dedicated Gradle dependency submission workflow will run on:

- pushes to `main` that affect Gradle build or dependency metadata;
- manual dispatch.

The job will:

- use `gradle/actions/dependency-submission`;
- use the Gradle wrapper and JDK 21;
- submit the resolved direct and transitive dependency graph;
- request `contents: write` only in this job;
- use `dependency-graph: generate-and-submit` to avoid retaining an unnecessary extra dependency-graph artifact;
- use an explicit timeout and concurrency group.

This workflow will not run on untrusted pull requests because submission requires repository write permission.

### 4.4 New `codeql.yml`

CodeQL will analyze `java-kotlin` using manual build mode because Kotlin requires an actual build for accurate extraction.

Triggers:

- human pull requests;
- pushes to `main`;
- a weekly scheduled scan;
- manual dispatch.

The workflow will:

- use `github/codeql-action` v4 pinned by full SHA;
- grant `security-events: write` only to the analysis job;
- generate a temporary Firebase placeholder for one representative flavor;
- build one representative flavor rather than all 17 because the shared modules contain most executable code;
- remove the placeholder in an `always()` cleanup step;
- use a read-only Gradle cache;
- skip Dependabot pull requests because dependency-specific gates already cover those changes.

The initial representative flavor is `kuran_kerim`, which exercises shared application, content, Firebase, UI, and feature wiring.

### 4.5 New `device-smoke.yml`

Instrumented tests will use Android Gradle Plugin build-managed devices rather than a third-party emulator action.

Configuration:

- one Automated Test Device profile on a stable modern API level;
- one representative flavor (`kuran_kerim`);
- SwiftShader GPU mode required for GitHub-hosted runners;
- one shard initially because the current `androidTest` suite is small;
- temporary Firebase placeholder generation and guaranteed cleanup;
- upload Android test HTML/XML/proto outputs for 14 days.

Triggers:

- nightly schedule at a time separate from the weekly security scan;
- manual dispatch;
- no automatic run for every pull request in the initial rollout.

This keeps device reliability coverage without consuming an emulator runner on every source change. The trigger can later be expanded to selected UI-sensitive PR paths after timing data is collected.

### 4.6 New `release-attested.yml`

A manual, protected release-artifact workflow will build and attest a signed AAB without publishing it to Google Play.

Inputs:

- one flavor name, validated against the repository catalog;
- optional artifact retention override within a restricted allowed range.

Security model:

- `workflow_dispatch` only;
- `environment: production`;
- production signing values obtained through a bootstrap `DOPPLER_TOKEN` secret and `scripts/doppler-run.sh`;
- no signing secrets available to PR workflows;
- exact job permissions: `contents: read`, `id-token: write`, `attestations: write`, and `artifact-metadata: write`;
- signed AAB uploaded with `actions/upload-artifact`;
- AAB provenance generated with `actions/attest`;
- SHA-256 checksum generated and uploaded beside the AAB;
- artifact retention default of 14 days to remain compatible with the repository's limited artifact storage.

This phase deliberately does not publish to Play Console. Build, verification, artifact upload, and attestation are established first; Play publishing remains a later protected delivery step consuming the exact same attested AAB.

The workflow will be active but manual. It will include a clear preflight failure if the production environment or Doppler bootstrap secret has not been configured.

## 5. Action inventory

The implementation will introduce only official GitHub or Gradle actions:

- `actions/dependency-review-action`;
- `gradle/actions/dependency-submission`;
- `actions/upload-artifact`;
- `github/codeql-action/init`;
- `github/codeql-action/analyze`;
- `actions/attest`.

Existing official actions remain:

- `actions/checkout`;
- `actions/setup-java`;
- `gradle/actions/setup-gradle`.

All references will be resolved to the current verified release commit at implementation time, recorded in the repository's workflow-security tool/config policy where appropriate, and validated by the existing workflow policy tests.

## 6. Permissions

Workflow-level permissions stay read-only. Exceptions are job-scoped:

| Workflow/job | Additional permissions |
|---|---|
| Dependency submission | `contents: write` |
| CodeQL analyze | `security-events: write` |
| Release attestation | `id-token: write`, `attestations: write`, `artifact-metadata: write` |

No new workflow receives `pull-requests: write`, `checks: write`, `packages: write`, or broad `write-all` permissions.

## 7. Artifact and storage policy

GitHub Free artifact storage is limited, so reports must be useful and short-lived.

- Kover, test, lint, and managed-device reports: 14 days.
- Signed manual release AAB and checksum: 14 days by default.
- No APK/AAB artifacts from every PR flavor job.
- Missing report files produce warnings for diagnostic uploads but do not hide the original test failure.
- Release artifact absence is fatal.
- Artifact names include workflow purpose, flavor, and commit short SHA where supported.

## 8. Failure and cleanup behavior

- Matrix uses `fail-fast: false`, so one flavor failure does not hide failures in other flavors.
- Full-speed matrix uses GitHub account queueing rather than local throttling.
- Every generated Firebase file is removed with `if: always()`.
- Managed devices are lifecycle-managed by Gradle.
- Doppler materialized signing files are removed by `scripts/doppler-run.sh` after success, failure, or signal.
- Artifact uploads use `if: always()` only for diagnostics. Release artifact and attestation steps run only after a successful signed build.
- CodeQL and dependency submission failures are blocking, not advisory.

## 9. Test strategy

### Static validation

- `actionlint` for every active workflow;
- `zizmor --offline --strict-collection --min-confidence high`;
- existing workflow policy validator;
- new policy tests for:
  - no `max-parallel` in the full flavor matrix;
  - Dependabot exclusion from expensive jobs;
  - matrix app jobs using read-only Gradle cache;
  - full-SHA pinning for every new action;
  - job-scoped write permissions;
  - required timeouts and concurrency groups;
  - release workflow restricted to manual dispatch and production environment.

### Repository tests

- existing flavor matrix resolver tests;
- Kover task dry-run and report-path verification;
- managed-device task discovery and Gradle dry-run;
- release task graph dry-run for a representative flavor;
- Firebase placeholder tests;
- Doppler wrapper lifecycle tests;
- Gitleaks history and directory scans.

### Live GitHub validation

Before merge:

1. Open a feature PR.
2. Confirm that the 17 matrix jobs are all runnable without a repository-local parallel limit.
3. Confirm GitHub starts jobs up to available account capacity and queues excess jobs.
4. Confirm Android Quality runs concurrently with app builds.
5. Confirm Kover and diagnostic artifacts exist.
6. Confirm Dependency Review and CodeQL succeed.
7. Manually dispatch the managed-device workflow and verify reports.
8. Validate dependency submission on the feature branch only through dry-run/static policy; perform the real submission after merge to `main`.
9. Do not run the production release workflow until the production environment and Doppler bootstrap secret are confirmed.

After merge:

- verify `main` CI, Security, CodeQL, and Dependency Submission are green;
- verify 17/17 flavor builds;
- verify no open Dependabot flood and no unexpected workflow queue remains;
- verify repository and local canonical workspace SHA equality.

## 10. Rollout order

1. Full-speed matrix and cache-write discipline.
2. Kover generation and report upload.
3. Dependency Review.
4. Dependency Submission.
5. CodeQL.
6. Managed-device smoke test.
7. Manual signed release artifact and attestation.
8. Live PR validation and merge.

## 11. Non-goals

This implementation will not:

- activate the legacy disabled release/publish workflows as-is;
- publish automatically to Google Play;
- run emulator tests for all 17 flavors;
- add Codecov, SonarQube, or another overlapping hosted coverage service;
- add `actions/cache`;
- add a third-party emulator action;
- expose Doppler or signing secrets to pull requests;
- remove the lightweight Dependabot policy.

## 12. Success criteria

The work is complete when:

- all 17 flavors build successfully with no explicit matrix parallel ceiling;
- GitHub automatically consumes available runner capacity and safely queues excess jobs;
- Dependabot PRs remain lightweight;
- Kover quality gates and downloadable reports are active;
- Dependency Review blocks new high/critical vulnerable dependencies;
- Gradle dependency snapshots appear in GitHub's dependency graph;
- CodeQL publishes a successful Java/Kotlin analysis;
- a managed-device smoke run succeeds and publishes reports;
- a manually built signed AAB can be uploaded and cryptographically attested once production bootstrap is configured;
- actionlint, zizmor, workflow policy, secret scans, and all repository regression tests pass.
