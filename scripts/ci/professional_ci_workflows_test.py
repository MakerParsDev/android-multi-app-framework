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
SETUP_NODE_SHA = "820762786026740c76f36085b0efc47a31fe5020"


def load(path: str) -> dict:
    value = yaml.safe_load((ROOT / path).read_text(encoding="utf-8"))
    assert isinstance(value, dict)
    return value


def named_step(job: dict, name: str) -> dict:
    return next(step for step in job["steps"] if step.get("name") == name)


def test_ci_gate_runs_professional_workflow_assertions() -> None:
    workflow = load(".github/workflows/ci-pr.yml")
    step = named_step(workflow["jobs"]["workflow-policy"], "Test approved GitHub action pins")
    assert "professional_ci_workflows_test.py" in step["run"]


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


def test_android_quality_materializes_all_firebase_placeholders_for_kover() -> None:
    quality = load(".github/workflows/ci-pr.yml")["jobs"]["android-quality"]
    steps = quality["steps"]
    names = [step.get("name") for step in steps]
    generate = named_step(quality, "Generate CI-only Firebase placeholders")
    cleanup = named_step(quality, "Remove CI-only Firebase placeholders")
    assert "--flavors all" in generate["run"]
    assert cleanup["if"] == "always()"
    assert "--clean --flavors all" in cleanup["run"]
    assert names.index("Generate CI-only Firebase placeholders") < names.index("Run Kover quality gate")
    assert names.index("Run Kover quality gate") < names.index("Remove CI-only Firebase placeholders")
    assert names.index("Remove CI-only Firebase placeholders") < names.index("Upload quality reports")


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
    workflow = load(".github/workflows/device-smoke.yml")
    event = workflow.get("on", workflow.get(True))
    assert event["schedule"] == [{"cron": "41 2 * * *"}]
    assert "workflow_dispatch" in event

    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    for value in (
        'create("ciPixel2Api30")',
        'device = "Pixel 2"',
        'apiLevel = 30',
        'systemImageSource = "aosp-atd"',
        'testedAbi = "x86_64"',
    ):
        assert value in gradle
    job = workflow["jobs"]["managed-device-smoke"]
    kvm = named_step(job, "Enable KVM acceleration")["run"]
    assert "99-kvm4all.rules" in kvm
    assert "test -w /dev/kvm" in kvm
    command = named_step(job, "Run managed-device smoke tests")["run"]
    assert "ciPixel2Api30Kuran_kerimDebugAndroidTest" in command
    assert "-PciSmoke=true" in command
    assert "swiftshader_indirect" in command
    assert "--no-configuration-cache" in command
    dependency_policy = load("config/dependency-policy.json")
    allowlist = {entry["coordinate"] for entry in dependency_policy["transitive_prerelease_allowlist"]}
    assert "com.google.testing.platform:*" in allowlist
    upload = named_step(job, "Upload managed-device reports")
    assert upload["with"]["retention-days"] == 14
    assert upload["if"] == "always()"


def test_ci_smoke_build_disables_remote_firebase_startup() -> None:
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    assert 'buildConfigField("boolean", "CI_SMOKE", "false")' in gradle
    assert 'gradleProperty("ciSmoke")' in gradle
    assert 'buildConfigField("boolean", "CI_SMOKE", smoke.toString())' in gradle

    manifest = (ROOT / "app/src/debug/AndroidManifest.xml").read_text(encoding="utf-8")
    disabled_metadata = (
        "firebase_performance_collection_deactivated",
        "firebase_analytics_collection_deactivated",
    )
    enabled_metadata = (
        "firebase_crashlytics_collection_enabled",
        "firebase_messaging_auto_init_enabled",
        "firebase_data_collection_default_enabled",
    )
    for key in disabled_metadata:
        assert f'android:name="{key}"\n            android:value="${{ciSmokeFirebaseDisabled}}"' in manifest
    for key in enabled_metadata:
        assert f'android:name="{key}"\n            android:value="${{ciSmokeFirebaseEnabled}}"' in manifest

    app = (ROOT / "app/src/main/java/com/parsfilo/contentapp/App.kt").read_text(encoding="utf-8")
    guard_index = app.index("if (BuildConfig.CI_SMOKE)")
    guard_block_end = app.index("        }", guard_index)
    guard_block = app[guard_index:guard_block_end]
    assert "CI smoke startup complete" in guard_block
    assert "return" in guard_block
    first_remote_initialization = min(
        app.index("appAnalytics.setAnalyticsCollectionEnabled"),
        app.index("FirebaseCrashlytics.getInstance"),
        app.index("runtimeObservability.configure"),
        app.index("appCheckInstaller.install"),
    )
    assert guard_index < first_remote_initialization
    assert app.index("return", guard_index, guard_block_end) < first_remote_initialization

    for path in (
        ROOT / "app/src/androidTest/java/com/parsfilo/contentapp/AppLaunchSmokeTest.kt",
        ROOT / "app/src/androidTest/java/com/parsfilo/contentapp/SimpleInteractionSmokeTest.kt",
    ):
        assert "androidx.compose.ui.test.junit4.v2.createAndroidComposeRule" in path.read_text(encoding="utf-8")


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
    }
    assert "DOPPLER_TOKEN" not in job.get("env", {})
    validate = named_step(job, "Validate Doppler bootstrap")
    assert validate["env"]["DOPPLER_TOKEN"] == "${{ secrets.DOPPLER_TOKEN }}"
    build = named_step(job, "Build signed AAB with Doppler")
    assert build["env"]["DOPPLER_TOKEN"] == "${{ secrets.DOPPLER_TOKEN }}"
    assert "scripts/doppler-run.sh" in build["run"]
    assert "scripts/ci/build_attested_release.sh" in build["run"]
    node = named_step(job, "Set up Node 24")
    assert node["uses"] == f"actions/setup-node@{SETUP_NODE_SHA}"
    assert node["with"]["node-version"] == "24.18.0"
    assert node["with"]["cache-dependency-path"] == "side-projects/cloudflare/workers/content-api/package-lock.json"
    release_script = (ROOT / "scripts/ci/build_attested_release.sh").read_text(encoding="utf-8")
    assert "restore_firebase_configs.sh" in release_script
    assert "verify_google_signin_config.py" in release_script
    assert "bundle${RELEASE_CAPITALIZED}Release" in release_script
    assert "publish" not in release_script.lower()
    attest = named_step(job, "Attest signed AAB")
    assert attest["uses"] == f"actions/attest@{ATTEST_SHA}"
    upload = named_step(job, "Upload signed AAB and checksum")
    assert upload["with"]["if-no-files-found"] == "error"



def test_ci_exposes_one_required_aggregate_check() -> None:
    job = load(".github/workflows/ci-pr.yml")["jobs"]["ci-required"]
    assert job["name"] == "CI Required"
    assert job["if"] == "always()"
    assert job["needs"] == [
        "workflow-policy",
        "repository-security",
        "android-quality",
        "resolve-apps",
        "app-builds",
        "dependabot-smoke",
    ]
    command = named_step(job, "Enforce aggregate CI result")["run"]
    assert "WORKFLOW_POLICY_RESULT" in command
    assert "APP_BUILDS_RESULT" in command
    assert "DEPENDABOT_SMOKE_RESULT" in command


def test_play_internal_builds_attests_and_publishes_one_exact_aab() -> None:
    workflow = load(".github/workflows/play-internal.yml")
    event = workflow.get("on", workflow.get(True))
    assert list(event) == ["workflow_dispatch"]
    job = workflow["jobs"]["publish-internal"]
    assert job["environment"] == "production"
    assert job["permissions"] == {
        "contents": "read",
        "id-token": "write",
        "attestations": "write",
    }
    node = named_step(job, "Set up Node 24")
    assert node["uses"] == f"actions/setup-node@{SETUP_NODE_SHA}"
    assert node["with"]["node-version"] == "24.18.0"
    dependencies = named_step(job, "Install pinned Play publisher dependencies")
    assert "--require-hashes" in dependencies["run"]
    assert "requirements-play-publisher.lock" in dependencies["run"]
    build = named_step(job, "Build one signed AAB with next Play version code")
    assert "scripts/doppler-run.sh" in build["run"]
    assert "build_play_internal_release.sh" in build["run"]
    play_script = (ROOT / "scripts/ci/build_play_internal_release.sh").read_text(encoding="utf-8")
    assert "restore_firebase_configs.sh" in play_script
    attest = named_step(job, "Attest exact signed AAB")
    assert attest["uses"] == f"actions/attest@{ATTEST_SHA}"
    publish = named_step(job, "Publish exact attested AAB to Play internal")
    assert "publish_play_internal.py" in publish["run"]
    assert "--track internal" in publish["run"]
    assert publish["env"]["AAB_PATH"] == "${{ steps.artifact.outputs.subject_path }}"
    assert publish["env"]["DOPPLER_TOKEN"] == "${{ secrets.DOPPLER_TOKEN }}"
    upload = named_step(job, "Upload AAB, checksum, and publication report")
    assert upload["uses"] == f"actions/upload-artifact@{UPLOAD_SHA}"
    assert upload["if"] == "always()"


def main() -> int:
    tests = [
        test_ci_gate_runs_professional_workflow_assertions,
        test_ci_runs_quality_and_flavors_in_parallel,
        test_ci_enforces_and_uploads_kover_reports,
        test_android_quality_materializes_all_firebase_placeholders_for_kover,
        test_security_runs_dependency_review_only_for_pull_requests,
        test_dependency_submission_is_trusted_and_job_scoped,
        test_codeql_uses_manual_kotlin_build_and_cleans_placeholder,
        test_managed_device_is_pinned_and_scheduled,
        test_ci_smoke_build_disables_remote_firebase_startup,
        test_release_is_manual_protected_and_attested,
        test_ci_exposes_one_required_aggregate_check,
        test_play_internal_builds_attests_and_publishes_one_exact_aab,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
