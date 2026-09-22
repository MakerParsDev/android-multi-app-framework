#!/usr/bin/env python3
"""Unit tests for osv_health.py."""

from __future__ import annotations

import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))

import osv_health


def vuln(
    vuln_id: str,
    *,
    database_severity: str | None = None,
    score: str | float | None = None,
) -> dict:
    value = {"id": vuln_id}
    if database_severity:
        value["database_specific"] = {"severity": database_severity}
    if score is not None:
        value["severity"] = [{"type": "CVSS_V3", "score": score}]
    return value


def report_for(vulnerabilities: list[dict], groups: list[dict] | None = None) -> dict:
    return {
        "results": [
            {
                "source": {"path": "repo.spdx.json", "type": "sbom"},
                "packages": [
                    {
                        "package": {
                            "name": "demo",
                            "version": "1.0.0",
                            "ecosystem": "Maven",
                        },
                        "vulnerabilities": vulnerabilities,
                        "groups": groups or [],
                    }
                ],
            }
        ]
    }


class OsvHealthTest(unittest.TestCase):
    def test_database_specific_severity_mapping(self):
        self.assertEqual(
            osv_health.vulnerability_severity(
                vuln("GHSA-a", database_severity="CRITICAL")
            ),
            "critical",
        )
        self.assertEqual(
            osv_health.vulnerability_severity(
                vuln("GHSA-b", database_severity="MODERATE")
            ),
            "medium",
        )

    def test_cvss3_vectors_are_scored(self):
        critical = vuln(
            "OSV-critical",
            score="CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H",
        )
        high = vuln(
            "OSV-high",
            score="CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H",
        )
        self.assertEqual(osv_health.vulnerability_severity(critical), "critical")
        self.assertEqual(osv_health.vulnerability_severity(high), "high")

    def test_alias_group_is_counted_once_at_highest_severity(self):
        data = report_for(
            [
                vuln("GHSA-one", database_severity="HIGH"),
                vuln("CVE-one", database_severity="MEDIUM"),
            ],
            groups=[{"ids": ["GHSA-one", "CVE-one"]}],
        )
        summary = osv_health.summarize_osv_data(data)
        self.assertEqual(summary["total"], 1)
        self.assertEqual(summary["counts"]["high"], 1)
        self.assertEqual(summary["state"], "ATTENTION_REQUIRED")

    def test_medium_or_low_is_degraded(self):
        data = report_for(
            [
                vuln("GHSA-medium", database_severity="MEDIUM"),
                vuln("GHSA-low", database_severity="LOW"),
            ]
        )
        summary = osv_health.summarize_osv_data(data)
        self.assertEqual(summary["state"], "DEGRADED")
        self.assertEqual(summary["total"], 2)

    def test_empty_results_are_healthy(self):
        summary = osv_health.summarize_osv_data({"results": []})
        self.assertEqual(summary["state"], "HEALTHY")
        self.assertEqual(summary["total"], 0)

    def test_missing_or_invalid_report_is_unknown(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "missing.json"
            state, _ = osv_health.summarize_osv_report(path)
            self.assertEqual(state, "UNKNOWN")
            path.write_text(json.dumps({"not_results": []}), encoding="utf-8")
            state, _ = osv_health.summarize_osv_report(path)
            self.assertEqual(state, "UNKNOWN")


if __name__ == "__main__":
    unittest.main()
