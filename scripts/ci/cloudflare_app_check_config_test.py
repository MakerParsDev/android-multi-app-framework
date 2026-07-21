from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from validate_cloudflare_app_check_config import validate


class CloudflareAppCheckConfigTest(unittest.TestCase):
    def write_fixture(
        self,
        root: Path,
        *,
        allowed_ids: str,
        project_number: str = "123",
        register_device_require_app_check: str = "true",
    ) -> tuple[Path, Path]:
        catalog = root / "firebase-apps.json"
        wrangler = root / "wrangler.toml"
        catalog.write_text(
            json.dumps(
                {
                    "one": {"projectId": "sample-project", "appId": "1:123:android:one"},
                    "two": {"projectId": "sample-project", "appId": "1:123:android:two"},
                }
            ),
            encoding="utf-8",
        )
        wrangler.write_text(
            "\n".join(
                [
                    "[vars]",
                    'FIREBASE_PROJECT_ID = "sample-project"',
                    f'FIREBASE_PROJECT_NUMBER = "{project_number}"',
                    f'FIREBASE_APP_CHECK_ALLOWED_APP_IDS = "{allowed_ids}"',
                    'VERIFY_PURCHASE_REQUIRE_APP_CHECK = "true"',
                    f'REGISTER_DEVICE_REQUIRE_APP_CHECK = "{register_device_require_app_check}"',
                ]
            ),
            encoding="utf-8",
        )
        return catalog, wrangler

    def test_accepts_exact_catalog_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            catalog, wrangler = self.write_fixture(
                Path(temporary),
                allowed_ids="1:123:android:one,1:123:android:two",
            )

            result = validate(catalog, wrangler)

            self.assertEqual("sample-project", result.project_id)
            self.assertEqual("123", result.project_number)
            self.assertEqual(2, result.app_count)

    def test_rejects_missing_and_unexpected_app_ids(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            catalog, wrangler = self.write_fixture(
                Path(temporary),
                allowed_ids="1:123:android:one,1:123:android:unexpected",
            )

            with self.assertRaisesRegex(ValueError, "missing 1 Firebase app ID"):
                validate(catalog, wrangler)

    def test_rejects_project_number_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            catalog, wrangler = self.write_fixture(
                Path(temporary),
                allowed_ids="1:123:android:one,1:123:android:two",
                project_number="999",
            )

            with self.assertRaisesRegex(ValueError, "FIREBASE_PROJECT_NUMBER mismatch"):
                validate(catalog, wrangler)

    def test_rejects_disabled_device_registration_app_check(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            catalog, wrangler = self.write_fixture(
                Path(temporary),
                allowed_ids="1:123:android:one,1:123:android:two",
                register_device_require_app_check="false",
            )

            with self.assertRaisesRegex(ValueError, "REGISTER_DEVICE_REQUIRE_APP_CHECK must be true"):
                validate(catalog, wrangler)


if __name__ == "__main__":
    unittest.main()
