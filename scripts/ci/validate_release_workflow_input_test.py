#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/validate_release_workflow_input.py"


def run(
    flavor: str,
    retention: str,
    enable_ad_inspector_debug: str = "false",
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--flavor",
            flavor,
            "--retention-days",
            retention,
            "--enable-ad-inspector-debug",
            enable_ad_inspector_debug,
        ],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )


def test_valid_input_defaults_ad_inspector_disabled() -> None:
    result = run("kuran_kerim", "14")
    assert result.returncode == 0, result.stderr
    assert "flavor=kuran_kerim" in result.stdout
    assert "capitalized=Kuran_kerim" in result.stdout
    assert "package_name=com.parsfilo.kuran_kerim" in result.stdout
    assert "retention_days=14" in result.stdout
    assert "enable_ad_inspector_debug=false" in result.stdout
    assert "release_name_prefix=internal" in result.stdout


def test_ad_inspector_debug_is_limited_to_the_two_issue_337_flavors() -> None:
    for flavor in ("namazsurelerivedualarsesli", "ismiazamduasi"):
        result = run(flavor, "14", "true")
        assert result.returncode == 0, result.stderr
        assert "ad_inspector_debug=true" in result.stdout

    rejected = run("kuran_kerim", "14", "true")
    assert rejected.returncode == 1
    assert "only supported" in rejected.stderr.lower()


def test_invalid_ad_inspector_debug_value_fails() -> None:
    result = run("namazsurelerivedualarsesli", "14", "yes")
    assert result.returncode == 2
    assert "true" in result.stderr.lower() and "false" in result.stderr.lower()


def test_unknown_flavor_fails() -> None:
    result = run("not_an_app", "14")
    assert result.returncode == 1
    assert "unknown flavor" in result.stderr.lower()


def test_retention_outside_allowlist_fails() -> None:
    result = run("kuran_kerim", "90")
    assert result.returncode == 1
    assert "retention" in result.stderr.lower()


def main() -> int:
    for test in (
        test_valid_input_defaults_ad_inspector_disabled,
        test_ad_inspector_debug_is_limited_to_the_two_issue_337_flavors,
        test_invalid_ad_inspector_debug_value_fails,
        test_unknown_flavor_fails,
        test_retention_outside_allowlist_fails,
    ):
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
