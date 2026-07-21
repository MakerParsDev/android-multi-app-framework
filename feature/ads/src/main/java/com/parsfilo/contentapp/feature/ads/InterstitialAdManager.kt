package com.parsfilo.contentapp.feature.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterstitialAdManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesDataSource: PreferencesDataSource,
    private val adRevenueLogger: AdRevenueLogger,
    private val adsPolicyProvider: AdsPolicyProvider,
) {
    private var interstitialAd: InterstitialAd? = null
    private var interstitialAdNext: InterstitialAd? = null
    private var isLoading = false
    private var isLoadingNext = false
    private var currentAdUnitId: String? = null
    private var currentPlacement: AdPlacement = AdPlacement.INTERSTITIAL_DEFAULT
    private var currentRoute: String? = null
    private var currentLoadContext: Context? = null
    private var loadBackoffState = AdLoadBackoffState()
    private var lastLoadStartedAtMillis: Long = 0L
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun effectiveCooldownMs(value: Long): Long = if (BuildConfig.DEBUG) 0L else value

    fun isAdReady(): Boolean = interstitialAd != null

    fun loadAd(
        loadContext: Context,
        adUnitId: String,
        placement: AdPlacement = AdPlacement.INTERSTITIAL_DEFAULT,
        route: String? = null,
        loadReason: String = "unspecified",
    ) {
        Timber.d(
            "Interstitial load requested placement=%s route=%s adUnit=%s reason=%s canRequestAds=%s",
            placement.analyticsValue,
            route,
            adUnitId,
            loadReason,
            AdsConsentRuntimeState.canRequestAds.value,
        )
        val policy = adsPolicyProvider.getPolicy()
        if (!policy.isInterstitialPlacementEnabled(placement)) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.INTERSTITIAL,
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
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                adUnitId = adUnitId,
                suppressReason = AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked(),
                route = route,
            )
            clearAd()
            return
        }
        val now = SystemTimeProvider.nowMillis()
        if (!AdLoadBackoffPolicy.canLoad(now, loadBackoffState)) {
            Timber.d(
                "Interstitial load throttled until=%d placement=%s",
                loadBackoffState.nextLoadAllowedAtMillis,
                placement.analyticsValue,
            )
            return
        }

        if (isLoading && currentAdUnitId == adUnitId) {
            Timber.d("Interstitial load skipped; already loading adUnit=%s", adUnitId)
            return
        }
        if (interstitialAd != null && currentAdUnitId == adUnitId) {
            Timber.d("Interstitial load skipped; ad already ready adUnit=%s", adUnitId)
            return
        }

        currentAdUnitId = adUnitId
        currentPlacement = placement
        currentRoute = route
        currentLoadContext = loadContext.findActivity() ?: loadContext
        lastLoadStartedAtMillis = now
        isLoading = true
        adRevenueLogger.logPreloadRequested(
            adFormat = AdFormat.INTERSTITIAL,
            placement = placement,
            adUnitId = adUnitId,
            route = route,
            reason = loadReason,
        )
        adRevenueLogger.logRequest(
            adFormat = AdFormat.INTERSTITIAL,
            placement = placement,
            adUnitId = adUnitId,
            route = route,
        )
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            currentLoadContext ?: appContext,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                    loadBackoffState = AdLoadBackoffPolicy.onLoadFailure(
                        nowMillis = SystemTimeProvider.nowMillis(),
                        current = loadBackoffState,
                        errorCode = adError.code,
                    )
                    adRevenueLogger.logFailedToLoad(
                        adFormat = AdFormat.INTERSTITIAL,
                        placement = currentPlacement,
                        adUnitId = adUnitId,
                        errorCode = adError.code,
                        errorMessage = adError.message,
                        route = currentRoute,
                        backoffAttempt = loadBackoffState.failureStreak,
                    )
                    Timber.w(
                        "Interstitial failed load placement=%s code=%d nextRetryAt=%d msg=%s",
                        currentPlacement.analyticsValue,
                        adError.code,
                        loadBackoffState.nextLoadAllowedAtMillis,
                        adError.message,
                    )
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    Timber.d(
                        "Interstitial loaded placement=%s route=%s adUnit=%s responseId=%s",
                        currentPlacement.analyticsValue,
                        currentRoute,
                        ad.adUnitId,
                        ad.responseInfo.responseId,
                    )
                    isLoading = false
                    loadBackoffState = AdLoadBackoffPolicy.onLoadSuccess()
                    val fillLatencyMs = (SystemTimeProvider.nowMillis() - lastLoadStartedAtMillis).coerceAtLeast(0L)
                    adRevenueLogger.logLoaded(
                        adFormat = AdFormat.INTERSTITIAL,
                        placement = currentPlacement,
                        adUnitId = adUnitId,
                        route = currentRoute,
                        fillLatencyMs = fillLatencyMs,
                        adapterName = ad.responseInfo.mediationAdapterClassName,
                    )
                    ad.onPaidEventListener = { adValue ->
                        adRevenueLogger.logPaidEvent(
                            AdPaidEventContext(
                                adUnitId = ad.adUnitId,
                                adFormat = AdFormat.INTERSTITIAL,
                                placement = currentPlacement,
                                route = currentRoute,
                                adValue = adValue,
                                responseMeta = adRevenueLogger.extractResponseMeta(ad.responseInfo),
                            ),
                        )
                    }
                    interstitialAd = ad
                    loadNextIfNeeded()
                }
            },
        )
    }

    suspend fun showAd(
        activity: Activity,
        placement: AdPlacement = AdPlacement.INTERSTITIAL_DEFAULT,
        route: String? = null,
        triggerKind: InterstitialTriggerKind = InterstitialTriggerKind.NAV_BREAK,
        onAdImpression: (String) -> Unit = {},
        onAdDismissed: () -> Unit,
    ) {
        Timber.d(
            "Interstitial show requested placement=%s route=%s trigger=%s adReady=%s",
            placement.analyticsValue,
            route,
            triggerKind.analyticsValue,
            interstitialAd != null,
        )
        if (!AdsConsentRuntimeState.canRequestAds.value) {
            val consentReason = AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked()
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                adUnitId = currentAdUnitId ?: "unknown",
                suppressReason = consentReason,
                route = route,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                suppressReason = consentReason,
                route = route,
                trigger = triggerKind.analyticsValue,
                diagnostics = currentDiagnostics(),
            )
            clearAd()
            onAdDismissed()
            return
        }

        val prefs = preferencesDataSource.userData.first()
        val now = SystemTimeProvider.nowMillis()

        if (prefs.isPremium || prefs.rewardedAdFreeUntil > now) {
            val reason = if (prefs.isPremium) AdSuppressReason.PREMIUM else AdSuppressReason.REWARDED_FREE
            Timber.d("Interstitial show blocked reason=%s route=%s", reason.analyticsValue, route)
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                adUnitId = currentAdUnitId ?: "unknown",
                suppressReason = reason,
                route = route,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                suppressReason = reason,
                route = route,
                trigger = triggerKind.analyticsValue,
                diagnostics = currentDiagnostics(),
            )
            onAdDismissed()
            return
        }

        val policy = adsPolicyProvider.getPolicy()
        if (!policy.isInterstitialPlacementEnabled(placement)) {
            Timber.d("Interstitial show blocked: placement disabled placement=%s route=%s", placement.analyticsValue, route)
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                adUnitId = currentAdUnitId ?: "unknown",
                suppressReason = AdSuppressReason.PLACEMENT_DISABLED,
                route = route,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                suppressReason = AdSuppressReason.PLACEMENT_DISABLED,
                route = route,
                trigger = triggerKind.analyticsValue,
                diagnostics = currentDiagnostics(),
            )
            onAdDismissed()
            return
        }
        val frequencyCapMs = effectiveCooldownMs(
            policy.interstitialFrequencyCapForPackage(appContext.packageName),
        )
        val baseFrequencyCapMs = effectiveCooldownMs(policy.interstitialFrequencyCapMs)
        if (frequencyCapMs != baseFrequencyCapMs) {
            Timber.d(
                "Interstitial frequency cap relaxed for package=%s capMs=%d",
                appContext.packageName,
                frequencyCapMs,
            )
        }
        if (now - prefs.lastInterstitialShown < frequencyCapMs) {
            Timber.d(
                "Interstitial show blocked: cooldown remainingMs=%d route=%s",
                frequencyCapMs - (now - prefs.lastInterstitialShown),
                route,
            )
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                adUnitId = currentAdUnitId ?: "unknown",
                suppressReason = AdSuppressReason.COOLDOWN,
                route = route,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                suppressReason = AdSuppressReason.COOLDOWN,
                route = route,
                trigger = triggerKind.analyticsValue,
                diagnostics = currentDiagnostics(now),
            )
            onAdDismissed()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            Timber.d("Interstitial show blocked: ad not loaded placement=%s route=%s", placement.analyticsValue, route)
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                adUnitId = currentAdUnitId ?: "unknown",
                suppressReason = AdSuppressReason.NOT_LOADED,
                route = route,
            )
            adRevenueLogger.logShowNotLoaded(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                route = route,
                trigger = triggerKind.analyticsValue,
                diagnostics = currentDiagnostics(),
            )
            onAdDismissed()
            maybeReload(reason = "not_loaded_recovery")
            return
        }

        currentPlacement = placement
        currentRoute = route
        var impressionStampRecorded = false
        adRevenueLogger.logShowStarted(
            adFormat = AdFormat.INTERSTITIAL,
            placement = placement,
            adUnitId = ad.adUnitId,
            route = route,
            trigger = triggerKind.analyticsValue,
        )

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Timber.d("Interstitial dismissed placement=%s route=%s adUnit=%s", placement.analyticsValue, route, ad.adUnitId)
                adRevenueLogger.logDismissed(
                    adFormat = AdFormat.INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
                adRevenueLogger.logShowDismissed(
                    adFormat = AdFormat.INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                    trigger = triggerKind.analyticsValue,
                )
                if (interstitialAdNext != null) {
                    interstitialAd = interstitialAdNext
                    interstitialAdNext = null
                    loadNextIfNeeded()
                } else {
                    interstitialAd = null
                    maybeReload(reason = "post_show")
                }
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Timber.w("Interstitial failed to show placement=%s err=%s", placement.analyticsValue, adError.message)
                adRevenueLogger.logFailedToShow(
                    adFormat = AdFormat.INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    errorCode = adError.code,
                    errorMessage = adError.message,
                    route = route,
                )
                adRevenueLogger.logShowFailed(
                    adFormat = AdFormat.INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                    trigger = triggerKind.analyticsValue,
                    errorCode = adError.code,
                    errorMessage = adError.message,
                )
                if (interstitialAdNext != null) {
                    interstitialAd = interstitialAdNext
                    interstitialAdNext = null
                    loadNextIfNeeded()
                } else {
                    interstitialAd = null
                    maybeReload(reason = "show_failed")
                }
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                Timber.d("Interstitial showed placement=%s route=%s adUnit=%s", placement.analyticsValue, route, ad.adUnitId)
                adRevenueLogger.logServed(
                    adFormat = AdFormat.INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
                interstitialAd = null
            }

            override fun onAdImpression() {
                Timber.d("Interstitial impression placement=%s route=%s adUnit=%s", placement.analyticsValue, route, ad.adUnitId)
                adRevenueLogger.logImpression(
                    adFormat = AdFormat.INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
                adRevenueLogger.logShowImpression(
                    adFormat = AdFormat.INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                    trigger = triggerKind.analyticsValue,
                )
                onAdImpression(ad.adUnitId)
                if (shouldRecordImpressionStamp(impressionStampRecorded)) {
                    impressionStampRecorded = true
                    callbackScope.launch {
                        preferencesDataSource.setLastInterstitialShown(SystemTimeProvider.nowMillis())
                    }
                }
            }

            override fun onAdClicked() {
                Timber.d("Interstitial clicked placement=%s route=%s adUnit=%s", placement.analyticsValue, route, ad.adUnitId)
                adRevenueLogger.logClick(
                    adFormat = AdFormat.INTERSTITIAL,
                    placement = placement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
            }
        }
        ad.show(activity)
    }

    internal fun loadNextIfNeeded() {
        val adUnitId = currentAdUnitId ?: return
        if (!AdsConsentRuntimeState.canRequestAds.value) return
        if (isLoadingNext || interstitialAdNext != null) return
        if (adsPolicyProvider.getPolicy().interstitialPoolMax < 2) return

        Timber.d(
            "Interstitial loading next slot placement=%s adUnit=%s",
            currentPlacement.analyticsValue,
            adUnitId,
        )
        isLoadingNext = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            currentLoadContext ?: appContext,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Timber.d("Interstitial next slot loaded adUnit=%s", ad.adUnitId)
                    isLoadingNext = false
                    ad.onPaidEventListener = { adValue ->
                        adRevenueLogger.logPaidEvent(
                            AdPaidEventContext(
                                adUnitId = ad.adUnitId,
                                adFormat = AdFormat.INTERSTITIAL,
                                placement = currentPlacement,
                                route = currentRoute,
                                adValue = adValue,
                                responseMeta = adRevenueLogger.extractResponseMeta(ad.responseInfo),
                            ),
                        )
                    }
                    interstitialAdNext = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Timber.d(
                        "Interstitial next slot failed code=%d placement=%s",
                        adError.code,
                        currentPlacement.analyticsValue,
                    )
                    isLoadingNext = false
                    interstitialAdNext = null
                }
            },
        )
    }

    internal fun maybeReload(reason: String) {
        val adUnitId = currentAdUnitId ?: return
        if (interstitialAd == null && AdsConsentRuntimeState.canRequestAds.value) {
            Timber.d(
                "Interstitial reload requested reason=%s placement=%s route=%s",
                reason,
                currentPlacement.analyticsValue,
                currentRoute,
            )
            loadAd(
                currentLoadContext ?: appContext,
                adUnitId,
                currentPlacement,
                currentRoute,
                loadReason = reason,
            )
        }
    }

    private fun currentDiagnostics(nowMillis: Long = SystemTimeProvider.nowMillis()): AdShowDiagnosticContext =
        AdShowDiagnosticContext(
            isLoading = isLoading,
            currentAdUnitId = currentAdUnitId,
            timeSinceLastLoadStartMs =
                lastLoadStartedAtMillis.takeIf { it > 0L }?.let { startedAt ->
                    (nowMillis - startedAt).coerceAtLeast(0L)
                },
            backoffNextAllowedAtMs =
                loadBackoffState.nextLoadAllowedAtMillis.takeIf { it > 0L },
        )

    fun clearAd() {
        Timber.d(
            "Interstitial clearAd placement=%s hasAd=%s loading=%s",
            currentPlacement.analyticsValue,
            interstitialAd != null,
            isLoading,
        )
        interstitialAd = null
        interstitialAdNext = null
        isLoading = false
        isLoadingNext = false
    }
}
