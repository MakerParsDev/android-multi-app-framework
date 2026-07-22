#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
UPLOAD_SHA = "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"
DEPENDENCY_REVIEW_SHA = "a1d282b36b6f3519aa1f3fc636f609c47dddb294"
GRADLE_ACTIONS_SHA = "3f131e8634966bd73d06cc69884922b02e6faf92"
CODEQL_SHA = "e0647621c2984b5ed2f768cb892365bf2a616ad1"
ATTEST_SHA = "f7c74d28b9d84cb8768d0b8ca14a4bac6ef463e6"


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


def test_security_runs_dependency_review_only_for_pull_requests() -> None:
    jobs = load(".github/workflows/security.yml")["jobs"]
    review = jobs["dependency-review"]
    assert review["if"] == "github.event_name == 'pull_request'"
    assert review["permissions"] == {"contents": "read"}
    step = named_step(review, "Review dependency changes")
    assert step["uses"] == f"actions/dependency-review-action@{DEPENDENCY_REVIEW_SHA}"
    assert step["with"] == {
        "fail-on-severity": "high",
        "show-openssf-scorecard": True,
        "show-patched-versions": True,
    }


def test_dependency_submission_is_trusted_and_job_scoped() -> None:
    workflow = load(".github/workflows/dependency-submission.yml")
    assert workflow["permissions"] == {"contents": "read"}
    job = workflow["jobs"]["submit-gradle-dependencies"]
    assert job["permissions"] == {"contents": "write"}
    step = named_step(job, "Submit Gradle dependency graph")
    assert step["uses"] == f"gradle/actions/dependency-submission@{GRADLE_ACTIONS_SHA}"
    assert step["with"]["dependency-graph"] == "generate-and-submit"
    assert step["with"]["cache-read-only"] is True


def test_codeql_uses_manual_kotlin_build_and_cleans_placeholder() -> None:
    workflow = load(".github/workflows/codeql.yml")
    job = workflow["jobs"]["analyze-java-kotlin"]
    assert job["permissions"] == {"contents": "read", "security-events": "write"}
    assert "dependabot[bot]" in job["if"]
    init = named_step(job, "Initialize CodeQL")
    assert init["uses"] == f"github/codeql-action/init@{CODEQL_SHA}"
    assert init["with"] == {
        "languages": "java-kotlin",
        "build-mode": "manual",
        "queries": "security-extended",
    }
    build = named_step(job, "Build representative flavor for CodeQL")
    assert "assembleKuran_kerimDebug" in build["run"]
    cleanup = named_step(job, "Remove CI-only Firebase placeholder")
    assert cleanup["if"] == "always()"
    analyze = named_step(job, "Analyze Java and Kotlin")
    assert analyze["uses"] == f"github/codeql-action/analyze@{CODEQL_SHA}"


def test_managed_device_is_pinned_and_scheduled() -> None:
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    for value in (
        'create("ciPixel2Api30")',
        'device = "Pixel 2"',
        'apiLevel = 30',
        'systemImageSource = "aosp-atd"',
    ):
        assert value in gradle
    workflow = load(".github/workflows/device-smoke.yml")
    job = workflow["jobs"]["managed-device-smoke"]
    command = named_step(job, "Run managed-device smoke tests")["run"]
    assert "ciPixel2Api30Kuran_kerimDebugAndroidTest" in command
    assert "swiftshader_indirect" in command
    upload = named_step(job, "Upload managed-device reports")
    assert upload["with"]["retention-days"] == 14
    assert upload["if"] == "always()"


def test_release_is_manual_protected_and_attested() -> None:
    workflow = load(".github/workflows/release-attested.yml")
    event = workflow.get("on", workflow.get(True))
    assert list(event) == ["workflow_dispatch"]
    job = workflow["jobs"]["build-attest-release"]
    assert job["environment"] == "production"
    assert job["permissions"] == {
        "contents": "read",
        "id-token": "write",
        "attestations": "write",
        "artifact-metadata": "write",
    }
    build = named_step(job, "Build signed AAB with Doppler")
    assert "scripts/doppler-run.sh" in build["run"]
    assert "scripts/ci/build_attested_release.sh" in build["run"]
    release_script = (ROOT / "scripts/ci/build_attested_release.sh").read_text(encoding="utf-8")
    assert "materialize_firebase_configs.py" in release_script
    assert "verify_google_signin_config.py" in release_script
    assert "bundle${RELEASE_CAPITALIZED}Release" in release_script
    assert "publish" not in release_script.lower()
    attest = named_step(job, "Attest signed AAB")
    assert attest["uses"] == f"actions/attest@{ATTEST_SHA}"
    upload = named_step(job, "Upload signed AAB and checksum")
    assert upload["with"]["if-no-files-found"] == "error"


def main() -> int:
    tests = [
        test_ci_runs_quality_and_flavors_in_parallel,
        test_ci_enforces_and_uploads_kover_reports,
        test_security_runs_dependency_review_only_for_pull_requests,
        test_dependency_submission_is_trusted_and_job_scoped,
        test_codeql_uses_manual_kotlin_build_and_cleans_placeholder,
        test_managed_device_is_pinned_and_scheduled,
        test_release_is_manual_protected_and_attested,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
