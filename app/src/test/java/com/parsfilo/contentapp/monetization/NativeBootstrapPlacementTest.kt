package com.parsfilo.contentapp.monetization

import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.product.ContentFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeBootstrapPlacementTest {
    @Test
    fun contentHomeUsesContentPlacement() {
        assertEquals(AdPlacement.NATIVE_FEED_CONTENT, nativeBootstrapPlacement(ContentFamily.CONTENT))
        assertEquals(AdPlacement.NATIVE_FEED_CONTENT, nativeBootstrapPlacement(ContentFamily.UNKNOWN))
    }

    @Test
    fun zikirmatikHomeUsesZikirPlacement() {
        assertEquals(AdPlacement.NATIVE_FEED_ZIKIR, nativeBootstrapPlacement(ContentFamily.ZIKIR_COUNTER))
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
            assertEquals(AdPlacement.NATIVE_FEED_HOME, nativeBootstrapPlacement(family))
        }
    }
}
