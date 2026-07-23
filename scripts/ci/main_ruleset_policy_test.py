from __future__ import annotations

import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / "config/main-ruleset.json"
APPLIER = ROOT / "scripts/ci/apply_main_ruleset.sh"
DOC = ROOT / "docs/BRANCH_PROTECTION.md"

EXPECTED_CHECKS = {
    "CI Required",
    "Analyze Java and Kotlin",
    "Secret Scan",
    "Semgrep SAST",
    "Workflow Audit",
    "Dependency Review",
    "Instrumentation Tests",
}


class MainRulesetPolicyTest(unittest.TestCase):
    def policy(self) -> dict:
        return json.loads(POLICY.read_text(encoding="utf-8"))

    def test_ruleset_targets_main_and_has_no_unreviewed_bypass(self) -> None:
        policy = self.policy()
        self.assertEqual("Protect main", policy["name"])
        self.assertEqual("branch", policy["target"])
        self.assertEqual("active", policy["enforcement"])
        self.assertEqual([], policy["bypass_actors"])
        self.assertEqual(
            {"ref_name": {"include": ["refs/heads/main"], "exclude": []}},
            policy["conditions"],
        )

    def test_ruleset_requires_pr_review_checks_and_history_protection(self) -> None:
        rules = {rule["type"]: rule for rule in self.policy()["rules"]}
        self.assertIn("deletion", rules)
        self.assertIn("non_fast_forward", rules)

        pull_request = rules["pull_request"]["parameters"]
        self.assertEqual(1, pull_request["required_approving_review_count"])
        self.assertTrue(pull_request["dismiss_stale_reviews_on_push"])
        self.assertTrue(pull_request["required_review_thread_resolution"])
        self.assertFalse(pull_request["require_last_push_approval"])

        status = rules["required_status_checks"]["parameters"]
        self.assertTrue(status["strict_required_status_checks_policy"])
        self.assertTrue(status["do_not_enforce_on_create"])
        contexts = {item["context"] for item in status["required_status_checks"]}
        self.assertEqual(EXPECTED_CHECKS, contexts)

    def test_applier_is_idempotent_and_policy_is_documented(self) -> None:
        script = APPLIER.read_text(encoding="utf-8")
        self.assertIn("gh api user --jq .login", script)
        self.assertIn("MakerParsDev", script)
        self.assertIn("repos/$REPO/rulesets", script)
        self.assertIn("--method POST", script)
        self.assertIn("--method PUT", script)
        self.assertNotIn("gho_", script)
        self.assertNotIn("git push --force", script.lower())
        self.assertNotIn("git push -f", script.lower())

        documentation = DOC.read_text(encoding="utf-8")
        self.assertIn("Acil bypass", documentation)
        self.assertIn("Dependabot", documentation)
        self.assertIn("Rule Insights", documentation)
        self.assertIn("#39", documentation)


if __name__ == "__main__":
    unittest.main()
