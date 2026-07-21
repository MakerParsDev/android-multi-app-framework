#!/usr/bin/env python3
"""Unit tests for Remote Config governance generation and validation helpers."""

from __future__ import annotations

import unittest

from remote_config_governance import (
    AppRecord,
    app_value,
    build_template,
    condition_expression,
    parse_kotlin_remote_keys,
    remote_string,
    validate_typed_value,
)


class RemoteConfigGovernanceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.app = AppRecord(
            flavor="sample",
            package_name="com.example.sample",
            firebase_app_id="1:123:android:abc",
            play_production_version_code=41,
            repo_version_code=42,
        )

    def test_condition_uses_firebase_app_id(self) -> None:
        self.assertEqual(
            condition_expression(self.app.firebase_app_id),
            "app.id == '1:123:android:abc'",
        )

    def test_derived_play_version(self) -> None:
        self.assertEqual(
            app_value(
                {
                    "default": 1,
                    "flavor_default": 1,
                    "derive": "play_production_version_code",
                },
                self.app,
            ),
            41,
        )

    def test_flavor_override_wins(self) -> None:
        self.assertEqual(
            app_value(
                {
                    "default": False,
                    "flavor_default": True,
                    "overrides": {"sample": False},
                },
                self.app,
            ),
            False,
        )

    def test_remote_string_is_lowercase_for_booleans(self) -> None:
        self.assertEqual(remote_string(True), "true")
        self.assertEqual(remote_string(False), "false")

    def test_template_emits_only_values_different_from_safe_default(self) -> None:
        governance = {
            "parameters": {
                "global_key": {
                    "default": "value",
                    "description": "Global",
                    "scope": "global",
                },
                "scoped_key": {
                    "default": False,
                    "flavor_default": True,
                    "description": "Scoped",
                    "scope": "flavor",
                },
            }
        }
        template = build_template(governance, [self.app])
        self.assertEqual(template["conditions"][0]["name"], "app_sample")
        self.assertNotIn("conditionalValues", template["parameters"]["global_key"])
        self.assertEqual(
            template["parameters"]["scoped_key"]["conditionalValues"],
            {"app_sample": {"value": "true"}},
        )

    def test_redundant_scoped_value_uses_safe_default_without_override(self) -> None:
        governance = {
            "parameters": {
                "scoped_key": {
                    "default": True,
                    "flavor_default": True,
                    "description": "Scoped",
                    "scope": "flavor",
                },
            }
        }

        template = build_template(governance, [self.app])

        self.assertNotIn("conditionalValues", template["parameters"]["scoped_key"])

    def test_integer_bounds_are_enforced(self) -> None:
        spec = {"type": "integer", "minimum": 10, "maximum": 20}
        self.assertEqual(validate_typed_value("key", spec, 15), [])
        self.assertTrue(validate_typed_value("key", spec, 9))
        self.assertTrue(validate_typed_value("key", spec, 21))

    def test_enum_values_are_enforced(self) -> None:
        spec = {"type": "enum", "allowed_values": ["none", "soft", "hard"]}
        self.assertEqual(validate_typed_value("mode", spec, "soft"), [])
        self.assertTrue(validate_typed_value("mode", spec, "invalid"))

    def test_kotlin_key_parser_handles_multiline_constants(self) -> None:
        text = '''
            const val KEY_ONE = "first_key"
            const val KEY_TWO =
                "second_key"
        '''
        self.assertEqual(parse_kotlin_remote_keys(text), {"first_key", "second_key"})


if __name__ == "__main__":
    unittest.main()
