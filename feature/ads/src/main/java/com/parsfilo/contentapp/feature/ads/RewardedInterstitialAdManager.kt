package com.parsfilo.contentapp.feature.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewarded interstitial ads must only be shown after an explicit intro/confirmation step.
 */
@Singleton
class RewardedInterstitialAdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adRevenueLogger: AdRevenueLogger,
    private val adsPolicyProvider: AdsPolicyProvider,
    private val rewardedInterstitialCoordinator: RewardedInterstitialCoordinator,
) {
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading = false
    private var currentAdUnitId: String = ""
    private var currentPlacement: AdPlacement = AdPlacement.REWARDED_INTERSTITIAL_DEFAULT
    private var currentRoute: String? = null
    private var loadBackoffState = AdLoadBackoffState()
    private var lastLoadStartedAtMillis: Long = 0L
    private val _isAdReady = MutableStateFlow(false)
    val isAdReady: StateFlow<Boolean> = _isAdReady.asStateFlow()

    fun loadAd(
        adUnitId: String,
        placement: AdPlacement = AdPlacement.REWARDED_INTERSTITIAL_DEFAULT,
        route: String? = null,
    ) {
        Timber.d(
            "RewardedInterstitial load requested placement=%s route=%s adUnit=%s canRequestAds=%s",
            placement.analyticsValue,
            route,
            adUnitId,
            AdsConsentRuntimeState.canRequestAds.value,
        )
        val policy = adsPolicyProvider.getPolicy()
        if (!policy.isRewardedInterstitialPlacementEnabled(placement)) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.REWARDED_INTERSTITIAL,
                placement = placement,
                adUnitId = adUnitId,
                suppressReason = AdSuppressReason.PLACEMENT_DISABLED,
                route = route,
            )
            clearAd()
            return
        }
        if (!AdsConsentRuntimeState.canRequestAds.value) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.REWARDED_INTERSTITIAL,
                placement = placement,
                adUnitId = adUnitId,
                suppressReason = AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked(),
                route = route,
            )
            clearAd()
            return
        }
        if (!AdLoadBackoffPolicy.canLoad(SystemTimeProvider.nowMillis(), loadBackoffState)) {
            Timber.d(
                "RewardedInterstitial load throttled nextAllowedAt=%d",
                loadBackoffState.nextLoadAllowedAtMillis,
            )
            return
        }
        if (isLoading || rewardedInterstitialAd != null) {
            Timber.d(
                "RewardedInterstitial load skipped loading=%s adReady=%s",
                isLoading,
                rewardedInterstitialAd != null,
            )
            return
        }
        currentAdUnitId = adUnitId
        currentPlacement = placement
        currentRoute = route
        lastLoadStartedAtMillis = SystemTimeProvider.nowMillis()
        adRevenueLogger.logRequest(
            adFormat = AdFormat.REWARDED_INTERSTITIAL,
            placement = placement,
            adUnitId = adUnitId,
            route = route,
        )

        isLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedInterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    Timber.d("Ad loaded: %s", adUnitId)
                    loadBackoffState = AdLoadBackoffPolicy.onLoadSuccess()
                    val fillLatencyMs = (SystemTimeProvider.nowMillis() - lastLoadStartedAtMillis).coerceAtLeast(0L)
                    adRevenueLogger.logLoaded(
                        adFormat = AdFormat.REWARDED_INTERSTITIAL,
                        placement = currentPlacement,
                        adUnitId = ad.adUnitId,
                        route = currentRoute,
                        fillLatencyMs = fillLatencyMs,
                        adapterName = ad.responseInfo.mediationAdapterClassName,
                    )
                    ad.onPaidEventListener = { value ->
                        adRevenueLogger.logPaidEvent(
                            AdPaidEventContext(
                                adUnitId = ad.adUnitId,
                                adFormat = AdFormat.REWARDED_INTERSTITIAL,
                                placement = currentPlacement,
                                route = currentRoute,
                                adValue = value,
                                responseMeta = adRevenueLogger.extractResponseMeta(ad.responseInfo),
                            ),
                        )
                    }
                    val responseInfo = ad.responseInfo
                    Timber.d(
                        "RewardedInterstitial response (%s): responseId=%s adapter=%s",
                        adUnitId,
                        responseInfo.responseId,
                        responseInfo.mediationAdapterClassName,
                    )
                    rewardedInterstitialAd = ad
                    isLoading = false
                    _isAdReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Timber.w("Failed to load: %s", error.message)
                    rewardedInterstitialAd = null
                    isLoading = false
                    _isAdReady.value = false
                    loadBackoffState = AdLoadBackoffPolicy.onLoadFailure(
                        nowMillis = SystemTimeProvider.nowMillis(),
                        current = loadBackoffState,
                        errorCode = error.code,
                    )
                    adRevenueLogger.logFailedToLoad(
                        adFormat = AdFormat.REWARDED_INTERSTITIAL,
                        placement = currentPlacement,
                        adUnitId = adUnitId,
                        errorCode = error.code,
                        errorMessage = error.message,
                        route = currentRoute,
                        backoffAttempt = loadBackoffState.failureStreak,
                    )
                }
            },
        )
    }

    fun showAfterConfirmedIntro(
        launchToken: RewardedInterstitialLaunchToken,
        activity: Activity,
        placement: AdPlacement = currentPlacement,
        route: String? = currentRoute,
        onAdImpression: (String) -> Unit = {},
        onUserEarnedReward: (type: String, amount: Int) -> Unit,
        onAdDismissed: () -> Unit,
    ) {
        Timber.d(
            "RewardedInterstitial show requested placement=%s route=%s adReady=%s",
            placement.analyticsValue,
            route,
            rewardedInterstitialAd != null,
        )
        if (!adsPolicyProvider.getPolicy().isRewardedInterstitialPlacementEnabled(placement)) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.REWARDED_INTERSTITIAL,
                placement = placement,
                adUnitId = currentAdUnitId.ifBlank { "unknown" },
                suppressReason = AdSuppressReason.PLACEMENT_DISABLED,
                route = route,
            )
            clearAd()
            onAdDismissed()
            return
        }
        if (!AdsConsentRuntimeState.canRequestAds.value) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.REWARDED_INTERSTITIAL,
                placement = placement,
                adUnitId = currentAdUnitId.ifBlank { "unknown" },
                suppressReason = AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked(),
                route = route,
            )
            clearAd()
            onAdDismissed()
            return
        }
        if (!rewardedInterstitialCoordinator.isTokenValid(launchToken, placement, route)) {
            Timber.d(
                "RewardedInterstitial show blocked: invalid intro token placement=%s route=%s",
                placement.analyticsValue,
                route,
            )
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.REWARDED_INTERSTITIAL,
                placement = placement,
                adUnitId = currentAdUnitId.ifBlank { "unknown" },
                suppressReason = AdSuppressReason.INTRO_SKIPPED,
                route = route,
            )
            onAdDismissed()
            return
        }
        val ad = rewardedInterstitialAd
        if (ad == null) {
            Timber.d("Ad not ready, calling onAdDismissed")
            adRevenueLogger.logShowNotLoaded(
                adFormat = AdFormat.REWARDED_INTERSTITIAL,
                placement = placement,
                route = route,
                diagnostics = AdShowDiagnosticContext(
                    isLoading = isLoading,
                    currentAdUnitId = currentAdUnitId.ifBlank { "unknown" },
                    timeSinceLastLoadStartMs =
                        (SystemTimeProvider.nowMillis() - lastLoadStartedAtMillis)
                            .coerceAtLeast(0L),
                    backoffNextAllowedAtMs = loadBackoffState.nextLoadAllowedAtMillis,
                ),
            )
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.REWARDED_INTERSTITIAL,
                placement = placement,
                adUnitId = currentAdUnitId.ifBlank { "unknown" },
                suppressReason = AdSuppressReason.NOT_LOADED,
                route = route,
            )
            onAdDismissed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Timber.d("Ad dismissed")
                adRevenueLogger.logDismissed(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
                adRevenueLogger.logShowDismissed(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
                rewardedInterstitialAd = null
                _isAdReady.value = false
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Timber.w("Failed to show: %s", adError.message)
                adRevenueLogger.logFailedToShow(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    errorCode = adError.code,
                    errorMessage = adError.message,
                    route = route,
                )
                adRevenueLogger.logShowFailed(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    errorCode = adError.code,
                    errorMessage = adError.message,
                    route = route,
                )
                rewardedInterstitialAd = null
                _isAdReady.value = false
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                Timber.d("Ad shown")
                adRevenueLogger.logShowStarted(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
                adRevenueLogger.logServed(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
            }

            override fun onAdImpression() {
                Timber.d("RewardedInterstitial impression recorded")
                onAdImpression(ad.adUnitId)
                adRevenueLogger.logShowImpression(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
                adRevenueLogger.logImpression(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
            }

            override fun onAdClicked() {
                adRevenueLogger.logClick(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
            }
        }

        ad.setServerSideVerificationOptions(
            ServerSideVerificationOptions.Builder()
                .setCustomData("placement=${placement.analyticsValue}&route=${route.orEmpty()}&adUnit=${ad.adUnitId}")
                .build(),
        )
        ad.show(activity) { rewardItem ->
            Timber.d("Reward earned: %s x%d", rewardItem.type, rewardItem.amount)
            onUserEarnedReward(rewardItem.type, rewardItem.amount)
        }
    }

    fun isAdReadyNow(): Boolean = rewardedInterstitialAd != null

    fun clearAd() {
        Timber.d(
            "RewardedInterstitial clearAd placement=%s hasAd=%s loading=%s",
            currentPlacement.analyticsValue,
            rewardedInterstitialAd != null,
            isLoading,
        )
        rewardedInterstitialAd = null
        isLoading = false
        _isAdReady.value = false
    }
}
