from __future__ import annotations

import json
import os
import shutil
# Executes a fixed local test command without a shell.
import subprocess  # nosec B404
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
PACKAGES = {
    "admin-notifications": ROOT / "side-projects/admin-notifications/package.json",
    "admin-api": ROOT / "side-projects/cloudflare/workers/admin-api/package.json",
    "content-api": ROOT / "side-projects/cloudflare/workers/content-api/package.json",
    "ssv-callback": ROOT / "side-projects/cloudflare/workers/ssv-callback/package.json",
    "firebase-functions": ROOT / "side-projects/firebase/functions/package.json",
}
ADMIN = PACKAGES["admin-api"]
FUNCTIONS = PACKAGES["firebase-functions"]
FIREBASE_JSON = ROOT / "side-projects/firebase/firebase.json"
RUNNER = ROOT / "scripts/ci/run_side_project_quality.sh"
AUDIT_POLICY = ROOT / "side-projects/audit-policy.json"
AUDIT_VALIDATOR = ROOT / "scripts/ci/validate_side_project_audits.py"
CI_PIPELINE = ROOT / "azure-pipelines/ci.yml"
FULL_PIPELINE = ROOT / "azure-pipelines/full-verification.yml"
RELEASE_PIPELINE = ROOT / "azure-pipelines/release.yml"
LEGACY_PR_PIPELINE = ROOT / "pipelines/azure-pipelines.yml"
MANUAL_PIPELINE = ROOT / "pipelines/azure-pipelines-manual.yml"
LEGACY_RELEASE_PIPELINE = ROOT / "pipelines/azure-pipelines-release.yml"
QUALITY_SCRIPT = ROOT / "scripts/azure/quality.sh"
RELEASE_SCRIPT = ROOT / "scripts/azure/release.sh"
ROOT_BUILD = ROOT / "build.gradle.kts"
NPM_RUN_LINT = "npm run lint"
NPM_TEST = "npm test"


class SideProjectQualityContractTest(unittest.TestCase):
    def test_all_five_projects_have_locked_blocking_quality_contracts(self) -> None:
        for project, package_path in PACKAGES.items():
            self.assertTrue(package_path.with_name("package-lock.json").is_file(), project)
            scripts = json.loads(package_path.read_text(encoding="utf-8"))["scripts"]
            for script in ("lint", "test", "verify", "predeploy", "deploy"):
                self.assertIn(script, scripts, f"{project}:{script}")
            self.assertIn(NPM_RUN_LINT, scripts["verify"], project)
            self.assertIn(NPM_TEST, scripts["verify"], project)
            self.assertIn("deploy_verified_side_project.mjs", scripts["deploy"], project)

    def test_admin_worker_has_compile_lint_test_and_predeploy_gate(self) -> None:
        scripts = json.loads(ADMIN.read_text(encoding="utf-8"))["scripts"]
        self.assertIn("check", scripts)
        self.assertIn("lint", scripts)
        self.assertIn("test", scripts)
        self.assertIn("npm run check", scripts["verify"])
        self.assertIn(NPM_RUN_LINT, scripts["verify"])
        self.assertIn(NPM_TEST, scripts["verify"])
        self.assertEqual("npm run verify", scripts["predeploy"])

    def test_firebase_functions_has_compile_lint_test_and_predeploy_gate(self) -> None:
        scripts = json.loads(FUNCTIONS.read_text(encoding="utf-8"))["scripts"]
        self.assertIn("typecheck", scripts)
        self.assertIn("lint", scripts)
        self.assertIn("test", scripts)
        self.assertIn("npm run typecheck", scripts["verify"])
        self.assertIn(NPM_RUN_LINT, scripts["verify"])
        self.assertIn(NPM_TEST, scripts["verify"])
        self.assertEqual("npm run verify", scripts["predeploy"])

        firebase = json.loads(FIREBASE_JSON.read_text(encoding="utf-8"))
        predeploy = firebase["functions"][0]["predeploy"]
        self.assertTrue(any("run verify" in command for command in predeploy))

    def test_central_runner_covers_all_required_side_project_checks(self) -> None:
        text = RUNNER.read_text(encoding="utf-8")
        for required in (
            "side-projects/admin-notifications",
            "side-projects/cloudflare/workers/admin-api",
            "side-projects/cloudflare/workers/content-api",
            "side-projects/cloudflare/workers/ssv-callback",
            "side-projects/firebase/functions",
            "side-projects/firebase/rules-tests",
            "npm --prefix side-projects/admin-notifications run verify",
            "npm --prefix side-projects/cloudflare/workers/admin-api run verify",
            "npm --prefix side-projects/cloudflare/workers/content-api run verify",
            "npm --prefix side-projects/cloudflare/workers/ssv-callback run verify",
            "npm --prefix side-projects/firebase/functions run verify",
            "npm --prefix side-projects/firebase/rules-tests test",
            "python3 scripts/ci/validate_side_project_audits.py",
            "side-projects/audit-policy.json",
            "python3 -m unittest discover -s scripts/ci -p '*_test.py'",
        ):
            self.assertIn(required, text)


    def test_audit_policy_is_blocking_owned_and_expiring(self) -> None:
        policy = json.loads(AUDIT_POLICY.read_text(encoding="utf-8"))
        self.assertEqual("zero-vulnerabilities", policy["productionPolicy"])
        self.assertGreater(len(policy["exceptions"]), 0)
        for entry in policy["exceptions"]:
            self.assertEqual("development", entry["scope"])
            self.assertIn(entry["severity"], {"low", "moderate"})
            self.assertTrue(entry["owner"])
            self.assertRegex(entry["trackingIssue"], r"#\d+")
            self.assertTrue(entry["expiresOn"])
        validator = AUDIT_VALIDATOR.read_text(encoding="utf-8")
        self.assertIn("production npm audit must be zero", validator)
        self.assertIn("BLOCKED_DEV_SEVERITIES", validator)
        self.assertNotIn("audit fix --force", RUNNER.read_text(encoding="utf-8"))

    def test_unexpanded_azure_health_placeholder_is_treated_as_unconfigured(self) -> None:
        text = RUNNER.read_text(encoding="utf-8")
        self.assertIn("== '$('*", text)
        self.assertNotIn(r"== '\$('*", text)

    def test_ci_and_full_verification_use_the_central_runner(self) -> None:
        for path in (
            CI_PIPELINE,
            FULL_PIPELINE,
            QUALITY_SCRIPT,
            LEGACY_PR_PIPELINE,
            MANUAL_PIPELINE,
            LEGACY_RELEASE_PIPELINE,
        ):
            text = path.read_text(encoding="utf-8")
            self.assertIn("run_side_project_quality.sh", text, path.as_posix())
        ci = CI_PIPELINE.read_text(encoding="utf-8")
        self.assertNotIn("npm --prefix side-projects/cloudflare/workers/admin-api", ci)
        self.assertNotIn("npm --prefix side-projects/firebase/rules-tests", ci)
        legacy_pr = LEGACY_PR_PIPELINE.read_text(encoding="utf-8")
        self.assertIn("side-projects/*", legacy_pr)
        self.assertIn("job: SideProjectQuality", legacy_pr)

    def test_release_publish_requires_quality_and_deploy_scripts_self_gate(self) -> None:
        release = RELEASE_SCRIPT.read_text(encoding="utf-8")
        self.assertIn("Publishing requires DO_QUALITY=true", release)
        self.assertIn("run_side_project_quality.sh", release)

        pipeline = RELEASE_PIPELINE.read_text(encoding="utf-8")
        self.assertRegex(pipeline, r"name: doQuality[\s\S]*default: true")

    def test_release_script_rejects_publish_when_quality_is_disabled(self) -> None:
        env = os.environ.copy()
        env.update(
            {
                "RESOLVED_FLAVORS_CSV": "zikirmatik",
                "BUILD_TYPE": "Release",
                "DO_BUILD": "true",
                "DO_PUBLISH": "true",
                "DO_INTERNAL_TEST": "false",
                "DO_QUALITY": "false",
            }
        )
        bash = shutil.which("bash")
        if bash is None:
            self.fail("bash executable was not found")
        # bash is resolved locally and all arguments are fixed test inputs.
        result = subprocess.run(  # nosec B603
            [bash, str(RELEASE_SCRIPT)],
            cwd=ROOT,
            env=env,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Publishing requires DO_QUALITY=true", result.stderr)

    def test_deploy_requires_same_commit_artifact_and_strict_drift_smoke(self) -> None:
        deploy = (ROOT / "scripts/ci/deploy_verified_side_project.mjs").read_text(encoding="utf-8")
        self.assertIn("report.gitSha !== sha", deploy)
        self.assertIn("older than six hours", deploy)
        self.assertIn("Refusing deployment from a dirty worktree", deploy)
        self.assertIn("if (result.error) throw result.error", deploy)
        self.assertIn('"--mode", "strict"', deploy)
        self.assertIn("check_side_project_deployment_drift.py", deploy)

    def test_health_and_build_metadata_are_traceable(self) -> None:
        sources = [
            ROOT / "side-projects/cloudflare/workers/admin-api/src/health.ts",
            ROOT / "side-projects/cloudflare/workers/content-api/src/index.ts",
            ROOT / "side-projects/cloudflare/workers/ssv-callback/src/index.ts",
            ROOT / "side-projects/firebase/functions/src/healthCheck.ts",
            ROOT / "side-projects/admin-notifications/vite.config.ts",
        ]
        for source in sources:
            text = source.read_text(encoding="utf-8")
            self.assertIn("gitSha", text, source.as_posix())
            self.assertIn("builtAt", text, source.as_posix())

    def test_gradle_static_gate_validates_side_project_contract(self) -> None:
        text = ROOT_BUILD.read_text(encoding="utf-8")
        self.assertIn("validateSideProjectQualityContract", text)
        self.assertIn("side_project_quality_contract_test.py", text)


if __name__ == "__main__":
    unittest.main()
