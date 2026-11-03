from __future__ import annotations

import pathlib
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from scripts.ci.verify_play_console_access import read_flavors, try_execute

ROOT = pathlib.Path(__file__).resolve().parents[2]


class VerifyPlayConsoleAccessFlavorParserTest(unittest.TestCase):
    def test_parses_current_named_argument_catalog(self) -> None:
        flavors = read_flavors(
            ROOT / "buildSrc/src/main/kotlin/FlavorConfig.kt"
        )

        self.assertEqual(17, len(flavors))
        self.assertEqual(
            "com.parsfilo.namazvakitleri",
            flavors["namazvakitleri"].package_name,
        )
        self.assertEqual(
            "com.parsfilo.kuran_kerim",
            flavors["kuran_kerim"].package_name,
        )

    def test_keeps_legacy_positional_catalog_support(self) -> None:
        source = '''
        object AppFlavors {
            val all = listOf(
                FlavorConfig("legacy", "Legacy", "com.example.legacy"),
            )
        }
        '''
        with tempfile.TemporaryDirectory() as directory:
            flavor_file = pathlib.Path(directory) / "FlavorConfig.kt"
            flavor_file.write_text(source, encoding="utf-8")
            flavors = read_flavors(flavor_file)

        self.assertEqual("com.example.legacy", flavors["legacy"].package_name)


class VerifyPlayTransientRetriesTest(unittest.TestCase):
    class ApiError(Exception):
        def __init__(self, status: int) -> None:
            self.resp = SimpleNamespace(status=status)
            super().__init__(f"HTTP {status}")

    @patch("scripts.ci.verify_play_console_access.time.sleep")
    def test_503_then_success_retries_once(self, sleep) -> None:
        calls = 0

        def read() -> None:
            nonlocal calls
            calls += 1
            if calls == 1:
                raise self.ApiError(503)

        self.assertEqual((True, ""), try_execute("tracks", read))
        self.assertEqual(2, calls)
        sleep.assert_called_once_with(1.0)

    @patch("scripts.ci.verify_play_console_access.time.sleep")
    def test_temporary_statuses_exhaust_three_attempts(self, sleep) -> None:
        for status in (429, 500, 502, 503, 504):
            with self.subTest(status=status):
                calls = 0

                def read(status: int = status) -> None:
                    nonlocal calls
                    calls += 1
                    raise self.ApiError(status)

                ok, error = try_execute("tracks", read)
                self.assertFalse(ok)
                self.assertEqual(3, calls)
                self.assertIn(f"HTTP {status}", error)
                self.assertIn("temporarily unavailable", error)
        self.assertEqual(10, sleep.call_count)


    @patch("scripts.ci.verify_play_console_access.time.sleep")
    def test_permission_denied_is_not_retried(self, sleep) -> None:
        for status in (401, 403):
            with self.subTest(status=status):
                calls = 0

                def read(status: int = status) -> None:
                    nonlocal calls
                    calls += 1
                    raise self.ApiError(status)

                ok, error = try_execute("tracks", read)
                self.assertFalse(ok)
                self.assertEqual(1, calls)
                self.assertIn(f"HTTP {status}", error)
        sleep.assert_not_called()

    @patch("scripts.ci.verify_play_console_access.time.sleep")
    def test_edit_creation_never_retried(self, sleep) -> None:
        calls = 0

        def create() -> None:
            nonlocal calls
            calls += 1
            raise self.ApiError(503)

        ok, error = try_execute("edit", create, retry_transient=False)
        self.assertFalse(ok)
        self.assertEqual(1, calls)
        self.assertIn("temporarily unavailable", error)
        sleep.assert_not_called()

    @patch("scripts.ci.verify_play_console_access.time.sleep")
    def test_non_http_failure_never_retried(self, sleep) -> None:
        def invalid() -> None:
            raise ValueError("bad response")

        self.assertEqual(
            (False, "tracks: bad response"),
            try_execute("tracks", invalid),
        )
        sleep.assert_not_called()


if __name__ == "__main__":
    unittest.main()
