package com.parsfilo.contentapp.feature.ads.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.ads.AdPlacement
import org.junit.Test

class BannerAdRequestKeyTest {
    private fun key(
        adUnitId: String = "banner-unit",
        placement: AdPlacement = AdPlacement.BANNER_HOME,
        route: String? = "home",
        widthDp: Int = 320,
        sizeWidth: Int = 320,
        sizeHeight: Int = 50,
        showPlacementLabels: Boolean = true,
    ) = BannerAdRequestKey(
        adUnitId = adUnitId,
        placement = placement,
        route = route,
        widthDp = widthDp,
        sizeWidth = sizeWidth,
        sizeHeight = sizeHeight,
        showPlacementLabels = showPlacementLabels,
    )

    @Test
    fun sameRequestIdentityReusesBannerView() {
        assertThat(key()).isEqualTo(key())
    }

    @Test
    fun placementAndRouteChangesCannotReuseBannerViewOrLoadState() {
        assertThat(key(placement = AdPlacement.BANNER_SETTINGS)).isNotEqualTo(key())
        assertThat(key(route = "settings")).isNotEqualTo(key())
        assertThat(key(route = null)).isNotEqualTo(key())
    }

    @Test
    fun sizingAndDisplayChangesCannotReuseBannerViewOrLoadState() {
        assertThat(key(adUnitId = "other-unit")).isNotEqualTo(key())
        assertThat(key(widthDp = 300)).isNotEqualTo(key())
        assertThat(key(sizeWidth = 300)).isNotEqualTo(key())
        assertThat(key(sizeHeight = 90)).isNotEqualTo(key())
        assertThat(key(showPlacementLabels = false)).isNotEqualTo(key())
    }
}
