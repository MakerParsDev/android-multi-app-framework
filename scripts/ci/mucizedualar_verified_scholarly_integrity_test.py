#!/usr/bin/env python3
"""Only the exact commentary-attested #11 short prayer; not a hadith authenticity claim."""
import hashlib
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSET = ROOT / "app/src/mucizedualar/assets/verified_scholarly_supplications.json"
LEGACY = ROOT / "app/src/mucizedualar/assets/data.json"

class ScholarlySourceIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads(ASSET.read_text(encoding="utf-8"))
        cls.legacy = json.loads(LEGACY.read_text(encoding="utf-8"))

    def test_exact_public_domain_commentary_quote_and_source(self):
        self.assertEqual(20, len(self.legacy))
        self.assertEqual(1, len(self.data["prayers"]))
        prayer = self.data["prayers"][0]
        self.assertEqual(self.legacy[10]["duaIsim"], prayer["title"])
        self.assertEqual("Başarı duası", prayer["title"])
        self.assertEqual("https://www.islamweb.net/ar/library/content/79/2643/", prayer["sourceUrl"])
        self.assertEqual("اللهم خر لي واختر لي ولا تكلني إلى اختياري", prayer["arabic"])
        self.assertEqual(
            "5a647e7655c923c91fba29d85d83d5a176eba9e5cba14eebfbbfcb4f37e42534",
            hashlib.sha256(prayer["arabic"].encode()).hexdigest(),
        )
        self.assertTrue(self.legacy[10]["duaArapca"].endswith("(Amin)"))
        self.assertNotIn("(Amin)", prayer["arabic"])

    def test_scholarly_is_neither_hadith_nor_quran(self):
        quran = json.loads((ROOT / "app/src/mucizedualar/assets/verified_quran_verses.json").read_text())
        hadith = json.loads((ROOT / "app/src/mucizedualar/assets/verified_hadith_supplications.json").read_text())
        title = self.data["prayers"][0]["title"]
        self.assertNotIn(title, {p["title"] for p in quran["prayers"]})
        self.assertNotIn(title, {p["title"] for p in hadith["prayers"]})
        self.assertIn("not attributed as an authenticated Prophetic hadith", self.data["source"]["name"])

    def test_all_source_categories_have_distinct_witnesses(self):
        quran = json.loads((ROOT / "app/src/mucizedualar/assets/verified_quran_verses.json").read_text())
        hadith = json.loads((ROOT / "app/src/mucizedualar/assets/verified_hadith_supplications.json").read_text())
        weak = json.loads((ROOT / "app/src/mucizedualar/assets/attested_weak_hadith_supplications.json").read_text())
        additional = ("attested_very_weak_hadith_supplications.json", "documented_traditional_supplications.json")
        extra = [p for fn in additional for p in json.loads((ROOT / "app/src/mucizedualar/assets" / fn).read_text())["prayers"]]
        titles = [p["title"] for p in quran["prayers"] + hadith["prayers"] + self.data["prayers"] + weak["prayers"] + extra]
        self.assertEqual(20, len(titles))
        self.assertEqual(20, len(set(titles)))
        self.assertEqual({r["duaIsim"] for r in self.legacy}, set(titles))

if __name__ == "__main__":
    unittest.main()
