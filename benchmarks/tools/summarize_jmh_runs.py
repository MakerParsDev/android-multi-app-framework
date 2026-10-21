#!/usr/bin/env python3
"""Describe repeated JMH JSON measurements without declaring regressions.

For same-source, same-host experiments only. Separate CodSpeed Walltime
comparisons require their own provenance and source compatibility evidence.
"""

import argparse
import json
import math
import statistics
import sys
from pathlib import Path


def load_run(path: Path) -> dict[str, dict]:
    contents = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(contents, list) or not contents:
        raise ValueError(f"{path}: expected a nonempty JMH JSON results array")
    records: dict[str, dict] = {}
    for item in contents:
        if not isinstance(item, dict):
            raise ValueError(f"{path}: non-object JMH record")
        name = item.get("benchmark")
        metric = item.get("primaryMetric", {})
        if not isinstance(name, str) or not isinstance(metric, dict):
            raise ValueError(f"{path}: incomplete benchmark record")
        score = metric.get("score")
        if not isinstance(score, (float, int)) or isinstance(score, bool):
            raise ValueError(f"{path}: missing numeric score for {name}")
        if not math.isfinite(score) or score <= 0:
            raise ValueError(f"{path}: invalid positive finite score for {name}")
        unit = metric.get("scoreUnit")
        mode = item.get("mode")
        # Exact JMH configuration matters; avoid comparing mixed sample plans.
        settings = tuple(item.get(f) for f in (
            "forks", "warmupIterations", "measurementIterations",
            "warmupTime", "measurementTime",
        ))
        if not isinstance(unit, str) or not isinstance(mode, str) or None in settings:
            raise ValueError(f"{path}: missing benchmark mode/unit/settings for {name}")
        if name in records:
            raise ValueError(f"{path}: duplicate benchmark {name}")
        records[name] = {
            "score": float(score),
            "unit": unit,
            "mode": mode,
            "settings": settings,
        }
    return records


def summarize(paths: list[Path]) -> dict:
    if len(paths) < 2:
        raise ValueError("at least two independent JMH JSON runs are required")
    parsed = [load_run(p) for p in paths]
    names = set(parsed[0])
    for idx, current in enumerate(parsed[1:], start=1):
        if set(current) != names:
            raise ValueError(f"{paths[idx]}: benchmark set differs from first run")
    series = []
    for name in sorted(names):
        first = parsed[0][name]
        for idx, current in enumerate(parsed[1:], start=1):
            entry = current[name]
            if any(entry[k] != first[k] for k in ("unit", "mode", "settings")):
                raise ValueError(
                    f"{paths[idx]}: benchmark settings/mode/unit differ for {name}"
                )
        scores = [run[name]["score"] for run in parsed]
        avg = statistics.mean(scores)
        series.append({
            "benchmark": name,
            "mode": first["mode"],
            "unit": first["unit"],
            "runs": scores,
            "mean": avg,
            "min": min(scores),
            "max": max(scores),
            "range_pct_vs_min": 100 * (max(scores) - min(scores)) / min(scores),
            "between_run_cv_pct": 100 * statistics.stdev(scores) / avg,
        })
    return {
        "run_count": len(paths),
        "benchmarks": series,
        "caveat": (
            "Descriptive across-run variation only, not a significance test or "
            "a CodSpeed noise-floor estimate. Confirm source/host/JVM provenance "
            "before treating repeated measurements as comparable."
        ),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jmh_json", nargs="+", type=Path)
    parser.add_argument("--json", action="store_true", help="Emit structured summary")
    args = parser.parse_args(argv)
    try:
        result = summarize(args.jmh_json)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        parser.exit(3, f"JMH summary error: {exc}\n")
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print(f"JMH independent runs: {result['run_count']}")
        for item in result["benchmarks"]:
            print(
                f"  {item['benchmark']}: {item['mean']:.6g} {item['unit']} "
                f"(range/min={item['range_pct_vs_min']:.2f}%, "
                f"between-run CV={item['between_run_cv_pct']:.2f}%)"
            )
        print(result["caveat"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
