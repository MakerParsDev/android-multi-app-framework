#!/usr/bin/env python3
"""Evaluate a 24/48/72-hour release health snapshot against repository policy."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Optional, Sequence

from runtime_health_policy import (
    DECISION_RANK,
    evaluate_snapshot,
    load_json,
    parse_flavor_packages,
    report_markdown,
    validate_policy,
)

ROOT = Path(__file__).resolve().parents[2]


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", required=True)
    parser.add_argument("--policy", default="config/runtime-observability-policy.json")
    parser.add_argument("--expected-checkpoint", type=int, choices=(24, 48, 72))
    parser.add_argument("--fail-on", choices=("watch", "hotfix", "rollback"), default="hotfix")
    parser.add_argument("--output-dir", default="build/reports/release-health")
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    policy = load_json((ROOT / args.policy).resolve())
    policy_errors = validate_policy(policy)
    if policy_errors:
        raise ValueError("Invalid policy: %s" % "; ".join(policy_errors))
    snapshot = load_json((ROOT / args.snapshot).resolve())
    if args.expected_checkpoint is not None and snapshot.get("checkpoint_hours") != args.expected_checkpoint:
        snapshot = dict(snapshot)
        snapshot["checkpoint_hours"] = -1
    flavors = parse_flavor_packages(ROOT / "buildSrc/src/main/kotlin/FlavorConfig.kt")
    result = evaluate_snapshot(policy, snapshot, flavors)

    output_dir = (ROOT / args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "decision.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (output_dir / "decision.md").write_text(report_markdown(result), encoding="utf-8")
    print(
        "Release health decision: %s flavor=%s versionCode=%s checkpoint=%sh"
        % (
            result["decision"].upper(),
            result.get("flavor"),
            result.get("version_code"),
            result.get("checkpoint_hours"),
        )
    )
    for breach in result["breaches"]:
        print(
            "  - %s=%s -> %s (threshold=%s)"
            % (breach["metric"], breach["value"], breach["level"], breach["threshold"])
        )
    for error in result["errors"]:
        print("  - INCOMPLETE: %s" % error)

    if result["decision"] == "incomplete":
        return 2
    return 1 if DECISION_RANK[result["decision"]] >= DECISION_RANK[args.fail_on] else 0


if __name__ == "__main__":
    raise SystemExit(main())
