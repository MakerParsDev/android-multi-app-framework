#!/usr/bin/env python3
"""Summarize OSV-Scanner JSON into the repository maintenance health model."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
from typing import Any

SEVERITY_ORDER = {"unknown": 0, "low": 1, "medium": 2, "high": 3, "critical": 4}


class OsvReportError(ValueError):
    pass


def _normalize_severity(value: object) -> str | None:
    if not isinstance(value, str):
        return None
    normalized = value.strip().lower()
    aliases = {
        "moderate": "medium",
        "medium": "medium",
        "low": "low",
        "high": "high",
        "critical": "critical",
    }
    return aliases.get(normalized)


def _severity_from_numeric_score(score: float) -> str:
    if score >= 9.0:
        return "critical"
    if score >= 7.0:
        return "high"
    if score >= 4.0:
        return "medium"
    if score > 0:
        return "low"
    return "unknown"


def _cvss3_score(vector: str) -> float | None:
    """Calculate a CVSS v3.0/v3.1 base score from a standard vector."""
    if not vector.startswith(("CVSS:3.0/", "CVSS:3.1/")):
        return None

    values = {}
    for item in vector.split("/")[1:]:
        if ":" not in item:
            continue
        key, value = item.split(":", 1)
        values[key] = value

    required = {"AV", "AC", "PR", "UI", "S", "C", "I", "A"}
    if not required.issubset(values):
        return None

    av = {"N": 0.85, "A": 0.62, "L": 0.55, "P": 0.2}
    ac = {"L": 0.77, "H": 0.44}
    ui = {"N": 0.85, "R": 0.62}
    cia = {"H": 0.56, "L": 0.22, "N": 0.0}
    pr_u = {"N": 0.85, "L": 0.62, "H": 0.27}
    pr_c = {"N": 0.85, "L": 0.68, "H": 0.5}

    try:
        scope_changed = values["S"] == "C"
        pr = (pr_c if scope_changed else pr_u)[values["PR"]]
        exploitability = (
            8.22 * av[values["AV"]] * ac[values["AC"]] * pr * ui[values["UI"]]
        )
        impact_base = 1 - (
            (1 - cia[values["C"]])
            * (1 - cia[values["I"]])
            * (1 - cia[values["A"]])
        )
    except KeyError:
        return None

    if scope_changed:
        impact = 7.52 * (impact_base - 0.029) - 3.25 * ((impact_base - 0.02) ** 15)
    else:
        impact = 6.42 * impact_base

    if impact <= 0:
        return 0.0
    base = (
        min(1.08 * (impact + exploitability), 10.0)
        if scope_changed
        else min(impact + exploitability, 10.0)
    )
    # CVSS requires round-up to one decimal, not normal round-to-nearest.
    return int((base * 10.0) + 0.999999) / 10.0


def vulnerability_severity(vulnerability: dict[str, Any]) -> str:
    candidates: list[str] = []

    database_specific = vulnerability.get("database_specific")
    if isinstance(database_specific, dict):
        normalized = _normalize_severity(database_specific.get("severity"))
        if normalized:
            candidates.append(normalized)

    ecosystem_specific = vulnerability.get("ecosystem_specific")
    if isinstance(ecosystem_specific, dict):
        normalized = _normalize_severity(ecosystem_specific.get("severity"))
        if normalized:
            candidates.append(normalized)

    severity_entries = vulnerability.get("severity")
    if isinstance(severity_entries, list):
        for entry in severity_entries:
            if not isinstance(entry, dict):
                continue
            raw_score = entry.get("score")
            numeric: float | None = None
            if isinstance(raw_score, (int, float)):
                numeric = float(raw_score)
            elif isinstance(raw_score, str):
                stripped = raw_score.strip()
                if re.fullmatch(r"[0-9]+(?:\.[0-9]+)?", stripped):
                    numeric = float(stripped)
                else:
                    numeric = _cvss3_score(stripped)
            if numeric is not None:
                candidates.append(_severity_from_numeric_score(numeric))

    if not candidates:
        return "unknown"
    return max(candidates, key=lambda value: SEVERITY_ORDER[value])


def _group_severity(
    vulnerabilities_by_id: dict[str, dict[str, Any]],
    ids: list[str],
) -> str:
    severities = [
        vulnerability_severity(vulnerabilities_by_id[vuln_id])
        for vuln_id in ids
        if vuln_id in vulnerabilities_by_id
    ]
    if not severities:
        return "unknown"
    return max(severities, key=lambda value: SEVERITY_ORDER[value])


def summarize_osv_data(data: object) -> dict[str, Any]:
    if not isinstance(data, dict):
        raise OsvReportError("OSV report root must be an object")
    results = data.get("results")
    if not isinstance(results, list):
        raise OsvReportError("OSV report must contain a results array")

    counts = {name: 0 for name in ("critical", "high", "medium", "low", "unknown")}
    affected_packages: set[tuple[str, str, str]] = set()
    group_keys: set[tuple[str, str, str, tuple[str, ...]]] = set()

    for result in results:
        if not isinstance(result, dict):
            raise OsvReportError("OSV result entries must be objects")
        packages = result.get("packages", [])
        if not isinstance(packages, list):
            raise OsvReportError("OSV result packages must be an array")

        for package_entry in packages:
            if not isinstance(package_entry, dict):
                continue
            package = package_entry.get("package")
            if not isinstance(package, dict):
                continue
            name = str(package.get("name") or "unknown")
            version = str(package.get("version") or "unknown")
            ecosystem = str(package.get("ecosystem") or "unknown")

            vulnerabilities = package_entry.get("vulnerabilities", [])
            if not isinstance(vulnerabilities, list):
                raise OsvReportError("OSV vulnerabilities must be an array")
            vuln_by_id = {
                str(vuln.get("id")): vuln
                for vuln in vulnerabilities
                if isinstance(vuln, dict) and vuln.get("id")
            }
            if not vuln_by_id:
                continue

            affected_packages.add((ecosystem, name, version))
            groups = package_entry.get("groups")
            if isinstance(groups, list) and groups:
                covered: set[str] = set()
                for group in groups:
                    if not isinstance(group, dict):
                        continue
                    ids = sorted(
                        {
                            str(vuln_id)
                            for vuln_id in group.get("ids", [])
                            if str(vuln_id) in vuln_by_id
                        }
                    )
                    if not ids:
                        continue
                    covered.update(ids)
                    key = (ecosystem, name, version, tuple(ids))
                    if key in group_keys:
                        continue
                    group_keys.add(key)
                    counts[_group_severity(vuln_by_id, ids)] += 1

                for vuln_id in sorted(set(vuln_by_id) - covered):
                    key = (ecosystem, name, version, (vuln_id,))
                    if key not in group_keys:
                        group_keys.add(key)
                        counts[vulnerability_severity(vuln_by_id[vuln_id])] += 1
            else:
                for vuln_id, vuln in vuln_by_id.items():
                    key = (ecosystem, name, version, (vuln_id,))
                    if key not in group_keys:
                        group_keys.add(key)
                        counts[vulnerability_severity(vuln)] += 1

    total = sum(counts.values())
    if counts["critical"] or counts["high"]:
        state = "ATTENTION_REQUIRED"
    elif total:
        state = "DEGRADED"
    else:
        state = "HEALTHY"

    return {
        "state": state,
        "total": total,
        "affected_packages": len(affected_packages),
        "counts": counts,
    }


def summarize_osv_report(path: Path) -> tuple[str, str]:
    if not path.is_file():
        return "UNKNOWN", f"OSV report missing: {path}"
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        summary = summarize_osv_data(data)
    except (OSError, json.JSONDecodeError, OsvReportError, ValueError) as error:
        return "UNKNOWN", f"OSV report invalid: {error}"

    counts = summary["counts"]
    if summary["state"] == "HEALTHY":
        return "HEALTHY", "No known vulnerabilities found in the GitHub SPDX SBOM"

    message = (
        f"{summary['total']} vulnerability group(s) across "
        f"{summary['affected_packages']} affected package(s): "
        f"{counts['critical']} critical, {counts['high']} high, "
        f"{counts['medium']} medium, {counts['low']} low, "
        f"{counts['unknown']} unknown"
    )
    return str(summary["state"]), message


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("build/reports/dependencies/osv-results.json"),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    state, message = summarize_osv_report(args.input)
    print(f"{state}: {message}")
    return 1 if state == "UNKNOWN" else 0


if __name__ == "__main__":
    raise SystemExit(main())
