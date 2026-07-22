#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import textwrap

import yaml
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
        write(
            repo / "config/pinned-github-actions.json",
            (ROOT / "config/pinned-github-actions.json").read_text(encoding="utf-8"),
        )
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
        secure_workflow().replace("        timeout-minutes: 10\n", ""),
        1,
        "missing timeout-minutes",
    )


def test_checkout_credentials_fail() -> None:
    assert_result(
        secure_workflow().replace(
            "            with:\n              persist-credentials: false\n",
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



def load_yaml(path: Path) -> dict:
    document = yaml.safe_load(path.read_text(encoding="utf-8"))
    assert isinstance(document, dict)
    return document


def test_resolve_flavors_uses_env_for_input() -> None:
    document = load_yaml(ROOT / ".github/actions/resolve-flavors/action.yml")
    steps = document["runs"]["steps"]
    resolve_step = next(step for step in steps if step.get("id") == "resolve")
    for step in steps:
        run = step.get("run")
        if isinstance(run, str):
            assert "${{" not in run, run
    assert resolve_step["env"]["TARGET_FLAVORS_INPUT"] == "${{ inputs.target_flavors }}"
    assert 'INPUT="${TARGET_FLAVORS_INPUT:-}"' in resolve_step["run"]


def test_verify_env_contract_uses_fixed_script() -> None:
    document = load_yaml(ROOT / ".github/actions/verify-env-contract/action.yml")
    assert "inputs" not in document
    steps = document["runs"]["steps"]
    for step in steps:
        run = step.get("run")
        if isinstance(run, str):
            assert "${{" not in run, run
    verify_env_run = steps[0]["run"]
    assert 'bash "scripts/ci/verify_env_contract.sh"' in verify_env_run


def test_performance_contract_inherits_read_only_permissions_without_secrets() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    job = workflow["jobs"]["performance-contract"]
    assert workflow["permissions"] == {"contents": "read"}
    assert "permissions" not in job
    assert "secrets" not in job
    assert "environment" not in job


def main() -> int:
    tests = [
        test_secure_fixture_passes,
        test_unpinned_action_fails,
        test_unapproved_action_sha_fails,
        test_direct_template_in_run_fails,
        test_missing_timeout_fails,
        test_checkout_credentials_fail,
        test_pull_request_target_fails,
        test_resolve_flavors_uses_env_for_input,
        test_verify_env_contract_uses_fixed_script,
        test_performance_contract_inherits_read_only_permissions_without_secrets,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
