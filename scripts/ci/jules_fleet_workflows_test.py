#!/usr/bin/env python3
"""
Unit and contract tests for Jules Fleet workflows, helper scripts, and risk classification policy.
Verifies all security, supply-chain, permission, and deterministic execution contracts.
"""

from __future__ import annotations

import os
import re
import sys
import unittest

import yaml

sys.path.insert(0, os.path.dirname(__file__))
from fleet_pr_risk import classify_changed_files, classify_file

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
WORKFLOWS_DIR = os.path.join(REPO_ROOT, ".github/workflows")
DOCS_DIR = os.path.join(REPO_ROOT, "docs")

FLEET_WORKFLOW_NAMES = [
    "fleet-analyze.yml",
    "fleet-dispatch.yml",
    "fleet-classify.yml",
    "fleet-merge.yml",
]
EXPECTED_JULES_VERSION = "0.0.1-experimental.35"
SHA_PATTERN = re.compile(r"^[a-f0-9]{40}$")
TEMPLATE_EXPR_PATTERN = re.compile(r"\$\{\{.*?\}\}", re.DOTALL)


class TestJulesFleetWorkflowsContract(unittest.TestCase):

    def _load_workflow(self, name: str) -> tuple[str, dict]:
        path = os.path.join(WORKFLOWS_DIR, name)
        self.assertTrue(os.path.exists(path), f"Missing expected Fleet workflow: {name}")
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        parsed = yaml.safe_load(content)
        self.assertIsInstance(parsed, dict, f"Invalid YAML structure in {name}")
        return content, parsed

    def test_all_four_workflows_exist_and_parse_yaml(self):
        for wf_name in FLEET_WORKFLOW_NAMES:
            _, parsed = self._load_workflow(wf_name)
            self.assertIn("name", parsed)
            self.assertIn("jobs", parsed)

    def test_top_level_permissions_are_strictly_contents_read(self):
        for wf_name in FLEET_WORKFLOW_NAMES:
            _, parsed = self._load_workflow(wf_name)
            perms = parsed.get("permissions")
            self.assertEqual(
                perms,
                {"contents": "read"},
                f"Workflow {wf_name} must have top-level 'permissions: contents: read'",
            )

    def test_job_level_permissions_are_minimal_and_appropriate(self):
        # Analyze
        _, analyze_parsed = self._load_workflow("fleet-analyze.yml")
        analyze_job_perms = analyze_parsed["jobs"]["analyze"].get("permissions")
        self.assertEqual(
            analyze_job_perms,
            {"contents": "read", "issues": "write", "pull-requests": "read"},
        )

        # Dispatch
        _, dispatch_parsed = self._load_workflow("fleet-dispatch.yml")
        dispatch_job_perms = dispatch_parsed["jobs"]["dispatch"].get("permissions")
        self.assertEqual(
            dispatch_job_perms,
            {"contents": "read", "issues": "write"},
        )

        # Classify
        _, classify_parsed = self._load_workflow("fleet-classify.yml")
        classify_job_perms = classify_parsed["jobs"]["classify"].get("permissions")
        self.assertEqual(
            classify_job_perms,
            {"contents": "read", "issues": "write", "pull-requests": "read"},
        )

        # Merge
        _, merge_parsed = self._load_workflow("fleet-merge.yml")
        merge_job_perms = merge_parsed["jobs"]["merge"].get("permissions")
        self.assertEqual(
            merge_job_perms,
            {"contents": "write", "pull-requests": "write", "issues": "write"},
        )

    def test_external_actions_are_sha_pinned(self):
        for wf_name in FLEET_WORKFLOW_NAMES:
            content, _ = self._load_workflow(wf_name)
            for line in content.splitlines():
                line = line.strip()
                if line.startswith("uses:"):
                    action_ref = line.split("uses:")[1].strip().split()[0]
                    if action_ref.startswith("./"):
                        continue
                    self.assertIn(
                        "@",
                        action_ref,
                        f"Action '{action_ref}' in {wf_name} missing '@' revision delimiter",
                    )
                    action_name, ref = action_ref.rsplit("@", 1)
                    self.assertTrue(
                        SHA_PATTERN.fullmatch(ref),
                        f"Action '{action_ref}' in {wf_name} is not pinned to a 40-char SHA: {ref}",
                    )

    def test_every_job_has_bounded_timeout_minutes(self):
        for wf_name in FLEET_WORKFLOW_NAMES:
            _, parsed = self._load_workflow(wf_name)
            for job_id, job in parsed.get("jobs", {}).items():
                self.assertIn(
                    "timeout-minutes",
                    job,
                    f"Job '{job_id}' in {wf_name} is missing timeout-minutes",
                )
                timeout = job["timeout-minutes"]
                self.assertIsInstance(timeout, int)
                self.assertGreater(timeout, 0)
                self.assertLessEqual(timeout, 60, f"Job '{job_id}' timeout exceeds 60m")

    def test_fail_closed_master_switch_and_trusted_main_restriction(self):
        for wf_name in ["fleet-analyze.yml", "fleet-dispatch.yml", "fleet-merge.yml"]:
            content, parsed = self._load_workflow(wf_name)
            # Must require vars.JULES_FLEET_ENABLED == 'true'
            self.assertIn("vars.JULES_FLEET_ENABLED == 'true'", content)
            self.assertNotIn("!= 'false'", content, f"{wf_name} uses fail-open switch")
            # Must require github.ref == 'refs/heads/main'
            self.assertIn("github.ref == 'refs/heads/main'", content)

        # Classifier requires fail-closed switch
        content, _ = self._load_workflow("fleet-classify.yml")
        self.assertIn("vars.JULES_FLEET_ENABLED == 'true'", content)
        self.assertNotIn("!= 'false'", content)

    def test_classifier_does_not_use_pull_request_target(self):
        content, parsed = self._load_workflow("fleet-classify.yml")
        self.assertNotIn("pull_request_target", content)
        # Event config
        on_val = parsed.get("on") or parsed.get(True)
        self.assertIn("pull_request", on_val)

    def test_classifier_trusted_checkout_and_no_secrets(self):
        content, parsed = self._load_workflow("fleet-classify.yml")
        # Never receives DOPPLER_TOKEN or JULES_API_KEY
        self.assertNotIn("DOPPLER_TOKEN", content)
        self.assertNotIn("JULES_API_KEY", content)
        # Must checkout base_ref
        self.assertIn("ref: ${{ github.base_ref }}", content)
        self.assertIn("persist-credentials: false", content)

    def test_no_jules_execution_receives_doppler_token(self):
        for wf_name in FLEET_WORKFLOW_NAMES:
            content, _ = self._load_workflow(wf_name)
            # Ensure doppler run does not wrap jules execution
            self.assertNotIn("doppler run -- npx", content)
            self.assertNotIn("doppler run -- jules", content)

    def test_no_redundant_jules_api_key_expression_mapping(self):
        for wf_name in FLEET_WORKFLOW_NAMES:
            content, _ = self._load_workflow(wf_name)
            # JULES_API_KEY must not be mapped as ${{ env.JULES_API_KEY }}
            self.assertNotIn(
                "JULES_API_KEY: ${{ env.JULES_API_KEY }}",
                content,
                f"{wf_name} re-maps JULES_API_KEY with ${{ env.JULES_API_KEY }}",
            )

    def test_no_template_expressions_inside_run_blocks(self):
        for wf_name in FLEET_WORKFLOW_NAMES:
            _, parsed = self._load_workflow(wf_name)
            for job in parsed.get("jobs", {}).values():
                for step in job.get("steps", []):
                    run = step.get("run")
                    if isinstance(run, str):
                        self.assertIsNone(
                            TEMPLATE_EXPR_PATTERN.search(run),
                            f"Template expression found in run block of {wf_name}: {run}",
                        )

    def test_checkout_has_persist_credentials_false(self):
        for wf_name in FLEET_WORKFLOW_NAMES:
            _, parsed = self._load_workflow(wf_name)
            for job in parsed.get("jobs", {}).values():
                for step in job.get("steps", []):
                    if step.get("uses", "").startswith("actions/checkout@"):
                        with_dict = step.get("with", {})
                        self.assertFalse(
                            with_dict.get("persist-credentials"),
                            f"actions/checkout in {wf_name} must have persist-credentials: false",
                        )

    def test_exact_jules_fleet_version_consistent_across_repo(self):
        expected_pkg = f"@google/jules-fleet@{EXPECTED_JULES_VERSION}"
        for wf_name in ["fleet-analyze.yml", "fleet-dispatch.yml", "fleet-merge.yml"]:
            content, _ = self._load_workflow(wf_name)
            self.assertIn(expected_pkg, content)

        plan_path = os.path.join(DOCS_DIR, "JULES_AUTOMATION_PLAN.md")
        with open(plan_path, "r", encoding="utf-8") as f:
            plan_content = f.read()
        self.assertIn(expected_pkg, plan_content)

        readme_path = os.path.join(WORKFLOWS_DIR, "README.md")
        with open(readme_path, "r", encoding="utf-8") as f:
            readme_content = f.read()
        self.assertIn(expected_pkg, readme_content)

    def test_pr_risk_classifier_fail_closed_and_comprehensive(self):
        # Empty list MUST fail closed to PROTECTED
        self.assertEqual(classify_changed_files([]), "PROTECTED")

        # Empty string path fails closed
        self.assertEqual(classify_file(""), "PROTECTED")
        self.assertEqual(classify_file("   "), "PROTECTED")

        # Low risk files
        self.assertEqual(
            classify_changed_files(["app/src/main/java/com/example/App.kt"]),
            "LOW_RISK",
        )
        self.assertEqual(
            classify_changed_files(["core/model/src/test/java/com/example/ModelTest.kt"]),
            "LOW_RISK",
        )
        self.assertEqual(
            classify_changed_files(["docs/FEATURE_GUIDE.md", "README.md"]),
            "LOW_RISK",
        )

        # Protected paths
        self.assertEqual(classify_changed_files([".github/workflows/ci.yml"]), "PROTECTED")
        self.assertEqual(classify_changed_files([".fleet/goals/ci.md"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["scripts/ci/security_gate.sh"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["build.gradle.kts"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["settings.gradle.kts"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["gradle.properties"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["gradlew"]), "PROTECTED")
        self.assertEqual(classify_changed_files([".gitleaks.toml"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["config/dependency-policy.json"]), "PROTECTED")

        # Mixed paths fail closed to PROTECTED
        mixed = ["app/src/main/java/App.kt", ".github/workflows/ci.yml"]
        self.assertEqual(classify_changed_files(mixed), "PROTECTED")

    def test_milestone_handling_shared_contract(self):
        analyze_content, _ = self._load_workflow("fleet-analyze.yml")
        dispatch_content, _ = self._load_workflow("fleet-dispatch.yml")

        # Analyze uses fleet_milestone.py --ensure
        self.assertIn("fleet_milestone.py --ensure", analyze_content)
        self.assertIn('--milestone "$FLEET_MILESTONE"', analyze_content)

        # Dispatch uses fleet_milestone.py --resolve
        self.assertIn("fleet_milestone.py --resolve", dispatch_content)
        self.assertIn('--milestone "$FLEET_MILESTONE"', dispatch_content)

    def test_merge_dry_run_when_disabled(self):
        merge_content, _ = self._load_workflow("fleet-merge.yml")
        self.assertIn('--dry-run', merge_content)
        self.assertIn('--redispatch', merge_content)


if __name__ == "__main__":
    unittest.main()
