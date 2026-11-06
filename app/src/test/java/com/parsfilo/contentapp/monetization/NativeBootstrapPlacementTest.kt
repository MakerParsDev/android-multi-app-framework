package com.parsfilo.contentapp.monetization

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.product.ContentFamily
import org.junit.Test

class NativeBootstrapPlacementTest {
    @Test
    fun contentHomeUsesContentPlacement() {
        assertThat(nativeBootstrapPlacement(ContentFamily.CONTENT))
            .isEqualTo(AdPlacement.NATIVE_FEED_CONTENT)
        assertThat(nativeBootstrapPlacement(ContentFamily.UNKNOWN))
            .isEqualTo(AdPlacement.NATIVE_FEED_CONTENT)
    }

    @Test
    fun zikirmatikHomeUsesZikirPlacement() {
        assertThat(nativeBootstrapPlacement(ContentFamily.ZIKIR_COUNTER))
            .isEqualTo(AdPlacement.NATIVE_FEED_ZIKIR)
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
            assertThat(nativeBootstrapPlacement(family))
                .isEqualTo(AdPlacement.NATIVE_FEED_HOME)
        }
    }
}
