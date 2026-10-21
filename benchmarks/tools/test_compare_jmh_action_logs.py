"""Offline checks for safe sample/provenance matching and fail-closed behavior."""

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from compare_jmh_action_logs import compare_logs, main, parse_evidence

PREFIX = "Run benchmarks\tReport sanitized JMH samples\t2026-10-21T12:54:15.538Z "
ENV_PREFIX = "Run benchmarks\tReport benchmark runner environment\t2026-10-21T12:52:00.001Z "
TREE_A = "f4e521dc5ec6bad6272c107c65ae3edc345f25e0"
TREE_B = "f4e521dc5ec6bad6272c107c65ae3edc345f25e1"
ENV = {
    "runner_os": "Linux", "runner_arch": "X64",
    "runner_image_os": "ubuntu24", "runner_image_version": "20261018.339.1",
    "kernel_arch": "x86_64", "cpu_model": "AMD EPYC 7763 64-Core Processor",
    "available_processors": "4"
}


def example(score=0.5):
    return {
        "benchmark": "sample.Example.foo", "mode": "cs", "unit": "s/op",
        "score": score, "fork_samples": [[score, score * 1.01, score * .99]]
    }


def log(row=None, env=None):
    row = example() if row is None else row
    env = ENV if env is None else env
    lines = [ENV_PREFIX + f"{k}={v}" for k, v in env.items()]
    lines += [ENV_PREFIX + 'openjdk version "21.0.12.1" 2026-08-18']
    lines += [PREFIX + "jmh_evidence_status=available benchmark_count=1"]
    lines += [PREFIX + "jmh_evidence_note=raw_jmh_samples_not_codspeed_profiler_scores"]
    lines += [PREFIX + "jmh_evidence=" + json.dumps(row, allow_nan=True)]
    return "\n".join(lines) + "\n"


class HostedSamplesTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def file(self, name, data):
        path = Path(self.tmp.name) / name
        path.write_text(data, encoding="utf-8")
        return path

    def pair(self, head=None, head_env=None):
        a = self.file("a.log", log())
        b = self.file("b.log", log(example(.6) if head is None else head, head_env))
        return a, b

    def test_parse_one(self):
        a, _ = self.pair()
        result = parse_evidence(a)
        self.assertEqual(len(result), 1)
        self.assertEqual(result["sample.Example.foo"]["mode"], "cs")

    def test_matched_requires_source_evidence(self):
        a, b = self.pair()
        self.assertEqual(compare_logs(a, b)["status"], "benchmark_source_unverified")

    def test_matched_descriptive_not_significance(self):
        a, b = self.pair()
        r = compare_logs(a, b, TREE_A, TREE_A)
        self.assertEqual(r["status"], "descriptive_review_only")
        self.assertAlmostEqual(r["scores"][0]["head_vs_base_pct"], 20.0)
        self.assertEqual(r["scores"][0]["samples_per_run"], [3, 3])
        self.assertIn("not prove statistical significance", r["caveat"])

    def test_cpu_mismatch_no_scores(self):
        a, b = self.pair(head_env={**ENV, "cpu_model": "AMD EPYC 9V45"})
        result = compare_logs(a, b, TREE_A, TREE_A)
        self.assertEqual(result["status"], "environment_mismatch")
        self.assertEqual(result["scores"], [])

    def test_source_mismatch_no_scores(self):
        a, b = self.pair()
        r = compare_logs(a, b, TREE_A, TREE_B)
        self.assertEqual(r["status"], "benchmark_source_mismatch")

    def test_mode_mismatch_no_scores(self):
        a, b = self.pair(head={**example(), "mode": "avgt"})
        r = compare_logs(a, b, TREE_A, TREE_A)
        self.assertEqual(r["status"], "incompatible_jmh_samples")

    def test_sample_shape_mismatch(self):
        row = example()
        row["fork_samples"] = [[.5, .51]]
        a, b = self.pair(head=row)
        self.assertEqual(compare_logs(a, b, TREE_A, TREE_A)["status"],
                         "incompatible_jmh_samples")

    def test_duplicate_is_invalid(self):
        a, _ = self.pair()
        with a.open("a") as out:
            out.write(PREFIX + "jmh_evidence=" + json.dumps(example()) + "\n")
        with self.assertRaisesRegex(ValueError, "duplicated"):
            parse_evidence(a)

    def test_incomplete_count_invalid(self):
        a = self.file("a.log", log().replace("benchmark_count=1", "benchmark_count=2"))
        with self.assertRaisesRegex(ValueError, "incomplete"):
            parse_evidence(a)

    def test_untrusted_step_is_ignored(self):
        a, _ = self.pair()
        with a.open("a") as out:
            out.write("Run benchmarks\tRun the benchmarks\t"
                      "2026-10-21T12:54:15.538Z jmh_evidence="
                      + json.dumps(example()) + "\n")
        self.assertEqual(len(parse_evidence(a)), 1)

    def test_nan_rejected_without_raw_leak(self):
        a, b = self.pair()
        with b.open("a") as out:
            out.write(PREFIX + "jmh_evidence=" + json.dumps(example(float("nan"))) + "\n")
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            with self.assertRaises(SystemExit) as caught:
                main([str(a), str(b)])
        self.assertEqual(caught.exception.code, 3)
        self.assertNotIn("nan", err.getvalue().lower())

    def test_rejects_same_log_as_two_replicates(self):
        a, _ = self.pair()
        with self.assertRaisesRegex(ValueError, "not independent"):
            compare_logs(a, a, TREE_A, TREE_A)

    def test_rejects_copied_log_content(self):
        a, b = self.pair()
        b.write_bytes(a.read_bytes())
        with self.assertRaisesRegex(ValueError, "not independent"):
            compare_logs(a, b, TREE_A, TREE_A)

    def test_missing_jmh_data_fails_closed(self):
        a, b = self.pair()
        b.write_text(log().replace("benchmark_count=1", "benchmark_count=3"))
        with self.assertRaisesRegex(ValueError, "incomplete"):
            compare_logs(a, b, TREE_A, TREE_A)

    def test_cli_mismatch_exit_two(self):
        a, b = self.pair(head_env={**ENV, "cpu_model": "other"})
        with contextlib.redirect_stdout(io.StringIO()):
            rc = main([str(a), str(b),
                       "--base-benchmark-tree", TREE_A,
                       "--head-benchmark-tree", TREE_A])
        self.assertEqual(rc, 2)


if __name__ == "__main__":
    unittest.main()
