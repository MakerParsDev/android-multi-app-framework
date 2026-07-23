from __future__ import annotations

import json

from pathlib import Path
import unittest

import yaml

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
CI_PIPELINE = ROOT / ".github/workflows/ci-pr.yml"
FULL_PIPELINE = ROOT / ".github/workflows/ci-main.yml"
RELEASE_PIPELINE = ROOT / ".github/workflows/release.yml"
ROOT_BUILD = ROOT / "build.gradle.kts"
NPM_RUN_LINT = "npm run lint"
NPM_TEST = "npm test"


class SideProjectQualityContractTest(unittest.TestCase):
    def test_all_five_projects_have_locked_blocking_quality_contracts(self) -> None:
        for project, package_path in PACKAGES.items():
            self.assertTrue(
                package_path.with_name("package-lock.json").is_file(), project
            )
            scripts = json.loads(package_path.read_text(encoding="utf-8"))["scripts"]
            for script in ("lint", "test", "verify", "predeploy", "deploy"):
                self.assertIn(script, scripts, f"{project}:{script}")
            self.assertIn(NPM_RUN_LINT, scripts["verify"], project)
            self.assertIn(NPM_TEST, scripts["verify"], project)
            self.assertIn(
                "deploy_verified_side_project.mjs", scripts["deploy"], project
            )

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

    def test_security_overrides_pin_patched_transitive_dependencies(self) -> None:
        wrangler_packages = (
            ROOT / "side-projects/admin-notifications/package.json",
            ROOT / "side-projects/cloudflare/workers/admin-api/package.json",
            ROOT / "side-projects/cloudflare/workers/content-api/package.json",
            ROOT / "side-projects/cloudflare/workers/ssv-callback/package.json",
        )
        for package_path in wrangler_packages:
            package = json.loads(package_path.read_text(encoding="utf-8"))
            self.assertEqual("4.113.0", package["devDependencies"]["wrangler"])
            self.assertEqual("0.35.3", package["overrides"]["sharp"])

            lock = json.loads(
                package_path.with_name("package-lock.json").read_text(encoding="utf-8")
            )
            self.assertEqual(
                "0.35.3",
                lock["packages"]["node_modules/sharp"]["version"],
            )

        rules_path = ROOT / "side-projects/firebase/rules-tests/package.json"
        rules = json.loads(rules_path.read_text(encoding="utf-8"))
        self.assertEqual("2.0.11", rules["overrides"]["@hono/node-server"])
        rules_lock = json.loads(
            rules_path.with_name("package-lock.json").read_text(encoding="utf-8")
        )
        self.assertEqual(
            "2.0.11",
            rules_lock["packages"]["node_modules/@hono/node-server"]["version"],
        )

    def test_unexpanded_azure_health_placeholder_is_treated_as_unconfigured(
        self,
    ) -> None:
        text = RUNNER.read_text(encoding="utf-8")
        self.assertIn("== '$('*", text)
        self.assertNotIn(r"== '\$('*", text)

    def test_ci_and_full_verification_use_the_central_runner(self) -> None:
        for path in (
            CI_PIPELINE,
            FULL_PIPELINE,
        ):
            text = path.read_text(encoding="utf-8")
            self.assertIn("run_side_project_quality.sh", text, path.as_posix())
        ci = CI_PIPELINE.read_text(encoding="utf-8")
        self.assertNotIn("npm --prefix side-projects/cloudflare/workers/admin-api", ci)
        self.assertNotIn("npm --prefix side-projects/firebase/rules-tests", ci)

    def test_release_publish_requires_quality_and_deploy_scripts_self_gate(
        self,
    ) -> None:
        pipeline = RELEASE_PIPELINE.read_text(encoding="utf-8")
        self.assertRegex(pipeline, r"do_quality:[\s\S]*default: true")
        self.assertIn("run_side_project_quality.sh", pipeline)

    def test_release_publish_requires_quality_input(self) -> None:
        workflow = yaml.safe_load(RELEASE_PIPELINE.read_text(encoding="utf-8"))
        publish_condition = workflow["jobs"]["publish-play"]["if"]
        self.assertIn("inputs.do_quality", publish_condition)
        self.assertIn("inputs.do_internal_test", publish_condition)
        self.assertIn("inputs.do_publish", publish_condition)

    def test_deploy_requires_same_commit_artifact_and_strict_drift_smoke(self) -> None:
        deploy = (ROOT / "scripts/ci/deploy_verified_side_project.mjs").read_text(
            encoding="utf-8"
        )
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
