#!/usr/bin/env python3
"""Unit tests for validate_app_ads_txt.py."""

from __future__ import annotations

import unittest

from validate_app_ads_txt import REQUIRED_GOOGLE_SELLER, validate_content


VALID_CONTENT = [
    "# app-ads.txt",
    "",
    ", ".join(REQUIRED_GOOGLE_SELLER),
    "example.com, seller-123, RESELLER",
    "partner.example, seller-456, RESELLER, abcdef1234567890",
]


class ValidateAppAdsTxtTest(unittest.TestCase):
    def test_accepts_valid_file_with_comments_and_blank_lines(self) -> None:
        self.assertEqual(validate_content(VALID_CONTENT), [])

    def test_rejects_missing_google_direct_line(self) -> None:
        errors = validate_content(["example.com, seller-123, DIRECT"])

        self.assertTrue(any("missing required seller line" in error for error in errors))

    def test_rejects_ca_app_pub_identifier(self) -> None:
        errors = validate_content(
            [
                ", ".join(REQUIRED_GOOGLE_SELLER),
                "google.com, ca-app-pub-3312485084079132/1234567890, DIRECT",
            ],
        )

        self.assertTrue(any("ca-app-pub" in error for error in errors))

    def test_rejects_malformed_row_shape(self) -> None:
        errors = validate_content([", ".join(REQUIRED_GOOGLE_SELLER), "too,few"])

        self.assertTrue(any("expected 3 or 4" in error for error in errors))

    def test_rejects_invalid_relationship(self) -> None:
        errors = validate_content(
            [", ".join(REQUIRED_GOOGLE_SELLER), "example.com, seller-123, PARTNER"],
        )

        self.assertTrue(any("DIRECT or RESELLER" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
