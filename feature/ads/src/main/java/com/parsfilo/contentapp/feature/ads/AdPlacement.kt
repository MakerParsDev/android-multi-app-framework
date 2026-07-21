package com.parsfilo.contentapp.feature.ads

enum class AdPlacement(
    val format: AdFormat,
    val resourceName: String? = null,
    val analyticsValue: String,
) {
    BANNER_HOME(AdFormat.BANNER, "ad_unit_banner_home", "banner_home"),
    BANNER_SETTINGS(AdFormat.BANNER, "ad_unit_banner_settings", "banner_settings"),
    BANNER_CONTENT_LIST(AdFormat.BANNER, "ad_unit_banner_content_list", "banner_content_list"),
    BANNER_CONTENT_DETAIL(AdFormat.BANNER, "ad_unit_banner_content_detail", "banner_content_detail"),
    BANNER_QIBLA(AdFormat.BANNER, "ad_unit_banner_qibla", "banner_qibla"),
    BANNER_ZIKIR(AdFormat.BANNER, "ad_unit_banner_zikir", "banner_zikir"),
    BANNER_DEFAULT(AdFormat.BANNER, null, "banner_default"),

    NATIVE_FEED_HOME(AdFormat.NATIVE, "ad_unit_native_feed_home", "native_feed_home"),
    NATIVE_FEED_CONTENT(AdFormat.NATIVE, "ad_unit_native_feed_content", "native_feed_content"),
    NATIVE_FEED_ZIKIR(AdFormat.NATIVE, "ad_unit_native_feed_zikir", "native_feed_zikir"),
    NATIVE_DEFAULT(AdFormat.NATIVE, null, "native_default"),

    INTERSTITIAL_NAV_BREAK(AdFormat.INTERSTITIAL, "ad_unit_interstitial_nav_break", "interstitial_nav_break"),
    INTERSTITIAL_DEFAULT(AdFormat.INTERSTITIAL, null, "interstitial_default"),

    APP_OPEN_RESUME(AdFormat.APP_OPEN, "ad_unit_open_app_resume", "app_open_resume"),
    APP_OPEN_DEFAULT(AdFormat.APP_OPEN, null, "app_open_default"),

    REWARDED_REWARDS_SCREEN(AdFormat.REWARDED, "ad_unit_rewarded_rewards_screen", "rewarded_rewards_screen"),
    REWARDED_DEFAULT(AdFormat.REWARDED, null, "rewarded_default"),

    REWARDED_INTERSTITIAL_HISTORY_UNLOCK(
        AdFormat.REWARDED_INTERSTITIAL,
        "ad_unit_rewarded_interstitial_history_unlock",
        "rewarded_interstitial_history_unlock",
    ),
    REWARDED_INTERSTITIAL_DEFAULT(AdFormat.REWARDED_INTERSTITIAL, null, "rewarded_interstitial_default"),
    ;

    companion object {
        fun defaultFor(format: AdFormat): AdPlacement =
            when (format) {
                AdFormat.BANNER -> BANNER_DEFAULT
                AdFormat.NATIVE -> NATIVE_DEFAULT
                AdFormat.INTERSTITIAL -> INTERSTITIAL_DEFAULT
                AdFormat.APP_OPEN -> APP_OPEN_DEFAULT
                AdFormat.REWARDED -> REWARDED_DEFAULT
                AdFormat.REWARDED_INTERSTITIAL -> REWARDED_INTERSTITIAL_DEFAULT
            }
    }
}
