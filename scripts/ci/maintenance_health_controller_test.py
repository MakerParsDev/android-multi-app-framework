#!/usr/bin/env python3
"""Unit tests for maintenance_health_controller.py."""

from __future__ import annotations

import json
import os
from datetime import date
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
                                "advisory": "GHSA-expired-test",
                                "expiresOn": "2026-09-20",
                            },
                            {
                                "project": "firebase-functions",
                                "advisory": "GHSA-expires-today-test",
                                "expiresOn": "2026-09-21",
                            },
                            {
                                "project": "firebase-tools",
                                "advisory": "GHSA-future-test",
                                "expiresOn": "2026-09-22",
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )
            with patch.object(health, "ROOT", root):
                # Test with today = 2026-09-21 (expired and expires-today should be found)
                findings = health.check_expirations(date(2026, 9, 21))

            self.assertEqual(len(findings), 2)
            self.assertTrue(any("firebase-rules-tests" in f and "GHSA-expired-test" in f for f in findings))
            self.assertTrue(any("firebase-functions" in f and "GHSA-expires-today-test" in f for f in findings))
            self.assertFalse(any("firebase-tools" in f for f in findings))

            # Test with today = 2026-09-22 (all three should be found)
            with patch.object(health, "ROOT", root):
                findings = health.check_expirations(date(2026, 9, 22))
            self.assertEqual(len(findings), 3)

            # Test with today = 2026-09-19 (none should be found)
            with patch.object(health, "ROOT", root):
                findings = health.check_expirations(date(2026, 9, 19))
            self.assertEqual(len(findings), 0)

    def test_active_security_exceptions_include_tracking_issue(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)

            audit_policy = root / "side-projects/audit-policy.json"
            audit_policy.parent.mkdir(parents=True)
            audit_policy.write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "exceptions": [
                            {
                                "project": "firebase-rules-tests",
                                "advisory": "GHSA-expired-test",
                                "trackingIssue": "#183",
                                "expiresOn": "2026-09-20",
                            },
                            {
                                "project": "firebase-functions",
                                "advisory": "GHSA-expires-today-test",
                                "trackingIssue": "#184",
                                "expiresOn": "2026-09-21",
                            },
                            {
                                "project": "firebase-tools",
                                "advisory": "GHSA-future-test",
                                "trackingIssue": "#185",
                                "expiresOn": "2026-09-22",
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            codeql_policy = root / "config/codeql-compatibility-policy.json"
            codeql_policy.parent.mkdir(parents=True)
            codeql_policy.write_text(
                json.dumps(
                    {
                        "kotlin": {
                            "blocked_security_upgrade": {
                                "advisory": "GHSA-kotlin-expired",
                                "tracking_issue": "#186",
                                "expires_on": "2026-09-20",
                            }
                        }
                    }
                ),
                encoding="utf-8",
            )

            # Test with today = 2026-09-21: only future exceptions should be active (expiresOn > today)
            with patch.object(health, "ROOT", root):
                active = health.list_active_security_exceptions(date(2026, 9, 21))

            self.assertEqual(len(active), 1)
            self.assertTrue(any("firebase-tools" in item and "GHSA-future-test" in item and "#185" in item for item in active))
            # CodeQL blocked_security_upgrade with expires_on 2026-09-20 is expired, so not included

            # Test with today = 2026-09-20: only future should be active (expiresOn > today)
            with patch.object(health, "ROOT", root):
                active = health.list_active_security_exceptions(date(2026, 9, 20))
            self.assertEqual(len(active), 2)
            self.assertTrue(any("firebase-functions" in item and "GHSA-expires-today-test" in item and "#184" in item for item in active))
            self.assertTrue(any("firebase-tools" in item and "GHSA-future-test" in item and "#185" in item for item in active))
            # CodeQL blocked_security_upgrade with expires_on 2026-09-20 is not > today, so not included

            # Test with today = 2026-09-19: all should be active (all expiresOn > today)
            with patch.object(health, "ROOT", root):
                active = health.list_active_security_exceptions(date(2026, 9, 19))
            self.assertEqual(len(active), 4)

    @patch("maintenance_health_controller.summarize_osv_report")
    def test_repository_vulnerability_health_reads_osv_report(
        self, summarize: MagicMock
    ):
        summarize.return_value = ("DEGRADED", "2 medium")
        with patch.dict(
            os.environ,
            {"OSV_REPORT_PATH": "build/reports/dependencies/custom-osv.json"},
            clear=False,
        ):
            state, message = health.check_repository_vulnerabilities()
        self.assertEqual(state, "DEGRADED")
        self.assertEqual(message, "2 medium")
        summarize.assert_called_once()
        report_path = summarize.call_args.args[0]
        self.assertTrue(
            str(report_path).replace("\\", "/").endswith(
                "build/reports/dependencies/custom-osv.json"
            )
        )

    @patch("maintenance_health_controller.summarize_osv_report")
    def test_repository_vulnerability_health_defaults_to_standard_report(
        self, summarize: MagicMock
    ):
        summarize.return_value = ("HEALTHY", "clean")
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("OSV_REPORT_PATH", None)
            state, message = health.check_repository_vulnerabilities()
        self.assertEqual((state, message), ("HEALTHY", "clean"))
        report_path = summarize.call_args.args[0]
        self.assertEqual(
            report_path,
            health.ROOT / "build/reports/dependencies/osv-results.json",
        )

    @patch("maintenance_health_controller.check_expirations", return_value=[])
    @patch(
        "maintenance_health_controller.check_repository_vulnerabilities",
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
        "maintenance_health_controller.check_repository_vulnerabilities",
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
        "maintenance_health_controller.check_repository_vulnerabilities",
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
        "maintenance_health_controller.check_repository_vulnerabilities",
        return_value=("ATTENTION_REQUIRED", "1 critical"),
    )
    @patch("maintenance_health_controller.check_automerge_control", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_github_ruleset_state", return_value=("HEALTHY", "ok"))
    @patch("maintenance_health_controller.check_dependabot_coverage", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_mergify_configuration", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_codeql_kotlin_compatibility", return_value=(True, "ok"))
    @patch("maintenance_health_controller.check_pinned_actions", return_value=(True, "ok"))
    def test_critical_repository_vulnerability_requires_attention(self, *_mocks):
        text, state = health.generate_dashboard_markdown("o/r")
        self.assertEqual(state, "ATTENTION_REQUIRED")
        self.assertIn(
            "Repository Vulnerability Scan (GitHub SPDX SBOM + OSV):** ❌ 1 critical",
            text,
        )

    @patch("maintenance_health_controller.check_expirations", return_value=["expired"])
    @patch(
        "maintenance_health_controller.check_repository_vulnerabilities",
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
