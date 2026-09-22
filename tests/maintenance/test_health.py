import unittest
from maintenance.policy import AutonomyPolicy
from maintenance.health import HealthReporter

class TestAutonomyHealth(unittest.TestCase):
    def test_health_report_generation(self):
        policy = AutonomyPolicy.load("config/autonomy-policy.yaml")
        reporter = HealthReporter(policy)
        report = reporter.generate_report()
        self.assertEqual(report["metrics"]["policy_version"], 1)
        self.assertEqual(report["metrics"]["active_exceptions_count"], 3)
        self.assertIn("# Autonomous Maintenance Observability Report", report["markdown"])
        self.assertIn("kotlin-codeql", report["markdown"])

if __name__ == "__main__":
    unittest.main()
