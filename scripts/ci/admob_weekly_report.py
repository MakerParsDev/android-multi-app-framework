#!/usr/bin/env python3
"""Markdown rendering for weekly AdMob optimization reports."""

from __future__ import annotations

import json
from typing import Any, Mapping


def render_markdown(payload: Mapping[str, Any]) -> str:
    analysis = payload["analysis"]
    windows = payload["windows"]
    total = analysis["totals"]
    lines = [
        "# AdMob Weekly Optimization",
        "",
        f"Generated: `{payload['generated_at']}`",
        f"Current week: **{windows['current']['start']} – {windows['current']['end']}**",
        f"Previous week: **{windows['previous']['start']} – {windows['previous']['end']}**",
        "",
        "## Portfolio trend",
        "",
        "| Metric | Current | Previous | Change |",
        "|---|---:|---:|---:|",
        f"| Earnings (TRY) | {total['current']['earnings_try']:.2f} | {total['previous']['earnings_try']:.2f} | {format_change(total['changes_pct']['earnings_try'])} |",
        f"| Requests | {total['current']['ad_requests']} | {total['previous']['ad_requests']} | {format_change(total['changes_pct']['ad_requests'])} |",
        f"| Impressions | {total['current']['impressions']} | {total['previous']['impressions']} | {format_change(total['changes_pct']['impressions'])} |",
        f"| CTR | {total['current']['ctr']:.1%} | {total['previous']['ctr']:.1%} | {format_change(total['changes_pct']['ctr'])} |",
        f"| Match rate | {total['current']['match_rate']:.1%} | {total['previous']['match_rate']:.1%} | {format_change(total['changes_pct']['match_rate'])} |",
        f"| Show rate | {total['current']['show_rate']:.1%} | {total['previous']['show_rate']:.1%} | {format_change(total['changes_pct']['show_rate'])} |",
        "",
        "## Format trend",
        "",
        "| Format | Requests | Match | Show | CTR | Earnings TRY | WoW earnings | eCPM TRY |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for item in analysis["formats"]:
        current = item["current"]
        lines.append(
            f"| {item['format']} | {current['ad_requests']} | {current['match_rate']:.1%} | "
            f"{current['show_rate']:.1%} | {current['ctr']:.1%} | {current['earnings_try']:.2f} | "
            f"{format_change(item['changes_pct']['earnings_try'])} | {current['impression_rpm_try']:.2f} |"
        )
    lines.extend([
        "",
        "## Flavor, version and ad-unit trend",
        "",
        "AdMob's network report exposes the ad unit rather than the in-app placement alias. "
        "The scheduled live pipeline maps AdMob app IDs to framework flavors and queries app version/platform directly. "
        "The source-controlled bootstrap fixture is intentionally portfolio-aggregated, so those rows are marked "
        "`unmapped`/`mixed`. The admin runtime report exposes exact placement conversion in "
        "`runtimeFunnelByPlacement` after rollout.",
        "",
        "| Flavor | App | Format | Platform | Version | Ad unit | Match | Show | CTR | eCPM TRY | Earnings TRY | WoW earnings |",
        "|---|---|---|---|---|---|---:|---:|---:|---:|---:|---:|",
    ])
    for item in analysis["app_formats"][:40]:
        current = item["current"]
        lines.append(
            f"| {item['flavor']} | {item['app']} | {item['format']} | {item['platform']} | "
            f"{item['app_version']} | {item['ad_unit']} | {current['match_rate']:.1%} | "
            f"{current['show_rate']:.1%} | {current['ctr']:.1%} | "
            f"{current['impression_rpm_try']:.2f} | {current['earnings_try']:.2f} | "
            f"{format_change(item['changes_pct']['earnings_try'])} |"
        )
    lines.extend(["", "## Highest-priority app/format opportunities", ""])
    for item in analysis["app_format_opportunities"][:20]:
        lines.append(
            f"- `{item['flavor']}` / `{item['app']}` / `{item['format']}` / "
            f"`{item['app_version']}` / `{item['ad_unit']}`: "
            f"{', '.join(item['reasons'])}; matched={item['current']['matched_requests']}, "
            f"show={item['current']['show_rate']:.1%}."
        )
    if not analysis["app_format_opportunities"]:
        lines.append("- None.")

    lines.extend(["", "## Country opportunities", ""])
    for item in analysis["country_opportunities"][:20]:
        lines.append(
            f"- `{item['country']}` / `{item['format']}`: {', '.join(item['reasons'])}; "
            f"requests={item['current']['ad_requests']}, match={item['current']['match_rate']:.1%}, "
            f"show={item['current']['show_rate']:.1%}."
        )
    if not analysis["country_opportunities"]:
        lines.append("- None.")

    lines.extend(["", "## Bounded experiments", ""])
    for hypothesis in analysis["hypotheses"]:
        lines.extend([
            f"### `{hypothesis['id']}`",
            "",
            hypothesis["evidence"],
            "",
            f"- Change: {hypothesis['change']}",
            f"- Success: {hypothesis['success']}",
            f"- Rollback: {hypothesis['rollback']}",
            f"- Bounds: `{json.dumps(hypothesis['remote_config_bounds'], sort_keys=True)}`",
            "",
        ])
    return "\n".join(lines)


def format_change(value: float | None) -> str:
    return "n/a" if value is None else f"{value:+.1f}%"
