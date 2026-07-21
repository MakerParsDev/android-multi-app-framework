#!/usr/bin/env python3
"""Unit tests for the strict Play versionCode release gate."""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from fetch_play_version_codes import (
    FlavorInfo,
    evaluate_version_gate,
    read_version_codes,
)


class FetchPlayVersionCodesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.flavor = FlavorInfo(name="sample", package_name="com.example.sample")

    def test_reads_positive_version_codes_and_ignores_other_properties(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "app-versions.properties"
            path.write_text(
                "# comment\nsample.versionCode=12\nsample.versionName=1.0.12\n",
                encoding="utf-8",
            )

            self.assertEqual(read_version_codes(path), {"sample": 12})

    def test_gate_passes_when_repo_code_is_greater_than_all_tracks(self) -> None:
        results = evaluate_version_gate(
            selected=[self.flavor],
            out_apps={
                "sample": {
                    "tracks": {"production": [10], "internal": [11]},
                    "maxVersionCode": 11,
                }
            },
            repo_version_codes={"sample": 12},
        )

        self.assertTrue(results[0].passed)
        self.assertEqual(results[0].status, "OK")
        self.assertEqual(results[0].max_tracks, ("internal",))

    def test_gate_blocks_equal_or_lower_repo_code(self) -> None:
        for repo_code in (11, 10):
            with self.subTest(repo_code=repo_code):
                result = evaluate_version_gate(
                    selected=[self.flavor],
                    out_apps={
                        "sample": {
                            "tracks": {"production": [11]},
                            "maxVersionCode": 11,
                        }
                    },
                    repo_version_codes={"sample": repo_code},
                )[0]

                self.assertFalse(result.passed)
                self.assertEqual(result.status, "BLOCKED")
                self.assertIn("at least 12", result.message)

    def test_gate_blocks_missing_repo_code_and_lookup_error(self) -> None:
        missing = evaluate_version_gate(
            selected=[self.flavor],
            out_apps={"sample": {"tracks": {}, "maxVersionCode": None}},
            repo_version_codes={},
        )[0]
        lookup_error = evaluate_version_gate(
            selected=[self.flavor],
            out_apps={"sample": {"error": "permission denied"}},
            repo_version_codes={"sample": 12},
        )[0]

        self.assertEqual(missing.status, "MISSING_REPO_CODE")
        self.assertFalse(missing.passed)
        self.assertEqual(lookup_error.status, "LOOKUP_ERROR")
        self.assertFalse(lookup_error.passed)

    def test_gate_allows_brand_new_app_with_positive_repo_code(self) -> None:
        result = evaluate_version_gate(
            selected=[self.flavor],
            out_apps={"sample": {"tracks": {}, "maxVersionCode": None}},
            repo_version_codes={"sample": 1},
        )[0]

        self.assertEqual(result.status, "NEW_APP")
        self.assertTrue(result.passed)


if __name__ == "__main__":
    unittest.main()
