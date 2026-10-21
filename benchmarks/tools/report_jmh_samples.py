#!/usr/bin/env python3
"""Emit bounded, sanitized numeric JMH evidence to CI logs.

Not a performance gate or a replacement for CodSpeed's own analysis.
Never print JVM arguments, paths, process environment, source files or secrets.
"""

import json
import math
import re
import sys
from pathlib import Path

MAX_FILE_BYTES = 2_000_000
MAX_BENCHMARKS = 100
MAX_FORKS = 16
MAX_ITERATIONS = 100
SAFE_NAME = re.compile(r"[A-Za-z0-9_.$]{1,200}\Z")
SAFE_UNIT = re.compile(r"[A-Za-z0-9/%._-]{1,32}\Z")
SAFE_MODE = re.compile(r"[a-z0-9_]{1,16}\Z")


def _strict_constant(value: str):
    raise ValueError(f"unsupported non-finite JSON value: {value}")


def extract_entries(raw: bytes) -> list[dict]:
    """Return only allowlisted, finite numeric measurements and safe labels."""
    result = json.loads(raw.decode("utf-8"), parse_constant=_strict_constant)
    if not isinstance(result, list) or not 0 < len(result) <= MAX_BENCHMARKS:
        raise ValueError("invalid JMH benchmark list size")
    output = []
    seen = set()
    for item in result:
        if not isinstance(item, dict):
            raise ValueError("non-object benchmark entry")
        name, mode = item.get("benchmark"), item.get("mode")
        metric = item.get("primaryMetric")
        if (
            not isinstance(name, str) or not SAFE_NAME.fullmatch(name)
            or name in seen or not isinstance(mode, str)
            or not SAFE_MODE.fullmatch(mode)
            or not isinstance(metric, dict)
        ):
            raise ValueError("missing, duplicate or unsafe benchmark label")
        seen.add(name)
        unit = metric.get("scoreUnit")
        score = metric.get("score")
        raw_data = metric.get("rawData")
        if not isinstance(unit, str) or not SAFE_UNIT.fullmatch(unit):
            raise ValueError("invalid JMH score unit")
        if (type(score) not in (float, int) or not math.isfinite(score)
                or score <= 0):
            raise ValueError("missing or non-positive JMH score")
        if (
            not isinstance(raw_data, list) or not 0 < len(raw_data) <= MAX_FORKS
            or any(
                not isinstance(fork, list)
                or not 0 < len(fork) <= MAX_ITERATIONS
                or any(
                    type(v) not in (float, int)
                    or not math.isfinite(v) or v <= 0
                    for v in fork
                )
                for fork in raw_data
            )
        ):
            raise ValueError("missing or invalid JMH raw sample arrays")
        output.append({
            "benchmark": name,
            "mode": mode,
            "unit": unit,
            "score": score,
            "fork_samples": raw_data,
        })
    return output


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 1:
        print("jmh_evidence_status=unavailable reason=expected_one_file")
        return 0  # diagnostics must never alter the existing benchmark gate
    file = Path(args[0])
    try:
        size = file.stat().st_size
        if not 0 < size <= MAX_FILE_BYTES:
            raise ValueError("invalid JMH result file size")
        rows = extract_entries(file.read_bytes())
    except (OSError, UnicodeError, ValueError) as exc:
        # The source path, exception or raw file may contain private data.
        # Do not echo any of them into the public job log.
        print("jmh_evidence_status=unavailable reason=missing_or_invalid_result")
        return 0
    print(f"jmh_evidence_status=available benchmark_count={len(rows)}")
    print("jmh_evidence_note=raw_jmh_samples_not_codspeed_profiler_scores")
    for row in rows:
        print("jmh_evidence=" + json.dumps(row, separators=(",", ":"), allow_nan=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
