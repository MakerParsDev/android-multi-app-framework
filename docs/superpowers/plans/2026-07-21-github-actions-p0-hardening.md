# GitHub Actions P0 Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Activate a minimal, secret-free GitHub Actions baseline that rejects unsafe workflow constructs and reliably validates the Android project on pull requests and `main`.

**Architecture:** A repository-owned Python policy validator is the first gate for active workflows and local composite actions. Two small workflows, `ci-pr.yml` and `security.yml`, use immutable action SHAs, least-privilege permissions, explicit timeouts, credential-free checkout, pinned security tools, and the repository’s own Android toolchain scripts. Legacy release and deployment workflows remain under `.github/workflows.disabled`.

**Tech Stack:** GitHub Actions, YAML, Python 3.12 with PyYAML 6.0.3, Bash, actionlint 1.7.12, zizmor 1.27.0, Gitleaks 8.30.1, JDK 21, Gradle 9.5.1, Android SDK manifest from `gradle.properties`.

## Global Constraints

- Active P0 workflows use no repository secrets and never use `pull_request_target`.
- External actions use immutable 40-character commit SHAs with version comments.
- Every active workflow job defines `timeout-minutes`.
- Workflow permissions are exactly `contents: read`; no P0 job receives write permission.
- Every checkout step sets `persist-credentials: false`.
- Event and input expressions enter shell code through `env:`, never direct `${{ ... }}` expansion inside `run:`.
- Runners are pinned to `ubuntu-24.04`; Java is Temurin 21.
- Android SDK packages are installed through `scripts/ci/setup-android-sdk.sh`.
- Gradle commands do not run `clean`; use `--no-daemon --stacktrace --max-workers=2`.
- Representative flavors are exactly `kuran_kerim`, `namazvakitleri`, `kible`, and `zikirmatik`.
- Legacy release, Play, Doppler, Cloudflare, Firebase-secret, AdMob credential, and deployment workflows remain disabled.
- Every commit uses `MakerParsDev <makerpars@gmail.com>`.

## File Map

- Create `scripts/ci/workflow_policy.py`: canonical active workflow/local action validator.
- Create `scripts/ci/workflow_policy_test.py`: secure and insecure fixture tests plus repository regressions.
- Modify `.github/actions/resolve-flavors/action.yml`: pass input through `env:`.
- Modify `.github/actions/verify-env-contract/action.yml`: remove arbitrary script-path input.
- Create `config/workflow-security-tools.json`: exact tool URLs and SHA-256 values.
- Create `scripts/ci/install_workflow_security_tools.sh`: checksum-verifying installer.
- Create `scripts/ci/workflow_security_tools_test.py`: tool metadata/interface tests.
- Create `scripts/ci/requirements-workflow-policy.txt`: `PyYAML==6.0.3`.
- Create `.github/workflows/ci-pr.yml`: secret-free Android CI.
- Create `.github/workflows/security.yml`: workflow and supply-chain security checks.
- Create `.github/dependabot.yml`: Actions, Gradle, and npm updates.
- Modify `.github/workflows/README.md`: active/disabled workflow boundary.

---

### Task 1: Add the workflow policy validator

**Files:**
- Create: `scripts/ci/workflow_policy.py`
- Create: `scripts/ci/workflow_policy_test.py`
- Create: `scripts/ci/requirements-workflow-policy.txt`

**Interfaces:**
- Consumes: `--repo PATH`, active `.github/workflows/*.{yml,yaml}`, and `.github/actions/*/action.{yml,yaml}`.
- Produces: `validate_repository(repo: Path) -> list[Finding]`; CLI exits `0` when clean and `1` on violations.

- [ ] **Step 1: Write failing fixture tests**

Create `scripts/ci/workflow_policy_test.py` with temporary-repository tests for these exact cases:

```python
#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = ROOT / "scripts/ci/workflow_policy.py"


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(content).lstrip(), encoding="utf-8")


def run_validator(repo: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["python3", str(VALIDATOR), "--repo", str(repo)],
        check=False,
        text=True,
        capture_output=True,
    )


def secure_workflow() -> str:
    return """
    name: CI
    on: [pull_request]
    permissions:
      contents: read
    jobs:
      test:
        runs-on: ubuntu-24.04
        timeout-minutes: 10
        steps:
          - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
            with:
              persist-credentials: false
          - env:
              USER_VALUE: ${{ github.head_ref }}
            run: |
              set -euo pipefail
              printf '%s\\n' "$USER_VALUE"
    """


def assert_result(workflow: str, expected_code: int, message: str = "") -> None:
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        write(repo / ".github/workflows/ci.yml", workflow)
        result = run_validator(repo)
        assert result.returncode == expected_code, result.stderr
        if message:
            assert message in result.stderr, result.stderr


def test_secure_fixture_passes() -> None:
    assert_result(secure_workflow(), 0)


def test_unpinned_action_fails() -> None:
    assert_result(
        secure_workflow().replace(
            "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
            "actions/checkout@v7",
        ),
        1,
        "external action must be pinned",
    )


def test_direct_template_in_run_fails() -> None:
    assert_result(
        secure_workflow().replace(
            "printf '%s\\n' \"$USER_VALUE\"",
            "printf '%s\\n' \"${{ github.head_ref }}\"",
        ),
        1,
        "template expression inside run block",
    )


def test_missing_timeout_fails() -> None:
    assert_result(
        secure_workflow().replace("timeout-minutes: 10\n", ""),
        1,
        "missing timeout-minutes",
    )


def test_checkout_credentials_fail() -> None:
    assert_result(
        secure_workflow().replace(
            "with:\n          persist-credentials: false\n",
            "",
        ),
        1,
        "persist-credentials: false",
    )


def test_pull_request_target_fails() -> None:
    assert_result(
        secure_workflow().replace("on: [pull_request]", "on: [pull_request_target]"),
        1,
        "pull_request_target is prohibited",
    )


def main() -> int:
    tests = [
        test_secure_fixture_passes,
        test_unpinned_action_fails,
        test_direct_template_in_run_fails,
        test_missing_timeout_fails,
        test_checkout_credentials_fail,
        test_pull_request_target_fails,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

Create `scripts/ci/requirements-workflow-policy.txt`:

```text
PyYAML==6.0.3
```

- [ ] **Step 2: Verify the test fails because the validator is absent**

Run:

```bash
python3 scripts/ci/workflow_policy_test.py
```

Expected: non-zero exit referring to missing `scripts/ci/workflow_policy.py`.

- [ ] **Step 3: Implement `workflow_policy.py`**

The implementation must:

```python
@dataclass(frozen=True)
class Finding:
    path: Path
    line: int
    message: str


def validate_repository(repo: Path) -> list[Finding]:
    """Validate active workflows and local actions, ignoring workflows.disabled."""
```

Use `yaml.safe_load`, scan only active workflow/local-action paths, and enforce:

```text
workflow permissions == {contents: read}
all jobs contain timeout-minutes
all external uses refs match ^[0-9a-f]{40}$
all actions/checkout steps set persist-credentials: false
no pull_request_target trigger
no ${{ expression }} text inside any shell run block
```

Local `./.github/actions/...` references are allowed. Each error prints `relative/path.yml:line: message` to stderr. YAML parse errors are findings rather than tracebacks.

- [ ] **Step 4: Run unit tests**

```bash
python3 scripts/ci/workflow_policy_test.py
```

Expected: six `PASS` lines and exit `0`.

- [ ] **Step 5: Run against the repository**

```bash
python3 scripts/ci/workflow_policy.py --repo .
```

Expected before Task 2: failure only for the two known composite-action `run:` interpolations; disabled workflows are ignored.

- [ ] **Step 6: Commit**

```bash
git add scripts/ci/workflow_policy.py scripts/ci/workflow_policy_test.py scripts/ci/requirements-workflow-policy.txt
git commit -m "test: add GitHub workflow policy validator"
```

---

### Task 2: Remove local composite-action template injection

**Files:**
- Modify: `.github/actions/resolve-flavors/action.yml`
- Modify: `.github/actions/verify-env-contract/action.yml`
- Modify: `scripts/ci/workflow_policy_test.py`

**Interfaces:**
- `resolve-flavors` retains `target_flavors` and existing `resolved_json`/`resolved_csv` outputs.
- `verify-env-contract` always executes `scripts/ci/verify_env_contract.sh` and exposes no script-path input.

- [ ] **Step 1: Add repository regression tests**

Add tests that parse both action files and assert:

```python
assert "${{" not in each_run_block
assert resolve_step["env"]["TARGET_FLAVORS_INPUT"] == "${{ inputs.target_flavors }}"
assert 'INPUT="${TARGET_FLAVORS_INPUT:-}"' in resolve_step["run"]
assert "inputs" not in verify_env_document
assert 'bash "scripts/ci/verify_env_contract.sh"' in verify_env_run
```

Add them to `main()` and run the test. Expected: failure against current action files.

- [ ] **Step 2: Fix `resolve-flavors/action.yml`**

Add:

```yaml
env:
  TARGET_FLAVORS_INPUT: ${{ inputs.target_flavors }}
```

inside the `resolve` step and replace:

```bash
INPUT="${{ inputs.target_flavors }}"
```

with:

```bash
INPUT="${TARGET_FLAVORS_INPUT:-}"
```

Keep all validation, deduplication, JSON, and CSV behavior unchanged.

- [ ] **Step 3: Fix `verify-env-contract/action.yml`**

Replace it with:

```yaml
name: Verify Env Contract
description: Verifies committed env template contract for GitHub Actions runtime values

runs:
  using: composite
  steps:
    - shell: bash
      run: |
        set -euo pipefail
        bash "scripts/ci/verify_env_contract.sh"
```

- [ ] **Step 4: Verify tests and analyzers**

```bash
python3 scripts/ci/workflow_policy_test.py
python3 scripts/ci/workflow_policy.py --repo .
uvx --from zizmor==1.27.0 zizmor --offline .github/actions
```

Expected: regression tests pass; no high-confidence `template-injection` finding. Existing low-confidence `$GITHUB_ENV` warnings remain visible and are not silently suppressed.

- [ ] **Step 5: Commit**

```bash
git add .github/actions/resolve-flavors/action.yml .github/actions/verify-env-contract/action.yml scripts/ci/workflow_policy_test.py
git commit -m "fix: remove composite action template injection"
```

---

### Task 3: Add checksum-verified workflow security tools

**Files:**
- Create: `config/workflow-security-tools.json`
- Create: `scripts/ci/install_workflow_security_tools.sh`
- Create: `scripts/ci/workflow_security_tools_test.py`

**Interfaces:**
- Installer supports `--bin-dir PATH` and `--print-config`.
- Normal output is exactly two shell assignment lines: `ACTIONLINT_BIN=...` and `ZIZMOR_BIN=...`.

- [ ] **Step 1: Write failing metadata/interface tests**

The test must assert these exact records:

```json
{
  "actionlint": {
    "version": "1.7.12",
    "asset": "actionlint_1.7.12_linux_amd64.tar.gz",
    "url": "https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz",
    "sha256": "8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8",
    "binary": "actionlint"
  },
  "zizmor": {
    "version": "1.27.0",
    "asset": "zizmor-x86_64-unknown-linux-gnu.tar.gz",
    "url": "https://github.com/zizmorcore/zizmor/releases/download/v1.27.0/zizmor-x86_64-unknown-linux-gnu.tar.gz",
    "sha256": "277f2bd8fd37cf60c42ab7afca6faa884e65440fa31e02b44bdaae60f62a358f",
    "binary": "zizmor"
  }
}
```

The `--print-config` test expects:

```text
actionlint=1.7.12
zizmor=1.27.0
```

Run `python3 scripts/ci/workflow_security_tools_test.py`; expected failure because files are absent.

- [ ] **Step 2: Add the exact JSON config**

Create `config/workflow-security-tools.json` with the object above.

- [ ] **Step 3: Implement the installer**

The Bash installer must use `set -euo pipefail`, `curl --proto '=https' --tlsv1.2`, a 10-second connect timeout, 300-second maximum time, `sha256sum --check --status`, `mktemp -d`, `trap` cleanup, and `install -m 0755`. It must cache under `.tools/workflow-security/<tool>/<version>/` unless `--bin-dir` is supplied.

- [ ] **Step 4: Verify unit and live checksum behavior**

```bash
chmod +x scripts/ci/install_workflow_security_tools.sh
python3 scripts/ci/workflow_security_tools_test.py
rm -rf /tmp/workflow-security-tools-test
bash scripts/ci/install_workflow_security_tools.sh --bin-dir /tmp/workflow-security-tools-test
/tmp/workflow-security-tools-test/actionlint/1.7.12/actionlint -version
/tmp/workflow-security-tools-test/zizmor/1.27.0/zizmor --version
```

Expected versions: `1.7.12` and `1.27.0`.

- [ ] **Step 5: Commit**

```bash
git add config/workflow-security-tools.json scripts/ci/install_workflow_security_tools.sh scripts/ci/workflow_security_tools_test.py
git commit -m "feat: install pinned workflow security tools"
```

---

### Task 4: Add secret-free Android pull-request CI

**Files:**
- Create: `.github/workflows/ci-pr.yml`
- Modify: `.github/workflows/README.md`

**Interfaces:**
- No secrets.
- Status checks: `CI / Workflow Policy`, `CI / Repository Security`, `CI / Android Quality`, and `CI / Build <flavor>`.

- [ ] **Step 1: Create `.github/workflows/ci-pr.yml`**

Use these immutable actions:

```yaml
uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0
```

Workflow skeleton:

```yaml
name: CI
on:
  pull_request:
  push:
    branches: [main]
permissions:
  contents: read
concurrency:
  group: ci-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true
```

Jobs and exact bounds:

```text
workflow-policy       ubuntu-24.04  timeout 10
repository-security   ubuntu-24.04  timeout 15
android-quality       ubuntu-24.04  timeout 45
representative-builds ubuntu-24.04  timeout 35, max-parallel 2, fail-fast false
```

Every checkout sets `persist-credentials: false`; security jobs use `fetch-depth: 0` only where history is required.

Commands:

```bash
python3 -m pip install --disable-pip-version-check --no-deps -r scripts/ci/requirements-workflow-policy.txt
python3 scripts/ci/workflow_policy_test.py
python3 scripts/ci/workflow_policy.py --repo .
python3 scripts/ci/validate_tracked_sensitive_files.py
bash scripts/ci/verify_env_contract.sh
bash scripts/ci/run_secret_scan.sh --mode dir --report-dir build/reports/security
bash scripts/ci/setup-android-sdk.sh
python3 scripts/ci/validate_ci_apps_catalog.py --mode strict --target-flavors all
python3 scripts/ci/validate_admob_inventory.py --mode warn --target-flavors all
python3 scripts/ci/validate_android_toolchain_config.py
./gradlew detekt ktlintCheck testDebugUnitTest validateFlavorVersions --continue --no-daemon --stacktrace --max-workers=2
```

Representative matrix:

```yaml
strategy:
  fail-fast: false
  max-parallel: 2
  matrix:
    flavor: [kuran_kerim, namazvakitleri, kible, zikirmatik]
```

Pass the matrix value through:

```yaml
env:
  FLAVOR: ${{ matrix.flavor }}
```

and run:

```bash
cap="${FLAVOR^}"
./gradlew "lint${cap}Debug" "assemble${cap}Debug" --no-daemon --stacktrace --max-workers=2
```

- [ ] **Step 2: Document active boundaries**

Update `.github/workflows/README.md` so it lists only `ci-pr.yml` and `security.yml` as active and states that release, Play, Cloudflare, Firebase-secret, AdMob credential, and deployment workflows must remain disabled until they pass policy, actionlint, and zizmor.

- [ ] **Step 3: Validate locally**

```bash
python3 scripts/ci/workflow_policy.py --repo .
actionlint .github/workflows/ci-pr.yml .github/actions/*/action.yml
bash scripts/ci/setup-android-sdk.sh
./gradlew detekt ktlintCheck testDebugUnitTest validateFlavorVersions --continue --no-daemon --stacktrace --max-workers=2
for flavor in kuran_kerim namazvakitleri kible zikirmatik; do
  cap="${flavor^}"
  ./gradlew "lint${cap}Debug" "assemble${cap}Debug" --no-daemon --stacktrace --max-workers=2
done
```

Expected: policy/actionlint exit `0`; Gradle commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci-pr.yml .github/workflows/README.md
git commit -m "ci: add secret-free Android pull request checks"
```

---

### Task 5: Add security workflow and Dependabot

**Files:**
- Create: `.github/workflows/security.yml`
- Create: `.github/dependabot.yml`

**Interfaces:**
- No secrets.
- Status check: `Security / Workflow and Supply Chain`.

- [ ] **Step 1: Create `security.yml`**

Triggers:

```yaml
on:
  pull_request:
  push:
    branches: [main]
  schedule:
    - cron: '17 4 * * 1'
  workflow_dispatch:
```

Set `permissions: {contents: read}`, `ubuntu-24.04`, `timeout-minutes: 20`, concurrency cancellation, and checkout SHA `3d3c42e5aac5ba805825da76410c181273ba90b1` with history and `persist-credentials: false`.

Run:

```bash
python3 -m pip install --disable-pip-version-check --no-deps -r scripts/ci/requirements-workflow-policy.txt
bash scripts/ci/install_workflow_security_tools.sh > "$RUNNER_TEMP/workflow-tools.env"
cat "$RUNNER_TEMP/workflow-tools.env" >> "$GITHUB_ENV"
"$ACTIONLINT_BIN" .github/workflows/*.yml .github/actions/*/action.yml
python3 scripts/ci/workflow_policy_test.py
python3 scripts/ci/workflow_policy.py --repo .
"$ZIZMOR_BIN" --offline .github/workflows .github/actions
python3 scripts/ci/dependency_policy.py
python3 scripts/ci/validate_supply_chain_policy.py
python3 scripts/ci/validate_secret_scan_policy.py
bash scripts/ci/run_secret_scan.sh --mode history --report-dir build/reports/security
```

Confirm the three Python policy scripts accept zero arguments with `--help` or source inspection. Use their documented exact invocation; never skip a failing policy check.

- [ ] **Step 2: Create `.github/dependabot.yml`**

Configure weekly Monday updates in `Europe/Istanbul` for:

```text
github-actions /
gradle /
npm /side-projects/admin-notifications
npm /side-projects/firebase/functions
npm /side-projects/firebase/rules-tests
npm /side-projects/cloudflare/workers/admin-api
npm /side-projects/cloudflare/workers/content-api
npm /side-projects/cloudflare/workers/ssv-callback
```

Group all GitHub Actions updates. Group npm minor/patch updates. Set open PR limit `5` for Actions and Gradle.

- [ ] **Step 3: Run the full security gate**

```bash
python3 scripts/ci/workflow_policy.py --repo .
mapfile -t tool_env < <(bash scripts/ci/install_workflow_security_tools.sh)
for line in "${tool_env[@]}"; do export "$line"; done
"$ACTIONLINT_BIN" .github/workflows/*.yml .github/actions/*/action.yml
"$ZIZMOR_BIN" --offline .github/workflows .github/actions
bash scripts/ci/run_secret_scan.sh --mode dir --report-dir /tmp/github-actions-p0-secret-scan
```

Expected: policy/actionlint/Gitleaks exit `0`; zizmor reports no high-confidence finding.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/security.yml .github/dependabot.yml
git commit -m "ci: add workflow and supply chain security checks"
```

---

### Task 6: Final validation and real GitHub rollout

**Files:**
- Modify only when a validation failure proves a necessary correction.

**Interfaces:**
- Produces a pushed feature branch, successful real Actions runs, and a PR to `main`.

- [ ] **Step 1: Verify branch identity and scope**

```bash
git branch --show-current
git status --short
git log --format='%h %an <%ae> | %s' main..HEAD
git diff --stat main...HEAD
```

Expected branch: `feature/github-actions-p0-hardening`; clean worktree; all commits by MakerParsDev.

- [ ] **Step 2: Run the complete local gate**

```bash
python3 scripts/ci/workflow_policy_test.py
python3 scripts/ci/workflow_security_tools_test.py
python3 scripts/ci/workflow_policy.py --repo .
mapfile -t tool_env < <(bash scripts/ci/install_workflow_security_tools.sh)
for line in "${tool_env[@]}"; do export "$line"; done
"$ACTIONLINT_BIN" .github/workflows/*.yml .github/actions/*/action.yml
"$ZIZMOR_BIN" --offline .github/workflows .github/actions
python3 scripts/ci/validate_tracked_sensitive_files.py
bash scripts/ci/verify_env_contract.sh
bash scripts/ci/run_secret_scan.sh --mode history --report-dir /tmp/github-actions-p0-final-secret-scan
./gradlew detekt ktlintCheck testDebugUnitTest validateFlavorVersions --continue --no-daemon --stacktrace --max-workers=2
for flavor in kuran_kerim namazvakitleri kible zikirmatik; do
  cap="${flavor^}"
  ./gradlew "lint${cap}Debug" "assemble${cap}Debug" --no-daemon --stacktrace --max-workers=2
done
```

Expected: every command exits `0`; no high-confidence zizmor finding; no secret leak; all Gradle builds succeed.

- [ ] **Step 3: Confirm active workflow inventory**

```bash
find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) -printf '%f\n' | sort
```

Expected:

```text
ci-pr.yml
security.yml
```

- [ ] **Step 4: Push the feature branch**

Use the established MSI MakerParsDev SSH path without modifying the MSI working tree. Verify remote branch SHA equals local `HEAD`.

- [ ] **Step 5: Create the PR**

Title:

```text
ci: establish secure GitHub Actions baseline
```

Body:

```markdown
## Summary
- adds secret-free Android PR/main CI
- adds workflow and supply-chain security checks
- pins external actions and security tools
- fixes composite-action template injection
- adds Dependabot coverage

## Safety boundary
Release, Play, Cloudflare, Firebase-secret, and deployment workflows remain disabled.

## Validation
- workflow policy tests
- actionlint
- zizmor
- Gitleaks
- Android static analysis and unit tests
- representative flavor lint and debug builds
```

- [ ] **Step 6: Observe real workflow runs**

Both `CI` and `Security` must pass on the branch/PR. Reproduce every failure locally, fix on the feature branch, rerun the relevant complete gate, commit, and push. Do not bypass a failed check.

- [ ] **Step 7: Merge only after green checks**

Squash merge after every active check succeeds. Set squash identity to `MakerParsDev <makerpars@gmail.com>`. Verify the `main` push runs both workflows successfully and reachable commit authors remain MakerParsDev only.
