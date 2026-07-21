#!/usr/bin/env python3
"""Validate Gradle wrapper integrity and dependency verification review policy."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--policy", type=Path, default=Path("config/supply-chain-policy.json"))
    parser.add_argument(
        "--json-report",
        type=Path,
        default=Path("build/reports/security/supply-chain-policy.json"),
    )
    return parser.parse_args(argv)


def load_json(path: Path) -> Dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"JSON root must be an object: {path}")
    return value


def parse_properties(path: Path) -> Dict[str, str]:
    values: Dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_wrapper_config(wrapper: Any) -> List[str]:
    if not isinstance(wrapper, dict):
        return ["gradle_wrapper must be an object"]
    errors: List[str] = []
    version = wrapper.get("version")
    if not isinstance(version, str) or not version:
        errors.append("gradle_wrapper.version must be a non-empty string")
    for name in ("distribution_sha256", "jar_sha256"):
        value = wrapper.get(name)
        if not isinstance(value, str) or not SHA256_RE.fullmatch(value):
            errors.append(f"gradle_wrapper.{name} must be a lowercase SHA-256 digest")
    return errors


def validate_wrapper_files(root: Path, wrapper: Dict[str, Any]) -> List[str]:
    errors: List[str] = []
    properties_path = root / "gradle/wrapper/gradle-wrapper.properties"
    jar_path = root / "gradle/wrapper/gradle-wrapper.jar"
    if not properties_path.is_file():
        errors.append(f"Missing Gradle wrapper properties: {properties_path}")
    else:
        properties = parse_properties(properties_path)
        expected_suffix = f"gradle-{wrapper.get('version', '')}-bin.zip"
        if not properties.get("distributionUrl", "").endswith(expected_suffix):
            errors.append(f"Gradle distribution URL must end with {expected_suffix}")
        if properties.get("distributionSha256Sum") != wrapper.get("distribution_sha256"):
            errors.append("Gradle distributionSha256Sum does not match supply-chain policy")
        if properties.get("validateDistributionUrl") != "true":
            errors.append("Gradle validateDistributionUrl must remain true")
    if not jar_path.is_file():
        errors.append(f"Missing Gradle wrapper JAR: {jar_path}")
    elif sha256_file(jar_path) != wrapper.get("jar_sha256"):
        errors.append("Gradle wrapper JAR SHA-256 does not match supply-chain policy")
    return errors


def parse_review_date(raw: Any) -> Optional[date]:
    if not isinstance(raw, str):
        return None
    try:
        return datetime.strptime(raw, "%Y-%m-%d").date()
    except ValueError:
        return None


def validate_dependency_decision(root: Path, decision: Any, today: date) -> List[str]:
    if not isinstance(decision, dict):
        return ["dependency_verification must be an object"]
    errors: List[str] = []
    status = decision.get("decision")
    if status not in {"enabled", "deferred"}:
        errors.append("dependency_verification.decision must be enabled or deferred")
    reason = decision.get("reason")
    if not isinstance(reason, str) or len(reason.strip()) < 80:
        errors.append("dependency_verification.reason must document the risk decision")
    review_date = parse_review_date(decision.get("next_review_on"))
    if review_date is None:
        errors.append("dependency_verification.next_review_on must be YYYY-MM-DD")
    elif review_date < today:
        errors.append(f"dependency verification decision review expired on {review_date.isoformat()}")
    if status == "enabled" and not (root / "gradle/verification-metadata.xml").is_file():
        errors.append("dependency verification is enabled but verification-metadata.xml is missing")
    return errors


def validate_policy(root: Path, policy: Dict[str, Any], today: date) -> List[str]:
    errors: List[str] = []
    if policy.get("schema_version") != 1:
        errors.append("supply-chain-policy.json schema_version must be 1")
    wrapper = policy.get("gradle_wrapper")
    wrapper_errors = validate_wrapper_config(wrapper)
    errors.extend(wrapper_errors)
    if isinstance(wrapper, dict) and not wrapper_errors:
        errors.extend(validate_wrapper_files(root, wrapper))
    errors.extend(validate_dependency_decision(root, policy.get("dependency_verification"), today))
    return errors


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    root = args.root.resolve()
    policy_path = args.policy if args.policy.is_absolute() else root / args.policy
    report_path = args.json_report if args.json_report.is_absolute() else root / args.json_report
    try:
        policy = load_json(policy_path)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Supply-chain policy could not be loaded: {error}", file=sys.stderr)
        return 1
    errors = validate_policy(root, policy, date.today())
    report = {
        "errors": errors,
        "gradleWrapper": policy.get("gradle_wrapper", {}),
        "dependencyVerification": policy.get("dependency_verification", {}),
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if errors:
        print("Supply-chain policy validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print("Supply-chain policy passed: Gradle wrapper checksums and review decision are current")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
