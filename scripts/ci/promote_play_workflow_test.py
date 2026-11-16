from __future__ import annotations

import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/play-promote-production.yml"


class WorkflowContractTests(unittest.TestCase):
    def test_manual_preview_default_and_protected_production(self):
        doc = yaml.safe_load(WORKFLOW.read_text(encoding="utf-8"))
        event = doc.get("on", doc.get(True))["workflow_dispatch"]
        inputs = event["inputs"]
        self.assertEqual(inputs["mode"]["default"], "preview")
        self.assertEqual(inputs["mode"]["options"], ["preview", "publish"])
        self.assertEqual(doc["jobs"]["promote"]["environment"], "production")
        self.assertEqual(doc["concurrency"]["group"], "release")
        self.assertFalse(doc["concurrency"]["cancel-in-progress"])

    def test_main_branch_only_exact_internal_promotion_without_build(self):
        doc = yaml.safe_load(WORKFLOW.read_text(encoding="utf-8"))
        job = doc["jobs"]["promote"]
        self.assertEqual(job["permissions"], {"contents": "read"})
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("refs/heads/main", text)
        self.assertIn("--expected-internal-code", text)
        self.assertIn("--expected-production-code", text)
        self.assertIn("--acceptance-issue", text)
        self.assertIn("--execute", text)
        self.assertIn("scripts/doppler-run.sh", text)
        self.assertNotIn("./gradlew", text)
        self.assertNotIn("bundles().upload", text)
        self.assertNotIn("releaseStatus", text)


if __name__ == "__main__":
    unittest.main()
