package com.parsfilo.contentapp.feature.ads

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

data class RewardedInterstitialIntroSpec(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val skipLabel: String,
)

class RewardedInterstitialLaunchToken internal constructor(
    val placement: AdPlacement,
    val issuedAtElapsedRealtimeMs: Long,
    val route: String?,
)

@Singleton
class RewardedInterstitialCoordinator @Inject constructor(
    private val adRevenueLogger: AdRevenueLogger,
) {
    fun buildIntroSpec(placement: AdPlacement): RewardedInterstitialIntroSpec =
        when (placement) {
            AdPlacement.REWARDED_INTERSTITIAL_HISTORY_UNLOCK -> RewardedInterstitialIntroSpec(
                title = "Gecmisi ac",
                body = "Bu reklamı izlersen zikirmatik gecmisinin tamamını acacaksın.",
                confirmLabel = "Izle ve kazan",
                skipLabel = "Atla",
            )
            else -> RewardedInterstitialIntroSpec(
                title = "Odullu reklam",
                body = "Devam etmeden once reklami izleyip odulu alabilirsin.",
                confirmLabel = "Izle ve kazan",
                skipLabel = "Atla",
            )
        }

    fun onIntroShown(
        placement: AdPlacement,
        adUnitId: String,
        route: String?,
    ) {
        adRevenueLogger.logRewardedInterstitialIntroShown(
            placement = placement,
            adUnitId = adUnitId,
            route = route,
        )
    }

    fun onIntroSkipped(
        placement: AdPlacement,
        adUnitId: String,
        route: String?,
    ) {
        adRevenueLogger.logRewardedInterstitialIntroSkipped(
            placement = placement,
            adUnitId = adUnitId,
            route = route,
        )
    }

    fun confirmIntro(
        placement: AdPlacement,
        adUnitId: String,
        route: String?,
    ): RewardedInterstitialLaunchToken {
        adRevenueLogger.logRewardedInterstitialIntroConfirmed(
            placement = placement,
            adUnitId = adUnitId,
            route = route,
        )
        return RewardedInterstitialLaunchToken(
            placement = placement,
            issuedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            route = route,
        )
    }

    fun isTokenValid(
        token: RewardedInterstitialLaunchToken,
        placement: AdPlacement,
        route: String?,
    ): Boolean {
        val ageMs = SystemClock.elapsedRealtime() - token.issuedAtElapsedRealtimeMs
        return token.placement == placement &&
            token.route == route &&
            ageMs in 0..15_000
    }
}
