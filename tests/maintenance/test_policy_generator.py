import unittest
import os
import tempfile
from maintenance.policy import AutonomyPolicy
from maintenance.render import generate_dependabot_yml, project_all_surfaces

class TestPolicyGenerator(unittest.TestCase):
    def test_policy_load(self):
        policy = AutonomyPolicy.load("config/autonomy-policy.yaml")
        self.assertEqual(policy.version, 1)
        self.assertIn("com.android.*", policy.protected_gradle_patterns)
        self.assertIn("wrangler", policy.protected_npm_patterns)

    def test_dependabot_render(self):
        policy = AutonomyPolicy.load("config/autonomy-policy.yaml")
        yml_str = generate_dependabot_yml(policy)
        self.assertIn("package-ecosystem: github-actions", yml_str)
        self.assertIn("package-ecosystem: gradle", yml_str)
        self.assertIn("package-ecosystem: npm", yml_str)
        self.assertIn("com.android.*", yml_str)
        self.assertIn("wrangler", yml_str)

    def test_drift_detection(self):
        # Running check on current state detects drift because header comment is missing in repo's dependabot.yml
        results = project_all_surfaces(write=False)
        self.assertIn(".github/dependabot.yml", results)

if __name__ == "__main__":
    unittest.main()
