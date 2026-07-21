#!/usr/bin/env python3
"""Regression tests for analytics governance discovery and validation."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from analytics_governance import (
    collect_source_inventory,
    parameter_has_forbidden_token,
    parse_object_constants,
    parse_schema_version,
    resolve_event_owner,
    validate_contract,
)


class AnalyticsGovernanceTest(unittest.TestCase):
    def test_parse_object_constants(self) -> None:
        text = '''
object AnalyticsEventName {
    const val FIRST = "first_event"
    const val SECOND = "second_event"
}
'''
        self.assertEqual(
            {"FIRST": "first_event", "SECOND": "second_event"},
            parse_object_constants(text, "AnalyticsEventName"),
        )

    def test_parse_schema_version(self) -> None:
        self.assertEqual(
            2,
            parse_schema_version("const val ANALYTICS_SCHEMA_VERSION: Int = 2"),
        )

    def test_longest_owner_prefix_wins(self) -> None:
        rules = [
            {"prefix": "purchase", "owner": "billing"},
            {"prefix": "purchase_verified", "owner": "verification"},
        ]
        self.assertEqual(
            "verification",
            resolve_event_owner("purchase_verified", rules),
        )

    def test_forbidden_parameter_tokens_match_boundaries(self) -> None:
        tokens = ["email", "purchase_token"]
        self.assertTrue(parameter_has_forbidden_token("customer_email", tokens))
        self.assertTrue(parameter_has_forbidden_token("customer_email_hash", tokens))
        self.assertTrue(parameter_has_forbidden_token("email_hash", tokens))
        self.assertTrue(parameter_has_forbidden_token("purchase_token", tokens))
        self.assertFalse(parameter_has_forbidden_token("user_tenure_days", tokens))

    def test_source_inventory_detects_raw_names_and_types(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "app/src/main/java/Test.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                '''
fun emit() {
    appAnalytics.logEvent("raw_event", Bundle().apply {
        putString("raw_param", "value")
        putLong(AnalyticsParamKey.COUNT, 1L)
    })
}
''',
                encoding="utf-8",
            )
            inventory = collect_source_inventory(root, [source])

        self.assertEqual("raw_event", inventory["raw_events"][0]["name"])
        self.assertEqual("raw_param", inventory["raw_params"][0]["name"])
        self.assertEqual({"integer"}, inventory["param_types"]["COUNT"])

    def test_contract_rejects_conflicting_parameter_types(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            app = root / "app/src/main/java/com/parsfilo/contentapp/App.kt"
            app.parent.mkdir(parents=True)
            app.write_text(
                "AnalyticsParamKey.FLAVOR_ID\n"
                "AnalyticsParamKey.APP_VERSION\n"
                "AnalyticsParamKey.SCHEMA_VERSION\n"
                "AnalyticsParamKey.CONSENT_STATE\n"
                "AnalyticsParamKey.SUBSCRIPTION_STATUS\n",
                encoding="utf-8",
            )
            policy = (
                root
                / "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/AnalyticsPayloadPolicy.kt"
            )
            policy.parent.mkdir(parents=True)
            policy.write_text("AnalyticsParamKey.ERROR_MESSAGE\n", encoding="utf-8")
            config = self._config()
            inventory = self._inventory()
            inventory["param_types"]["AD_VALUE"] = {"string", "number"}
            errors = validate_contract(
                root,
                config,
                1,
                {"PURCHASE": "purchase"},
                self._params(),
                {"FLAVOR": "flavor"},
                inventory,
            )

        self.assertTrue(any("conflicting types" in error for error in errors))

    @staticmethod
    def _config():
        return {
            "schema_version": 1,
            "required_default_parameters": [
                "flavor_id",
                "app_version",
                "analytics_schema_version",
                "consent_state",
                "subscription_status",
            ],
            "custom_dimensions": [
                {
                    "parameter_name": "flavor_id",
                    "display_name": "Flavor",
                    "scope": "EVENT",
                    "cardinality": "low",
                }
            ],
            "custom_metrics": [
                {
                    "parameter_name": "ad_value",
                    "display_name": "Value",
                    "scope": "EVENT",
                    "measurement_unit": "CURRENCY",
                }
            ],
            "key_events": ["purchase"],
            "retired_key_events": [],
            "event_owner_rules": [{"prefix": "purchase", "owner": "billing"}],
            "pii_policy": {
                "forbidden_parameter_tokens": ["email"],
                "runtime_dropped_parameters": ["error_message"],
            },
        }

    @staticmethod
    def _params():
        return {
            "FLAVOR_ID": "flavor_id",
            "APP_VERSION": "app_version",
            "SCHEMA_VERSION": "analytics_schema_version",
            "CONSENT_STATE": "consent_state",
            "SUBSCRIPTION_STATUS": "subscription_status",
            "AD_VALUE": "ad_value",
            "ERROR_MESSAGE": "error_message",
        }

    @staticmethod
    def _inventory():
        return {
            "event_references": {"PURCHASE"},
            "param_references": {
                "FLAVOR_ID",
                "APP_VERSION",
                "SCHEMA_VERSION",
                "CONSENT_STATE",
                "SUBSCRIPTION_STATUS",
                "AD_VALUE",
                "ERROR_MESSAGE",
            },
            "user_property_references": {"FLAVOR"},
            "param_types": {
                "FLAVOR_ID": {"string"},
                "APP_VERSION": {"string"},
                "SCHEMA_VERSION": {"integer"},
                "CONSENT_STATE": {"string"},
                "SUBSCRIPTION_STATUS": {"string"},
                "AD_VALUE": {"number"},
                "ERROR_MESSAGE": {"string"},
            },
            "raw_events": [],
            "raw_params": [],
        }


if __name__ == "__main__":
    unittest.main()
