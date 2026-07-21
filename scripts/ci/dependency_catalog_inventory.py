#!/usr/bin/env python3
"""Build and validate a deterministic dependency catalog inventory."""

from __future__ import annotations

from typing import Any, Dict, List, Set, Tuple

from dependency_catalog_parser import (
    is_dynamic_version,
    is_prerelease,
    library_coordinate,
    resolved_version,
)


def record_reference(
    alias: str,
    source: str,
    used_version_keys: Set[str],
    version_key_aliases: Dict[str, Set[str]],
) -> None:
    if not source.startswith("ref:"):
        return
    version_key = source.split(":", 1)[1]
    used_version_keys.add(version_key)
    version_key_aliases.setdefault(version_key, set()).add(alias)


def build_library_entries(
    raw_entries: Dict[str, Dict[str, str]],
    versions: Dict[str, str],
    used_version_keys: Set[str],
    version_key_aliases: Dict[str, Set[str]],
    errors: List[str],
) -> List[Dict[str, Any]]:
    entries: List[Dict[str, Any]] = []
    for alias, fields in sorted(raw_entries.items()):
        coordinate = library_coordinate(fields)
        if not coordinate:
            errors.append(f"Library alias '{alias}' must define module or group/name")
        version, source = resolved_version(alias, fields, versions, errors)
        record_reference(alias, source, used_version_keys, version_key_aliases)
        entries.append(
            {"alias": alias, "coordinate": coordinate, "version": version, "versionSource": source}
        )
    return entries


def build_plugin_entries(
    raw_entries: Dict[str, Dict[str, str]],
    versions: Dict[str, str],
    used_version_keys: Set[str],
    version_key_aliases: Dict[str, Set[str]],
    errors: List[str],
) -> List[Dict[str, Any]]:
    entries: List[Dict[str, Any]] = []
    for alias, fields in sorted(raw_entries.items()):
        plugin_id = fields.get("id", "").strip()
        if not plugin_id:
            errors.append(f"Plugin alias '{alias}' must define id")
        version, source = resolved_version(alias, fields, versions, errors)
        record_reference(alias, source, used_version_keys, version_key_aliases)
        entries.append({"alias": alias, "id": plugin_id, "version": version, "versionSource": source})
    return entries


def validate_entries(
    entries: List[Dict[str, Any]],
    prerelease_aliases: Set[str],
) -> List[str]:
    errors: List[str] = []
    for entry in entries:
        alias = str(entry["alias"])
        version = entry.get("version")
        if entry["versionSource"] == "inline":
            errors.append(f"Inline version is not allowlisted for alias '{alias}'")
        if not isinstance(version, str):
            continue
        if is_dynamic_version(version):
            errors.append(f"Dynamic/range version is forbidden for alias '{alias}': {version}")
        if is_prerelease(version) and alias not in prerelease_aliases:
            errors.append(f"Pre-release version is not allowlisted for alias '{alias}': {version}")
    return errors


def validate_version_keys(
    versions: Dict[str, str],
    version_key_aliases: Dict[str, Set[str]],
) -> List[str]:
    errors: List[str] = []
    for key, version in sorted(versions.items()):
        if is_dynamic_version(version):
            errors.append(f"Dynamic/range version is forbidden for version key '{key}': {version}")
        if is_prerelease(version) and not version_key_aliases.get(key):
            errors.append(
                f"Unused pre-release version key is forbidden because no alias can own its exception: '{key}'={version}"
            )
    return errors


def build_inventory(
    catalog: Dict[str, Dict[str, Any]],
    allowlists: Dict[str, Set[str]],
) -> Tuple[Dict[str, Any], List[str]]:
    versions: Dict[str, str] = catalog["versions"]
    errors: List[str] = []
    used_version_keys: Set[str] = set()
    version_key_aliases: Dict[str, Set[str]] = {}
    libraries = build_library_entries(
        catalog["libraries"], versions, used_version_keys, version_key_aliases, errors
    )
    plugins = build_plugin_entries(
        catalog["plugins"], versions, used_version_keys, version_key_aliases, errors
    )
    all_entries = libraries + plugins
    errors.extend(validate_entries(all_entries, allowlists["prerelease_aliases"]))
    errors.extend(validate_version_keys(versions, version_key_aliases))
    unused_version_keys = sorted(set(versions) - used_version_keys)
    inventory = {
        "summary": {
            "versionKeys": len(versions),
            "libraries": len(libraries),
            "plugins": len(plugins),
            "unusedVersionKeys": len(unused_version_keys),
            "inlineVersions": sum(entry["versionSource"] == "inline" for entry in all_entries),
            "catalogPrereleases": sum(
                isinstance(entry.get("version"), str) and is_prerelease(str(entry["version"]))
                for entry in all_entries
            ),
            "transitivePrereleaseAllowlist": len(allowlists["transitive_coordinates"]),
        },
        "versions": dict(sorted(versions.items())),
        "unusedVersionKeys": unused_version_keys,
        "libraries": libraries,
        "plugins": plugins,
        "transitivePrereleaseAllowlist": sorted(allowlists["transitive_coordinates"]),
    }
    return inventory, errors
