"""Offline, stdlib-only tests for CodSpeed provenance classification."""

import json
import tempfile
import unittest
from pathlib import Path

from compare_runner_provenance import FIELDS, compare, main, parse_log

PREFIX = (
    "Run benchmarks\tReport benchmark runner environment\t"
    "2026-10-21T11:15:23.2500000Z "
)
DATA = {
    "runner_os": "Linux",
    "runner_arch": "X64",
    "runner_image_os": "ubuntu24",
    "runner_image_version": "20261018.339.1",
    "kernel_arch": "x86_64",
    "cpu_model": "AMD EPYC 7763 64-Core Processor",
    "available_processors": "4",
    "java_version": "21.0.12.1",
}


def log_lines(data=DATA):
    parts = [PREFIX + f"{k}={v}" for k, v in data.items() if k != "java_version"]
    if "java_version" in data:
        parts += [PREFIX + f'openjdk version "{data["java_version"]}" 2026-08-18 LTS']
    return "\n".join(parts) + "\n"


class ProvenanceTests(unittest.TestCase):
    def test_metadata_match_not_performance_claim(self):
        result = compare(DATA, dict(DATA))
        self.assertEqual(result["status"], "metadata_matched")
        self.assertEqual(result["differences"], {})
        self.assertIn("does not guarantee", result["caveat"])

    def test_cpu_mismatch(self):
        other = {**DATA, "cpu_model": "AMD EPYC 9V45 96-Core Processor"}
        result = compare(DATA, other)
        self.assertEqual(result["status"], "environment_mismatch")
        self.assertEqual(list(result["differences"]), ["cpu_model"])

    def test_image_mismatch_with_identical_cpu(self):
        other = {**DATA, "runner_image_version": "20261012.334.1"}
        self.assertIn("runner_image_version", compare(DATA, other)["differences"])

    def test_unknown_sentinel_is_incomplete_not_a_match(self):
        value = {**DATA, "cpu_model": "unknown"}
        self.assertEqual(compare(value, value)["status"], "incomplete")

    def test_missing_fields_cannot_be_called_matched(self):
        result = compare(DATA, {"runner_os": "Linux"})
        self.assertEqual(result["status"], "incomplete")
        self.assertEqual(len(result["missing"]["right"]), len(FIELDS) - 1)

    def test_parses_actual_step_only_not_echoed_shell_source(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "run.log"
            path.write_text(
                PREFIX + "printf 'cpu_model=%s\\n' \"$cpu_model\"\n"
                + "Run benchmarks\tRun the benchmarks\t2026-10-21T11:15:23Z cpu_model=INVALID\n"
                + log_lines(),
                encoding="utf-8",
            )
            self.assertEqual(parse_log(path), DATA)

    def test_conflicting_metadata_fails_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "run.log"
            path.write_text(
                log_lines() + PREFIX + "cpu_model=Conflicting CPU\n", encoding="utf-8"
            )
            with self.assertRaisesRegex(ValueError, "conflicting cpu_model"):
                parse_log(path)

    def test_exit_codes_match_mismatch_and_missing(self):
        with tempfile.TemporaryDirectory() as folder:
            left = Path(folder) / "left.log"
            right = Path(folder) / "right.log"
            left.write_text(log_lines(), encoding="utf-8")
            right.write_text(log_lines(), encoding="utf-8")
            self.assertEqual(main([str(left), str(right), "--json"]), 0)
            right.write_text(
                log_lines({**DATA, "cpu_model": "different CPU"}), encoding="utf-8"
            )
            self.assertEqual(main([str(left), str(right), "--json"]), 2)
            right.write_text(PREFIX + "cpu_model=unknown\n", encoding="utf-8")
            self.assertEqual(main([str(left), str(right), "--json"]), 3)


if __name__ == "__main__":
    unittest.main()
