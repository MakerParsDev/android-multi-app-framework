#!/usr/bin/env python3
"""Unit tests for the metadata-only GA4 export verifier. No cloud access."""

from __future__ import annotations

from datetime import date
import json
import subprocess
import unittest
from unittest.mock import patch

from check_ga4_bigquery_export import audit, expected_tables, parse_listing


PROJECT = "makerpars-oaslananka-mobil"
DATASET = "analytics_526790653"


def listing(*names: str) -> str:
    return json.dumps(
        [
            {
                "tableReference": {
                    "projectId": PROJECT,
                    "datasetId": DATASET,
                    "tableId": name,
                },
            }
            for name in names
        ]
    )


class GA4BigQueryExportTest(unittest.TestCase):
    def test_matured_dates_skip_yesterday(self) -> None:
        self.assertEqual(
            expected_tables(date(2026, 10, 19)),
            ["events_20261015", "events_20261016", "events_20261017"],
        )

    def test_all_matured_dates_pass_without_query(self) -> None:
        names = expected_tables(date(2026, 10, 19))
        payload = listing("pseudonymous_users_20260916", *names)
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 0, payload, "")
            result = audit(
                project=PROJECT, dataset=DATASET,
                as_of=date(2026, 10, 19), days=3, lag_days=2,
            )
        self.assertEqual(result["status"], "pass")
        self.assertEqual(result["missing_tables"], [])
        self.assertIs(result["row_and_stream_contents_verified"], False)
        run.assert_called_once_with(
            ["bq", "ls", "-n", "1000", "--format=json", f"{PROJECT}:{DATASET}"],
            capture_output=True, text=True, timeout=35, check=False,
        )

    def test_missing_tables_are_explicit(self) -> None:
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess(
                [], 0, listing("pseudonymous_users_20260916"), "",
            )
            result = audit(
                project=PROJECT, dataset=DATASET,
                as_of=date(2026, 10, 19), days=3, lag_days=2,
            )
        self.assertEqual(result["status"], "missing_daily_tables")
        self.assertEqual(result["missing_tables"], expected_tables(date(2026, 10, 19)))
        self.assertEqual(result["daily_table_count"], 0)

    def test_invalid_dataset_rejected_before_any_subprocess(self) -> None:
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            with self.assertRaisesRegex(ValueError, "identifier"):
                audit(
                    project=PROJECT, dataset="x;rm -rf",
                    as_of=date(2026, 10, 19), days=3, lag_days=2,
                )
            run.assert_not_called()

    def test_truncated_list_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "truncated"):
            parse_listing(
                listing(*("events_" + str(i) for i in range(1000))),
                project=PROJECT, dataset=DATASET,
            )

    def test_wrong_project_rejected(self) -> None:
        data = json.loads(listing("events_20261017"))
        data[0]["tableReference"]["projectId"] = "unexpected"
        with self.assertRaisesRegex(ValueError, "unexpected"):
            parse_listing(json.dumps(data), project=PROJECT, dataset=DATASET)

    def test_bq_error_not_misclassified_as_missing(self) -> None:
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 1, "", "permission denied")
            with self.assertRaisesRegex(RuntimeError, "exit code 1"):
                audit(
                    project=PROJECT, dataset=DATASET,
                    as_of=date(2026, 10, 19), days=3, lag_days=2,
                )

    def test_nonpositive_lag_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "positive"):
            expected_tables(date(2026, 10, 19), lag_days=0)

    def test_bad_json_fails_instead_of_reporting_missing(self) -> None:
        with self.assertRaises(json.JSONDecodeError):
            parse_listing("{", project=PROJECT, dataset=DATASET)


if __name__ == "__main__":
    unittest.main()
