#!/usr/bin/env python3
"""
Unit and contract tests for Jules Fleet workflows, helper scripts, and risk classification policy.
"""

import os
import re
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import unittest
import yaml

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
WORKFLOWS_DIR = os.path.join(REPO_ROOT, ".github/workflows")

class TestJulesFleetWorkflows(unittest.TestCase):

    def test_fleet_workflows_exist(self):
        expected_workflows = [
            "fleet-analyze.yml",
            "fleet-dispatch.yml",
            "fleet-classify.yml",
            "fleet-merge.yml",
        ]
        for wf in expected_workflows:
            wf_path = os.path.join(WORKFLOWS_DIR, wf)
            self.assertTrue(os.path.exists(wf_path), f"Missing expected Fleet workflow: {wf}")

    def test_fleet_workflows_valid_yaml_and_pinned_actions(self):
        expected_workflows = [
            "fleet-analyze.yml",
            "fleet-dispatch.yml",
            "fleet-classify.yml",
            "fleet-merge.yml",
        ]
        sha_pattern = re.compile(r"^[a-f0-9]{40}$")

        for wf in expected_workflows:
            wf_path = os.path.join(WORKFLOWS_DIR, wf)
            with open(wf_path, "r", encoding="utf-8") as f:
                content = f.read()
                parsed = yaml.safe_load(content)
                self.assertIsInstance(parsed, dict, f"Invalid YAML structure in {wf}")

            # Check action usages
            for line in content.splitlines():
                line = line.strip()
                if line.startswith("uses:"):
                    action_ref = line.split("uses:")[1].strip().split()[0]
                    if action_ref.startswith("./"):
                        continue
                    if "@" in action_ref:
                        action_name, ref = action_ref.rsplit("@", 1)
                        self.assertTrue(
                            sha_pattern.match(ref),
                            f"Action '{action_ref}' in {wf} is not pinned to a 40-char SHA: {ref}"
                        )

    def test_doppler_token_not_passed_to_fleet_worker_or_process(self):
        for wf in ["fleet-analyze.yml", "fleet-dispatch.yml", "fleet-merge.yml"]:
            wf_path = os.path.join(WORKFLOWS_DIR, wf)
            with open(wf_path, "r", encoding="utf-8") as f:
                content = f.read()
                # Check that doppler run is not wrapping jules-fleet execution directly with full prod env
                self.assertNotIn(
                    "doppler run -- npx @google/jules-fleet",
                    content,
                    f"{wf} directly wraps jules-fleet with doppler run without secret isolation!"
                )

    def test_pr_risk_classifier_logic(self):
        from fleet_pr_risk import classify_changed_files

        # Low risk test cases
        low_risk_files = ["app/src/main/java/com/example/App.kt"]
        self.assertEqual(classify_changed_files(low_risk_files), "LOW_RISK")

        low_risk_tests = ["core/model/src/test/java/com/example/ModelTest.kt"]
        self.assertEqual(classify_changed_files(low_risk_tests), "LOW_RISK")

        low_risk_docs = ["docs/FEATURE_GUIDE.md"]
        self.assertEqual(classify_changed_files(low_risk_docs), "LOW_RISK")

        # High risk test cases
        self.assertEqual(classify_changed_files([".github/workflows/ci-pr.yml"]), "PROTECTED")
        self.assertEqual(classify_changed_files([".fleet/goals/ci-health.md"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["scripts/ci/security_gate.sh"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["build.gradle.kts"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["config/dependency-policy.json"]), "PROTECTED")

        # Mixed cases fail closed
        mixed = ["app/src/main/java/App.kt", ".github/workflows/ci-pr.yml"]
        self.assertEqual(classify_changed_files(mixed), "PROTECTED")


if __name__ == "__main__":
    unittest.main()
