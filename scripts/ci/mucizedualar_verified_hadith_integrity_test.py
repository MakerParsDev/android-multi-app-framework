#!/usr/bin/env python3
"""Exact-source guards for four classical hadith supplication excerpts, separate from Tanzil."""
import hashlib
import json
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
LEGACY = ROOT / "app/src/mucizedualar/assets/data.json"
ARABIC = ROOT / "app/src/mucizedualar/assets/verified_hadith_supplications.json"
GOLDEN = {'Sahih al-Bukhari 5675': 'ffc77c8692f4c17f24f7b2df91eb242db6995780b258ba3239dc1aae56791a24', 'Jami at-Tirmidhi 3577': 'ba17c4d7ce685848fefeec43982aa1ff9c99a0a47a9fe123d422932cd5aad440', 'Musnad Ahmad 446': '1cc5b5d84c163c7d9e6b778041834e3029962f333993ec6e6f10ce7b141bd3ed', 'Sunan Abi Dawud 3893': '9b9c82557be00c9251b885208106dd9679e741f09d6651f76d2244c40736735a'}
REFERENCES_BY_INDEX = {2: 'Sahih al-Bukhari 5675', 3: 'Jami at-Tirmidhi 3577', 7: 'Musnad Ahmad 446', 19: 'Sunan Abi Dawud 3893'}
class HadithSourceIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.legacy = json.loads(LEGACY.read_text())
        cls.data = json.loads(ARABIC.read_text())
    def test_four_unique_cited_excerpts_are_unchanged(self):
        self.assertEqual(20, len(self.legacy))
        self.assertEqual(4, len(self.data['prayers']))
        self.assertEqual('Cited classical hadith supplication excerpts', self.data['source']['name'])
        titles = []
        for index, ref in REFERENCES_BY_INDEX.items():
            expected = self.legacy[index - 1]
            entry = self.data['prayers'][len(titles)]
            titles.append(entry['title'])
            self.assertEqual(expected['duaIsim'], entry['title'])
            self.assertEqual(ref, entry['reference'])
            self.assertEqual('https://sunnah.com/', entry['sourceUrl'][:19])
            self.assertEqual(GOLDEN[ref], hashlib.sha256(entry['arabic'].encode()).hexdigest())
            self.assertTrue(any('\u0600' <= ch <= '\u06FF' for ch in entry['arabic']))
            self.assertNotIn('(Amin)', entry['arabic'])
            self.assertTrue(expected['duaArapca'].endswith('(Amin)'))
        self.assertEqual(4, len(set(titles)))
    def test_hadith_and_quran_attributions_never_overlap(self):
        quran = json.loads((ROOT / 'app/src/mucizedualar/assets/verified_quran_verses.json').read_text())
        self.assertTrue(set(x['title'] for x in self.data['prayers']).isdisjoint(x['title'] for x in quran['prayers']))
        for entry in self.data['prayers']:
            self.assertNotIn('verses', entry)
            self.assertNotIn('includesBasmala', entry)
if __name__ == '__main__':
    unittest.main()
