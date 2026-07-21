#!/usr/bin/env python3
"""Shared types and low-level helpers for AdMob inventory governance."""

from __future__ import annotations

import html
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

APP_ID_PATTERN = re.compile(r"^ca-app-pub-(\d{16})~(\d{10})$")
AD_UNIT_ID_PATTERN = re.compile(r"^ca-app-pub-(\d{16})/(\d{10})$")
PACKAGE_PATTERN = re.compile(r"^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$")
STRING_TAG_PATTERN = re.compile(r"<string\b(?P<attrs>[^>]*)>(?P<value>.*?)</string>", re.DOTALL)
NAME_ATTRIBUTE_PATTERN = re.compile(
    r"\bname\s*=\s*[\"\'](?P<name>\w+)[\"\']",
    re.ASCII,
)

PRIMARY_AD_UNITS = {
    "banner": ("ad_unit_banner", "BANNER"),
    "interstitial": ("ad_unit_interstitial", "INTERSTITIAL"),
    "native": ("ad_unit_native", "NATIVE"),
    "rewarded": ("ad_unit_rewarded", "REWARDED"),
    "open_app": ("ad_unit_open_app", "APP_OPEN"),
    "rewarded_interstitial": (
        "ad_unit_rewarded_interstitial",
        "REWARDED_INTERSTITIAL",
    ),
}
TRAFFIC_METRICS = (
    "ad_requests",
    "matched_requests",
    "impressions",
    "clicks",
    "estimated_earnings_micros",
)
CLEANUP_ACTIONS = {"archive_or_remove_after_console_confirmation"}
MANUAL_STATUSES = {"pending", "completed"}
GOOGLE_TEST_PREFIX = "ca-app-pub-3940256099942544"
OBSOLETE_PLACEMENTS = (
    "ad_unit_interstitial_audio_stop",
    "ad_unit_interstitial_content_mode_switch",
)
ALIAS_RESOURCE_UNITS = {
    "ad_unit_banner_home": "banner",
    "ad_unit_banner_settings": "banner",
    "ad_unit_banner_content_list": "banner",
    "ad_unit_banner_content_detail": "banner",
    "ad_unit_banner_qibla": "banner",
    "ad_unit_banner_zikir": "banner",
    "ad_unit_native_feed_home": "native",
    "ad_unit_native_feed_content": "native",
    "ad_unit_native_feed_zikir": "native",
    "ad_unit_interstitial_nav_break": "interstitial",
    "ad_unit_open_app_resume": "open_app",
    "ad_unit_rewarded_rewards_screen": "rewarded",
    "ad_unit_rewarded_interstitial_history_unlock": "rewarded_interstitial",
}


@dataclass
class ValidationContext:
    root: Path
    publisher_id: str
    errors: list[str]
    warnings: list[str]


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def parse_ads_xml(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    if "<!DOCTYPE" in text.upper() or "<!ENTITY" in text.upper():
        raise ValueError("DOCTYPE and ENTITY declarations are not allowed")
    if not re.search(r"<resources(?:\s[^>]*)?>", text) or "</resources>" not in text:
        raise ValueError("Expected an Android <resources> root")

    values: dict[str, str] = {}
    for match in STRING_TAG_PATTERN.finditer(text):
        name_match = NAME_ATTRIBUTE_PATTERN.search(match.group("attrs"))
        if not name_match:
            raise ValueError("String resource is missing a valid name attribute")
        raw_value = match.group("value").strip()
        if "<" in raw_value or ">" in raw_value:
            raise ValueError("Nested XML is not allowed in AdMob string resources")
        name = name_match.group("name")
        if name in values:
            raise ValueError(f"Duplicate string resource: {name}")
        values[name] = html.unescape(raw_value)

    declared_strings = len(re.findall(r"<string\b", text))
    if declared_strings != len(values):
        raise ValueError("Unsupported or malformed string resource markup")
    return values


def normalize_targets(raw: str, known: set[str], errors: list[str]) -> list[str]:
    if raw == "all":
        return sorted(known)
    targets = [item.strip() for item in raw.split(",") if item.strip()]
    unknown = sorted(set(targets) - known)
    if unknown:
        errors.append("Unknown target flavors: " + ", ".join(unknown))
    return targets


def validate_debug_ads(context: ValidationContext) -> None:
    path = context.root / "app" / "src" / "debug" / "res" / "values" / "ads.xml"
    if not path.exists():
        context.errors.append(f"Missing debug ads.xml: {path.relative_to(context.root)}")
        return
    try:
        resources = parse_ads_xml(path)
    except (OSError, ValueError) as exc:
        context.errors.append(f"Failed to parse debug ads.xml: {exc}")
        return

    required = {"admob_app_id"}
    required.update(resource_name for resource_name, _ in PRIMARY_AD_UNITS.values())
    for key in sorted(required):
        value = resources.get(key, "")
        if not value:
            context.errors.append(f"debug ads.xml missing {key}")
        elif not value.startswith(GOOGLE_TEST_PREFIX):
            context.errors.append(f"debug ads.xml {key} is not a Google test ID")

    for alias, unit_name in ALIAS_RESOURCE_UNITS.items():
        primary_resource = PRIMARY_AD_UNITS[unit_name][0]
        if resources.get(alias) != resources.get(primary_resource):
            context.errors.append(
                f"debug ads.xml {alias} does not match {primary_resource}"
            )
    for obsolete in OBSOLETE_PLACEMENTS:
        if obsolete in resources:
            context.warnings.append(
                f"debug ads.xml still defines obsolete placement {obsolete}"
            )


def validate_app_id(value: Any, field: str, context: ValidationContext) -> None:
    match = APP_ID_PATTERN.fullmatch(str(value))
    if not match:
        context.errors.append(f"{field}: invalid AdMob app ID {value!r}")
        return
    if f"pub-{match.group(1)}" != context.publisher_id:
        context.errors.append(f"{field}: app ID belongs to a different publisher")


def validate_ad_unit_id(value: Any, field: str, context: ValidationContext) -> None:
    match = AD_UNIT_ID_PATTERN.fullmatch(str(value))
    if not match:
        context.errors.append(f"{field}: invalid AdMob ad unit ID {value!r}")
        return
    if f"pub-{match.group(1)}" != context.publisher_id:
        context.errors.append(f"{field}: ad unit belongs to a different publisher")


def duplicate_values(values: list[str]) -> list[str]:
    seen: set[str] = set()
    duplicates: set[str] = set()
    for value in values:
        if value in seen:
            duplicates.add(value)
        seen.add(value)
    return sorted(duplicates)


def append_duplicate_errors(
    values: list[str],
    prefix: str,
    context: ValidationContext,
) -> None:
    for duplicate in duplicate_values(values):
        context.errors.append(f"{prefix}: {duplicate}")


def relationship_label(candidate: Mapping[str, Any]) -> str:
    relationship = candidate.get("relationship", {})
    relation_type = relationship.get("type", "unknown")
    target = relationship.get("target_flavor")
    return f"{relation_type}:{target}" if target else str(relation_type)


def as_mapping(
    value: Any,
    field: str,
    context: ValidationContext,
) -> Mapping[str, Any]:
    if isinstance(value, Mapping):
        return value
    context.errors.append(f"{field} must be an object")
    return {}


def as_list(value: Any, field: str, context: ValidationContext) -> list[Any]:
    if isinstance(value, list):
        return value
    context.errors.append(f"{field} must be an array")
    return []
