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
    assert "package_name=com.parsfilo.kuran_kerim" in result.stdout
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
