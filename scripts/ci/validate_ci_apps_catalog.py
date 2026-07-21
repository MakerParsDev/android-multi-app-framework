#!/usr/bin/env python3
"""
Validate FlavorConfig.kt <-> .ci/apps.json consistency for CI metadata (AdMob catalog).

Purpose:
- Catch missing flavor metadata before publish/build steps.
- Provide warning-only mode for visibility and strict mode for fail-fast.

Exit codes:
- 0 in warn mode (always), unless script/runtime error occurs
- non-zero in strict mode when relevant validation errors are found
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
from dataclasses import dataclass
from typing import Any


NAMED_ARG_PATTERN = re.compile(r'(?P<key>\w+)\s*=\s*"(?P<value>[^"]+)"')
POSITIONAL_PATTERN = re.compile(r'"(?P<name>[^"]+)"\s*,\s*"[^"]*"\s*,\s*"(?P<pkg>[^"]+)"')

REQUIRED_AD_UNITS = ("banner", "interstitial", "native", "rewarded", "open_app")
OPTIONAL_AD_UNITS = ("rewarded_interstitial",)


@dataclass(frozen=True)
class FlavorInfo:
    name: str
    package_name: str


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Validate .ci/apps.json against FlavorConfig.kt")
    p.add_argument(
        "--mode",
        choices=("warn", "strict"),
        default="warn",
        help="warn: log warnings and exit 0, strict: fail on relevant issues (default: warn)",
    )
    p.add_argument(
        "--target-flavors",
        default="all",
        help="Comma-separated flavor list or 'all'. In strict mode, validation errors for these flavors fail CI.",
    )
    p.add_argument(
        "--flavor-config",
        default="buildSrc/src/main/kotlin/FlavorConfig.kt",
        help="Path to FlavorConfig.kt",
    )
    p.add_argument(
        "--apps-json",
        default=".ci/apps.json",
        help="Path to CI apps catalog JSON",
    )
    p.add_argument(
        "--allowlist-json",
        default=".ci/apps-catalog-allowlist.json",
        help="Optional allowlist JSON for intentionally missing flavor catalog entries",
    )
    return p.parse_args()


def gh_or_local_log(level: str, message: str) -> None:
    level = level.lower()
    if "GITHUB_ACTIONS" in os_environ():
        print(f"::{level}::{message}")
        return
    prefix = "ERROR" if level == "error" else "WARNING"
    print(f"{prefix}: {message}")


def os_environ() -> dict[str, str]:
    # Small wrapper to ease testing/mocking if needed later.
    import os

    return os.environ


def read_flavors(path: pathlib.Path) -> dict[str, FlavorInfo]:
    text = path.read_text(encoding="utf-8")
    result: dict[str, FlavorInfo] = {}
    for block in extract_flavor_blocks(text):
        flavor = parse_flavor_block(block)
        if flavor is None:
            continue
        result[flavor.name] = flavor
    if not result:
        raise RuntimeError(f"No flavors parsed from {path}")
    return result


def extract_flavor_blocks(text: str) -> list[str]:
    blocks: list[str] = []
    marker = "FlavorConfig("
    search_from = 0

    while True:
        start = text.find(marker, search_from)
        if start == -1:
            break

        index = start + len("FlavorConfig")
        depth = 0
        in_string = False
        escaped = False

        while index < len(text):
            char = text[index]

            if in_string:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
            else:
                if char == '"':
                    in_string = True
                elif char == "(":
                    depth += 1
                elif char == ")":
                    depth -= 1
                    if depth == 0:
                        blocks.append(text[start : index + 1])
                        search_from = index + 1
                        break
            index += 1
        else:
            break

    return blocks


def parse_flavor_block(block: str) -> FlavorInfo | None:
    named_values = {
        match.group("key").strip(): match.group("value").strip()
        for match in NAMED_ARG_PATTERN.finditer(block)
    }

    if named_values.get("name") and named_values.get("packageName"):
        return FlavorInfo(
            name=named_values["name"],
            package_name=named_values["packageName"],
        )

    positional_match = POSITIONAL_PATTERN.search(block)
    if positional_match:
        return FlavorInfo(
            name=positional_match.group("name").strip(),
            package_name=positional_match.group("pkg").strip(),
        )

    return None


def read_apps(path: pathlib.Path) -> list[dict[str, Any]]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise RuntimeError(f"{path} must contain a JSON array")
    return raw


def read_allowlisted_missing_flavors(path: pathlib.Path) -> dict[str, str]:
    if not path.exists():
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(raw, dict):
        mf = raw.get("missing_flavors")
        if isinstance(mf, dict):
            return {str(k): str(v) for k, v in mf.items()}
        arr = raw.get("allow_missing_flavors")
        if isinstance(arr, list):
            return {str(item): "" for item in arr if isinstance(item, str)}
    if isinstance(raw, list):
        return {str(item): "" for item in raw if isinstance(item, str)}
    raise RuntimeError(f"{path} must be an object/list if present")


def normalize_target_flavors(raw: str, all_flavors: set[str]) -> set[str]:
    value = (raw or "all").strip()
    if not value or value.lower() == "all":
        return set(all_flavors)
    names = {x.strip() for x in value.split(",") if x.strip()}
    unknown = sorted(names - all_flavors)
    if unknown:
        raise RuntimeError(f"Unknown target flavors: {', '.join(unknown)}")
    return names


def validate_schema_entry(entry: Any, idx: int) -> list[str]:
    errors: list[str] = []
    if not isinstance(entry, dict):
        return [f"apps[{idx}] is not an object"]

    def require_string(key: str) -> None:
        v = entry.get(key)
        if not isinstance(v, str) or not v.strip():
            errors.append(f"apps[{idx}].{key} must be a non-empty string")

    require_string("flavor")
    require_string("package")
    require_string("admob_app_id")

    ad_units = entry.get("ad_units")
    if not isinstance(ad_units, dict):
        errors.append(f"apps[{idx}].ad_units must be an object")
    else:
        for key in REQUIRED_AD_UNITS:
            v = ad_units.get(key)
            if not isinstance(v, str) or not v.strip():
                errors.append(f"apps[{idx}].ad_units.{key} must be a non-empty string")
        for key in OPTIONAL_AD_UNITS:
            if key not in ad_units:
                continue
            v = ad_units.get(key)
            if not isinstance(v, str) or not v.strip():
                errors.append(
                    f"apps[{idx}].ad_units.{key} must be a non-empty string when provided"
                )
    return errors


def main() -> int:
    args = parse_args()
    repo_root = pathlib.Path(__file__).resolve().parents[2]
    flavor_path = (repo_root / args.flavor_config).resolve()
    apps_path = (repo_root / args.apps_json).resolve()
    allowlist_path = (repo_root / args.allowlist_json).resolve()

    flavors = read_flavors(flavor_path)
    apps = read_apps(apps_path)
    allowlisted_missing = read_allowlisted_missing_flavors(allowlist_path)
    target_flavors = normalize_target_flavors(args.target_flavors, set(flavors))

    schema_errors: list[str] = []
    for idx, entry in enumerate(apps):
        schema_errors.extend(validate_schema_entry(entry, idx))

    flavor_seen: dict[str, dict[str, Any]] = {}
    duplicates: list[str] = []
    package_duplicates: dict[str, list[str]] = {}

    for idx, entry in enumerate(apps):
        if not isinstance(entry, dict):
            continue
        flavor = str(entry.get("flavor") or "").strip()
        pkg = str(entry.get("package") or "").strip()
        if flavor:
            if flavor in flavor_seen:
                duplicates.append(flavor)
            else:
                flavor_seen[flavor] = entry
        if pkg:
            package_duplicates.setdefault(pkg, []).append(flavor or f"<idx:{idx}>")

    extra_flavors = sorted(set(flavor_seen) - set(flavors))
    missing_flavors = sorted(set(flavors) - set(flavor_seen))
    missing_allowlisted = sorted(f for f in missing_flavors if f in allowlisted_missing)
    missing_non_allowlisted = sorted(f for f in missing_flavors if f not in allowlisted_missing)

    package_mismatches: list[str] = []
    for flavor_name, fi in flavors.items():
        entry = flavor_seen.get(flavor_name)
        if entry is None:
            continue
        pkg = str(entry.get("package") or "").strip()
        if pkg != fi.package_name:
            package_mismatches.append(
                f"{flavor_name}: FlavorConfig={fi.package_name}, apps.json={pkg or '<missing>'}"
            )

    package_collision_errors: list[str] = []
    for pkg, mapped_flavors in sorted(package_duplicates.items()):
        if len(mapped_flavors) > 1:
            package_collision_errors.append(f"{pkg}: {', '.join(sorted(mapped_flavors))}")

    warnings: list[str] = []
    errors: list[str] = []

    for msg in schema_errors:
        errors.append(msg)
    for flavor in sorted(set(duplicates)):
        errors.append(f"Duplicate apps.json flavor entry: {flavor}")
    for msg in package_collision_errors:
        errors.append(f"Duplicate package mapping in apps.json -> {msg}")
    for flavor in extra_flavors:
        warnings.append(f"apps.json contains flavor not found in FlavorConfig: {flavor}")
    for flavor in missing_non_allowlisted:
        warnings.append(f"FlavorConfig flavor missing from apps.json: {flavor}")
    for flavor in missing_allowlisted:
        reason = allowlisted_missing.get(flavor, "").strip()
        suffix = f" (allowlisted: {reason})" if reason else " (allowlisted)"
        warnings.append(f"FlavorConfig flavor missing from apps.json: {flavor}{suffix}")
    for msg in package_mismatches:
        errors.append(f"Package mismatch: {msg}")

    print("CI apps catalog validation summary")
    print(f"- FlavorConfig flavors: {len(flavors)}")
    print(f"- .ci/apps.json entries: {len(apps)}")
    print(f"- Missing in apps.json: {len(missing_flavors)}")
    print(f"- Missing in apps.json (allowlisted): {len(missing_allowlisted)}")
    print(f"- Missing in apps.json (non-allowlisted): {len(missing_non_allowlisted)}")
    print(f"- Extra in apps.json: {len(extra_flavors)}")
    print(f"- Schema errors: {len(schema_errors)}")
    print(f"- Package mismatches: {len(package_mismatches)}")
    if missing_flavors:
        print(f"  missing -> {', '.join(missing_flavors)}")
    if extra_flavors:
        print(f"  extra -> {', '.join(extra_flavors)}")
    print("")

    for msg in warnings:
        gh_or_local_log("warning", msg)

    strict_errors: list[str] = []
    if args.mode == "strict":
        # Missing target flavors is an error in strict mode.
        for flavor in sorted(target_flavors):
            if flavor not in flavor_seen:
                reason = allowlisted_missing.get(flavor, "").strip()
                suffix = f" (allowlisted: {reason})" if reason else ""
                strict_errors.append(f"Target flavor missing from apps.json: {flavor}{suffix}")

        # Package mismatch and schema problems are relevant for targeted flavors (or all in strict/all).
        for idx, entry in enumerate(apps):
            if not isinstance(entry, dict):
                continue
            flavor = str(entry.get("flavor") or "").strip()
            if flavor and flavor not in target_flavors:
                continue
            for e in validate_schema_entry(entry, idx):
                strict_errors.append(e)
        for msg in package_mismatches:
            flavor = msg.split(":", 1)[0]
            if flavor in target_flavors:
                strict_errors.append(f"Package mismatch: {msg}")
        for d in sorted(set(duplicates)):
            if d in target_flavors:
                strict_errors.append(f"Duplicate apps.json flavor entry: {d}")
        # Extra flavors are warnings only, even in strict mode.

    # De-duplicate while preserving order.
    seen: set[str] = set()
    strict_errors = [e for e in strict_errors if not (e in seen or seen.add(e))]

    for msg in strict_errors:
        gh_or_local_log("error", msg)

    if args.mode == "strict" and strict_errors:
        print("")
        print(f"Strict validation failed with {len(strict_errors)} error(s).")
        return 1

    print("Validation completed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
