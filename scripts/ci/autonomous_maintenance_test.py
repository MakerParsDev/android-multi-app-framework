#!/usr/bin/env python3
"""Contract and drift tests for the autonomous maintenance control plane."""

from __future__ import annotations

import json
from pathlib import Path
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]


class AutonomousMaintenanceContractTest(unittest.TestCase):
    def test_mergify_configuration_contract(self) -> None:
        mergify_path = ROOT / ".mergify.yml"
        self.assertTrue(mergify_path.is_file(), ".mergify.yml must exist at repository root")

        content = mergify_path.read_text(encoding="utf-8")
        self.assertNotIn("autoqueue", content, "Deprecated autoqueue key must not be used in .mergify.yml")

        data = yaml.safe_load(content)
        self.assertIn("merge_protections", data, ".mergify.yml must define merge_protections")
        self.assertIn("merge_protections_settings", data, ".mergify.yml must define merge_protections_settings")
        self.assertIn("queue_rules", data, ".mergify.yml must define queue_rules")

        auto_merge_conditions = data["merge_protections_settings"].get("auto_merge_conditions")
        self.assertNotEqual(
            auto_merge_conditions,
            True,
            "auto_merge_conditions must not be unconditionally set to true",
        )
        self.assertIsInstance(
            auto_merge_conditions,
            list,
            "auto_merge_conditions must be a restricted list of condition rules",
        )

        # Verify semver-major is blocked
        mergify_str = yaml.dump(auto_merge_conditions)
        self.assertIn("dependabot-update-type != version-update:semver-major", mergify_str)

        # Verify circuit breaker label is respected
        self.assertIn("-label = automerge:disabled", mergify_str)

    def test_dependabot_manifest_coverage_contract(self) -> None:
        dependabot_path = ROOT / ".github/dependabot.yml"
        self.assertTrue(dependabot_path.is_file(), ".github/dependabot.yml must exist")

        data = yaml.safe_load(dependabot_path.read_text(encoding="utf-8"))
        self.assertEqual(data.get("version"), 2)

        updates = data.get("updates", [])
        self.assertGreaterEqual(len(updates), 9, "Dependabot must cover all monorepo ecosystems and side projects")

        ecosystem_dirs = {(u["package-ecosystem"], u["directory"]) for u in updates}

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
        for exp in expected:
            self.assertIn(exp, ecosystem_dirs, f"Missing Dependabot coverage for {exp}")

        for u in updates:
            self.assertEqual(
                u.get("rebase-strategy"),
                "disabled",
                f"rebase-strategy must be disabled for Mergify queue compatibility in {u.get('directory')}",
            )

    def test_no_competing_renovate_configuration_exists(self) -> None:
        forbidden = [
            ROOT / "renovate.json",
            ROOT / "renovate.json5",
            ROOT / ".github/renovate.json",
            ROOT / ".github/renovate.json5",
            ROOT / ".renovaterc",
            ROOT / ".renovaterc.json",
        ]
        for path in forbidden:
            self.assertFalse(path.exists(), f"Competing Renovate config must not exist: {path}")

    def test_codecov_action_pin_and_non_blocking_policy(self) -> None:
        pinned_path = ROOT / "config/pinned-github-actions.json"
        manifest = json.loads(pinned_path.read_text(encoding="utf-8"))
        self.assertIn("codecov/codecov-action", manifest, "codecov/codecov-action must be pinned in manifest")
        self.assertEqual(
            manifest["codecov/codecov-action"]["sha"],
            "0fb7174895f61a3b6b78fc075e0cd60383518dac",
        )

        codecov_yml = ROOT / "codecov.yml"
        self.assertTrue(codecov_yml.is_file(), "codecov.yml must exist")
        cc_data = yaml.safe_load(codecov_yml.read_text(encoding="utf-8"))
        self.assertTrue(
            cc_data.get("coverage", {}).get("status", {}).get("project", {}).get("default", {}).get("informational"),
            "Codecov project status must initially be informational to avoid deadlocking non-code PRs",
        )

    def test_protected_paths_include_mergify_and_codecov(self) -> None:
        from fleet_pr_risk import PROTECTED_EXACT_FILES, classify_file

        self.assertIn(".mergify.yml", PROTECTED_EXACT_FILES)
        self.assertIn("codecov.yml", PROTECTED_EXACT_FILES)
        self.assertEqual(classify_file(".mergify.yml"), "PROTECTED")
        self.assertEqual(classify_file("codecov.yml"), "PROTECTED")

    def test_all_workflow_jobs_have_timeouts_and_least_privilege(self) -> None:
        workflows_dir = ROOT / ".github/workflows"
        for wf_path in workflows_dir.glob("*.yml"):
            wf_text = wf_path.read_text(encoding="utf-8")
            wf_data = yaml.safe_load(wf_text)

            # Check top-level permissions
            self.assertIn(
                "permissions",
                wf_data,
                f"Workflow {wf_path.name} must declare explicit top-level permissions",
            )
            top_perms = wf_data["permissions"]
            if isinstance(top_perms, dict):
                self.assertEqual(
                    top_perms.get("contents"),
                    "read",
                    f"Workflow {wf_path.name} must have top-level contents: read",
                )

            # Check timeouts on each job
            jobs = wf_data.get("jobs", {})
            for job_name, job_data in jobs.items():
                self.assertIn(
                    "timeout-minutes",
                    job_data,
                    f"Job '{job_name}' in {wf_path.name} must declare timeout-minutes",
                )
                self.assertLessEqual(
                    job_data["timeout-minutes"],
                    360,
                    f"Job '{job_name}' in {wf_path.name} timeout must not exceed 360 minutes",
                )


if __name__ == "__main__":
    unittest.main()
