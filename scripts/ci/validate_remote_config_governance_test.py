#!/usr/bin/env python3
"""Regression tests for live-template compatibility checks."""

from __future__ import annotations

import unittest

from remote_config_governance import AppRecord
from validate_remote_config_governance import validate_baseline_compatibility


class ValidateRemoteConfigGovernanceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.app = AppRecord(
            flavor="sample",
            package_name="com.example.sample",
            firebase_app_id="1:123:android:abc",
            play_production_version_code=41,
            repo_version_code=42,
        )
        self.baseline = {
            "version": {"versionNumber": "2"},
            "parameters": {
                "ads_banner_enabled": {"defaultValue": {"value": "true"}},
                "latest_version_code": {"defaultValue": {"value": "1"}},
            },
        }

    def test_parameter_removal_is_blocked(self) -> None:
        candidate = {
            "conditions": [],
            "parameters": {
                "latest_version_code": {"defaultValue": {"value": "1"}},
            },
        }

        errors, impact = validate_baseline_compatibility(self.baseline, candidate, [self.app])

        self.assertTrue(any("removes live parameters" in error for error in errors))
        self.assertEqual(impact["removed_parameters"], ["ads_banner_enabled"])

    def test_existing_ad_behavior_drift_is_blocked_for_matched_app(self) -> None:
        candidate = {
            "conditions": [{"name": "app_sample", "expression": "app.id == '1:123:android:abc'"}],
            "parameters": {
                "ads_banner_enabled": {
                    "defaultValue": {"value": "false"},
                    "conditionalValues": {"app_sample": {"value": "false"}},
                },
                "latest_version_code": {
                    "defaultValue": {"value": "1"},
                    "conditionalValues": {"app_sample": {"value": "41"}},
                },
            },
        }

        errors, _ = validate_baseline_compatibility(self.baseline, candidate, [self.app])

        self.assertTrue(any("ads_banner_enabled" in error for error in errors))

    def test_play_version_change_is_the_only_allowed_existing_key_change(self) -> None:
        candidate = {
            "conditions": [{"name": "app_sample", "expression": "app.id == '1:123:android:abc'"}],
            "parameters": {
                "ads_banner_enabled": {
                    "defaultValue": {"value": "false"},
                    "conditionalValues": {"app_sample": {"value": "true"}},
                },
                "latest_version_code": {
                    "defaultValue": {"value": "1"},
                    "conditionalValues": {"app_sample": {"value": "41"}},
                },
                "updates_enabled": {
                    "defaultValue": {"value": "false"},
                    "conditionalValues": {"app_sample": {"value": "true"}},
                },
            },
        }

        errors, impact = validate_baseline_compatibility(self.baseline, candidate, [self.app])

        self.assertEqual(errors, [])
        self.assertEqual(impact["added_parameters"], ["updates_enabled"])
        self.assertEqual(
            impact["per_app_existing_parameter_changes"],
            [
                {
                    "flavor": "sample",
                    "parameter": "latest_version_code",
                    "before": "1",
                    "after": "41",
                }
            ],
        )

    def test_baseline_condition_value_is_used_for_effective_comparison(self) -> None:
        baseline = {
            "version": {"versionNumber": "3"},
            "parameters": {
                "ads_banner_enabled": {
                    "defaultValue": {"value": "false"},
                    "conditionalValues": {"app_sample": {"value": "true"}},
                }
            },
        }
        candidate = {
            "conditions": [{"name": "app_sample", "expression": "app.id == '1:123:android:abc'"}],
            "parameters": {
                "ads_banner_enabled": {
                    "defaultValue": {"value": "false"},
                    "conditionalValues": {"app_sample": {"value": "true"}},
                }
            },
        }

        errors, impact = validate_baseline_compatibility(baseline, candidate, [self.app])

        self.assertEqual(errors, [])
        self.assertEqual(impact["per_app_existing_parameter_changes"], [])

    def test_condition_value_drift_reports_effective_before_value(self) -> None:
        baseline = {
            "version": {"versionNumber": "3"},
            "parameters": {
                "ads_banner_enabled": {
                    "defaultValue": {"value": "false"},
                    "conditionalValues": {"app_sample": {"value": "true"}},
                }
            },
        }
        candidate = {
            "conditions": [{"name": "app_sample", "expression": "app.id == '1:123:android:abc'"}],
            "parameters": {
                "ads_banner_enabled": {
                    "defaultValue": {"value": "false"},
                    "conditionalValues": {"app_sample": {"value": "false"}},
                }
            },
        }

        errors, _ = validate_baseline_compatibility(baseline, candidate, [self.app])

        self.assertEqual(
            errors,
            ["Unexpected existing-key behavior change for sample:ads_banner_enabled: 'true' -> 'false'"],
        )


if __name__ == "__main__":
    unittest.main()
