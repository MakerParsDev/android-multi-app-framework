#!/usr/bin/env python3
"""Pin last two Arabic wording witnesses without implying religious or medical authenticity."""
import hashlib
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/mucizedualar/assets"
EXPECTED_HASHES = {4: 'a1051909cf94948771942ff11fd5dd71badfadc0866762c3c8ace9186d38d62f', 17: 'a4f60380c198d345302584b6c385a224269e5131960b63ef7277b042b2b7b9b4'}

class RemainingArabicSourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.legacy = json.loads((ASSETS / "data.json").read_text())
        cls.weak = json.loads((ASSETS / "attested_very_weak_hadith_supplications.json").read_text())
        cls.traditional = json.loads((ASSETS / "documented_traditional_supplications.json").read_text())

    def test_exact_witnesses_preserve_grade_and_source(self):
        self.assertEqual(20, len(self.legacy))
        self.assertEqual(1, len(self.weak["prayers"]))
        self.assertEqual(1, len(self.traditional["prayers"]))
        for idx, source in ((4, self.weak), (17, self.traditional)):
            item = source["prayers"][0]
            self.assertEqual(self.legacy[idx]["duaIsim"], item["title"])
            self.assertEqual(EXPECTED_HASHES[idx], hashlib.sha256(item["arabic"].encode()).hexdigest())
            self.assertTrue(any("\u0600" <= c <= "\u06ff" for c in item["arabic"]))
            self.assertNotIn("(Amin)", item["arabic"])
            self.assertTrue(self.legacy[idx]["duaArapca"].endswith("(Amin)"))
        self.assertIn("very-weak", self.weak["source"]["name"])
        self.assertIn("Daif Jiddan", self.weak["prayers"][0]["grade"])
        self.assertEqual("https://dorar.net/h/cmOtogIl", self.weak["prayers"][0]["sourceUrl"])
        self.assertIn("al-Albani 1328", self.weak["prayers"][0]["reference"])
        self.assertEqual("documented-traditional-practice", self.traditional["prayers"][0]["classification"])
        self.assertIn("Marmara University", self.traditional["source"]["notice"])
        self.assertIn("#page=78", self.traditional["prayers"][0]["sourceUrl"])

    def test_exactly_twenty_nonoverlapping_wording_sources(self):
        names = (
            "verified_quran_verses.json",
            "verified_hadith_supplications.json",
            "verified_scholarly_supplications.json",
            "attested_weak_hadith_supplications.json",
            "attested_very_weak_hadith_supplications.json",
            "documented_traditional_supplications.json",
        )
        sources = [json.loads((ASSETS / n).read_text())["prayers"] for n in names]
        self.assertEqual([11, 4, 1, 2, 1, 1], [len(g) for g in sources])
        titles = [p["title"] for group in sources for p in group]
        self.assertEqual(20, len(titles))
        self.assertEqual(len(titles), len(set(titles)))
        self.assertEqual({p["duaIsim"] for p in self.legacy}, set(titles))

if __name__ == "__main__":
    unittest.main()
