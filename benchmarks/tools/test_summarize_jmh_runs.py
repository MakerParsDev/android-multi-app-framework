"""Offline tests for the descriptive (non-gating) JMH summarizer."""

import json
import math
import tempfile
import unittest
from pathlib import Path

from summarize_jmh_runs import summarize, load_run


def payload(score: float, *, mode: str = "avgt", unit: str = "us/op"):
    return [{
        "benchmark": "example.Test.myBenchmark",
        "mode": mode,
        "forks": 2,
        "warmupIterations": 3,
        "measurementIterations": 6,
        "warmupTime": "1 s",
        "measurementTime": "1 s",
        "primaryMetric": {"score": score, "scoreUnit": unit},
    }]


class SummaryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def make(self, idx, rows):
        p = Path(self.tmp.name) / f"{idx}.json"
        p.write_text(json.dumps(rows), encoding="utf-8")
        return p

    def test_mean_range_and_cv(self):
        x = [self.make(i, payload(score)) for i, score in enumerate((10, 11, 9))]
        result = summarize(x)
        self.assertEqual(result["run_count"], 3)
        m = result["benchmarks"][0]
        self.assertAlmostEqual(m["mean"], 10)
        self.assertAlmostEqual(m["range_pct_vs_min"], 100 * 2 / 9)
        self.assertAlmostEqual(m["between_run_cv_pct"], 10)
        self.assertIn("not a significance test", result["caveat"])

    def test_single_file_not_independent_replications(self):
        with self.assertRaisesRegex(ValueError, "at least two"):
            summarize([self.make(1, payload(10))])

    def test_rejects_mode_mismatch(self):
        a = self.make(1, payload(10))
        b = self.make(2, payload(10, mode="thrpt"))
        with self.assertRaisesRegex(ValueError, "settings/mode/unit"):
            summarize([a, b])

    def test_rejects_unit_mismatch(self):
        with self.assertRaisesRegex(ValueError, "settings/mode/unit"):
            summarize([self.make(1, payload(10)), self.make(2, payload(20, unit="ns/op"))])

    def test_rejects_changed_measurement_count(self):
        a = self.make(1, payload(10))
        other = payload(10)
        other[0]["measurementIterations"] = 3
        with self.assertRaisesRegex(ValueError, "settings/mode/unit"):
            summarize([a, self.make(2, other)])

    def test_rejects_added_benchmark(self):
        a = self.make(1, payload(10))
        other = payload(10)
        second = payload(3)[0]
        second["benchmark"] = "different.Test.case"
        other.append(second)
        with self.assertRaisesRegex(ValueError, "benchmark set differs"):
            summarize([a, self.make(2, other)])

    def test_rejects_non_finite_and_negative_scores(self):
        for idx, score in enumerate((float("nan"), -1, 0, float("inf"))):
            with self.subTest(score=score), self.assertRaisesRegex(ValueError, "invalid positive finite"):
                load_run(self.make(idx, payload(score)))

    def test_rejects_duplicate(self):
        with self.assertRaisesRegex(ValueError, "duplicate"):
            load_run(self.make(1, payload(10) + payload(11)))


if __name__ == "__main__":
    unittest.main()
