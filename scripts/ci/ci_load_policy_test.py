#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]


def load_yaml(path: Path) -> dict:
    value = yaml.safe_load(path.read_text(encoding="utf-8"))
    assert isinstance(value, dict), path
    return value


def test_dependabot_matches_autonomous_update_boundaries() -> None:
    config = load_yaml(ROOT / ".github/dependabot.yml")
    assert config.get("version") == 2

    updates = config.get("updates")
    assert isinstance(updates, list)
    expected = {
        ("github-actions", "/"),
        ("gradle", "/"),
        ("pip", "/scripts/ci"),
        ("npm", "/side-projects/admin-notifications"),
        ("npm", "/side-projects/cloudflare/workers/admin-api"),
        ("npm", "/side-projects/cloudflare/workers/content-api"),
        ("npm", "/side-projects/cloudflare/workers/ssv-callback"),
        ("npm", "/side-projects/firebase/functions"),
        ("npm", "/side-projects/firebase/rules-tests"),
    }
    actual = {
        (item.get("package-ecosystem"), item.get("directory"))
        for item in updates
    }
    assert expected == actual

    for item in updates:
        assert item.get("schedule") == {"interval": "weekly", "day": "sunday"}
        assert item.get("rebase-strategy") != "disabled"

    gradle = next(
        item for item in updates if item.get("package-ecosystem") == "gradle"
    )
    dev = gradle["groups"]["gradle-dev-patch-minor"]
    prod = gradle["groups"]["gradle-prod-patch"]
    assert dev["dependency-type"] == "development"
    assert set(dev["update-types"]) == {"patch", "minor"}
    assert prod["dependency-type"] == "production"
    assert prod["update-types"] == ["patch"]

    for item in updates:
        if item.get("package-ecosystem") != "npm":
            continue
        groups = item["groups"]
        assert groups["npm-dev-patch-minor"]["dependency-type"] == "development"
        assert set(groups["npm-dev-patch-minor"]["update-types"]) == {
            "patch",
            "minor",
        }
        assert groups["npm-prod-patch"]["dependency-type"] == "production"
        assert groups["npm-prod-patch"]["update-types"] == ["patch"]


def test_ci_uses_impact_analysis_for_flavor_and_side_project_selection() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    jobs = workflow["jobs"]
    analyze = jobs["analyze-impact"]
    assert "has_code" in analyze["outputs"]
    assert "has_side_project_changes" in analyze["outputs"]
    assert "flavors_json" in analyze["outputs"]

    static = jobs["static-analysis"]
    needs = static["needs"]
    if isinstance(needs, str):
        needs = [needs]
    assert needs == ["analyze-impact"]
    assert "max-parallel" not in static.get("strategy", {})


def test_android_quality_jobs_are_impact_gated() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    jobs = workflow["jobs"]
    for job_name in (
        "static-analysis",
        "validate-and-test",
        "android-lint",
        "kover-coverage",
    ):
        job = jobs[job_name]
        needs = job["needs"]
        if isinstance(needs, str):
            needs = [needs]
        assert "analyze-impact" in needs, (
            f"{job_name} should depend on analyze-impact"
        )


def test_side_project_quality_is_blocking_when_side_projects_change() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    jobs = workflow["jobs"]
    side = jobs["side-projects"]
    assert side["needs"] == "analyze-impact"
    assert (
        side["if"]
        == "needs.analyze-impact.outputs.has_side_project_changes == 'true'"
    )
    assert "continue-on-error" not in side

    aggregate = jobs["aggregate-gate"]
    assert "side-projects" in aggregate["needs"]
    step = next(
        item
        for item in aggregate["steps"]
        if item.get("name") == "Check required jobs"
    )
    assert step["env"]["SIDE_PROJECT_RESULT"] == "${{ needs.side-projects.result }}"
    assert "side-projects:$SIDE_PROJECT_RESULT" in step["run"]


def test_ci_load_tests_run_after_security_gate() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    aggregate = workflow["jobs"]["aggregate-gate"]
    assert "security-gate" in aggregate["needs"]
    assert "side-projects" in aggregate["needs"]
    assert "static-analysis" in aggregate["needs"]
    assert "validate-and-test" in aggregate["needs"]


def main() -> int:
    tests = [
        test_dependabot_matches_autonomous_update_boundaries,
        test_ci_uses_impact_analysis_for_flavor_and_side_project_selection,
        test_android_quality_jobs_are_impact_gated,
        test_side_project_quality_is_blocking_when_side_projects_change,
        test_ci_load_tests_run_after_security_gate,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
