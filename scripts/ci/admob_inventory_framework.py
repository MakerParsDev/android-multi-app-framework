#!/usr/bin/env python3
"""Validate AdMob ownership for Android framework flavors and resources."""

from __future__ import annotations

from typing import Any, Mapping

from admob_inventory_common import (
    ALIAS_RESOURCE_UNITS,
    OBSOLETE_PLACEMENTS,
    PACKAGE_PATTERN,
    PRIMARY_AD_UNITS,
    ValidationContext,
    append_duplicate_errors,
    as_mapping,
    load_json,
    normalize_targets,
    parse_ads_xml,
    validate_ad_unit_id,
    validate_app_id,
)


def load_framework_catalog(
    context: ValidationContext,
    audit: Mapping[str, Any],
) -> list[Mapping[str, Any]]:
    sources = as_mapping(audit.get("sources"), "audit.sources", context)
    catalog_path = context.root / str(
        sources.get("framework_catalog", ".ci/apps.json")
    )
    if not catalog_path.exists():
        context.errors.append(
            f"Missing framework catalog: {catalog_path.relative_to(context.root)}"
        )
        return []
    catalog = load_json(catalog_path)
    if not isinstance(catalog, list):
        context.errors.append("Framework catalog must be a JSON array")
        return []
    return [entry for entry in catalog if isinstance(entry, Mapping)]


def validate_framework_entry(
    context: ValidationContext,
    flavor: str,
    catalog_app: Mapping[str, Any],
    inventory_app: Mapping[str, Any],
) -> dict[str, Any]:
    package_name = str(catalog_app.get("package", ""))
    display_name = str(catalog_app.get("name", ""))
    app_id = str(catalog_app.get("admob_app_id", ""))
    if not PACKAGE_PATTERN.fullmatch(package_name):
        context.errors.append(f"{flavor}: invalid package {package_name!r}")
    validate_app_id(app_id, f"{flavor}.admob_app_id", context)

    validate_framework_metadata(
        context,
        flavor,
        inventory_app,
        {
            "package": package_name,
            "display_name": display_name,
            "admob_app_id": app_id,
            "linked_store_id": package_name,
            "approval_state": "APPROVED",
        },
    )
    catalog_units = as_mapping(
        catalog_app.get("ad_units"),
        f"{flavor}.ad_units",
        context,
    )
    if set(catalog_units) != set(PRIMARY_AD_UNITS):
        context.errors.append(
            f"{flavor}: catalog ad unit keys must be exactly "
            + ", ".join(sorted(PRIMARY_AD_UNITS))
        )

    xml_values = load_flavor_ads_xml(context, flavor)
    if xml_values.get("admob_app_id") != app_id:
        context.errors.append(
            f"{flavor}: ads.xml app ID {xml_values.get('admob_app_id')!r} "
            f"does not match catalog {app_id!r}"
        )
    unit_ids = validate_primary_and_alias_units(
        context,
        flavor,
        catalog_units,
        xml_values,
    )
    for obsolete in OBSOLETE_PLACEMENTS:
        if obsolete in xml_values:
            context.warnings.append(
                f"{flavor}: obsolete placement resource still present: {obsolete}"
            )
    return {
        "row": {
            "flavor": flavor,
            "package": package_name,
            "display_name": display_name,
            "admob_app_id": app_id,
            "primary_ad_unit_count": len(catalog_units),
        },
        "package": package_name,
        "app_id": app_id,
        "unit_ids": unit_ids,
        "resource_values": set(xml_values.values()),
    }


def validate_framework_metadata(
    context: ValidationContext,
    flavor: str,
    inventory_app: Mapping[str, Any],
    expected_fields: Mapping[str, Any],
) -> None:
    for key, expected in expected_fields.items():
        if inventory_app.get(key) != expected:
            context.errors.append(
                f"{flavor}.{key}: expected {expected!r}, "
                f"got {inventory_app.get(key)!r}"
            )


def load_flavor_ads_xml(
    context: ValidationContext,
    flavor: str,
) -> dict[str, str]:
    xml_path = (
        context.root / "app" / "src" / flavor / "res" / "values" / "ads.xml"
    )
    if not xml_path.exists():
        context.errors.append(f"{flavor}: missing {xml_path.relative_to(context.root)}")
        return {}
    try:
        return parse_ads_xml(xml_path)
    except (OSError, ValueError) as exc:
        context.errors.append(f"{flavor}: failed to parse ads.xml: {exc}")
        return {}


def validate_primary_and_alias_units(
    context: ValidationContext,
    flavor: str,
    catalog_units: Mapping[str, Any],
    xml_values: Mapping[str, str],
) -> list[str]:
    unit_ids: list[str] = []
    for unit_name, (resource_name, _format) in PRIMARY_AD_UNITS.items():
        unit_id = str(catalog_units.get(unit_name, ""))
        unit_ids.append(unit_id)
        validate_ad_unit_id(
            unit_id,
            f"{flavor}.ad_units.{unit_name}",
            context,
        )
        if xml_values.get(resource_name) != unit_id:
            context.errors.append(
                f"{flavor}: {resource_name} {xml_values.get(resource_name)!r} "
                f"does not match catalog {unit_id!r}"
            )
    for alias_resource, unit_name in ALIAS_RESOURCE_UNITS.items():
        expected_unit = str(catalog_units.get(unit_name, ""))
        if xml_values.get(alias_resource) != expected_unit:
            context.errors.append(
                f"{flavor}: {alias_resource} {xml_values.get(alias_resource)!r} "
                f"does not match catalog {expected_unit!r}"
            )
    return unit_ids


def validate_framework_inventory(
    context: ValidationContext,
    framework_apps: list[Any],
    catalog: list[Mapping[str, Any]],
    target_flavors: str,
) -> dict[str, Any]:
    framework_by_flavor = {
        str(app.get("flavor")): app
        for app in framework_apps
        if isinstance(app, Mapping)
    }
    catalog_by_flavor = {
        str(app.get("flavor")): app
        for app in catalog
        if isinstance(app, Mapping)
    }
    requested_targets = normalize_targets(
        target_flavors,
        set(catalog_by_flavor),
        context.errors,
    )
    report_scope_drift(context, framework_by_flavor, catalog_by_flavor)

    rows: list[dict[str, Any]] = []
    packages: list[str] = []
    app_ids: list[str] = []
    unit_ids: list[str] = []
    resource_values: set[str] = set()
    for flavor, catalog_app in sorted(catalog_by_flavor.items()):
        inventory_app = framework_by_flavor.get(flavor)
        if not isinstance(inventory_app, Mapping):
            continue
        result = validate_framework_entry(
            context,
            flavor,
            catalog_app,
            inventory_app,
        )
        rows.append(result["row"])
        packages.append(result["package"])
        app_ids.append(result["app_id"])
        unit_ids.extend(result["unit_ids"])
        resource_values.update(result["resource_values"])

    append_duplicate_errors(packages, "Duplicate framework package", context)
    append_duplicate_errors(app_ids, "Duplicate framework AdMob app ID", context)
    append_duplicate_errors(
        unit_ids,
        "Primary ad unit is owned by multiple framework apps",
        context,
    )
    return {
        "rows": rows,
        "packages": packages,
        "app_ids": app_ids,
        "unit_ids": unit_ids,
        "resource_values": resource_values,
        "catalog_by_flavor": catalog_by_flavor,
        "requested_targets": requested_targets,
    }


def report_scope_drift(
    context: ValidationContext,
    framework_by_flavor: Mapping[str, Any],
    catalog_by_flavor: Mapping[str, Any],
) -> None:
    missing = sorted(set(catalog_by_flavor) - set(framework_by_flavor))
    extra = sorted(set(framework_by_flavor) - set(catalog_by_flavor))
    if missing:
        context.errors.append(
            "Framework apps missing from AdMob inventory: " + ", ".join(missing)
        )
    if extra:
        context.errors.append(
            "Unknown framework apps in AdMob inventory: " + ", ".join(extra)
        )
