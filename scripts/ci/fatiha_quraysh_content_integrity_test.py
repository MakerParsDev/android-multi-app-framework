#!/usr/bin/env python3
"""Guard core Qur'an verse integrity in the bundled prayer/sura reader.

Primary references:
- Diyanet Fâtiha: https://kuran.diyanet.gov.tr/mushaf/kuran-meal-1/fatiha-suresi-1/ayet-1/kuran-yolu-meali-5
- Diyanet Kureyş: https://kuran.diyanet.gov.tr/kuran?ayet=1&mode=kuran-meal&sure=106
- Diyanet Tebbet recitation: https://webdosyasp.diyanet.gov.tr/muftuluk/UserFiles/mersin/Ilceler/gulnar/UserFiles/Files/NAMAZ%20SURELERI%CC%87_06dad0a9-8e34-4c89-9087-65a4062b8e1e.pdf
"""

import hashlib
import json
import unicodedata
import unittest
from pathlib import Path


DATA = (
    Path(__file__).resolve().parents[2]
    / "app/src/namazsurelerivedualarsesli/assets/data.json"
)

FATIHA_VERSE_TWO = "اَلْحَمْدُ لِلّٰهِ رَبِّ الْعَالَمٖينَۙ"
QURAYSH_VERSE_FOUR = "الَّذِي أَطْعَمَهُمْ مِنْ جُوعٍ وَآمَنَهُمْ مِنْ خَوْفٍ"

QURAYSH_VERSE_TWO = "إِيلَافِهِمْ رِحْلَةَ الشِّتَاءِ وَالصَّيْفِ"
QURAYSH_VERSE_TWO_LATIN = "İlâfihim rehletesshitaai vessayf"

# Offline golden fingerprints for every bundled Quran-derived verse (55 sura ayahs
# and Ayat al-Kursi 2:255). Reference: Quran Foundation "imlaei_simple" script,
# https://api.quran.com/api/v4/quran/verses/imlaei_simple (fetched 2026-10-25).
# Order matters: a missing, shifted or substituted verse changes the digest.
# Hashes avoid vendoring a second full Quran corpus or making CI network calls.
# Only diacritics, ornamental signs, whitespace and common alif/ya orthographic
# variants are ignored; actual letter differences remain detectable.
QURAN_REFERENCE = {
    0: (2, 1, "16d89dc65032b1d0f6279afd5f301350c3da98325906308d11d758d3e7a73848"),
    1: (1, 7, "3f819963cce9bdc12d3dbf7b034836844aeb139d9ca369817c073f82b78a359b"),
    2: (105, 5, "7311f88e1eb0bc40e775d6069f8c2f934e4190ce1a9329b1b2dc1150ceae080f"),
    3: (113, 5, "bd7d641a99405ea7b0b594fc54f0e94441e15c1f67bc84bba9ef4ce17681365a"),
    4: (114, 6, "fb4fbd6aeb1b4a910bb48fb605d9ca16de32bb954e8e58e17d288d97b210967d"),
    5: (106, 4, "8c65e23fe75a3a7d7cdb6f1b38a6f6d1f21734d94b3cb54a59e55df06a9df620"),
    6: (109, 6, "beb1fc592089c8bd7999d1dbbf263a280b5c10fc9e4e0b3dd1e13a66225357d4"),
    7: (108, 3, "0048aff16f195c37b5788327669e33b015400456fbdfa7a787e35660d44692b5"),
    8: (107, 7, "87eafdf4ca420eb664fc51e64620c199cd258d810f4381f43d736bc162632ec5"),
    9: (110, 3, "af581fa293bf518ec4f93f97ed9eabdef6dac7d59c556c70c0fc03b87e7196d4"),
    10: (112, 4, "d500739442b5173c97f46a1f4140dd7afa5dec508a64b0d0839333eea00a95b9"),
    11: (111, 5, "66dd7bee1fcee52b2365a32729e0f874f63e3b684f3b7d94e8a577ac50a87465"),
}


def normalized_arabic(text):
    """Canonical lexical check, tolerant of printed-mushaf vocalization style."""
    text = unicodedata.normalize("NFD", text.strip())
    text = "".join(
        char
        for char in text
        if unicodedata.category(char) not in ("Mn", "Me", "Cf") and char != "ـ"
    )
    text = text.translate(str.maketrans("ٱآأإىؤئٲٳٵ", "اااايويااا"))
    return "".join(text.split())



class QuranReaderContentIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        records = json.loads(DATA.read_text(encoding="utf-8"))
        cls.suras = {row["sureID"]: row for row in records}

    def test_fatiha_verse_two_is_praise_not_duplicated_mercy(self):
        verses = self.suras[1]["ayetler"]
        self.assertEqual(7, self.suras[1]["ayetSayisi"])
        self.assertEqual(list(range(1, 8)), [v["ayetID"] for v in verses])
        self.assertEqual(FATIHA_VERSE_TWO, verses[1]["ayetAR"].strip())
        self.assertNotEqual(verses[1]["ayetAR"].strip(), verses[2]["ayetAR"].strip())

    def test_all_bundled_quran_verses_match_pinned_canonical_lexical_reference(self):
        self.assertTrue(set(QURAN_REFERENCE).issubset(self.suras))
        for app_id, (surah, count, digest) in QURAN_REFERENCE.items():
            with self.subTest(app_id=app_id, surah=surah):
                record = self.suras[app_id]
                verses = record["ayetler"]
                self.assertEqual(count, record["ayetSayisi"])
                self.assertEqual(count, len(verses))
                self.assertEqual(
                    list(range(1, count + 1)),
                    [v["ayetID"] for v in verses],
                )
                self.assertTrue(all(
                    all(str(v.get(field, "")).strip() for field in ("ayetAR", "ayetLAT", "ayetTR", "ayetEN"))
                    for v in verses
                ))
                actual = hashlib.sha256(
                    "|".join(normalized_arabic(v["ayetAR"]) for v in verses).encode("utf-8")
                ).hexdigest()
                self.assertEqual(digest, actual, f"Canonical lexical mismatch in surah {surah}")

    def test_fatiha_all_seven_readings_and_meanings_stay_aligned(self):
        verses = self.suras[1]["ayetler"]
        self.assertEqual(7, len(verses))
        expected_terms = [
            ("Bismillah", "Rahmân", "name"),
            ("Elhamdü", "Hamd", "Praise"),
            ("Rahmân", "Rahmân", "Merciful"),
            ("Mâliki", "Günün", "Day"),
            ("İyyâke", "yalnız", "You"),
            ("İhdina", "yola", "path"),
            ("Siratallezîne", "yoluna", "path"),
        ]
        for verse, words in zip(verses, expected_terms):
            with self.subTest(ayah=verse["ayetID"]):
                self.assertIn(words[0].casefold(), verse["ayetLAT"].casefold())
                self.assertIn(words[1].casefold(), verse["ayetTR"].casefold())
                self.assertIn(words[2].casefold(), verse["ayetEN"].casefold())

    def test_tebbet_verse_three_has_verified_latin_recitation_not_turkish_prose(self):
        record = self.suras[11]
        verses = record["ayetler"]
        self.assertEqual(5, record["ayetSayisi"])
        self.assertEqual([1, 2, 3, 4, 5], [v["ayetID"] for v in verses])
        self.assertEqual("سَيَصْلَىٰ نَارًا ذَاتَ لَهَبٍ", verses[2]["ayetAR"].strip())
        self.assertEqual("Seyaslâ nâran zâte leheb.", verses[2]["ayetLAT"].strip())
        self.assertNotEqual(verses[2]["ayetLAT"].strip(), verses[2]["ayetTR"].strip())

    def test_quraysh_contains_all_four_verses_in_order(self):
        record = self.suras[5]
        verses = record["ayetler"]
        self.assertEqual(4, record["ayetSayisi"])
        self.assertEqual(4, len(verses))
        self.assertEqual([1, 2, 3, 4], [v["ayetID"] for v in verses])
        self.assertEqual(QURAYSH_VERSE_TWO, verses[1]["ayetAR"].strip())
        self.assertEqual(QURAYSH_VERSE_TWO_LATIN, verses[1]["ayetLAT"].strip())
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
