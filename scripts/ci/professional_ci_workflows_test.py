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
SETUP_PYTHON_SHA = "a26af69be951a213d495a4c3e4e4022e16d87065"


def load(path: str) -> dict:
    value = yaml.safe_load((ROOT / path).read_text(encoding="utf-8"))
    assert isinstance(value, dict)
    return value


def named_step(job: dict, name: str) -> dict:
    return next(step for step in job["steps"] if step.get("name") == name)


def source_block(source: str, marker: str, start: int = 0) -> str:
    marker_index = source.index(marker, start)
    block_start = source.index("{", marker_index)
    depth = 0
    for index in range(block_start, len(source)):
        character = source[index]
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return source[block_start : index + 1]
    raise AssertionError(f"unterminated source block: {marker}")


def test_ci_gate_has_required_jobs() -> None:
    workflow = load(".github/workflows/ci-pr.yml")
    jobs = workflow["jobs"]
    for name in (
        "security-gate",
        "analyze-impact",
        "static-analysis",
        "validate-and-test",
        "kover-coverage",
        "aggregate-gate",
    ):
        assert name in jobs, f"missing job: {name}"


def test_ci_security_gate_uses_full_history_checkout() -> None:
    workflow = load(".github/workflows/ci-pr.yml")
    security = workflow["jobs"]["security-gate"]
    for step in security["steps"]:
        if step.get("uses", "").startswith("actions/checkout@"):
            assert step["with"]["persist-credentials"] is False
            assert step["with"]["fetch-depth"] == 0
            break


def test_ci_quality_jobs_depend_on_impact_analysis() -> None:
    jobs = load(".github/workflows/ci-pr.yml")["jobs"]
    for job_name in ("static-analysis", "validate-and-test", "android-lint"):
        needs = jobs[job_name]["needs"]
        if isinstance(needs, str):
            needs = [needs]
        assert "analyze-impact" in needs


def test_ci_enforces_and_uploads_kover_reports() -> None:
    quality = load(".github/workflows/ci-pr.yml")["jobs"]["kover-coverage"]
    steps = quality["steps"]
    run_commands = "\n".join(step.get("run", "") for step in steps)
    for task in (
        "koverVerifyQuality",
        "koverXmlReportQuality",
        "koverHtmlReportQuality",
        "validate_critical_coverage",
    ):
        assert task in run_commands
    upload = named_step(quality, "Upload coverage reports")
    assert upload["if"] == "always()"
    assert upload["uses"] == f"actions/upload-artifact@{UPLOAD_SHA}"
    assert upload["with"]["retention-days"] == 14


def test_ci_materializes_firebase_configs_for_tests() -> None:
    quality = load(".github/workflows/ci-pr.yml")["jobs"]["validate-and-test"]
    steps = quality["steps"]
    names = [step.get("name") for step in steps]
    assert "Materialize Firebase configs" in names
    generate = named_step(quality, "Materialize Firebase configs")
    assert "materialize_firebase_configs.py" in generate["run"]
    assert names.index("Materialize Firebase configs") < names.index(
        "Run version validation and unit tests"
    )


def test_security_runs_dependency_review_only_for_pull_requests() -> None:
    jobs = load(".github/workflows/security.yml")["jobs"]
    review = jobs["dependency-review"]
    assert review["if"] == "github.event_name == 'pull_request'"
    assert review["permissions"] == {"contents": "read"}
    uses = [
        step["uses"]
        for step in review["steps"]
        if step.get("uses", "").startswith("actions/dependency-review")
    ]
    assert uses == [f"actions/dependency-review-action@{DEPENDENCY_REVIEW_SHA}"]


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


def test_physical_performance_is_manual_and_serial() -> None:
    workflow = load(".github/workflows/physical-performance.yml")
    event = workflow.get("on", workflow.get(True))
    assert set(event) == {"workflow_dispatch"}
    job = workflow["jobs"]["benchmark"]
    assert job["runs-on"] == ["self-hosted", "android-performance"]
    assert "strategy" not in job
    assert job["permissions"] == {"contents": "read"}
    assert "environment" not in job
    runs = "\n".join(step.get("run", "") for step in job["steps"])
    assert "run_physical_performance.sh" in runs
    assert "ro.kernel.qemu" in runs
    assert "emulator-*" in runs
    assert "DOPPLER_TOKEN" not in str(job)
    script = (ROOT / "scripts/ci/run_physical_performance.sh").read_text(
        encoding="utf-8"
    )
    assert "trap cleanup EXIT" in script
    assert "exactly one authorized Android device" in script
    assert "benchmarkIterations" in script
    assert "window_animation_scale" in script
    assert "transition_animation_scale" in script
    assert "animator_duration_scale" in script
    assert 'cd "$repo_root"' in script
    assert "--max-workers=1" in script
    actionlint = load(".github/actionlint.yaml")
    assert "android-performance" in actionlint["self-hosted-runner"]["labels"]


def test_baseline_profiles_workflow_is_full_speed_and_safe() -> None:
    workflow = load(".github/workflows/baseline-profiles.yml")
    event = workflow.get("on", workflow.get(True))
    assert "schedule" in event
    assert "workflow_dispatch" in event
    assert workflow["permissions"] == {"contents": "read"}
    matrix_job = workflow["jobs"]["generate"]
    assert "max-parallel" not in matrix_job["strategy"]
    assert matrix_job["strategy"]["fail-fast"] is False
    assert matrix_job["permissions"] == {"contents": "read"}
    assert matrix_job["needs"] == ["resolve"]
    aggregate = workflow["jobs"]["aggregate"]
    assert aggregate["permissions"] == {"contents": "read"}
    assert aggregate["needs"] == ["resolve", "generate"]
    assert "vars.PERFORMANCE_AUTOMATION_ENABLED == 'true'" in aggregate["if"]
    runs = "\n".join(step.get("run", "") for step in matrix_job["steps"])
    assert "performance_profile_policy.py task" in runs
    assert "generate_ci_google_services.py --clean" in runs
    assert "swiftshader_indirect" in runs
    assert "setup-performance-device-sdk.sh" in runs


def test_managed_device_is_pinned_and_scheduled() -> None:
    workflow = load(".github/workflows/device-smoke.yml")
    event = workflow.get("on", workflow.get(True))
    assert event["schedule"] == [{"cron": "41 2 * * *"}]
    assert "workflow_dispatch" in event

    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    for value in (
        'create("ciPixel2Api30")',
        'device = "Pixel 2"',
        "apiLevel = 30",
        'systemImageSource = "aosp-atd"',
        'testedAbi = "x86_64"',
    ):
        assert value in gradle
    job = workflow["jobs"]["managed-device-smoke"]
    kvm = named_step(job, "Enable KVM acceleration")["run"]
    assert "[[ ! -e /dev/kvm ]]" in kvm
    assert "::error::GitHub runner does not expose /dev/kvm" in kvm
    assert "exit 1" in kvm
    assert "sudo chmod 0666 /dev/kvm" in kvm
    assert "stat --format='%A %a %U:%G %n' /dev/kvm" in kvm
    assert "udevadm trigger" not in kvm
    assert "test -r /dev/kvm" in kvm
    assert "test -w /dev/kvm" in kvm
    command = named_step(job, "Run managed-device smoke tests")["run"]
    assert "ciPixel2Api30Kuran_kerimDebugAndroidTest" in command
    assert "-PciSmoke=true" in command
    assert "swiftshader_indirect" in command
    assert "--no-configuration-cache" in command
    dependency_policy = load("config/dependency-policy.json")
    allowlist = {
        entry["coordinate"]
        for entry in dependency_policy["transitive_prerelease_allowlist"]
    }
    assert "com.google.testing.platform:*" in allowlist
    upload = named_step(job, "Upload managed-device reports")
    assert upload["with"]["retention-days"] == 14
    assert upload["if"] == "always()"


def test_ci_smoke_build_disables_remote_firebase_startup() -> None:
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    assert 'buildConfigField("boolean", "CI_SMOKE", "false")' in gradle
    assert 'gradleProperty("ciSmoke")' in gradle
    assert 'buildConfigField("boolean", "CI_SMOKE", smoke.toString())' in gradle

    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
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
        assert (
            f'android:name="{key}"\n            android:value="${{ciSmokeFirebaseDisabled}}"'
            in manifest
        )
    for key in enabled_metadata:
        assert (
            f'android:name="{key}"\n            android:value="${{ciSmokeFirebaseEnabled}}"'
            in manifest
        )

    app = (ROOT / "app/src/main/java/com/parsfilo/contentapp/App.kt").read_text(
        encoding="utf-8"
    )
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
    assert (
        app.index("return", guard_index, guard_block_end) < first_remote_initialization
    )

    for path in (
        ROOT / "app/src/androidTest/java/com/parsfilo/contentapp/AppLaunchSmokeTest.kt",
        ROOT
        / "app/src/androidTest/java/com/parsfilo/contentapp/SimpleInteractionSmokeTest.kt",
    ):
        assert (
            "androidx.compose.ui.test.junit4.v2.createAndroidComposeRule"
            in path.read_text(encoding="utf-8")
        )


def test_performance_startup_skips_prompts_and_remote_services() -> None:
    activity = (
        ROOT / "app/src/main/java/com/parsfilo/contentapp/MainActivity.kt"
    ).read_text(encoding="utf-8")
    runtime_start = activity.index("private fun startRuntimeServices()")
    runtime_end = activity.index("\n    override fun onNewIntent", runtime_start)
    runtime_block = activity[runtime_start:runtime_end]
    guard_index = runtime_block.index("if (BuildConfig.CI_SMOKE)")
    return_index = runtime_block.index("return", guard_index)
    for remote_call in (
        "observePermissionPrompts()",
        'pushRegistrationManager.syncRegistration("app_start")',
        'pushRegistrationManager.syncRegistration("notification_setting_changed")',
        "adOrchestrator.initialize(this, lifecycleScope)",
    ):
        assert return_index < runtime_block.index(remote_call)

    content_app = (
        ROOT / "app/src/main/java/com/parsfilo/contentapp/ui/ContentApp.kt"
    ).read_text(encoding="utf-8")
    update_effect = content_app.index("LaunchedEffect(Unit)")
    update_guard = source_block(
        content_app, "if (!BuildConfig.CI_SMOKE)", update_effect
    )
    assert "updateGateViewModel.checkForUpdate()" in update_guard

    route_effect = content_app.index("LaunchedEffect(selectedTopLevelRoute)")
    route_guard = source_block(content_app, "if (!BuildConfig.CI_SMOKE)", route_effect)
    assert "appAnalytics.logTabSelected" in route_guard

    view_model = (
        ROOT / "app/src/main/java/com/parsfilo/contentapp/ui/MainViewModel.kt"
    ).read_text(encoding="utf-8")
    init_start = view_model.index("        init {")
    refresh_guard = source_block(view_model, "if (!BuildConfig.CI_SMOKE)", init_start)
    assert "otherAppsRepository.refreshIfNeeded()" in refresh_guard


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
    assert (
        node["with"]["cache-dependency-path"]
        == "side-projects/cloudflare/workers/content-api/package-lock.json"
    )
    release_script = (ROOT / "scripts/ci/build_attested_release.sh").read_text(
        encoding="utf-8"
    )
    assert "restore_firebase_configs.sh" in release_script
    assert "verify_google_signin_config.py" in release_script
    assert "performance_profile_policy.py validate-source" in release_script
    assert (
        "validate${RELEASE_CAPITALIZED}ReleaseBaselineProfileInBundle" in release_script
    )
    assert "bundle${RELEASE_CAPITALIZED}Release" not in release_script
    assert "publish" not in release_script.lower()
    attest = named_step(job, "Attest signed AAB")
    assert attest["uses"] == f"actions/attest@{ATTEST_SHA}"
    prepare = named_step(job, "Prepare AAB and checksum")["run"]
    assert 'artifact_name="$(basename "$artifact")"' in prepare
    assert (
        '(cd dist && sha256sum "$artifact_name" > "${artifact_name}.sha256")' in prepare
    )
    assert 'sha256sum "$artifact" > "${artifact}.sha256"' not in prepare
    upload = named_step(job, "Upload signed AAB and checksum")
    assert upload["with"]["if-no-files-found"] == "error"


def test_ci_aggregate_gate_enforces_all_required_jobs() -> None:
    job = load(".github/workflows/ci-pr.yml")["jobs"]["aggregate-gate"]
    assert job["if"] == "always()"
    assert set(job["needs"]) >= {
        "security-gate",
        "static-analysis",
        "validate-and-test",
        "kover-coverage",
    }
    command = named_step(job, "Check required jobs")["run"]
    assert "security-gate" in command
    assert "static-analysis" in command
    assert "validate-and-test" in command
    assert "kover-coverage" in command



def test_side_project_quality_is_required_in_main_and_pr() -> None:
    for workflow_path in (
        ".github/workflows/ci-main.yml",
        ".github/workflows/ci-pr.yml",
    ):
        jobs = load(workflow_path)["jobs"]
        side_projects = jobs["side-projects"]
        assert side_projects.get("continue-on-error") is not True, workflow_path

        aggregate = jobs["aggregate-gate"]
        assert "side-projects" in aggregate["needs"], workflow_path
        step = named_step(aggregate, "Check required jobs")
        assert step["env"]["NEEDS_JSON"] == "${{ toJSON(needs) }}", workflow_path
        command = step["run"]
        assert "side-projects" in command, workflow_path
        required_loops = [
            line.strip()
            for line in command.splitlines()
            if line.strip().startswith("for job in ")
        ]
        assert any("side-projects" in loop.split() for loop in required_loops), workflow_path


def test_aggregate_gates_reject_unexpected_skips() -> None:
    main_job = load(".github/workflows/ci-main.yml")["jobs"]["aggregate-gate"]
    main_step = named_step(main_job, "Check required jobs")
    assert main_step["env"]["NEEDS_JSON"] == "${{ toJSON(needs) }}"
    main_command = main_step["run"]
    assert 'result" != "success"' in main_command
    assert 'result" != "skipped"' not in main_command

    pr_job = load(".github/workflows/ci-pr.yml")["jobs"]["aggregate-gate"]
    pr_step = named_step(pr_job, "Check required jobs")
    assert pr_step["env"] == {
        "NEEDS_JSON": "${{ toJSON(needs) }}",
        "HAS_CODE": "${{ needs.analyze-impact.outputs.has_code }}",
    }
    command = pr_step["run"]
    for always_required in ("security-gate", "analyze-impact", "repository-security"):
        assert always_required in command
    for code_job in (
        "side-projects",
        "static-analysis",
        "validate-and-test",
        "android-lint",
        "kover-coverage",
    ):
        assert code_job in command
    assert 'if [ "$HAS_CODE" = "true" ]' in command
    assert "unexpectedly skipped" in command


def test_ci_pr_shell_blocks_do_not_embed_github_expressions() -> None:
    jobs = load(".github/workflows/ci-pr.yml")["jobs"]
    for job_name, job in jobs.items():
        for step in job.get("steps", []):
            run = step.get("run", "")
            assert "${{" not in run, f"{job_name}:{step.get('name', '<unnamed>')}"


def test_required_pr_workflows_are_unfiltered_and_connected_tests_fail_closed() -> None:
    ci = load(".github/workflows/ci-pr.yml")
    ci_event = ci.get("on", ci.get(True))
    pull_request = ci_event["pull_request"] or {}
    assert "paths" not in pull_request
    assert "paths-ignore" not in pull_request

    connected = load(".github/workflows/connected-tests.yml")
    connected_event = connected.get("on", connected.get(True))
    assert "pull_request" in connected_event
    connected_pr = connected_event["pull_request"] or {}
    assert "paths" not in connected_pr
    assert "paths-ignore" not in connected_pr

    job = connected["jobs"]["instrumentation-tests"]
    assert job["name"] == "Instrumentation Tests"
    assert job["runs-on"] == "ubuntu-24.04"
    command = named_step(job, "Run managed-device instrumentation tests")["run"]
    assert "ciPixel2Api30Kuran_kerimDebugAndroidTest" in command
    assert "|| true" not in command
    kvm = named_step(job, "Enable KVM acceleration")["run"]
    assert "exit 1" in kvm
    cleanup = named_step(job, "Remove CI-only Firebase placeholder")
    assert cleanup["if"] == "always()"


def test_security_workflow_is_fail_closed() -> None:
    jobs = load(".github/workflows/security.yml")["jobs"]
    required_steps = (
        (jobs["secret-scan"], "Run synthetic leak self-test"),
        (jobs["semgrep"], "Run Semgrep"),
        (jobs["workflow-audit"], "Run actionlint"),
    )
    for job, step_name in required_steps:
        step = named_step(job, step_name)
        assert step.get("continue-on-error") is not True, step_name
        assert "|| true" not in step.get("run", ""), step_name

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
    play_script = (ROOT / "scripts/ci/build_play_internal_release.sh").read_text(
        encoding="utf-8"
    )
    assert "restore_firebase_configs.sh" in play_script
    assert "performance_profile_policy.py validate-source" in play_script
    assert "validate${RELEASE_CAPITALIZED}ReleaseBaselineProfileInBundle" in play_script
    assert "bundle${RELEASE_CAPITALIZED}Release" not in play_script
    attest = named_step(job, "Attest exact signed AAB")
    assert attest["uses"] == f"actions/attest@{ATTEST_SHA}"
    publish = named_step(job, "Publish exact attested AAB to Play internal")
    assert "publish_play_internal.py" in publish["run"]
    assert "--track internal" in publish["run"]
    assert publish["env"]["AAB_PATH"] == "${{ steps.artifact.outputs.subject_path }}"
    assert publish["env"]["DOPPLER_TOKEN"] == "${{ secrets.DOPPLER_TOKEN }}"
    prepare = named_step(job, "Prepare exact AAB and checksum")["run"]
    assert 'artifact_name="$(basename "$artifact")"' in prepare
    assert (
        '(cd dist && sha256sum "$artifact_name" > "${artifact_name}.sha256")' in prepare
    )
    assert 'sha256sum "$artifact" > "${artifact}.sha256"' not in prepare
    upload = named_step(job, "Upload AAB, checksum, and publication report")
    assert upload["uses"] == f"actions/upload-artifact@{UPLOAD_SHA}"
    assert upload["if"] == "always()"


def main() -> int:
    tests = [
        test_ci_gate_has_required_jobs,
        test_ci_security_gate_uses_full_history_checkout,
        test_ci_quality_jobs_depend_on_impact_analysis,
        test_ci_enforces_and_uploads_kover_reports,
        test_ci_materializes_firebase_configs_for_tests,
        test_security_runs_dependency_review_only_for_pull_requests,
        test_dependency_submission_is_trusted_and_job_scoped,
        test_codeql_uses_manual_kotlin_build_and_cleans_placeholder,
        test_baseline_profiles_workflow_is_full_speed_and_safe,
        test_physical_performance_is_manual_and_serial,
        test_managed_device_is_pinned_and_scheduled,
        test_ci_smoke_build_disables_remote_firebase_startup,
        test_performance_startup_skips_prompts_and_remote_services,
        test_release_is_manual_protected_and_attested,
        test_ci_aggregate_gate_enforces_all_required_jobs,
        test_side_project_quality_is_required_in_main_and_pr,
        test_aggregate_gates_reject_unexpected_skips,
        test_ci_pr_shell_blocks_do_not_embed_github_expressions,
        test_required_pr_workflows_are_unfiltered_and_connected_tests_fail_closed,
        test_security_workflow_is_fail_closed,
        test_play_internal_builds_attests_and_publishes_one_exact_aab,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
