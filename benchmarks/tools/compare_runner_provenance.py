#!/usr/bin/env python3
"""Compare nonsecret CodSpeed runner metadata from two GitHub Actions logs.

This is an evidence classifier, not a performance test or a CI gate. It never
suppresses an external benchmark regression and does not request GitHub access.
"""

import argparse
import json
import re
import sys
from pathlib import Path

FIELDS = (
    "runner_os",
    "runner_arch",
    "runner_image_os",
    "runner_image_version",
    "kernel_arch",
    "cpu_model",
    "available_processors",
    "java_version",
)
TIMESTAMP = re.compile(r"\d{4}-\d\d-\d\dT\S+Z\s+(.*)$")
JAVA = re.compile(r'^(?:openjdk|java) version "([^"]+)"(?:\s|$)')
VALUE = re.compile(r"^([a-z][a-z0-9_]*)=([^\r\n]+)$")


def parse_log(path: Path) -> dict[str, str]:
    """Read only the diagnostic step's actual output (not echoed shell source)."""
    values: dict[str, str] = {}
    with path.open("r", encoding="utf-8", errors="replace") as stream:
        for line in stream:
            # The explicit step name prevents parsing unrelated workflows or
            # echoed run commands containing the same key names.
            if "\tReport benchmark runner environment\t" not in line:
                continue
            match = TIMESTAMP.search(line)
            if not match:
                continue
            payload = match.group(1).strip()
            pair = VALUE.fullmatch(payload)
            if pair and pair.group(1) in FIELDS:
                key, value = pair.groups()
            else:
                java = JAVA.match(payload)
                if java is None:
                    continue
                key, value = "java_version", java.group(1)
            if key in values and values[key] != value:
                raise ValueError(f"{path}: conflicting {key} values in one log")
            values[key] = value
    return values


def compare(left: dict[str, str], right: dict[str, str]) -> dict:
    missing = {
        "left": [f for f in FIELDS if not left.get(f) or left[f] == "unknown"],
        "right": [f for f in FIELDS if not right.get(f) or right[f] == "unknown"],
    }
    differences = {
        f: {"left": left[f], "right": right[f]}
        for f in FIELDS
        if f in left and f in right and left[f] != right[f]
    }
    # "matched" is a provisional *metadata* match, not a hardware/performance
    # equivalence guarantee (contention, CPU microcode, load, and JMH vary).
    status = (
        "incomplete"
        if missing["left"] or missing["right"]
        else "environment_mismatch"
        if differences
        else "metadata_matched"
    )
    return {
        "status": status,
        "left": {f: left[f] for f in FIELDS if f in left},
        "right": {f: right[f] for f in FIELDS if f in right},
        "missing": missing,
        "differences": differences,
        "caveat": (
            "Matched metadata does not guarantee identical host resources or "
            "prove statistical significance. Never bypass CodSpeed or CI checks."
        ),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("left_log", type=Path)
    parser.add_argument("right_log", type=Path)
    parser.add_argument("--json", action="store_true", help="Emit structured summary")
    args = parser.parse_args(argv)
    try:
        result = compare(parse_log(args.left_log), parse_log(args.right_log))
    except (OSError, ValueError) as exc:
        parser.exit(3, f"Runner provenance read error: {exc}\n")
    if args.json:
        print(json.dumps(result, indent=2, ensure_ascii=False))
    else:
        print("Runner provenance:", result["status"])
        for key in FIELDS:
            left = result["left"].get(key, "(missing)")
            right = result["right"].get(key, "(missing)")
            print(f"  {key}: {left}  |  {right}")
        print(result["caveat"])
    return {"metadata_matched": 0, "environment_mismatch": 2, "incomplete": 3}[
        result["status"]
    ]


if __name__ == "__main__":
    sys.exit(main())
