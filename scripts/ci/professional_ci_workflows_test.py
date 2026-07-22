#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
UPLOAD_SHA = "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"


def load(path: str) -> dict:
    value = yaml.safe_load((ROOT / path).read_text(encoding="utf-8"))
    assert isinstance(value, dict)
    return value


def named_step(job: dict, name: str) -> dict:
    return next(step for step in job["steps"] if step.get("name") == name)


def test_ci_runs_quality_and_flavors_in_parallel() -> None:
    jobs = load(".github/workflows/ci-pr.yml")["jobs"]
    assert jobs["android-quality"]["needs"] == ["workflow-policy", "repository-security"]
    assert jobs["app-builds"]["needs"] == [
        "workflow-policy", "repository-security", "resolve-apps"
    ]
    assert "max-parallel" not in jobs["app-builds"]["strategy"]


def test_ci_enforces_and_uploads_kover_reports() -> None:
    quality = load(".github/workflows/ci-pr.yml")["jobs"]["android-quality"]
    coverage = named_step(quality, "Run Kover quality gate")
    command = coverage["run"]
    for task in (
        "koverVerifyQuality",
        "koverXmlReportQuality",
        "koverHtmlReportQuality",
        "validateCriticalCoverage",
    ):
        assert task in command
    upload = named_step(quality, "Upload quality reports")
    assert upload["if"] == "always()"
    assert upload["uses"] == f"actions/upload-artifact@{UPLOAD_SHA}"
    assert upload["with"]["retention-days"] == 14
    assert upload["with"]["if-no-files-found"] == "warn"


def main() -> int:
    tests = [
        test_ci_runs_quality_and_flavors_in_parallel,
        test_ci_enforces_and_uploads_kover_reports,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
