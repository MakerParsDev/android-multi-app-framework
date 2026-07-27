#!/usr/bin/env python3
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


def banner_call_blocks(source: str) -> list[str]:
    blocks: list[str] = []
    search_from = 0
    marker = "BannerAd("
    while True:
        start = source.find(marker, search_from)
        if start < 0:
            return blocks

        cursor = start + len("BannerAd")
        depth = 0
        quote: str | None = None
        escaped = False
        line_comment = False
        block_comment = False

        while cursor < len(source):
            char = source[cursor]
            next_char = source[cursor + 1] if cursor + 1 < len(source) else ""

            if line_comment:
                if char == "\n":
                    line_comment = False
                cursor += 1
                continue
            if block_comment:
                if char == "*" and next_char == "/":
                    block_comment = False
                    cursor += 2
                else:
                    cursor += 1
                continue
            if quote is not None:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == quote:
                    quote = None
                cursor += 1
                continue
            if char == "/" and next_char == "/":
                line_comment = True
                cursor += 2
                continue
            if char == "/" and next_char == "*":
                block_comment = True
                cursor += 2
                continue
            if char in {'"', "'"}:
                quote = char
                cursor += 1
                continue
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    blocks.append(source[start : cursor + 1])
                    search_from = cursor + 1
                    break
            cursor += 1
        else:
            raise AssertionError(f"Unclosed BannerAd call at offset {start}")


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

    def test_failed_banner_load_collapses_the_ad_slot(self) -> None:
        component = (ROOT / "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/ui/BannerAd.kt").read_text()

        self.assertIn("BannerLoadState.FAILED", component)
        self.assertIn("loadState = BannerLoadState.FAILED", component)
        self.assertIn("loadState = BannerLoadState.LOADED", component)
        self.assertIn("if (loadState == BannerLoadState.FAILED) return@BoxWithConstraints", component)

    def test_long_content_keeps_a_top_banner_alongside_inline_ads(self) -> None:
        source = (ROOT / "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/NativeAdInsertionPolicy.kt").read_text()

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
            for block_index, block in enumerate(banner_call_blocks(path.read_text(errors="ignore"))):
                with self.subTest(path=str(path), block=block_index):
                    self.assertNotIn("padding(horizontal", block)

    def test_content_screens_declare_content_banner_placements(self) -> None:
        expected = {
            "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/ContentScreen.kt": "AdPlacement.BANNER_CONTENT_DETAIL",
            "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/prayer/PrayerListScreen.kt": "AdPlacement.BANNER_CONTENT_LIST",
            "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/miracles/MiraclesListScreen.kt": "AdPlacement.BANNER_CONTENT_LIST",
        }
        for relative_path, placement in expected.items():
            with self.subTest(path=relative_path):
                blocks = banner_call_blocks((ROOT / relative_path).read_text())
                self.assertEqual(len(blocks), 1)
                self.assertIn(f"placement = {placement}", blocks[0])


if __name__ == "__main__":
    unittest.main()
