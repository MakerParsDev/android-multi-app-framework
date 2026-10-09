#!/usr/bin/env python3
"""Regression tests for fail-closed PR impact and conditional CI jobs."""

from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path

import yaml

from compute_pr_impact import classify_paths

ROOT = Path(__file__).resolve().parents[2]
FLAVORS = ["kuran_kerim", "namazvakitleri"]


def test_shared_code_runs_all_flavors() -> None:
    for path in (
        "core/auth/src/main/User.kt",
        "scripts/ci/verify_release.py",
        ".github/workflows/ci-pr.yml",
        "config/autonomy-policy.yaml",
        "app/src/test/java/AppTest.kt",
        "gradle/libs.versions.toml",
        "tests/maintenance/test_policy.py",
    ):
        result = classify_paths([path], FLAVORS)
        assert result["has_code"] == "true", path
        assert json.loads(result["flavors_json"]) == FLAVORS, path


def test_one_flavor_only() -> None:
    result = classify_paths(["app/src/kuran_kerim/res/values/strings.xml"], FLAVORS)
    assert result["has_code"] == "true"
    assert json.loads(result["flavors_json"]) == ["kuran_kerim"]


def test_multiple_flavors_selected_in_catalog_order() -> None:
    paths = ["app/src/namazvakitleri/AndroidManifest.xml", "app/src/kuran_kerim/assets/data.bin"]
    assert json.loads(classify_paths(paths, FLAVORS)["flavors_json"]) == FLAVORS


def test_unknown_android_flavor_fails_closed_to_all() -> None:
    result = classify_paths(["app/src/unknown/AndroidManifest.xml"], FLAVORS)
    assert json.loads(result["flavors_json"]) == FLAVORS


def test_side_project_only_needs_side_project_quality() -> None:
    result = classify_paths(["side-projects/firebase/functions/package-lock.json"], FLAVORS)
    assert result["has_code"] == "false"
    assert result["has_side_project_changes"] == "true"
    assert result["flavors_json"] == "[]"


def test_mixed_impact_runs_both_areas() -> None:
    result = classify_paths(["side-projects/firebase/functions/package.json",
                            "scripts/ci/security_gate.sh"], FLAVORS)
    assert result["has_code"] == "true"
    assert result["has_side_project_changes"] == "true"
    assert json.loads(result["flavors_json"]) == FLAVORS


def test_documentation_only_skips_nonessential_builds() -> None:
    result = classify_paths(["docs/CI_CD.md", "README.md"], FLAVORS)
    assert result["has_code"] == "false"
    assert result["has_side_project_changes"] == "false"


def test_pr_aggregate_requires_success_for_every_affected_job() -> None:
    workflow = yaml.safe_load((ROOT / ".github/workflows/ci-pr.yml").read_text())
    jobs = workflow["jobs"]
    analyze_step = next(x for x in jobs["analyze-impact"]["steps"]
                        if x.get("name") == "Detect changed flavors")
    assert "compute_pr_impact.py" in analyze_step["run"]
    aggregate = next(x for x in jobs["aggregate-gate"]["steps"]
                     if x.get("name") == "Check required jobs")
    script = aggregate["run"]
    assert "ANALYZE_IMPACT_RESULT" in script
    assert "HAS_CODE" in script and "HAS_SIDE_PROJECT_CHANGES" in script
    assert 'check_result "$STATIC_ANALYSIS_RESULT" "$HAS_CODE"' in script
    assert 'check_result "$SIDE_PROJECT_RESULT" "$HAS_SIDE_PROJECT_CHANGES"' in script


def test_required_aggregate_runtime_fail_closed() -> None:
    workflow = yaml.safe_load((ROOT / ".github/workflows/ci-pr.yml").read_text())
    run = next(x["run"] for x in workflow["jobs"]["aggregate-gate"]["steps"]
               if x.get("name") == "Check required jobs")
    env = dict(os.environ)
    env.update({
        "SECURITY_GATE_RESULT": "success", "ANALYZE_IMPACT_RESULT": "success",
        "REPOSITORY_SECURITY_RESULT": "success", "SECURITY_REQUIRED_RESULT": "success",
        "HAS_CODE": "true", "HAS_SIDE_PROJECT_CHANGES": "false",
        "SIDE_PROJECT_RESULT": "skipped", "STATIC_ANALYSIS_RESULT": "success",
        "VALIDATE_TEST_RESULT": "success", "ANDROID_LINT_RESULT": "success",
        "KOVER_COVERAGE_RESULT": "success",
    })

    def succeeds(**changes: str) -> bool:
        test_env = {**env, **changes}
        return subprocess.run(["bash", "-c", run], env=test_env,
                              capture_output=True, text=True).returncode == 0

    assert succeeds()
    assert not succeeds(ANALYZE_IMPACT_RESULT="failure")
    assert not succeeds(VALIDATE_TEST_RESULT="skipped")
    assert not succeeds(STATIC_ANALYSIS_RESULT="failure")
    assert not succeeds(HAS_SIDE_PROJECT_CHANGES="true")
    assert not succeeds(HAS_CODE="")
    assert succeeds(HAS_CODE="false", STATIC_ANALYSIS_RESULT="skipped",
                    VALIDATE_TEST_RESULT="skipped", ANDROID_LINT_RESULT="skipped",
                    KOVER_COVERAGE_RESULT="skipped")
    assert not succeeds(HAS_CODE="false", STATIC_ANALYSIS_RESULT="success",
                        VALIDATE_TEST_RESULT="skipped", ANDROID_LINT_RESULT="skipped",
                        KOVER_COVERAGE_RESULT="skipped")


def main() -> int:
    tests = [value for key, value in globals().items() if key.startswith("test_") and callable(value)]
    for test in tests:
        test()
        print("PASS", test.__name__)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
