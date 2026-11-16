"""Regression contract: banner ads belong above primary content in every flavor."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def source(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


class TopBannerPlacementTest(unittest.TestCase):
    def test_prayer_and_miracles_list_banners_are_above_scrollable_content(self):
        for relative in (
            "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/prayer/PrayerListScreen.kt",
            "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/miracles/MiraclesListScreen.kt",
        ):
            with self.subTest(screen=relative):
                screen = source(relative)
                self.assertEqual(screen.count("BannerAd("), 1)
                self.assertLess(screen.index("BannerAd("), screen.index("LazyColumn("))

    def test_prayer_times_banner_is_above_schedule_even_when_empty(self):
        screen = source("feature/prayertimes/src/main/java/com/parsfilo/contentapp/feature/prayertimes/ui/PrayerTimesScreen.kt")
        self.assertEqual(screen.count("bannerAdContent.invoke()"), 1)
        self.assertLess(screen.index("PrayerTimesAppHeader("), screen.index("bannerAdContent.invoke()"))
        self.assertLess(screen.index("bannerAdContent.invoke()"), screen.index("if (uiState.days.isEmpty())"))

    def test_prayer_times_empty_state_has_no_lower_native_ad(self):
        screen = source("feature/prayertimes/src/main/java/com/parsfilo/contentapp/feature/prayertimes/ui/PrayerTimesScreen.kt")
        self.assertIn("if (nativeAdContent != null && uiState.days.isNotEmpty())", screen)
        self.assertLess(screen.index("if (uiState.days.isEmpty())"), screen.index("nativeAdContent.invoke()"))

    def test_qibla_banner_precedes_compass_and_sensor_guidance(self):
        screen = source("feature/qibla/src/main/java/com/parsfilo/contentapp/feature/qibla/QiblaScreen.kt")
        self.assertEqual(screen.count("item { bannerAdContent() }"), 1)
        self.assertLess(screen.index("QiblaAppHeader("), screen.index("item { bannerAdContent() }"))
        self.assertLess(screen.index("item { bannerAdContent() }"), screen.index("item {\n                Card("))

    def test_counter_main_banner_precedes_counter_and_is_not_inside_scroll(self):
        screen = source("feature/counter/src/main/java/com/parsfilo/contentapp/feature/counter/ui/CounterScreen.kt")
        main = screen.split("} else {\n        BackHandler", 1)[1].split("if (uiState.showReminderSettings)", 1)[0]
        self.assertEqual(main.count("bannerAdContent?.invoke()"), 1)
        self.assertLess(main.index("bannerAdContent?.invoke()"), main.index("if (uiState.isLoading)"))
        self.assertLess(main.index("bannerAdContent?.invoke()"), main.index(".verticalScroll(scrollState)"))

    def test_quran_bookmarks_banner_is_above_list_not_scaffold_bottom_bar(self):
        screen = source("feature/quran/src/main/java/com/parsfilo/contentapp/feature/quran/ui/bookmarks/QuranBookmarksScreen.kt")
        self.assertNotIn("bottomBar = {", screen)
        self.assertEqual(screen.count("bannerAdContent?.invoke()"), 1)
        self.assertLess(screen.index("bannerAdContent?.invoke()"), screen.index("if (state.items.isEmpty())"))
        self.assertLess(screen.index("bannerAdContent?.invoke()"), screen.index("LazyColumn("))
        self.assertIn("Modifier.fillMaxWidth().height(MAX_INLINE_BANNER_HEIGHT_DP.dp)", screen)

    def test_reader_banner_never_moves_below_verses_in_landscape(self):
        policy = source("feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/NativeAdInsertionPolicy.kt")
        screen = source("feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui/ContentScreen.kt")
        self.assertNotIn("AFTER_VERSES", policy)
        self.assertNotIn("ReaderBannerPosition.AFTER_VERSES", screen)
        self.assertEqual(screen.count("BannerAd("), 1)

    def test_native_feed_fallback_cannot_create_an_inline_banner(self):
        native = source("feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/ui/NativeFeedAdSlot.kt")
        self.assertNotIn("BannerAd(", native)


    def test_existing_top_banner_routes_remain_above_primary_ui(self):
        paths = (
            (
                "feature/quran/src/main/java/com/parsfilo/contentapp/feature/quran/ui/surelist/QuranSuraListScreen.kt",
                "bannerAdContent?.invoke()",
                "LazyColumn(",
            ),
            (
                "feature/quran/src/main/java/com/parsfilo/contentapp/feature/quran/ui/suradetail/QuranSuraDetailScreen.kt",
                "bannerAdContent?.invoke()",
                "LazyColumn(",
            ),
            (
                "feature/prayertimes/src/main/java/com/parsfilo/contentapp/feature/prayertimes/ui/PrayerLocationPickerScreen.kt",
                "content?.invoke()",
                "val canSave =",
            ),
            (
                "feature/counter/src/main/java/com/parsfilo/contentapp/feature/counter/ui/components/ZikirSelectorSheet.kt",
                "content?.invoke()",
                "Spacer(modifier = Modifier.height(d.space8))",
            ),
        )
        for relative, banner, following in paths:
            with self.subTest(screen=relative):
                screen = source(relative)
                self.assertEqual(screen.count(banner), 1)
                if "PrayerLocationPicker" in relative:
                    self.assertLess(screen.index(banner), screen.index("Column(\n                    modifier = Modifier"))
                else:
                    self.assertLess(screen.index(banner), screen.index(following))

if __name__ == "__main__":
    unittest.main()
