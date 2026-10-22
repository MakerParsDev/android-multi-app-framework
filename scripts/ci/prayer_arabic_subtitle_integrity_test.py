#!/usr/bin/env python3
"""Guard Qur'an/prayer row titles against misleading Arabic subtitles.

Sura-name reference: Diyanet's Arabic Qur'an pages for suras 106 and 111.
For six ordinary prayer types and two Kunut prayers, prefer no
Arabic subtitle over a fabricated or incorrect title.
"""

import json
import unittest
from pathlib import Path


DATA = (
    Path(__file__).resolve().parents[2]
    / "app/src/namazsurelerivedualarsesli/assets/data.json"
)


class PrayerArabicSubtitleIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        data = json.loads(DATA.read_text(encoding="utf-8"))
        cls.rows = {entry["sureID"]: entry for entry in data}

    def test_canonical_sura_titles(self):
        self.assertEqual("سورة قريش", self.rows[5]["sureAdiAR"])
        self.assertEqual("سورة المسد", self.rows[11]["sureAdiAR"])

    def test_prayer_rows_have_no_incorrect_fatiha_label(self):
        self.assertEqual("سورة الفاتحة", self.rows[1]["sureAdiAR"])
        for record_id in range(12, 20):
            with self.subTest(prayer=record_id):
                self.assertEqual("", self.rows[record_id]["sureAdiAR"])

    def test_sura_titles_are_not_copied_from_other_suras(self):
        sura_titles = [self.rows[idx]["sureAdiAR"] for idx in range(1, 12)]
        self.assertEqual(len(sura_titles), len(set(sura_titles)))


if __name__ == "__main__":
    unittest.main()
