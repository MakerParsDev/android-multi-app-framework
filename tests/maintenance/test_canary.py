import unittest
from maintenance.canary import run_kotlin_codeql_canary, run_firebase_stream_json_canary, execute_canary

class TestCanaryFramework(unittest.TestCase):
    def test_kotlin_codeql_canary_fail(self):
        result = run_kotlin_codeql_canary(candidate_version="2.4.20", codeql_bundle_version="2.27.0")
        self.assertFalse(result.success)
        self.assertEqual(result.artifact["status"], "VERSION_POLICY_BLOCKED")

    def test_kotlin_codeql_canary_pass(self):
        result = run_kotlin_codeql_canary(candidate_version="2.4.10", codeql_bundle_version="2.27.0")
        self.assertTrue(result.success)
        self.assertEqual(result.artifact["status"], "VERSION_ELIGIBLE_LIVE_CODEQL_REQUIRED")

    def test_codeql_2271_accepts_fixed_kotlin_as_policy_preflight(self):
        result = run_kotlin_codeql_canary(
            candidate_version="2.4.20", codeql_bundle_version="2.27.1"
        )
        self.assertTrue(result.success)
        self.assertEqual(result.artifact["status"], "VERSION_ELIGIBLE_LIVE_CODEQL_REQUIRED")
        self.assertFalse(result.artifact["executed_codeql_extractor"])

    def test_firebase_stream_json_canary_fail(self):
        result = run_firebase_stream_json_canary(candidate_stream_json_version="3.5.0")
        self.assertFalse(result.success)
        self.assertEqual(result.artifact["status"], "MODULE_RESOLUTION_BREAKAGE")

    def test_execute_canary_dispatch(self):
        result = execute_canary("kotlin-codeql")
        self.assertEqual(result.canary_id, "kotlin-codeql")

if __name__ == "__main__":
    unittest.main()
