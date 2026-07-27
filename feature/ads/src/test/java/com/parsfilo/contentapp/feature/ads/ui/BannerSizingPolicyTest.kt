package com.parsfilo.contentapp.feature.ads.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.ads.AdPlacement
import org.junit.Test

class BannerSizingPolicyTest {
    @Test
    fun `content placements use capped inline adaptive banners`() {
        val listPolicy = AdPlacement.BANNER_CONTENT_LIST.bannerSizingPolicy()
        val detailPolicy = AdPlacement.BANNER_CONTENT_DETAIL.bannerSizingPolicy()

        assertThat(listPolicy.mode).isEqualTo(BannerSizingMode.INLINE_ADAPTIVE)
        assertThat(detailPolicy.mode).isEqualTo(BannerSizingMode.INLINE_ADAPTIVE)
        assertThat(listPolicy.maxHeightDp).isEqualTo(80)
        assertThat(detailPolicy.maxHeightDp).isEqualTo(80)
    }

    @Test
    fun `non content placements use fixed standard banners`() {
        val anchoredPlacements = listOf(
            AdPlacement.BANNER_HOME,
            AdPlacement.BANNER_SETTINGS,
            AdPlacement.BANNER_QIBLA,
            AdPlacement.BANNER_ZIKIR,
            AdPlacement.BANNER_DEFAULT,
        )

        anchoredPlacements.forEach { placement ->
            val policy = placement.bannerSizingPolicy()
            assertThat(policy.mode).isEqualTo(BannerSizingMode.FIXED_STANDARD)
            assertThat(policy.maxHeightDp).isNull()
        }
    }
}
