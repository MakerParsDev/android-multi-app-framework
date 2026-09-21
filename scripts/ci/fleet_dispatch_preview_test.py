#!/usr/bin/env python3
"""Unit tests for scripts/ci/fleet_dispatch_preview.py."""

from __future__ import annotations

import os
import sys
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, os.path.dirname(__file__))

from fleet_dispatch_preview import (
    DISPATCH_MARKER,
    _paged_get,
    has_dispatch_event,
    is_target_fleet_issue,
    linked_pr_numbers,
    preview_candidates,
)


class TestFleetDispatchPreview(unittest.TestCase):
    def test_target_issue_policy(self):
        self.assertTrue(
            is_target_fleet_issue(
                {
                    "number": 1,
                    "labels": [{"name": "fleet"}],
                }
            )
        )
        self.assertFalse(
            is_target_fleet_issue(
                {
                    "number": 2,
                    "labels": [{"name": "fleet"}],
                    "pull_request": {"url": "https://example.invalid/pr/2"},
                }
            )
        )
        self.assertFalse(
            is_target_fleet_issue(
                {
                    "number": 3,
                    "labels": [
                        {"name": "fleet"},
                        {"name": "status: ignore"},
                    ],
                }
            )
        )
        self.assertFalse(
            is_target_fleet_issue(
                {
                    "number": 4,
                    "labels": [{"name": "bug"}],
                }
            )
        )

    def test_dispatch_marker_and_linked_pr_detection(self):
        self.assertTrue(
            has_dispatch_event(
                [{"body": f"{DISPATCH_MARKER}\nSession: 1234567890"}]
            )
        )
        self.assertFalse(has_dispatch_event([{"body": "ordinary comment"}]))

        prs = [
            {"number": 10, "body": "Fixes #171"},
            {"number": 11, "body": "Unrelated change"},
            {"number": 12, "body": "Tracking #171 while this is open"},
        ]
        self.assertEqual(linked_pr_numbers(171, prs), [10, 12])

    @patch("fleet_dispatch_preview.fetch_issue_comments")
    @patch("fleet_dispatch_preview.fetch_open_prs")
    @patch("fleet_dispatch_preview.fetch_open_fleet_issues")
    def test_preview_candidates_matches_dispatch_safety_rules(
        self,
        mock_issues: MagicMock,
        mock_prs: MagicMock,
        mock_comments: MagicMock,
    ):
        mock_issues.return_value = [
            {"number": 171, "title": "candidate", "labels": [{"name": "fleet"}]},
            {"number": 172, "title": "dispatched", "labels": [{"name": "fleet"}]},
            {"number": 173, "title": "linked", "labels": [{"name": "fleet"}]},
        ]
        mock_prs.return_value = [{"number": 99, "body": "Fixes #173"}]

        def comments(_repo: str, issue_number: int, _token: str):
            if issue_number == 172:
                return [{"body": f"{DISPATCH_MARKER}\nSession: 1234567890"}]
            return []

        mock_comments.side_effect = comments

        candidates, skipped = preview_candidates("owner/repo", 5, "token")

        self.assertEqual(candidates, [{"number": 171, "title": "candidate"}])
        self.assertEqual(
            skipped,
            [
                {
                    "number": 172,
                    "title": "dispatched",
                    "reason": "already dispatched",
                },
                {
                    "number": 173,
                    "title": "linked",
                    "reason": "open linked PR(s): 99",
                },
            ],
        )

    @patch("fleet_dispatch_preview._make_request")
    def test_paged_get_reads_all_pages(self, mock_request: MagicMock):
        page_one = [{"number": n} for n in range(1, 101)]
        page_two = [{"number": 101}]
        mock_request.side_effect = [
            (200, page_one),
            (200, page_two),
        ]

        result = _paged_get("https://api.example.invalid/items?state=open", "token")

        self.assertEqual(len(result), 101)
        self.assertEqual(mock_request.call_count, 2)
        self.assertIn("page=1", mock_request.call_args_list[0][0][0])
        self.assertIn("page=2", mock_request.call_args_list[1][0][0])

    @patch("fleet_dispatch_preview._make_request")
    def test_paged_get_fails_closed_on_api_error(self, mock_request: MagicMock):
        mock_request.return_value = (403, {"message": "forbidden"})
        with self.assertRaises(RuntimeError):
            _paged_get("https://api.example.invalid/items", "token")


if __name__ == "__main__":
    unittest.main()
