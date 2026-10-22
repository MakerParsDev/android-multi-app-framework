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


if __name__ == "__main__":
    unittest.main()
