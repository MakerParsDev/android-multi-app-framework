#!/usr/bin/env python3
"""Unit tests for fetch_github_sbom.py."""

from __future__ import annotations

import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))

import fetch_github_sbom as sbom


class FetchGithubSbomTest(unittest.TestCase):
    @patch("fetch_github_sbom.time.sleep")
    @patch("fetch_github_sbom._request_json")
    def test_async_generate_then_poll_writes_raw_spdx(
        self, request_json, sleep
    ):
        repo = "owner/repo"
        report_url = (
            "https://api.github.com/repos/owner/repo/"
            "dependency-graph/sbom/fetch-report/abc123"
        )
        report = {
            "spdxVersion": "SPDX-2.3",
            "packages": [{"name": "demo", "SPDXID": "SPDXRef-demo"}],
            "relationships": [],
        }
        request_json.side_effect = [
            (201, {"sbom_url": report_url}),
            (202, {}),
            (200, report),
        ]

        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir) / "repo.spdx.json"
            result = sbom.fetch_sbom(
                repo,
                output,
                token="token",
                poll_seconds=0,
                timeout_seconds=30,
            )
            self.assertEqual(result, report)
            self.assertEqual(json.loads(output.read_text()), report)

        self.assertEqual(request_json.call_count, 3)
        generate_call = request_json.call_args_list[0]
        self.assertEqual(generate_call.kwargs["token"], "token")
        self.assertTrue(generate_call.args[0].endswith("/generate-report"))
        for fetch_call in request_json.call_args_list[1:]:
            self.assertEqual(fetch_call.args[0], report_url)
            self.assertEqual(fetch_call.kwargs["token"], "token")
        sleep.assert_called_once_with(0)

    @patch("fetch_github_sbom._request_json")
    def test_report_url_must_remain_on_expected_repository_api(self, request_json):
        request_json.return_value = (
            201,
            {"sbom_url": "https://evil.invalid/steal"},
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaises(sbom.SbomFetchError):
                sbom.fetch_sbom(
                    "owner/repo",
                    Path(temp_dir) / "repo.spdx.json",
                    token="token",
                )

    def test_redirect_keeps_token_on_api_host_only(self):
        source = urllib.request.Request(
            "https://api.github.com/repos/owner/repo/dependency-graph/sbom/fetch-report/abc123",
            headers={"Authorization": "Bearer secret"},
        )
        handler = sbom._SafeRedirectHandler()

        api_redirect = handler.redirect_request(
            source, None, 302, "Found", {},
            "https://api.github.com/other-path",
        )
        self.assertEqual(api_redirect.get_header("Authorization"), "Bearer secret")

        storage_redirect = handler.redirect_request(
            source, None, 302, "Found", {},
            "https://storage.example.invalid/temporary-report",
        )
        self.assertIsNone(storage_redirect.get_header("Authorization"))

        with self.assertRaises(sbom.SbomFetchError):
            handler.redirect_request(
                source, None, 302, "Found", {},
                "http://storage.example.invalid/insecure",
            )

    @patch("fetch_github_sbom._request_json")
    def test_empty_packages_fail_closed(self, request_json):
        report_url = (
            "https://api.github.com/repos/owner/repo/"
            "dependency-graph/sbom/fetch-report/abc123"
        )
        request_json.side_effect = [
            (201, {"sbom_url": report_url}),
            (200, {"spdxVersion": "SPDX-2.3", "packages": []}),
        ]
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaises(sbom.SbomFetchError):
                sbom.fetch_sbom(
                    "owner/repo",
                    Path(temp_dir) / "repo.spdx.json",
                )

    def test_repository_format_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaises(sbom.SbomFetchError):
                sbom.fetch_sbom(
                    "../not-a-repo",
                    Path(temp_dir) / "repo.spdx.json",
                )


if __name__ == "__main__":
    unittest.main()
