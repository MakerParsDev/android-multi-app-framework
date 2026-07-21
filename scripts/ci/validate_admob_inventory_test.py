#!/usr/bin/env python3
"""Regression tests for AdMob inventory governance."""

from __future__ import annotations

import copy
import json
import tempfile
import unittest
from datetime import date
from pathlib import Path

from validate_admob_inventory import parse_ads_xml, validate_inventory


class ValidateAdMobInventoryTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.flavor = "sample"
        self.package = "com.example.sample"
        self.app_id = "ca-app-pub-3312485084079132~1000000001"
        self.units = {
            "banner": "ca-app-pub-3312485084079132/2000000001",
            "interstitial": "ca-app-pub-3312485084079132/2000000002",
            "native": "ca-app-pub-3312485084079132/2000000003",
            "rewarded": "ca-app-pub-3312485084079132/2000000004",
            "open_app": "ca-app-pub-3312485084079132/2000000005",
            "rewarded_interstitial": "ca-app-pub-3312485084079132/2000000006",
        }
        catalog = [
            {
                "flavor": self.flavor,
                "package": self.package,
                "name": "Sample",
                "admob_app_id": self.app_id,
                "ad_units": self.units,
            }
        ]
        (self.root / ".ci").mkdir(parents=True)
        (self.root / ".ci/apps.json").write_text(json.dumps(catalog), encoding="utf-8")
        values = {
            "admob_app_id": self.app_id,
            "ad_unit_banner": self.units["banner"],
            "ad_unit_interstitial": self.units["interstitial"],
            "ad_unit_native": self.units["native"],
            "ad_unit_rewarded": self.units["rewarded"],
            "ad_unit_open_app": self.units["open_app"],
            "ad_unit_rewarded_interstitial": self.units["rewarded_interstitial"],
            "ad_unit_banner_home": self.units["banner"],
            "ad_unit_banner_settings": self.units["banner"],
            "ad_unit_banner_content_list": self.units["banner"],
            "ad_unit_banner_content_detail": self.units["banner"],
            "ad_unit_banner_qibla": self.units["banner"],
            "ad_unit_banner_zikir": self.units["banner"],
            "ad_unit_native_feed_home": self.units["native"],
            "ad_unit_native_feed_content": self.units["native"],
            "ad_unit_native_feed_zikir": self.units["native"],
            "ad_unit_interstitial_nav_break": self.units["interstitial"],
            "ad_unit_open_app_resume": self.units["open_app"],
            "ad_unit_rewarded_rewards_screen": self.units["rewarded"],
            "ad_unit_rewarded_interstitial_history_unlock": self.units["rewarded_interstitial"],
        }
        self.ads_xml_path = self.root / "app/src/sample/res/values/ads.xml"
        self.ads_xml_path.parent.mkdir(parents=True)
        self.write_ads_xml(self.ads_xml_path, values)

        google_test_prefix = "ca-app-pub-3940256099942544"
        debug_values = {
            key: value.replace("ca-app-pub-3312485084079132", google_test_prefix)
            for key, value in values.items()
        }
        debug_path = self.root / "app/src/debug/res/values/ads.xml"
        debug_path.parent.mkdir(parents=True)
        self.write_ads_xml(debug_path, debug_values)
        self.inventory = {
            "schema_version": 1,
            "account": {
                "publisher_id": "pub-3312485084079132",
                "reporting_time_zone": "Europe/Istanbul",
                "currency_code": "TRY",
            },
            "audit": {
                "captured_on": "2026-07-13",
                "freshness_max_days": 90,
                "traffic_window": {
                    "start_date": "2026-04-14",
                    "end_date": "2026-07-12",
                    "inclusive_days": 90,
                },
                "live_counts": {
                    "apps": 2,
                    "approved_apps": 1,
                    "action_required_apps": 1,
                },
                "sources": {"framework_catalog": ".ci/apps.json"},
            },
            "approved_framework_apps": [
                {
                    "flavor": self.flavor,
                    "package": self.package,
                    "display_name": "Sample",
                    "admob_app_id": self.app_id,
                    "linked_store_id": self.package,
                    "approval_state": "APPROVED",
                }
            ],
            "approved_external_apps": [],
            "action_required_apps": [
                {
                    "display_name": "Legacy Sample",
                    "admob_app_id": "ca-app-pub-3312485084079132~1000000002",
                    "approval_state": "ACTION_REQUIRED",
                    "linked_store_id": None,
                    "relationship": {
                        "type": "duplicate_of_framework_app",
                        "target_flavor": self.flavor,
                    },
                    "ad_units": [],
                    "traffic_90d": {
                        "ad_requests": 0,
                        "matched_requests": 0,
                        "impressions": 0,
                        "clicks": 0,
                        "estimated_earnings_micros": 0,
                    },
                    "recommended_action": "archive_or_remove_after_console_confirmation",
                    "manual_console_status": "pending",
                }
            ],
            "mediation_audit": {
                "api_status": "permission_denied",
                "manual_action": "Grant read access.",
            },
            "cleanup_policy": {"minimum_zero_traffic_days": 90},
        }

    @staticmethod
    def write_ads_xml(path: Path, values: dict[str, str]) -> None:
        path.write_text(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n"
            + "\n".join(
                f'  <string name="{key}" translatable="false">{value}</string>'
                for key, value in values.items()
            )
            + "\n</resources>\n",
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def validate(self, inventory: dict | None = None, *, today: date = date(2026, 7, 13)):
        return validate_inventory(
            self.root,
            inventory or self.inventory,
            today=today,
        )

    def test_valid_inventory_matches_catalog_and_resources(self) -> None:
        errors, report = self.validate()

        self.assertEqual(errors, [])
        self.assertEqual(report["framework_app_count"], 1)
        self.assertEqual(report["framework_primary_ad_unit_count"], 6)
        self.assertEqual(report["pending_manual_cleanup_count"], 1)

    def test_ads_xml_parser_returns_named_values(self) -> None:
        values = parse_ads_xml(self.ads_xml_path)

        self.assertEqual(values["admob_app_id"], self.app_id)
        self.assertEqual(values["ad_unit_native"], self.units["native"])

    def test_ads_xml_parser_rejects_entity_declarations(self) -> None:
        self.ads_xml_path.write_text(
            "<!DOCTYPE resources [<!ENTITY leak SYSTEM 'file:///etc/passwd'>]>"
            '<resources><string name="admob_app_id">&leak;</string></resources>',
            encoding="utf-8",
        )

        with self.assertRaisesRegex(ValueError, "DOCTYPE and ENTITY"):
            parse_ads_xml(self.ads_xml_path)

    def test_ads_xml_parser_rejects_duplicate_names(self) -> None:
        self.ads_xml_path.write_text(
            "<resources>"
            '<string name="admob_app_id">first</string>'
            '<string name="admob_app_id">second</string>'
            "</resources>",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(ValueError, "Duplicate string resource"):
            parse_ads_xml(self.ads_xml_path)

    def test_nonzero_cleanup_traffic_blocks_cleanup_recommendation(self) -> None:
        inventory = copy.deepcopy(self.inventory)
        inventory["action_required_apps"][0]["traffic_90d"]["impressions"] = 1

        errors, _ = self.validate(inventory)

        self.assertTrue(any("cannot recommend cleanup" in error for error in errors))

    def test_cleanup_ad_unit_cannot_overlap_production_unit(self) -> None:
        inventory = copy.deepcopy(self.inventory)
        inventory["action_required_apps"][0]["ad_units"] = [
            {
                "ad_unit_id": self.units["banner"],
                "name": "legacy_banner",
                "format": "BANNER",
            }
        ]

        errors, _ = self.validate(inventory)

        self.assertTrue(any("leak into production resources" in error for error in errors))
        self.assertTrue(any("overlap production units" in error for error in errors))

    def test_alias_resource_drift_is_blocked(self) -> None:
        xml_path = self.ads_xml_path
        xml_path.write_text(
            xml_path.read_text().replace(
                f'name="ad_unit_banner_home" translatable="false">{self.units["banner"]}',
                'name="ad_unit_banner_home" translatable="false">ca-app-pub-3312485084079132/2999999998',
            ),
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(any("ad_unit_banner_home" in error for error in errors))

    def test_debug_resources_must_use_google_test_ids(self) -> None:
        debug_path = self.root / "app/src/debug/res/values/ads.xml"
        debug_path.write_text(
            debug_path.read_text().replace(
                "ca-app-pub-3940256099942544/2000000001",
                self.units["banner"],
            ),
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(any("debug ads.xml ad_unit_banner is not a Google test ID" in error for error in errors))

    def test_catalog_resource_drift_is_blocked(self) -> None:
        xml_path = self.ads_xml_path
        xml_path.write_text(
            xml_path.read_text().replace(self.units["native"], "ca-app-pub-3312485084079132/2999999999"),
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(any("ad_unit_native" in error for error in errors))

    def test_snapshot_freshness_forces_periodic_reaudit(self) -> None:
        errors, _ = self.validate(today=date(2026, 10, 12))

        self.assertTrue(any("snapshot is stale" in error for error in errors))

    def test_duplicate_relationship_must_target_framework_flavor(self) -> None:
        inventory = copy.deepcopy(self.inventory)
        inventory["action_required_apps"][0]["relationship"]["target_flavor"] = "missing"

        errors, _ = self.validate(inventory)

        self.assertTrue(any("unknown duplicate target" in error for error in errors))

    def test_live_counts_must_cover_every_inventory_scope(self) -> None:
        inventory = copy.deepcopy(self.inventory)
        inventory["audit"]["live_counts"]["apps"] = 3

        errors, _ = self.validate(inventory)

        self.assertTrue(any("audit.live_counts.apps" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
