#!/usr/bin/env python3
"""Read-only existence audit for matured GA4 BigQuery daily event tables.

Never runs a BigQuery query, enables billing, modifies retention, or writes data.
The optional --as-of-date is the GA4 property-local calendar date, for repeatability.
"""

from __future__ import annotations

import argparse
from datetime import date, timedelta
import json
import re
import subprocess
import sys
from typing import Any

IDENTIFIER = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]*$")


def expected_tables(as_of: date, *, days: int = 3, lag_days: int = 2) -> list[str]:
    """Skip recently completed days to avoid flagging legitimate export latency."""
    if days < 1 or lag_days < 1:
        raise ValueError("days and lag_days must be positive")
    return [
        "events_" + (as_of - timedelta(days=lag_days + offset)).strftime("%Y%m%d")
        for offset in reversed(range(days))
    ]


def parse_listing(payload: str, *, project: str, dataset: str) -> set[str]:
    parsed: Any = json.loads(payload)
    if not isinstance(parsed, list):
        raise ValueError("bq ls output must be a JSON array")
    if len(parsed) >= 1000:
        raise ValueError("bq ls returned 1000 tables; listing could be truncated")
    result: set[str] = set()
    for row in parsed:
        if not isinstance(row, dict) or not isinstance(row.get("tableReference"), dict):
            raise ValueError("unexpected table-list entry")
        ref = row["tableReference"]
        if ref.get("projectId") != project or ref.get("datasetId") != dataset:
            raise ValueError("bq ls returned a table from an unexpected project/dataset")
        table_id = ref.get("tableId")
        if not isinstance(table_id, str):
            raise ValueError("bq ls returned an entry without tableId")
        result.add(table_id)
    return result


def audit(*, project: str, dataset: str, as_of: date, days: int, lag_days: int) -> dict[str, Any]:
    if not IDENTIFIER.fullmatch(project) or not IDENTIFIER.fullmatch(dataset):
        raise ValueError("invalid project or dataset identifier")
    table_names = expected_tables(as_of, days=days, lag_days=lag_days)
    command = ["bq", "ls", "-n", "1000", "--format=json", f"{project}:{dataset}"]
    result = subprocess.run(command, capture_output=True, text=True, timeout=35, check=False)
    if result.returncode:
        raise RuntimeError(f"bq ls failed with exit code {result.returncode}: {result.stderr[:500]}")
    available = parse_listing(result.stdout, project=project, dataset=dataset)
    missing = [name for name in table_names if name not in available]
    return {
        "status": "pass" if not missing else "missing_daily_tables",
        "project": project,
        "dataset": dataset,
        "as_of_date": as_of.isoformat(),
        "lag_days": lag_days,
        "expected_tables": table_names,
        "missing_tables": missing,
        "daily_table_count": sum(name.startswith("events_") and name[7:].isdigit() for name in available),
        "metadata_only": True,
        "row_and_stream_contents_verified": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", default="makerpars-oaslananka-mobil")
    parser.add_argument("--dataset", default="analytics_526790653")
    parser.add_argument("--as-of-date", type=date.fromisoformat, default=date.today())
    parser.add_argument("--days", type=int, default=3)
    parser.add_argument("--lag-days", type=int, default=2)
    args = parser.parse_args()
    try:
        report = audit(
            project=args.project,
            dataset=args.dataset,
            as_of=args.as_of_date,
            days=args.days,
            lag_days=args.lag_days,
        )
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired, json.JSONDecodeError) as exc:
        print(json.dumps({"status": "error", "message": str(exc)}, sort_keys=True), file=sys.stdout)
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["status"] == "pass" else 2


if __name__ == "__main__":
    raise SystemExit(main())
