package com.parsfilo.contentapp.monetization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenReadingResumePolicyTest {
    @Test
    fun returningToVisibleReadingSurfacesDoesNotRequestAppOpenAds() {
        listOf(
            "content",
            "miracles_detail/{prayerIndex}",
            "miracles_detail/2",
            "prayer_detail/{prayerId}",
            "prayer_detail/12",
            "quran_sura_detail/{suraNumber}?ayahNumber={ayahNumber}",
            "quran_sura_detail/2?ayahNumber=13",
        ).forEach { route ->
            assertTrue("Reading should stay uninterrupted on $route", isReadingSurfaceForAppOpenResume(route))
        }
    }

    @Test
    fun listAndNonReadingDestinationsRetainEligibleAppOpenPlacements() {
        listOf(
            "miracles_list",
            "prayer_list",
            "quran_sura_list",
            "quran_bookmarks",
            "settings",
            "message_list",
            "subscription",
            "content_list",
            "quran_sura_detail_extra/2",
        ).forEach { route ->
            assertFalse("Non-reader route should preserve placement policy on $route", isReadingSurfaceForAppOpenResume(route))
        }
        assertFalse(isReadingSurfaceForAppOpenResume(null))
    }
}
