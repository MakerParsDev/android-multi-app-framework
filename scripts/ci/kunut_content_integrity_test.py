#!/usr/bin/env python3
"""Guard the published Arabic Kunut clauses against transcription regressions.

Editorial reference: Diyanet İşleri Başkanlığı, Kunut Duaları,
https://www.diyanethaber.com.tr/kunut-duasini-bilmeyen-bir-kimse-ne-yapar
"""

import json
import unittest
from pathlib import Path


DATA = (
    Path(__file__).resolve().parents[2]
    / "app/src/namazsurelerivedualarsesli/assets/data.json"
)


class KunutContentIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        prayers = json.loads(DATA.read_text(encoding="utf-8"))
        cls.kunut = {prayer["sureID"]: prayer for prayer in prayers if prayer["sureID"] in (18, 19)}

    def arabic(self, prayer_id, segment_id):
        prayer = self.kunut[prayer_id]
        segment = next(part for part in prayer["ayetler"] if part["ayetID"] == segment_id)
        return segment["ayetAR"].strip()

    def test_kunut_one_repents_before_reliance(self):
        self.assertEqual(
            "وَنُؤْمِنُ بِكَ وَنَتُوبُ إِلَيْكَ",
            self.arabic(18, 3),
        )
        self.assertEqual("وَنَتَوَكَّلُ عَلَيْكَ", self.arabic(18, 4))

    def test_kunut_two_strives_toward_god(self):
        self.assertEqual(
            "وَإِلَيْكَ نَسْعَى وَنَحْفِدُ",
            self.arabic(19, 3),
        )

    def test_kunut_two_punishment_clause_vocalization(self):
        self.assertEqual(
            "إِنَّ عَذَابَكَ بِالْكُفَّارِ مُلْحِقٌ",
            self.arabic(19, 5),
        )


    def test_kunut_one_readable_meanings_preserve_intended_sense(self):
        expected = {
            (1, "ayetTR"): "Allah'ım! Senden yardım isteriz.",
            (2, "ayetTR"): "Günahlarımızı bağışlamanı ve bizi doğru yola iletmeni isteriz.",
            (2, "ayetEN"): "We seek Your forgiveness and guidance.",
            (3, "ayetTR"): "Sana inanır, sana tövbe ederiz.",
            (3, "ayetEN"): "We believe in You and repent to You.",
            (5, "ayetTR"): "Seni bütün hayırlarla över ve sana şükrederiz.",
            (5, "ayetEN"): "We praise You for all goodness and thank You.",
            (6, "ayetEN"): "We are not ungrateful to You.",
            (7, "ayetTR"): "Sana karşı gelenleri terk ederiz.",
            (7, "ayetEN"): "We renounce and leave those who defy You.",
        }
        for (verse_id, key), meaning in expected.items():
            with self.subTest(verse=verse_id, key=key):
                verse = next(
                    part for part in self.kunut[18]["ayetler"] if part["ayetID"] == verse_id
                )
                self.assertEqual(meaning, verse[key].strip())

    def test_kunut_two_meanings_and_reading(self):
        expected = {
            (2, "ayetTR"): "Namazı yalnız senin için kılar, sana secde ederiz.",
            (3, "ayetTR"): "Yalnız sana yönelir, senin rızan için çalışıp çabalarız.",
            (5, "ayetLAT"): "İnne azâbeke bil kuffâri mülhık.",
            (5, "ayetTR"): "Şüphesiz senin azabın inkârcılara ulaşır.",
        }
        for (verse_id, key), meaning in expected.items():
            with self.subTest(verse=verse_id, key=key):
                verse = next(
                    part for part in self.kunut[19]["ayetler"] if part["ayetID"] == verse_id
                )
                self.assertEqual(meaning, verse[key].strip())


if __name__ == "__main__":
    unittest.main()
