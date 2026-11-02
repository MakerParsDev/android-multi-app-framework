#!/usr/bin/env python3
"""Pin two historically attested Arabic phrases without claiming hadith authenticity."""
import hashlib
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/mucizedualar/assets"
EXPECTED = {'Nimetleri bollaştıran şükür duası': '5c4cf2a40a8cfa58887079debfb39ab6897427b5ea361767daef3622b93ec0cc', 'Borçlardan kurtulma duası': '7a76fd77af3bd920b247e5b689b57241c8cfdbce9d84c445e52d089e6478770d'}

class WeakReportSourceIntegrityTest(unittest.TestCase):
    def test_exact_attested_text_and_grading(self):
        rows = json.loads((ASSETS / "data.json").read_text(encoding="utf-8"))
        source = json.loads((ASSETS / "attested_weak_hadith_supplications.json").read_text(encoding="utf-8"))
        self.assertEqual(20, len(rows))
        self.assertEqual(2, len(source["prayers"]))
        self.assertIn("graded weak", source["source"]["name"])
        self.assertEqual(
            ["Sunan Ibn Majah 3801", "Sunan Abi Dawud 1555"],
            [p["reference"] for p in source["prayers"]],
        )
        for index, record, grader in zip((8,14), source["prayers"], ("Darussalam", "Al-Albani")):
            self.assertEqual(rows[index]["duaIsim"], record["title"])
            self.assertEqual("Daif", record["grade"])
            self.assertEqual(grader, record["grader"])
            self.assertEqual(
                EXPECTED[record["title"]], hashlib.sha256(record["arabic"].encode("utf-8")).hexdigest()
            )
            self.assertTrue(record["sourceUrl"].startswith("https://sunnah.com/"))
            self.assertTrue(any("\u0600" <= c <= "\u06ff" for c in record["arabic"]))
            self.assertNotIn("(Amin)", record["arabic"])
            self.assertTrue(rows[index]["duaArapca"].endswith("(Amin)"))

    def test_sources_do_not_overlap_and_two_records_remain_unverified(self):
        rows = json.loads((ASSETS / "data.json").read_text(encoding="utf-8"))
        files = (
            "verified_quran_verses.json",
            "verified_hadith_supplications.json",
            "verified_scholarly_supplications.json",
            "attested_weak_hadith_supplications.json",
        )
        titles = [p["title"] for fn in files for p in json.loads((ASSETS / fn).read_text())["prayers"]]
        self.assertEqual(18, len(titles))
        self.assertEqual(18, len(set(titles)))
        self.assertEqual({4,17}, {i for i,p in enumerate(rows) if p["duaIsim"] not in titles})

if __name__ == "__main__":
    unittest.main()
