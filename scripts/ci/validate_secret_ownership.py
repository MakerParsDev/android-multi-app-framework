#!/usr/bin/env python3
"""Validate secret-store ownership, migration actions, and review deadlines."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Set

PREFIX_RE = re.compile(r"^[A-Z][A-Z0-9_]+_$")
ALLOWED_STORES = {
    "doppler",
    "azure_secure_files_or_variable_group",
    "azure_variable_group",
    "azure_system_access_token_or_oidc",
    "doppler_scoped_api_token",
}


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", type=Path, default=Path("config/secret-ownership.json"))
    parser.add_argument(
        "--json-report",
        type=Path,
        default=Path("build/reports/security/secret-ownership.json"),
    )
    return parser.parse_args(argv)


def load_policy(path: Path) -> Dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Secret ownership policy root must be an object")
    return value


def parse_date(raw: Any) -> Optional[date]:
    if not isinstance(raw, str):
        return None
    try:
        return datetime.strptime(raw, "%Y-%m-%d").date()
    except ValueError:
        return None


def validate_rotation(entry: Dict[str, Any], subject: str) -> List[str]:
    errors: List[str] = []
    if "rotation_days" not in entry and "rotation_mode" not in entry:
        return [f"{subject} must define rotation_days or rotation_mode"]
    rotation_days = entry.get("rotation_days")
    if rotation_days is not None and (
        not isinstance(rotation_days, int) or isinstance(rotation_days, bool) or rotation_days < 1
    ):
        errors.append(f"{subject}.rotation_days must be a positive integer")
    rotation_mode = entry.get("rotation_mode")
    if rotation_mode is not None and (
        not isinstance(rotation_mode, str) or len(rotation_mode.strip()) < 8
    ):
        errors.append(f"{subject}.rotation_mode must be meaningful")
    return errors


def validate_owner_action(entry: Dict[str, Any], subject: str) -> List[str]:
    errors: List[str] = []
    for key in ("owner", "action", "canonical_store"):
        value = entry.get(key)
        if not isinstance(value, str) or not value.strip():
            errors.append(f"{subject}.{key} must be a non-empty string")
    store = entry.get("canonical_store")
    if isinstance(store, str) and store not in ALLOWED_STORES:
        errors.append(f"{subject}.canonical_store is unsupported: {store}")
    errors.extend(validate_rotation(entry, subject))
    return errors


def validate_inventory(inventory: Any, today: date) -> List[str]:
    if not isinstance(inventory, dict):
        return ["legacy_github_inventory must be an object"]
    errors: List[str] = []
    count = inventory.get("expected_name_count")
    if not isinstance(count, int) or isinstance(count, bool) or count < 1:
        errors.append("legacy_github_inventory.expected_name_count must be positive")
    if inventory.get("status") != "legacy_mirror_remove_after_migration":
        errors.append("legacy GitHub inventory must remain marked for removal after migration")
    for key in ("owner", "evidence"):
        value = inventory.get(key)
        if not isinstance(value, str) or not value.strip():
            errors.append(f"legacy_github_inventory.{key} must be non-empty")
    due = parse_date(inventory.get("review_due_on"))
    if due is None:
        errors.append("legacy_github_inventory.review_due_on must use YYYY-MM-DD")
    elif due < today:
        errors.append(f"legacy GitHub secret migration review expired on {due.isoformat()}")
    return errors


def validate_rule(
    raw_rule: Any,
    index: int,
    ids: Set[str],
    prefixes: Set[str],
) -> List[str]:
    subject = f"classification_rules[{index}]"
    if not isinstance(raw_rule, dict):
        return [f"{subject} must be an object"]
    errors = validate_owner_action(raw_rule, subject)
    rule_id = raw_rule.get("id")
    if not isinstance(rule_id, str) or not rule_id.strip():
        errors.append(f"{subject}.id must be non-empty")
    elif rule_id in ids:
        errors.append(f"Duplicate classification rule id: {rule_id}")
    else:
        ids.add(rule_id)
    raw_prefixes = raw_rule.get("name_prefixes")
    if not isinstance(raw_prefixes, list) or not raw_prefixes:
        return errors + [f"{subject}.name_prefixes must be a non-empty list"]
    for prefix in raw_prefixes:
        if not isinstance(prefix, str) or not PREFIX_RE.fullmatch(prefix):
            errors.append(f"{subject} contains invalid secret-name prefix: {prefix}")
        elif prefix in prefixes:
            errors.append(f"Duplicate secret-name prefix classification: {prefix}")
        else:
            prefixes.add(prefix)
    return errors


def validate_rules(rules: Any) -> List[str]:
    if not isinstance(rules, list) or not rules:
        return ["classification_rules must be a non-empty list"]
    errors: List[str] = []
    ids: Set[str] = set()
    prefixes: Set[str] = set()
    for index, raw_rule in enumerate(rules):
        errors.extend(validate_rule(raw_rule, index, ids, prefixes))
    return errors


def validate_policy(policy: Dict[str, Any], today: date) -> List[str]:
    errors: List[str] = []
    if policy.get("schema_version") != 1:
        errors.append("secret-ownership.json schema_version must be 1")
    errors.extend(validate_inventory(policy.get("legacy_github_inventory"), today))
    default_rule = policy.get("default_rule")
    if not isinstance(default_rule, dict):
        errors.append("default_rule must be an object")
    else:
        errors.extend(validate_owner_action(default_rule, "default_rule"))
    errors.extend(validate_rules(policy.get("classification_rules")))
    return errors


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    try:
        policy = load_policy(args.policy)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Secret ownership policy could not be loaded: {error}", file=sys.stderr)
        return 1
    errors = validate_policy(policy, date.today())
    report = {
        "errors": errors,
        "legacyInventory": policy.get("legacy_github_inventory", {}),
        "classificationRuleCount": len(policy.get("classification_rules", [])),
    }
    args.json_report.parent.mkdir(parents=True, exist_ok=True)
    args.json_report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if errors:
        print("Secret ownership policy validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    inventory = policy["legacy_github_inventory"]
    print(
        "Secret ownership policy passed: "
        f"{inventory['expected_name_count']} legacy GitHub secret name(s), "
        f"{len(policy['classification_rules'])} classification rule(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
