#!/usr/bin/env python3
"""
Unit tests for scripts/ci/classify_pr.py
"""

from __future__ import annotations

import os
import sys
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, os.path.dirname(__file__))
from classify_pr import fetch_all_pr_files, update_pr_labels


class TestClassifyPR(unittest.TestCase):
    @patch("classify_pr._make_request")
    def test_pagination_fetches_multiple_pages(self, mock_req: MagicMock):
        # Page 1 has 100 files, Page 2 has 25 files
        page1 = [{"filename": f"app/src/File{i}.kt"} for i in range(100)]
        page2 = [{"filename": f"app/src/File{i}.kt"} for i in range(100, 125)]

        mock_req.side_effect = [
            (200, page1),
            (200, page2),
        ]

        files = fetch_all_pr_files("owner/repo", 123, "fake-token")
        self.assertEqual(len(files), 125)
        self.assertEqual(mock_req.call_count, 2)

    @patch("classify_pr._make_request")
    def test_update_labels_low_risk(self, mock_req: MagicMock):
        mock_req.side_effect = [
            (200, {}),  # add fleet-merge-ready
            (200, {}),  # add risk:low
            (200, {}),  # del fleet-review-required
            (200, {}),  # del risk:protected
        ]
        update_pr_labels("owner/repo", 123, "fake-token", "LOW_RISK")
        self.assertEqual(mock_req.call_count, 4)

        add_call = mock_req.call_args_list[0]
        self.assertIn("issues/123/labels", add_call[0][0])
        self.assertEqual(add_call[1]["data"], {"labels": ["fleet-merge-ready"]})

        add_call = mock_req.call_args_list[1]
        self.assertEqual(add_call[1]["data"], {"labels": ["risk:low"]})

    @patch("classify_pr._make_request")
    def test_update_labels_protected(self, mock_req: MagicMock):
        mock_req.side_effect = [
            (200, {}),  # add fleet-review-required
            (200, {}),  # add risk:protected
            (200, {}),  # del fleet-merge-ready
            (200, {}),  # del risk:low
        ]
        update_pr_labels("owner/repo", 123, "fake-token", "PROTECTED")
        self.assertEqual(mock_req.call_count, 4)

        add_call = mock_req.call_args_list[0]
        self.assertIn("issues/123/labels", add_call[0][0])
        self.assertEqual(add_call[1]["data"], {"labels": ["fleet-review-required"]})

        add_call = mock_req.call_args_list[1]
        self.assertEqual(add_call[1]["data"], {"labels": ["risk:protected"]})

    @patch("classify_pr._make_request")
    def test_update_labels_add_failure_raises(self, mock_req: MagicMock):
        mock_req.return_value = (
            403,
            {"message": "Resource not accessible by integration"},
        )
        with self.assertRaises(RuntimeError) as ctx:
            update_pr_labels("owner/repo", 123, "fake-token", "LOW_RISK")
        self.assertIn("Failed to add label", str(ctx.exception))

    @patch("classify_pr._make_request")
    def test_update_labels_remove_404_ignored(self, mock_req: MagicMock):
        # add fleet-merge-ready, add risk:low, del fleet-review-required (404), del risk:protected (404)
        mock_req.side_effect = [
            (200, {}),  # add fleet-merge-ready
            (200, {}),  # add risk:low
            (404, {"message": "Label does not exist"}),  # del fleet-review-required
            (404, {"message": "Label does not exist"}),  # del risk:protected
        ]
        # Should NOT raise
        update_pr_labels("owner/repo", 123, "fake-token", "LOW_RISK")
        self.assertEqual(mock_req.call_count, 4)

    @patch("classify_pr._make_request")
    def test_update_labels_remove_other_error_raises(self, mock_req: MagicMock):
        # add fleet-merge-ready, add risk:low, del fleet-review-required (200), del risk:protected (500)
        mock_req.side_effect = [
            (200, {}),  # add fleet-merge-ready
            (200, {}),  # add risk:low
            (200, {}),  # del fleet-review-required
            (500, {"message": "Internal Server Error"}),  # del risk:protected
        ]
        with self.assertRaises(RuntimeError) as ctx:
            update_pr_labels("owner/repo", 123, "fake-token", "LOW_RISK")
        self.assertIn("Failed to remove label 'risk:protected'", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
