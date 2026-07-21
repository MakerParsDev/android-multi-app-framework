#!/usr/bin/env python3
"""Validate external AdMob apps and ACTION_REQUIRED cleanup candidates."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping

from admob_inventory_common import (
    CLEANUP_ACTIONS,
    MANUAL_STATUSES,
    PACKAGE_PATTERN,
    PRIMARY_AD_UNITS,
    TRAFFIC_METRICS,
    ValidationContext,
    append_duplicate_errors,
    as_list,
    as_mapping,
    relationship_label,
    validate_ad_unit_id,
    validate_app_id,
)


@dataclass
class CleanupContext:
    validation: ValidationContext
    catalog_by_flavor: Mapping[str, Any]
    production_resource_values: set[str]
    inclusive_days: int | None
    minimum_zero_days: int


def validate_external_inventory(
    context: ValidationContext,
    external_apps: list[Any],
) -> dict[str, Any]:
    rows: list[dict[str, Any]] = []
    packages: list[str] = []
    app_ids: list[str] = []
    for index, raw_app in enumerate(external_apps):
        app = as_mapping(raw_app, f"approved_external_apps[{index}]", context)
        package_name = str(app.get("package", ""))
        app_id = str(app.get("admob_app_id", ""))
        packages.append(package_name)
        app_ids.append(app_id)
        if not PACKAGE_PATTERN.fullmatch(package_name):
            context.errors.append(
                f"external app {index}: invalid package {package_name!r}"
            )
        validate_app_id(app_id, f"external app {index}", context)
        if app.get("approval_state") != "APPROVED":
            context.errors.append(
                f"external app {package_name}: approval_state must be APPROVED"
            )
        if app.get("repository_scope") != "external":
            context.errors.append(
                f"external app {package_name}: repository_scope must be external"
            )
        rows.append(
            {
                "package": package_name,
                "display_name": str(app.get("display_name", "")),
                "admob_app_id": app_id,
            }
        )
    append_duplicate_errors(packages, "Duplicate external package", context)
    append_duplicate_errors(app_ids, "Duplicate external AdMob app ID", context)
    return {"rows": rows, "packages": packages, "app_ids": app_ids}


def create_cleanup_context(
    validation: ValidationContext,
    catalog_by_flavor: Mapping[str, Any],
    production_resource_values: set[str],
    inclusive_days: int | None,
    cleanup_policy: Mapping[str, Any],
) -> CleanupContext:
    minimum_zero_days = cleanup_policy.get("minimum_zero_traffic_days", 90)
    if not isinstance(minimum_zero_days, int) or minimum_zero_days < 1:
        validation.errors.append(
            "cleanup_policy.minimum_zero_traffic_days must be a positive integer"
        )
        minimum_zero_days = 90
    return CleanupContext(
        validation=validation,
        catalog_by_flavor=catalog_by_flavor,
        production_resource_values=production_resource_values,
        inclusive_days=inclusive_days,
        minimum_zero_days=minimum_zero_days,
    )


def validate_cleanup_inventory(
    context: CleanupContext,
    cleanup_apps: list[Any],
) -> dict[str, Any]:
    rows: list[dict[str, Any]] = []
    app_ids: list[str] = []
    unit_ids: list[str] = []
    for index, candidate in enumerate(cleanup_apps):
        result = validate_cleanup_candidate(context, candidate, index)
        rows.append(result["row"])
        app_ids.append(result["app_id"])
        unit_ids.extend(result["unit_ids"])
    append_duplicate_errors(
        app_ids,
        "Duplicate cleanup candidate AdMob app ID",
        context.validation,
    )
    append_duplicate_errors(
        unit_ids,
        "Duplicate cleanup candidate ad unit ID",
        context.validation,
    )
    return {"rows": rows, "app_ids": app_ids, "unit_ids": unit_ids}


def validate_cleanup_candidate(
    context: CleanupContext,
    raw_candidate: Any,
    index: int,
) -> dict[str, Any]:
    validation = context.validation
    candidate = as_mapping(
        raw_candidate,
        f"action_required_apps[{index}]",
        validation,
    )
    display_name = str(candidate.get("display_name", ""))
    app_id = str(candidate.get("admob_app_id", ""))
    validate_app_id(app_id, f"cleanup candidate {display_name}", validation)
    validate_cleanup_metadata(validation, candidate, display_name)
    validate_cleanup_relationship(context, candidate, display_name)
    units, unit_ids, formats = validate_cleanup_units(
        validation,
        candidate,
        display_name,
    )
    metrics = validate_cleanup_metrics(context, candidate, display_name)
    report_cleanup_resource_leaks(context, display_name, app_id, unit_ids)
    return {
        "row": {
            "display_name": display_name,
            "admob_app_id": app_id,
            "relationship": relationship_label(candidate),
            "ad_unit_count": len(units),
            "ad_unit_formats": sorted(formats),
            "traffic_90d": {metric: metrics.get(metric) for metric in TRAFFIC_METRICS},
            "recommended_action": candidate.get("recommended_action"),
            "manual_console_status": candidate.get("manual_console_status"),
        },
        "app_id": app_id,
        "unit_ids": unit_ids,
    }


def validate_cleanup_metadata(
    context: ValidationContext,
    candidate: Mapping[str, Any],
    display_name: str,
) -> None:
    if candidate.get("approval_state") != "ACTION_REQUIRED":
        context.errors.append(
            f"cleanup candidate {display_name}: approval_state must be ACTION_REQUIRED"
        )
    if candidate.get("linked_store_id") is not None:
        context.errors.append(
            f"cleanup candidate {display_name}: linked_store_id must be null"
        )
    if candidate.get("recommended_action") not in CLEANUP_ACTIONS:
        context.errors.append(
            f"cleanup candidate {display_name}: unsupported recommended_action"
        )
    if candidate.get("manual_console_status") not in MANUAL_STATUSES:
        context.errors.append(
            f"cleanup candidate {display_name}: invalid manual_console_status"
        )


def validate_cleanup_units(
    context: ValidationContext,
    candidate: Mapping[str, Any],
    display_name: str,
) -> tuple[list[Mapping[str, Any]], list[str], list[str]]:
    raw_units = as_list(
        candidate.get("ad_units"),
        f"cleanup candidate {display_name}.ad_units",
        context,
    )
    units: list[Mapping[str, Any]] = []
    unit_ids: list[str] = []
    formats: list[str] = []
    allowed_formats = {value[1] for value in PRIMARY_AD_UNITS.values()}
    for index, raw_unit in enumerate(raw_units):
        unit = as_mapping(
            raw_unit,
            f"cleanup candidate {display_name}.ad_units[{index}]",
            context,
        )
        unit_id = str(unit.get("ad_unit_id", ""))
        unit_format = str(unit.get("format", ""))
        units.append(unit)
        unit_ids.append(unit_id)
        formats.append(unit_format)
        validate_ad_unit_id(
            unit_id,
            f"cleanup candidate {display_name} ad unit {index}",
            context,
        )
        if not str(unit.get("name", "")).strip():
            context.errors.append(
                f"cleanup candidate {display_name}: ad unit name is empty"
            )
        if unit_format not in allowed_formats:
            context.errors.append(
                f"cleanup candidate {display_name}: unsupported ad format {unit_format!r}"
            )
    return units, unit_ids, formats


def validate_cleanup_metrics(
    context: CleanupContext,
    candidate: Mapping[str, Any],
    display_name: str,
) -> Mapping[str, Any]:
    validation = context.validation
    metrics = as_mapping(
        candidate.get("traffic_90d"),
        f"cleanup candidate {display_name}.traffic_90d",
        validation,
    )
    nonzero_metrics: list[str] = []
    for metric in TRAFFIC_METRICS:
        value = metrics.get(metric)
        if isinstance(value, bool) or not isinstance(value, int) or value < 0:
            validation.errors.append(
                f"cleanup candidate {display_name}: "
                f"{metric} must be a non-negative integer"
            )
        elif value != 0:
            nonzero_metrics.append(metric)
    if (
        isinstance(context.inclusive_days, int)
        and context.inclusive_days < context.minimum_zero_days
    ):
        validation.errors.append(
            f"cleanup candidate {display_name}: traffic evidence covers only "
            f"{context.inclusive_days} days; minimum is {context.minimum_zero_days}"
        )
    if candidate.get("recommended_action") in CLEANUP_ACTIONS and nonzero_metrics:
        validation.errors.append(
            f"cleanup candidate {display_name}: cannot recommend cleanup "
            "with non-zero metrics: " + ", ".join(nonzero_metrics)
        )
    return metrics


def validate_cleanup_relationship(
    context: CleanupContext,
    candidate: Mapping[str, Any],
    display_name: str,
) -> None:
    validation = context.validation
    relationship = as_mapping(
        candidate.get("relationship"),
        f"cleanup candidate {display_name}.relationship",
        validation,
    )
    relation_type = relationship.get("type")
    target_flavor = relationship.get("target_flavor")
    if relation_type == "duplicate_of_framework_app":
        if target_flavor not in context.catalog_by_flavor:
            validation.errors.append(
                f"cleanup candidate {display_name}: "
                f"unknown duplicate target {target_flavor!r}"
            )
    elif relation_type == "legacy_external_app":
        if target_flavor is not None:
            validation.errors.append(
                f"cleanup candidate {display_name}: "
                "legacy external app cannot target a flavor"
            )
    else:
        validation.errors.append(
            f"cleanup candidate {display_name}: unsupported relationship type"
        )


def report_cleanup_resource_leaks(
    context: CleanupContext,
    display_name: str,
    app_id: str,
    unit_ids: list[str],
) -> None:
    validation = context.validation
    if app_id in context.production_resource_values:
        validation.errors.append(
            f"cleanup candidate app ID leaks into production resources: {app_id}"
        )
    leaked_units = sorted(set(unit_ids) & context.production_resource_values)
    if leaked_units:
        validation.errors.append(
            f"cleanup candidate {display_name}: "
            "ad units leak into production resources: " + ", ".join(leaked_units)
        )
