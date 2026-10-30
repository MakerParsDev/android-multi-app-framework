#!/usr/bin/env python3
"""Guard the debt supplication's source-verified Latin phonetic reading.

Witnesses: Sahih al-Bukhari 6369; Diyanet, Hadislerle Islam, volume 1,
page 168 (https://hadislerleislam.diyanet.gov.tr/sayfa.php?CILT=1&SAYFA=168).
This does not claim other Mucize Dualar entries have verified Arabic originals.
"""

import json
import unittest
from pathlib import Path


DATA = Path(__file__).resolve().parents[2] / "app/src/mucizedualar/assets/data.json"


class MiraclesDebtPronunciationIntegrityTest(unittest.TestCase):
    def test_debt_supplication_uses_cubn_not_cinn(self):
        rows = json.loads(DATA.read_text(encoding="utf-8"))
        self.assertEqual(20, len(rows))
        entry = rows[14]
        self.assertEqual("Borçlardan kurtulma duası", entry["duaIsim"])
        phonetic = entry["duaArapca"]
        self.assertIn("mine’l-cübni", phonetic)
        self.assertNotIn("mine’l-cinni", phonetic)


if __name__ == "__main__":
    unittest.main()
