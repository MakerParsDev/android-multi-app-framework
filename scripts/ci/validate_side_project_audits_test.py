from __future__ import annotations

from datetime import date
import json
from pathlib import Path
import tempfile
import unittest

from scripts.ci.validate_side_project_audits import (
    AuditPolicyError,
    load_policy,
    resolve_advisories,
    validate_project_audits,
)

ADVISORY = "GHSA-87mf-gv2c-c62c"


def audit_payload(
    vulnerabilities: dict[str, dict[str, object]] | None = None,
    *,
    total: int | None = None,
) -> dict[str, object]:
    records = vulnerabilities or {}
    if total is None:
        total = len(records)
    return {
        "metadata": {
            "vulnerabilities": {
                "info": 0,
                "low": 0,
                "moderate": total,
                "high": 0,
                "critical": 0,
                "total": total,
            }
        },
        "vulnerabilities": records,
    }


def exception() -> dict[str, str]:
    return {
        "project": "firebase-functions",
        "advisory": ADVISORY,
        "severity": "moderate",
        "scope": "development",
        "owner": "oaslananka",
        "trackingIssue": "#124",
        "expiresOn": "2026-08-15",
        "reason": "Upstream test dependency has no compatible patched release yet.",
        "upgradePlan": "Upgrade the upstream package and remove this exception immediately.",
    }


class SideProjectAuditPolicyTest(unittest.TestCase):
    def test_resolves_transitive_advisory_through_wrapper_package(self) -> None:
        vulnerabilities = {
            "firebase-functions-test": {
                "severity": "moderate",
                "via": ["ts-deepmerge"],
            },
            "ts-deepmerge": {
                "severity": "moderate",
                "via": [
                    {
                        "source": 1121318,
                        "url": f"https://github.com/advisories/{ADVISORY}",
                    }
                ],
            },
        }
        self.assertEqual({ADVISORY}, resolve_advisories("firebase-functions-test", vulnerabilities))

    def test_malformed_vulnerability_entry_is_ignored_without_crashing(self) -> None:
        vulnerabilities = {"broken-package": "unexpected"}
        self.assertEqual(set(), resolve_advisories("broken-package", vulnerabilities))

    def test_rejects_any_production_finding(self) -> None:
        production = audit_payload(
            {"runtime-package": {"severity": "low", "via": []}},
            total=1,
        )
        errors, _used, _summary = validate_project_audits(
            "firebase-functions",
            production,
            audit_payload({}, total=0),
            {},
        )
        self.assertTrue(any("production npm audit must be zero" in error for error in errors))

    def test_allows_only_owned_moderate_dev_advisory(self) -> None:
        vulnerabilities = {
            "firebase-functions-test": {
                "severity": "moderate",
                "via": ["ts-deepmerge"],
            },
            "ts-deepmerge": {
                "severity": "moderate",
                "via": [
                    {
                        "source": 1121318,
                        "url": f"https://github.com/advisories/{ADVISORY}",
                    }
                ],
            },
        }
        errors, used, _summary = validate_project_audits(
            "firebase-functions",
            audit_payload({}, total=0),
            audit_payload(vulnerabilities, total=2),
            {("firebase-functions", ADVISORY): exception()},
        )
        self.assertEqual([], errors)
        self.assertEqual({("firebase-functions", ADVISORY)}, used)

    def test_rejects_unowned_or_high_dev_findings(self) -> None:
        moderate = {
            "ts-deepmerge": {
                "severity": "moderate",
                "via": [{"source": 1, "url": f"https://github.com/advisories/{ADVISORY}"}],
            }
        }
        errors, _used, _summary = validate_project_audits(
            "firebase-functions",
            audit_payload({}, total=0),
            audit_payload(moderate, total=1),
            {},
        )
        self.assertTrue(any("unowned dev audit advisory" in error for error in errors))

        high = {
            "dangerous-tool": {
                "severity": "high",
                "via": [{"source": 2, "url": "https://github.com/advisories/GHSA-aaaa-bbbb-cccc"}],
            }
        }
        errors, _used, _summary = validate_project_audits(
            "firebase-functions",
            audit_payload({}, total=0),
            audit_payload(high, total=1),
            {},
        )
        self.assertTrue(any("blocked high" in error for error in errors))

    def test_policy_requires_owner_tracking_and_future_expiry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "policy.json"
            payload = {
                "schemaVersion": 1,
                "productionPolicy": "zero-vulnerabilities",
                "exceptions": [exception()],
            }
            path.write_text(json.dumps(payload), encoding="utf-8")
            loaded = load_policy(path, date(2026, 7, 21))
            self.assertIn(("firebase-functions", ADVISORY), loaded)

            payload["exceptions"][0]["expiresOn"] = "2026-07-20"
            path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(AuditPolicyError, "expired"):
                load_policy(path, date(2026, 7, 21))


if __name__ == "__main__":
    unittest.main()
