#!/usr/bin/env python3
"""Unit tests for maintenance_health_controller.py."""

from __future__ import annotations

import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, os.path.dirname(__file__))

import maintenance_health_controller as health


class MaintenanceHealthControllerTest(unittest.TestCase):
    def api_result(self, data=None, returncode=0, stderr=""):
        from bootstrap_autonomous_repository_settings import GhApiResult
        return GhApiResult(returncode, data, stderr)

    @patch("maintenance_health_controller.run_gh_api")
    def test_ruleset_absent_is_bootstrap_pending(self, api: MagicMock):
        api.return_value = self.api_result([])
        state, message = health.check_github_ruleset_state("o/r")
        self.assertEqual(state, "BOOTSTRAP_PENDING")
        self.assertIn("not active yet", message)

    @patch("maintenance_health_controller.run_gh_api")
    def test_ruleset_api_error_is_unknown(self, api: MagicMock):
        api.return_value = self.api_result(None, 1, "HTTP 403")
        state, _ = health.check_github_ruleset_state("o/r")
        self.assertEqual(state, "UNKNOWN")

    @patch("maintenance_health_controller.run_gh_api")
    @patch("maintenance_health_controller.ruleset_drift", return_value=True)
    def test_ruleset_drift_requires_attention(
        self, drift: MagicMock, api: MagicMock
    ):
        api.side_effect = [
            self.api_result([{"id": 9, "name": health.RULESET_NAME}]),
            self.api_result({"id": 9, "name": health.RULESET_NAME, "enforcement": "active"}),
        ]
        state, _ = health.check_github_ruleset_state("o/r")
        self.assertEqual(state, "ATTENTION_REQUIRED")
        drift.assert_called_once()

    @patch("maintenance_health_controller.run_gh_api")
    @patch("maintenance_health_controller.ruleset_drift", return_value=False)
    def test_ruleset_active_and_matching_is_healthy(
        self, drift: MagicMock, api: MagicMock
    ):
        api.side_effect = [
            self.api_result([{"id": 9, "name": health.RULESET_NAME}]),
            self.api_result({"id": 9, "name": health.RULESET_NAME, "enforcement": "active"}),
        ]
        state, _ = health.check_github_ruleset_state("o/r")
        self.assertEqual(state, "HEALTHY")

    def test_side_project_exception_expiry_uses_current_policy_schema(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            policy_path = root / "side-projects/audit-policy.json"
            policy_path.parent.mkdir(parents=True)
            policy_path.write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "exceptions": [
                            {
                                "project": "firebase-rules-tests",
                                "advisory": "GHSA-example-test",
                                "expiresOn": "2026-09-21",
                            },
                            {
                                "project": "firebase-functions",
                                "advisory": "GHSA-future-test",
                                "expiresOn": "2026-10-01",
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )
            with patch.object(health, "ROOT", root):
                findings = health.check_expirations()

        self.assertEqual(len(findings), 1)
        self.assertIn("firebase-rules-tests", findings[0])
        self.assertIn("GHSA-example-test", findings[0])

    @patch("maintenance_health_controller.run_gh_api")
    def test_dependabot_alerts_critical_or_high_require_attention(
        self, api: MagicMock
    ):
        api.return_value = self.api_result(
            [[
                {"security_advisory": {"severity": "critical"}},
                {"security_advisory": {"severity": "high"}},
                {"security_advisory": {"severity": "medium"}},
            ]]
        )
        state, message = health.check_dependabot_alerts("o/r")
        api.assert_called_once_with(
            "GET",
            "repos/o/r/dependabot/alerts?state=open&per_page=100",
            paginate=True,
        )
        self.assertEqual(state, "ATTENTION_REQUIRED")
        self.assertIn("1 critical", message)
        self.assertIn("1 high", message)

    @patch("maintenance_health_controller.run_gh_api")
    def test_dependabot_alerts_medium_or_low_are_degraded(self, api: MagicMock):
        api.return_value = self.api_result(
            [[
                {"security_advisory": {"severity": "medium"}},
                {"security_advisory": {"severity": "low"}},
            ]]
        )
        state, message = health.check_dependabot_alerts("o/r")
        self.assertEqual(state, "DEGRADED")
        self.assertIn("2 open alert", message)

    @patch("maintenance_health_controller.run_gh_api")
    def test_dependabot_alerts_later_page_severe_alert_requires_attention(
        self, api: MagicMock
    ):
        api.return_value = self.api_result(
            [
                [{"security_advisory": {"severity": "low"}}] * 100,
                [{"security_advisory": {"severity": "critical"}}],
            ]
        )
        state, message = health.check_dependabot_alerts("o/r")
        self.assertEqual(state, "ATTENTION_REQUIRED")
        self.assertIn("1 critical", message)
        self.assertIn("100 low", message)

    @patch("maintenance_health_controller.run_gh_api")
    def test_dependabot_alerts_invalid_page_shape_is_unknown(self, api: MagicMock):
        api.return_value = self.api_result([[{"security_advisory": {"severity": "low"}}], {"bad": "shape"}])
        state, message = health.check_dependabot_alerts("o/r")
        self.assertEqual(state, "UNKNOWN")
        self.assertIn("invalid page shape", message)

    @patch("maintenance_health_controller.run_gh_api")
    def test_dependabot_alerts_api_error_is_unknown(self, api: MagicMock):
        api.return_value = self.api_result(None, 1, "HTTP 403")
        state, message = health.check_dependabot_alerts("o/r")
        self.assertEqual(state, "UNKNOWN")
        self.assertIn("403", message)

    @patch("maintenance_health_controller.check_expirations", return_value=[])
    @patch(
        "maintenance_health_controller.check_dependabot_alerts",
        return_value=("HEALTHY", "ok"),
    )
    @patch("maintenance_health_controller.check_automerge_control", return_value=(False, "broken"))
    @patch("maintenance_health_controller.check_github_ruleset_state", return_value=("BOOTSTRAP_PENDING", "pending"))
    @patch("maintenance_health_controller.check_dependabot_coverage", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_mergify_configuration", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_codeql_kotlin_compatibility", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_pinned_actions", return_value=(True, "ok"))
    def test_critical_failure_beats_bootstrap_pending(self, *_mocks):
        _text, state = health.generate_dashboard_markdown("o/r")
        self.assertEqual(state, "ATTENTION_REQUIRED")

    @patch("maintenance_health_controller.check_expirations", return_value=[])
    @patch(
        "maintenance_health_controller.check_dependabot_alerts",
        return_value=("HEALTHY", "ok"),
    )
    @patch("maintenance_health_controller.check_automerge_control", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_github_ruleset_state", return_value=("UNKNOWN", "unavailable"))
    @patch("maintenance_health_controller.check_dependabot_coverage", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_mergify_configuration", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_codeql_kotlin_compatibility", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_pinned_actions", return_value=(True, "ok"))
    def test_unknown_ruleset_state_stays_unknown(self, *_mocks):
        _text, state = health.generate_dashboard_markdown("o/r")
        self.assertEqual(state, "UNKNOWN")

    @patch("maintenance_health_controller.check_expirations", return_value=[])
    @patch(
        "maintenance_health_controller.check_dependabot_alerts",
        return_value=("HEALTHY", "ok"),
    )
    @patch("maintenance_health_controller.check_automerge_control", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_github_ruleset_state", return_value=("HEALTHY", "ok"))
    @patch("maintenance_health_controller.check_dependabot_coverage", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_mergify_configuration", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_codeql_kotlin_compatibility", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_pinned_actions", return_value=(True, "ok"))
    def test_dashboard_reports_read_only_fleet_merge_architecture(self, *_mocks):
        text, state = health.generate_dashboard_markdown("o/r")
        self.assertEqual(state, "HEALTHY")
        self.assertIn("Jules Fleet Merge Status:** Read-only audit only", text)
        self.assertIn("Mergify is the sole merge authority", text)
        self.assertNotIn("Jules Fleet Merge Status:** Dry-run only", text)

    @patch("maintenance_health_controller.check_expirations", return_value=[])
    @patch(
        "maintenance_health_controller.check_dependabot_alerts",
        return_value=("ATTENTION_REQUIRED", "1 critical"),
    )
    @patch("maintenance_health_controller.check_automerge_control", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_github_ruleset_state", return_value=("HEALTHY", "ok"))
    @patch("maintenance_health_controller.check_dependabot_coverage", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_mergify_configuration", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_codeql_kotlin_compatibility", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_pinned_actions", return_value=(True, "ok"))
    def test_critical_dependabot_alerts_require_attention(self, *_mocks):
        text, state = health.generate_dashboard_markdown("o/r")
        self.assertEqual(state, "ATTENTION_REQUIRED")
        self.assertIn("Dependabot Security Alerts:** ❌ 1 critical", text)

    @patch("maintenance_health_controller.check_expirations", return_value=["expired"])
    @patch(
        "maintenance_health_controller.check_dependabot_alerts",
        return_value=("HEALTHY", "ok"),
    )
    @patch("maintenance_health_controller.check_automerge_control", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_github_ruleset_state", return_value=("HEALTHY", "ok"))
    @patch("maintenance_health_controller.check_dependabot_coverage", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_mergify_configuration", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_codeql_kotlin_compatibility", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_pinned_actions", return_value=(True, "ok"))
    def test_expiration_is_degraded_not_healthy(self, *_mocks):
        _text, state = health.generate_dashboard_markdown("o/r")
        self.assertEqual(state, "DEGRADED")


if __name__ == "__main__":
    unittest.main()
