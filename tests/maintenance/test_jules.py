import unittest
import time
from maintenance.policy import AutonomyPolicy
from maintenance.jules import DispatchLeaseStore

class TestJulesDispatchSafety(unittest.TestCase):
    def setUp(self):
        self.policy = AutonomyPolicy.load("config/autonomy-policy.yaml")
        self.store = DispatchLeaseStore()

    def test_acquire_lease_success(self):
        acquired, key, reason = self.store.try_acquire_lease(
            repo="MakerParsDev/android-multi-app-framework",
            goal_or_issue="issue-245",
            base_sha="abc1234",
            operation_class="autonomous-maintenance",
            policy=self.policy
        )
        self.assertTrue(acquired)
        self.assertIn("Lease acquired", reason)

    def test_duplicate_dispatch_rejected(self):
        acquired1, key1, _ = self.store.try_acquire_lease(
            repo="MakerParsDev/android-multi-app-framework",
            goal_or_issue="issue-245",
            base_sha="abc1234",
            operation_class="autonomous-maintenance",
            policy=self.policy
        )
        self.assertTrue(acquired1)

        acquired2, key2, reason2 = self.store.try_acquire_lease(
            repo="MakerParsDev/android-multi-app-framework",
            goal_or_issue="issue-245",
            base_sha="abc1234",
            operation_class="autonomous-maintenance",
            policy=self.policy
        )
        self.assertFalse(acquired2)
        self.assertIn("Active session lease already exists", reason2)

    def test_kill_switch(self):
        custom_data = dict(self.policy.data)
        custom_data["jules_fleet_dispatch"] = dict(custom_data["jules_fleet_dispatch"])
        custom_data["jules_fleet_dispatch"]["global_kill_switch"] = True
        kill_policy = AutonomyPolicy(custom_data)

        acquired, _, reason = self.store.try_acquire_lease(
            repo="MakerParsDev/android-multi-app-framework",
            goal_or_issue="issue-245",
            base_sha="abc1234",
            operation_class="autonomous-maintenance",
            policy=kill_policy
        )
        self.assertFalse(acquired)
        self.assertIn("Global kill switch", reason)

    def test_ttl_expiry(self):
        now = 1000.0
        acquired1, key, _ = self.store.try_acquire_lease(
            repo="MakerParsDev/android-multi-app-framework",
            goal_or_issue="issue-245",
            base_sha="abc1234",
            operation_class="autonomous-maintenance",
            policy=self.policy,
            now=now
        )
        self.assertTrue(acquired1)

        # 61 minutes later (TTL = 60m)
        later = now + (61 * 60)
        acquired2, _, _ = self.store.try_acquire_lease(
            repo="MakerParsDev/android-multi-app-framework",
            goal_or_issue="issue-245",
            base_sha="abc1234",
            operation_class="autonomous-maintenance",
            policy=self.policy,
            now=later
        )
        self.assertTrue(acquired2)

if __name__ == "__main__":
    unittest.main()
