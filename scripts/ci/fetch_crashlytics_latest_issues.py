#!/usr/bin/env python3
"""
Fetch Crashlytics issues for the latest app build version per flavor via BigQuery export.

Notes:
- Crashlytics "open/closed" state is not available in BigQuery export.
- This script reports issues observed in the latest build_version for each app table.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import sys
import time
from dataclasses import dataclass
from typing import Any

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError


FLAVOR_PATTERN = re.compile(
    r'FlavorConfig\(\s*"(?P<name>[^"]+)"\s*,\s*"[^"]*"\s*,\s*"(?P<pkg>[^"]+)"'
)


@dataclass(frozen=True)
class FlavorInfo:
    name: str
    package_name: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch latest-build Crashlytics issues for all/specified flavors"
    )
    parser.add_argument(
        "--flavors",
        default="all",
        help="Comma-separated flavor names or 'all'",
    )
    parser.add_argument(
        "--service-account",
        default="SECRET/makerpars-oaslananka-mobil-FullAdminServiceAccount.json",
        help="Path to service account json",
    )
    parser.add_argument(
        "--project-id",
        default="",
        help="BigQuery project id (optional, defaults to service account project_id)",
    )
    parser.add_argument(
        "--dataset",
        default="firebase_crashlytics",
        help="Primary dataset name (default: firebase_crashlytics)",
    )
    parser.add_argument(
        "--fallback-datasets",
        default="crashlytics",
        help="Comma-separated fallback datasets to try if table isn't found",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=50,
        help="Max issues to return per flavor (default: 50)",
    )
    parser.add_argument(
        "--output",
        default="build/reports/crashlytics/latest-build-issues.json",
        help="Output JSON path",
    )
    return parser.parse_args()


def read_flavors(flavor_file: pathlib.Path) -> dict[str, FlavorInfo]:
    text = flavor_file.read_text(encoding="utf-8")
    result: dict[str, FlavorInfo] = {}
    for match in FLAVOR_PATTERN.finditer(text):
        name = match.group("name").strip()
        pkg = match.group("pkg").strip()
        result[name] = FlavorInfo(name=name, package_name=pkg)
    if not result:
        raise RuntimeError(f"No flavors parsed from {flavor_file}")
    return result


def build_bigquery_service(service_account_path: pathlib.Path):
    if not service_account_path.exists():
        raise RuntimeError(f"Service account file not found: {service_account_path}")
    info = json.loads(service_account_path.read_text(encoding="utf-8"))
    creds = service_account.Credentials.from_service_account_info(
        info,
        scopes=["https://www.googleapis.com/auth/bigquery.readonly"],
    )
    service = build("bigquery", "v2", credentials=creds, cache_discovery=False)
    return service, str(info.get("project_id") or "").strip()


def list_tables(service, project_id: str, dataset_id: str) -> list[str]:
    table_ids: list[str] = []
    page_token = None
    while True:
        resp = service.tables().list(
            projectId=project_id,
            datasetId=dataset_id,
            maxResults=1000,
            pageToken=page_token,
        ).execute()
        for item in resp.get("tables", []) or []:
            ref = item.get("tableReference") or {}
            table_id = str(ref.get("tableId") or "").strip()
            if table_id:
                table_ids.append(table_id)
        page_token = resp.get("nextPageToken")
        if not page_token:
            break
    return table_ids


def resolve_table_id(package_name: str, table_ids: list[str]) -> str | None:
    norm = package_name.replace(".", "_")
    expected = f"{norm}_ANDROID"

    # 1) exact match
    for t in table_ids:
        if t == expected:
            return t

    # 2) case-insensitive match
    lower_expected = expected.lower()
    for t in table_ids:
        if t.lower() == lower_expected:
            return t

    # 3) likely legacy/fuzzy
    needle = norm.lower()
    candidates = [
        t
        for t in table_ids
        if needle in t.lower() and (t.lower().endswith("_android") or "android" in t.lower())
    ]
    if candidates:
        return sorted(candidates)[0]

    return None


def bq_query(service, project_id: str, query: str, params: dict[str, Any] | None = None) -> list[dict[str, Any]]:
    body: dict[str, Any] = {
        "query": query,
        "useLegacySql": False,
        "timeoutMs": 120000,
    }
    if params:
        body["parameterMode"] = "NAMED"
        body["queryParameters"] = [
            {
                "name": key,
                "parameterType": {"type": "STRING"},
                "parameterValue": {"value": str(value)},
            }
            for key, value in params.items()
        ]

    resp = service.jobs().query(projectId=project_id, body=body).execute()

    job_ref = resp.get("jobReference") or {}
    job_id = str(job_ref.get("jobId") or "").strip()
    location = str(job_ref.get("location") or "").strip() or None

    while not resp.get("jobComplete", True):
        if not job_id:
            raise RuntimeError("BigQuery job is not complete and no jobId returned")
        time.sleep(1)
        resp = service.jobs().getQueryResults(
            projectId=project_id,
            jobId=job_id,
            location=location,
            timeoutMs=120000,
        ).execute()

    schema_fields = (resp.get("schema") or {}).get("fields") or []

    rows: list[dict[str, Any]] = []

    def append_rows(payload: dict[str, Any]) -> None:
        payload_rows = payload.get("rows") or []
        for row in payload_rows:
            values = row.get("f") or []
            item: dict[str, Any] = {}
            for i, field in enumerate(schema_fields):
                name = str(field.get("name") or f"col_{i}")
                v = values[i].get("v") if i < len(values) else None
                item[name] = v
            rows.append(item)

    append_rows(resp)

    page_token = resp.get("pageToken")
    while page_token:
        if not job_id:
            raise RuntimeError("Missing jobId while paging query results")
        page = service.jobs().getQueryResults(
            projectId=project_id,
            jobId=job_id,
            location=location,
            pageToken=page_token,
            timeoutMs=120000,
        ).execute()
        append_rows(page)
        page_token = page.get("pageToken")

    return rows


def main() -> int:
    args = parse_args()
    repo_root = pathlib.Path(__file__).resolve().parents[2]

    flavor_file = repo_root / "buildSrc" / "src" / "main" / "kotlin" / "FlavorConfig.kt"
    all_flavors = read_flavors(flavor_file)

    requested = [x.strip() for x in args.flavors.split(",") if x.strip()]
    if not requested or requested == ["all"]:
        selected = list(all_flavors.values())
    else:
        unknown = [x for x in requested if x not in all_flavors]
        if unknown:
            print(f"ERROR: Unknown flavor(s): {', '.join(unknown)}")
            return 1
        selected = [all_flavors[x] for x in requested]

    service_account_path = (repo_root / args.service_account).resolve()
    service, default_project = build_bigquery_service(service_account_path)

    project_id = (args.project_id or default_project).strip()
    if not project_id:
        print("ERROR: project id is missing. Provide --project-id or service account with project_id")
        return 1

    datasets = [args.dataset.strip()]
    datasets.extend([d.strip() for d in args.fallback_datasets.split(",") if d.strip() and d.strip() != args.dataset.strip()])

    tables_cache: dict[str, list[str]] = {}

    results: list[dict[str, Any]] = []
    resolved_count = 0

    for flavor in selected:
        item: dict[str, Any] = {
            "flavor": flavor.name,
            "packageName": flavor.package_name,
            "dataset": None,
            "table": None,
            "latestBuildVersion": None,
            "latestDisplayVersion": None,
            "issues": [],
            "error": None,
        }
        try:
            table = None
            used_dataset = None

            for ds in datasets:
                if ds not in tables_cache:
                    try:
                        tables_cache[ds] = list_tables(service, project_id, ds)
                    except HttpError as exc:
                        if exc.resp is not None and exc.resp.status == 404:
                            tables_cache[ds] = []
                        else:
                            raise
                table = resolve_table_id(flavor.package_name, tables_cache[ds])
                if table:
                    used_dataset = ds
                    break

            if not table or not used_dataset:
                item["error"] = "Crashlytics table not found"
                results.append(item)
                continue

            item["dataset"] = used_dataset
            item["table"] = table
            resolved_count += 1

            table_ref = f"`{project_id}.{used_dataset}.{table}`"

            latest_query = f"""
                SELECT
                  application.build_version AS build_version,
                  application.display_version AS display_version,
                  MAX(event_timestamp) AS last_seen
                FROM {table_ref}
                GROUP BY build_version, display_version
                ORDER BY SAFE_CAST(build_version AS INT64) DESC, last_seen DESC
                LIMIT 1
            """
            latest_rows = bq_query(service, project_id, latest_query)
            if not latest_rows:
                item["error"] = "No events in table"
                results.append(item)
                continue

            latest = latest_rows[0]
            build_version = latest.get("build_version")
            display_version = latest.get("display_version")
            item["latestBuildVersion"] = build_version
            item["latestDisplayVersion"] = display_version

            if not build_version:
                item["error"] = "Latest row has empty build_version"
                results.append(item)
                continue

            issues_query = f"""
                SELECT
                  issue_id,
                  COUNT(DISTINCT event_id) AS event_count,
                  COUNTIF(is_fatal) AS fatal_count,
                  COUNTIF(NOT is_fatal) AS nonfatal_count,
                  MAX(event_timestamp) AS last_seen
                FROM {table_ref}
                WHERE application.build_version = @buildVersion
                GROUP BY issue_id
                ORDER BY event_count DESC
                LIMIT {max(1, args.limit)}
            """

            issue_rows = bq_query(service, project_id, issues_query, params={"buildVersion": build_version})
            parsed_issues = []
            for r in issue_rows:
                parsed_issues.append(
                    {
                        "issueId": r.get("issue_id"),
                        "eventCount": int(r.get("event_count") or 0),
                        "fatalCount": int(r.get("fatal_count") or 0),
                        "nonfatalCount": int(r.get("nonfatal_count") or 0),
                        "lastSeen": r.get("last_seen"),
                    }
                )
            item["issues"] = parsed_issues
            results.append(item)

        except Exception as exc:  # noqa: BLE001
            item["error"] = str(exc)
            results.append(item)

    payload = {
        "generatedAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "projectId": project_id,
        "datasetsTried": datasets,
        "flavorCount": len(selected),
        "resolvedTableCount": resolved_count,
        "results": results,
        "note": "Crashlytics open/closed issue state is not in BigQuery export; results are issues observed in latest build_version.",
    }

    output_path = (repo_root / args.output).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Wrote report: {output_path}")
    print(f"Flavor count: {len(selected)}, resolved tables: {resolved_count}")

    # Console summary
    for item in results:
        flavor = item["flavor"]
        pkg = item["packageName"]
        err = item.get("error")
        if err:
            print(f"- {flavor} ({pkg}): ERROR - {err}")
            continue
        build_ver = item.get("latestBuildVersion") or "?"
        disp_ver = item.get("latestDisplayVersion") or "?"
        issues = item.get("issues") or []
        print(f"- {flavor} ({pkg}): build={build_ver} ({disp_ver}), issues={len(issues)}")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130)
