#!/usr/bin/env python3
"""
Unit and contract tests for Jules Fleet workflows, helper scripts, and risk classification policy.
"""

import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import unittest
import yaml
import re
import subprocess
import tempfile
import stat

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
WORKFLOWS_DIR = os.path.join(REPO_ROOT, ".github/workflows")

class TestJulesFleetWorkflows(unittest.TestCase):

    def test_fleet_workflows_exist(self):
        expected_workflows = ["fleet-analyze.yml", "fleet-dispatch.yml", "fleet-classify.yml", "fleet-merge.yml"]
        for wf in expected_workflows:
            wf_path = os.path.join(WORKFLOWS_DIR, wf)
            self.assertTrue(os.path.exists(wf_path), f"Missing expected Fleet workflow: {wf}")

    def test_fleet_workflows_valid_yaml_and_pinned_actions(self):
        expected_workflows = ["fleet-analyze.yml", "fleet-dispatch.yml", "fleet-classify.yml", "fleet-merge.yml"]
        sha_pattern = re.compile(r"^[a-f0-9]{40}$")
        for wf in expected_workflows:
            wf_path = os.path.join(WORKFLOWS_DIR, wf)
            with open(wf_path, "r", encoding="utf-8") as f:
                content = f.read()
                parsed = yaml.safe_load(content)
                self.assertIsInstance(parsed, dict, f"Invalid YAML structure in {wf}")
            for line in content.splitlines():
                line = line.strip()
                if line.startswith("uses:"):
                    action_ref = line.split("uses:")[1].strip().split()[0]
                    if action_ref.startswith("./"):
                        continue
                    if "@" in action_ref:
                        action_name, ref = action_ref.rsplit("@", 1)
                        self.assertTrue(sha_pattern.match(ref), f"Action '{action_ref}' in {wf} is not pinned to a 40-char SHA: {ref}")

    def test_doppler_token_not_passed_to_fleet_worker_or_process(self):
        for wf in ["fleet-analyze.yml", "fleet-dispatch.yml", "fleet-merge.yml"]:
            wf_path = os.path.join(WORKFLOWS_DIR, wf)
            with open(wf_path, "r", encoding="utf-8") as f:
                content = f.read()
                self.assertNotIn("doppler run -- npx @google/jules-fleet", content, f"{wf} directly wraps jules-fleet with doppler run without secret isolation!")

    def test_pr_risk_classifier_logic(self):
        from fleet_pr_risk import classify_changed_files
        self.assertEqual(classify_changed_files(["app/src/main/java/com/example/App.kt"]), "LOW_RISK")
        self.assertEqual(classify_changed_files(["core/model/src/test/java/com/example/ModelTest.kt"]), "LOW_RISK")
        self.assertEqual(classify_changed_files(["docs/FEATURE_GUIDE.md"]), "LOW_RISK")
        self.assertEqual(classify_changed_files([".github/workflows/ci-pr.yml"]), "PROTECTED")
        self.assertEqual(classify_changed_files([".fleet/goals/ci-health.md"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["scripts/ci/security_gate.sh"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["build.gradle.kts"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["config/dependency-policy.json"]), "PROTECTED")
        self.assertEqual(classify_changed_files(["app/src/main/java/App.kt", ".github/workflows/ci-pr.yml"]), "PROTECTED")

    def test_resolve_jules_api_key_missing_doppler_token(self):
        script_path = os.path.join(REPO_ROOT, "scripts/ci/resolve_jules_api_key.sh")
        env = dict(os.environ)
        env.pop("DOPPLER_TOKEN", None)
        env.pop("JULES_API_KEY", None)
        proc = subprocess.run(["bash", script_path], env=env, capture_output=True, text=True)
        self.assertNotEqual(proc.returncode, 0)
        self.assertIn("DOPPLER_TOKEN secret is not set", proc.stderr)

    def test_resolve_jules_api_key_already_set(self):
        script_path = os.path.join(REPO_ROOT, "scripts/ci/resolve_jules_api_key.sh")
        env = dict(os.environ)
        env["JULES_API_KEY"] = "mock_jules_key_12345"
        proc = subprocess.run(["bash", script_path], env=env, capture_output=True, text=True)
        self.assertEqual(proc.returncode, 0)
        self.assertIn("::add-mask::mock_jules_key_12345", proc.stdout)

    def test_resolve_jules_api_key_mock_doppler_auth_failure(self):
        script_path = os.path.join(REPO_ROOT, "scripts/ci/resolve_jules_api_key.sh")
        with tempfile.TemporaryDirectory() as tmpdir:
            mock_doppler = os.path.join(tmpdir, "doppler")
            with open(mock_doppler, "w", encoding="utf-8") as f:
                f.write("#!/bin/sh\nif [ \"$1\" = \"me\" ]; then echo 'dp.error: Authentication failed: Invalid token' >&2; return 1 2>/dev/null || true; fi\n")
            os.chmod(mock_doppler, stat.S_IRWXU)
            env = dict(os.environ)
            env["PATH"] = f"{tmpdir}:{env.get('PATH', '')}"
            env["DOPPLER_TOKEN"] = "mock_invalid_token"
            env.pop("JULES_API_KEY", None)
            proc = subprocess.run(["bash", script_path], env=env, capture_output=True, text=True)
            self.assertNotEqual(proc.returncode, 0)
            self.assertIn("Doppler authentication failed", proc.stderr)
            self.assertIn("DOPPLER_TOKEN is invalid, expired, or revoked", proc.stderr)

    def test_resolve_jules_api_key_mock_doppler_success(self):
        script_path = os.path.join(REPO_ROOT, "scripts/ci/resolve_jules_api_key.sh")
        with tempfile.TemporaryDirectory() as tmpdir:
            mock_doppler = os.path.join(tmpdir, "doppler")
            gh_env_file = os.path.join(tmpdir, "gh_env.txt")
            with open(mock_doppler, "w", encoding="utf-8") as f:
                f.write("#!/bin/sh\nif [ \"$1\" = \"me\" ]; then echo 'user@example.com'; return 0 2>/dev/null || true; fi\nif [ \"$1\" = \"secrets\" ]; then echo 'resolved_mock_jules_api_key_999'; return 0 2>/dev/null || true; fi\n")
            os.chmod(mock_doppler, stat.S_IRWXU)
            env = dict(os.environ)
            env["PATH"] = f"{tmpdir}:{env.get('PATH', '')}"
            env["DOPPLER_TOKEN"] = "mock_valid_token"
            env["GITHUB_ENV"] = gh_env_file
            env.pop("JULES_API_KEY", None)
            proc = subprocess.run(["bash", script_path], env=env, capture_output=True, text=True)
            self.assertEqual(proc.returncode, 0)
            self.assertIn("::add-mask::resolved_mock_jules_api_key_999", proc.stdout)
            with open(gh_env_file, "r", encoding="utf-8") as gf:
                self.assertIn("JULES_API_KEY=resolved_mock_jules_api_key_999", gf.read())

if __name__ == "__main__":
    unittest.main()
