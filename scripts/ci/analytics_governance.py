#!/usr/bin/env python3
"""Analytics contract discovery, validation, and deterministic reporting."""

from __future__ import annotations

import json
import re
from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Set, Tuple

NAME_PATTERN = re.compile(r"^[A-Za-z][A-Za-z0-9_]*$")
RESERVED_PREFIXES = ("firebase_", "google_", "ga_")
CONST_PATTERN = re.compile(r'const\s+val\s+(\w+)\s*=\s*"([^"]+)"')
SCHEMA_VERSION_PATTERN = re.compile(
    r"const\s+val\s+ANALYTICS_SCHEMA_VERSION(?:\s*:\s*Int)?\s*=\s*(\d+)"
)
EVENT_REFERENCE_PATTERN = re.compile(r"AnalyticsEventName\.(\w+)")
PARAM_REFERENCE_PATTERN = re.compile(r"AnalyticsParamKey\.(\w+)")
USER_PROPERTY_REFERENCE_PATTERN = re.compile(r"AnalyticsUserPropertyKey\.(\w+)")
RAW_EVENT_PATTERN = re.compile(r'logEvent\(\s*"([A-Za-z][A-Za-z0-9_]*)"')
RAW_PARAM_PATTERN = re.compile(
    r'put(?:String|Long|Double|Int|Float|Boolean)\(\s*"([A-Za-z][A-Za-z0-9_]*)"'
)
PARAM_TYPE_PATTERN = re.compile(
    r"put(String|Long|Double|Int|Float|Boolean)\(\s*AnalyticsParamKey\.(\w+)"
)

TYPE_MAP = {
    "String": "string",
    "Long": "integer",
    "Int": "integer",
    "Double": "number",
    "Float": "number",
    "Boolean": "boolean",
}
NUMERIC_TYPES = {"integer", "number"}


def load_json(path: Path) -> Dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Analytics governance config must be a JSON object")
    return value


def parse_schema_version(text: str) -> int:
    match = SCHEMA_VERSION_PATTERN.search(text)
    if match is None:
        raise ValueError("Missing ANALYTICS_SCHEMA_VERSION constant")
    return int(match.group(1))


def parse_object_constants(text: str, object_name: str) -> Dict[str, str]:
    match = re.search(
        r"object\s+" + re.escape(object_name) + r"\s*\{(.*?)^\}",
        text,
        flags=re.DOTALL | re.MULTILINE,
    )
    if match is None:
        raise ValueError("Missing Kotlin object: " + object_name)
    return dict(CONST_PATTERN.findall(match.group(1)))


def production_kotlin_files(root: Path) -> List[Path]:
    files: List[Path] = []
    for top_level in ("app", "core", "feature"):
        base = root / top_level
        if not base.exists():
            continue
        files.extend(base.glob("**/src/main/**/*.kt"))
    return sorted(path for path in files if "/build/" not in path.as_posix())


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def collect_source_inventory(
    root: Path,
    files: Sequence[Path],
) -> Dict[str, Any]:
    event_references: Set[str] = set()
    param_references: Set[str] = set()
    user_property_references: Set[str] = set()
    param_types: Dict[str, Set[str]] = defaultdict(set)
    raw_events: List[Dict[str, Any]] = []
    raw_params: List[Dict[str, Any]] = []

    for path in files:
        text = path.read_text(encoding="utf-8")
        relative = path.relative_to(root).as_posix()
        event_references.update(EVENT_REFERENCE_PATTERN.findall(text))
        param_references.update(PARAM_REFERENCE_PATTERN.findall(text))
        user_property_references.update(USER_PROPERTY_REFERENCE_PATTERN.findall(text))
        for type_name, symbol in PARAM_TYPE_PATTERN.findall(text):
            param_types[symbol].add(TYPE_MAP[type_name])

        if "logEvent(" not in text:
            continue
        for match in RAW_EVENT_PATTERN.finditer(text):
            raw_events.append(
                {
                    "name": match.group(1),
                    "path": relative,
                    "line": line_number(text, match.start()),
                }
            )
        for match in RAW_PARAM_PATTERN.finditer(text):
            raw_params.append(
                {
                    "name": match.group(1),
                    "path": relative,
                    "line": line_number(text, match.start()),
                }
            )

    return {
        "event_references": event_references,
        "param_references": param_references,
        "user_property_references": user_property_references,
        "param_types": param_types,
        "raw_events": raw_events,
        "raw_params": raw_params,
    }


def duplicate_values(constants: Mapping[str, str]) -> Dict[str, List[str]]:
    symbols_by_value: Dict[str, List[str]] = defaultdict(list)
    for symbol, value in constants.items():
        symbols_by_value[value].append(symbol)
    return {
        value: sorted(symbols)
        for value, symbols in symbols_by_value.items()
        if len(symbols) > 1
    }


def validate_names(
    values: Iterable[str],
    label: str,
    max_length: int,
) -> List[str]:
    errors: List[str] = []
    for value in sorted(values):
        if len(value) > max_length:
            errors.append("%s exceeds %d characters: %s" % (label, max_length, value))
        if NAME_PATTERN.fullmatch(value) is None:
            errors.append("%s has an invalid name: %s" % (label, value))
        if value.lower().startswith(RESERVED_PREFIXES):
            errors.append("%s uses a reserved prefix: %s" % (label, value))
    return errors


def parameter_has_forbidden_token(parameter: str, tokens: Sequence[str]) -> bool:
    normalized = parameter.lower()
    return any(
        normalized == token
        or normalized.startswith(token + "_")
        or normalized.endswith("_" + token)
        or ("_" + token + "_") in normalized
        for token in tokens
    )


def resolve_event_owner(event_name: str, rules: Sequence[Mapping[str, Any]]) -> Optional[str]:
    candidates: List[Tuple[int, str]] = []
    for rule in rules:
        prefix = rule.get("prefix")
        owner = rule.get("owner")
        if isinstance(prefix, str) and isinstance(owner, str) and event_name.startswith(prefix):
            candidates.append((len(prefix), owner))
    if not candidates:
        return None
    return max(candidates, key=lambda item: item[0])[1]


def reverse_constants(constants: Mapping[str, str]) -> Dict[str, str]:
    return {value: symbol for symbol, value in constants.items()}


def validate_contract(
    root: Path,
    config: Mapping[str, Any],
    schema_version: int,
    event_constants: Mapping[str, str],
    param_constants: Mapping[str, str],
    user_property_constants: Mapping[str, str],
    inventory: Mapping[str, Any],
) -> List[str]:
    errors: List[str] = []
    configured_schema_version = config.get("schema_version")
    if configured_schema_version != schema_version:
        errors.append(
            "Analytics schema version mismatch: code=%s config=%s"
            % (schema_version, configured_schema_version)
        )
    errors.extend(validate_names(event_constants.values(), "Event", 40))
    errors.extend(validate_names(param_constants.values(), "Parameter", 40))
    errors.extend(validate_names(user_property_constants.values(), "User property", 24))

    for label, constants in (
        ("event", event_constants),
        ("parameter", param_constants),
        ("user property", user_property_constants),
    ):
        for value, symbols in duplicate_values(constants).items():
            errors.append("Duplicate %s value %s: %s" % (label, value, ", ".join(symbols)))

    for raw in inventory["raw_events"]:
        errors.append(
            "Raw analytics event %s in %s:%s"
            % (raw["name"], raw["path"], raw["line"])
        )
    for raw in inventory["raw_params"]:
        errors.append(
            "Raw analytics parameter %s in %s:%s"
            % (raw["name"], raw["path"], raw["line"])
        )

    for reference in sorted(inventory["event_references"] - set(event_constants)):
        errors.append("Unknown AnalyticsEventName reference: " + reference)
    for reference in sorted(inventory["param_references"] - set(param_constants)):
        errors.append("Unknown AnalyticsParamKey reference: " + reference)
    for reference in sorted(inventory["user_property_references"] - set(user_property_constants)):
        errors.append("Unknown AnalyticsUserPropertyKey reference: " + reference)

    rules = config.get("event_owner_rules", [])
    if not isinstance(rules, list):
        errors.append("event_owner_rules must be a list")
        rules = []
    for event_name in sorted(event_constants.values()):
        if resolve_event_owner(event_name, rules) is None:
            errors.append("Event has no owner rule: " + event_name)

    parameter_by_value = reverse_constants(param_constants)
    event_values = set(event_constants.values())
    required_defaults = config.get("required_default_parameters", [])
    app_source = (root / "app/src/main/java/com/parsfilo/contentapp/App.kt").read_text(encoding="utf-8")
    for parameter in required_defaults:
        if parameter not in parameter_by_value:
            errors.append("Required default parameter is not declared: " + str(parameter))
            continue
        symbol = parameter_by_value[parameter]
        if "AnalyticsParamKey.%s" % symbol not in app_source:
            errors.append("Required default parameter is not initialized in App.kt: " + str(parameter))

    param_types = inventory["param_types"]
    for symbol, types in sorted(param_types.items()):
        if len(types) > 1:
            errors.append(
                "Parameter %s is emitted with conflicting types: %s"
                % (param_constants.get(symbol, symbol), ", ".join(sorted(types)))
            )

    dimensions = config.get("custom_dimensions", [])
    metrics = config.get("custom_metrics", [])
    for definition in list(dimensions) + list(metrics):
        parameter = definition.get("parameter_name") if isinstance(definition, dict) else None
        if not isinstance(parameter, str) or parameter not in parameter_by_value:
            errors.append("GA4 custom definition references unknown parameter: " + str(parameter))
            continue
        symbol = parameter_by_value[parameter]
        types = param_types.get(symbol, set())
        if definition in metrics and not types.intersection(NUMERIC_TYPES):
            errors.append("Custom metric is not emitted as numeric: " + parameter)

    for event_name in config.get("key_events", []):
        if event_name not in event_values:
            errors.append("Key event is not declared in code: " + str(event_name))
    retired = set(config.get("retired_key_events", []))
    overlap = retired.intersection(config.get("key_events", []))
    if overlap:
        errors.append("Events cannot be both key and retired: " + ", ".join(sorted(overlap)))

    pii_policy = config.get("pii_policy", {})
    tokens = pii_policy.get("forbidden_parameter_tokens", []) if isinstance(pii_policy, dict) else []
    for parameter in sorted(param_constants.values()):
        if parameter_has_forbidden_token(parameter, tokens):
            errors.append("PII-like parameter name is forbidden: " + parameter)
    dropped = pii_policy.get("runtime_dropped_parameters", []) if isinstance(pii_policy, dict) else []
    policy_source = (
        root
        / "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/AnalyticsPayloadPolicy.kt"
    ).read_text(encoding="utf-8")
    for parameter in dropped:
        symbol = parameter_by_value.get(parameter)
        if symbol is None or "AnalyticsParamKey.%s" % symbol not in policy_source:
            errors.append("Runtime-dropped parameter is not enforced in payload policy: " + str(parameter))

    return errors


def event_rows(
    event_constants: Mapping[str, str],
    config: Mapping[str, Any],
) -> List[Dict[str, Any]]:
    key_events = set(config.get("key_events", []))
    retired = set(config.get("retired_key_events", []))
    rules = config.get("event_owner_rules", [])
    return [
        {
            "symbol": symbol,
            "name": name,
            "owner": resolve_event_owner(name, rules) or "unowned",
            "key_event": name in key_events,
            "retired_key_event": name in retired,
        }
        for symbol, name in sorted(event_constants.items(), key=lambda item: item[1])
    ]


def parameter_rows(
    param_constants: Mapping[str, str],
    inventory: Mapping[str, Any],
    config: Mapping[str, Any],
) -> List[Dict[str, Any]]:
    dimensions = {item["parameter_name"]: item for item in config.get("custom_dimensions", [])}
    metrics = {item["parameter_name"]: item for item in config.get("custom_metrics", [])}
    rows: List[Dict[str, Any]] = []
    for symbol, name in sorted(param_constants.items(), key=lambda item: item[1]):
        types = sorted(inventory["param_types"].get(symbol, set()))
        rows.append(
            {
                "symbol": symbol,
                "name": name,
                "types": types or ["declared-only"],
                "custom_dimension": name in dimensions,
                "custom_metric": name in metrics,
                "cardinality": dimensions.get(name, {}).get("cardinality", "not-registered"),
            }
        )
    return rows


def render_markdown(report: Mapping[str, Any]) -> str:
    lines: List[str] = [
        "# Analytics Governance",
        "",
        "> Generated by `scripts/ci/validate_analytics_governance.py`; do not edit manually.",
        "",
        "## Contract",
        "",
        "- Schema version: **%s**" % report["schema_version"],
        "- GA4 property: **%s**" % report["ga4_property_id"],
        "- Retention policy: **%s months**" % report["retention_months"],
        "- Declared events: **%d**" % len(report["events"]),
        "- Declared parameters: **%d**" % len(report["parameters"]),
        "- User properties: **%d**" % len(report["user_properties"]),
        "",
        "Event names and parameters are immutable within a schema version. Any rename requires a",
        "schema-version increment, parallel reporting during migration, and an explicit GA4 admin plan.",
        "",
        "## Default event context",
        "",
    ]
    for parameter in report["required_default_parameters"]:
        lines.append("- `%s`" % parameter)

    lines.extend(["", "## Custom dimensions", "", "| Parameter | Display name | Scope | Cardinality |", "|---|---|---|---|"])
    for item in report["custom_dimensions"]:
        lines.append(
            "| `%s` | %s | %s | %s |"
            % (item["parameter_name"], item["display_name"], item["scope"], item["cardinality"])
        )

    lines.extend(["", "## Custom metrics", "", "| Parameter | Display name | Unit |", "|---|---|---|"])
    for item in report["custom_metrics"]:
        lines.append(
            "| `%s` | %s | %s |"
            % (item["parameter_name"], item["display_name"], item["measurement_unit"])
        )

    lines.extend(["", "## Key-event migration", "", "Desired key events:"])
    for name in report["key_events"]:
        lines.append("- `%s`" % name)
    lines.extend(["", "Retire from key-event status:"])
    for name in report["retired_key_events"]:
        lines.append("- `%s`" % name)

    lines.extend(
        [
            "",
            "## Event catalog",
            "",
            "| Event | Owner | Key event |",
            "|---|---|---|",
        ]
    )
    for item in report["events"]:
        lines.append(
            "| `%s` | %s | %s |"
            % (item["name"], item["owner"], "yes" if item["key_event"] else "no")
        )

    lines.extend(
        [
            "",
            "## Parameter catalog",
            "",
            "| Parameter | Types | GA4 registration | Cardinality |",
            "|---|---|---|---|",
        ]
    )
    for item in report["parameters"]:
        registrations: List[str] = []
        if item["custom_dimension"]:
            registrations.append("dimension")
        if item["custom_metric"]:
            registrations.append("metric")
        lines.append(
            "| `%s` | %s | %s | %s |"
            % (
                item["name"],
                ", ".join(item["types"]),
                ", ".join(registrations) or "none",
                item["cardinality"],
            )
        )

    lines.extend(
        [
            "",
            "## Privacy and cardinality rules",
            "",
            "- Email, phone, address, authentication/purchase tokens, advertising/device IDs, and",
            "  precise location parameters are blocked by CI and runtime payload sanitization.",
            "- `error_message` is intentionally dropped at runtime; categorical `error_code`/`error`",
            "  values must be used for reporting.",
            "- String values are normalized and capped at 100 characters; detected email, phone,",
            "  bearer-token, and JWT-like values are redacted.",
            "- `content_id` is high-cardinality and must not be a default dashboard breakdown.",
            "- Ad unit IDs, response IDs, routes, and network names are diagnostic only and are not",
            "  registered as custom dimensions.",
            "",
            "## DebugView and realtime contract test",
            "",
            "1. Install one debug flavor with Analytics DebugView enabled.",
            "2. Resolve consent and verify every event includes `flavor_id`, `app_version`,",
            "   `analytics_schema_version`, `consent_state`, and `subscription_status`.",
            "3. Exercise one content completion, audio completion, push open, rewarded completion,",
            "   purchase sandbox flow, and ad paid/impression flow.",
            "4. Confirm parameter types match this catalog and no raw email, phone, token, URL query,",
            "   or free-text error value appears.",
            "5. Run a GA4 realtime report by `eventName`, `customEvent:flavor_id`,",
            "   `customEvent:consent_state`, and `customEvent:ad_placement`.",
            "6. Compare `ad_paid_event` count/value and `ad_impression` count with the same-day AdMob",
            "   paid-event/impression export; investigate material divergence before release rollout.",
            "7. Compare monetary values only after filtering to the same ISO currency or converting",
            "   both systems to a common currency. `ad_value` is a raw custom metric; the standard",
            "   `value` + `currency` pair is emitted for GA4 revenue-aware reporting.",
            "",
            "## Dashboard contract",
            "",
            "- Acquisition: `first_open`, `session_start`, flavor and app-version breakdowns.",
            "- Engagement: content/audio completion, notification opens, and bounded content IDs.",
            "- Monetization: purchase, rewarded completion, paid-event value, format, and placement.",
            "- Stability: runtime-observability signals joined by flavor and app version; free-text",
            "  stack/error data remains in Crashlytics, never GA4.",
            "",
        ]
    )
    return "\n".join(lines)


def build_report(
    config: Mapping[str, Any],
    event_constants: Mapping[str, str],
    param_constants: Mapping[str, str],
    user_property_constants: Mapping[str, str],
    inventory: Mapping[str, Any],
) -> Dict[str, Any]:
    return {
        "schema_version": config["schema_version"],
        "ga4_property_id": config["ga4_property_id"],
        "retention_months": config["retention_months"],
        "required_default_parameters": config["required_default_parameters"],
        "custom_dimensions": config["custom_dimensions"],
        "custom_metrics": config["custom_metrics"],
        "key_events": config["key_events"],
        "retired_key_events": config["retired_key_events"],
        "events": event_rows(event_constants, config),
        "parameters": parameter_rows(param_constants, inventory, config),
        "user_properties": [
            {"symbol": symbol, "name": name}
            for symbol, name in sorted(user_property_constants.items(), key=lambda item: item[1])
        ],
    }
