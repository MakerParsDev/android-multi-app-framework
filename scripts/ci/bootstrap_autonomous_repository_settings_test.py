#!/usr/bin/env python3
"""Unit tests for bootstrap_autonomous_repository_settings.py."""

from __future__ import annotations

import os
import sys
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, os.path.dirname(__file__))

import bootstrap_autonomous_repository_settings as bootstrap


class BootstrapSettingsTest(unittest.TestCase):
    def result(self, data=None, returncode=0, stderr=""):
        return bootstrap.GhApiResult(returncode, data, stderr)

    @patch("bootstrap_autonomous_repository_settings.subprocess.run")
    def test_run_gh_api_success_json(self, mock_run: MagicMock):
        mock_run.return_value = MagicMock(
            returncode=0, stdout='{"ok": true}', stderr=""
        )
        result = bootstrap.run_gh_api("GET", "repos/o/r")
        self.assertTrue(result.ok)
        self.assertEqual(result.data, {"ok": True})
        self.assertEqual(result.stderr, "")

    @patch("bootstrap_autonomous_repository_settings.subprocess.run")
    def test_run_gh_api_paginated_uses_gh_link_following(self, mock_run: MagicMock):
        mock_run.return_value = MagicMock(
            returncode=0,
            stdout='[[{"page": 1}], [{"page": 2}]]',
            stderr="",
        )
        result = bootstrap.run_gh_api("GET", "repos/o/r/items", paginate=True)
        self.assertTrue(result.ok)
        self.assertEqual(result.data, [[{"page": 1}], [{"page": 2}]])
        command = mock_run.call_args.args[0]
        self.assertIn("--paginate", command)
        self.assertIn("--slurp", command)

    def test_run_gh_api_rejects_paginated_writes(self):
        result = bootstrap.run_gh_api(
            "POST",
            "repos/o/r/items",
            {"name": "x"},
            paginate=True,
        )
        self.assertFalse(result.ok)
        self.assertIn("GET requests", result.stderr)

    @patch("bootstrap_autonomous_repository_settings.subprocess.run")
    def test_run_gh_api_success_no_content(self, mock_run: MagicMock):
        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="")
        result = bootstrap.run_gh_api("PATCH", "repos/o/r/actions/variables/X")
        self.assertTrue(result.ok)
        self.assertIsNone(result.data)

    @patch("bootstrap_autonomous_repository_settings.subprocess.run")
    def test_run_gh_api_success_non_json(self, mock_run: MagicMock):
        mock_run.return_value = MagicMock(returncode=0, stdout="ok", stderr="")
        result = bootstrap.run_gh_api("GET", "repos/o/r")
        self.assertTrue(result.ok)
        self.assertEqual(result.data, "ok")

    @patch("bootstrap_autonomous_repository_settings.subprocess.run")
    def test_run_gh_api_error_never_exposes_data(self, mock_run: MagicMock):
        mock_run.return_value = MagicMock(
            returncode=22, stdout='{"message":"ignored"}', stderr="HTTP 422"
        )
        result = bootstrap.run_gh_api("POST", "repos/o/r/rulesets", {})
        self.assertFalse(result.ok)
        self.assertIsNone(result.data)
        self.assertEqual(result.stderr, "HTTP 422")

    @patch("bootstrap_autonomous_repository_settings.run_gh_api")
    def test_repository_variables_use_actions_endpoint(self, api: MagicMock):
        def response(method: str, endpoint: str, input_data=None):
            if endpoint == "repos/o/r":
                return self.result(
                    {
                        "allow_auto_merge": False,
                        "delete_branch_on_merge": False,
                        "permissions": {"admin": True},
                    }
                )
            if endpoint.endswith("/branches/main/protection"):
                return self.result(None, 1, "HTTP 404")
            if endpoint == "repos/o/r/rulesets":
                return self.result([])
            if endpoint == "repos/o/r/actions/variables":
                return self.result(
                    {
                        "total_count": 2,
                        "variables": [
                            {"name": "JULES_FLEET_ENABLED", "value": "false"},
                            {
                                "name": "JULES_FLEET_AUTO_MERGE_ENABLED",
                                "value": "false",
                            },
                        ],
                    }
                )
            raise AssertionError(endpoint)

        api.side_effect = response
        state = bootstrap.check_repo_settings("o/r")
        self.assertEqual(
            state["variables"],
            {
                "JULES_FLEET_ENABLED": "false",
                "JULES_FLEET_AUTO_MERGE_ENABLED": "false",
            },
        )
        self.assertFalse(state["errors"])

    @patch("bootstrap_autonomous_repository_settings.run_gh_api")
    def test_existing_ruleset_fetches_full_definition(self, api: MagicMock):
        desired = bootstrap.load_ruleset_payload()

        def response(method: str, endpoint: str, input_data=None):
            if endpoint == "repos/o/r":
                return self.result(
                    {
                        "allow_auto_merge": False,
                        "delete_branch_on_merge": True,
                        "permissions": {"admin": True},
                    }
                )
            if endpoint.endswith("/branches/main/protection"):
                return self.result(None, 1, "HTTP 404")
            if endpoint == "repos/o/r/rulesets":
                return self.result(
                    [{"id": 42, "name": bootstrap.RULESET_NAME, "enforcement": "active"}]
                )
            if endpoint == "repos/o/r/rulesets/42":
                detail = dict(desired)
                detail["id"] = 42
                return self.result(detail)
            if endpoint == "repos/o/r/actions/variables":
                return self.result(
                    {
                        "total_count": len(bootstrap.VARIABLE_DEFAULTS),
                        "variables": [
                            {"name": name, "value": value}
                            for name, value in bootstrap.VARIABLE_DEFAULTS.items()
                        ],
                    }
                )
            raise AssertionError(endpoint)

        api.side_effect = response
        state = bootstrap.check_repo_settings("o/r")
        self.assertEqual(state["ruleset_detail"]["id"], 42)
        self.assertFalse(bootstrap.ruleset_drift(state["ruleset_detail"], desired))

    def test_plan_creates_missing_fail_closed_variables(self):
        current = {
            "allow_auto_merge": False,
            "delete_branch_on_merge": True,
            "rulesets": [],
            "ruleset_detail": None,
            "variables": {
                "JULES_FLEET_ENABLED": "false",
                "JULES_FLEET_AUTO_MERGE_ENABLED": "false",
            },
        }
        labels = {name: True for name in bootstrap.LABEL_SPECS}
        plan = bootstrap.plan_changes(
            current, labels, bootstrap.load_ruleset_payload()
        )
        self.assertIn("CREATE_RULESET", plan)
        self.assertIn(
            "CREATE_VARIABLE:AUTONOMOUS_MAINTENANCE_ENABLED:false", plan
        )
        self.assertIn("CREATE_VARIABLE:AUTONOMOUS_MERGE_ENABLED:false", plan)
        self.assertIn(
            "CREATE_VARIABLE:JULES_FLEET_SCHEDULED_ANALYZE_ENABLED:false", plan
        )
        self.assertNotIn("CREATE_VARIABLE:JULES_FLEET_ENABLED:false", plan)

    def test_plan_is_idempotent_when_state_matches(self):
        desired = bootstrap.load_ruleset_payload()
        current_detail = dict(desired)
        current_detail["id"] = 42
        current = {
            "allow_auto_merge": False,
            "delete_branch_on_merge": True,
            "rulesets": [{"id": 42, "name": bootstrap.RULESET_NAME}],
            "ruleset_detail": current_detail,
            "variables": dict(bootstrap.VARIABLE_DEFAULTS),
        }
        labels = {name: True for name in bootstrap.LABEL_SPECS}
        self.assertEqual(bootstrap.plan_changes(current, labels, desired), [])

    def test_existing_variable_values_are_not_overwritten(self):
        desired = bootstrap.load_ruleset_payload()
        current = {
            "allow_auto_merge": False,
            "delete_branch_on_merge": True,
            "rulesets": [],
            "ruleset_detail": None,
            "variables": {
                **bootstrap.VARIABLE_DEFAULTS,
                "AUTONOMOUS_MAINTENANCE_ENABLED": "true",
            },
        }
        labels = {name: True for name in bootstrap.LABEL_SPECS}
        plan = bootstrap.plan_changes(current, labels, desired)
        self.assertFalse(
            any(
                item.startswith("CREATE_VARIABLE:AUTONOMOUS_MAINTENANCE_ENABLED:")
                for item in plan
            )
        )

    @patch("bootstrap_autonomous_repository_settings.run_gh_api")
    def test_apply_create_success_uses_actions_variables_endpoint(self, api: MagicMock):
        api.return_value = self.result({"id": 1})
        ok = bootstrap.apply_changes(
            "o/r",
            ["CREATE_VARIABLE:AUTONOMOUS_MERGE_ENABLED:false"],
            bootstrap.load_ruleset_payload(),
        )
        self.assertTrue(ok)
        api.assert_called_once_with(
            "POST",
            "repos/o/r/actions/variables",
            {"name": "AUTONOMOUS_MERGE_ENABLED", "value": "false"},
        )

    @patch("bootstrap_autonomous_repository_settings.run_gh_api")
    def test_apply_update_ruleset_uses_put(self, api: MagicMock):
        api.return_value = self.result({"id": 42})
        desired = bootstrap.load_ruleset_payload()
        self.assertTrue(
            bootstrap.apply_changes("o/r", ["UPDATE_RULESET:42"], desired)
        )
        api.assert_called_once_with("PUT", "repos/o/r/rulesets/42", desired)

    @patch("bootstrap_autonomous_repository_settings.run_gh_api")
    def test_apply_failure_is_nonzero_signal(self, api: MagicMock):
        api.return_value = self.result(None, 1, "HTTP 422")
        ok = bootstrap.apply_changes(
            "o/r", ["CREATE_RULESET"], bootstrap.load_ruleset_payload()
        )
        self.assertFalse(ok)

    def test_ruleset_payload_contract(self):
        payload = bootstrap.load_ruleset_payload()
        self.assertEqual(payload["target"], "branch")
        self.assertEqual(
            payload["conditions"]["ref_name"]["include"], ["~DEFAULT_BRANCH"]
        )
        pull = next(rule for rule in payload["rules"] if rule["type"] == "pull_request")
        params = pull["parameters"]
        self.assertEqual(params["allowed_merge_methods"], ["squash"])
        for key in (
            "dismiss_stale_reviews_on_push",
            "require_code_owner_review",
            "require_last_push_approval",
            "required_approving_review_count",
            "required_review_thread_resolution",
        ):
            self.assertIn(key, params)
        checks = next(
            rule for rule in payload["rules"] if rule["type"] == "required_status_checks"
        )["parameters"]
        self.assertFalse(checks["strict_required_status_checks_policy"])
        self.assertFalse(checks["do_not_enforce_on_create"])
        contexts = {item["context"] for item in checks["required_status_checks"]}
        self.assertEqual(
            contexts,
            {
                "CI Required",
                "Security Required",
                "Secret Scan",
                "Workflow Audit",
                "Dependency Review",
                "SonarCloud Code Analysis",
                "Mergify Merge Protections",
                "Analyze Java and Kotlin",
            },
        )


if __name__ == "__main__":
    unittest.main()
