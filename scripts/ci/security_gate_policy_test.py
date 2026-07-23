#!/usr/bin/env python3
"""Regression tests for the secret and supply-chain security gate."""

from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from secret_scan_policy import (
    render_gitleaksignore,
    validate_ignore_file,
    validate_policy as validate_scan,
)
from tracked_sensitive_files import find_sensitive_paths, sensitive_reason
from validate_secret_ownership import validate_policy as validate_ownership
from validate_security_pipeline import (
    validate_github_workflow,
    validate_security_workflow,
)
from validate_supply_chain_policy import validate_policy as validate_supply


def scan_policy():
    return {
        "schema_version": 1,
        "gitleaks": {
            "version": "8.30.1",
            "asset": "gitleaks_8.30.1_linux_x64.tar.gz",
            "download_url": "https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_linux_x64.tar.gz",
            "sha256": "551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb",
        },
        "baseline": [],
    }


def ownership_policy():
    return {
        "schema_version": 1,
        "legacy_github_inventory": {
            "expected_name_count": 47,
            "owner": "platform/security",
            "review_due_on": "2026-08-15",
            "status": "legacy_mirror_remove_after_migration",
            "evidence": "issue #27",
        },
        "default_rule": {
            "canonical_store": "doppler",
            "owner": "platform/security",
            "rotation_days": 90,
            "action": "migrate_then_delete_github_mirror",
        },
        "classification_rules": [
            {
                "id": "service",
                "name_prefixes": ["SERVICE_"],
                "canonical_store": "doppler",
                "owner": "platform/security",
                "rotation_days": 90,
                "action": "migrate_then_delete_github_mirror",
            }
        ],
    }


def supply_fixture(root: Path):
    wrapper_dir = root / "gradle/wrapper"
    wrapper_dir.mkdir(parents=True)
    jar_bytes = b"wrapper-fixture"
    jar_sha = hashlib.sha256(jar_bytes).hexdigest()
    (wrapper_dir / "gradle-wrapper.jar").write_bytes(jar_bytes)
    distribution_sha = "a" * 64
    (wrapper_dir / "gradle-wrapper.properties").write_text(
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.1-bin.zip\n"
        f"distributionSha256Sum={distribution_sha}\n"
        "validateDistributionUrl=true\n",
        encoding="utf-8",
    )
    return {
        "schema_version": 1,
        "gradle_wrapper": {
            "version": "9.5.1",
            "distribution_sha256": distribution_sha,
            "jar_sha256": jar_sha,
        },
        "dependency_verification": {
            "decision": "deferred",
            "next_review_on": "2026-10-01",
            "reason": "Dependency verification remains deferred while artifact churn is measured under blocking catalog, wrapper, and scheduled audit controls.",
        },
    }


class SecurityGatePolicyTest(unittest.TestCase):
    def test_pinned_scanner_policy_and_empty_baseline_pass(self):
        tool, baseline, errors = validate_scan(scan_policy(), date(2026, 7, 12))
        self.assertEqual([], errors)
        self.assertEqual("8.30.1", tool["version"])
        self.assertEqual([], baseline)

    def test_bad_scanner_checksum_fails(self):
        policy = scan_policy()
        policy["gitleaks"]["sha256"] = "abc"
        self.assertTrue(validate_scan(policy, date(2026, 7, 12))[2])

    def test_expired_baseline_fails(self):
        policy = scan_policy()
        policy["baseline"] = [
            {
                "fingerprint": "a" * 40 + ":path/file.txt:generic-api-key:12",
                "owner": "platform/security",
                "reason": "Historical finding retained while credential rotation is verified.",
                "expires_on": "2026-01-01",
            }
        ]
        self.assertTrue(
            any(
                "expired" in error
                for error in validate_scan(policy, date(2026, 7, 12))[2]
            )
        )

    def test_ignore_file_is_deterministic(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / ".gitleaksignore"
            path.write_text(render_gitleaksignore([]), encoding="utf-8")
            self.assertEqual([], validate_ignore_file([], path))

    def test_sensitive_paths_and_templates(self):
        self.assertEqual("", sensitive_reason("config/.env.example"))
        findings = find_sensitive_paths(
            [".env", "app/google-services.json", "release.jks"]
        )
        self.assertEqual(3, len(findings))

    def test_supply_policy_passes_and_detects_tamper(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            policy = supply_fixture(root)
            self.assertEqual([], validate_supply(root, policy, date(2026, 7, 12)))
            (root / "gradle/wrapper/gradle-wrapper.jar").write_bytes(b"tampered")
            self.assertTrue(
                any(
                    "JAR SHA-256" in error
                    for error in validate_supply(root, policy, date(2026, 7, 12))
                )
            )

    def test_expired_dependency_review_fails(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            policy = supply_fixture(root)
            policy["dependency_verification"]["next_review_on"] = "2026-01-01"
            self.assertTrue(
                any(
                    "review expired" in error
                    for error in validate_supply(root, policy, date(2026, 7, 12))
                )
            )

    def write_workflow(self, root: Path, body: str):
        path = root / ".github/workflows/ci-pr.yml"
        path.parent.mkdir(parents=True)
        path.write_text(body, encoding="utf-8")
        return path

    def test_checkout_with_persist_credentials_pass(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            path = self.write_workflow(
                root,
                "jobs:\n  test:\n    steps:\n      - uses: actions/checkout@abc123\n        with:\n          fetch-depth: 0\n          persist-credentials: false\n",
            )
            self.assertEqual([], validate_github_workflow(path, root))

    def test_checkout_without_persist_credentials_fails(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            path = self.write_workflow(
                root,
                "jobs:\n  test:\n    steps:\n      - uses: actions/checkout@abc123\n        with:\n          fetch-depth: 0\n",
            )
            errors = validate_github_workflow(path, root)
            self.assertTrue(any("persist-credentials" in error for error in errors))

    def test_security_workflow_required(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            errors = validate_security_workflow(root)
            self.assertTrue(any("Missing" in error for error in errors))

    def test_ownership_policy_and_deadline(self):
        self.assertEqual([], validate_ownership(ownership_policy(), date(2026, 7, 12)))
        policy = ownership_policy()
        policy["legacy_github_inventory"]["review_due_on"] = "2026-01-01"
        self.assertTrue(
            any(
                "review expired" in error
                for error in validate_ownership(policy, date(2026, 7, 12))
            )
        )

    def test_duplicate_ownership_prefix_fails(self):
        policy = ownership_policy()
        duplicate = json.loads(json.dumps(policy["classification_rules"][0]))
        duplicate["id"] = "duplicate"
        policy["classification_rules"].append(duplicate)
        self.assertTrue(
            any(
                "Duplicate secret-name prefix" in error
                for error in validate_ownership(policy, date(2026, 7, 12))
            )
        )


if __name__ == "__main__":
    unittest.main()
