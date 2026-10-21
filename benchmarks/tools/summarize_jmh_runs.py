#!/usr/bin/env python3
"""Describe repeated JMH JSON measurements without declaring regressions.

For same-source, same-host experiments only. Separate CodSpeed Walltime
comparisons require their own provenance and source compatibility evidence.
"""

import argparse
import hashlib
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
        # Both the measurement plan and JVM identity can affect the score.
        # A compatible benchmark name/mode/iteration count alone is insufficient.
        setting_keys = (
            "jmhVersion", "jvm", "jvmArgs", "jdkVersion", "vmName",
            "vmVersion", "threads", "forks", "warmupIterations",
            "measurementIterations", "warmupTime", "measurementTime",
            "warmupBatchSize", "measurementBatchSize",
        )
        settings = tuple(item.get(f) for f in setting_keys)
        if (
            not isinstance(unit, str) or not unit or not isinstance(mode, str)
            or not mode or None in settings
            or not isinstance(item.get("jvmArgs"), list)
            or any(not isinstance(arg, str) for arg in item["jvmArgs"])
        ):
            raise ValueError(f"{path}: missing benchmark mode/unit/JVM/settings for {name}")
        # Lists are mutable and cannot compare safely to run metadata tuples.
        settings = tuple(
            tuple(value) if isinstance(value, list) else value
            for value in settings
        )
        forks = item["forks"]
        iterations = item["measurementIterations"]
        if (
            type(forks) is not int or forks < 1
            or type(iterations) is not int or iterations < 1
        ):
            raise ValueError(f"{path}: invalid JMH fork/iteration settings for {name}")
        # A score without the underlying samples can mask a truncated or
        # synthesized result. Require the expected number of fork samples.
        raw = metric.get("rawData")
        if (
            not isinstance(raw, list) or len(raw) != forks
            or any(
                not isinstance(sample, list) or len(sample) != iterations
                or any(
                    isinstance(value, bool) or not isinstance(value, (float, int))
                    or not math.isfinite(value) or value <= 0
                    for value in sample
                )
                for sample in raw
            )
        ):
            raise ValueError(f"{path}: missing/invalid JMH raw samples for {name}")
        if name in records:
            raise ValueError(f"{path}: duplicate benchmark {name}")
        records[name] = {
            "score": float(score),
            "unit": unit,
            "mode": mode,
            "settings": settings,
            "samples": sum(len(fork) for fork in raw),
        }
    return records


def summarize(paths: list[Path]) -> dict:
    if len(paths) < 2:
        raise ValueError("at least two independent JMH JSON runs are required")
    # Passing one run twice would give a misleading zero-variability estimate.
    # Even copied byte-identical JSON is not evidence of independent launches.
    hashes = [hashlib.sha256(p.read_bytes()).digest() for p in paths]
    if len(set(hashes)) != len(paths):
        raise ValueError("duplicate JMH run content; independent runs required")
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
            "samples_per_run": [run[name]["samples"] for run in parsed],
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
            "a CodSpeed noise-floor estimate. Confirm source/host provenance "
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
