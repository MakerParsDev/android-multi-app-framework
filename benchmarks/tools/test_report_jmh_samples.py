"""Offline tests: never disclose arbitrary JMH metadata to CI logs."""

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from report_jmh_samples import extract_entries, main


def valid_data():
    return [{
        "benchmark": "sample.Example.fastTest",
        "mode": "cs",
        "jvmArgs": ["-Dprivate.token=NEVER_LOG_ME"],
        "primaryMetric": {
            "score": 0.000123456,
            "scoreUnit": "s/op",
            "rawData": [[0.00012, 0.00014, 0.00011]],
        },
    }]


class EvidenceTests(unittest.TestCase):
    def test_only_numeric_evidence_exposed(self):
        rows = extract_entries(json.dumps(valid_data()).encode())
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["score"], 0.000123456)
        self.assertNotIn("jvmArgs", rows[0])
        self.assertNotIn("NEVER_LOG_ME", json.dumps(rows))

    def test_normalized_cli_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "data.json"
            path.write_text(json.dumps(valid_data()))
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(main([str(path)]), 0)
            self.assertIn("jmh_evidence_status=available", out.getvalue())
            self.assertIn("jmh_evidence=", out.getvalue())
            self.assertNotIn("NEVER_LOG_ME", out.getvalue())

    def test_missing_file_does_not_change_ci_gate(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(main(["/nonexistent/jmh/results.json"]), 0)
        self.assertIn("status=unavailable", out.getvalue())

    def test_truncated_json_does_not_fail_benchmark(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "result.json"
            p.write_text("{no valid json")
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(main([str(p)]), 0)
            self.assertIn("reason=missing_or_invalid_result", out.getvalue())

    def test_reject_duplicate_names(self):
        x = valid_data()
        with self.assertRaisesRegex(ValueError, "label"):
            extract_entries(json.dumps(x + x).encode())

    def test_reject_benchmark_injection(self):
        x = valid_data()
        x[0]["benchmark"] = "test\n::error::secret"
        with self.assertRaisesRegex(ValueError, "label"):
            extract_entries(json.dumps(x).encode())

    def test_reject_nonfinite_samples(self):
        x = valid_data()
        x[0]["primaryMetric"]["rawData"][0][0] = float("nan")
        with self.assertRaisesRegex(ValueError, "non-finite"):
            extract_entries(json.dumps(x).encode())

    def test_reject_nonfinite_score(self):
        x = valid_data()
        x[0]["primaryMetric"]["score"] = float("inf")
        with self.assertRaisesRegex(ValueError, "non-finite"):
            extract_entries(json.dumps(x).encode())

    def test_reject_no_samples(self):
        x = valid_data()
        x[0]["primaryMetric"]["rawData"] = []
        with self.assertRaisesRegex(ValueError, "raw sample"):
            extract_entries(json.dumps(x).encode())

    def test_reject_overlong_name(self):
        x = valid_data()
        x[0]["benchmark"] = "X" * 201
        with self.assertRaisesRegex(ValueError, "label"):
            extract_entries(json.dumps(x).encode())


if __name__ == "__main__":
    unittest.main()
