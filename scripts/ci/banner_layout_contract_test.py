#!/usr/bin/env python3
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class BannerLayoutContractTest(unittest.TestCase):
    def test_banner_component_uses_fixed_or_bounded_sizes_without_internal_padding(self) -> None:
        component = (ROOT / "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/ui/BannerAd.kt").read_text()
        policy = (ROOT / "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/ui/BannerSizingPolicy.kt").read_text()

        self.assertNotIn("getLargeAnchoredAdaptiveBannerAdSize", component)
        self.assertIn("AdSize.BANNER", component)
        self.assertIn("getInlineAdaptiveBannerAdSize", component)
        self.assertIn("MAX_INLINE_BANNER_HEIGHT_DP = 80", policy)
        self.assertNotIn("LocalDimens", component)
        self.assertNotIn(".padding(horizontal = dimens.space6)", component)

    def test_unfilled_banner_keeps_sdk_view_attached_for_refresh(self) -> None:
        component = (ROOT / "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/ui/BannerAd.kt").read_text()
        self.assertIn("Keep the view attached after a failed load", component)
        self.assertIn("Lifecycle.Event.ON_RESUME", component)
        self.assertIn("AndroidView(", component)
        self.assertNotIn("if (loadState == BannerLoadState.FAILED) return@BoxWithConstraints", component)

    def test_reader_top_banner_reserves_stable_tap_safe_height(self) -> None:
        source = (ROOT / "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/ContentScreen.kt").read_text()
        policy = (ROOT / "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/ui/BannerSizingPolicy.kt").read_text()

        top = source.split("if (bannerPosition == ReaderBannerPosition.ABOVE_VERSES) {", 1)[1].split("// Gold divider", 1)[0]
        bottom = source.split("if (bannerPosition == ReaderBannerPosition.AFTER_VERSES) {", 1)[1].split("BottomModeSelector(", 1)[0]

        self.assertIn("const val MAX_INLINE_BANNER_HEIGHT_DP = 80", policy)
        self.assertIn("val showTopBanner = uiState.shouldShowAds &&", source)
        self.assertIn("Box(", top)
        self.assertIn("Modifier.fillMaxWidth().height(MAX_INLINE_BANNER_HEIGHT_DP.dp)", top)
        self.assertIn("BannerAd(", top)
        self.assertNotIn("MAX_INLINE_BANNER_HEIGHT_DP", bottom)

    def test_long_content_keeps_a_top_banner_alongside_inline_ads(self) -> None:
        source = (ROOT / "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/NativeAdInsertionPolicy.kt").read_text(encoding="utf-8")

        self.assertIn(
            "shouldShowTopBannerForScrollableContent(totalContentItems: Int): Boolean =\n    totalContentItems > 0",
            source,
        )
        self.assertNotIn(
            "totalContentItems > 0 && !shouldPreferInlineFeedAds(totalContentItems)",
            source,
        )

    def test_banner_call_sites_do_not_reduce_available_width(self) -> None:
        for path in ROOT.rglob("*.kt"):
            if "/build/" in str(path) or path.name == "BannerAd.kt":
                continue
            lines = path.read_text(errors="ignore").splitlines()
            for index, line in enumerate(lines):
                if "BannerAd(" not in line:
                    continue
                block = "\n".join(lines[index : index + 10])
                with self.subTest(path=str(path), line=index + 1):
                    self.assertNotIn("padding(horizontal", block)

    def test_content_screens_declare_content_banner_placements(self) -> None:
        expected = {
            "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/ContentScreen.kt": "AdPlacement.BANNER_CONTENT_DETAIL",
            "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/prayer/PrayerListScreen.kt": "AdPlacement.BANNER_CONTENT_LIST",
            "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/miracles/MiraclesListScreen.kt": "AdPlacement.BANNER_CONTENT_LIST",
        }
        for relative_path, placement in expected.items():
            with self.subTest(path=relative_path):
                source = (ROOT / relative_path).read_text(encoding="utf-8")
                self.assertIn(f"placement = {placement}", source)


if __name__ == "__main__":
    unittest.main()
