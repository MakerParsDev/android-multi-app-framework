#!/usr/bin/env python3
"""
Unit tests for scripts/ci/fleet_milestone.py
"""

from __future__ import annotations

import os
import sys
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, os.path.dirname(__file__))
from fleet_milestone import (
    DEFAULT_MILESTONE_TITLE,
    ensure_labels,
    ensure_milestone,
    resolve_open_milestone,
)


class TestFleetMilestone(unittest.TestCase):
    @patch("fleet_milestone._make_request")
    def test_resolve_open_milestone_found(self, mock_req: MagicMock):
        mock_req.return_value = (
            200,
            [
                {"number": 1, "title": "Other Milestone"},
                {"number": 42, "title": DEFAULT_MILESTONE_TITLE},
            ],
        )
        num = resolve_open_milestone(
            "owner/repo", "fake-token", DEFAULT_MILESTONE_TITLE
        )
        self.assertEqual(num, 42)

    @patch("fleet_milestone._make_request")
    def test_resolve_open_milestone_not_found(self, mock_req: MagicMock):
        mock_req.return_value = (
            200,
            [
                {"number": 1, "title": "Other Milestone"},
            ],
        )
        num = resolve_open_milestone(
            "owner/repo", "fake-token", DEFAULT_MILESTONE_TITLE
        )
        self.assertIsNone(num)

    @patch("fleet_milestone.resolve_open_milestone")
    @patch("fleet_milestone._make_request")
    def test_ensure_milestone_creates_when_missing(
        self, mock_req: MagicMock, mock_resolve: MagicMock
    ):
        mock_resolve.return_value = None
        mock_req.return_value = (201, {"number": 99, "title": DEFAULT_MILESTONE_TITLE})

        num = ensure_milestone("owner/repo", "fake-token", DEFAULT_MILESTONE_TITLE)
        self.assertEqual(num, 99)
        mock_req.assert_called_once()

    @patch("fleet_milestone.resolve_open_milestone")
    @patch("fleet_milestone._make_request")
    def test_ensure_milestone_returns_existing(
        self, mock_req: MagicMock, mock_resolve: MagicMock
    ):
        mock_resolve.return_value = 55
        num = ensure_milestone("owner/repo", "fake-token", DEFAULT_MILESTONE_TITLE)
        self.assertEqual(num, 55)
        mock_req.assert_not_called()

    @patch("fleet_milestone._make_request")
    def test_ensure_labels_creates_missing(self, mock_req: MagicMock):
        # For each of 8 labels: GET -> 404, POST -> 201
        side_effect = []
        for _ in range(8):
            side_effect.append((404, None))
            side_effect.append((201, {}))
        mock_req.side_effect = side_effect
        ensure_labels("owner/repo", "fake-token")
        self.assertEqual(mock_req.call_count, 16)


if __name__ == "__main__":
    unittest.main()
