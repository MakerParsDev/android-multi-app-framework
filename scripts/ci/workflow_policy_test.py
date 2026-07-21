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
