import unittest
from maintenance.policy import AutonomyPolicy
from maintenance.security import render_security_issue_body, evaluate_security_issue_state

class TestSecurityTracking(unittest.TestCase):
    def test_render_security_issue_body(self):
        policy = AutonomyPolicy.load("config/autonomy-policy.yaml")
        body = render_security_issue_body(policy)
        self.assertIn("## Active Blocked Advisories", body)
        self.assertIn("kotlin-codeql", body)
        self.assertIn("GHSA-r937-wjx7-w2jp", body)
        self.assertIn("firebase-stream-json", body)

    def test_evaluate_security_issue_state_open(self):
        policy = AutonomyPolicy.load("config/autonomy-policy.yaml")
        state = evaluate_security_issue_state(policy)
        self.assertTrue(state["should_be_open"])
        self.assertEqual(state["active_blocker_count"], 3)

    def test_evaluate_security_issue_state_close(self):
        empty_policy_data = {
            "version": 1,
            "active_canaries_and_exceptions": []
        }
        policy = AutonomyPolicy(empty_policy_data)
        state = evaluate_security_issue_state(policy)
        self.assertFalse(state["should_be_open"])
        self.assertEqual(state["active_blocker_count"], 0)

if __name__ == "__main__":
    unittest.main()
