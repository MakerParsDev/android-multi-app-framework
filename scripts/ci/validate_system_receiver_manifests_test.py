from pathlib import Path
import sys
import shutil
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from validate_system_receiver_manifests import ManifestValidationError, validate_manifest


class SystemReceiverManifestValidationTest(unittest.TestCase):
    def test_accepts_system_only_receiver_with_exact_allowlist(self) -> None:
        manifest = self._manifest(exported="false")
        result = validate_manifest("zikirmatik", manifest)
        self.assertFalse(result["exported"])
        self.assertEqual(4, len(result["actions"]))

    def test_rejects_exported_receiver(self) -> None:
        manifest = self._manifest(exported="true")
        with self.assertRaisesRegex(ManifestValidationError, "exported"):
            validate_manifest("zikirmatik", manifest)

    def test_rejects_unexpected_action(self) -> None:
        manifest = self._manifest(
            exported="false",
            extra_action="com.attacker.FORCE_RESCHEDULE",
        )
        with self.assertRaisesRegex(ManifestValidationError, "unexpected"):
            validate_manifest("zikirmatik", manifest)

    def test_build_wiring_uses_agp_merged_manifest_artifact(self) -> None:
        root_dir = Path(__file__).resolve().parents[2]
        root_build = (root_dir / "build.gradle.kts").read_text(encoding="utf-8")
        app_build = (root_dir / "app/build.gradle.kts").read_text(encoding="utf-8")

        self.assertNotIn("intermediates/merged_manifest", root_build + app_build)
        self.assertIn("SingleArtifact.MERGED_MANIFEST", app_build)
        self.assertIn(":app:validateSystemReceiverManifests", root_build)

    def _manifest(self, *, exported: str, extra_action: str | None = None) -> Path:
        actions = [
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.MY_PACKAGE_REPLACED",
        ]
        if extra_action:
            actions.append(extra_action)
        action_xml = "\n".join(
            f'<action android:name="{action}" />' for action in actions
        )
        temp_dir = Path(tempfile.mkdtemp())
        path = temp_dir / "AndroidManifest.xml"
        path.write_text(
            f'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application>
    <receiver
      android:name="com.parsfilo.contentapp.feature.counter.alarm.ZikirSystemBroadcastReceiver"
      android:enabled="true"
      android:exported="{exported}">
      <intent-filter>{action_xml}</intent-filter>
    </receiver>
  </application>
</manifest>
''',
            encoding="utf-8",
        )
        self.addCleanup(shutil.rmtree, temp_dir, True)
        return path


if __name__ == "__main__":
    unittest.main()
