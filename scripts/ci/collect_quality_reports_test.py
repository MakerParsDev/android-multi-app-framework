from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from collect_quality_reports import collect_reports


class CollectQualityReportsTest(unittest.TestCase):
    def test_collects_content_atomically_without_copying_source_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app_report = root / "app/build/reports/ktlint/check/report.html"
            test_result = root / "core/common/build/test-results/testDebugUnitTest/TEST-sample.xml"
            app_report.parent.mkdir(parents=True)
            test_result.parent.mkdir(parents=True)
            app_report.write_text("app-report", encoding="utf-8")
            test_result.write_text("test-result", encoding="utf-8")

            destination = root / "build/quality-reports"
            destination.mkdir(parents=True)
            (destination / "stale.txt").write_text("stale", encoding="utf-8")

            with mock.patch.object(
                shutil,
                "copystat",
                side_effect=PermissionError("source directory metadata is not portable"),
            ):
                manifest = collect_reports(root, destination)

            self.assertEqual("app-report", (destination / "app/reports/ktlint/check/report.html").read_text())
            self.assertEqual(
                "test-result",
                (
                    destination
                    / "core/common/test-results/testDebugUnitTest/TEST-sample.xml"
                ).read_text(),
            )
            self.assertFalse((destination / "stale.txt").exists())
            self.assertEqual(2, manifest["copiedFileCount"])
            persisted = json.loads((destination / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["copiedRoots"], persisted["copiedRoots"])


if __name__ == "__main__":
    unittest.main()
