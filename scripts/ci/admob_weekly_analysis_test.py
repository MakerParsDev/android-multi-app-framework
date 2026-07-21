#!/usr/bin/env python3
"""Regression tests for weekly AdMob optimization analysis."""

from __future__ import annotations

import unittest
from datetime import date

from check_admob_weekly_optimization import completed_week_end, resolve_generated_at
from admob_weekly_analysis import (
    aggregate_rows,
    build_weekly_analysis,
    change_pct,
    compare_aggregates,
    detect_opportunities,
)
from check_admob_weekly_optimization import parse_report_payload


class AdMobWeeklyAnalysisTest(unittest.TestCase):
    def test_aggregate_calculates_funnel_and_rpm(self) -> None:
        rows = [
            {
                "format": "native",
                "ad_requests": 200,
                "matched_requests": 160,
                "impressions": 40,
                "clicks": 2,
                "earnings_micros": 2_000_000,
            },
            {
                "format": "native",
                "ad_requests": 100,
                "matched_requests": 80,
                "impressions": 20,
                "clicks": 1,
                "earnings_micros": 1_000_000,
            },
        ]

        result = aggregate_rows(rows, ("format",))[0]

        self.assertEqual(result["ad_requests"], 300)
        self.assertEqual(result["matched_requests"], 240)
        self.assertEqual(result["impressions"], 60)
        self.assertEqual(result["match_rate"], 0.8)
        self.assertEqual(result["show_rate"], 0.25)
        self.assertEqual(result["impression_rpm_try"], 50.0)

    def test_compare_reports_week_over_week_changes(self) -> None:
        current = aggregate_rows(
            [{"format": "banner", "impressions": 120, "earnings_micros": 12_000_000}],
            ("format",),
        )
        previous = aggregate_rows(
            [{"format": "banner", "impressions": 100, "earnings_micros": 10_000_000}],
            ("format",),
        )

        comparison = compare_aggregates(current, previous, ("format",))[0]

        self.assertEqual(comparison["changes_pct"]["impressions"], 20.0)
        self.assertEqual(comparison["changes_pct"]["earnings_try"], 20.0)

    def test_zero_previous_change_is_bounded(self) -> None:
        self.assertIsNone(change_pct(0, 0))
        self.assertEqual(change_pct(1, 0), 100.0)

    def test_opportunity_flags_zero_impressions_with_inventory(self) -> None:
        comparisons = [
            {
                "app": "Sample",
                "format": "native",
                "app_version": "1.0",
                "ad_unit": "unit",
                "current": {
                    "ad_requests": 150,
                    "matched_requests": 120,
                    "impressions": 0,
                    "match_rate": 0.8,
                    "show_rate": 0.0,
                },
                "changes_pct": {"show_rate": -100.0},
            }
        ]

        opportunities = detect_opportunities(
            comparisons,
            ("app", "format", "app_version", "ad_unit"),
        )

        self.assertEqual(opportunities[0]["reasons"], ["zero_impressions", "show_rate_decline"])

    def test_report_parser_preserves_platform_version_and_resource_ids(self) -> None:
        payload = [
            {"header": {}},
            {
                "row": {
                    "dimensionValues": {
                        "APP": {"value": "app-id", "displayLabel": "Sample App"},
                        "FORMAT": {"value": "interstitial"},
                        "PLATFORM": {"value": "Android"},
                        "APP_VERSION_NAME": {"value": "1.2.3"},
                        "AD_UNIT": {"value": "unit-id", "displayLabel": "sample_interstitial"},
                    },
                    "metricValues": {
                        "AD_REQUESTS": {"integerValue": "10"},
                        "MATCHED_REQUESTS": {"integerValue": "8"},
                        "IMPRESSIONS": {"integerValue": "2"},
                        "CLICKS": {"integerValue": "1"},
                        "ESTIMATED_EARNINGS": {"microsValue": "250000"},
                    },
                }
            },
            {"footer": {}},
        ]

        row = parse_report_payload(payload)[0]

        self.assertEqual(row["app"], "Sample App")
        self.assertEqual(row["app_id"], "app-id")
        self.assertEqual(row["platform"], "Android")
        self.assertEqual(row["app_version"], "1.2.3")
        self.assertEqual(row["ad_unit"], "sample_interstitial")
        self.assertEqual(row["earnings_micros"], 250_000)

    def test_weekly_analysis_generates_bounded_hypotheses(self) -> None:
        current = [
            self.row("Sample", "native", (1000, 800, 40, 2, 4_000_000)),
            self.row("Sample", "interstitial", (1000, 800, 80, 5, 20_000_000)),
            self.row("Sample", "rewarded", (200, 190, 5, 1, 3_000_000)),
        ]
        previous = [
            self.row("Sample", "native", (1000, 800, 80, 3, 5_000_000)),
            self.row("Sample", "interstitial", (900, 700, 90, 4, 19_000_000)),
            self.row("Sample", "rewarded", (180, 170, 8, 1, 2_000_000)),
        ]
        countries = [
            {
                "country": "NoFillLand",
                "format": "interstitial",
                "ad_requests": 600,
                "matched_requests": 0,
                "impressions": 0,
                "clicks": 0,
                "earnings_micros": 0,
            }
        ]

        analysis = build_weekly_analysis(current, previous, countries, countries)
        ids = [item["id"] for item in analysis["hypotheses"]]

        self.assertEqual(len(analysis["app_formats"]), 3)
        self.assertIn("countries", analysis)
        self.assertIn("native_visibility_recovery", ids)
        self.assertIn("fullscreen_readiness_recovery", ids)
        self.assertIn("rewarded_opt_in_discovery", ids)
        self.assertIn("geo_no_fill_diagnosis", ids)
        for hypothesis in analysis["hypotheses"]:
            self.assertIn("rollback", hypothesis)
            self.assertIn("remote_config_bounds", hypothesis)


    def test_fixture_timestamp_is_deterministic(self) -> None:
        source = {"evidence": {"captured_on": "2026-07-13"}}

        generated_at = resolve_generated_at(None, source, True, date(2026, 7, 12))

        self.assertEqual(generated_at, "2026-07-13T00:00:00+03:00")

    def test_completed_week_uses_previous_sunday_on_sunday(self) -> None:
        self.assertEqual(completed_week_end(date(2026, 7, 12)), date(2026, 7, 5))
        self.assertEqual(completed_week_end(date(2026, 7, 13)), date(2026, 7, 12))

    @staticmethod
    def row(
        app: str,
        ad_format: str,
        metrics: tuple[int, int, int, int, int],
    ) -> dict:
        requests, matched, impressions, clicks, earnings = metrics
        return {
            "app": app,
            "app_id": "app-id",
            "format": ad_format,
            "app_version": "1.0",
            "ad_unit": f"{ad_format}-unit",
            "ad_requests": requests,
            "matched_requests": matched,
            "impressions": impressions,
            "clicks": clicks,
            "earnings_micros": earnings,
        }


if __name__ == "__main__":
    unittest.main()
