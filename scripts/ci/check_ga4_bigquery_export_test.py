#!/usr/bin/env python3
"""Unit tests for the read-only GA4 BigQuery table metadata verifier."""

from __future__ import annotations

from datetime import date
import io
import json
import subprocess
import unittest
from unittest.mock import patch

from check_ga4_bigquery_export import audit, expected_tables, inspect_table, main


PROJECT = "makerpars-oaslananka-mobil"
DATASET = "analytics_526790653"


def table_result(name: str, *, kind: str = "TABLE") -> subprocess.CompletedProcess:
    return subprocess.CompletedProcess(
        [], 0,
        json.dumps({
            "tableReference": {"projectId": PROJECT, "datasetId": DATASET, "tableId": name},
            "type": kind,
        }),
        "",
    )


def not_found(name: str) -> subprocess.CompletedProcess:
    return subprocess.CompletedProcess(
        [], 2,
        f"BigQuery error in show operation: Not found: Table {PROJECT}:{DATASET}.{name}",
        "",
    )


class GA4BigQueryExportTest(unittest.TestCase):
    def test_matured_dates_skip_recent_days(self) -> None:
        self.assertEqual(expected_tables(date(2026, 10, 19)), [
            "events_20261015", "events_20261016", "events_20261017",
        ])

    def test_all_matured_dates_pass_without_data_query(self) -> None:
        names = expected_tables(date(2026, 10, 19))
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            run.side_effect = [table_result(name) for name in names]
            result = audit(project=PROJECT, dataset=DATASET, as_of=date(2026, 10, 19), days=3, lag_days=2)
        self.assertEqual(result["status"], "pass")
        self.assertEqual(result["verified_daily_table_count"], 3)
        self.assertIs(result["row_and_stream_contents_verified"], False)
        self.assertEqual(run.call_count, 3)
        for call, name in zip(run.call_args_list, names):
            self.assertEqual(call.args[0], ["bq", "show", "--format=json", f"{PROJECT}:{DATASET}.{name}"])
            self.assertIs(call.kwargs["check"], False)

    def test_missing_daily_tables_are_explicit(self) -> None:
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            names = expected_tables(date(2026, 10, 19))
            run.side_effect = [not_found(name) for name in names]
            result = audit(project=PROJECT, dataset=DATASET, as_of=date(2026, 10, 19), days=3, lag_days=2)
        self.assertEqual(result["status"], "missing_or_invalid_daily_tables")
        self.assertEqual(result["missing_tables"], names)
        self.assertEqual(result["verified_daily_table_count"], 0)

    def test_views_cannot_satisfy_daily_export(self) -> None:
        names = expected_tables(date(2026, 10, 19))
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            run.side_effect = [
                table_result(names[0]), table_result(names[1], kind="VIEW"), table_result(names[2]),
            ]
            result = audit(project=PROJECT, dataset=DATASET, as_of=date(2026, 10, 19), days=3, lag_days=2)
        self.assertEqual(result["wrong_type_tables"], [names[1]])
        self.assertEqual(result["verified_daily_table_count"], 2)
        self.assertNotEqual(result["status"], "pass")

    def test_one_table_metadata_lookup_scales_to_any_dataset(self) -> None:
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            run.return_value = table_result("events_20261017")
            self.assertEqual(
                inspect_table(project=PROJECT, dataset=DATASET, name="events_20261017"), "present",
            )
            run.assert_called_once()

    def test_invalid_dataset_rejected_before_subprocess(self) -> None:
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            with self.assertRaisesRegex(ValueError, "identifier"):
                audit(project=PROJECT, dataset="x;rm -rf", as_of=date(2026, 10, 19), days=3, lag_days=2)
            run.assert_not_called()

    def test_unexpected_table_metadata_rejected(self) -> None:
        data = json.loads(table_result("events_20261017").stdout)
        data["tableReference"]["projectId"] = "wrong"
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 0, json.dumps(data), "")
            with self.assertRaisesRegex(ValueError, "unexpected"):
                inspect_table(project=PROJECT, dataset=DATASET, name="events_20261017")

    def test_permission_denied_is_error_not_missing(self) -> None:
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 1, "Access Denied", "")
            with self.assertRaisesRegex(RuntimeError, "exit 1"):
                inspect_table(project=PROJECT, dataset=DATASET, name="events_20261017")

    def test_missing_dataset_is_error_not_missing_table(self) -> None:
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 2, "Not found: Dataset xyz", "")
            with self.assertRaises(RuntimeError):
                inspect_table(project=PROJECT, dataset=DATASET, name="events_20261017")

    def test_nonpositive_lag_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "positive"):
            expected_tables(date(2026, 10, 19), lag_days=0)

    def test_unparsable_response_rejected(self) -> None:
        with patch("check_ga4_bigquery_export.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 0, "{", "")
            with self.assertRaises(json.JSONDecodeError):
                inspect_table(project=PROJECT, dataset=DATASET, name="events_20261017")

    def test_invalid_cli_options_return_error_not_missing(self) -> None:
        for arguments in (
            ["--as-of-date", "2026-10-19", "--days", "abc"],
            ["--as-of-date"],
            ["--unknown-option", "x"],
        ):
            with self.subTest(arguments=arguments):
                with patch("sys.argv", ["audit.py", *arguments]):
                    with patch("check_ga4_bigquery_export.subprocess.run") as run:
                        with patch("sys.stdout", new_callable=io.StringIO) as output:
                            self.assertEqual(main(), 1)
                        self.assertIn('"status": "error"', output.getvalue())
                        run.assert_not_called()

    def test_help_remains_successful(self) -> None:
        with patch("sys.argv", ["audit.py", "--help"]):
            with patch("sys.stdout"):
                with self.assertRaises(SystemExit) as outcome:
                    main()
            self.assertEqual(outcome.exception.code, 0)

    def test_property_date_is_required(self) -> None:
        with patch("sys.argv", ["check_ga4_bigquery_export.py"]):
            with patch("check_ga4_bigquery_export.subprocess.run") as run:
                with patch("sys.stdout"):
                    self.assertEqual(main(), 1)
                run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
