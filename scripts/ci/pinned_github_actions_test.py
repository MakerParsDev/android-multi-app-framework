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


def test_composite_action_pin_is_checked_against_manifest() -> None:
    approved = "3d3c42e5aac5ba805825da76410c181273ba90b1"
    repo = write_repo(workflow(f"actions/checkout@{approved}"), {
        "actions/checkout": {"sha": approved, "version": "v7.0.1"}
    })
    action = repo / ".github/actions/example/action.yml"
    action.parent.mkdir(parents=True)
    action.write_text(
        f"""name: Example
runs:
  using: composite
  steps:
    - uses: actions/checkout@{'b' * 40}
""",
        encoding="utf-8",
    )
    findings = validate_pinned_actions(repo)
    assert len(findings) == 1
    assert findings[0].path == Path(".github/actions/example/action.yml")
    assert "must use approved SHA" in findings[0].message


def test_repository_workflows_use_only_manifest_pins() -> None:
    assert validate_pinned_actions(ROOT) == []


def main() -> int:
    tests = [
        test_approved_exact_sha_passes,
        test_unknown_action_fails,
        test_wrong_sha_fails,
        test_composite_action_pin_is_checked_against_manifest,
        test_repository_workflows_use_only_manifest_pins,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
