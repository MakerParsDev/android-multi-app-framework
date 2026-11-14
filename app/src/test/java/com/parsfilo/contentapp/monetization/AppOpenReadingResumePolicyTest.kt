package com.parsfilo.contentapp.monetization

import com.parsfilo.contentapp.feature.ads.AdSuppressReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenReadingResumePolicyTest {
    @Test
    fun staleAppOpenCannotInterruptNewReadingRoute() {
        assertEquals(
            AdSuppressReason.CONTENT_IN_PROGRESS,
            appOpenLatePresentationBlockReason("quran_sura_list", "quran_sura_detail/4?ayahNumber=1", false),
        )
        assertEquals(
            AdSuppressReason.CONTENT_IN_PROGRESS,
            appOpenLatePresentationBlockReason("miracles_list", "miracles_detail/2", false),
        )
    }

    @Test
    fun navigationChangeOrNewActiveReadingInvalidatesPendingAppOpen() {
        assertEquals(
            AdSuppressReason.APP_LIFECYCLE,
            appOpenLatePresentationBlockReason("quran_sura_list", "quran_bookmarks", false),
        )
        assertEquals(
            AdSuppressReason.CONTENT_IN_PROGRESS,
            appOpenLatePresentationBlockReason("content_list", "content_list", true),
        )
    }

    @Test
    fun unchangedNonReadingRouteKeepsUsualAppOpenEligibility() {
        assertNull(appOpenLatePresentationBlockReason("quran_sura_list", "quran_sura_list", false))
        assertNull(appOpenLatePresentationBlockReason(null, null, false))
        assertNull(appOpenLatePresentationBlockReason("miracles_list", "miracles_list", false))
    }

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
