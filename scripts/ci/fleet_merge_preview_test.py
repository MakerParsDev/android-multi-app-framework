#!/usr/bin/env python3
"""Unit tests for scripts/ci/fleet_merge_preview.py."""

from __future__ import annotations

import os
import sys
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, os.path.dirname(__file__))

from fleet_merge_preview import (
    fetch_open_prs,
    is_fleet_ready_for_mergify,
    preview_ready_prs,
)


class TestFleetMergePreview(unittest.TestCase):
    def test_ready_policy_requires_same_repo_jules_low_risk(self):
        repo = "owner/repo"
        good = {
            "number": 10,
            "head": {
                "ref": "fix-171-live-canary-verification-3904992159357426987",
                "repo": {"full_name": repo},
            },
            "body": (
                "Fixes #171\n"
                "https://jules.google.com/task/3904992159357426987"
            ),
            "labels": [
                {"name": "fleet-merge-ready"},
                {"name": "risk:low"},
            ],
        }
        self.assertTrue(is_fleet_ready_for_mergify(good, repo))

        forked = {
            **good,
            "head": {
                "ref": "jules/fix-171-3904992159357426987",
                "repo": {"full_name": "fork/repo"},
            },
        }
        self.assertFalse(is_fleet_ready_for_mergify(forked, repo))

        non_jules = {
            **good,
            "head": {
                "ref": "feature/spoof",
                "repo": {"full_name": repo},
            },
        }
        self.assertFalse(is_fleet_ready_for_mergify(non_jules, repo))

        protected = {
            **good,
            "labels": [
                {"name": "fleet-merge-ready"},
                {"name": "risk:low"},
                {"name": "fleet-review-required"},
            ],
        }
        self.assertFalse(is_fleet_ready_for_mergify(protected, repo))

    @patch("fleet_merge_preview._make_request")
    def test_fetch_open_prs_paginates(self, mock_request: MagicMock):
        first = [{"number": n} for n in range(1, 101)]
        second = [{"number": 101}]
        mock_request.side_effect = [(200, first), (200, second)]

        result = fetch_open_prs("owner/repo", "token")

        self.assertEqual(len(result), 101)
        self.assertEqual(mock_request.call_count, 2)

    @patch("fleet_merge_preview.fetch_open_prs")
    def test_preview_returns_only_ready_prs(self, mock_fetch: MagicMock):
        repo = "owner/repo"
        mock_fetch.return_value = [
            {
                "number": 10,
                "title": "ready",
                "html_url": "https://example.invalid/10",
                "head": {
                    "ref": "fix-171-live-canary-verification-3904992159357426987",
                    "repo": {"full_name": repo},
                },
                "body": (
                    "Fixes #171\n"
                    "https://jules.google.com/task/3904992159357426987"
                ),
                "labels": [
                    {"name": "fleet-merge-ready"},
                    {"name": "risk:low"},
                ],
            },
            {
                "number": 11,
                "title": "review",
                "html_url": "https://example.invalid/11",
                "head": {
                    "ref": "fix-172-review-3904992159357426999",
                    "repo": {"full_name": repo},
                },
                "body": (
                    "Fixes #172\n"
                    "https://jules.google.com/task/3904992159357426999"
                ),
                "labels": [
                    {"name": "fleet-review-required"},
                    {"name": "risk:protected"},
                ],
            },
        ]

        result = preview_ready_prs(repo, "token")

        self.assertEqual(
            result,
            [
                {
                    "number": 10,
                    "title": "ready",
                    "head": "fix-171-live-canary-verification-3904992159357426987",
                    "url": "https://example.invalid/10",
                }
            ],
        )

    @patch("fleet_merge_preview._make_request")
    def test_fetch_open_prs_fails_closed(self, mock_request: MagicMock):
        mock_request.return_value = (500, {"message": "server error"})
        with self.assertRaises(RuntimeError):
            fetch_open_prs("owner/repo", "token")


if __name__ == "__main__":
    unittest.main()
