import unittest
from maintenance.policy import AutonomyPolicy
from maintenance.recovery import PostMergeRecoveryController

class TestPostMergeRecovery(unittest.TestCase):
    def setUp(self):
        self.policy = AutonomyPolicy.load("config/autonomy-policy.yaml")
        self.controller = PostMergeRecoveryController(self.policy)

    def test_observe_healthy_main(self):
        result = self.controller.observe_and_evaluate(
            recent_pr_number=101,
            recent_pr_author="dependabot[bot]",
            files_changed=["gradle/libs.versions.toml"],
            main_ci_status="SUCCESS"
        )
        self.assertEqual(result["status"], "HEALTHY")
        self.assertEqual(result["action_taken"], "NONE")

    def test_observe_unhealthy_main(self):
        result = self.controller.observe_and_evaluate(
            recent_pr_number=102,
            recent_pr_author="dependabot[bot]",
            files_changed=["gradle/libs.versions.toml"],
            main_ci_status="FAILURE",
            failure_logs="Build failed in gradle/libs.versions.toml line 45"
        )
        self.assertEqual(result["status"], "UNHEALTHY_MAIN")
        self.assertEqual(result["mode"], "OBSERVE")
        self.assertEqual(result["action_taken"], "DRAFT_PROPOSAL_LOGGED")
        self.assertFalse(result["recovery_proposal"]["direct_revert_main_allowed"])
        self.assertFalse(result["recovery_proposal"]["bypass_mergify_allowed"])
        self.assertEqual(result["recovery_proposal"]["target_pr"], 102)

if __name__ == "__main__":
    unittest.main()
