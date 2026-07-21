#!/usr/bin/env python3
"""Remote Config governance helpers and deterministic template generator."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping


@dataclass(frozen=True)
class AppRecord:
    flavor: str
    package_name: str
    firebase_app_id: str
    play_production_version_code: int
    repo_version_code: int

    @property
    def condition_name(self) -> str:
        return f"app_{self.flavor}"


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def read_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"Invalid properties line at {path}:{line_number}")
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def load_app_records(root: Path) -> list[AppRecord]:
    catalog = load_json(root / ".ci/apps.json")
    firebase_apps = load_json(root / "config/firebase-apps.json")
    play_snapshot = load_json(root / "config/remote-config/play-production-version-codes.json")
    versions = read_properties(root / "app-versions.properties")

    play_by_flavor = {
        item["flavor"]: int(item["production_version_code"])
        for item in play_snapshot["apps"]
    }
    records: list[AppRecord] = []
    for item in sorted(catalog, key=lambda row: row["flavor"]):
        flavor = item["flavor"]
        records.append(
            AppRecord(
                flavor=flavor,
                package_name=item["package"],
                firebase_app_id=firebase_apps[flavor]["appId"],
                play_production_version_code=play_by_flavor[flavor],
                repo_version_code=int(versions[f"{flavor}.versionCode"]),
            )
        )
    return records


def remote_string(value: Any) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    if value is None:
        return ""
    return str(value)


def app_value(spec: Mapping[str, Any], app: AppRecord) -> Any:
    derive = spec.get("derive")
    if derive == "play_production_version_code":
        return app.play_production_version_code
    if derive == "repo_version_code":
        return app.repo_version_code
    if derive == "min_supported_version_code":
        return int(spec.get("flavor_default", 1))
    overrides = spec.get("overrides", {})
    if app.flavor in overrides:
        return overrides[app.flavor]
    return spec.get("flavor_default", spec["default"])


def condition_expression(firebase_app_id: str) -> str:
    return f"app.id == '{firebase_app_id}'"


def build_template(governance: Mapping[str, Any], apps: Iterable[AppRecord]) -> dict[str, Any]:
    apps = list(apps)
    conditions = [
        {
            "name": app.condition_name,
            "expression": condition_expression(app.firebase_app_id),
            "tagColor": "INDIGO",
        }
        for app in apps
    ]
    parameters: dict[str, Any] = {}
    for key, spec in sorted(governance["parameters"].items()):
        parameter: dict[str, Any] = {
            "defaultValue": {"value": remote_string(spec["default"])},
            "description": spec["description"],
            "valueType": "STRING",
        }
        if spec["scope"] == "flavor":
            default_value = remote_string(spec["default"])
            conditional_values = {
                app.condition_name: {"value": remote_string(app_value(spec, app))}
                for app in apps
                if remote_string(app_value(spec, app)) != default_value
            }
            if conditional_values:
                parameter["conditionalValues"] = conditional_values
        parameters[key] = parameter
    return {"conditions": conditions, "parameters": parameters}


def validate_typed_value(key: str, spec: Mapping[str, Any], value: Any) -> list[str]:
    errors: list[str] = []
    logical_type = spec["type"]
    if logical_type == "boolean":
        if not isinstance(value, bool):
            errors.append(f"{key}: expected boolean, got {value!r}")
    elif logical_type == "integer":
        if isinstance(value, bool) or not isinstance(value, int):
            errors.append(f"{key}: expected integer, got {value!r}")
        else:
            if "minimum" in spec and value < int(spec["minimum"]):
                errors.append(f"{key}: {value} is below minimum {spec['minimum']}")
            if "maximum" in spec and value > int(spec["maximum"]):
                errors.append(f"{key}: {value} is above maximum {spec['maximum']}")
    elif logical_type in {"string", "csv", "url", "enum"}:
        if not isinstance(value, str):
            errors.append(f"{key}: expected string, got {value!r}")
        elif logical_type == "enum" and value not in spec.get("allowed_values", []):
            errors.append(f"{key}: {value!r} is not in {spec.get('allowed_values', [])}")
        elif logical_type == "url" and not value.startswith("https://"):
            errors.append(f"{key}: URL must use https://")
    else:
        errors.append(f"{key}: unknown logical type {logical_type!r}")
    return errors


def parse_kotlin_remote_keys(text: str) -> set[str]:
    return set(re.findall(r'const\s+val\s+[A-Z0-9_]+\s*=\s*"([a-z0-9_]+)"', text, flags=re.S))


def validate_governance(
    root: Path,
    governance: Mapping[str, Any],
    apps: list[AppRecord],
    template: Mapping[str, Any],
) -> list[str]:
    errors: list[str] = []
    if governance.get("schema_version") != 1:
        errors.append("Remote Config governance schema_version must be 1")
    if governance.get("firebase_project_id") != "makerpars-oaslananka-mobil":
        errors.append("Unexpected Firebase project id")
    if len(apps) != 17:
        errors.append(f"Expected 17 production apps, found {len(apps)}")

    flavors = [app.flavor for app in apps]
    if len(flavors) != len(set(flavors)):
        errors.append("Duplicate flavor in app inventory")
    packages = [app.package_name for app in apps]
    if len(packages) != len(set(packages)):
        errors.append("Duplicate package in app inventory")
    app_ids = [app.firebase_app_id for app in apps]
    if len(app_ids) != len(set(app_ids)):
        errors.append("Duplicate Firebase App ID in app inventory")

    for app in apps:
        google_services = root / "app/src" / app.flavor / "google-services.json"
        # google-services.json is materialized from CI secrets and is intentionally not tracked.
        # When present locally, use it as an additional cross-check; the committed source of truth
        # is config/firebase-apps.json plus .ci/apps.json.
        if google_services.exists():
            data = load_json(google_services)
            matched = False
            for client in data.get("client", []):
                info = client.get("client_info", {})
                package_name = info.get("android_client_info", {}).get("package_name")
                firebase_app_id = info.get("mobilesdk_app_id")
                if package_name == app.package_name and firebase_app_id == app.firebase_app_id:
                    matched = True
                    break
            if not matched:
                errors.append(f"Firebase app mapping mismatch for {app.flavor}")
        if app.play_production_version_code <= 0:
            errors.append(f"Invalid Play production version for {app.flavor}")
        if app.repo_version_code <= app.play_production_version_code:
            errors.append(
                f"{app.flavor}: repo versionCode {app.repo_version_code} must be greater than "
                f"Play production {app.play_production_version_code}"
            )

    parameters = governance.get("parameters", {})
    required_metadata = {"owner", "type", "scope", "default", "description"}
    for key, spec in parameters.items():
        missing = required_metadata - set(spec)
        if missing:
            errors.append(f"{key}: missing metadata {sorted(missing)}")
            continue
        if spec["scope"] not in {"global", "flavor"}:
            errors.append(f"{key}: invalid scope {spec['scope']!r}")
        errors.extend(validate_typed_value(key, spec, spec["default"]))
        if "client_default" in spec:
            errors.extend(validate_typed_value(key + ".client_default", spec, spec["client_default"]))
        if spec["scope"] == "flavor":
            for app in apps:
                errors.extend(validate_typed_value(f"{key}[{app.flavor}]", spec, app_value(spec, app)))

    update_keys_text = (root / "app/src/main/java/com/parsfilo/contentapp/update/UpdatePolicy.kt").read_text(
        encoding="utf-8"
    )
    ads_keys_text = (
        root / "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdsPolicyProvider.kt"
    ).read_text(encoding="utf-8")
    runtime_keys = parse_kotlin_remote_keys(update_keys_text) | parse_kotlin_remote_keys(ads_keys_text)
    missing_runtime_keys = sorted(runtime_keys - set(parameters))
    if missing_runtime_keys:
        errors.append("Runtime Remote Config keys missing from governance: " + ", ".join(missing_runtime_keys))

    expected_template = build_template(governance, apps)
    if template != expected_template:
        errors.append("Committed Remote Config template is stale; regenerate it")

    condition_names = {item["name"] for item in template.get("conditions", [])}
    expected_conditions = {app.condition_name for app in apps}
    if condition_names != expected_conditions:
        errors.append("Template conditions do not exactly match production flavors")
    for app in apps:
        expected = condition_expression(app.firebase_app_id)
        actual = next(
            (condition["expression"] for condition in template["conditions"] if condition["name"] == app.condition_name),
            None,
        )
        if actual != expected:
            errors.append(f"Condition expression mismatch for {app.flavor}")

    scoped_count = sum(1 for spec in parameters.values() if spec["scope"] == "flavor")
    for key, spec in parameters.items():
        parameter = template.get("parameters", {}).get(key, {})
        if spec["scope"] == "flavor":
            conditional_values = parameter.get("conditionalValues", {})
            expected_overrides = {
                app.condition_name
                for app in apps
                if remote_string(app_value(spec, app)) != remote_string(spec["default"])
            }
            if set(conditional_values) != expected_overrides:
                errors.append(
                    f"{key}: conditional overrides do not match the deterministic app diff"
                )
        elif "conditionalValues" in parameter:
            errors.append(f"{key}: global parameter must not have conditional values")

    safe_defaults = governance.get("safe_fallback_requirements", {})
    for key, expected in safe_defaults.items():
        if key not in parameters:
            errors.append(f"Safe fallback key is not governed: {key}")
        elif parameters[key]["default"] != expected:
            errors.append(f"Unsafe fallback for {key}: expected {expected!r}")

    for app in apps:
        values = {key: app_value(spec, app) for key, spec in parameters.items() if spec["scope"] == "flavor"}
        minimum = int(values["min_supported_version_code"])
        latest = int(values["latest_version_code"])
        if not (1 <= minimum <= latest <= app.repo_version_code):
            errors.append(
                f"{app.flavor}: invalid update range min={minimum}, latest={latest}, repo={app.repo_version_code}"
            )
        if values["update_mode"] not in {"none", "soft", "hard"}:
            errors.append(f"{app.flavor}: invalid update mode")
        if values["updates_enabled"] is not True or values["update_emergency_disabled"] is not False:
            errors.append(f"{app.flavor}: update condition is not explicitly enabled and safe")
        if values["ads_emergency_disabled"] is not False:
            errors.append(f"{app.flavor}: ad condition must explicitly clear app emergency disable")

    if scoped_count == 0:
        errors.append("No flavor-scoped parameters were defined")
    return errors


def build_report(governance: Mapping[str, Any], apps: list[AppRecord]) -> dict[str, Any]:
    parameters = governance["parameters"]
    global_parameters = sorted(key for key, spec in parameters.items() if spec["scope"] == "global")
    flavor_parameters = sorted(key for key, spec in parameters.items() if spec["scope"] == "flavor")
    app_rows = []
    for app in apps:
        values = {key: app_value(parameters[key], app) for key in flavor_parameters}
        app_rows.append(
            {
                "flavor": app.flavor,
                "package": app.package_name,
                "firebase_app_id": app.firebase_app_id,
                "condition": app.condition_name,
                "play_production_version_code": app.play_production_version_code,
                "repo_version_code": app.repo_version_code,
                "min_supported_version_code": values["min_supported_version_code"],
                "latest_version_code": values["latest_version_code"],
                "update_mode": values["update_mode"],
                "updates_enabled": values["updates_enabled"],
                "ads_emergency_disabled": values["ads_emergency_disabled"],
            }
        )
    return {
        "schema_version": governance["schema_version"],
        "firebase_project_id": governance["firebase_project_id"],
        "condition_count": len(apps),
        "parameter_count": len(parameters),
        "global_parameter_count": len(global_parameters),
        "flavor_parameter_count": len(flavor_parameters),
        "conditional_value_count": sum(
            1
            for key in flavor_parameters
            for app in apps
            if remote_string(app_value(parameters[key], app))
            != remote_string(parameters[key]["default"])
        ),
        "global_parameters": global_parameters,
        "flavor_parameters": flavor_parameters,
        "apps": app_rows,
        "rollback": governance["rollback"],
    }


def render_markdown(report: Mapping[str, Any]) -> str:
    lines = [
        "# Remote Config Governance",
        "",
        "This file is generated by `scripts/ci/validate_remote_config_governance.py`.",
        "",
        "## Contract summary",
        "",
        f"- Firebase project: `{report['firebase_project_id']}`",
        f"- Production app conditions: **{report['condition_count']}**",
        f"- Governed parameters: **{report['parameter_count']}**",
        f"- Global parameters: **{report['global_parameter_count']}**",
        f"- Flavor-scoped parameters: **{report['flavor_parameter_count']}**",
        f"- Explicit conditional values: **{report['conditional_value_count']}**",
        "",
        "Unknown/unmatched Firebase apps receive the server fallback: updates disabled and ads emergency-disabled.",
        "Known production apps must match exactly one `app.id` condition.",
        "",
        "## Deployment impact",
        "",
        "| Flavor | Package | Play production | Repo | RC min | RC latest | Mode | Condition |",
        "|---|---|---:|---:|---:|---:|---|---|",
    ]
    for app in report["apps"]:
        lines.append(
            "| {flavor} | `{package}` | {play_production_version_code} | {repo_version_code} | "
            "{min_supported_version_code} | {latest_version_code} | `{update_mode}` | `{condition}` |".format(
                **app
            )
        )
    lines.extend(
        [
            "",
            "## Safety and rollback",
            "",
            "- `update_global_emergency_disabled=true` disables every update prompt before version rules.",
            "- `updates_enabled=false` or `update_emergency_disabled=true` disables update prompts for one app.",
            "- `ads_global_emergency_disabled=true` disables all governed ad formats.",
            "- `ads_emergency_disabled=true` disables all governed ad formats for one app.",
            "- Malformed update ranges fall back to the installed version and resolve to no prompt.",
            "- Numeric ad values are range-checked by both CI schema validation and runtime sanitizers.",
            "- Firebase stores template history; rollback uses the audited previous version number.",
            "",
            "## Review workflow",
            "",
            "1. Update the governance manifest or Play production snapshot.",
            "2. Regenerate the deterministic template and this report.",
            "3. Run the validator and unit tests.",
            "4. Validate the template against Firebase with `validate_only=true`.",
            "5. Review the generated deployment impact table before publishing.",
            "6. Record the published template version and retain the previous version for rollback.",
            "",
            "## Rollback policy",
            "",
            f"- Minimum retained template versions: **{report['rollback']['minimum_versions_to_retain']}**",
            f"- Emergency owner: `{report['rollback']['emergency_owner']}`",
            f"- Change owner: `{report['rollback']['change_owner']}`",
            "",
        ]
    )
    return "\n".join(lines)
