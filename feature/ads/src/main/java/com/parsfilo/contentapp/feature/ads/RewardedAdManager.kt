package com.parsfilo.contentapp.feature.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardedAdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adRevenueLogger: AdRevenueLogger,
    private val adsPolicyProvider: AdsPolicyProvider,
) {
    private var rewardedAd: RewardedAd? = null
    private var rewardedAdNext: RewardedAd? = null
    private var isLoading = false
    private var isLoadingNext = false
    private var currentAdUnitId: String = ""
    private var currentPlacement: AdPlacement = AdPlacement.REWARDED_DEFAULT
    private var currentRoute: String? = null
    private var loadBackoffState = AdLoadBackoffState()
    private var lastLoadStartedAtMillis: Long = 0L
    private val _isAdReady = MutableStateFlow(false)
    val isAdReady: StateFlow<Boolean> = _isAdReady.asStateFlow()

    fun isAdReadyNow(): Boolean = _isAdReady.value

    fun loadAd(
        adUnitId: String,
        placement: AdPlacement = AdPlacement.REWARDED_DEFAULT,
        route: String? = null,
    ) {
        Timber.d(
            "Rewarded load requested placement=%s route=%s adUnit=%s canRequestAds=%s",
            placement.analyticsValue,
            route,
            adUnitId,
            AdsConsentRuntimeState.canRequestAds.value,
        )
        val policy = adsPolicyProvider.getPolicy()
        if (!policy.isRewardedPlacementEnabled(placement)) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.REWARDED,
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
                adFormat = AdFormat.REWARDED,
                placement = placement,
                adUnitId = adUnitId,
                suppressReason = AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked(),
                route = route,
            )
            clearAd()
            return
        }
        if (isLoading || rewardedAd != null) {
            Timber.d("Rewarded load skipped loading=%s adReady=%s", isLoading, rewardedAd != null)
            return
        }
        val now = SystemTimeProvider.nowMillis()
        if (!AdLoadBackoffPolicy.canLoad(now, loadBackoffState)) {
            Timber.d("Rewarded load throttled nextAllowedAt=%d", loadBackoffState.nextLoadAllowedAtMillis)
            return
        }
        currentAdUnitId = adUnitId
        currentPlacement = placement
        currentRoute = route
        lastLoadStartedAtMillis = now
        isLoading = true
        adRevenueLogger.logRequest(
            adFormat = AdFormat.REWARDED,
            placement = placement,
            adUnitId = adUnitId,
            route = route,
        )
        val adRequest = AdRequest.Builder().build()
        Timber.d("Rewarded load requested: %s", adUnitId)
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                    _isAdReady.value = false
                    isLoading = false
                    loadBackoffState = AdLoadBackoffPolicy.onLoadFailure(
                        nowMillis = SystemTimeProvider.nowMillis(),
                        current = loadBackoffState,
                        errorCode = adError.code,
                    )
                    adRevenueLogger.logFailedToLoad(
                        adFormat = AdFormat.REWARDED,
                        placement = currentPlacement,
                        adUnitId = adUnitId,
                        errorCode = adError.code,
                        errorMessage = adError.message,
                        route = currentRoute,
                        backoffAttempt = loadBackoffState.failureStreak,
                    )
                    Timber.w("Rewarded failed to load (%s): %s", adUnitId, adError.message)
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    loadBackoffState = AdLoadBackoffPolicy.onLoadSuccess()
                    val fillLatencyMs = (SystemTimeProvider.nowMillis() - lastLoadStartedAtMillis).coerceAtLeast(0L)
                    adRevenueLogger.logLoaded(
                        adFormat = AdFormat.REWARDED,
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
                                adFormat = AdFormat.REWARDED,
                                placement = currentPlacement,
                                route = currentRoute,
                                adValue = value,
                                responseMeta = adRevenueLogger.extractResponseMeta(ad.responseInfo),
                            ),
                        )
                    }
                    val responseInfo = ad.responseInfo
                    Timber.d(
                        "Rewarded loaded (%s): responseId=%s adapter=%s",
                        adUnitId,
                        responseInfo.responseId,
                        responseInfo.mediationAdapterClassName,
                    )
                    rewardedAd = ad
                    _isAdReady.value = true
                    isLoading = false
                    loadNextIfNeeded()
                }
            }
        )
    }

    internal fun loadNextIfNeeded() {
        val adUnitId = currentAdUnitId.ifBlank { return }
        if (!AdsConsentRuntimeState.canRequestAds.value) return
        if (isLoadingNext || rewardedAdNext != null) return
        if (adsPolicyProvider.getPolicy().rewardedPoolMax < 2) return

        Timber.d(
            "Rewarded loading next slot placement=%s adUnit=%s",
            currentPlacement.analyticsValue,
            adUnitId,
        )
        isLoadingNext = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Timber.d("Rewarded next slot loaded adUnit=%s", ad.adUnitId)
                    isLoadingNext = false
                    ad.onPaidEventListener = { value ->
                        adRevenueLogger.logPaidEvent(
                            AdPaidEventContext(
                                adUnitId = ad.adUnitId,
                                adFormat = AdFormat.REWARDED,
                                placement = currentPlacement,
                                route = currentRoute,
                                adValue = value,
                                responseMeta = adRevenueLogger.extractResponseMeta(ad.responseInfo),
                            ),
                        )
                    }
                    rewardedAdNext = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Timber.d(
                        "Rewarded next slot failed code=%d placement=%s",
                        adError.code,
                        currentPlacement.analyticsValue,
                    )
                    isLoadingNext = false
                    rewardedAdNext = null
                }
            },
        )
    }

    fun showAd(activity: Activity, onUserEarnedReward: () -> Unit, onAdDismissed: () -> Unit) {
        Timber.d(
            "Rewarded show requested placement=%s route=%s adReady=%s",
            currentPlacement.analyticsValue,
            currentRoute,
            rewardedAd != null,
        )
        if (!adsPolicyProvider.getPolicy().isRewardedPlacementEnabled(currentPlacement)) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.REWARDED,
                placement = currentPlacement,
                adUnitId = currentAdUnitId.ifBlank { "unknown" },
                suppressReason = AdSuppressReason.PLACEMENT_DISABLED,
                route = currentRoute,
            )
            clearAd()
            onAdDismissed()
            return
        }
        if (!AdsConsentRuntimeState.canRequestAds.value) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.REWARDED,
                placement = currentPlacement,
                adUnitId = currentAdUnitId.ifBlank { "unknown" },
                suppressReason = AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked(),
                route = currentRoute,
            )
            clearAd()
            onAdDismissed()
            return
        }
        val loadedAd = rewardedAd
        if (loadedAd != null) {
            loadedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (rewardedAdNext != null) {
                        rewardedAd = rewardedAdNext
                        rewardedAdNext = null
                        loadNextIfNeeded()
                    } else {
                        rewardedAd = null
                        _isAdReady.value = false
                    }
                    isLoading = false
                    Timber.d("Rewarded dismissed")
                    adRevenueLogger.logDismissed(
                        adFormat = AdFormat.REWARDED,
                        placement = currentPlacement,
                        adUnitId = loadedAd.adUnitId,
                        route = currentRoute,
                    )
                    adRevenueLogger.logShowDismissed(
                        adFormat = AdFormat.REWARDED,
                        placement = currentPlacement,
                        adUnitId = loadedAd.adUnitId,
                        route = currentRoute,
                    )
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    if (rewardedAdNext != null) {
                        rewardedAd = rewardedAdNext
                        rewardedAdNext = null
                        loadNextIfNeeded()
                    } else {
                        rewardedAd = null
                        _isAdReady.value = false
                    }
                    isLoading = false
                    Timber.w("Rewarded failed to show: %s", adError.message)
                    adRevenueLogger.logFailedToShow(
                        adFormat = AdFormat.REWARDED,
                        placement = currentPlacement,
                        adUnitId = loadedAd.adUnitId,
                        errorCode = adError.code,
                        errorMessage = adError.message,
                        route = currentRoute,
                    )
                    adRevenueLogger.logShowFailed(
                        adFormat = AdFormat.REWARDED,
                        placement = currentPlacement,
                        adUnitId = loadedAd.adUnitId,
                        errorCode = adError.code,
                        errorMessage = adError.message,
                        route = currentRoute,
                    )
                    onAdDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    Timber.d("Rewarded showed full screen content")
                    adRevenueLogger.logShowStarted(
                        adFormat = AdFormat.REWARDED,
                        placement = currentPlacement,
                        adUnitId = loadedAd.adUnitId,
                        route = currentRoute,
                    )
                    adRevenueLogger.logServed(
                        adFormat = AdFormat.REWARDED,
                        placement = currentPlacement,
                        adUnitId = loadedAd.adUnitId,
                        route = currentRoute,
                    )
                }

                override fun onAdImpression() {
                    Timber.d("Rewarded impression recorded")
                    adRevenueLogger.logShowImpression(
                        adFormat = AdFormat.REWARDED,
                        placement = currentPlacement,
                        adUnitId = loadedAd.adUnitId,
                        route = currentRoute,
                    )
                    adRevenueLogger.logImpression(
                        adFormat = AdFormat.REWARDED,
                        placement = currentPlacement,
                        adUnitId = loadedAd.adUnitId,
                        route = currentRoute,
                    )
                }

                override fun onAdClicked() {
                    adRevenueLogger.logClick(
                        adFormat = AdFormat.REWARDED,
                        placement = currentPlacement,
                        adUnitId = loadedAd.adUnitId,
                        route = currentRoute,
                    )
                }
            }
            loadedAd.setServerSideVerificationOptions(
                ServerSideVerificationOptions.Builder()
                    .setCustomData("placement=${currentPlacement.analyticsValue}&route=${currentRoute.orEmpty()}&adUnit=${loadedAd.adUnitId}")
                    .build(),
            )
            loadedAd.show(activity) { rewardItem ->
                Timber.d("Rewarded reward earned: %s x%d", rewardItem.type, rewardItem.amount)
                onUserEarnedReward()
            }
        } else {
            _isAdReady.value = false
            isLoading = false
            Timber.d("Rewarded show skipped: ad not ready")
            adRevenueLogger.logShowNotLoaded(
                adFormat = AdFormat.REWARDED,
                placement = currentPlacement,
                route = currentRoute,
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
                adFormat = AdFormat.REWARDED,
                placement = currentPlacement,
                adUnitId = currentAdUnitId.ifBlank { "unknown" },
                suppressReason = AdSuppressReason.NOT_LOADED,
                route = currentRoute,
            )
            onAdDismissed()
        }
    }

    fun clearAd() {
        Timber.d(
            "Rewarded clearAd placement=%s hasAd=%s loading=%s",
            currentPlacement.analyticsValue,
            rewardedAd != null,
            isLoading,
        )
        rewardedAd = null
        rewardedAdNext = null
        _isAdReady.value = false
        isLoading = false
        isLoadingNext = false
    }
}
