#!/usr/bin/env python3
"""Prevent utility-only Android flavors from shipping the unused Media3 service."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
NON_AUDIO = ("mucizedualar", "kible", "namazvakitleri", "zikirmatik")
AUDIO_SERVICE = "com.parsfilo.contentapp.feature.audio.service.AudioService"
MEDIA_PERMISSION = "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"


class NonAudioForegroundServiceContractTest(unittest.TestCase):
    def test_unused_asset_delivery_foreground_service_removed_for_all_flavors(self):
        root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        data_sync = [
            node for node in root.findall("uses-permission")
            if node.get(ANDROID + "name") == "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
        ]
        self.assertEqual(1, len(data_sync))
        self.assertEqual("remove", data_sync[0].get(TOOLS + "node"))
        extraction = [
            node for node in root.findall("./application/service")
            if node.get(ANDROID + "name") ==
            "com.google.android.play.core.assetpacks.ExtractionForegroundService"
        ]
        self.assertEqual(1, len(extraction))
        self.assertEqual("remove", extraction[0].get(TOOLS + "node"))
        self.assertNotIn(
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            [node.get(ANDROID + "name") for node in root.findall("uses-permission")
             if node.get(TOOLS + "node") == "remove"],
        )

    def test_disabled_asset_pack_manager_is_not_eagerly_injected(self):
        # Play Asset Delivery enables ExtractionForegroundService when constructed.
        # Our inactive asset pack has no manifest service, so it must never be
        # instantiated as a side effect of Hilt creating the audio ViewModel.
        viewmodel = (
            ROOT / "feature/audio/src/main/java/com/parsfilo/"
            "contentapp/feature/audio/ui/AudioPlayerViewModel.kt"
        ).read_text()
        self.assertIn("import dagger.Lazy", viewmodel)
        self.assertIn(
            "private val assetPackManagerProvider: Lazy<AssetPackManager>",
            viewmodel,
        )
        self.assertNotIn(
            "private val assetPackManager: AssetPackManager",
            viewmodel,
        )
        self.assertIn(
            "private val assetPackManager by lazy { assetPackManagerProvider.get() }",
            viewmodel,
        )
        self.assertIn("if (!useAssetPackAudio)", viewmodel)
        import re
        self.assertEqual(1, viewmodel.count("assetPackManagerProvider.get()"))
        calls = re.findall(r"assetPackManager\.([a-zA-Z]+)", viewmodel)
        self.assertGreaterEqual(len(calls), 7)
        self.assertNotIn("get", calls)

    def test_asset_pack_feature_is_globally_disabled_before_fgs_removal(self):
        import re
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertEqual(
            1,
            len(re.findall(
                r'buildConfigField\("boolean",\s*"USE_ASSET_PACK_AUDIO",\s*"false"\)',
                gradle,
            )),
        )
        self.assertNotRegex(
            gradle,
            r'buildConfigField\("boolean",\s*"USE_ASSET_PACK_AUDIO",\s*"true"\)',
        )
        settings = (ROOT / "settings.gradle.kts").read_text()
        self.assertNotIn('include(":audioassets")', settings)
        self.assertNotIn('include(":assetpack")', settings)

    def test_shared_module_still_declares_real_audio_service(self):
        root = ET.parse(ROOT / "feature/audio/src/main/AndroidManifest.xml").getroot()
        names = [x.get(ANDROID + "name") for x in root.findall("uses-permission")]
        self.assertIn(MEDIA_PERMISSION, names)
        service = root.find("./application/service")
        self.assertIsNotNone(service)
        self.assertEqual(".service.AudioService", service.get(ANDROID + "name"))
        self.assertEqual("mediaPlayback", service.get(ANDROID + "foregroundServiceType"))

    def test_utility_flavors_remove_only_unused_audio_permission_and_service(self):
        for flavor in NON_AUDIO:
            with self.subTest(flavor=flavor):
                root = ET.parse(ROOT / f"app/src/{flavor}/AndroidManifest.xml").getroot()
                removed = [
                    node for node in root.findall("uses-permission")
                    if node.get(ANDROID + "name") == MEDIA_PERMISSION
                ]
                self.assertEqual(1, len(removed))
                self.assertEqual("remove", removed[0].get(TOOLS + "node"))
                service = [
                    node for node in root.findall("./application/service")
                    if node.get(ANDROID + "name") == AUDIO_SERVICE
                ]
                self.assertEqual(1, len(service))
                self.assertEqual("remove", service[0].get(TOOLS + "node"))
                self.assertNotIn(
                    "android.permission.FOREGROUND_SERVICE",
                    [x.get(ANDROID + "name") for x in root.findall("uses-permission")
                     if x.get(TOOLS + "node") == "remove"],
                )

    def test_non_audio_family_allowlist_matches_flavor_configuration(self):
        text = (ROOT / "buildSrc/src/main/kotlin/FlavorConfig.kt").read_text()
        for flavor, family in (
            ("mucizedualar", "miracles"),
            ("kible", "qibla"),
            ("namazvakitleri", "prayer_times"),
            ("zikirmatik", "zikir_counter"),
        ):
            with self.subTest(flavor=flavor):
                section = text.split(f'name = "{flavor}"', 1)[1].split("FlavorConfig(", 1)[0]
                self.assertIn(f'contentFamily = "{family}"', section)
                self.assertNotIn("audioFileName =", section)

    def test_audio_flavors_do_not_remove_required_service(self):
        text = (ROOT / "buildSrc/src/main/kotlin/FlavorConfig.kt").read_text()
        import re
        flavors = re.findall(r'name = "([a-z_]+)"', text)
        self.assertEqual(17, len(flavors))
        for flavor in flavors:
            if flavor in NON_AUDIO:
                continue
            with self.subTest(flavor=flavor):
                path = ROOT / f"app/src/{flavor}/AndroidManifest.xml"
                if not path.exists():
                    continue
                root = ET.parse(path).getroot()
                for node in root.findall("uses-permission") + root.findall("./application/service"):
                    self.assertFalse(
                        node.get(TOOLS + "node") == "remove"
                        and node.get(ANDROID + "name") in (MEDIA_PERMISSION, AUDIO_SERVICE),
                        flavor,
                    )


if __name__ == "__main__":
    unittest.main()
