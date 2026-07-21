package com.parsfilo.contentapp.feature.ads

data class AdsPolicyConfig(
    val interstitialFrequencyCapMs: Long,
    val interstitialRelaxedFrequencyCapMs: Long,
    val interstitialRelaxedPackages: Set<String>,
    val appOpenCooldownMs: Long,
    val appOpenResumeGapMs: Long,
    val appOpenMaxPerSession: Int,
    val interstitialMaxPerSession: Int,
    val rewardedMaxPerSession: Int,
    val rewardedInterstitialMinIntervalMs: Long,
    val rewardedInterstitialMaxPerSession: Int,
    val rewardedInterstitialIntroRequired: Boolean,
    val appOpenEnabled: Boolean,
    val interstitialEnabled: Boolean,
    val bannerEnabled: Boolean,
    val nativeEnabled: Boolean,
    val rewardedEnabled: Boolean,
    val rewardedInterstitialEnabled: Boolean,
    val appOpenPlacementsDisabled: Set<String>,
    val interstitialPlacementsDisabled: Set<String>,
    val bannerPlacementsDisabled: Set<String>,
    val nativePlacementsDisabled: Set<String>,
    val rewardedPlacementsDisabled: Set<String>,
    val rewardedInterstitialPlacementsDisabled: Set<String>,
    val appOpenRouteBlocklist: Set<String>,
    val interstitialRouteBlocklist: Set<String>,
    val interstitialAggressivePreloadPackages: Set<String> = emptySet(),
    val appOpenAggressivePreloadPackages: Set<String> = emptySet(),
    val rewardOfferRoutes: Set<String> = emptySet(),
    val interstitialHotRoutes: Set<String> = emptySet(),
    val interstitialNotLoadedRecoveryEnabled: Boolean = false,
    val nativeBannerFallbackEnabled: Boolean = false,
    val nativeBannerFallbackPackages: Set<String> = emptySet(),
    val reportFreshnessMaxHours: Int = 24,
    val consentRetryBackoffMinutes: Int = 30,
    val interstitialPoolMax: Int = 2,
    val rewardedPoolMax: Int = 2,
    val nativePoolMax: Int,
    val nativeTtlMs: Long,
    val nativeExactPlacementOnly: Boolean,
) {
    fun interstitialFrequencyCapForPackage(packageName: String): Long =
        if (packageName in interstitialRelaxedPackages) {
            interstitialRelaxedFrequencyCapMs
        } else {
            interstitialFrequencyCapMs
        }

    fun isBannerPlacementEnabled(placement: AdPlacement): Boolean =
        isPlacementEnabled(placement)

    fun isInterstitialPlacementEnabled(placement: AdPlacement): Boolean =
        isPlacementEnabled(placement)

    fun isNativePlacementEnabled(placement: AdPlacement): Boolean =
        isPlacementEnabled(placement)

    fun isAppOpenPlacementEnabled(placement: AdPlacement): Boolean =
        isPlacementEnabled(placement)

    fun isRewardedPlacementEnabled(placement: AdPlacement): Boolean =
        isPlacementEnabled(placement)

    fun isRewardedInterstitialPlacementEnabled(placement: AdPlacement): Boolean =
        isPlacementEnabled(placement)

    fun isPlacementEnabled(placement: AdPlacement): Boolean {
        val disabledSet = when (placement.format) {
            AdFormat.APP_OPEN -> appOpenPlacementsDisabled
            AdFormat.INTERSTITIAL -> interstitialPlacementsDisabled
            AdFormat.BANNER -> bannerPlacementsDisabled
            AdFormat.NATIVE -> nativePlacementsDisabled
            AdFormat.REWARDED -> rewardedPlacementsDisabled
            AdFormat.REWARDED_INTERSTITIAL -> rewardedInterstitialPlacementsDisabled
        }
        if (placement.analyticsValue in disabledSet) return false
        return when (placement.format) {
            AdFormat.APP_OPEN -> appOpenEnabled
            AdFormat.INTERSTITIAL -> interstitialEnabled
            AdFormat.BANNER -> bannerEnabled
            AdFormat.NATIVE -> nativeEnabled
            AdFormat.REWARDED -> rewardedEnabled
            AdFormat.REWARDED_INTERSTITIAL -> rewardedInterstitialEnabled
        }
    }

    fun isBlockedContext(vararg values: String?): Boolean =
        values
            .asSequence()
            .filterNotNull()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .any { candidate ->
                appOpenRouteBlocklist.contains(candidate) || interstitialRouteBlocklist.contains(candidate)
            }

    fun shouldUseAggressiveInterstitialPreload(packageName: String): Boolean =
        packageName in interstitialAggressivePreloadPackages

    fun shouldUseAggressiveAppOpenPreload(packageName: String): Boolean =
        packageName in appOpenAggressivePreloadPackages

    fun shouldOfferRewardOnRoute(route: String?): Boolean {
        val normalized = route?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return false
        return rewardOfferRoutes.any { rewardRoute ->
            normalized == rewardRoute || normalized.startsWith("$rewardRoute/")
        }
    }

    fun shouldUseNativeBannerFallback(packageName: String): Boolean =
        nativeBannerFallbackEnabled && packageName in nativeBannerFallbackPackages

    fun isHotInterstitialRoute(route: String?): Boolean {
        val normalized = route?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return false
        return interstitialHotRoutes.any { hotRoute ->
            normalized == hotRoute || normalized.startsWith("$hotRoute/")
        }
    }
}
