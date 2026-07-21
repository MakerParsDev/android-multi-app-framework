#!/usr/bin/env python3
"""Orchestrate AdMob inventory validation and deterministic reporting."""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any, Mapping

from admob_inventory_cleanup import (
    create_cleanup_context,
    validate_cleanup_inventory,
    validate_external_inventory,
)
from admob_inventory_common import (
    ValidationContext,
    append_duplicate_errors,
    as_list,
    as_mapping,
    validate_debug_ads,
)
from admob_inventory_framework import (
    load_framework_catalog,
    validate_framework_inventory,
)


@dataclass
class AuditResult:
    publisher_id: str
    audit: Mapping[str, Any]
    window: Mapping[str, Any]
    captured_on: date | None
    inclusive_days: int | None
    snapshot_age_days: int | None


@dataclass
class ScopeResults:
    framework: Mapping[str, Any]
    external: Mapping[str, Any]
    cleanup: Mapping[str, Any]
    mediation_status: str | None
    warnings: list[str]


def preflight_mapping(
    value: Any,
    field: str,
    errors: list[str],
) -> Mapping[str, Any]:
    if isinstance(value, Mapping):
        return value
    errors.append(f"{field} must be an object")
    return {}


def parse_iso_date(value: Any, field: str, errors: list[str]) -> date | None:
    try:
        return date.fromisoformat(str(value))
    except (TypeError, ValueError):
        errors.append(f"{field}: expected ISO date, got {value!r}")
        return None


def validate_account_and_audit(
    inventory: Mapping[str, Any],
    today: date,
    errors: list[str],
) -> AuditResult:
    account = preflight_mapping(inventory.get("account"), "account", errors)
    publisher_id = str(account.get("publisher_id", ""))
    if not re.fullmatch(r"pub-\d{16}", publisher_id):
        errors.append(f"account.publisher_id: invalid publisher ID {publisher_id!r}")
    if account.get("reporting_time_zone") != "Europe/Istanbul":
        errors.append("account.reporting_time_zone must be Europe/Istanbul")
    if account.get("currency_code") != "TRY":
        errors.append("account.currency_code must be TRY")

    audit = preflight_mapping(inventory.get("audit"), "audit", errors)
    captured_on = parse_iso_date(audit.get("captured_on"), "audit.captured_on", errors)
    window = preflight_mapping(
        audit.get("traffic_window"),
        "audit.traffic_window",
        errors,
    )
    inclusive_days = validate_traffic_window(window, captured_on, errors)
    validate_snapshot_freshness(audit, captured_on, today, errors)
    return AuditResult(
        publisher_id=publisher_id,
        audit=audit,
        window=window,
        captured_on=captured_on,
        inclusive_days=inclusive_days,
        snapshot_age_days=(today - captured_on).days if captured_on else None,
    )


def validate_traffic_window(
    window: Mapping[str, Any],
    captured_on: date | None,
    errors: list[str],
) -> int | None:
    start_date = parse_iso_date(
        window.get("start_date"),
        "audit.traffic_window.start_date",
        errors,
    )
    end_date = parse_iso_date(
        window.get("end_date"),
        "audit.traffic_window.end_date",
        errors,
    )
    inclusive_days = window.get("inclusive_days")
    if not start_date or not end_date:
        return inclusive_days if isinstance(inclusive_days, int) else None

    actual_days = (end_date - start_date).days + 1
    if actual_days <= 0:
        errors.append("audit.traffic_window must be chronological")
    if inclusive_days != actual_days:
        errors.append(
            f"audit.traffic_window.inclusive_days: expected {actual_days}, "
            f"got {inclusive_days!r}"
        )
    if captured_on and end_date > captured_on:
        errors.append("audit traffic window cannot end after capture date")
    return inclusive_days if isinstance(inclusive_days, int) else None


def validate_snapshot_freshness(
    audit: Mapping[str, Any],
    captured_on: date | None,
    today: date,
    errors: list[str],
) -> None:
    freshness_max_days = audit.get("freshness_max_days", 90)
    if not isinstance(freshness_max_days, int) or freshness_max_days < 1:
        errors.append("audit.freshness_max_days must be a positive integer")
    elif captured_on and (today - captured_on).days > freshness_max_days:
        errors.append(
            f"AdMob inventory snapshot is stale: captured {captured_on.isoformat()}, "
            f"maximum age {freshness_max_days} days"
        )


def validate_cross_scope_uniqueness(
    context: ValidationContext,
    framework: Mapping[str, Any],
    external: Mapping[str, Any],
    cleanup: Mapping[str, Any],
) -> None:
    all_app_ids = framework["app_ids"] + external["app_ids"] + cleanup["app_ids"]
    append_duplicate_errors(
        all_app_ids,
        "AdMob app ID appears in multiple inventory scopes",
        context,
    )
    cross_scope_units = sorted(set(framework["unit_ids"]) & set(cleanup["unit_ids"]))
    if cross_scope_units:
        context.errors.append(
            "Cleanup ad units overlap production units: "
            + ", ".join(cross_scope_units)
        )


def validate_live_counts(
    context: ValidationContext,
    audit: Mapping[str, Any],
    scope_counts: Mapping[str, int],
) -> None:
    live_counts = as_mapping(audit.get("live_counts"), "audit.live_counts", context)
    expected_approved = scope_counts["framework"] + scope_counts["external"]
    expected = {
        "approved_apps": expected_approved,
        "action_required_apps": scope_counts["cleanup"],
        "apps": expected_approved + scope_counts["cleanup"],
    }
    for key, value in expected.items():
        if live_counts.get(key) != value:
            context.errors.append(
                f"audit.live_counts.{key}: expected {value}, "
                f"got {live_counts.get(key)!r}"
            )


def validate_mediation(
    context: ValidationContext,
    inventory: Mapping[str, Any],
) -> str | None:
    mediation = as_mapping(
        inventory.get("mediation_audit"),
        "mediation_audit",
        context,
    )
    status = mediation.get("api_status")
    if status not in {"verified", "permission_denied"}:
        context.errors.append(
            "mediation_audit.api_status must be verified or permission_denied"
        )
    if not str(mediation.get("manual_action", "")).strip():
        context.errors.append(
            "mediation_audit.manual_action must describe the remediation"
        )
    return str(status) if status is not None else None


def validate_inventory(
    root: Path,
    inventory: Mapping[str, Any],
    *,
    today: date,
    target_flavors: str = "all",
) -> tuple[list[str], dict[str, Any]]:
    errors: list[str] = []
    warnings: list[str] = []
    audit_result = validate_account_and_audit(inventory, today, errors)
    context = ValidationContext(
        root=root,
        publisher_id=audit_result.publisher_id,
        errors=errors,
        warnings=warnings,
    )
    catalog = load_framework_catalog(context, audit_result.audit)
    framework_apps = as_list(
        inventory.get("approved_framework_apps"),
        "approved_framework_apps",
        context,
    )
    external_apps = as_list(
        inventory.get("approved_external_apps"),
        "approved_external_apps",
        context,
    )
    cleanup_apps = as_list(
        inventory.get("action_required_apps"),
        "action_required_apps",
        context,
    )

    framework = validate_framework_inventory(
        context,
        framework_apps,
        catalog,
        target_flavors,
    )
    external = validate_external_inventory(context, external_apps)
    cleanup_policy = as_mapping(
        inventory.get("cleanup_policy"),
        "cleanup_policy",
        context,
    )
    cleanup_context = create_cleanup_context(
        context,
        framework["catalog_by_flavor"],
        framework["resource_values"],
        audit_result.inclusive_days,
        cleanup_policy,
    )
    cleanup = validate_cleanup_inventory(cleanup_context, cleanup_apps)
    validate_cross_scope_uniqueness(context, framework, external, cleanup)
    validate_live_counts(
        context,
        audit_result.audit,
        {
            "framework": len(framework_apps),
            "external": len(external_apps),
            "cleanup": len(cleanup_apps),
        },
    )
    validate_debug_ads(context)
    mediation_status = validate_mediation(context, inventory)
    scopes = ScopeResults(
        framework=framework,
        external=external,
        cleanup=cleanup,
        mediation_status=mediation_status,
        warnings=warnings,
    )
    return errors, build_report(inventory, audit_result, scopes)


def build_report(
    inventory: Mapping[str, Any],
    audit: AuditResult,
    scopes: ScopeResults,
) -> dict[str, Any]:
    framework = scopes.framework
    external = scopes.external
    cleanup = scopes.cleanup
    return {
        "schema_version": inventory.get("schema_version"),
        "publisher_id": audit.publisher_id,
        "captured_on": audit.audit.get("captured_on"),
        "traffic_window": audit.window,
        "snapshot_age_days": audit.snapshot_age_days,
        "framework_app_count": len(framework["rows"]),
        "framework_primary_ad_unit_count": len(framework["unit_ids"]),
        "external_approved_app_count": len(external["rows"]),
        "action_required_app_count": len(cleanup["rows"]),
        "action_required_ad_unit_count": len(cleanup["unit_ids"]),
        "pending_manual_cleanup_count": sum(
            1 for row in cleanup["rows"] if row["manual_console_status"] == "pending"
        ),
        "mediation_api_status": scopes.mediation_status,
        "requested_target_flavors": framework["requested_targets"],
        "warnings": scopes.warnings,
        "framework_apps": framework["rows"],
        "external_apps": external["rows"],
        "cleanup_candidates": cleanup["rows"],
    }


def render_framework_section(report: Mapping[str, Any]) -> list[str]:
    lines = [
        "## Framework production ownership",
        "",
        "| Flavor | Package | AdMob app ID | Primary units |",
        "|---|---|---|---:|",
    ]
    for app in report["framework_apps"]:
        lines.append(
            "| {flavor} | `{package}` | `{admob_app_id}` | "
            "{primary_ad_unit_count} |".format(**app)
        )
    return lines


def render_cleanup_section(report: Mapping[str, Any]) -> list[str]:
    lines = [
        "## Cleanup candidates",
        "",
        "All candidates below had zero requests, matches, impressions, clicks, "
        "and estimated earnings during the audited 90-day window.",
        "",
        "| App | AdMob app ID | Relationship | Units | Formats | Console status |",
        "|---|---|---|---:|---|---|",
    ]
    for candidate in report["cleanup_candidates"]:
        formats = ", ".join(candidate["ad_unit_formats"]) or "None"
        lines.append(
            "| {display_name} | `{admob_app_id}` | `{relationship}` | "
            "{ad_unit_count} | {formats} | `{manual_console_status}` |".format(
                formats=formats,
                **candidate,
            )
        )
    return lines


def render_external_section(report: Mapping[str, Any]) -> list[str]:
    lines = [
        "## Approved external apps",
        "",
        "These apps belong to the AdMob account but are intentionally outside "
        "this 17-flavor repository.",
        "",
        "| App | Package | AdMob app ID |",
        "|---|---|---|",
    ]
    for app in report["external_apps"]:
        lines.append(
            "| {display_name} | `{package}` | `{admob_app_id}` |".format(**app)
        )
    return lines


def render_markdown(report: Mapping[str, Any]) -> str:
    window = report["traffic_window"]
    lines = [
        "# AdMob Inventory Governance",
        "",
        "This file is generated by `scripts/ci/validate_admob_inventory.py`.",
        "",
        "## Account summary",
        "",
        f"- Publisher: `{report['publisher_id']}`",
        f"- Snapshot date: **{report['captured_on']}**",
        f"- Traffic evidence: **{window['start_date']} – {window['end_date']}** "
        f"({window['inclusive_days']} days)",
        f"- Framework production apps: **{report['framework_app_count']}**",
        f"- Framework primary ad units: **{report['framework_primary_ad_unit_count']}**",
        f"- Approved external apps: **{report['external_approved_app_count']}**",
        f"- ACTION_REQUIRED apps: **{report['action_required_app_count']}**",
        f"- ACTION_REQUIRED ad units: **{report['action_required_ad_unit_count']}**",
        f"- Pending manual cleanup: **{report['pending_manual_cleanup_count']}**",
        f"- Mediation API: `{report['mediation_api_status']}`",
        "",
    ]
    lines.extend(render_framework_section(report))
    lines.extend([""] + render_cleanup_section(report))
    lines.extend([""] + render_external_section(report))
    lines.extend(render_completion_sections())
    return "\n".join(lines)


def render_completion_sections() -> list[str]:
    return [
        "",
        "## Manual completion checklist",
        "",
        "1. Reconfirm the four candidates show zero recent traffic in the AdMob console.",
        "2. Archive or remove the three duplicate/manual religious-app records.",
        "3. Confirm all six Missile Core ad units are unused, then archive or remove the legacy app record.",
        "4. Grant mediation-group/ad-source read access to the audit service account and rerun the mediation audit.",
        "5. Refresh this snapshot after console cleanup; the ACTION_REQUIRED count must become zero.",
        "",
        "## CI guarantees",
        "",
        "- Every framework package maps to exactly one approved AdMob app ID.",
        "- Every framework flavor owns exactly six unique primary ad units.",
        "- XML resources, `.ci/apps.json`, and this account snapshot must agree.",
        "- Cleanup app/ad-unit IDs cannot appear in production resources.",
        "- A cleanup recommendation is blocked when any audited traffic or earnings metric is non-zero.",
        "- The account snapshot expires and forces a periodic re-audit.",
        "",
    ]
