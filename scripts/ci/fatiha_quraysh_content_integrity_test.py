#!/usr/bin/env python3
"""Guard core Qur'an verse integrity in the bundled prayer/sura reader.

Primary references:
- Diyanet Fâtiha: https://kuran.diyanet.gov.tr/mushaf/kuran-meal-1/fatiha-suresi-1/ayet-1/kuran-yolu-meali-5
- Diyanet Kureyş: https://kuran.diyanet.gov.tr/kuran?ayet=1&mode=kuran-meal&sure=106
"""

import json
import unittest
from pathlib import Path


DATA = (
    Path(__file__).resolve().parents[2]
    / "app/src/namazsurelerivedualarsesli/assets/data.json"
)

FATIHA_VERSE_TWO = "اَلْحَمْدُ لِلّٰهِ رَبِّ الْعَالَمٖينَۙ"
QURAYSH_VERSE_FOUR = "الَّذِي أَطْعَمَهُمْ مِنْ جُوعٍ وَآمَنَهُمْ مِنْ خَوْفٍ"


class QuranReaderContentIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        records = json.loads(DATA.read_text(encoding="utf-8"))
        cls.suras = {row["sureID"]: row for row in records if row["sureID"] in (1, 5)}

    def test_fatiha_verse_two_is_praise_not_duplicated_mercy(self):
        verses = self.suras[1]["ayetler"]
        self.assertEqual(7, self.suras[1]["ayetSayisi"])
        self.assertEqual(list(range(1, 8)), [v["ayetID"] for v in verses])
        self.assertEqual(FATIHA_VERSE_TWO, verses[1]["ayetAR"].strip())
        self.assertNotEqual(verses[1]["ayetAR"].strip(), verses[2]["ayetAR"].strip())

    def test_quraysh_contains_all_four_verses_in_order(self):
        record = self.suras[5]
        verses = record["ayetler"]
        self.assertEqual(4, record["ayetSayisi"])
        self.assertEqual(4, len(verses))
        self.assertEqual([1, 2, 3, 4], [v["ayetID"] for v in verses])
        self.assertEqual(QURAYSH_VERSE_FOUR, verses[3]["ayetAR"].strip())
        expected_translations = {
            "ayetLAT": "Ellezi at'amehum min cuın ve amenehum min havf.",
            "ayetTR": "Kendilerini besleyip açlıklarını gideren ve her çeşit korkudan emin kılan.",
            "ayetEN": "Who has fed them against hunger and secured them against fear.",
        }
        for field, expected in expected_translations.items():
            with self.subTest(verse=4, field=field):
                self.assertEqual(expected, verses[3][field].strip())
        for verse in verses:
            for field in ("ayetAR", "ayetLAT", "ayetTR", "ayetEN"):
                with self.subTest(verse=verse["ayetID"], field=field):
                    self.assertTrue(verse.get(field, "").strip())


if __name__ == "__main__":
    unittest.main()
