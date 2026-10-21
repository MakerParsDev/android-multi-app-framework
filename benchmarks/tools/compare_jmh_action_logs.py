#!/usr/bin/env python3
"""Compare sanitized JMH samples from two real CodSpeed GitHub Actions logs.

A descriptive, offline investigation tool, never a benchmark gate. It refuses
cross-environment/source comparisons and does not assess CodSpeed profiler scores.
"""

import argparse
import hashlib
import json
import math
import re
import sys
from pathlib import Path

from compare_runner_provenance import compare as compare_environment
from compare_runner_provenance import parse_log as parse_environment
from report_jmh_samples import extract_entries

STEP = "\tReport sanitized JMH samples\t"
TIMESTAMP = re.compile(r"\d{4}-\d\d-\d\dT\S+Z\s+(.*)$")
COUNT = re.compile(r"jmh_evidence_status=available benchmark_count=(\d+)\Z")
TREE = re.compile(r"[0-9a-f]{40}\Z")


def parse_evidence(path: Path) -> dict[str, dict]:
    """Accept only entries printed by the exact evidence step, not echoed code."""
    values = {}
    count = None
    seen_status = False
    with path.open("r", encoding="utf-8", errors="replace") as stream:
        for line in stream:
            if STEP not in line:
                continue
            match = TIMESTAMP.search(line)
            if match is None:
                continue
            payload = match.group(1).strip()
            if payload.startswith("jmh_evidence_status="):
                if seen_status:
                    raise ValueError("multiple JMH evidence statuses")
                seen_status = True
                m = COUNT.fullmatch(payload)
                if m is None:
                    raise ValueError("missing or invalid JMH evidence")
                count = int(m.group(1))
                if not 0 < count <= 100:
                    raise ValueError("out of range JMH benchmark count")
            elif payload.startswith("jmh_evidence="):
                if len(values) >= 100:
                    raise ValueError("too many JMH evidence records")
                row = json.loads(
                    payload[len("jmh_evidence="):],
                    parse_constant=lambda x: (_ for _ in ()).throw(
                        ValueError("nonfinite JSON constant")
                    ),
                )
                if not isinstance(row, dict) or set(row) != {
                    "benchmark", "mode", "unit", "score", "fork_samples"
                }:
                    raise ValueError("unexpected JMH evidence fields")
                # Reuse identical sanitation/limits as the reporter.
                validated = extract_entries(json.dumps([{
                    "benchmark": row["benchmark"],
                    "mode": row["mode"],
                    "primaryMetric": {
                        "scoreUnit": row["unit"],
                        "score": row["score"],
                        "rawData": row["fork_samples"],
                    },
                }]).encode("utf-8"))[0]
                name = validated["benchmark"]
                if name in values:
                    raise ValueError("duplicated JMH benchmark")
                values[name] = validated
    if count is None or count != len(values):
        raise ValueError("incomplete JMH evidence records")
    return values


def compare_logs(
    left: Path,
    right: Path,
    left_tree: str | None = None,
    right_tree: str | None = None,
) -> dict:
    # Copied/same logs cannot serve as independent measurement replicates.
    def digest(path: Path) -> bytes:
        h = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                h.update(chunk)
        return h.digest()

    if digest(left) == digest(right):
        raise ValueError("identical logs are not independent runs")
    evidence_left = parse_evidence(left)
    evidence_right = parse_evidence(right)
    env = compare_environment(parse_environment(left), parse_environment(right))
    differences = {}
    if set(evidence_left) != set(evidence_right):
        differences["benchmark_set"] = {
            "only_left": sorted(set(evidence_left) - set(evidence_right)),
            "only_right": sorted(set(evidence_right) - set(evidence_left)),
        }
    for name in sorted(set(evidence_left) & set(evidence_right)):
        a, b = evidence_left[name], evidence_right[name]
        for key in ("mode", "unit"):
            if a[key] != b[key]:
                differences.setdefault(name, {})[key] = [a[key], b[key]]
        shapes = [[len(f) for f in row["fork_samples"]] for row in (a, b)]
        if shapes[0] != shapes[1]:
            differences.setdefault(name, {})["sample_shape"] = shapes
    tree_evidence = (
        "missing" if left_tree is None or right_tree is None
        else "invalid" if not TREE.fullmatch(left_tree) or not TREE.fullmatch(right_tree)
        else "different" if left_tree != right_tree else "same"
    )
    if env["status"] != "metadata_matched":
        status = env["status"]
    elif differences:
        status = "incompatible_jmh_samples"
    elif tree_evidence == "different":
        status = "benchmark_source_mismatch"
    elif tree_evidence != "same":
        status = "benchmark_source_unverified"
    else:
        status = "descriptive_review_only"
    result = {
        "status": status,
        "environment": env["status"],
        "environment_differences": env["differences"],
        "source_tree": tree_evidence,
        "sample_compatibility_differences": differences,
        "benchmark_count": len(evidence_left),
        "scores": [],
        "caveat": (
            "JMH-generated 1-fork/3-iteration samples are not CodSpeed's "
            "separate profiler scores. Matching metadata and benchmark source "
            "still do not prove statistical significance or equal host load. "
            "Never suppress or waive CodSpeed/CI failures."
        ),
    }
    if status == "descriptive_review_only":
        result["scores"] = [
            {
                "benchmark": name,
                "unit": evidence_left[name]["unit"],
                "base_score": evidence_left[name]["score"],
                "head_score": evidence_right[name]["score"],
                "head_vs_base_pct": round(
                    100 * (evidence_right[name]["score"]
                           / evidence_left[name]["score"] - 1), 6
                ),
                "samples_per_run": [
                    sum(len(f) for f in evidence_left[name]["fork_samples"]),
                    sum(len(f) for f in evidence_right[name]["fork_samples"]),
                ],
            }
            for name in sorted(evidence_left)
        ]
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_log", type=Path)
    parser.add_argument("head_log", type=Path)
    parser.add_argument("--base-benchmark-tree")
    parser.add_argument("--head-benchmark-tree")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)
    try:
        result = compare_logs(
            args.base_log, args.head_log,
            args.base_benchmark_tree, args.head_benchmark_tree,
        )
    except (OSError, ValueError, UnicodeError) as exc:
        parser.exit(3, "JMH log evidence unavailable or invalid\n")
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print("Hosted JMH evidence:", result["status"])
        print("Runner metadata:", result["environment"])
        print("Benchmark source:", result["source_tree"])
        if result["scores"]:
            for row in result["scores"]:
                print(f"  {row['benchmark']}: descriptive {row['head_vs_base_pct']:+.2f}%")
        print(result["caveat"])
    return (
        0 if result["status"] == "descriptive_review_only"
        else 2 if result["status"] in (
            "environment_mismatch", "incompatible_jmh_samples",
            "benchmark_source_mismatch"
        ) else 3
    )


if __name__ == "__main__":
    sys.exit(main())
