package com.parsfilo.contentapp.feature.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenAdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: PreferencesDataSource,
    private val adRevenueLogger: AdRevenueLogger,
    private val adsPolicyProvider: AdsPolicyProvider,
) {
    enum class ShowCompletionReason {
        BLOCKED,
        NOT_LOADED,
        ATTEMPTED,
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var loadTime: Long = 0
    private var currentAdUnitId: String? = null
    private var currentPlacement: AdPlacement = AdPlacement.APP_OPEN_DEFAULT
    private var loadBackoffState = AdLoadBackoffState()
    private var lastLoadStartedAtMillis: Long = 0L
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isShowingAd = false

    private fun effectiveCooldownMs(value: Long): Long = if (BuildConfig.DEBUG) 0L else value

    fun isShowingAdNow(): Boolean = isShowingAd

    fun isAdReady(): Boolean = isAdAvailable()

    fun loadAd(
        adUnitId: String,
        placement: AdPlacement = AdPlacement.APP_OPEN_DEFAULT,
        loadReason: String = "unspecified",
    ) {
        Timber.d(
            "AppOpen load requested placement=%s adUnit=%s reason=%s canRequestAds=%s",
            placement.analyticsValue,
            adUnitId,
            loadReason,
            AdsConsentRuntimeState.canRequestAds.value,
        )
        val policy = adsPolicyProvider.getPolicy()
        if (!policy.isAppOpenPlacementEnabled(placement)) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.APP_OPEN,
                placement = placement,
                adUnitId = adUnitId,
                suppressReason = AdSuppressReason.PLACEMENT_DISABLED,
                route = null,
            )
            clearAd()
            return
        }
        if (!AdsConsentRuntimeState.canRequestAds.value) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.APP_OPEN,
                placement = placement,
                adUnitId = adUnitId,
                suppressReason = AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked(),
                route = null,
            )
            clearAd()
            return
        }
        val now = SystemTimeProvider.nowMillis()
        if (!AdLoadBackoffPolicy.canLoad(now, loadBackoffState)) {
            Timber.d(
                "AppOpen load throttled placement=%s nextAllowedAt=%d",
                placement.analyticsValue,
                loadBackoffState.nextLoadAllowedAtMillis,
            )
            return
        }
        if (isLoadingAd || isAdAvailable()) {
            Timber.d(
                "AppOpen load skipped placement=%s loading=%s available=%s",
                placement.analyticsValue,
                isLoadingAd,
                isAdAvailable(),
            )
            return
        }

        currentAdUnitId = adUnitId
        currentPlacement = placement
        lastLoadStartedAtMillis = now
        adRevenueLogger.logPreloadRequested(
            adFormat = AdFormat.APP_OPEN,
            placement = placement,
            adUnitId = adUnitId,
            route = null,
            reason = loadReason,
        )
        adRevenueLogger.logRequest(
            adFormat = AdFormat.APP_OPEN,
            placement = placement,
            adUnitId = adUnitId,
            route = null,
        )
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Timber.d(
                        "AppOpen loaded placement=%s adUnit=%s responseId=%s",
                        currentPlacement.analyticsValue,
                        ad.adUnitId,
                        ad.responseInfo.responseId,
                    )
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    loadBackoffState = AdLoadBackoffPolicy.onLoadSuccess()
                    val fillLatencyMs = (SystemTimeProvider.nowMillis() - lastLoadStartedAtMillis).coerceAtLeast(0L)
                    adRevenueLogger.logLoaded(
                        adFormat = AdFormat.APP_OPEN,
                        placement = currentPlacement,
                        adUnitId = ad.adUnitId,
                        route = null,
                        fillLatencyMs = fillLatencyMs,
                        adapterName = ad.responseInfo.mediationAdapterClassName,
                    )
                    ad.onPaidEventListener = { adValue ->
                        adRevenueLogger.logPaidEvent(
                            AdPaidEventContext(
                                adUnitId = ad.adUnitId,
                                adFormat = AdFormat.APP_OPEN,
                                placement = currentPlacement,
                                route = null,
                                adValue = adValue,
                                responseMeta = adRevenueLogger.extractResponseMeta(ad.responseInfo),
                            ),
                        )
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Timber.d(
                        "AppOpen failedToLoad placement=%s adUnit=%s code=%d msg=%s",
                        currentPlacement.analyticsValue,
                        adUnitId,
                        loadAdError.code,
                        loadAdError.message,
                    )
                    isLoadingAd = false
                    loadBackoffState = AdLoadBackoffPolicy.onLoadFailure(
                        nowMillis = SystemTimeProvider.nowMillis(),
                        current = loadBackoffState,
                        errorCode = loadAdError.code,
                    )
                    adRevenueLogger.logFailedToLoad(
                        adFormat = AdFormat.APP_OPEN,
                        placement = currentPlacement,
                        adUnitId = adUnitId,
                        errorCode = loadAdError.code,
                        errorMessage = loadAdError.message,
                        route = null,
                        backoffAttempt = loadBackoffState.failureStreak,
                    )
                }
            },
        )
    }

    suspend fun showAdIfAvailable(
        activity: Activity,
        route: String? = null,
        triggerReason: AppOpenTriggerReason = AppOpenTriggerReason.RESUME,
        onAdImpression: (String) -> Unit = {},
        onShowComplete: (ShowCompletionReason) -> Unit,
    ) {
        if (isShowingAd) {
            Timber.d("AppOpen show skipped: ad already showing route=%s", route)
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                suppressReason = AdSuppressReason.RAPID_REPEAT,
                route = route,
                trigger = triggerReason.analyticsValue,
                diagnostics = currentDiagnostics(),
            )
            onShowComplete(ShowCompletionReason.BLOCKED)
            return
        }
        if (!AdsConsentRuntimeState.canRequestAds.value) {
            val consentReason = AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked()
            Timber.d("AppOpen show blocked: no consent route=%s", route)
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                adUnitId = currentAdUnitId ?: "unknown",
                suppressReason = consentReason,
                route = route,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                suppressReason = consentReason,
                route = route,
                trigger = triggerReason.analyticsValue,
                diagnostics = currentDiagnostics(),
            )
            clearAd()
            onShowComplete(ShowCompletionReason.BLOCKED)
            return
        }
        val prefs = preferencesDataSource.userData.first()
        val now = SystemTimeProvider.nowMillis()
        val policy = adsPolicyProvider.getPolicy()

        if (!policy.isAppOpenPlacementEnabled(currentPlacement)) {
            Timber.d(
                "AppOpen show blocked: placement disabled placement=%s route=%s",
                currentPlacement.analyticsValue,
                route,
            )
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                adUnitId = currentAdUnitId ?: "unknown",
                suppressReason = AdSuppressReason.PLACEMENT_DISABLED,
                route = route,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                suppressReason = AdSuppressReason.PLACEMENT_DISABLED,
                route = route,
                trigger = triggerReason.analyticsValue,
                diagnostics = currentDiagnostics(),
            )
            onShowComplete(ShowCompletionReason.BLOCKED)
            return
        }

        if (prefs.isPremium || prefs.rewardedAdFreeUntil > now) {
            val reason = if (prefs.isPremium) AdSuppressReason.PREMIUM else AdSuppressReason.REWARDED_FREE
            Timber.d(
                "AppOpen show blocked: premiumOrRewardedFree reason=%s route=%s",
                reason.analyticsValue,
                route,
            )
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                adUnitId = currentAdUnitId ?: "unknown",
                suppressReason = reason,
                route = route,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                suppressReason = reason,
                route = route,
                trigger = triggerReason.analyticsValue,
                diagnostics = currentDiagnostics(),
            )
            onShowComplete(ShowCompletionReason.BLOCKED)
            return
        }

        val cooldownMs = effectiveCooldownMs(policy.appOpenCooldownMs)
        if (now - prefs.lastAppOpenAdShown < cooldownMs) {
            Timber.d(
                "AppOpen show blocked: cooldown remainingMs=%d route=%s",
                cooldownMs - (now - prefs.lastAppOpenAdShown),
                route,
            )
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                adUnitId = currentAdUnitId ?: "unknown",
                suppressReason = AdSuppressReason.COOLDOWN,
                route = route,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                suppressReason = AdSuppressReason.COOLDOWN,
                route = route,
                trigger = triggerReason.analyticsValue,
                diagnostics = currentDiagnostics(now),
            )
            onShowComplete(ShowCompletionReason.BLOCKED)
            return
        }

        val ad = appOpenAd
        if (ad == null || !isAdAvailable()) {
            Timber.d(
                "AppOpen show blocked: not loaded adNull=%s available=%s route=%s",
                ad == null,
                isAdAvailable(),
                route,
            )
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                adUnitId = currentAdUnitId ?: "unknown",
                suppressReason = AdSuppressReason.NOT_LOADED,
                route = route,
            )
            adRevenueLogger.logShowNotLoaded(
                adFormat = AdFormat.APP_OPEN,
                placement = currentPlacement,
                route = route,
                trigger = triggerReason.analyticsValue,
                diagnostics = currentDiagnostics(),
            )
            onShowComplete(ShowCompletionReason.NOT_LOADED)
            return
        }

        var impressionStampRecorded = false
        isShowingAd = true
        adRevenueLogger.logShowStarted(
            adFormat = AdFormat.APP_OPEN,
            placement = currentPlacement,
            adUnitId = ad.adUnitId,
            route = route,
            trigger = triggerReason.analyticsValue,
        )
        Timber.d(
            "AppOpen show starting placement=%s route=%s adUnit=%s",
            currentPlacement.analyticsValue,
            route,
            ad.adUnitId,
        )
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Timber.d(
                    "AppOpen dismissed placement=%s route=%s adUnit=%s",
                    currentPlacement.analyticsValue,
                    route,
                    ad.adUnitId,
                )
                adRevenueLogger.logDismissed(
                    adFormat = AdFormat.APP_OPEN,
                    placement = currentPlacement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
                adRevenueLogger.logShowDismissed(
                    adFormat = AdFormat.APP_OPEN,
                    placement = currentPlacement,
                    adUnitId = ad.adUnitId,
                    route = route,
                    trigger = triggerReason.analyticsValue,
                )
                appOpenAd = null
                isShowingAd = false
                onShowComplete(ShowCompletionReason.ATTEMPTED)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Timber.d(
                    "AppOpen failedToShow placement=%s route=%s code=%d msg=%s",
                    currentPlacement.analyticsValue,
                    route,
                    adError.code,
                    adError.message,
                )
                adRevenueLogger.logFailedToShow(
                    adFormat = AdFormat.APP_OPEN,
                    placement = currentPlacement,
                    adUnitId = ad.adUnitId,
                    errorCode = adError.code,
                    errorMessage = adError.message,
                    route = route,
                )
                adRevenueLogger.logShowFailed(
                    adFormat = AdFormat.APP_OPEN,
                    placement = currentPlacement,
                    adUnitId = ad.adUnitId,
                    route = route,
                    trigger = triggerReason.analyticsValue,
                    errorCode = adError.code,
                    errorMessage = adError.message,
                )
                appOpenAd = null
                isShowingAd = false
                onShowComplete(ShowCompletionReason.ATTEMPTED)
            }

            override fun onAdImpression() {
                Timber.d(
                    "AppOpen impression placement=%s route=%s adUnit=%s",
                    currentPlacement.analyticsValue,
                    route,
                    ad.adUnitId,
                )
                adRevenueLogger.logImpression(
                    adFormat = AdFormat.APP_OPEN,
                    placement = currentPlacement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
                adRevenueLogger.logShowImpression(
                    adFormat = AdFormat.APP_OPEN,
                    placement = currentPlacement,
                    adUnitId = ad.adUnitId,
                    route = route,
                    trigger = triggerReason.analyticsValue,
                )
                onAdImpression(ad.adUnitId)
                if (shouldRecordImpressionStamp(impressionStampRecorded)) {
                    impressionStampRecorded = true
                    callbackScope.launch {
                        preferencesDataSource.setLastAppOpenAdShown(SystemTimeProvider.nowMillis())
                    }
                }
            }

            override fun onAdClicked() {
                Timber.d(
                    "AppOpen clicked placement=%s route=%s adUnit=%s",
                    currentPlacement.analyticsValue,
                    route,
                    ad.adUnitId,
                )
                adRevenueLogger.logClick(
                    adFormat = AdFormat.APP_OPEN,
                    placement = currentPlacement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
            }

            override fun onAdShowedFullScreenContent() {
                Timber.d(
                    "AppOpen showedFullScreen placement=%s route=%s adUnit=%s",
                    currentPlacement.analyticsValue,
                    route,
                    ad.adUnitId,
                )
                adRevenueLogger.logServed(
                    adFormat = AdFormat.APP_OPEN,
                    placement = currentPlacement,
                    adUnitId = ad.adUnitId,
                    route = route,
                )
            }
        }

        ad.show(activity)
    }

    fun clearAd() {
        Timber.d(
            "AppOpen clearAd placement=%s hasAd=%s loading=%s showing=%s",
            currentPlacement.analyticsValue,
            appOpenAd != null,
            isLoadingAd,
            isShowingAd,
        )
        appOpenAd = null
        isLoadingAd = false
        loadTime = 0L
        isShowingAd = false
    }

    private fun isAdAvailable(): Boolean = appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)

    private fun currentDiagnostics(nowMillis: Long = SystemTimeProvider.nowMillis()): AdShowDiagnosticContext =
        AdShowDiagnosticContext(
            isLoading = isLoadingAd,
            currentAdUnitId = currentAdUnitId,
            timeSinceLastLoadStartMs =
                lastLoadStartedAtMillis.takeIf { it > 0L }?.let { startedAt ->
                    (nowMillis - startedAt).coerceAtLeast(0L)
                },
            backoffNextAllowedAtMs =
                loadBackoffState.nextLoadAllowedAtMillis.takeIf { it > 0L },
        )

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3_600_000
        return dateDifference < numMilliSecondsPerHour * numHours
    }
}
