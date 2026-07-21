package com.parsfilo.contentapp.feature.ads

/**
 * AdMob reklam birim kimlikleri — ContentApp
 *
 * Production ID'leri local.properties veya CI ortam değişkenlerinden
 * BuildConfig aracılığıyla inject edilir. Kaynak kodda hardcode edilmez.
 *
 * Debug build'lerde Google'ın resmi test ID'leri kullanılır.
 *
 * @see <a href="https://developers.google.com/admob/android/test-ads">AdMob Test Ads</a>
 */
object AdUnitIds {

    // ── Google Official Test Ad Unit IDs ────────────────────────

    object Test {
        const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
        const val BANNER = "ca-app-pub-3940256099942544/9214589741"
        const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
        const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
        const val REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379"
        const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    }

    // ── Production IDs — injected via BuildConfig from local.properties ──

    /**
     * Production ID'leri local.properties dosyasından okunur:
     *
     * ```properties
     * ADMOB_APP_ID=ca-app-pub-XXXXXXX~XXXXXXX
     * ADMOB_BANNER_ID=ca-app-pub-XXXXXXX/XXXXXXX
     * ADMOB_INTERSTITIAL_ID=ca-app-pub-XXXXXXX/XXXXXXX
     * ADMOB_NATIVE_ID=ca-app-pub-XXXXXXX/XXXXXXX
     * ADMOB_REWARDED_ID=ca-app-pub-XXXXXXX/XXXXXXX
     * ADMOB_REWARDED_INTERSTITIAL_ID=ca-app-pub-XXXXXXX/XXXXXXX
     * ADMOB_APP_OPEN_ID=ca-app-pub-XXXXXXX/XXXXXXX
     * ```
     */

    object Production {
        val APP_ID: String get() = BuildConfig.ADMOB_APP_ID
        val BANNER: String get() = BuildConfig.ADMOB_BANNER_ID
        val INTERSTITIAL: String get() = BuildConfig.ADMOB_INTERSTITIAL_ID
        val NATIVE: String get() = BuildConfig.ADMOB_NATIVE_ID
        val REWARDED: String get() = BuildConfig.ADMOB_REWARDED_ID
        val REWARDED_INTERSTITIAL: String get() = BuildConfig.ADMOB_REWARDED_INTERSTITIAL_ID
        val APP_OPEN: String get() = BuildConfig.ADMOB_APP_OPEN_ID
    }

    /**
     * BuildConfig.DEBUG'a göre doğru ID'yi döndürür.
     * Debug → Test ID, Release → Production ID
     */
    fun banner(isDebug: Boolean): String = if (isDebug) Test.BANNER else Production.BANNER

    fun interstitial(isDebug: Boolean): String =
        if (isDebug) Test.INTERSTITIAL else Production.INTERSTITIAL

    fun native(isDebug: Boolean): String = if (isDebug) Test.NATIVE else Production.NATIVE

    fun rewarded(isDebug: Boolean): String = if (isDebug) Test.REWARDED else Production.REWARDED

    fun rewardedInterstitial(isDebug: Boolean): String =
        if (isDebug) Test.REWARDED_INTERSTITIAL else Production.REWARDED_INTERSTITIAL

    fun appOpen(isDebug: Boolean): String = if (isDebug) Test.APP_OPEN else Production.APP_OPEN
}
