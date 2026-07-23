#!/usr/bin/env python3
"""Enforce production-zero and owned, expiring dev-only npm audit policy."""
from __future__ import annotations

import argparse
from datetime import date, datetime, timezone
import json
from pathlib import Path
import re
import shutil
# Executes a fixed npm command without a shell.
import subprocess  # nosec B404
import sys
from typing import Any

ADVISORY_RE = re.compile(r"GHSA-[A-Za-z0-9-]+")
ISSUE_RE = re.compile(r"#\d+")
PROJECTS = {
    "admin-notifications": Path("side-projects/admin-notifications"),
    "admin-api": Path("side-projects/cloudflare/workers/admin-api"),
    "content-api": Path("side-projects/cloudflare/workers/content-api"),
    "ssv-callback": Path("side-projects/cloudflare/workers/ssv-callback"),
    "firebase-functions": Path("side-projects/firebase/functions"),
    "firebase-rules-tests": Path("side-projects/firebase/rules-tests"),
}
ALLOWED_EXCEPTION_SEVERITIES = {"low", "moderate"}
BLOCKED_DEV_SEVERITIES = {"high", "critical"}
EXPIRY_WARNING_DAYS = 30


class AuditPolicyError(RuntimeError):
    pass


def advisory_id(via: dict[str, Any]) -> str | None:
    url = via.get("url")
    if isinstance(url, str):
        match = ADVISORY_RE.search(url)
        if match:
            return match.group(0)
    source = via.get("source")
    return f"NPM-{source}" if isinstance(source, int) else None


def resolve_advisories(
    package: str,
    vulnerabilities: dict[str, Any],
    seen: set[str] | None = None,
) -> set[str]:
    seen = set() if seen is None else seen
    if package in seen:
        return set()
    seen.add(package)
    result: set[str] = set()
    vulnerability = vulnerabilities.get(package)
    if not isinstance(vulnerability, dict):
        return result
    for via in vulnerability.get("via", []):
        if isinstance(via, dict):
            resolved = advisory_id(via)
            if resolved:
                result.add(resolved)
        elif isinstance(via, str):
            result.update(resolve_advisories(via, vulnerabilities, seen))
    return result


def _exception_identity(entry: dict[str, Any]) -> tuple[str, str]:
    project = entry.get("project")
    advisory = entry.get("advisory")
    if project not in PROJECTS:
        raise AuditPolicyError(f"unknown audit exception project: {project}")
    valid_advisory = isinstance(advisory, str) and (
        ADVISORY_RE.fullmatch(advisory) or advisory.startswith("NPM-")
    )
    if not valid_advisory:
        raise AuditPolicyError(f"invalid advisory id for {project}")
    return project, advisory


def _require_text(
    entry: dict[str, Any],
    field: str,
    context: str,
    message: str,
    minimum_length: int = 1,
) -> None:
    value = entry.get(field)
    if not isinstance(value, str) or len(value.strip()) < minimum_length:
        raise AuditPolicyError(f"{message} for {context}")


def _validate_exception(entry: dict[str, Any], today: date) -> tuple[str, str]:
    project, advisory = _exception_identity(entry)
    context = f"{project}/{advisory}"
    if entry.get("severity") not in ALLOWED_EXCEPTION_SEVERITIES:
        raise AuditPolicyError(f"only low/moderate dev exceptions are allowed: {context}")
    if entry.get("scope") != "development":
        raise AuditPolicyError(f"audit exceptions must be development-only: {context}")
    _require_text(entry, "owner", context, "missing owner")
    tracking_issue = entry.get("trackingIssue")
    if not isinstance(tracking_issue, str) or not ISSUE_RE.fullmatch(tracking_issue):
        raise AuditPolicyError(f"missing tracking issue for {context}")
    _require_text(entry, "dependencyChain", context, "missing dependency chain", minimum_length=5)
    _require_text(entry, "reason", context, "missing rationale", minimum_length=20)
    _require_text(entry, "upgradePlan", context, "missing upgrade plan", minimum_length=20)
    try:
        introduced_on = date.fromisoformat(str(entry.get("introducedOn")))
    except ValueError as error:
        raise AuditPolicyError(f"invalid introduction date for {context}") from error
    if introduced_on > today:
        raise AuditPolicyError(
            f"future introduction date for {context}: {introduced_on}"
        )
    try:
        expires_on = date.fromisoformat(str(entry.get("expiresOn")))
    except ValueError as error:
        raise AuditPolicyError(f"invalid expiry for {context}") from error
    if expires_on < today:
        raise AuditPolicyError(f"expired audit exception: {context} expired {expires_on}")
    if expires_on <= introduced_on:
        raise AuditPolicyError(
            f"expiry must be after introduction for {context}: "
            f"introduced={introduced_on} expires={expires_on}"
        )
    return project, advisory


def load_policy(path: Path, today: date) -> dict[tuple[str, str], dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("schemaVersion") != 1:
        raise AuditPolicyError("audit policy schemaVersion must be 1")
    if payload.get("productionPolicy") != "zero-vulnerabilities":
        raise AuditPolicyError("production policy must require zero vulnerabilities")
    exceptions = payload.get("exceptions")
    if not isinstance(exceptions, list):
        raise AuditPolicyError("audit policy exceptions must be a list")

    result: dict[tuple[str, str], dict[str, Any]] = {}
    for entry in exceptions:
        if not isinstance(entry, dict):
            raise AuditPolicyError("audit exception must be an object")
        key = _validate_exception(entry, today)
        if key in result:
            raise AuditPolicyError(f"duplicate audit exception: {key[0]}/{key[1]}")
        result[key] = entry
    return result


def exception_expiry_warnings(
    exceptions: dict[tuple[str, str], dict[str, Any]],
    today: date,
    warning_days: int = EXPIRY_WARNING_DAYS,
) -> list[str]:
    warnings: list[str] = []
    for (project, advisory), entry in sorted(exceptions.items()):
        expires_on = date.fromisoformat(str(entry["expiresOn"]))
        days_remaining = (expires_on - today).days
        if 0 <= days_remaining <= warning_days:
            warnings.append(
                f"audit exception {project}/{advisory} expires in "
                f"{days_remaining} days on {expires_on}; "
                f"owner={entry['owner']} tracking={entry['trackingIssue']}"
            )
    return warnings


def run_npm_audit(root: Path, project_path: Path, production: bool) -> dict[str, Any]:
    npm = shutil.which("npm")
    if not npm:
        raise AuditPolicyError("npm executable was not found")
    command = [npm, "--prefix", str(project_path), "audit"]
    if production:
        command.append("--omit=dev")
    command.append("--json")
    # Every argument is generated from the fixed PROJECTS mapping; no shell is used.
    # The command and project paths come from the fixed PROJECTS mapping.
    result = subprocess.run(  # nosec B603
        command,
        cwd=root,
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode not in {0, 1}:
        scope = "production" if production else "all"
        raise AuditPolicyError(f"npm audit execution failed for {project_path} ({scope})")
    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise AuditPolicyError(f"npm audit returned invalid JSON for {project_path}") from error
    if not isinstance(payload, dict):
        raise AuditPolicyError(f"npm audit returned a non-object for {project_path}")
    return payload


def vulnerability_total(payload: dict[str, Any]) -> int:
    vulnerabilities = payload.get("metadata", {}).get("vulnerabilities", {})
    total = vulnerabilities.get("total", 0)
    return total if isinstance(total, int) else 0


def _validate_advisory(
    project: str,
    advisory: str,
    severity: Any,
    exceptions: dict[tuple[str, str], dict[str, Any]],
) -> tuple[list[str], set[tuple[str, str]]]:
    key = (project, advisory)
    exception = exceptions.get(key)
    if exception is None:
        return [f"{project}: unowned dev audit advisory {advisory}"], set()
    if exception.get("severity") != severity:
        message = (
            f"{project}: policy severity mismatch for {advisory}; "
            f"audit={severity} policy={exception.get('severity')}"
        )
        return [message], set()
    return [], {key}


def _validate_vulnerability(
    project: str,
    package: str,
    vulnerability: Any,
    vulnerabilities: dict[str, Any],
    exceptions: dict[tuple[str, str], dict[str, Any]],
) -> tuple[list[str], set[tuple[str, str]], set[str]]:
    if not isinstance(vulnerability, dict):
        return [f"{project}: malformed audit record for {package}"], set(), set()
    severity = vulnerability.get("severity")
    if severity in BLOCKED_DEV_SEVERITIES:
        return [f"{project}: dev audit contains blocked {severity} vulnerability in {package}"], set(), set()
    if severity not in ALLOWED_EXCEPTION_SEVERITIES:
        return [], set(), set()
    advisories = resolve_advisories(package, vulnerabilities)
    if not advisories:
        return [f"{project}: {package} has an untraceable {severity} audit finding"], set(), set()

    errors: list[str] = []
    used: set[tuple[str, str]] = set()
    for advisory in advisories:
        advisory_errors, advisory_used = _validate_advisory(
            project,
            advisory,
            severity,
            exceptions,
        )
        errors.extend(advisory_errors)
        used.update(advisory_used)
    return errors, used, advisories


def validate_project_audits(
    project: str,
    production_payload: dict[str, Any],
    all_payload: dict[str, Any],
    exceptions: dict[tuple[str, str], dict[str, Any]],
) -> tuple[list[str], set[tuple[str, str]], dict[str, Any]]:
    errors: list[str] = []
    used: set[tuple[str, str]] = set()
    advisories: set[str] = set()
    production_total = vulnerability_total(production_payload)
    if production_total != 0:
        errors.append(f"{project}: production npm audit must be zero; found {production_total}")

    vulnerabilities = all_payload.get("vulnerabilities", {})
    if not isinstance(vulnerabilities, dict):
        vulnerabilities = {}
    for package, vulnerability in sorted(vulnerabilities.items()):
        item_errors, item_used, item_advisories = _validate_vulnerability(
            project,
            package,
            vulnerability,
            vulnerabilities,
            exceptions,
        )
        errors.extend(item_errors)
        used.update(item_used)
        advisories.update(item_advisories)

    summary = {
        "production": production_payload.get("metadata", {}).get("vulnerabilities", {}),
        "all": all_payload.get("metadata", {}).get("vulnerabilities", {}),
        "advisories": sorted(advisories),
    }
    return errors, used, summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument(
        "--policy",
        type=Path,
        default=Path("side-projects/audit-policy.json"),
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("build/reports/side-projects/npm-audit.json"),
    )
    return parser.parse_args()


def _collect_audit_results(
    root: Path,
    exceptions: dict[tuple[str, str], dict[str, Any]],
) -> tuple[list[str], set[tuple[str, str]], dict[str, Any]]:
    errors: list[str] = []
    used: set[tuple[str, str]] = set()
    projects_report: dict[str, Any] = {}
    for project, relative_path in PROJECTS.items():
        production = run_npm_audit(root, relative_path, production=True)
        all_dependencies = run_npm_audit(root, relative_path, production=False)
        project_errors, project_used, summary = validate_project_audits(
            project,
            production,
            all_dependencies,
            exceptions,
        )
        errors.extend(project_errors)
        used.update(project_used)
        projects_report[project] = summary
    return errors, used, projects_report


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    policy_path = args.policy if args.policy.is_absolute() else root / args.policy
    report_path = args.report if args.report.is_absolute() else root / args.report
    try:
        today = datetime.now(timezone.utc).date()
        exceptions = load_policy(policy_path, today)
        for warning in exception_expiry_warnings(exceptions, today):
            print(f"::warning::{warning}", file=sys.stderr)
        errors, used, projects_report = _collect_audit_results(root, exceptions)
        stale = sorted(set(exceptions) - used)
        errors.extend(
            f"stale audit exception must be removed: {project}/{advisory}"
            for project, advisory in stale
        )
        if errors:
            raise AuditPolicyError("\n".join(errors))
    except (OSError, AuditPolicyError, json.JSONDecodeError) as error:
        print(f"Side-project npm audit policy failed:\n{error}", file=sys.stderr)
        return 1

    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(
            {
                "status": "passed",
                "checkedAt": datetime.now(timezone.utc).isoformat(),
                "productionPolicy": "zero-vulnerabilities",
                "usedExceptions": [
                    {
                        "project": project,
                        "advisory": advisory,
                        "dependencyChain": exceptions[(project, advisory)]["dependencyChain"],
                        "owner": exceptions[(project, advisory)]["owner"],
                        "trackingIssue": exceptions[(project, advisory)]["trackingIssue"],
                        "introducedOn": exceptions[(project, advisory)]["introducedOn"],
                        "expiresOn": exceptions[(project, advisory)]["expiresOn"],
                    }
                    for project, advisory in sorted(used)
                ],
                "projects": projects_report,
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    print(
        "Side-project npm audit policy passed: "
        f"projects={len(PROJECTS)} production_findings=0 dev_exceptions={len(used)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
