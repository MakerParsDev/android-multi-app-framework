#!/usr/bin/env python3
"""Prospective, non-gating JMH replication budget from independent run means.

Calculations are normal-approximation planning scenarios, not a significance
test, a CI quality threshold, or a CodSpeed hosted Walltime noise estimate.
"""
import argparse
import json
import math
import re
import statistics
import sys
from pathlib import Path

from summarize_jmh_runs import summarize

SHA = re.compile(r"[0-9a-f]{40}\Z")
SCENARIOS = (2, 5, 10)
# Intentional, explicit sensitivity analysis: not a statistical upper bound.
UNCERTAINTY_MULTIPLIER = 1.5
MAX_PLANNED_PER_ARM = 1_000_000


def plan(
    paths: list[Path],
    benchmark_tree: str,
    host_label: str,
    family_size: int = 15,
    family_alpha: float = 0.05,
    power: float = 0.80,
) -> dict:
    if not SHA.fullmatch(benchmark_tree):
        raise ValueError("a verified 40-character Git benchmark tree SHA is required")
    if not isinstance(host_label, str) or not 1 <= len(host_label) <= 100:
        raise ValueError("a descriptive host label is required")
    if any(ord(c) < 32 or ord(c) > 126 for c in host_label):
        raise ValueError("host label must be printable ASCII")
    if type(family_size) is not int or not 1 <= family_size <= 100:
        raise ValueError("family_size must be between 1 and 100")
    if not 0 < family_alpha < 0.5 or not 0.5 < power < 1:
        raise ValueError("invalid alpha or statistical power")
    if len(paths) < 6:
        raise ValueError("at least six independent pilot runs required")
    baseline = summarize(paths)
    z = statistics.NormalDist()
    z_reject = z.inv_cdf(1 - family_alpha / (2 * family_size))
    z_power = z.inv_cdf(power)
    normal_factor = 2 * (z_reject + z_power)**2
    results = []
    for benchmark in baseline["benchmarks"]:
        scores = benchmark["runs"]
        if len(scores) < 6:
            raise ValueError("too few independent benchmark run means")
        # One independent JVM invocation is the unit of replication.
        sigma_log = statistics.stdev([math.log(score) for score in scores])
        if sigma_log < 1e-12:
            raise ValueError(
                "zero/near-zero pilot variance is not a reliable sample-size estimate"
            )
        scenarios = []
        for target_percent in SCENARIOS:
            effect_log = math.log1p(target_percent / 100)
            def estimate(sd):
                computed = normal_factor * (sd / effect_log)**2
                if not math.isfinite(computed):
                    raise ValueError("nonfinite estimated sample size")
                return max(2, math.ceil(computed))
            n = estimate(sigma_log)
            conservative = estimate(sigma_log * UNCERTAINTY_MULTIPLIER)
            scenarios.append({
                "target_change_percent": target_percent,
                "independent_runs_per_arm_pilot_sd": n,
                "independent_runs_per_arm_1_5x_sd": conservative,
                "total_runs_two_arms_1_5x_sd": conservative * 2,
                "sensitivity_exceeds_one_million_runs_per_arm":
                    conservative > MAX_PLANNED_PER_ARM,
            })
        results.append({
            "benchmark": benchmark["benchmark"],
            "mode": benchmark["mode"],
            "unit": benchmark["unit"],
            "pilot_independent_runs": len(scores),
            "pilot_between_run_cv_percent": benchmark["between_run_cv_pct"],
            "pilot_log_standard_deviation": sigma_log,
            "scenarios": scenarios,
        })
    return {
        "status": "planning_only_not_a_perf_gate",
        "benchmark_tree": benchmark_tree,
        "host_label_unverified": host_label,
        "pilot_independent_runs": baseline["run_count"],
        "hypothetical_two_arm_design": {
            "alpha_family": family_alpha,
            "family_size_bonferroni": family_size,
            "alpha_per_endpoint": family_alpha/family_size,
            "desired_power": power,
            "test": "two-sided normal approximation on log independent run means",
            "variance_model": "equal between-run log SD in head and base arms",
            "no_interim_peeking": True,
        },
        "results": results,
        "limits": [
            "Planning scenarios only: estimates use local JMH mode/host, NOT CodSpeed hosted Walltime; re-estimate hosted variance from independent matched-environment runs.",
            "1.5x pilot log SD is a sensitivity scenario, NOT a validated upper confidence limit.",
            "Actual runner CPU/image/JDK equivalence, source tree and execution parameters must be independently verified; CLI host label is not proof.",
            "Never count JMH iterations or forks as independent hosted workflow runs.",
            "Random hosting drift, autocorrelation, baseline movement, skew and multiple comparisons can invalidate the normal approximation.",
            "Choose endpoints and practical effect before collecting comparison data; do not stop sampling when a favorable p-value appears.",
            "Do not modify CodSpeed external assessments, thresholds, merge protections or paid runner settings based on these calculations.",
        ],
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pilot_json", nargs="+", type=Path)
    parser.add_argument("--benchmark-tree", required=True)
    parser.add_argument("--host-label", required=True)
    parser.add_argument("--family-size", type=int, default=15)
    parser.add_argument("--family-alpha", type=float, default=0.05)
    parser.add_argument("--power", type=float, default=0.8)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)
    try:
        report = plan(
            args.pilot_json,
            args.benchmark_tree,
            args.host_label,
            args.family_size,
            args.family_alpha,
            args.power,
        )
    except (OSError, ValueError, OverflowError) as exc:
        parser.exit(3, f"JMH pilot planning input invalid: {exc}\n")
    if args.json:
        print(json.dumps(report, indent=2))
    else:
        print("JMH pilot replication planning (not a CI gate)")
        print("Independent JVM launches:", report["pilot_independent_runs"])
        print("Bonferroni family size:", report["hypothetical_two_arm_design"]["family_size_bonferroni"])
        for row in report["results"]:
            print(row["benchmark"].split(".")[-1],
                  f"log-SD={row['pilot_log_standard_deviation']:.4g}")
            for scenario in row["scenarios"]:
                print(
                    f"  {scenario['target_change_percent']}% hypothetical change:"
                    f" {scenario['independent_runs_per_arm_pilot_sd']}/arm pilot,"
                    f" {scenario['independent_runs_per_arm_1_5x_sd']}/arm"
                    f" at 1.5x pilot SD"
                )
        print("Only an exploratory local-variance scenario; not a hosted Walltime sample requirement.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
