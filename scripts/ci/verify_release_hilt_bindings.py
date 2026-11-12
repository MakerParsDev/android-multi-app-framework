#!/usr/bin/env python3
"""Fail signed Quran AAB publication if R8 lost required Hilt ViewModel keys.

KSP 2.3.13 can omit Hilt-generated multi-module bindings without failing
compilation. The actual v69 AAB had no bindings for three Quran ViewModels,
while Play v68 had both lazy-key bindings for each. This checks the *built AAB*.
"""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

MAPPING_PATH = "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"
QURAN_VIEWMODELS: dict[str, str] = {
    "QuranSuraListViewModel": "com.parsfilo.contentapp.feature.quran.ui.surelist",
    "QuranSuraDetailViewModel": "com.parsfilo.contentapp.feature.quran.ui.suradetail",
    "QuranBookmarksViewModel": "com.parsfilo.contentapp.feature.quran.ui.bookmarks",
}
LAZY_KEYS = (
    "KeyModule_Provide_LazyMapKey",
    "BindsModule_Binds_LazyMapKey",
)


def verify_aab(flavor: str, aab: Path) -> bool:
    if not aab.is_file():
        print(f"::error::Signed AAB is missing: {aab}")
        return False
    # Other flavors have no approved Quran-key baseline. Do not add a new
    # publication gate to unrelated apps until their own baseline is validated.
    if flavor != "kuran_kerim":
        print(f"SKIP: no Quran-specific Hilt binding gate for {flavor}")
        return True
    try:
        with zipfile.ZipFile(aab) as archive:
            if MAPPING_PATH not in archive.namelist():
                print(f"::error::Signed AAB is missing {MAPPING_PATH}")
                return False

            required = {
                (f"{namespace}.{view_model}_HiltModules_{lazy_key}").encode("utf-8"):
                f"{view_model}/{lazy_key}"
                for view_model, namespace in QURAN_VIEWMODELS.items()
                for lazy_key in LAZY_KEYS
            }
            missing = dict(required)
            with archive.open(MAPPING_PATH) as mapping:
                for line in mapping:
                    if b"_HiltModules_" not in line:
                        continue
                    for key in tuple(missing):
                        if key in line:
                            del missing[key]
                    if not missing:
                        break
            if missing:
                for entry in missing.values():
                    print(f"::error::Missing release Hilt binding: {entry}")
                return False
            print("PASS: Quran signed AAB contains all 6 required Hilt lazy map keys")
            return True
    except (OSError, zipfile.BadZipFile) as error:
        print(f"::error::Cannot inspect signed AAB Hilt metadata: {type(error).__name__}")
        return False


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--flavor", required=True)
    parser.add_argument("--aab", required=True, type=Path)
    args = parser.parse_args(argv)
    return 0 if verify_aab(args.flavor, args.aab) else 2


if __name__ == "__main__":
    sys.exit(main())
