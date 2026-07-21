#!/usr/bin/env python3
"""Generate a two-week AdMob optimization report with bounded experiments."""

from __future__ import annotations

import argparse
import json
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any, Mapping, Sequence

import requests
from admob_weekly_analysis import build_weekly_analysis
from admob_weekly_report import render_markdown


DEFAULT_OUTPUT = "TEMP_OUT/admob_weekly_optimization.json"
DEFAULT_MARKDOWN = "TEMP_OUT/admob_weekly_optimization.md"

def ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def normalize_account_name(value: str) -> str:
    cleaned = value.strip()
    if not cleaned:
        return ""
    return cleaned if cleaned.startswith("accounts/") else f"accounts/{cleaned}"



def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="AdMob completed-week trend and experiment report")
    parser.add_argument("--token-file", default="SECRET/ADMOB_KOTNROL/token.json")
    parser.add_argument("--publisher", default="")
    parser.add_argument(
        "--inventory-json",
        default="config/admob-inventory.json",
        help="AdMob inventory used to map app IDs to framework flavors",
    )
    parser.add_argument("--week-end", type=date.fromisoformat)
    parser.add_argument("--out-json", default=DEFAULT_OUTPUT)
    parser.add_argument("--out-markdown", default=DEFAULT_MARKDOWN)
    parser.add_argument(
        "--check-markdown",
        help="Fail when the generated Markdown differs from this source-controlled report",
    )
    parser.add_argument(
        "--generated-at",
        help="Override report generation timestamp; fixture mode otherwise uses evidence.captured_on",
    )
    parser.add_argument("--fixture-json", help="Offline fixture containing the four report row arrays")
    return parser.parse_args()


def completed_week_end(today: date) -> date:
    days_since_sunday = (today.weekday() + 1) % 7
    last_sunday = today - timedelta(days=days_since_sunday)
    return last_sunday if last_sunday < today else last_sunday - timedelta(days=7)


def report_windows(week_end: date) -> dict[str, tuple[date, date]]:
    current_start = week_end - timedelta(days=6)
    previous_end = current_start - timedelta(days=1)
    return {
        "current": (current_start, week_end),
        "previous": (previous_end - timedelta(days=6), previous_end),
    }


def admob_date(value: date) -> dict[str, int]:
    return {"year": value.year, "month": value.month, "day": value.day}


def metric_int(metrics: Mapping[str, Any], key: str) -> int:
    value = metrics.get(key, {}) or {}
    raw = value.get("integerValue") or value.get("microsValue") or 0
    return int(raw)


def dimension_value(dimensions: Mapping[str, Any], key: str) -> str:
    value = dimensions.get(key, {}) or {}
    return str(value.get("displayLabel") or value.get("value") or "unknown")


def parse_report_payload(payload: Any) -> list[dict[str, Any]]:
    if not isinstance(payload, list):
        raise ValueError("AdMob report payload must be a streamed JSON array")
    rows: list[dict[str, Any]] = []
    for item in payload:
        if not isinstance(item, Mapping) or "row" not in item:
            continue
        row = item.get("row") or {}
        dimensions = row.get("dimensionValues", {}) or {}
        metrics = row.get("metricValues", {}) or {}
        rows.append({
            "app": dimension_value(dimensions, "APP"),
            "app_id": str((dimensions.get("APP", {}) or {}).get("value") or "unknown"),
            "format": dimension_value(dimensions, "FORMAT").lower(),
            "platform": dimension_value(dimensions, "PLATFORM"),
            "country": dimension_value(dimensions, "COUNTRY"),
            "app_version": dimension_value(dimensions, "APP_VERSION_NAME"),
            "ad_unit": dimension_value(dimensions, "AD_UNIT"),
            "ad_requests": metric_int(metrics, "AD_REQUESTS"),
            "matched_requests": metric_int(metrics, "MATCHED_REQUESTS"),
            "impressions": metric_int(metrics, "IMPRESSIONS"),
            "clicks": metric_int(metrics, "CLICKS"),
            "earnings_micros": metric_int(metrics, "ESTIMATED_EARNINGS"),
        })
    return rows


def fetch_account_name(headers: Mapping[str, str], preferred: str) -> str:
    response = requests.get("https://admob.googleapis.com/v1/accounts", headers=headers, timeout=30)
    response.raise_for_status()
    names = [str(item.get("name")) for item in response.json().get("account", []) if item.get("name")]
    if not names:
        raise RuntimeError("No accessible AdMob account")
    normalized = normalize_account_name(preferred)
    if normalized:
        if normalized not in names:
            raise RuntimeError(f"Requested account is not accessible: {normalized}")
        return normalized
    return names[0]


def fetch_report(
    account_name: str,
    headers: Mapping[str, str],
    start: date,
    end: date,
    dimensions: Sequence[str],
) -> list[dict[str, Any]]:
    body = {
        "reportSpec": {
            "dateRange": {"startDate": admob_date(start), "endDate": admob_date(end)},
            "dimensions": list(dimensions),
            "metrics": [
                "AD_REQUESTS",
                "MATCHED_REQUESTS",
                "IMPRESSIONS",
                "CLICKS",
                "ESTIMATED_EARNINGS",
            ],
        }
    }
    response = requests.post(
        f"https://admob.googleapis.com/v1/{account_name}/networkReport:generate",
        headers=headers,
        json=body,
        timeout=180,
    )

    if response.status_code >= 400:
        raise RuntimeError(f"AdMob report failed: HTTP {response.status_code} {response.text[:1000]}")
    return parse_report_payload(response.json())


def fetch_live_rows(token_file: Path, publisher: str, windows: Mapping[str, tuple[date, date]]) -> dict[str, Any]:
    from check_admob_today_latest import refresh_credentials_with_fallback
    token_info = json.loads(token_file.read_text(encoding="utf-8"))
    credentials = refresh_credentials_with_fallback(token_info)
    headers = {"Authorization": f"Bearer {credentials.token}", "Content-Type": "application/json"}
    account_name = fetch_account_name(headers, publisher)
    output: dict[str, Any] = {"account_name": account_name}
    for label, (start, end) in windows.items():
        output[f"{label}_rows"] = fetch_report(
            account_name,
            headers,
            start,
            end,
            ("APP", "FORMAT", "PLATFORM", "APP_VERSION_NAME", "AD_UNIT"),
        )
        output[f"{label}_country_rows"] = fetch_report(
            account_name,
            headers,
            start,
            end,
            ("COUNTRY", "FORMAT"),
        )
    return output


def load_flavor_by_app_id(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    apps = payload.get("approved_framework_apps", []) if isinstance(payload, Mapping) else []
    result: dict[str, str] = {}
    for item in apps if isinstance(apps, list) else []:
        if not isinstance(item, Mapping):
            continue
        app_id = str(item.get("admob_app_id") or "").strip()
        flavor = str(item.get("flavor") or "").strip()
        if app_id and flavor:
            result[app_id] = flavor
    return result


def enrich_app_rows(
    rows: Sequence[Mapping[str, Any]],
    flavor_by_app_id: Mapping[str, str],
) -> list[dict[str, Any]]:
    enriched: list[dict[str, Any]] = []
    for source_row in rows:
        row = dict(source_row)
        app_id = str(row.get("app_id") or "")
        row["flavor"] = str(row.get("flavor") or flavor_by_app_id.get(app_id) or "unmapped")
        row["platform"] = str(row.get("platform") or "ANDROID")
        enriched.append(row)
    return enriched


def resolve_generated_at(
    explicit_value: str | None,
    source: Mapping[str, Any],
    fixture_mode: bool,
    week_end: date,
) -> str:
    if explicit_value:
        return explicit_value
    if fixture_mode:
        evidence = source.get("evidence", {})
        captured_on = evidence.get("captured_on") if isinstance(evidence, Mapping) else None
        deterministic_date = str(captured_on or week_end.isoformat())
        return f"{deterministic_date}T00:00:00+03:00"
    return datetime.now().astimezone().replace(microsecond=0).isoformat()


def main() -> int:
    args = parse_args()
    week_end = args.week_end or completed_week_end(date.today())
    windows = report_windows(week_end)
    if args.fixture_json:
        source = json.loads(Path(args.fixture_json).read_text(encoding="utf-8"))
        account_name = source.get("account_name", "fixture")
    else:
        token_path = Path(args.token_file)
        if not token_path.exists():
            raise SystemExit(f"Token file not found: {token_path}")
        source = fetch_live_rows(token_path, args.publisher, windows)
        account_name = source["account_name"]

    flavor_by_app_id = load_flavor_by_app_id(Path(args.inventory_json))
    current_rows = enrich_app_rows(source.get("current_rows", []), flavor_by_app_id)
    previous_rows = enrich_app_rows(source.get("previous_rows", []), flavor_by_app_id)
    analysis = build_weekly_analysis(
        current_rows,
        previous_rows,
        source.get("current_country_rows", []),
        source.get("previous_country_rows", []),
    )
    payload = {
        "generated_at": resolve_generated_at(
            args.generated_at,
            source,
            bool(args.fixture_json),
            week_end,
        ),
        "source": "fixture" if args.fixture_json else "admob_api",
        "account_name": account_name,
        "windows": {
            key: {"start": value[0].isoformat(), "end": value[1].isoformat()}
            for key, value in windows.items()
        },
        "analysis": analysis,
    }
    out_json = Path(args.out_json)
    out_markdown = Path(args.out_markdown)
    ensure_parent(out_json)
    ensure_parent(out_markdown)
    markdown = render_markdown(payload)
    out_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    out_markdown.write_text(markdown, encoding="utf-8")
    if args.check_markdown:
        check_path = Path(args.check_markdown)
        if not check_path.exists():
            raise SystemExit(f"Source-controlled AdMob report is missing: {check_path}")
        if check_path.read_text(encoding="utf-8") != markdown:
            raise SystemExit(
                "Source-controlled AdMob report is stale: regenerate "
                f"{check_path} from the approved weekly fixture"
            )
    print(
        "AdMob weekly optimization report generated: "
        f"earnings={analysis['totals']['current']['earnings_try']:.2f} TRY, "
        f"opportunities={len(analysis['app_format_opportunities'])}, "
        f"hypotheses={len(analysis['hypotheses'])}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
