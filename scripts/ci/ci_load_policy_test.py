#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]


def load_yaml(path: Path) -> dict:
    value = yaml.safe_load(path.read_text(encoding="utf-8"))
    assert isinstance(value, dict), path
    return value


def test_dependabot_is_one_monthly_android_maintenance_group() -> None:
    config = load_yaml(ROOT / ".github/dependabot.yml")
    assert config.get("version") == 2
    groups = config.get("multi-ecosystem-groups")
    assert isinstance(groups, dict)
    maintenance = groups.get("android-maintenance")
    assert isinstance(maintenance, dict)
    assert maintenance.get("schedule") == {"interval": "monthly"}

    updates = config.get("updates")
    assert isinstance(updates, list)
    assert [item.get("package-ecosystem") for item in updates] == [
        "github-actions",
        "gradle",
    ]
    for item in updates:
        assert item.get("directory") == "/"
        assert item.get("patterns") == ["*"]
        assert item.get("multi-ecosystem-group") == "android-maintenance"
        assert item.get("open-pull-requests-limit") == 1
        cooldown = item.get("cooldown")
        assert isinstance(cooldown, dict)
        assert cooldown.get("default-days") == 30
    gradle = updates[1]
    assert gradle["cooldown"].get("semver-major-days") == 60


def test_ci_uses_impact_analysis_for_flavor_selection() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    jobs = workflow["jobs"]
    analyze = jobs["analyze-impact"]
    assert "has_code" in analyze["outputs"]
    assert "flavors_json" in analyze["outputs"]
    static = jobs["static-analysis"]
    needs = static["needs"]
    if isinstance(needs, str):
        needs = [needs]
    assert needs == ["analyze-impact"]
    assert "max-parallel" not in static.get("strategy", {})


def test_dependabot_prs_skip_heavy_android_jobs() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    jobs = workflow["jobs"]
    for job_name in (
        "static-analysis",
        "validate-and-test",
        "android-lint",
        "kover-coverage",
    ):
        job = jobs[job_name]
        assert "analyze-impact" in job["needs"], (
            f"{job_name} should depend on analyze-impact"
        )


def test_ci_load_tests_run_after_security_gate() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    aggregate = workflow["jobs"]["aggregate-gate"]
    assert "security-gate" in aggregate["needs"]
    assert "static-analysis" in aggregate["needs"]
    assert "validate-and-test" in aggregate["needs"]


def main() -> int:
    tests = [
        test_dependabot_is_one_monthly_android_maintenance_group,
        test_ci_uses_impact_analysis_for_flavor_selection,
        test_dependabot_prs_skip_heavy_android_jobs,
        test_ci_load_tests_run_after_security_gate,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
