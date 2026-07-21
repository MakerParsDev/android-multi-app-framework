#!/usr/bin/env python3
"""Pure weekly AdMob trend analysis and bounded experiment recommendations."""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from typing import Any, Iterable, Mapping, Sequence

METRIC_KEYS = (
    "ad_requests",
    "matched_requests",
    "impressions",
    "clicks",
    "earnings_micros",
)


@dataclass(frozen=True)
class Thresholds:
    minimum_requests: int = 100
    minimum_matched_requests: int = 100
    low_match_rate: float = 0.50
    low_show_rate: float = 0.15
    severe_show_rate: float = 0.05
    decline_pct: float = -20.0


def safe_rate(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator > 0 else 0.0


def change_pct(current: float, previous: float) -> float | None:
    if previous == 0:
        return None if current == 0 else 100.0
    return (current / previous - 1.0) * 100.0


def normalize_row(row: Mapping[str, Any]) -> dict[str, Any]:
    normalized = {key: max(0, int(row.get(key, 0) or 0)) for key in METRIC_KEYS}
    for key in ("flavor", "app", "app_id", "format", "platform", "country", "app_version", "ad_unit"):
        normalized[key] = str(row.get(key, "unknown") or "unknown")
    return normalized


def aggregate_rows(
    rows: Iterable[Mapping[str, Any]],
    dimensions: Sequence[str],
) -> list[dict[str, Any]]:
    grouped: dict[tuple[str, ...], dict[str, Any]] = defaultdict(
        lambda: {key: 0 for key in METRIC_KEYS}
    )
    for raw in rows:
        row = normalize_row(raw)
        key = tuple(row[dimension] for dimension in dimensions)
        bucket = grouped[key]
        for metric in METRIC_KEYS:
            bucket[metric] += row[metric]

    output: list[dict[str, Any]] = []
    for key, metrics in grouped.items():
        item = {dimension: value for dimension, value in zip(dimensions, key)}
        item.update(enrich_metrics(metrics))
        output.append(item)
    output.sort(
        key=lambda item: (
            -int(item["earnings_micros"]),
            -int(item["impressions"]),
            tuple(str(item[dimension]) for dimension in dimensions),
        )
    )
    return output


def enrich_metrics(metrics: Mapping[str, Any]) -> dict[str, Any]:
    requests = max(0, int(metrics.get("ad_requests", 0) or 0))
    matched = max(0, int(metrics.get("matched_requests", 0) or 0))
    impressions = max(0, int(metrics.get("impressions", 0) or 0))
    clicks = max(0, int(metrics.get("clicks", 0) or 0))
    earnings_micros = max(0, int(metrics.get("earnings_micros", 0) or 0))
    return {
        "ad_requests": requests,
        "matched_requests": matched,
        "impressions": impressions,
        "clicks": clicks,
        "earnings_micros": earnings_micros,
        "earnings_try": round(earnings_micros / 1_000_000.0, 6),
        "match_rate": round(safe_rate(matched, requests), 6),
        "show_rate": round(safe_rate(impressions, matched), 6),
        "ctr": round(safe_rate(clicks, impressions), 6),
        "impression_rpm_try": round(
            earnings_micros / 1_000_000.0 * 1000.0 / impressions,
            6,
        ) if impressions else 0.0,
    }


def compare_aggregates(
    current: Sequence[Mapping[str, Any]],
    previous: Sequence[Mapping[str, Any]],
    dimensions: Sequence[str],
) -> list[dict[str, Any]]:
    def key_of(item: Mapping[str, Any]) -> tuple[str, ...]:
        return tuple(str(item.get(dimension, "unknown")) for dimension in dimensions)

    current_by_key = {key_of(item): item for item in current}
    previous_by_key = {key_of(item): item for item in previous}
    rows: list[dict[str, Any]] = []
    for key in sorted(set(current_by_key) | set(previous_by_key)):
        current_item = current_by_key.get(key, enrich_metrics({}))
        previous_item = previous_by_key.get(key, enrich_metrics({}))
        row = {dimension: value for dimension, value in zip(dimensions, key)}
        row["current"] = dict(current_item)
        row["previous"] = dict(previous_item)
        row["changes_pct"] = {
            "ad_requests": nullable_round(change_pct(
                float(current_item["ad_requests"]),
                float(previous_item["ad_requests"]),
            )),
            "impressions": nullable_round(change_pct(
                float(current_item["impressions"]),
                float(previous_item["impressions"]),
            )),
            "earnings_try": nullable_round(change_pct(
                float(current_item["earnings_try"]),
                float(previous_item["earnings_try"]),
            )),
            "match_rate": nullable_round(change_pct(
                float(current_item["match_rate"]),
                float(previous_item["match_rate"]),
            )),
            "show_rate": nullable_round(change_pct(
                float(current_item["show_rate"]),
                float(previous_item["show_rate"]),
            )),
            "ctr": nullable_round(change_pct(
                float(current_item["ctr"]),
                float(previous_item["ctr"]),
            )),
            "impression_rpm_try": nullable_round(change_pct(
                float(current_item["impression_rpm_try"]),
                float(previous_item["impression_rpm_try"]),
            )),
        }
        rows.append(row)
    rows.sort(
        key=lambda item: (
            -float(item["current"]["earnings_try"]),
            -int(item["current"]["matched_requests"]),
            key_of(item),
        )
    )
    return rows


def nullable_round(value: float | None) -> float | None:
    return round(value, 2) if value is not None else None


def detect_opportunities(
    comparisons: Sequence[Mapping[str, Any]],
    dimensions: Sequence[str],
    thresholds: Thresholds = Thresholds(),
) -> list[dict[str, Any]]:
    opportunities: list[dict[str, Any]] = []
    for item in comparisons:
        current = item["current"]
        identity = {dimension: item[dimension] for dimension in dimensions}
        reasons: list[str] = []
        if (
            int(current["ad_requests"]) >= thresholds.minimum_requests
            and float(current["match_rate"]) < thresholds.low_match_rate
        ):
            reasons.append("low_match_rate")
        if (
            int(current["matched_requests"]) >= thresholds.minimum_matched_requests
            and int(current["impressions"]) == 0
        ):
            reasons.append("zero_impressions")
        elif (
            int(current["matched_requests"]) >= thresholds.minimum_matched_requests
            and float(current["show_rate"]) < thresholds.low_show_rate
        ):
            reasons.append("low_show_rate")
        show_delta = item["changes_pct"].get("show_rate")
        if show_delta is not None and show_delta <= thresholds.decline_pct:
            reasons.append("show_rate_decline")
        if reasons:
            opportunities.append({
                **identity,
                "reasons": reasons,
                "current": dict(current),
                "changes_pct": dict(item["changes_pct"]),
            })
    opportunities.sort(
        key=lambda item: (
            "zero_impressions" not in item["reasons"],
            float(item["current"]["show_rate"]),
            -int(item["current"]["matched_requests"]),
        )
    )
    return opportunities


def build_hypotheses(
    format_comparisons: Sequence[Mapping[str, Any]],
    app_format_opportunities: Sequence[Mapping[str, Any]],
    country_opportunities: Sequence[Mapping[str, Any]],
) -> list[dict[str, Any]]:
    by_format = {str(item["format"]): item for item in format_comparisons}
    hypotheses: list[dict[str, Any]] = []

    native = by_format.get("native")
    if native and float(native["current"]["show_rate"]) < 0.20:
        hypotheses.append({
            "id": "native_visibility_recovery",
            "evidence": "Native matched-to-impression conversion is below 20%.",
            "change": "Audit only placements with >=100 matched requests and low visibility; fix composition/lifecycle attachment without increasing request frequency.",
            "remote_config_bounds": {
                "ads_native_banner_fallback_enabled": [False, True],
                "ads_native_pool_max": [1, 2],
                "frequency_increase_allowed": False,
            },
            "success": "Native show rate improves >=20% relative while CTR, crashes and session retention do not regress.",
            "rollback": "Disable native fallback and restore pool size when show rate, CTR quality or runtime health regresses.",
        })

    fullscreen = [
        by_format.get("app_open"),
        by_format.get("interstitial"),
    ]
    if any(item and float(item["current"]["show_rate"]) < 0.15 for item in fullscreen):
        hypotheses.append({
            "id": "fullscreen_readiness_recovery",
            "evidence": "High-match app-open/interstitial inventory converts to impressions below 15%.",
            "change": "Measure lifecycle/activity/not-loaded suppression and improve preload readiness; do not reduce cooldowns or raise session caps.",
            "remote_config_bounds": {
                "ads_interstitial_frequency_cap_ms_min": 90000,
                "ads_app_open_cooldown_ms_min": 120000,
                "session_cap_increase_allowed": False,
            },
            "success": "Show rate improves >=15% relative with unchanged caps and no ANR/crash/retention regression.",
            "rollback": "Use app/format emergency switches when suppression, UX or release-health signals worsen.",
        })

    rewarded = by_format.get("rewarded")
    if rewarded and int(rewarded["current"]["matched_requests"]) >= 100 and float(rewarded["current"]["show_rate"]) < 0.10:
        hypotheses.append({
            "id": "rewarded_opt_in_discovery",
            "evidence": "Rewarded inventory has high match but low opt-in impression conversion.",
            "change": "Test clearer user-initiated reward offers only on configured reward routes; never auto-show rewarded formats.",
            "remote_config_bounds": {
                "ads_rewarded_max_per_session_max": 10,
                "ads_reward_offer_routes_csv": "allowlist_only",
                "auto_show_allowed": False,
            },
            "success": "Rewarded impressions and completion rise without increasing skipped offers or complaints.",
            "rollback": "Remove experiment routes and retain current opt-in behavior.",
        })

    no_fill_countries = [
        item for item in country_opportunities
        if "low_match_rate" in item["reasons"] and int(item["current"]["ad_requests"]) >= 500
    ]
    if no_fill_countries:
        hypotheses.append({
            "id": "geo_no_fill_diagnosis",
            "evidence": "One or more countries have >=500 requests with sub-50% match rate.",
            "change": "Investigate demand/mediation and consent coverage by country; do not increase request volume in no-fill markets.",
            "remote_config_bounds": {
                "request_frequency_increase_allowed": False,
                "country_targeting_requires_review": True,
            },
            "success": "Match rate improves without higher request counts or policy exceptions.",
            "rollback": "Revert country-specific changes and keep global safe defaults.",
        })

    if app_format_opportunities:
        hypotheses.append({
            "id": "placement_canary_only",
            "evidence": "App/format outliers exist and portfolio-wide changes would hide placement-specific regressions.",
            "change": "Run one-package, one-placement canaries for seven days with explicit owner and previous template version.",
            "remote_config_bounds": {
                "max_apps_per_canary": 1,
                "max_placements_per_canary": 1,
                "minimum_observation_days": 7,
            },
            "success": "Canary meets show-rate/revenue target with stable UX and release health before expansion.",
            "rollback": "Restore the previous Remote Config template immediately on guardrail breach.",
        })
    return hypotheses[:5]


def build_weekly_analysis(
    current_rows: Sequence[Mapping[str, Any]],
    previous_rows: Sequence[Mapping[str, Any]],
    current_country_rows: Sequence[Mapping[str, Any]],
    previous_country_rows: Sequence[Mapping[str, Any]],
) -> dict[str, Any]:
    current_formats = aggregate_rows(current_rows, ("format",))
    previous_formats = aggregate_rows(previous_rows, ("format",))
    format_comparisons = compare_aggregates(
        current_formats,
        previous_formats,
        ("format",),
    )
    app_keys = ("flavor", "app", "format", "platform", "app_version", "ad_unit")
    app_format_comparisons = compare_aggregates(
        aggregate_rows(current_rows, app_keys),
        aggregate_rows(previous_rows, app_keys),
        app_keys,
    )
    country_comparisons = compare_aggregates(
        aggregate_rows(current_country_rows, ("country", "format")),
        aggregate_rows(previous_country_rows, ("country", "format")),
        ("country", "format"),
    )
    app_opportunities = detect_opportunities(app_format_comparisons, app_keys)
    country_opportunities = detect_opportunities(
        country_comparisons,
        ("country", "format"),
    )
    totals = compare_aggregates(
        aggregate_rows(current_rows, ()),
        aggregate_rows(previous_rows, ()),
        (),
    )[0]
    return {
        "totals": totals,
        "formats": format_comparisons,
        "app_formats": app_format_comparisons,
        "countries": country_comparisons,
        "app_format_opportunities": app_opportunities,
        "country_opportunities": country_opportunities,
        "hypotheses": build_hypotheses(
            format_comparisons,
            app_opportunities,
            country_opportunities,
        ),
    }
