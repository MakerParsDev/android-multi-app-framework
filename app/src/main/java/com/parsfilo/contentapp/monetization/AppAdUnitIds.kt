package com.parsfilo.contentapp.monetization

import android.content.Context
import com.parsfilo.contentapp.R
import com.parsfilo.contentapp.feature.ads.AdFormat
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.feature.ads.AdUnitIds
import timber.log.Timber

/**
 * Resolves AdMob unit IDs for the current app/flavor.
 *
 * Why this exists:
 * - CI builds (Azure) don't have local.properties, so feature:ads BuildConfig.ADMOB_* can be empty.
 * - The per-flavor production IDs already live in app/src/<flavor>/res/values/ads.xml.
 *
 * AdMob IDs are not secrets; using resources keeps the build reproducible and flavor-correct.
 */
object AppAdUnitIds {
    @Volatile
    private var rewardedInterstitialFallbackWarningLogged = false
    const val GOOGLE_TEST_PUBLISHER_PREFIX = "ca-app-pub-3940256099942544"

    data class Ids(
        val banner: String,
        val interstitial: String,
        val native: String,
        val rewarded: String,
        val rewardedInterstitial: String,
        val appOpen: String,
    )

    fun resolve(
        context: Context,
        useTestAds: Boolean,
    ): Ids =
        if (useTestAds) {
            Ids(
                banner = AdUnitIds.Test.BANNER,
                interstitial = AdUnitIds.Test.INTERSTITIAL,
                native = AdUnitIds.Test.NATIVE,
                rewarded = AdUnitIds.Test.REWARDED,
                rewardedInterstitial = AdUnitIds.Test.REWARDED_INTERSTITIAL,
                appOpen = AdUnitIds.Test.APP_OPEN,
            )
        } else {
            val rewarded = context.getString(R.string.ad_unit_rewarded)
            val rewardedInterstitial =
                context.getString(R.string.ad_unit_rewarded_interstitial).takeIf { it.isNotBlank() }
                    ?: rewarded.also {
                        if (!rewardedInterstitialFallbackWarningLogged) {
                            rewardedInterstitialFallbackWarningLogged = true
                            Timber.w(
                                "Blank ad_unit_rewarded_interstitial in ads.xml for %s. Falling back to rewarded unit id.",
                                context.packageName,
                            )
                        }
                    }
            Ids(
                banner = context.getString(R.string.ad_unit_banner),
                interstitial = context.getString(R.string.ad_unit_interstitial),
                native = context.getString(R.string.ad_unit_native),
                rewarded = rewarded,
                rewardedInterstitial = rewardedInterstitial,
                appOpen = context.getString(R.string.ad_unit_open_app),
            )
        }

    fun resolvePlacement(
        context: Context,
        placement: AdPlacement,
        useTestAds: Boolean,
    ): String {
        val ids = resolve(context, useTestAds)
        val placementValue =
            resourceIdForPlacement(placement)
                ?.let(context::getString)
                ?.takeIf { it.isNotBlank() }
        return placementOrDefault(placementValue, ids, placement.format)
    }

    internal fun defaultIdForFormat(
        ids: Ids,
        format: AdFormat,
    ): String =
        when (format) {
            AdFormat.BANNER -> ids.banner
            AdFormat.NATIVE -> ids.native
            AdFormat.INTERSTITIAL -> ids.interstitial
            AdFormat.APP_OPEN -> ids.appOpen
            AdFormat.REWARDED -> ids.rewarded
            AdFormat.REWARDED_INTERSTITIAL -> ids.rewardedInterstitial
        }

    internal fun placementOrDefault(
        placementValue: String?,
        ids: Ids,
        format: AdFormat,
    ): String = placementValue?.takeIf { it.isNotBlank() } ?: defaultIdForFormat(ids, format)

    fun Ids.usesGoogleTestIds(): Boolean =
        listOf(banner, interstitial, native, rewarded, rewardedInterstitial, appOpen)
            .all { it.startsWith(GOOGLE_TEST_PUBLISHER_PREFIX) }

    internal fun resourceIdForPlacement(placement: AdPlacement): Int? =
        when (placement) {
            AdPlacement.BANNER_HOME -> R.string.ad_unit_banner_home
            AdPlacement.BANNER_SETTINGS -> R.string.ad_unit_banner_settings
            AdPlacement.BANNER_CONTENT_LIST -> R.string.ad_unit_banner_content_list
            AdPlacement.BANNER_CONTENT_DETAIL -> R.string.ad_unit_banner_content_detail
            AdPlacement.BANNER_QIBLA -> R.string.ad_unit_banner_qibla
            AdPlacement.BANNER_ZIKIR -> R.string.ad_unit_banner_zikir
            AdPlacement.NATIVE_FEED_HOME -> R.string.ad_unit_native_feed_home
            AdPlacement.NATIVE_FEED_CONTENT -> R.string.ad_unit_native_feed_content
            AdPlacement.NATIVE_FEED_ZIKIR -> R.string.ad_unit_native_feed_zikir
            AdPlacement.INTERSTITIAL_NAV_BREAK -> R.string.ad_unit_interstitial_nav_break
            AdPlacement.APP_OPEN_RESUME -> R.string.ad_unit_open_app_resume
            AdPlacement.REWARDED_REWARDS_SCREEN -> R.string.ad_unit_rewarded_rewards_screen
            AdPlacement.REWARDED_INTERSTITIAL_HISTORY_UNLOCK ->
                R.string.ad_unit_rewarded_interstitial_history_unlock
            AdPlacement.BANNER_DEFAULT,
            AdPlacement.NATIVE_DEFAULT,
            AdPlacement.INTERSTITIAL_DEFAULT,
            AdPlacement.APP_OPEN_DEFAULT,
            AdPlacement.REWARDED_DEFAULT,
            AdPlacement.REWARDED_INTERSTITIAL_DEFAULT,
            -> null
        }
}
