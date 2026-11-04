#!/usr/bin/env python3
"""Offline contract checks for the GA4 app-owned ad funnel query template."""

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SQL = ROOT / "docs" / "analytics" / "BIGQUERY_QUERIES.sql"
CONTRACT = ROOT / "core" / "firebase" / "src" / "main" / "java" / "com" / "parsfilo" / "contentapp" / "core" / "firebase" / "AnalyticsContract.kt"


def funnel_query() -> str:
    sql = SQL.read_text(encoding="utf-8")
    section = sql.split("-- 2) ", 1)[1].split("-- 3) ", 1)[0]
    return "\n".join(line for line in section.splitlines() if not line.lstrip().startswith("--"))


class BigQueryAdFunnelContractTest(unittest.TestCase):
    def test_source_placement_parameter_is_not_legacy_key(self) -> None:
        self.assertIn('const val PLACEMENT = "ad_placement"', CONTRACT.read_text(encoding="utf-8"))
        query = funnel_query()
        self.assertIn("ep.key = 'ad_placement'", query)
        self.assertNotIn("ep.key = 'placement'", query)

    def test_sdk_autocollected_impressions_are_not_client_placement_callbacks(self) -> None:
        query = funnel_query()
        self.assertIn("'ad_placement_impression'", query)
        self.assertIn("placement_impression_callbacks", query)
        self.assertNotIn("'ad_impression'", query)
        self.assertNotIn(" AS impressions", query)
        self.assertNotIn("show_rate_pct", query)

    def test_counters_are_scoped_to_one_app_and_version(self) -> None:
        query = funnel_query()
        self.assertIn("app_info.id", query)
        self.assertIn("app_info.version", query)
        self.assertIn("GROUP BY event_date, app_package, app_version, ad_format, ad_placement", query)

    def test_regression_runs_in_standard_security_pipeline(self) -> None:
        security = (ROOT / ".github" / "workflows" / "security.yml").read_text(encoding="utf-8")
        self.assertIn("python3 scripts/ci/bigquery_ad_funnel_contract_test.py", security)

    def test_ui_handoff_and_inventory_discard_are_separate_from_impressions(self) -> None:
        query = funnel_query()
        self.assertIn("COUNTIF(event_name = 'ad_served') AS ui_handoffs", query)
        self.assertIn("COUNTIF(event_name = 'ad_inventory_discarded') AS native_discarded_before_handoff", query)
        self.assertIn("COUNTIF(event_name = 'ad_failed_to_load') AS failed_to_load_events", query)


if __name__ == "__main__":
    unittest.main()
