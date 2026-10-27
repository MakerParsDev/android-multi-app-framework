"""Regression: Guava security floor must cover app plugin classpath and version catalog."""

import tempfile
import unittest
from pathlib import Path

from validate_supply_chain_policy import validate_transitive_security_overrides

COORDINATE = "com.google.guava:guava"
VERSION = "33.7.2-jre"
FORCE = f'force("{COORDINATE}:{VERSION}")'
OVERRIDES = [{"coordinate": COORDINATE, "version": VERSION, "reason": "Reviewed security floor for deserialization allocation advisory."}]


class GuavaPluginClasspathTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        (self.root / "gradle").mkdir()
        (self.root / "settings.gradle.kts").write_text(f"buildscript {{ {FORCE} }}")
        (self.root / "build.gradle.kts").write_text(
            f"buildscript {{ {FORCE} }}\n"
            f"allprojects {{\n"
            f"  buildscript {{ configurations.classpath {{ {FORCE} }} }}\n"
            f"  configurations.configureEach {{ {FORCE} }}\n"
            f"}}\n"
        )
        (self.root / "gradle/libs.versions.toml").write_text(f'guava = "{VERSION}"\n')

    def test_covers_all_three_gradle_classpaths_and_catalog(self):
        self.assertEqual(validate_transitive_security_overrides(self.root, OVERRIDES), [])

    def test_rejects_missing_app_plugin_override_even_if_root_and_project_are_forced(self):
        path = self.root / "build.gradle.kts"
        path.write_text(path.read_text().replace(
            f"buildscript {{ configurations.classpath {{ {FORCE} }} }}", "buildscript { }"
        ))
        errors = validate_transitive_security_overrides(self.root, OVERRIDES)
        self.assertTrue(any("allprojects buildscript" in error for error in errors), errors)

    def test_rejects_stale_version_catalog_despite_forced_resolution(self):
        (self.root / "gradle/libs.versions.toml").write_text('guava = "33.7.1-jre"\n')
        errors = validate_transitive_security_overrides(self.root, OVERRIDES)
        self.assertTrue(any("versions.toml Guava" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
