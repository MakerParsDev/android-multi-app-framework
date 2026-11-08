package com.parsfilo.contentapp.feature.ads.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.ads.AdPlacement
import org.junit.Test

class BannerSizingPolicyTest {
    @Test
    fun contentBannerNeverGrowsBeyondReaderWidth() {
        val policy = AdPlacement.BANNER_CONTENT_DETAIL.bannerSizingPolicy()
        assertThat(policy.requestWidthDp(280)).isEqualTo(280)
        assertThat(policy.requestWidthDp(320)).isEqualTo(320)
        assertThat(policy.requestWidthDp(360)).isEqualTo(360)
        assertThat(policy.requestWidthDp(411)).isEqualTo(360)
        assertThat(policy.requestWidthDp(840)).isEqualTo(360)
    }

    @Test
    fun standardBannerStaysWithinActualViewport() {
        val policy = AdPlacement.BANNER_DEFAULT.bannerSizingPolicy()
        assertThat(policy.requestWidthDp(280)).isEqualTo(0)
        assertThat(policy.requestWidthDp(319)).isEqualTo(0)
        assertThat(policy.requestWidthDp(320)).isEqualTo(320)
        assertThat(policy.requestWidthDp(411)).isEqualTo(320)
        assertThat(policy.requestWidthDp(840)).isEqualTo(320)
        assertThat(policy.requestWidthDp(0)).isEqualTo(0)
        assertThat(policy.requestWidthDp(-1)).isEqualTo(0)
    }

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
    fun `non content placements use bounded anchored standard policy`() {
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
