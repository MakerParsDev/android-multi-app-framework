# CI Professional Actions and Full-Speed Matrix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add professional Android CI security, dependency, coverage, CodeQL, managed-device, artifact, and attested-release layers while allowing all 17 application flavor jobs to consume GitHub's available runner capacity.

**Architecture:** Keep the existing `CI` and `Security` workflows as the short-gate foundation, remove the repository-local flavor concurrency ceiling, and add narrowly scoped workflows for dependency submission, CodeQL, scheduled device smoke tests, and manual release provenance. Repository-native Python/Bash validators remain the source of truth; every external action is pinned to an approved immutable SHA and every write permission is job-scoped.

**Tech Stack:** GitHub Actions, Android Gradle Plugin 9.2.1, Gradle Wrapper 9.5.1, JDK 21, Kotlin/Android, Kover 0.9.8, Python 3, Bash, Doppler CLI 3.76.1, GitHub CodeQL v4, Gradle Managed Devices.

## Global Constraints

- The app catalog contains 17 flavors and `.ci/apps.json` plus `config/firebase-apps.json` remain the only flavor source of truth.
- Remove `max-parallel: 3`; do not replace it with another hardcoded matrix ceiling.
- GitHub decides how many matrix jobs start immediately and queues the remainder when account runner capacity is occupied.
- Dependabot pull requests must continue to skip `android-quality`, `resolve-apps`, `app-builds`, and CodeQL.
- Pull requests never receive Doppler, signing, Play, or production Firebase secrets.
- Every external action reference must use a 40-character immutable commit SHA and must exist in `config/pinned-github-actions.json`.
- Workflow-level permissions remain exactly `contents: read`.
- Write permissions are job-scoped and limited to: dependency submission `contents: write`; CodeQL `security-events: write`; release `id-token: write`, `attestations: write`, and `artifact-metadata: write`.
- `gradle/actions/setup-gradle` remains the only Gradle cache manager; do not add `actions/cache`.
- The `android-quality` job may write cache only on trusted `main` pushes; flavor matrix, CodeQL, device test, dependency submission, and pull request jobs are read-only.
- Diagnostic report artifacts are retained for 14 days.
- Ordinary pull request flavor builds do not upload APK or AAB files.
- Device smoke tests use Gradle Managed Devices with `aosp-atd`, API 30, one shard, and `swiftshader_indirect`.
- The release workflow is manual, uses the protected `production` environment, builds one signed AAB, produces a SHA-256 checksum, creates provenance, and does not publish to Play Console.
- Release secrets are read from Doppler through `scripts/doppler-run.sh`; only the bootstrap `DOPPLER_TOKEN` exists in GitHub's `production` environment.
- All new tests must fail before their corresponding implementation and pass afterward.

---

## File Structure

### New files

- `config/pinned-github-actions.json` — approved external action names, immutable SHAs, and human-readable release labels.
- `scripts/ci/pinned_github_actions.py` — scans active workflows and rejects unapproved action names or SHAs.
- `scripts/ci/pinned_github_actions_test.py` — isolated fixtures and repository-level action pin regression tests.
- `scripts/ci/professional_ci_workflows_test.py` — asserts full-speed DAG, Kover artifacts, dependency gates, CodeQL, managed-device, and release workflow policy.
- `.github/workflows/dependency-submission.yml` — trusted `main` dependency graph submission.
- `.github/workflows/codeql.yml` — Java/Kotlin manual-build CodeQL scan.
- `.github/workflows/device-smoke.yml` — scheduled/manual Gradle Managed Device smoke test.
- `.github/workflows/release-attested.yml` — protected manual signed AAB, checksum, upload, and provenance.
- `scripts/ci/setup_managed_device_sdk.sh` — installs the exact emulator and ATD system image required by CI.
- `scripts/ci/setup_managed_device_sdk_test.sh` — validates package selection without modifying a real SDK.
- `scripts/ci/install_doppler_cli.sh` — checksum-verified Doppler CLI 3.76.1 installer.
- `scripts/ci/install_doppler_cli_test.sh` — validates pinned metadata, checksum failure, and install output.
- `scripts/ci/validate_release_workflow_input.py` — validates flavor and retention inputs and emits normalized GitHub outputs.
- `scripts/ci/validate_release_workflow_input_test.py` — valid/invalid flavor and retention regression tests.

### Modified files

- `scripts/ci/workflow_policy.py` — invoke the approved-action manifest check from the repository policy command.
- `scripts/ci/workflow_policy_test.py` — confirm unapproved action SHAs are rejected.
- `scripts/ci/ci_load_policy_test.py` — replace the three-job ceiling assertion with full-speed and cache-discipline assertions.
- `.github/workflows/ci-pr.yml` — full-speed DAG, Kover tasks, report upload, and read-only matrix cache.
- `.github/workflows/security.yml` — dependency review and approved-action policy validation.
- `app/build.gradle.kts` — one Gradle Managed Device definition.
- `.github/workflows/README.md` — document workflow purpose, triggers, permissions, artifacts, and full-speed behavior.
- `docs/SECRETS_SETUP.md` — document the `production` environment bootstrap secret and attested release flow.

---

### Task 1: Approved immutable GitHub action manifest

**Files:**
- Create: `config/pinned-github-actions.json`
- Create: `scripts/ci/pinned_github_actions.py`
- Create: `scripts/ci/pinned_github_actions_test.py`
- Modify: `scripts/ci/workflow_policy.py`
- Modify: `scripts/ci/workflow_policy_test.py`
- Modify: `.github/workflows/ci-pr.yml`
- Modify: `.github/workflows/security.yml`

**Interfaces:**
- Consumes: active `.github/workflows/*.yml` files and external `uses:` strings.
- Produces: `validate_pinned_actions(repo: Path) -> list[PinFinding]` and CLI `python3 scripts/ci/pinned_github_actions.py --repo .`.

- [ ] **Step 1: Write the approved-action manifest**

Create `config/pinned-github-actions.json`:

```json
{
  "actions/attest": {
    "sha": "f7c74d28b9d84cb8768d0b8ca14a4bac6ef463e6",
    "version": "v4.2.0"
  },
  "actions/checkout": {
    "sha": "3d3c42e5aac5ba805825da76410c181273ba90b1",
    "version": "v7.0.1"
  },
  "actions/dependency-review-action": {
    "sha": "a1d282b36b6f3519aa1f3fc636f609c47dddb294",
    "version": "v5.0.0"
  },
  "actions/setup-java": {
    "sha": "03ad4de0992f5dab5e18fcb136590ce7c4a0ac95",
    "version": "v5.6.0"
  },
  "actions/upload-artifact": {
    "sha": "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a",
    "version": "v7.0.1"
  },
  "github/codeql-action": {
    "sha": "e0647621c2984b5ed2f768cb892365bf2a616ad1",
    "version": "v4.37.2"
  },
  "gradle/actions": {
    "sha": "3f131e8634966bd73d06cc69884922b02e6faf92",
    "version": "v6.2.0"
  }
}
```

- [ ] **Step 2: Write failing pin-policy tests**

Create `scripts/ci/pinned_github_actions_test.py`:

```python
#!/usr/bin/env python3
from __future__ import annotations

import json
import tempfile
from pathlib import Path

from pinned_github_actions import validate_pinned_actions

ROOT = Path(__file__).resolve().parents[2]


def write_repo(workflow: str, manifest: dict[str, dict[str, str]]) -> Path:
    root = Path(tempfile.mkdtemp(prefix="pinned-actions-test-"))
    (root / ".github/workflows").mkdir(parents=True)
    (root / "config").mkdir()
    (root / ".github/workflows/test.yml").write_text(workflow, encoding="utf-8")
    (root / "config/pinned-github-actions.json").write_text(
        json.dumps(manifest), encoding="utf-8"
    )
    return root


def workflow(uses: str) -> str:
    return f"""name: Test
on: push
permissions:
  contents: read
jobs:
  test:
    runs-on: ubuntu-24.04
    timeout-minutes: 5
    steps:
      - uses: {uses}
"""


def test_approved_exact_sha_passes() -> None:
    sha = "3d3c42e5aac5ba805825da76410c181273ba90b1"
    repo = write_repo(workflow(f"actions/checkout@{sha}"), {
        "actions/checkout": {"sha": sha, "version": "v7.0.1"}
    })
    assert validate_pinned_actions(repo) == []


def test_unknown_action_fails() -> None:
    sha = "a" * 40
    repo = write_repo(workflow(f"example/action@{sha}"), {})
    findings = validate_pinned_actions(repo)
    assert [item.message for item in findings] == ["external action is not approved: example/action"]


def test_wrong_sha_fails() -> None:
    approved = "3d3c42e5aac5ba805825da76410c181273ba90b1"
    repo = write_repo(workflow(f"actions/checkout@{'b' * 40}"), {
        "actions/checkout": {"sha": approved, "version": "v7.0.1"}
    })
    findings = validate_pinned_actions(repo)
    assert len(findings) == 1
    assert "must use approved SHA" in findings[0].message


def test_repository_workflows_use_only_manifest_pins() -> None:
    assert validate_pinned_actions(ROOT) == []


def main() -> int:
    tests = [
        test_approved_exact_sha_passes,
        test_unknown_action_fails,
        test_wrong_sha_fails,
        test_repository_workflows_use_only_manifest_pins,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 3: Run the test and verify RED**

Run:

```bash
python3 scripts/ci/pinned_github_actions_test.py
```

Expected: import failure because `scripts/ci/pinned_github_actions.py` does not exist.

- [ ] **Step 4: Implement the manifest validator**

Create `scripts/ci/pinned_github_actions.py`:

```python
#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterator, Sequence

import yaml

FULL_SHA = re.compile(r"^[0-9a-f]{40}$")


@dataclass(frozen=True)
class PinFinding:
    path: Path
    line: int
    message: str


def iter_steps(value: Any) -> Iterator[dict[str, Any]]:
    if isinstance(value, dict):
        if "uses" in value:
            yield value
        for child in value.values():
            yield from iter_steps(child)
    elif isinstance(value, list):
        for child in value:
            yield from iter_steps(child)


def line_for(text: str, needle: str) -> int:
    for number, line in enumerate(text.splitlines(), start=1):
        if needle in line:
            return number
    return 1


def action_owner_repo(action_path: str) -> str:
    parts = action_path.split("/")
    if len(parts) < 2:
        return action_path
    return "/".join(parts[:2])


def load_manifest(repo: Path) -> dict[str, dict[str, str]]:
    path = repo / "config/pinned-github-actions.json"
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError("pinned action manifest must be an object")
    for name, metadata in value.items():
        if not isinstance(name, str) or not isinstance(metadata, dict):
            raise RuntimeError("invalid pinned action manifest entry")
        sha = metadata.get("sha")
        version = metadata.get("version")
        if not isinstance(sha, str) or not FULL_SHA.fullmatch(sha):
            raise RuntimeError(f"invalid pinned SHA for {name}")
        if not isinstance(version, str) or not version.strip():
            raise RuntimeError(f"missing pinned version for {name}")
    return value


def validate_pinned_actions(repo: Path) -> list[PinFinding]:
    repo = repo.resolve()
    manifest = load_manifest(repo)
    findings: list[PinFinding] = []
    paths = sorted((repo / ".github/workflows").glob("*.y*ml"))
    for path in paths:
        text = path.read_text(encoding="utf-8")
        document = yaml.safe_load(text)
        if not isinstance(document, dict):
            continue
        for step in iter_steps(document):
            uses = step.get("uses")
            if not isinstance(uses, str) or uses.startswith("./") or "@" not in uses:
                continue
            action_path, sha = uses.rsplit("@", 1)
            approved_name = action_owner_repo(action_path)
            metadata = manifest.get(approved_name)
            relative = path.relative_to(repo)
            if metadata is None:
                findings.append(PinFinding(
                    relative,
                    line_for(text, uses),
                    f"external action is not approved: {approved_name}",
                ))
                continue
            approved_sha = metadata["sha"]
            if sha != approved_sha:
                findings.append(PinFinding(
                    relative,
                    line_for(text, uses),
                    f"{approved_name} must use approved SHA {approved_sha}",
                ))
    return sorted(findings, key=lambda item: (str(item.path), item.line, item.message))


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path("."))
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        findings = validate_pinned_actions(args.repo)
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    for finding in findings:
        print(f"{finding.path}:{finding.line}: {finding.message}", file=sys.stderr)
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 5: Integrate the validator into repository policy**

In `scripts/ci/workflow_policy.py`, import and append the manifest findings:

```python
from pinned_github_actions import validate_pinned_actions
```

At the end of `validate_repository`, before its return, add:

```python
    for pin_finding in validate_pinned_actions(repo):
        findings.append(
            Finding(pin_finding.path, pin_finding.line, pin_finding.message)
        )
```

In `scripts/ci/workflow_policy_test.py`, update `assert_result` so every temporary repository receives the real manifest before the validator runs:

```python
        write(
            repo / "config/pinned-github-actions.json",
            (ROOT / "config/pinned-github-actions.json").read_text(encoding="utf-8"),
        )
```

Then add:

```python
def test_unapproved_action_sha_fails() -> None:
    wrong_sha = "b" * 40
    assert_result(
        secure_workflow().replace(
            "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
            f"actions/checkout@{wrong_sha}",
        ),
        1,
        "must use approved SHA",
    )
```

Add `test_unapproved_action_sha_fails` to `main()`.

- [ ] **Step 6: Add policy tests to active workflow jobs**

In both `.github/workflows/ci-pr.yml` and `.github/workflows/security.yml`, run:

```yaml
      - name: Test approved GitHub action pins
        run: |
          set -euo pipefail
          python3 scripts/ci/pinned_github_actions_test.py
          python3 scripts/ci/pinned_github_actions.py --repo .
```

Place the step after PyYAML installation and before `workflow_policy.py`.

- [ ] **Step 7: Run Task 1 verification**

Run:

```bash
python3 scripts/ci/pinned_github_actions_test.py
python3 scripts/ci/workflow_policy_test.py
python3 scripts/ci/workflow_policy.py --repo .
```

Expected: all tests print `PASS`; policy command exits 0.

- [ ] **Step 8: Commit Task 1**

```bash
git add config/pinned-github-actions.json \
  scripts/ci/pinned_github_actions.py \
  scripts/ci/pinned_github_actions_test.py \
  scripts/ci/workflow_policy.py \
  scripts/ci/workflow_policy_test.py \
  .github/workflows/ci-pr.yml \
  .github/workflows/security.yml
git commit -m "ci: enforce approved immutable action pins"
```

---

### Task 2: Full-speed flavor matrix and Kover report artifacts

**Files:**
- Modify: `scripts/ci/ci_load_policy_test.py`
- Create: `scripts/ci/professional_ci_workflows_test.py`
- Modify: `.github/workflows/ci-pr.yml`

**Interfaces:**
- Consumes: dynamic flavor output from `resolve-apps`, existing Kover tasks, existing report paths.
- Produces: unconstrained flavor matrix, parallel quality/build DAG, `android-quality-reports-*` artifact retained for 14 days.

- [ ] **Step 1: Replace the old three-job ceiling test with failing full-speed assertions**

In `scripts/ci/ci_load_policy_test.py`, replace `test_ci_uses_dynamic_catalog_matrix` with:

```python
def test_ci_uses_full_speed_dynamic_catalog_matrix() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    jobs = workflow["jobs"]
    resolver = jobs["resolve-apps"]
    builds = jobs["app-builds"]
    assert resolver["outputs"] == {
        "flavors": "${{ steps.catalog.outputs.flavors }}",
        "count": "${{ steps.catalog.outputs.count }}",
    }
    strategy = builds["strategy"]
    assert strategy["matrix"]["flavor"] == "${{ fromJSON(needs.resolve-apps.outputs.flavors) }}"
    assert "max-parallel" not in strategy
    assert builds["needs"] == ["workflow-policy", "repository-security", "resolve-apps"]
    setup_gradle = next(
        step for step in builds["steps"] if step.get("name") == "Set up Gradle"
    )
    assert setup_gradle["with"]["cache-read-only"] is True
```

Update `main()` to call `test_ci_uses_full_speed_dynamic_catalog_matrix`.

Create `scripts/ci/professional_ci_workflows_test.py` with:

```python
#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
UPLOAD_SHA = "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"


def load(path: str) -> dict:
    value = yaml.safe_load((ROOT / path).read_text(encoding="utf-8"))
    assert isinstance(value, dict)
    return value


def named_step(job: dict, name: str) -> dict:
    return next(step for step in job["steps"] if step.get("name") == name)


def test_ci_runs_quality_and_flavors_in_parallel() -> None:
    jobs = load(".github/workflows/ci-pr.yml")["jobs"]
    assert jobs["android-quality"]["needs"] == ["workflow-policy", "repository-security"]
    assert jobs["app-builds"]["needs"] == [
        "workflow-policy", "repository-security", "resolve-apps"
    ]
    assert "max-parallel" not in jobs["app-builds"]["strategy"]


def test_ci_enforces_and_uploads_kover_reports() -> None:
    quality = load(".github/workflows/ci-pr.yml")["jobs"]["android-quality"]
    coverage = named_step(quality, "Run Kover quality gate")
    command = coverage["run"]
    for task in (
        "koverVerifyQuality",
        "koverXmlReportQuality",
        "koverHtmlReportQuality",
        "validateCriticalCoverage",
    ):
        assert task in command
    upload = named_step(quality, "Upload quality reports")
    assert upload["if"] == "always()"
    assert upload["uses"] == f"actions/upload-artifact@{UPLOAD_SHA}"
    assert upload["with"]["retention-days"] == 14
    assert upload["with"]["if-no-files-found"] == "warn"


def main() -> int:
    tests = [
        test_ci_runs_quality_and_flavors_in_parallel,
        test_ci_enforces_and_uploads_kover_reports,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 2: Run tests and verify RED**

```bash
python3 scripts/ci/ci_load_policy_test.py
python3 scripts/ci/professional_ci_workflows_test.py
```

Expected: failures report `max-parallel`, the extra `android-quality` dependency, missing Kover step, and missing upload step.

- [ ] **Step 3: Modify `ci-pr.yml` for full-speed execution**

In `app-builds`:

```yaml
    needs: [workflow-policy, repository-security, resolve-apps]
```

Delete:

```yaml
      max-parallel: 3
```

Set the matrix `Set up Gradle` input to:

```yaml
        with:
          cache-read-only: true
```

Keep `android-quality` depending only on:

```yaml
    needs: [workflow-policy, repository-security]
```

- [ ] **Step 4: Add the Kover gate and diagnostic artifact**

After `Run static analysis and unit tests` in `android-quality`, add:

```yaml
      - name: Run Kover quality gate
        run: |
          set -euo pipefail
          ./gradlew \
            koverVerifyQuality \
            koverXmlReportQuality \
            koverHtmlReportQuality \
            validateCriticalCoverage \
            --continue \
            --no-daemon \
            --stacktrace \
            --max-workers=2

      - name: Upload quality reports
        if: always()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: android-quality-reports-${{ github.sha }}
          path: |
            build/reports/kover/
            **/build/reports/tests/
            **/build/test-results/
            **/build/reports/lint-results-*.html
            **/build/reports/lint-results-*.xml
            **/build/reports/lint-results-*.sarif
          if-no-files-found: warn
          retention-days: 14
```

- [ ] **Step 5: Run Task 2 verification**

```bash
python3 scripts/ci/ci_load_policy_test.py
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy.py --repo .
python3 scripts/ci/pinned_github_actions.py --repo .
```

Expected: all commands exit 0.

Run the Gradle task graph without building APKs:

```bash
./gradlew \
  koverVerifyQuality \
  koverXmlReportQuality \
  koverHtmlReportQuality \
  validateCriticalCoverage \
  --dry-run --no-daemon --max-workers=1
```

Expected: all four tasks appear in the graph.

- [ ] **Step 6: Commit Task 2**

```bash
git add .github/workflows/ci-pr.yml \
  scripts/ci/ci_load_policy_test.py \
  scripts/ci/professional_ci_workflows_test.py
git commit -m "ci: run flavor matrix at full available capacity"
```

---

### Task 3: Dependency review and Gradle dependency submission

**Files:**
- Modify: `.github/workflows/security.yml`
- Create: `.github/workflows/dependency-submission.yml`
- Modify: `scripts/ci/professional_ci_workflows_test.py`

**Interfaces:**
- Consumes: pull-request dependency diff and trusted `main` Gradle resolution.
- Produces: blocking high/critical dependency review and submitted direct/transitive Gradle graph.

- [ ] **Step 1: Add failing dependency workflow tests**

Append to `scripts/ci/professional_ci_workflows_test.py`:

```python
DEPENDENCY_REVIEW_SHA = "a1d282b36b6f3519aa1f3fc636f609c47dddb294"
GRADLE_ACTIONS_SHA = "3f131e8634966bd73d06cc69884922b02e6faf92"


def test_security_runs_dependency_review_only_for_pull_requests() -> None:
    jobs = load(".github/workflows/security.yml")["jobs"]
    review = jobs["dependency-review"]
    assert review["if"] == "github.event_name == 'pull_request'"
    assert review["permissions"] == {"contents": "read"}
    step = named_step(review, "Review dependency changes")
    assert step["uses"] == f"actions/dependency-review-action@{DEPENDENCY_REVIEW_SHA}"
    assert step["with"] == {
        "fail-on-severity": "high",
        "show-openssf-scorecard": True,
        "show-patched-versions": True,
    }


def test_dependency_submission_is_trusted_and_job_scoped() -> None:
    workflow = load(".github/workflows/dependency-submission.yml")
    assert workflow["permissions"] == {"contents": "read"}
    job = workflow["jobs"]["submit-gradle-dependencies"]
    assert job["permissions"] == {"contents": "write"}
    step = named_step(job, "Submit Gradle dependency graph")
    assert step["uses"] == f"gradle/actions/dependency-submission@{GRADLE_ACTIONS_SHA}"
    assert step["with"]["dependency-graph"] == "generate-and-submit"
    assert step["with"]["cache-read-only"] is True
```

Add both functions to `main()`.

- [ ] **Step 2: Run test and verify RED**

```bash
python3 scripts/ci/professional_ci_workflows_test.py
```

Expected: missing `dependency-review` job and missing `dependency-submission.yml`.

- [ ] **Step 3: Add the pull-request dependency review job**

Append this job to `.github/workflows/security.yml`:

```yaml
  dependency-review:
    name: Dependency Review
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-24.04
    timeout-minutes: 10
    permissions:
      contents: read
    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          fetch-depth: 1
          persist-credentials: false

      - name: Review dependency changes
        uses: actions/dependency-review-action@a1d282b36b6f3519aa1f3fc636f609c47dddb294 # v5.0.0
        with:
          fail-on-severity: high
          show-openssf-scorecard: true
          show-patched-versions: true
```

- [ ] **Step 4: Create the trusted dependency submission workflow**

Create `.github/workflows/dependency-submission.yml`:

```yaml
name: Dependency Submission

on:
  push:
    branches: [main]
    paths:
      - '**/*.gradle'
      - '**/*.gradle.kts'
      - 'gradle/**'
      - 'gradle.properties'
      - 'settings.gradle.kts'
      - 'buildSrc/**'
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: dependency-submission-${{ github.ref }}
  cancel-in-progress: true

jobs:
  submit-gradle-dependencies:
    name: Submit Gradle Dependencies
    runs-on: ubuntu-24.04
    timeout-minutes: 20
    permissions:
      contents: write
    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          fetch-depth: 1
          persist-credentials: false

      - name: Set up JDK 21
        uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: '21'
          check-latest: false

      - name: Submit Gradle dependency graph
        uses: gradle/actions/dependency-submission@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0
        with:
          dependency-graph: generate-and-submit
          cache-read-only: true
```

- [ ] **Step 5: Run Task 3 verification**

```bash
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy.py --repo .
python3 scripts/ci/pinned_github_actions.py --repo .
```

Expected: exit 0.

- [ ] **Step 6: Commit Task 3**

```bash
git add .github/workflows/security.yml \
  .github/workflows/dependency-submission.yml \
  scripts/ci/professional_ci_workflows_test.py
git commit -m "ci: add dependency review and graph submission"
```

---

### Task 4: Java/Kotlin CodeQL with representative manual build

**Files:**
- Create: `.github/workflows/codeql.yml`
- Modify: `scripts/ci/professional_ci_workflows_test.py`

**Interfaces:**
- Consumes: secret-free Firebase placeholder generator and `assembleKuran_kerimDebug`.
- Produces: CodeQL `java-kotlin` analysis uploaded to GitHub code scanning.

- [ ] **Step 1: Add failing CodeQL policy test**

Append:

```python
CODEQL_SHA = "e0647621c2984b5ed2f768cb892365bf2a616ad1"


def test_codeql_uses_manual_kotlin_build_and_cleans_placeholder() -> None:
    workflow = load(".github/workflows/codeql.yml")
    job = workflow["jobs"]["analyze-java-kotlin"]
    assert job["permissions"] == {"contents": "read", "security-events": "write"}
    assert "dependabot[bot]" in job["if"]
    init = named_step(job, "Initialize CodeQL")
    assert init["uses"] == f"github/codeql-action/init@{CODEQL_SHA}"
    assert init["with"] == {
        "languages": "java-kotlin",
        "build-mode": "manual",
        "queries": "security-extended",
    }
    build = named_step(job, "Build representative flavor for CodeQL")
    assert "assembleKuran_kerimDebug" in build["run"]
    cleanup = named_step(job, "Remove CI-only Firebase placeholder")
    assert cleanup["if"] == "always()"
    analyze = named_step(job, "Analyze Java and Kotlin")
    assert analyze["uses"] == f"github/codeql-action/analyze@{CODEQL_SHA}"
```

Add it to `main()`.

- [ ] **Step 2: Run test and verify RED**

```bash
python3 scripts/ci/professional_ci_workflows_test.py
```

Expected: missing `.github/workflows/codeql.yml`.

- [ ] **Step 3: Create `.github/workflows/codeql.yml`**

```yaml
name: CodeQL

on:
  pull_request:
  push:
    branches: [main]
  schedule:
    - cron: '23 3 * * 3'
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: codeql-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true

jobs:
  analyze-java-kotlin:
    name: Analyze Java and Kotlin
    if: github.event_name != 'pull_request' || github.event.pull_request.user.login != 'dependabot[bot]'
    runs-on: ubuntu-24.04
    timeout-minutes: 60
    permissions:
      contents: read
      security-events: write
    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          fetch-depth: 1
          persist-credentials: false

      - name: Set up JDK 21
        uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: '21'
          check-latest: false

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0
        with:
          cache-read-only: true

      - name: Set up pinned Android SDK
        run: |
          set -euo pipefail
          bash scripts/ci/setup-android-sdk.sh

      - name: Generate CI-only Firebase placeholder
        run: |
          set -euo pipefail
          python3 scripts/ci/generate_ci_google_services.py --flavors kuran_kerim

      - name: Initialize CodeQL
        uses: github/codeql-action/init@e0647621c2984b5ed2f768cb892365bf2a616ad1 # v4.37.2
        with:
          languages: java-kotlin
          build-mode: manual
          queries: security-extended

      - name: Build representative flavor for CodeQL
        run: |
          set -euo pipefail
          chmod +x ./gradlew
          ./gradlew assembleKuran_kerimDebug --no-daemon --stacktrace --max-workers=2

      - name: Remove CI-only Firebase placeholder
        if: always()
        run: |
          set -euo pipefail
          python3 scripts/ci/generate_ci_google_services.py --clean --flavors kuran_kerim

      - name: Analyze Java and Kotlin
        uses: github/codeql-action/analyze@e0647621c2984b5ed2f768cb892365bf2a616ad1 # v4.37.2
        with:
          category: /language:java-kotlin
```

- [ ] **Step 4: Verify CodeQL workflow statically**

```bash
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy.py --repo .
python3 scripts/ci/pinned_github_actions.py --repo .
```

Expected: exit 0.

Verify the representative build task exists:

```bash
python3 scripts/ci/generate_ci_google_services.py --flavors kuran_kerim
trap 'python3 scripts/ci/generate_ci_google_services.py --clean --flavors kuran_kerim' EXIT
./gradlew assembleKuran_kerimDebug --dry-run --no-daemon --max-workers=1
```

Expected: task graph resolves successfully.

- [ ] **Step 5: Commit Task 4**

```bash
git add .github/workflows/codeql.yml scripts/ci/professional_ci_workflows_test.py
git commit -m "ci: add Java and Kotlin CodeQL analysis"
```

---

### Task 5: Gradle Managed Device smoke tests

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `scripts/ci/setup_managed_device_sdk.sh`
- Create: `scripts/ci/setup_managed_device_sdk_test.sh`
- Create: `.github/workflows/device-smoke.yml`
- Modify: `scripts/ci/professional_ci_workflows_test.py`

**Interfaces:**
- Consumes: Android SDK installed by `setup-android-sdk.sh`, two existing `androidTest` smoke classes, and flavor `kuran_kerim`.
- Produces: Gradle task `ciPixel2Api30Kuran_kerimDebugAndroidTest` and retained device-test reports.

- [ ] **Step 1: Write failing managed-device SDK installer test**

Create `scripts/ci/setup_managed_device_sdk_test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
mkdir -p "$tmp/sdk/cmdline-tools/21.0/bin" "$tmp/bin"
log="$tmp/sdkmanager.log"
cat > "$tmp/sdk/cmdline-tools/21.0/bin/sdkmanager" <<SH
#!/usr/bin/env bash
printf '%s\n' "\$@" >> "$log"
SH
chmod +x "$tmp/sdk/cmdline-tools/21.0/bin/sdkmanager"
cat > "$tmp/bin/yes" <<'SH'
#!/usr/bin/env bash
printf 'y\n'
SH
chmod +x "$tmp/bin/yes"

PATH="$tmp/bin:$PATH" \
ANDROID_SDK_ROOT="$tmp/sdk" \
ANDROID_HOME="$tmp/sdk" \
bash "$repo_root/scripts/ci/setup_managed_device_sdk.sh"

grep -Fx -- '--sdk_root='"$tmp/sdk" "$log"
grep -Fx 'emulator' "$log"
grep -Fx 'system-images;android-30;aosp_atd;x86_64' "$log"
echo 'PASS setup_managed_device_sdk_test'
```

- [ ] **Step 2: Add failing workflow and Gradle assertions**

Append to `scripts/ci/professional_ci_workflows_test.py`:

```python
def test_managed_device_is_pinned_and_scheduled() -> None:
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    for value in (
        'create("ciPixel2Api30")',
        'device = "Pixel 2"',
        'apiLevel = 30',
        'systemImageSource = "aosp-atd"',
    ):
        assert value in gradle
    workflow = load(".github/workflows/device-smoke.yml")
    job = workflow["jobs"]["managed-device-smoke"]
    command = named_step(job, "Run managed-device smoke tests")["run"]
    assert "ciPixel2Api30Kuran_kerimDebugAndroidTest" in command
    assert "swiftshader_indirect" in command
    upload = named_step(job, "Upload managed-device reports")
    assert upload["with"]["retention-days"] == 14
    assert upload["if"] == "always()"
```

Add to `main()`.

- [ ] **Step 3: Run tests and verify RED**

```bash
bash scripts/ci/setup_managed_device_sdk_test.sh
python3 scripts/ci/professional_ci_workflows_test.py
```

Expected: missing installer, missing Gradle device, and missing workflow.

- [ ] **Step 4: Implement the managed-device package installer**

Create `scripts/ci/setup_managed_device_sdk.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$repo_root/scripts/ci/android-toolchain.sh"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$sdk_root" ]] || { echo 'ANDROID_SDK_ROOT or ANDROID_HOME is required' >&2; exit 1; }
manager="$sdk_root/cmdline-tools/$ANDROID_CMDLINE_TOOLS_VERSION/bin/sdkmanager"
[[ -x "$manager" ]] || { echo "Pinned sdkmanager is missing: $manager" >&2; exit 1; }
set +o pipefail
yes | "$manager" --sdk_root="$sdk_root" --licenses >/dev/null
license_rc=$?
set -o pipefail
[[ $license_rc -eq 0 ]] || exit "$license_rc"
"$manager" --sdk_root="$sdk_root" \
  emulator \
  'system-images;android-30;aosp_atd;x86_64'
echo 'Managed-device SDK packages installed.'
```

Set executable bits:

```bash
chmod +x scripts/ci/setup_managed_device_sdk.sh scripts/ci/setup_managed_device_sdk_test.sh
```

- [ ] **Step 5: Configure the Gradle Managed Device**

Inside `android {}` in `app/build.gradle.kts`, before `lint {}`, add:

```kotlin
    testOptions {
        managedDevices {
            localDevices {
                create("ciPixel2Api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
```

- [ ] **Step 6: Create the scheduled/manual device workflow**

Create `.github/workflows/device-smoke.yml`:

```yaml
name: Device Smoke

on:
  schedule:
    - cron: '41 2 * * *'
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: device-smoke-${{ github.ref }}
  cancel-in-progress: true

jobs:
  managed-device-smoke:
    name: Managed Device Smoke
    runs-on: ubuntu-24.04
    timeout-minutes: 60
    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          fetch-depth: 1
          persist-credentials: false

      - name: Set up JDK 21
        uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: '21'
          check-latest: false

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0
        with:
          cache-read-only: true

      - name: Set up pinned Android SDK
        run: |
          set -euo pipefail
          bash scripts/ci/setup-android-sdk.sh
          bash scripts/ci/setup_managed_device_sdk.sh

      - name: Generate CI-only Firebase placeholder
        run: |
          set -euo pipefail
          python3 scripts/ci/generate_ci_google_services.py --flavors kuran_kerim

      - name: Run managed-device smoke tests
        run: |
          set -euo pipefail
          chmod +x ./gradlew
          ./gradlew \
            ciPixel2Api30Kuran_kerimDebugAndroidTest \
            -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
            --no-daemon \
            --stacktrace \
            --max-workers=2

      - name: Remove CI-only Firebase placeholder
        if: always()
        run: |
          set -euo pipefail
          python3 scripts/ci/generate_ci_google_services.py --clean --flavors kuran_kerim

      - name: Upload managed-device reports
        if: always()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: managed-device-smoke-${{ github.sha }}
          path: |
            app/build/reports/androidTests/managedDevice/
            app/build/outputs/androidTest-results/managedDevice/
          if-no-files-found: warn
          retention-days: 14
```

- [ ] **Step 7: Run Task 5 verification**

```bash
bash scripts/ci/setup_managed_device_sdk_test.sh
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy.py --repo .
./gradlew :app:tasks --all --no-daemon --max-workers=1 | grep -F 'ciPixel2Api30Kuran_kerimDebugAndroidTest'
```

Expected: all tests pass and the Gradle task is listed.

- [ ] **Step 8: Commit Task 5**

```bash
git add app/build.gradle.kts \
  scripts/ci/setup_managed_device_sdk.sh \
  scripts/ci/setup_managed_device_sdk_test.sh \
  .github/workflows/device-smoke.yml \
  scripts/ci/professional_ci_workflows_test.py
git commit -m "ci: add managed-device Android smoke tests"
```

---

### Task 6: Checksum-verified Doppler installer and release input validation

**Files:**
- Create: `scripts/ci/install_doppler_cli.sh`
- Create: `scripts/ci/install_doppler_cli_test.sh`
- Create: `scripts/ci/validate_release_workflow_input.py`
- Create: `scripts/ci/validate_release_workflow_input_test.py`

**Interfaces:**
- Consumes: Linux amd64 GitHub runner and `.ci/apps.json` flavor catalog.
- Produces: executable Doppler 3.76.1 in a requested bin directory and normalized outputs `flavor`, `capitalized`, and `retention_days`.

- [ ] **Step 1: Write failing Doppler installer test**

Create `scripts/ci/install_doppler_cli_test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
config="$(bash "$repo_root/scripts/ci/install_doppler_cli.sh" --print-config)"
grep -Fx 'version=3.76.1' <<<"$config"
grep -Fx 'asset=doppler_3.76.1_linux_amd64.tar.gz' <<<"$config"
grep -Fx 'sha256=e35230bd21fdbd7e41ddcb24672ec61cecefdb22de244d0216ea6b59853f63f2' <<<"$config"
echo 'PASS install_doppler_cli_test'
```

- [ ] **Step 2: Write failing release-input tests**

Create `scripts/ci/validate_release_workflow_input_test.py`:

```python
#!/usr/bin/env python3
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/validate_release_workflow_input.py"


def run(flavor: str, retention: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["python3", str(SCRIPT), "--flavor", flavor, "--retention-days", retention],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )


def test_valid_input() -> None:
    result = run("kuran_kerim", "14")
    assert result.returncode == 0, result.stderr
    assert "flavor=kuran_kerim" in result.stdout
    assert "capitalized=Kuran_kerim" in result.stdout
    assert "retention_days=14" in result.stdout


def test_unknown_flavor_fails() -> None:
    result = run("not_an_app", "14")
    assert result.returncode == 1
    assert "unknown flavor" in result.stderr.lower()


def test_retention_outside_allowlist_fails() -> None:
    result = run("kuran_kerim", "90")
    assert result.returncode == 1
    assert "retention" in result.stderr.lower()


def main() -> int:
    for test in (test_valid_input, test_unknown_flavor_fails, test_retention_outside_allowlist_fails):
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 3: Run tests and verify RED**

```bash
bash scripts/ci/install_doppler_cli_test.sh
python3 scripts/ci/validate_release_workflow_input_test.py
```

Expected: both fail because implementation files do not exist.

- [ ] **Step 4: Implement checksum-verified Doppler installer**

Create `scripts/ci/install_doppler_cli.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
version='3.76.1'
asset="doppler_${version}_linux_amd64.tar.gz"
sha256='e35230bd21fdbd7e41ddcb24672ec61cecefdb22de244d0216ea6b59853f63f2'
url="https://github.com/DopplerHQ/cli/releases/download/${version}/${asset}"
if [[ "${1:-}" == '--print-config' ]]; then
  printf 'version=%s\nasset=%s\nsha256=%s\n' "$version" "$asset" "$sha256"
  exit 0
fi
bin_dir="${1:-${RUNNER_TEMP:-/tmp}/doppler-bin}"
mkdir -p "$bin_dir"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
curl -fsSL --retry 3 --retry-delay 2 "$url" -o "$tmp/$asset"
printf '%s  %s\n' "$sha256" "$tmp/$asset" | sha256sum -c -
tar -xzf "$tmp/$asset" -C "$tmp"
install -m 0755 "$tmp/doppler" "$bin_dir/doppler"
"$bin_dir/doppler" --version >&2
printf 'DOPPLER_BIN=%q\n' "$bin_dir/doppler"
```

- [ ] **Step 5: Implement release input validator**

Create `scripts/ci/validate_release_workflow_input.py`:

```python
#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from resolve_ci_flavor_matrix import load_flavors

ALLOWED_RETENTION = {7, 14, 30}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--flavor", required=True)
    parser.add_argument("--retention-days", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        flavors = load_flavors(root)
        retention = int(args.retention_days)
    except (RuntimeError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    if args.flavor not in flavors:
        print(
            f"ERROR: unknown flavor {args.flavor!r}; allowed: {', '.join(flavors)}",
            file=sys.stderr,
        )
        return 1
    if retention not in ALLOWED_RETENTION:
        print("ERROR: retention days must be one of 7, 14, or 30", file=sys.stderr)
        return 1
    print(f"flavor={args.flavor}")
    print(f"capitalized={args.flavor[0].upper() + args.flavor[1:]}")
    print(f"retention_days={retention}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

Set executable bits:

```bash
chmod +x scripts/ci/install_doppler_cli.sh \
  scripts/ci/install_doppler_cli_test.sh \
  scripts/ci/validate_release_workflow_input.py \
  scripts/ci/validate_release_workflow_input_test.py
```

- [ ] **Step 6: Run Task 6 verification**

```bash
bash scripts/ci/install_doppler_cli_test.sh
python3 scripts/ci/validate_release_workflow_input_test.py
bash -n scripts/ci/install_doppler_cli.sh
```

Expected: all pass.

- [ ] **Step 7: Commit Task 6**

```bash
git add scripts/ci/install_doppler_cli.sh \
  scripts/ci/install_doppler_cli_test.sh \
  scripts/ci/validate_release_workflow_input.py \
  scripts/ci/validate_release_workflow_input_test.py
git commit -m "ci: add verified Doppler release bootstrap"
```

---

### Task 7: Protected signed AAB upload and provenance attestation

**Files:**
- Create: `.github/workflows/release-attested.yml`
- Modify: `scripts/ci/professional_ci_workflows_test.py`
- Modify: `docs/SECRETS_SETUP.md`

**Interfaces:**
- Consumes: `production` environment secret `DOPPLER_TOKEN`, `scripts/doppler-run.sh`, `FIREBASE_CONFIGS_ZIP_BASE64`, signing secrets, and one validated flavor.
- Produces: one signed AAB, one `.sha256` file, one GitHub artifact, and one GitHub provenance attestation; no Play upload.

- [ ] **Step 1: Add failing attested-release workflow test**

Append:

```python
ATTEST_SHA = "f7c74d28b9d84cb8768d0b8ca14a4bac6ef463e6"


def test_release_is_manual_protected_and_attested() -> None:
    workflow = load(".github/workflows/release-attested.yml")
    event = workflow.get("on", workflow.get(True))
    assert list(event) == ["workflow_dispatch"]
    job = workflow["jobs"]["build-attest-release"]
    assert job["environment"] == "production"
    assert job["permissions"] == {
        "contents": "read",
        "id-token": "write",
        "attestations": "write",
        "artifact-metadata": "write",
    }
    build = named_step(job, "Build signed AAB with Doppler")
    assert "scripts/doppler-run.sh" in build["run"]
    assert "materialize_firebase_configs.py" in build["run"]
    assert "publish" not in build["run"].lower()
    attest = named_step(job, "Attest signed AAB")
    assert attest["uses"] == f"actions/attest@{ATTEST_SHA}"
    upload = named_step(job, "Upload signed AAB and checksum")
    assert upload["with"]["if-no-files-found"] == "error"
```

Add to `main()`.

- [ ] **Step 2: Run test and verify RED**

```bash
python3 scripts/ci/professional_ci_workflows_test.py
```

Expected: missing `release-attested.yml`.

- [ ] **Step 3: Create `.github/workflows/release-attested.yml`**

```yaml
name: Attested Release Artifact

on:
  workflow_dispatch:
    inputs:
      flavor:
        description: Android application flavor
        required: true
        type: string
        default: kuran_kerim
      retention_days:
        description: Artifact retention
        required: true
        type: choice
        default: '14'
        options: ['7', '14', '30']

permissions:
  contents: read

concurrency:
  group: release-attested-${{ inputs.flavor }}
  cancel-in-progress: false

jobs:
  build-attest-release:
    name: Build and Attest ${{ inputs.flavor }}
    runs-on: ubuntu-24.04
    timeout-minutes: 90
    environment: production
    permissions:
      contents: read
      id-token: write
      attestations: write
      artifact-metadata: write
    env:
      REQUESTED_FLAVOR: ${{ inputs.flavor }}
      REQUESTED_RETENTION_DAYS: ${{ inputs.retention_days }}
      DOPPLER_TOKEN: ${{ secrets.DOPPLER_TOKEN }}
      DOPPLER_PROJECT: android-multi-app-framework
      DOPPLER_CONFIG: prod
    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          fetch-depth: 1
          persist-credentials: false

      - name: Set up JDK 21
        uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: '21'
          check-latest: false

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0
        with:
          cache-read-only: true

      - name: Set up pinned Android SDK
        run: |
          set -euo pipefail
          bash scripts/ci/setup-android-sdk.sh

      - name: Validate release inputs
        id: release-inputs
        run: |
          set -euo pipefail
          python3 scripts/ci/validate_release_workflow_input.py \
            --flavor "$REQUESTED_FLAVOR" \
            --retention-days "$REQUESTED_RETENTION_DAYS" >> "$GITHUB_OUTPUT"

      - name: Validate Doppler bootstrap
        run: |
          set -euo pipefail
          [[ -n "${DOPPLER_TOKEN:-}" ]] || {
            echo '::error::production environment secret DOPPLER_TOKEN is missing'
            exit 1
          }

      - name: Install verified Doppler CLI
        run: |
          set -euo pipefail
          assignments="$(bash scripts/ci/install_doppler_cli.sh "$RUNNER_TEMP/doppler-bin")"
          eval "$assignments"
          printf '%s\n' "$(dirname "$DOPPLER_BIN")" >> "$GITHUB_PATH"

      - name: Build signed AAB with Doppler
        env:
          RELEASE_FLAVOR: ${{ steps.release-inputs.outputs.flavor }}
          RELEASE_CAPITALIZED: ${{ steps.release-inputs.outputs.capitalized }}
        run: |
          set -euo pipefail
          export RELEASE_FLAVOR RELEASE_CAPITALIZED
          scripts/doppler-run.sh -- bash -lc '
            set -euo pipefail
            cleanup_firebase() {
              rm -f -- "app/src/${RELEASE_FLAVOR}/google-services.json"
            }
            trap cleanup_firebase EXIT
            python3 scripts/ci/materialize_firebase_configs.py \
              --flavors "$RELEASE_FLAVOR" \
              --mode strict
            python3 scripts/ci/verify_google_signin_config.py \
              --flavors "$RELEASE_FLAVOR" \
              --require-web-client-id
            ./gradlew ":app:bundle${RELEASE_CAPITALIZED}Release" \
              --no-daemon \
              --stacktrace \
              --max-workers=2
          '

      - name: Prepare AAB and checksum
        id: artifact
        env:
          RELEASE_FLAVOR: ${{ steps.release-inputs.outputs.flavor }}
        run: |
          set -euo pipefail
          source_dir="app/build/outputs/bundle/${RELEASE_FLAVOR}Release"
          mapfile -t aabs < <(find "$source_dir" -maxdepth 1 -type f -name '*.aab' -print)
          [[ ${#aabs[@]} -eq 1 ]] || {
            echo "::error::Expected exactly one AAB in $source_dir, found ${#aabs[@]}"
            exit 1
          }
          mkdir -p dist
          short_sha="${GITHUB_SHA:0:12}"
          artifact="dist/${RELEASE_FLAVOR}-${short_sha}.aab"
          cp -- "${aabs[0]}" "$artifact"
          sha256sum "$artifact" > "${artifact}.sha256"
          printf 'subject_path=%s\n' "$artifact" >> "$GITHUB_OUTPUT"

      - name: Attest signed AAB
        uses: actions/attest@f7c74d28b9d84cb8768d0b8ca14a4bac6ef463e6 # v4.2.0
        with:
          subject-path: ${{ steps.artifact.outputs.subject_path }}
          create-storage-record: false

      - name: Upload signed AAB and checksum
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: signed-aab-${{ steps.release-inputs.outputs.flavor }}-${{ github.sha }}
          path: |
            ${{ steps.artifact.outputs.subject_path }}
            ${{ steps.artifact.outputs.subject_path }}.sha256
          if-no-files-found: error
          retention-days: ${{ steps.release-inputs.outputs.retention_days }}
```


- [ ] **Step 4: Document the protected release bootstrap**

Append to `docs/SECRETS_SETUP.md`:

```markdown
## GitHub protected attested release

The active `Attested Release Artifact` workflow is manual and uses the GitHub environment `production`.

GitHub stores only one bootstrap secret in that environment:

- `DOPPLER_TOKEN`: a read-only service token scoped to project `android-multi-app-framework`, config `prod`.

The workflow installs checksum-verified Doppler CLI 3.76.1, calls `scripts/doppler-run.sh`, materializes the signing keystore and Firebase config only for the requested flavor, builds one signed AAB, deletes temporary secret files, writes a SHA-256 checksum, uploads both files for 7/14/30 days, and creates a GitHub artifact attestation. It does not publish to Google Play.
```

- [ ] **Step 5: Run Task 7 verification**

```bash
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/workflow_policy.py --repo .
python3 scripts/ci/pinned_github_actions.py --repo .
bash scripts/ci/test_doppler_run.sh
bash scripts/ci/release_task_graph_dry_run.sh
```

Expected: all pass.

- [ ] **Step 6: Commit Task 7**

```bash
git add .github/workflows/release-attested.yml \
  scripts/ci/professional_ci_workflows_test.py \
  docs/SECRETS_SETUP.md
git commit -m "ci: add protected attested Android release artifacts"
```

---

### Task 8: Documentation, complete static gate, live rollout, and capacity verification

**Files:**
- Modify: `.github/workflows/README.md`
- Modify: `scripts/ci/professional_ci_workflows_test.py` if live findings require a regression assertion.

**Interfaces:**
- Consumes: Tasks 1-7.
- Produces: reviewed PR, green GitHub workflows, production environment bootstrap, and evidence that the fixed three-job ceiling is gone.

- [ ] **Step 1: Update workflow documentation**

Document these active workflows in `.github/workflows/README.md`:

```markdown
| Workflow | Trigger | Purpose | Expensive path |
|---|---|---|---|
| CI | PR, main push | policy, secrets, quality, 17 flavor lint/assemble | skipped for Dependabot |
| Security | PR, main push, weekly, manual | actionlint, zizmor, Gitleaks, dependency review | dependency review only on PR |
| Dependency Submission | dependency/build changes on main, manual | submit resolved Gradle graph | trusted main only |
| CodeQL | human PR, main, weekly, manual | Java/Kotlin manual-build analysis | one representative flavor |
| Device Smoke | nightly, manual | two instrumentation smoke tests on Gradle Managed Device | one flavor, one ATD |
| Attested Release Artifact | manual | signed AAB, checksum, upload, provenance | protected production environment |
```

Add the capacity statement:

```markdown
The flavor matrix does not define `max-parallel`. All 17 jobs become runnable after the short policy/security/catalog gates. GitHub starts as many jobs as the account's currently available hosted-runner concurrency permits and queues the rest. Dependabot never enters this matrix.
```

- [ ] **Step 2: Run the complete local static and repository gate**

```bash
python3 scripts/ci/pinned_github_actions_test.py
python3 scripts/ci/workflow_policy_test.py
python3 scripts/ci/professional_ci_workflows_test.py
python3 scripts/ci/ci_load_policy_test.py
python3 scripts/ci/generate_ci_google_services_test.py
python3 scripts/ci/validate_release_workflow_input_test.py
bash scripts/ci/install_doppler_cli_test.sh
bash scripts/ci/setup_managed_device_sdk_test.sh
bash scripts/ci/test_doppler_run.sh
python3 scripts/ci/workflow_policy.py --repo .
python3 scripts/ci/pinned_github_actions.py --repo .
bash scripts/ci/run_secret_scan.sh --mode dir --report-dir /tmp/ci-professional-actions-secret-scan
```

Expected: every command exits 0 and Gitleaks reports zero leaks.

- [ ] **Step 3: Run workflow syntax and security tools**

```bash
assignments="$(bash scripts/ci/install_workflow_security_tools.sh --bin-dir /tmp/ci-professional-workflow-tools)"
eval "$assignments"
"$ACTIONLINT_BIN" .github/workflows/*.yml
"$ZIZMOR_BIN" --offline --strict-collection --min-confidence high \
  --format plain .github/workflows .github/actions .github/dependabot.yml
```

Expected: `actionlint` and `zizmor` exit 0.

- [ ] **Step 4: Commit documentation and final local fixes**

```bash
git add .github/workflows/README.md scripts/ci/professional_ci_workflows_test.py
git commit -m "docs: document professional Android CI workflows"
```

- [ ] **Step 5: Push the feature branch and open a PR**

Push with the MakerParsDev credential path already used for this repository, then create:

```text
Title: ci: add professional Android actions and full-speed matrix
Base: main
Head: feature/ci-professional-actions
```

PR body must list:

```markdown
- removes the fixed three-flavor concurrency ceiling
- runs Android Quality and flavor builds concurrently
- adds Kover report artifacts
- adds Dependency Review and Gradle Dependency Submission
- adds Java/Kotlin CodeQL manual-build analysis
- adds scheduled Gradle Managed Device smoke tests
- adds protected signed AAB provenance workflow
- keeps Dependabot on the lightweight smoke path
```

- [ ] **Step 6: Verify the PR's live capacity behavior**

Use GitHub run/job inspection and record:

```text
Workflow Policy: success
Repository Security: success
Dependency Review: success
Android Quality: success
CodeQL: success
17 flavor build jobs: success
```

During the first flavor wave, confirm more than three flavor jobs are simultaneously `in_progress` when account capacity is free. The acceptance condition is not “all 17 must always run at once”; it is “the workflow imposes no local ceiling, and GitHub is the only queueing authority.”

- [ ] **Step 7: Merge only after required PR workflows are green**

Squash merge with:

```text
ci: add professional Android actions and full-speed matrix
```

Verify the resulting commit author is:

```text
MakerParsDev <makerpars@gmail.com>
```

- [ ] **Step 8: Configure the protected production environment**

Create or confirm the GitHub environment `production`, require manual approval if the account plan supports it, and set only `DOPPLER_TOKEN` as an environment secret. Generate a dedicated read-only Doppler service token for project `android-multi-app-framework`, config `prod`, and pipe it directly into GitHub CLI without printing it.

The token transfer must follow this shape:

```bash
doppler configs tokens create github-actions-release \
  --project android-multi-app-framework \
  --config prod \
  --plain |
gh secret set DOPPLER_TOKEN \
  --repo MakerParsDev/android-multi-app-framework \
  --env production \
  --body -
```

Do not store the generated token in a file, command argument, shell history expansion, log, or chat response.

- [ ] **Step 9: Validate trusted post-merge workflows**

After merge:

1. Confirm `CI`, `Security`, and `CodeQL` succeed on `main`.
2. Confirm `Dependency Submission` succeeds or manually dispatch it once.
3. Manually dispatch `Device Smoke`; require both existing instrumentation smoke tests to pass and inspect the retained reports.
4. Manually dispatch `Attested Release Artifact` for `kuran_kerim`, retention `14`.
5. Confirm exactly one AAB and one checksum artifact exist.
6. Verify the attestation:

```bash
gh attestation verify path/to/downloaded.aab \
  --repo MakerParsDev/android-multi-app-framework
```

7. Confirm no `app/src/*/google-services.json`, keystore, service-account JSON, or Doppler token remains in the repository workspace or uploaded diagnostic artifacts.

- [ ] **Step 10: Clean feature worktree and branch**

After merge and successful post-merge validation:

```bash
git worktree remove .worktrees/ci-professional-actions
git branch -d feature/ci-professional-actions
```

Do not remove any other worktree that contains uncommitted changes.
