from __future__ import annotations

import pathlib
import tempfile
import unittest

from scripts.ci.verify_play_console_access import read_flavors


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


if __name__ == "__main__":
    unittest.main()
