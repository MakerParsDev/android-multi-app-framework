"""Non-gating pilot power-planning input and arithmetic checks."""

import json
import math
import tempfile
import unittest
from pathlib import Path

from plan_jmh_repetitions import plan

TREE = "f4e521dc5ec6bad6272c107c65ae3edc345f25e0"


def fixture(score: float, *, version="21.0.12.1"):
    return [{
        "benchmark": "test.Foo.criticalMethod", "mode": "avgt",
        "jmhVersion": "0.2.1", "jvm": "/usr/bin/java",
        "jvmArgs": [], "jdkVersion": version,
        "vmName": "OpenJDK VM", "vmVersion": "21.0.12",
        "threads": 1, "forks": 2, "warmupIterations": 3,
        "measurementIterations": 3, "warmupTime": "1 s",
        "measurementTime": "1 s", "warmupBatchSize": 1,
        "measurementBatchSize": 1,
        "primaryMetric": {
            "score": score, "scoreUnit": "us/op",
            "rawData": [[score]*3, [score]*3],
        },
    }]


class PlanningTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.paths = []
        for i, score in enumerate((100, 107, 99, 103, 104, 96, 100.1, 111, 101, 98)):
            f = Path(self.tmp.name) / f"pilot{i}.json"
            f.write_text(json.dumps(fixture(score)))
            self.paths.append(f)

    def report(self, **kwargs):
        return plan(self.paths, TREE, "ryzen2700x", **kwargs)

    def test_sample_sizes_grow_for_smaller_effect(self):
        result = self.report()
        scenarios = result["results"][0]["scenarios"]
        sizes = [x["independent_runs_per_arm_pilot_sd"] for x in scenarios]
        self.assertEqual(len(scenarios), 3)
        self.assertGreater(sizes[0], sizes[1])
        self.assertGreater(sizes[1], sizes[2])
        self.assertEqual(result["pilot_independent_runs"], 10)
        self.assertEqual(result["status"], "planning_only_not_a_perf_gate")

    def test_pilot_log_sd_matches_independent_means(self):
        import statistics
        result = self.report()["results"][0]
        pilot = (100, 107, 99, 103, 104, 96, 100.1, 111, 101, 98)
        self.assertAlmostEqual(
            result["pilot_log_standard_deviation"],
            statistics.stdev([math.log(x) for x in pilot]),
            places=12,
        )

    def test_exact_normal_approximation_formula(self):
        import statistics
        result = self.report()["results"][0]
        z = statistics.NormalDist()
        alpha=.05 / 15
        power=.8
        effect=math.log1p(.05)
        sigma=result["pilot_log_standard_deviation"]
        expected=max(2, math.ceil(2*(z.inv_cdf(1-alpha/2)+z.inv_cdf(power))**2*
                                  (sigma/effect)**2))
        self.assertEqual(result["scenarios"][1]["independent_runs_per_arm_pilot_sd"],
                         expected)

    def test_bonferroni_is_more_conservative(self):
        fifteen = self.report(family_size=15)
        one = self.report(family_size=1)
        self.assertGreater(
            fifteen["results"][0]["scenarios"][1]["independent_runs_per_arm_pilot_sd"],
            one["results"][0]["scenarios"][1]["independent_runs_per_arm_pilot_sd"],
        )

    def test_power_changes_budget(self):
        high = self.report(power=.9)
        low = self.report(power=.8)
        self.assertGreater(high["results"][0]["scenarios"][0]["independent_runs_per_arm_pilot_sd"],
                           low["results"][0]["scenarios"][0]["independent_runs_per_arm_pilot_sd"])

    def test_sensitivity_factor_increases_budget(self):
        result = self.report()
        for s in result["results"][0]["scenarios"]:
            self.assertGreaterEqual(s["independent_runs_per_arm_1_5x_sd"],
                                    s["independent_runs_per_arm_pilot_sd"])
            self.assertEqual(s["total_runs_two_arms_1_5x_sd"],
                             2*s["independent_runs_per_arm_1_5x_sd"])

    def test_fail_inadequate_independent_runs(self):
        with self.assertRaisesRegex(ValueError, "at least six"):
            plan(self.paths[:5], TREE, "ryzen2700x")

    def test_fail_duplicate_run_content(self):
        with self.assertRaisesRegex(ValueError, "duplicate"):
            plan(self.paths+[self.paths[0]], TREE, "ryzen2700x")

    def test_fail_unknown_source(self):
        with self.assertRaisesRegex(ValueError, "verified"):
            plan(self.paths, "garbage", "ryzen2700x")

    def test_fail_missing_host(self):
        with self.assertRaisesRegex(ValueError, "host label"):
            plan(self.paths, TREE, "")

    def test_fail_invalid_family(self):
        for num in (0,-2,101):
            with self.subTest(num=num), self.assertRaisesRegex(ValueError, "family_size"):
                self.report(family_size=num)

    def test_fail_unusable_alpha_or_power(self):
        for alpha in (0,-.01,.5,1):
            with self.subTest(alpha=alpha), self.assertRaisesRegex(ValueError, "alpha"):
                self.report(family_alpha=alpha)
        for power in (.4,1.0):
            with self.subTest(power=power), self.assertRaisesRegex(ValueError, "alpha"):
                self.report(power=power)

    def test_fail_incompatible_jvm(self):
        mutated = json.loads(self.paths[8].read_text())
        mutated[0]["jdkVersion"] = "22.0.1"
        self.paths[8].write_text(json.dumps(mutated))
        with self.assertRaisesRegex(ValueError, "settings/mode/unit"):
            self.report()

    def test_zero_between_run_variance_fails_closed(self):
        for idx, path in enumerate(self.paths):
            data = fixture(100)
            data[0]["primaryMetric"]["rawData"][0][0] = 100 + idx*.01
            path.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "zero/near-zero pilot variance"):
            self.report()

    def test_fail_missing_raw_samples(self):
        mutated = json.loads(self.paths[8].read_text())
        mutated[0]["primaryMetric"]["rawData"] = []
        self.paths[8].write_text(json.dumps(mutated))
        with self.assertRaisesRegex(ValueError, "raw samples"):
            self.report()


if __name__ == "__main__":
    unittest.main()
