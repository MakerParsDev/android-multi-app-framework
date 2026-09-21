#!/usr/bin/env python3
"""
Unit tests for scripts/ci/sync_automerge_label.py
"""

from __future__ import annotations

import os
import sys
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, os.path.dirname(__file__))
from sync_automerge_label import (
    get_open_prs,
    ensure_label_on_pr,
    remove_label_from_pr,
    main,
)


class TestSyncAutoMergeLabel(unittest.TestCase):
    @patch("sync_automerge_label._make_request")
    def test_get_open_prs_pagination(self, mock_req: MagicMock):
        page1 = [{"number": i, "labels": []} for i in range(1, 101)]
        page2 = [{"number": i, "labels": []} for i in range(101, 126)]

        mock_req.side_effect = [
            (200, page1),
            (200, page2),
        ]

        prs = get_open_prs("owner/repo", "fake-token")
        self.assertEqual(len(prs), 125)
        self.assertEqual(mock_req.call_count, 2)

    @patch("sync_automerge_label._make_request")
    def test_ensure_label_on_pr_success(self, mock_req: MagicMock):
        mock_req.return_value = (201, {})
        ensure_label_on_pr("owner/repo", 123, "fake-token", "automerge:enabled")
        self.assertEqual(mock_req.call_count, 1)
        add_call = mock_req.call_args
        self.assertIn("issues/123/labels", add_call[0][0])
        self.assertEqual(add_call[1]["data"], {"labels": ["automerge:enabled"]})

    @patch("sync_automerge_label._make_request")
    def test_ensure_label_on_pr_failure_raises(self, mock_req: MagicMock):
        mock_req.return_value = (403, {"message": "Resource not accessible"})
        with self.assertRaises(RuntimeError) as ctx:
            ensure_label_on_pr("owner/repo", 123, "fake-token", "automerge:enabled")
        self.assertIn("Failed to add label", str(ctx.exception))

    @patch("sync_automerge_label._make_request")
    def test_remove_label_from_pr_success(self, mock_req: MagicMock):
        mock_req.return_value = (200, {})
        remove_label_from_pr("owner/repo", 123, "fake-token", "automerge:enabled")
        self.assertEqual(mock_req.call_count, 1)

    @patch("sync_automerge_label._make_request")
    def test_remove_label_from_pr_404_ignored(self, mock_req: MagicMock):
        mock_req.return_value = (404, {"message": "Label does not exist"})
        # Should NOT raise
        remove_label_from_pr("owner/repo", 123, "fake-token", "automerge:enabled")
        self.assertEqual(mock_req.call_count, 1)

    @patch("sync_automerge_label._make_request")
    def test_remove_label_from_pr_other_error_raises(self, mock_req: MagicMock):
        mock_req.return_value = (500, {"message": "Internal Server Error"})
        with self.assertRaises(RuntimeError) as ctx:
            remove_label_from_pr("owner/repo", 123, "fake-token", "automerge:enabled")
        self.assertIn("Failed to remove label", str(ctx.exception))

    @patch("sync_automerge_label.get_open_prs")
    @patch("sync_automerge_label.ensure_label_on_pr")
    def test_main_enabled_adds_label(self, mock_ensure: MagicMock, mock_get: MagicMock):
        mock_get.return_value = [
            {"number": 1, "labels": [{"name": "other"}]},
            {"number": 2, "labels": [{"name": "automerge:enabled"}]},
        ]
        # Mock sys.argv and env
        with patch.dict(
            os.environ,
            {
                "GITHUB_REPOSITORY": "owner/repo",
                "GITHUB_TOKEN": "token",
                "AUTONOMOUS_MERGE_ENABLED": "true",
            },
        ):
            result = main()
        self.assertEqual(result, 0)
        # Should add label to PR 1 (doesn't have it), not PR 2 (already has it)
        self.assertEqual(mock_ensure.call_count, 1)
        mock_ensure.assert_called_with("owner/repo", 1, "token", "automerge:enabled")

    @patch("sync_automerge_label.get_open_prs")
    @patch("sync_automerge_label.remove_label_from_pr")
    def test_main_disabled_removes_label(
        self, mock_remove: MagicMock, mock_get: MagicMock
    ):
        mock_get.return_value = [
            {"number": 1, "labels": [{"name": "automerge:enabled"}]},
            {"number": 2, "labels": [{"name": "other"}]},
        ]
        with patch.dict(
            os.environ,
            {
                "GITHUB_REPOSITORY": "owner/repo",
                "GITHUB_TOKEN": "token",
                "AUTONOMOUS_MERGE_ENABLED": "false",
            },
        ):
            result = main()
        self.assertEqual(result, 0)
        # Should remove label from PR 1 (has it), not PR 2 (doesn't have it)
        self.assertEqual(mock_remove.call_count, 1)
        mock_remove.assert_called_with("owner/repo", 1, "token", "automerge:enabled")

    @patch("sync_automerge_label.get_open_prs")
    @patch("sync_automerge_label.remove_label_from_pr")
    def test_main_missing_variable_defaults_disabled(
        self, mock_remove: MagicMock, mock_get: MagicMock
    ):
        mock_get.return_value = [
            {"number": 1, "labels": [{"name": "automerge:enabled"}]}
        ]
        mock_remove.return_value = None
        with patch.dict(
            os.environ,
            {"GITHUB_REPOSITORY": "owner/repo", "GITHUB_TOKEN": "token"},
            clear=True,
        ):
            result = main()
        self.assertEqual(result, 0)
        mock_remove.assert_called_once_with(
            "owner/repo", 1, "token", "automerge:enabled"
        )

    @patch("sync_automerge_label.get_open_prs")
    @patch("sync_automerge_label.remove_label_from_pr")
    def test_disable_continues_after_first_removal_failure(
        self, mock_remove: MagicMock, mock_get: MagicMock
    ):
        mock_get.return_value = [
            {"number": 1, "labels": [{"name": "automerge:enabled"}]},
            {"number": 2, "labels": [{"name": "automerge:enabled"}]},
        ]
        mock_remove.side_effect = [RuntimeError("boom"), None]
        with patch.dict(
            os.environ,
            {
                "GITHUB_REPOSITORY": "owner/repo",
                "GITHUB_TOKEN": "token",
                "AUTONOMOUS_MERGE_ENABLED": "false",
            },
            clear=True,
        ):
            result = main()
        self.assertEqual(result, 1)
        self.assertEqual(mock_remove.call_count, 2)

    @patch("sync_automerge_label.get_open_prs")
    @patch("sync_automerge_label.remove_label_from_pr")
    def test_disable_aggregates_multiple_failures(
        self, mock_remove: MagicMock, mock_get: MagicMock
    ):
        mock_get.return_value = [
            {"number": 1, "labels": [{"name": "automerge:enabled"}]},
            {"number": 2, "labels": [{"name": "automerge:enabled"}]},
            {"number": 3, "labels": [{"name": "automerge:enabled"}]},
        ]
        mock_remove.side_effect = [RuntimeError("one"), None, RuntimeError("three")]
        with patch.dict(
            os.environ,
            {
                "GITHUB_REPOSITORY": "owner/repo",
                "GITHUB_TOKEN": "token",
                "AUTONOMOUS_MERGE_ENABLED": "false",
            },
            clear=True,
        ):
            result = main()
        self.assertEqual(result, 1)
        self.assertEqual(mock_remove.call_count, 3)

    @patch("sync_automerge_label.get_open_prs", side_effect=RuntimeError("fetch failed"))
    def test_fetch_failure_is_nonzero(self, mock_get: MagicMock):
        with patch.dict(
            os.environ,
            {
                "GITHUB_REPOSITORY": "owner/repo",
                "GITHUB_TOKEN": "token",
                "AUTONOMOUS_MERGE_ENABLED": "false",
            },
            clear=True,
        ):
            self.assertEqual(main(), 1)
        mock_get.assert_called_once()


if __name__ == "__main__":
    unittest.main()
