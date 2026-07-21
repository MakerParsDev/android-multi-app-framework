#!/usr/bin/env python3
"""Validate owned Gradle, D8, and ASM warning policy entries."""

from __future__ import annotations

import json
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

REQUIRED_FIELDS = ("id", "category", "match", "owner", "source", "reason", "expires_on")
SUPPORTED_CATEGORIES = {"gradle-deprecation", "d8", "asm-unresolved-classes"}


def load_policy(path: Path) -> Dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Gradle warning policy root must be a JSON object")
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


def normalize_entry(raw_entry: Any, subject: str, errors: List[str]) -> Optional[Dict[str, str]]:
    if not isinstance(raw_entry, dict):
        errors.append(f"{subject} must be an object")
        return None
    entry: Dict[str, str] = {}
    for key in REQUIRED_FIELDS:
        value = raw_entry.get(key)
        if not isinstance(value, str) or not value.strip():
            errors.append(f"{subject} must define non-empty {key}")
        else:
            entry[key] = value.strip()
    return entry if len(entry) == len(REQUIRED_FIELDS) else None


def validate_entry(entry: Dict[str, str], subject: str, today: date, errors: List[str]) -> None:
    if entry["category"] not in SUPPORTED_CATEGORIES:
        errors.append(f"Unsupported warning category for {entry['id']}: {entry['category']}")
    if len(entry["reason"]) < 24:
        errors.append(f"Warning policy reason is too short for {entry['id']}")
    expiry = parse_expiry(entry["expires_on"], subject, errors)
    if expiry is not None and expiry < today:
        errors.append(f"Warning policy entry expired on {expiry.isoformat()}: {entry['id']}")


def record_unique(value: str, seen: Set[str], label: str, errors: List[str]) -> None:
    if value in seen:
        errors.append(f"Duplicate warning policy {label}: {value}")
    seen.add(value)


def validate_policy(policy: Dict[str, Any], today: date) -> Tuple[List[Dict[str, str]], List[str]]:
    errors: List[str] = []
    if policy.get("schema_version") != 1:
        errors.append("gradle-warning-policy.json schema_version must be 1")
    raw_entries = policy.get("known_warnings")
    if not isinstance(raw_entries, list):
        return [], errors + ["gradle-warning-policy.json known_warnings must be a list"]
    entries: List[Dict[str, str]] = []
    ids: Set[str] = set()
    matches: Set[str] = set()
    for index, raw_entry in enumerate(raw_entries):
        subject = f"known_warnings[{index}]"
        entry = normalize_entry(raw_entry, subject, errors)
        if entry is None:
            continue
        record_unique(entry["id"], ids, "id", errors)
        record_unique(entry["match"], matches, "match", errors)
        validate_entry(entry, subject, today, errors)
        entries.append(entry)
    return entries, errors
