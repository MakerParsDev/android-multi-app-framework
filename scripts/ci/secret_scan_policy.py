#!/usr/bin/env python3
"""Validate the pinned secret scanner and owned historical exceptions."""

from __future__ import annotations

import json
import re
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
FINGERPRINT_RE = re.compile(r"^[0-9a-f]{40,64}:.+:[A-Za-z0-9_.-]+:\d+$")


def load_policy(path: Path) -> Dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Secret scan policy root must be a JSON object")
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


def validate_tool(raw: Any, errors: List[str]) -> Dict[str, str]:
    if not isinstance(raw, dict):
        errors.append("gitleaks must be an object")
        return {}
    required = ("version", "asset", "download_url", "sha256")
    tool: Dict[str, str] = {}
    for key in required:
        value = raw.get(key)
        if not isinstance(value, str) or not value.strip():
            errors.append(f"gitleaks.{key} must be a non-empty string")
        else:
            tool[key] = value.strip()
    if "sha256" in tool and not SHA256_RE.fullmatch(tool["sha256"]):
        errors.append("gitleaks.sha256 must be a lowercase SHA-256 digest")
    if tool.get("version") and tool.get("asset"):
        expected = f"gitleaks_{tool['version']}_linux_x64.tar.gz"
        if tool["asset"] != expected:
            errors.append(f"gitleaks.asset must equal {expected}")
    if tool.get("asset") and tool.get("download_url"):
        if not tool["download_url"].endswith("/" + tool["asset"]):
            errors.append("gitleaks.download_url must end with the pinned asset name")
        if not tool["download_url"].startswith(
            "https://github.com/gitleaks/gitleaks/releases/download/"
        ):
            errors.append("gitleaks.download_url must use the official GitHub release origin")
    return tool


def validate_baseline_entry(
    raw: Any,
    index: int,
    today: date,
    errors: List[str],
) -> Optional[Dict[str, str]]:
    subject = f"baseline[{index}]"
    if not isinstance(raw, dict):
        errors.append(f"{subject} must be an object")
        return None
    required = ("fingerprint", "owner", "reason", "expires_on")
    entry: Dict[str, str] = {}
    for key in required:
        value = raw.get(key)
        if not isinstance(value, str) or not value.strip():
            errors.append(f"{subject} must define non-empty {key}")
        else:
            entry[key] = value.strip()
    if len(entry) != len(required):
        return None
    if not FINGERPRINT_RE.fullmatch(entry["fingerprint"]):
        errors.append(f"{subject}.fingerprint has an invalid Gitleaks fingerprint format")
    if len(entry["reason"]) < 24:
        errors.append(f"{subject}.reason must explain why the historical finding is retained")
    expiry = parse_expiry(entry["expires_on"], subject, errors)
    if expiry is not None and expiry < today:
        errors.append(f"{subject} expired on {expiry.isoformat()}: {entry['fingerprint']}")
    return entry


def validate_policy(
    policy: Dict[str, Any],
    today: date,
) -> Tuple[Dict[str, str], List[Dict[str, str]], List[str]]:
    errors: List[str] = []
    if policy.get("schema_version") != 1:
        errors.append("secret-scan-policy.json schema_version must be 1")
    tool = validate_tool(policy.get("gitleaks"), errors)
    raw_baseline = policy.get("baseline")
    if not isinstance(raw_baseline, list):
        errors.append("baseline must be a list")
        raw_baseline = []
    baseline: List[Dict[str, str]] = []
    fingerprints = set()
    for index, raw_entry in enumerate(raw_baseline):
        entry = validate_baseline_entry(raw_entry, index, today, errors)
        if entry is None:
            continue
        fingerprint = entry["fingerprint"]
        if fingerprint in fingerprints:
            errors.append(f"Duplicate baseline fingerprint: {fingerprint}")
        fingerprints.add(fingerprint)
        baseline.append(entry)
    return tool, baseline, errors


def render_gitleaksignore(entries: Sequence[Dict[str, str]]) -> str:
    lines = [
        "# Generated from config/secret-scan-policy.json.",
        "# Do not add unowned fingerprints directly to this file.",
    ]
    lines.extend(entry["fingerprint"] for entry in sorted(entries, key=lambda item: item["fingerprint"]))
    return "\n".join(lines) + "\n"


def validate_ignore_file(entries: Sequence[Dict[str, str]], ignore_path: Path) -> List[str]:
    expected = render_gitleaksignore(entries)
    if not ignore_path.exists():
        return [f"Missing generated Gitleaks ignore file: {ignore_path}"]
    actual = ignore_path.read_text(encoding="utf-8")
    if actual != expected:
        return [
            f"Stale Gitleaks ignore file: {ignore_path}. Regenerate it from secret-scan-policy.json."
        ]
    return []
