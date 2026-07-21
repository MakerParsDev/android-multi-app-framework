#!/usr/bin/env python3
"""Validate the repository dependency lifecycle policy."""

from __future__ import annotations

import json
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple


def load_policy(path: Path) -> Dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Dependency policy root must be a JSON object")
    return value


def parse_expiry(raw: Any, subject: str, errors: List[str]) -> Optional[date]:
    if not isinstance(raw, str) or not raw.strip():
        errors.append(f"{subject} must define non-empty expires_on")
        return None
    try:
        return datetime.strptime(raw, "%Y-%m-%d").date()
    except ValueError:
        errors.append(f"{subject} expires_on must use YYYY-MM-DD: {raw}")
        return None


def validate_allowlist_entry(
    raw_entry: Any,
    subject: str,
    identity_key: str,
    today: date,
    errors: List[str],
) -> Optional[str]:
    if not isinstance(raw_entry, dict):
        errors.append(f"{subject} must be an object")
        return None
    identity = raw_entry.get(identity_key)
    if not isinstance(identity, str) or not identity.strip():
        errors.append(f"{subject} must define non-empty {identity_key}")
        return None
    owner = raw_entry.get("owner")
    reason = raw_entry.get("reason")
    if not isinstance(owner, str) or not owner.strip():
        errors.append(f"{subject} must define non-empty owner")
    if not isinstance(reason, str) or len(reason.strip()) < 12:
        errors.append(f"{subject} must define a meaningful reason")
    expiry = parse_expiry(raw_entry.get("expires_on"), subject, errors)
    normalized = identity.strip()
    if expiry is not None and expiry < today:
        errors.append(f"{subject} expired on {expiry.isoformat()}: {normalized}")
    return normalized


def validate_allowlist_entries(
    entries: Any,
    subject_key: str,
    identity_key: str,
    today: date,
    errors: List[str],
) -> Set[str]:
    identities: Set[str] = set()
    if not isinstance(entries, list):
        errors.append(f"Policy key '{subject_key}' must be a list")
        return identities
    for index, raw_entry in enumerate(entries):
        subject = f"{subject_key}[{index}]"
        identity = validate_allowlist_entry(raw_entry, subject, identity_key, today, errors)
        if identity is None:
            continue
        if identity in identities:
            errors.append(f"Duplicate {subject_key} identity: {identity}")
        identities.add(identity)
    return identities


def validate_policy(policy: Dict[str, Any], today: date) -> Tuple[Dict[str, Set[str]], List[str]]:
    errors: List[str] = []
    if policy.get("schema_version") != 1:
        errors.append("dependency-policy.json schema_version must be 1")
    if policy.get("stable_only") is not True:
        errors.append("dependency-policy.json stable_only must be true")
    cadence = policy.get("review_cadence_days")
    if not isinstance(cadence, int) or isinstance(cadence, bool) or cadence < 1:
        errors.append("review_cadence_days must be a positive integer")
    if policy.get("catalog_inline_version_allowlist", []) != []:
        errors.append("catalog_inline_version_allowlist must remain empty; inline versions are forbidden")
    prerelease_aliases = validate_allowlist_entries(
        policy.get("catalog_prerelease_allowlist", []),
        "catalog_prerelease_allowlist",
        "alias",
        today,
        errors,
    )
    transitive_coordinates = validate_allowlist_entries(
        policy.get("transitive_prerelease_allowlist", []),
        "transitive_prerelease_allowlist",
        "coordinate",
        today,
        errors,
    )
    invalid_coordinates = {
        coordinate
        for coordinate in transitive_coordinates
        if coordinate == "*:*" or coordinate.count(":") != 1
    }
    errors.extend(
        f"Invalid or overly broad transitive allowlist coordinate: {coordinate}"
        for coordinate in sorted(invalid_coordinates)
    )
    return {
        "inline_aliases": set(),
        "prerelease_aliases": prerelease_aliases,
        "transitive_coordinates": transitive_coordinates,
    }, errors
