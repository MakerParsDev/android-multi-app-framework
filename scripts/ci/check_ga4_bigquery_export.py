#!/usr/bin/env python3
"""Read-only metadata verification of matured GA4 BigQuery daily export tables."""

from __future__ import annotations

import argparse
from datetime import date, timedelta
import json
import re
import subprocess
import sys
from typing import Any

IDENTIFIER = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]*$")
TABLE_NOT_FOUND = re.compile(r"\bNot found: Table\b", re.IGNORECASE)


class AuditArgumentParser(argparse.ArgumentParser):
    """Keep CLI syntax errors separate from missing export-table status 2."""

    def error(self, message: str) -> None:
        raise ValueError("invalid CLI arguments: " + message)


def expected_tables(as_of: date, *, days: int = 3, lag_days: int = 2) -> list[str]:
    """Ignore recently completed dates, when exports may still be arriving."""
    if days < 1 or lag_days < 1:
        raise ValueError("days and lag_days must be positive")
    return [
        "events_" + (as_of - timedelta(days=lag_days + offset)).strftime("%Y%m%d")
        for offset in reversed(range(days))
    ]


def inspect_table(*, project: str, dataset: str, name: str) -> str:
    """Return present, missing, or wrong_type; other failures raise."""
    command = ["bq", "show", "--format=json", f"{project}:{dataset}.{name}"]
    result = subprocess.run(command, capture_output=True, text=True, timeout=35, check=False)
    if result.returncode:
        error = (result.stderr + "\n" + result.stdout).strip()
        if TABLE_NOT_FOUND.search(error):
            return "missing"
        raise RuntimeError(f"bq show failed (exit {result.returncode}): {error[:500]}")
    response: Any = json.loads(result.stdout)
    if not isinstance(response, dict) or not isinstance(response.get("tableReference"), dict):
        raise ValueError(f"bq show returned unexpected metadata for {name}")
    ref = response["tableReference"]
    if (
        ref.get("projectId") != project
        or ref.get("datasetId") != dataset
        or ref.get("tableId") != name
    ):
        raise ValueError(f"bq show returned unexpected project/dataset/table for {name}")
    return "present" if response.get("type") == "TABLE" else "wrong_type"


def audit(*, project: str, dataset: str, as_of: date, days: int, lag_days: int) -> dict[str, Any]:
    if not IDENTIFIER.fullmatch(project) or not IDENTIFIER.fullmatch(dataset):
        raise ValueError("invalid project or dataset identifier")
    expected = expected_tables(as_of, days=days, lag_days=lag_days)
    states = {name: inspect_table(project=project, dataset=dataset, name=name) for name in expected}
    missing = [name for name, state in states.items() if state == "missing"]
    wrong_type = [name for name, state in states.items() if state == "wrong_type"]
    return {
        "status": "pass" if not missing and not wrong_type else "missing_or_invalid_daily_tables",
        "project": project,
        "dataset": dataset,
        "as_of_date": as_of.isoformat(),
        "lag_days": lag_days,
        "expected_tables": expected,
        "missing_tables": missing,
        "wrong_type_tables": wrong_type,
        "verified_daily_table_count": sum(state == "present" for state in states.values()),
        "metadata_only": True,
        "row_and_stream_contents_verified": False,
    }


def main() -> int:
    parser = AuditArgumentParser(description=__doc__)
    parser.add_argument("--project", default="makerpars-oaslananka-mobil")
    parser.add_argument("--dataset", default="analytics_526790653")
    parser.add_argument("--as-of-date", help="REQUIRED: GA4 property-local date YYYY-MM-DD")
    parser.add_argument("--days", type=int, default=3)
    parser.add_argument("--lag-days", type=int, default=2)
    try:
        args = parser.parse_args()
        if args.as_of_date is None:
            raise ValueError("--as-of-date is required (GA4 property-local calendar date)")
        as_of = date.fromisoformat(args.as_of_date)
        report = audit(
            project=args.project, dataset=args.dataset, as_of=as_of,
            days=args.days, lag_days=args.lag_days,
        )
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as exc:
        print(json.dumps({"status": "error", "message": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["status"] == "pass" else 2


if __name__ == "__main__":
    raise SystemExit(main())
