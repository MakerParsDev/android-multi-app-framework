#!/usr/bin/env python3
"""Offline exact-text checks for verbatim Tanzil verses bundled in Mucize Dualar.

Reference: https://tanzil.net/pub/download/index.php
Uthmani v1.1 text with verse numbers; original download SHA256
6933e133dd56db778c801bf738848454e43648105a151e8d84d86a7cae39ec5f.
Pinned hashes below represent full Unicode Arabic strings without normalization.
"""

import hashlib
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PRAYERS = ROOT / "app/src/mucizedualar/assets/data.json"
SOURCE = ROOT / "app/src/mucizedualar/assets/verified_quran_verses.json"

GOLDEN_VERSES = {
    "38:35": "c00ac5006c73cd41c21f851267ae6949c5653e88b64856a717d6454f0969a056",
    "3:173": "b9d03fe93162be716ec6c63cff91693dcc4c78f60fdb21446985da8d3636d406",
    "59:22": "0f3dff1b9f4f6eae55fe1df8a0a400581b488617fe1319f5b4f48610a4de59dc",
    "59:23": "4357f9baa2c12bd70ed9126d0231cf6d10d3e7a3e8520fb30f1955b68459c3e1",
    "59:24": "3b9edfe0687fcca173919bec9bac2a0d38c1dce374d81919f193c8873f940467",
    "19:96": "5b189581bac93c9d124289f62563a7ce3c3595f5ae66422b17774baa181c2374",
    "68:51": "a4ac83d885f90d7eb1f54a5228b4777b99548e9c15dcfabaff8acb48a7537e61",
    "68:52": "b92f64aed523da78fb84d2bd8a096455f56e3a5929f958112c1d7ce244d707cf",
    "20:25": "638e7c1cab464a0b98eca54d8bb80062e90b94fad8517674769fbc177fb12551",
    "20:26": "141820db0e3b53ae0061211db24cd2aa7b866f8e81e8ba02e46fc0159e9fe6ae",
    "20:27": "1da0cadd01694ab4d546c99003dcdace60b582fca74477454059948a3a956663",
    "20:28": "1116aee50e5f55f6877a4dcd3ce6867d76bf4b938e079e04447f8b6eb4030966",
    "12:101": "c6d5c8fde6b48e6437d3f115a0180cff5d1055baec776145f1601d5cd03a3158",
    "2:255": "f1f9b8cd879ba4b078e1dada34552c927339009c5b2551ec287c7ee261d17e3a",
    "48:1": "2804c8515fb1e8549fc36d7f2e7b2a2e9f9c713c2ef339d7a401f067c3fad86e",
    "48:2": "1783840d4c2b765754c17073bd959cf1b869bcb33b4797cc18296251c1bd9533",
    "48:3": "9e77616709f3ea4c55d6586f90bc6ffca8e0e41912be449b5661ac201763c955",
    "48:4": "4e380b19f8a1b2651fc50801c56fb52378021bbdf22c7b36f0b5cd4169a51a08",
    "48:5": "a683bd0fc000a67de48b45e9373068eef4a1de4bb1022fb82f2db64a4f55cd96",
    "48:6": "b3e2b2bc05cc92e61f6ec8997734c88c83b4685a4c5627a051b029836e0b4835",
    "48:7": "fae97b3c22abcf5ed5a687e8968c55c7647bd3b31f7807fa7fffcc0161478974",
    "48:8": "119163b04d6d497026147f32e276262da9699869712e68dfee5219b7e558ff89",
    "48:9": "83289fdc8429590918213f2ad39848ec13f8b80382839826d3c0198b2cec39c2",
    "48:10": "76fe35c1cc78990a8a15a0da9a56ee7962c1042f83aeeceaa34ba46792157d87",
    "23:29": "c7f7568b68d6d5e592fcfe8fc3a0fb25b818051a3a352ec430e1012ca2da3c70",
    "2:107": "f71b72980437a9a052d328e04691a85d5ffdb2f4ee9c65223a1b9ac7d1709f46",
}


class TanzilOriginalArabicIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.legacy = json.loads(PRAYERS.read_text(encoding="utf-8"))
        cls.source = json.loads(SOURCE.read_text(encoding="utf-8"))

    def test_source_provenance_and_license_notice_are_retained(self):
        metadata = self.source["source"]
        self.assertEqual("Tanzil Quran Text", metadata["name"])
        self.assertEqual("Uthmani v1.1", metadata["edition"])
        self.assertEqual("https://tanzil.net/", metadata["url"])
        self.assertEqual("Creative Commons Attribution 3.0", metadata["license"])
        self.assertEqual(
            "6933e133dd56db778c801bf738848454e43648105a151e8d84d86a7cae39ec5f",
            metadata["downloadSha256"],
        )
        for segment in (
            "Copyright (C) 2007-2021 Tanzil Project",
            "CHANGING IT IS NOT ALLOWED",
            "tanzil.net",
            "Please check updates at:",
        ):
            self.assertIn(segment, metadata["notice"])

    def test_all_26_verses_are_exactly_pinned_without_normalization(self):
        expected = {
            "Zenginlik duası": ['38:35'],
            "Ruhsal sıkıntılardan kurtulma duası": ['3:173'],
            "Dilek duası": ['59:22', '59:23', '59:24'],
            "Aile mutluluğunu temin eden dua": ['19:96'],
            "Nazardan ve her türlü şerden koruyan dua": ['68:51', '68:52'],
            "Konuşma yeteneğini arttıran dua": ['20:25', '20:26', '20:27', '20:28'],
            "Kötü alışkanlıklardan kişiyi kurtaran dua": ['12:101'],
            "Her türlü dileğin gerçekleşmesi için okunan dua": ['2:255'],
            "Helal dairesindeki isteklerin gerçekleşmesini sağlayan dua": ['48:1', '48:2', '48:3', '48:4', '48:5', '48:6', '48:7', '48:8', '48:9', '48:10'],
            "İşlerin açılması için dua": ['23:29'],
            "Zor durumda kalanların okuyacağı dua": ['2:107'],
        }
        self.assertEqual(20, len(self.legacy))
        self.assertEqual(11, len(self.source["prayers"]))
        observed = {}
        titles = [r["duaIsim"] for r in self.legacy]
        for record in self.source["prayers"]:
            title = record["title"]
            self.assertIn(title, titles)
            self.assertNotIn(title, observed)
            refs = [v["reference"] for v in record["verses"]]
            observed[title] = refs
            for verse in record["verses"]:
                with self.subTest(reference=verse["reference"]):
                    self.assertIn(verse["reference"], GOLDEN_VERSES)
                    arabic = verse["arabic"]
                    self.assertEqual(
                        GOLDEN_VERSES[verse["reference"]],
                        hashlib.sha256(arabic.encode("utf-8")).hexdigest(),
                    )
                    self.assertTrue(any("\u0600" <= ch <= "\u06ff" for ch in arabic))
                    self.assertNotIn("(Amin)", arabic)

            self.assertEqual(
                title == "Helal dairesindeki isteklerin gerçekleşmesini sağlayan dua",
                record["includesBasmala"],
            )
            if record["includesBasmala"]:
                self.assertTrue(record["verses"][0]["arabic"].startswith("بِسْمِ"))

        self.assertEqual(expected, observed)
        self.assertEqual(set(GOLDEN_VERSES), {
            v["reference"] for r in self.source["prayers"] for v in r["verses"]
        })

    def test_original_prayer_latin_text_and_structure_stay_intact(self):
        # A source-only extension never replaces the legacy phonetic data.
        self.assertTrue(all(
            "originalArabic" not in prayer and prayer["duaArapca"]
            for prayer in self.legacy
        ))
        for record in self.source["prayers"]:
            latin = next(
                prayer["duaArapca"]
                for prayer in self.legacy if prayer["duaIsim"] == record["title"]
            )
            self.assertTrue(latin.endswith("(Amin)"))


if __name__ == "__main__":
    unittest.main()
