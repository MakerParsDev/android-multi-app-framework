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


def test_dependabot_prs_skip_heavy_android_jobs() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    jobs = workflow["jobs"]
    human_only = "github.event_name != 'pull_request' || github.event.pull_request.user.login != 'dependabot[bot]'"
    for job_name in ("android-quality", "resolve-apps", "app-builds"):
        assert jobs[job_name].get("if") == human_only
    smoke = jobs.get("dependabot-smoke")
    assert isinstance(smoke, dict)
    assert smoke.get("if") == "github.event_name == 'pull_request' && github.event.pull_request.user.login == 'dependabot[bot]'"
    assert smoke.get("timeout-minutes") == 20


def test_ci_load_tests_run_after_pinned_yaml_install() -> None:
    workflow = load_yaml(ROOT / ".github/workflows/ci-pr.yml")
    steps = workflow["jobs"]["workflow-policy"]["steps"]
    names = [step.get("name") for step in steps]
    install_index = names.index("Install workflow policy dependency")
    test_index = names.index("Test CI load policy")
    assert install_index < test_index
    repository_names = [
        step.get("name")
        for step in workflow["jobs"]["repository-security"]["steps"]
    ]
    assert "Test CI load policy" not in repository_names


def main() -> int:
    tests = [
        test_dependabot_is_one_monthly_android_maintenance_group,
        test_ci_uses_full_speed_dynamic_catalog_matrix,
        test_dependabot_prs_skip_heavy_android_jobs,
        test_ci_load_tests_run_after_pinned_yaml_install,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
