package com.parsfilo.contentapp.monetization

import com.parsfilo.contentapp.R
import com.parsfilo.contentapp.feature.ads.AdFormat
import com.parsfilo.contentapp.feature.ads.AdPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppAdUnitIdsTest {
    private val ids =
        AppAdUnitIds.Ids(
            banner = "banner-default",
            interstitial = "interstitial-default",
            native = "native-default",
            rewarded = "rewarded-default",
            rewardedInterstitial = "rewarded-inter-default",
            appOpen = "app-open-default",
        )

    @Test
    fun `placement value wins when present`() {
        val resolved = AppAdUnitIds.placementOrDefault("banner-home", ids, AdFormat.BANNER)
        assertEquals("banner-home", resolved)
    }

    @Test
    fun `falls back to format default when placement missing`() {
        val resolved = AppAdUnitIds.placementOrDefault(null, ids, AdFormat.INTERSTITIAL)
        assertEquals("interstitial-default", resolved)
    }

    @Test
    fun `blank placement falls back to default`() {
        val resolved = AppAdUnitIds.placementOrDefault(" ", ids, AdFormat.APP_OPEN)
        assertEquals("app-open-default", resolved)
    }

    @Test
    fun `placement resources are mapped without reflection`() {
        val expected =
            mapOf(
                AdPlacement.BANNER_HOME to R.string.ad_unit_banner_home,
                AdPlacement.BANNER_SETTINGS to R.string.ad_unit_banner_settings,
                AdPlacement.BANNER_CONTENT_LIST to R.string.ad_unit_banner_content_list,
                AdPlacement.BANNER_CONTENT_DETAIL to R.string.ad_unit_banner_content_detail,
                AdPlacement.BANNER_QIBLA to R.string.ad_unit_banner_qibla,
                AdPlacement.BANNER_ZIKIR to R.string.ad_unit_banner_zikir,
                AdPlacement.NATIVE_FEED_HOME to R.string.ad_unit_native_feed_home,
                AdPlacement.NATIVE_FEED_CONTENT to R.string.ad_unit_native_feed_content,
                AdPlacement.NATIVE_FEED_ZIKIR to R.string.ad_unit_native_feed_zikir,
                AdPlacement.INTERSTITIAL_NAV_BREAK to R.string.ad_unit_interstitial_nav_break,
                AdPlacement.APP_OPEN_RESUME to R.string.ad_unit_open_app_resume,
                AdPlacement.REWARDED_REWARDS_SCREEN to R.string.ad_unit_rewarded_rewards_screen,
                AdPlacement.REWARDED_INTERSTITIAL_HISTORY_UNLOCK to
                    R.string.ad_unit_rewarded_interstitial_history_unlock,
            )

        assertEquals(
            AdPlacement.entries.filter { it.resourceName != null }.toSet(),
            expected.keys,
        )
        expected.forEach { (placement, resourceId) ->
            assertEquals(resourceId, AppAdUnitIds.resourceIdForPlacement(placement))
        }
    }

    @Test
    fun `default placements do not require dedicated resources`() {
        AdFormat.entries.forEach { format ->
            assertNull(AppAdUnitIds.resourceIdForPlacement(AdPlacement.defaultFor(format)))
        }
    }
}
