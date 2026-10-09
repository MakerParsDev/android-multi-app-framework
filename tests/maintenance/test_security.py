import unittest
from maintenance.policy import AutonomyPolicy
from maintenance.security import render_security_issue_body, evaluate_security_issue_state

class TestSecurityTracking(unittest.TestCase):
    def test_empty_policy_has_no_active_exceptions(self):
        policy = AutonomyPolicy.load("config/autonomy-policy.yaml")
        body = render_security_issue_body(policy)
        self.assertIn("## Active Blocked Advisories", body)
        self.assertIn("Verify current SBOM/OSV advisory results independently", body)
        self.assertNotIn("#183", body)
        state = evaluate_security_issue_state(policy)
        self.assertFalse(state["should_be_open"])
        self.assertEqual(state["active_blocker_count"], 0)
        self.assertNotIn("issue_number", state)

    def test_npm_locks_cannot_reintroduce_retired_advisories(self):
        import json
        from pathlib import Path

        root = Path(__file__).resolve().parents[2]
        locks = sorted((root / "side-projects").rglob("package-lock.json"))
        self.assertEqual(len(locks), 6, "Audit all six canonical npm lockfiles")
        for lock in locks:
            data = json.loads(lock.read_text(encoding="utf-8"))
            for path, meta in data.get("packages", {}).items():
                name = path.split("/")[-1]
                if name not in {"stream-json", "ts-deepmerge"}:
                    continue
                version = meta.get("version", "")
                parts = version.split(".")
                self.assertGreaterEqual(len(parts), 3, (lock, path, version))
                try:
                    semantic = tuple(int(v) for v in parts[:3])
                except ValueError as exc:
                    self.fail(f"Unreviewed prerelease version in {lock}: {version} ({exc})")
                if name == "stream-json":
                    self.assertGreater(semantic, (3, 4, 0), (lock, path, version))
                else:
                    self.assertGreaterEqual(semantic, (8, 0, 0), (lock, path, version))

    def test_new_explicit_exception_is_reported(self):
        policy = AutonomyPolicy({"active_canaries_and_exceptions": [
            {"id": "example", "advisory": "GHSA-example", "owner": "security",
             "review_expires_on": "2026-11-01", "rationale": "test fixture"}
        ]})
        body = render_security_issue_body(policy)
        self.assertIn("GHSA-example", body)
        state = evaluate_security_issue_state(policy)
        self.assertTrue(state["should_be_open"])
        self.assertEqual(state["active_blocker_count"], 1)
        self.assertEqual(state["blocker_ids"], ["example"])

if __name__ == "__main__":
    unittest.main()
