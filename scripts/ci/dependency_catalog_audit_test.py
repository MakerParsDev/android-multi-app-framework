#!/usr/bin/env python3
"""Regression tests for dependency catalog lifecycle policy."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from dependency_catalog_audit import (
    build_inventory,
    parse_catalog,
    validate_policy,
    write_reports,
)


VALID_POLICY = {
    "schema_version": 1,
    "stable_only": True,
    "review_cadence_days": 30,
    "catalog_inline_version_allowlist": [],
    "catalog_prerelease_allowlist": [],
    "transitive_prerelease_allowlist": [
        {
            "coordinate": "example.group:preview-module",
            "owner": "core/example",
            "reason": "Required temporarily by a stable top-level SDK.",
            "expires_on": "2027-01-31",
        }
    ],
}


def write_catalog(directory: Path, content: str) -> Path:
    path = directory / "libs.versions.toml"
    path.write_text(content, encoding="utf-8")
    return path


class DependencyCatalogAuditTest(unittest.TestCase):
    def audit(self, catalog_text: str, policy=None):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            catalog, parse_errors = parse_catalog(write_catalog(root, catalog_text))
            allowlists, policy_errors = validate_policy(
                VALID_POLICY if policy is None else policy,
                date(2026, 7, 11),
            )
            inventory, inventory_errors = build_inventory(catalog, allowlists)
            return inventory, parse_errors + policy_errors + inventory_errors

    def test_stable_catalog_passes_without_inline_versions(self) -> None:
        inventory, errors = self.audit(
            """
[versions]
androidx = "1.2.3"
plugin = "4.5.6"

[libraries]
androidx-core = { module = "androidx.core:core", version.ref = "androidx" }
managed = { module = "androidx.compose.ui:ui" }

[plugins]
android = { id = "com.android.application", version.ref = "plugin" }
"""
        )
        self.assertEqual([], errors)
        self.assertEqual(0, inventory["summary"]["inlineVersions"])
        self.assertEqual(0, inventory["summary"]["catalogPrereleases"])

    def test_inline_version_is_rejected(self) -> None:
        _, errors = self.audit(
            """
[versions]
stable = "1.0.0"
[libraries]
inline = { module = "example:inline", version = "1.0.0" }
[plugins]
"""
        )
        self.assertIn("Inline version is not allowlisted for alias 'inline'", errors)

    def test_prerelease_version_requires_expiring_allowlist(self) -> None:
        catalog = """
[versions]
preview = "2.0.0-beta01"
[libraries]
preview-lib = { module = "example:preview", version.ref = "preview" }
[plugins]
"""
        _, errors = self.audit(catalog)
        self.assertIn(
            "Pre-release version is not allowlisted for alias 'preview-lib': 2.0.0-beta01",
            errors,
        )

        policy = json.loads(json.dumps(VALID_POLICY))
        policy["catalog_prerelease_allowlist"] = [
            {
                "alias": "preview-lib",
                "owner": "feature/example",
                "reason": "Temporary compatibility requirement for preview API.",
                "expires_on": "2026-12-31",
            }
        ]
        inventory, allowed_errors = self.audit(catalog, policy)
        self.assertEqual([], allowed_errors)
        self.assertEqual(1, inventory["summary"]["catalogPrereleases"])

    def test_dynamic_and_range_versions_are_rejected(self) -> None:
        for version in ("1.+", "latest.release", "[1.0,2.0)"):
            _, errors = self.audit(
                f"""
[versions]
dynamic = "{version}"
[libraries]
dynamic-lib = {{ module = "example:dynamic", version.ref = "dynamic" }}
[plugins]
"""
            )
            self.assertTrue(any("Dynamic/range version is forbidden" in error for error in errors))

    def test_missing_version_reference_is_rejected(self) -> None:
        _, errors = self.audit(
            """
[versions]
stable = "1.0.0"
[libraries]
broken = { module = "example:broken", version.ref = "missing" }
[plugins]
"""
        )
        self.assertIn("Alias 'broken' references missing version key 'missing'", errors)

    def test_expired_allowlist_entry_is_rejected(self) -> None:
        policy = json.loads(json.dumps(VALID_POLICY))
        policy["transitive_prerelease_allowlist"][0]["expires_on"] = "2026-01-01"
        _, errors = self.audit(
            """
[versions]
stable = "1.0.0"
[libraries]
stable-lib = { module = "example:stable", version.ref = "stable" }
[plugins]
""",
            policy,
        )
        self.assertTrue(any("expired on 2026-01-01" in error for error in errors))

    def test_report_output_is_deterministic(self) -> None:
        inventory, errors = self.audit(
            """
[versions]
stable = "1.0.0"
[libraries]
stable-lib = { module = "example:stable", version.ref = "stable" }
[plugins]
"""
        )
        self.assertEqual([], errors)
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            json_path = root / "audit.json"
            markdown_path = root / "audit.md"
            write_reports(inventory, json_path, markdown_path)
            first_json = json_path.read_text(encoding="utf-8")
            first_markdown = markdown_path.read_text(encoding="utf-8")
            write_reports(inventory, json_path, markdown_path)
            self.assertEqual(first_json, json_path.read_text(encoding="utf-8"))
            self.assertEqual(first_markdown, markdown_path.read_text(encoding="utf-8"))

    def test_inline_allowlist_cannot_bypass_catalog_policy(self) -> None:
        policy = json.loads(json.dumps(VALID_POLICY))
        policy["catalog_inline_version_allowlist"] = ["inline"]
        _, errors = self.audit(
            """
[versions]
stable = "1.0.0"
[libraries]
inline = { module = "example:inline", version = "1.0.0" }
[plugins]
""",
            policy,
        )
        self.assertTrue(any("must remain empty" in error for error in errors))
        self.assertTrue(any("Inline version is not allowlisted" in error for error in errors))

    def test_unused_prerelease_version_key_is_rejected(self) -> None:
        _, errors = self.audit(
            """
[versions]
stable = "1.0.0"
unused-preview = "2.0.0-preview01"
[libraries]
stable-lib = { module = "example:stable", version.ref = "stable" }
[plugins]
"""
        )
        self.assertTrue(any("Unused pre-release version key is forbidden" in error for error in errors))

    def test_extended_prerelease_markers_are_rejected(self) -> None:
        for version in ("2.0.0-preview01", "2.0.0-eap-2", "2.0.0-SNAPSHOT", "2.0.0-M1"):
            _, errors = self.audit(
                f"""
[versions]
preview = "{version}"
[libraries]
preview-lib = {{ module = "example:preview", version.ref = "preview" }}
[plugins]
"""
            )
            self.assertTrue(
                any("Pre-release version is not allowlisted" in error for error in errors),
                version,
            )

    def test_single_quoted_literal_strings_preserve_hash_characters(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            catalog, errors = parse_catalog(
                write_catalog(
                    Path(temp_dir),
                    """
[versions]
stable = '1.0.0#literal' # trailing comment
[libraries]
literal = { module = 'example:literal#module', version.ref = 'stable' }
[plugins]
""",
                )
            )
        self.assertEqual([], errors)
        self.assertEqual("1.0.0#literal", catalog["versions"]["stable"])
        self.assertEqual("example:literal#module", catalog["libraries"]["literal"]["module"])
        self.assertEqual("stable", catalog["libraries"]["literal"]["version.ref"])


if __name__ == "__main__":
    unittest.main()
