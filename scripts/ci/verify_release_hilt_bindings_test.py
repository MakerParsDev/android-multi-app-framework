#!/usr/bin/env python3
"""Signed AAB Hilt registration guard: release must fail before Play publish."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import tomllib
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHECKER = ROOT / "scripts" / "ci" / "verify_release_hilt_bindings.py"
MAP_PATH = "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"
QURAN_VMS = ("QuranSuraListViewModel", "QuranSuraDetailViewModel", "QuranBookmarksViewModel")
QURAN_NAMESPACES = {
    "QuranSuraListViewModel": "com.parsfilo.contentapp.feature.quran.ui.surelist",
    "QuranSuraDetailViewModel": "com.parsfilo.contentapp.feature.quran.ui.suradetail",
    "QuranBookmarksViewModel": "com.parsfilo.contentapp.feature.quran.ui.bookmarks",
}
LAZY_KEY_SUFFIXES = (
    "KeyModule_Provide_LazyMapKey",
    "BindsModule_Binds_LazyMapKey",
)


def valid_mapping() -> str:
    lines = ["# synthetic mapping fixture, not a production build"]
    for vm in QURAN_VMS:
        for suffix in LAZY_KEY_SUFFIXES:
            lines.append(
                f"    int {QURAN_NAMESPACES[vm]}.{vm}_HiltModules_{suffix}.$r8$clinit -> x"
            )
    return "\n".join(lines) + "\n"


class SignedAabHiltContractTest(unittest.TestCase):
    def test_ksp_version_avoids_known_broken_parallel_codegen(self) -> None:
        with (ROOT / "gradle" / "libs.versions.toml").open("rb") as handle:
            versions = tomllib.load(handle)["versions"]
        self.assertEqual(
            "2.3.12",
            versions["ksp"],
            "KSP 2.3.13 may silently omit Hilt bindings under concurrent processing",
        )

    def run_checker(self, mapping: str | None) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as directory:
            aab = Path(directory) / "quran.aab"
            with zipfile.ZipFile(aab, "w", zipfile.ZIP_DEFLATED) as archive:
                if mapping is not None:
                    archive.writestr(MAP_PATH, mapping)
            return subprocess.run(
                [
                    sys.executable,
                    str(CHECKER),
                    "--flavor", "kuran_kerim",
                    "--aab", str(aab),
                ],
                cwd=ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )

    def test_all_required_quran_hilt_keys_allow_release(self) -> None:
        result = self.run_checker(valid_mapping())
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("PASS", result.stdout)

    def test_missing_sura_list_hilt_binding_blocks_release(self) -> None:
        mapping = valid_mapping().replace("QuranSuraListViewModel_HiltModules_", "DROPPED_")
        result = self.run_checker(mapping)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("QuranSuraListViewModel", result.stdout + result.stderr)

    def test_missing_one_lazy_map_key_blocks_release(self) -> None:
        mapping = valid_mapping().replace(
            "QuranBookmarksViewModel_HiltModules_BindsModule_Binds_LazyMapKey",
            "DROPPED_BindsModule",
        )
        result = self.run_checker(mapping)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("QuranBookmarksViewModel", result.stdout + result.stderr)

    def test_missing_obfuscation_mapping_blocks_release(self) -> None:
        result = self.run_checker(None)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("proguard.map", result.stdout + result.stderr)

    def test_other_flavors_do_not_inherit_quran_mapping_requirements(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            aab = Path(directory) / "other.aab"
            with zipfile.ZipFile(aab, "w") as archive:
                archive.writestr("base/manifest/AndroidManifest.xml", b"fixture")
            result = subprocess.run(
                [
                    sys.executable,
                    str(CHECKER),
                    "--flavor", "yasin",
                    "--aab", str(aab),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("SKIP", result.stdout)

    def test_missing_aab_fails_before_flavor_specific_checks(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                [
                    sys.executable,
                    str(CHECKER),
                    "--flavor", "yasin",
                    "--aab", str(Path(directory) / "missing.aab"),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("missing", result.stdout.lower())

    def test_released_workflow_gates_hilt_before_play(self) -> None:
        text = (ROOT / ".github" / "workflows" / "play-internal.yml").read_text(
            encoding="utf-8"
        )
        assert "Verify release Hilt ViewModel bindings" in text
        assert "verify_release_hilt_bindings.py" in text
        assert text.index("Prepare exact AAB and checksum") < text.index(
            "Verify release Hilt ViewModel bindings"
        ) < text.index("Publish exact attested AAB to Play internal")


if __name__ == "__main__":
    unittest.main()