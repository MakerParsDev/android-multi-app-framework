package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.firebase.config.RemoteConfigManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.Test

class AdsPolicyProviderTest {

    @Test
    fun `getPolicy sanitizes invalid values and parses placement CSV`() {
        val remoteConfigManager = mockRemoteConfig(
            longs = mapOf(
                AdsPolicyProvider.KEY_INTERSTITIAL_FREQUENCY_CAP_MS to 1L, // invalid -> fallback
                AdsPolicyProvider.KEY_INTERSTITIAL_RELAXED_FREQUENCY_CAP_MS to 10L, // invalid -> fallback
                AdsPolicyProvider.KEY_APP_OPEN_COOLDOWN_MS to 999_999_999L, // invalid -> fallback
                AdsPolicyProvider.KEY_APP_OPEN_MAX_PER_SESSION to 99L, // invalid -> fallback
                AdsPolicyProvider.KEY_INTERSTITIAL_MAX_PER_SESSION to -1L, // invalid -> fallback
                AdsPolicyProvider.KEY_REWARDED_MAX_PER_SESSION to 99L, // invalid -> fallback
                AdsPolicyProvider.KEY_REWARDED_INTERSTITIAL_MIN_INTERVAL_MS to -1L, // invalid -> fallback
                AdsPolicyProvider.KEY_REWARDED_INTERSTITIAL_MAX_PER_SESSION to 99L, // invalid -> fallback
                AdsPolicyProvider.KEY_NATIVE_POOL_MAX to 0L, // invalid -> fallback
                AdsPolicyProvider.KEY_NATIVE_TTL_MS to 1L, // invalid -> fallback
                AdsPolicyProvider.KEY_REPORT_FRESHNESS_MAX_HOURS to 0L, // invalid -> fallback
                AdsPolicyProvider.KEY_CONSENT_RETRY_BACKOFF_MINUTES to 500L, // invalid -> fallback
            ),
            strings = mapOf(
                AdsPolicyProvider.KEY_BANNER_ENABLED to "true",
                AdsPolicyProvider.KEY_NATIVE_ENABLED to "false",
                AdsPolicyProvider.KEY_BANNER_PLACEMENTS_DISABLED_CSV to
                    "banner_home, BANNER_QIBLA, unknown_placement",
                AdsPolicyProvider.KEY_NATIVE_PLACEMENTS_DISABLED_CSV to "NATIVE_FEED_HOME",
                AdsPolicyProvider.KEY_INTERSTITIAL_RELAXED_PACKAGES_CSV to
                    "com.parsfilo.yasinsuresi, com.parsfilo.mucizedualar",
                AdsPolicyProvider.KEY_INTERSTITIAL_AGGRESSIVE_PRELOAD_PACKAGES_CSV to
                    "com.parsfilo.namazsurelerivedualarsesli, com.parsfilo.mucizedualar",
                AdsPolicyProvider.KEY_APP_OPEN_AGGRESSIVE_PRELOAD_PACKAGES_CSV to
                    "com.parsfilo.namazsurelerivedualarsesli",
                AdsPolicyProvider.KEY_INTERSTITIAL_HOT_ROUTES_CSV to
                    "prayer_list, prayer_detail, miracles_detail",
                AdsPolicyProvider.KEY_INTERSTITIAL_NOT_LOADED_RECOVERY_ENABLED to "true",
                AdsPolicyProvider.KEY_REWARD_OFFER_ROUTES_CSV to
                    "home, content, prayer_list",
                AdsPolicyProvider.KEY_NATIVE_BANNER_FALLBACK_ENABLED to "true",
                AdsPolicyProvider.KEY_NATIVE_BANNER_FALLBACK_PACKAGES_CSV to
                    "com.parsfilo.mucizedualar, com.parsfilo.yasinsuresi",
            ),
        )

        val policy = AdsPolicyProvider(remoteConfigManager).getPolicy()

        assertThat(policy.interstitialFrequencyCapMs)
            .isEqualTo(AdsPolicyProvider.DEFAULT_INTERSTITIAL_FREQUENCY_CAP_MS)
        assertThat(policy.interstitialRelaxedFrequencyCapMs)
            .isEqualTo(AdsPolicyProvider.DEFAULT_INTERSTITIAL_RELAXED_FREQUENCY_CAP_MS)
        assertThat(policy.appOpenCooldownMs)
            .isEqualTo(AdsPolicyProvider.DEFAULT_APP_OPEN_COOLDOWN_MS)
        assertThat(policy.appOpenMaxPerSession)
            .isEqualTo(AdsPolicyProvider.DEFAULT_APP_OPEN_MAX_PER_SESSION)
        assertThat(policy.interstitialMaxPerSession)
            .isEqualTo(AdsPolicyProvider.DEFAULT_INTERSTITIAL_MAX_PER_SESSION)
        assertThat(policy.rewardedMaxPerSession)
            .isEqualTo(AdsPolicyProvider.DEFAULT_REWARDED_MAX_PER_SESSION)
        assertThat(policy.rewardedInterstitialMinIntervalMs)
            .isEqualTo(AdsPolicyProvider.DEFAULT_REWARDED_INTERSTITIAL_MIN_INTERVAL_MS)
        assertThat(policy.rewardedInterstitialMaxPerSession)
            .isEqualTo(AdsPolicyProvider.DEFAULT_REWARDED_INTERSTITIAL_MAX_PER_SESSION)
        assertThat(policy.reportFreshnessMaxHours)
            .isEqualTo(AdsPolicyProvider.DEFAULT_REPORT_FRESHNESS_MAX_HOURS)
        assertThat(policy.consentRetryBackoffMinutes)
            .isEqualTo(AdsPolicyProvider.DEFAULT_CONSENT_RETRY_BACKOFF_MINUTES)
        assertThat(policy.nativePoolMax).isEqualTo(AdsPolicyProvider.DEFAULT_NATIVE_POOL_MAX)
        assertThat(policy.nativeTtlMs).isEqualTo(AdsPolicyProvider.DEFAULT_NATIVE_TTL_MS)
        assertThat(policy.nativeExactPlacementOnly)
            .isEqualTo(AdsPolicyProvider.DEFAULT_NATIVE_EXACT_PLACEMENT_ONLY)

        assertThat(policy.bannerEnabled).isTrue()
        assertThat(policy.nativeEnabled).isFalse()
        assertThat(policy.bannerPlacementsDisabled)
            .containsExactly(
                AdPlacement.BANNER_HOME.analyticsValue,
                AdPlacement.BANNER_QIBLA.analyticsValue,
            )
        assertThat(policy.nativePlacementsDisabled)
            .containsExactly(AdPlacement.NATIVE_FEED_HOME.analyticsValue)
        assertThat(policy.interstitialRelaxedPackages)
            .containsExactly("com.parsfilo.yasinsuresi", "com.parsfilo.mucizedualar")
        assertThat(policy.interstitialAggressivePreloadPackages)
            .containsExactly(
                "com.parsfilo.namazsurelerivedualarsesli",
                "com.parsfilo.mucizedualar",
            )
        assertThat(policy.appOpenAggressivePreloadPackages)
            .containsExactly("com.parsfilo.namazsurelerivedualarsesli")
        assertThat(policy.rewardOfferRoutes)
            .containsExactly("home", "content", "prayer_list")
        assertThat(policy.interstitialHotRoutes)
            .containsExactly("prayer_list", "prayer_detail", "miracles_detail")
        assertThat(policy.interstitialNotLoadedRecoveryEnabled).isTrue()
        assertThat(policy.nativeBannerFallbackEnabled).isTrue()
        assertThat(policy.nativeBannerFallbackPackages)
            .containsExactly("com.parsfilo.mucizedualar", "com.parsfilo.yasinsuresi")
    }

    @Test
    fun `getPolicy keeps valid values in range`() {
        val remoteConfigManager = mockRemoteConfig(
            longs = mapOf(
                AdsPolicyProvider.KEY_INTERSTITIAL_FREQUENCY_CAP_MS to 180_000L,
                AdsPolicyProvider.KEY_INTERSTITIAL_RELAXED_FREQUENCY_CAP_MS to 360_000L,
                AdsPolicyProvider.KEY_APP_OPEN_COOLDOWN_MS to 300_000L,
                AdsPolicyProvider.KEY_APP_OPEN_MAX_PER_SESSION to 2L,
                AdsPolicyProvider.KEY_INTERSTITIAL_MAX_PER_SESSION to 4L,
                AdsPolicyProvider.KEY_REWARDED_MAX_PER_SESSION to 6L,
                AdsPolicyProvider.KEY_REWARDED_INTERSTITIAL_MIN_INTERVAL_MS to 1_200_000L,
                AdsPolicyProvider.KEY_REWARDED_INTERSTITIAL_MAX_PER_SESSION to 3L,
                AdsPolicyProvider.KEY_NATIVE_POOL_MAX to 3L,
                AdsPolicyProvider.KEY_NATIVE_TTL_MS to 2_400_000L,
                AdsPolicyProvider.KEY_REPORT_FRESHNESS_MAX_HOURS to 12L,
                AdsPolicyProvider.KEY_CONSENT_RETRY_BACKOFF_MINUTES to 45L,
            ),
            strings = mapOf(
                AdsPolicyProvider.KEY_BANNER_ENABLED to "false",
                AdsPolicyProvider.KEY_NATIVE_ENABLED to "true",
                AdsPolicyProvider.KEY_BANNER_PLACEMENTS_DISABLED_CSV to "",
                AdsPolicyProvider.KEY_NATIVE_PLACEMENTS_DISABLED_CSV to "native_feed_zikir",
                AdsPolicyProvider.KEY_INTERSTITIAL_RELAXED_PACKAGES_CSV to "com.parsfilo.zikirmatik",
                AdsPolicyProvider.KEY_INTERSTITIAL_AGGRESSIVE_PRELOAD_PACKAGES_CSV to "",
                AdsPolicyProvider.KEY_APP_OPEN_AGGRESSIVE_PRELOAD_PACKAGES_CSV to "",
                AdsPolicyProvider.KEY_INTERSTITIAL_HOT_ROUTES_CSV to "",
                AdsPolicyProvider.KEY_INTERSTITIAL_NOT_LOADED_RECOVERY_ENABLED to "false",
                AdsPolicyProvider.KEY_APP_OPEN_ROUTE_BLOCKLIST_CSV to "subscription,rewards",
                AdsPolicyProvider.KEY_INTERSTITIAL_ROUTE_BLOCKLIST_CSV to "settings",
                AdsPolicyProvider.KEY_REWARD_OFFER_ROUTES_CSV to "home,content",
                AdsPolicyProvider.KEY_NATIVE_BANNER_FALLBACK_ENABLED to "true",
                AdsPolicyProvider.KEY_NATIVE_BANNER_FALLBACK_PACKAGES_CSV to "com.parsfilo.zikirmatik",
            ),
        )

        val policy = AdsPolicyProvider(remoteConfigManager).getPolicy()

        assertThat(policy.interstitialFrequencyCapMs).isEqualTo(180_000L)
        assertThat(policy.interstitialRelaxedFrequencyCapMs).isEqualTo(360_000L)
        assertThat(policy.appOpenCooldownMs).isEqualTo(300_000L)
        assertThat(policy.appOpenMaxPerSession).isEqualTo(2)
        assertThat(policy.interstitialMaxPerSession).isEqualTo(4)
        assertThat(policy.rewardedMaxPerSession).isEqualTo(6)
        assertThat(policy.rewardedInterstitialMinIntervalMs).isEqualTo(1_200_000L)
        assertThat(policy.rewardedInterstitialMaxPerSession).isEqualTo(3)
        assertThat(policy.reportFreshnessMaxHours).isEqualTo(12)
        assertThat(policy.consentRetryBackoffMinutes).isEqualTo(45)
        assertThat(policy.nativePoolMax).isEqualTo(3)
        assertThat(policy.nativeTtlMs).isEqualTo(2_400_000L)
        assertThat(policy.bannerEnabled).isFalse()
        assertThat(policy.nativeEnabled).isTrue()
        assertThat(policy.nativePlacementsDisabled)
            .containsExactly(AdPlacement.NATIVE_FEED_ZIKIR.analyticsValue)
        assertThat(policy.interstitialFrequencyCapForPackage("com.parsfilo.zikirmatik"))
            .isEqualTo(360_000L)
        assertThat(policy.interstitialFrequencyCapForPackage("com.parsfilo.kible"))
            .isEqualTo(180_000L)
        assertThat(policy.nativeExactPlacementOnly).isFalse()
        assertThat(policy.interstitialAggressivePreloadPackages).isEmpty()
        assertThat(policy.appOpenAggressivePreloadPackages).isEmpty()
        assertThat(policy.interstitialHotRoutes).isEmpty()
        assertThat(policy.interstitialNotLoadedRecoveryEnabled).isFalse()
        assertThat(policy.appOpenRouteBlocklist).containsExactly("subscription", "rewards")
        assertThat(policy.interstitialRouteBlocklist).containsExactly("settings")
        assertThat(policy.rewardOfferRoutes).containsExactly("home", "content")
        assertThat(policy.nativeBannerFallbackEnabled).isTrue()
        assertThat(policy.nativeBannerFallbackPackages).containsExactly("com.parsfilo.zikirmatik")
    }

    @Test
    fun `global emergency switch disables every ad format`() {
        val remoteConfigManager =
            mockRemoteConfig(
                longs = emptyMap(),
                strings =
                    mapOf(
                        AdsPolicyProvider.KEY_GLOBAL_EMERGENCY_DISABLED to "true",
                        AdsPolicyProvider.KEY_BANNER_ENABLED to "true",
                        AdsPolicyProvider.KEY_NATIVE_ENABLED to "true",
                        AdsPolicyProvider.KEY_APP_OPEN_ENABLED to "true",
                        AdsPolicyProvider.KEY_INTERSTITIAL_ENABLED to "true",
                        AdsPolicyProvider.KEY_REWARDED_ENABLED to "true",
                        AdsPolicyProvider.KEY_REWARDED_INTERSTITIAL_ENABLED to "true",
                    ),
            )

        val policy = AdsPolicyProvider(remoteConfigManager).getPolicy()

        assertThat(policy.bannerEnabled).isFalse()
        assertThat(policy.nativeEnabled).isFalse()
        assertThat(policy.appOpenEnabled).isFalse()
        assertThat(policy.interstitialEnabled).isFalse()
        assertThat(policy.rewardedEnabled).isFalse()
        assertThat(policy.rewardedInterstitialEnabled).isFalse()
    }

    @Test
    fun `app emergency switch disables only the matched app policy`() {
        val remoteConfigManager =
            mockRemoteConfig(
                longs = emptyMap(),
                strings =
                    mapOf(
                        AdsPolicyProvider.KEY_EMERGENCY_DISABLED to "true",
                        AdsPolicyProvider.KEY_BANNER_ENABLED to "true",
                    ),
            )

        val policy = AdsPolicyProvider(remoteConfigManager).getPolicy()

        assertThat(policy.bannerEnabled).isFalse()
    }

    private fun mockRemoteConfig(
        longs: Map<String, Long>,
        strings: Map<String, String>,
    ): RemoteConfigManager {
        val remoteConfigManager = mockk<RemoteConfigManager>()
        every { remoteConfigManager.setDefaults(any()) } just runs
        every { remoteConfigManager.getLongOrNull(any()) } answers { longs[firstArg()] }
        every { remoteConfigManager.getBooleanOrNull(any()) } answers {
            when (strings[firstArg<String>()]?.trim()?.lowercase()) {
                "1", "true", "yes", "on" -> true
                "0", "false", "no", "off" -> false
                else -> null
            }
        }
        every { remoteConfigManager.getStringOrNull(any()) } answers { strings[firstArg()] }
        return remoteConfigManager
    }
}
