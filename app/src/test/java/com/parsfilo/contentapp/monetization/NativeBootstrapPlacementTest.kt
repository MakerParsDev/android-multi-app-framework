package com.parsfilo.contentapp.monetization

import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.product.ContentFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeBootstrapPlacementTest {
    private fun eagerPlacement(family: ContentFamily): AdPlacement? =
        nativeBootstrapPlacement(
            family = family,
            demandAwarePreloadEnabled = false,
            noInlineNativeSlot = false,
        )

    @Test
    fun contentHomeUsesContentPlacement() {
        assertEquals(AdPlacement.NATIVE_FEED_CONTENT, eagerPlacement(ContentFamily.CONTENT))
        assertEquals(AdPlacement.NATIVE_FEED_CONTENT, eagerPlacement(ContentFamily.UNKNOWN))
    }

    @Test
    fun zikirmatikHomeUsesZikirPlacement() {
        assertEquals(AdPlacement.NATIVE_FEED_ZIKIR, eagerPlacement(ContentFamily.ZIKIR_COUNTER))
    }

    @Test
    fun listAndNavigationHomeRoutesUseHomePlacement() {
        val homeFamilies = listOf(
            ContentFamily.PRAYER_LIBRARY,
            ContentFamily.MIRACLES,
            ContentFamily.ESMA,
            ContentFamily.PRAYER_TIMES,
            ContentFamily.QIBLA,
            ContentFamily.QURAN,
        )
        homeFamilies.forEach { family ->
            assertEquals(AdPlacement.NATIVE_FEED_HOME, eagerPlacement(family))
        }
    }

    @Test
    fun demandAwareModeNeverPreloadsAheadOfAnActualSlot() {
        ContentFamily.entries.forEach { family ->
            assertNull(
                nativeBootstrapPlacement(
                    family = family,
                    demandAwarePreloadEnabled = true,
                    noInlineNativeSlot = false,
                ),
            )
        }
    }

    @Test
    fun shortContentWithoutInlineSlotNeverPreloads() {
        ContentFamily.entries.forEach { family ->
            assertNull(
                nativeBootstrapPlacement(
                    family = family,
                    demandAwarePreloadEnabled = false,
                    noInlineNativeSlot = true,
                ),
            )
        }
    }
}
