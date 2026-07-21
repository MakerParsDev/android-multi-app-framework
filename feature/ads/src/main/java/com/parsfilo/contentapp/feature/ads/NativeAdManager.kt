package com.parsfilo.contentapp.feature.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private data class PooledNativeAd(
    val ad: NativeAd,
    val loadedAtMillis: Long,
    val placement: AdPlacement,
    val adUnitId: String,
)

@Singleton
class NativeAdManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val adRevenueLogger: AdRevenueLogger,
    private val adGateChecker: AdGateChecker,
    private val adsPolicyProvider: AdsPolicyProvider,
) {
    private val nativeAds = mutableListOf<PooledNativeAd>()
    private val gateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isLoading = false

    @Volatile
    private var canShowAdsByGate = true
    private var currentAdUnitId: String = ""
    private var currentPlacement: AdPlacement = AdPlacement.NATIVE_DEFAULT
    private var currentLoadContext: Context? = null
    private var loadBackoffState = AdLoadBackoffState()

    private val _adLoadedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val adLoadedFlow: SharedFlow<Unit> = _adLoadedFlow.asSharedFlow()

    companion object {
        private const val DEFAULT_POOL_MAX = 2
        private const val DEFAULT_NATIVE_TTL_MS = 30 * 60 * 1000L
    }

    init {
        gateScope.launch {
            adGateChecker.shouldShowAds.collectLatest { allowed ->
                canShowAdsByGate = allowed
                if (!allowed) {
                    destroyAds()
                }
            }
        }
    }

    fun loadAds(
        loadContext: Context,
        adUnitId: String,
        placement: AdPlacement = AdPlacement.NATIVE_DEFAULT,
        count: Int,
    ) {
        Timber.d(
            "Native load requested placement=%s count=%d adUnit=%s canRequestAds=%s gate=%s",
            placement.analyticsValue,
            count,
            adUnitId,
            AdsConsentRuntimeState.canRequestAds.value,
            canShowAdsByGate,
        )
        val policy = adsPolicyProvider.getPolicy()
        if (!canUseNativeAds(placement, policy)) {
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.NATIVE,
                placement = placement,
                adUnitId = adUnitId,
                suppressReason = resolveSuppressReasonForNative(placement, policy),
                route = null,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.NATIVE,
                placement = placement,
                suppressReason = resolveSuppressReasonForNative(placement, policy),
            )
            destroyAds()
            return
        }
        pruneExpiredAds()
        if (!AdLoadBackoffPolicy.canLoad(SystemTimeProvider.nowMillis(), loadBackoffState)) {
            Timber.d("Native load throttled until=%d", loadBackoffState.nextLoadAllowedAtMillis)
            return
        }
        if (isLoading) {
            Timber.d("Already loading, ignoring native load request")
            return
        }
        val poolMax = policy.nativePoolMax.takeIf { it > 0 } ?: DEFAULT_POOL_MAX
        val targetCount = synchronized(nativeAds) {
            if (nativeAds.size >= poolMax) {
                Timber.d("Native pool already full (%d)", nativeAds.size)
                return
            }
            count.coerceIn(1, (poolMax - nativeAds.size).coerceAtLeast(1))
        }

        currentAdUnitId = adUnitId
        currentPlacement = placement
        currentLoadContext = loadContext.findActivity() ?: loadContext
        isLoading = true

        val loadedCount = AtomicInteger(0)

        repeat(targetCount) {
            val requestStartedAtMillis = SystemTimeProvider.nowMillis()
            adRevenueLogger.logRequest(
                adFormat = AdFormat.NATIVE,
                placement = placement,
                adUnitId = adUnitId,
                route = null,
            )
            val adLoader = AdLoader.Builder(currentLoadContext ?: appContext, adUnitId)
                .forNativeAd { ad: NativeAd ->
                    adRevenueLogger.logLoaded(
                        adFormat = AdFormat.NATIVE,
                        placement = placement,
                        adUnitId = adUnitId,
                        route = null,
                        fillLatencyMs = (SystemTimeProvider.nowMillis() - requestStartedAtMillis).coerceAtLeast(0L),
                        adapterName = ad.responseInfo?.mediationAdapterClassName,
                    )
                    ad.setOnPaidEventListener { adValue: AdValue ->
                        adRevenueLogger.logPaidEvent(
                            AdPaidEventContext(
                                adUnitId = adUnitId,
                                adFormat = AdFormat.NATIVE,
                                placement = placement,
                                route = null,
                                adValue = adValue,
                                responseMeta = adRevenueLogger.extractResponseMeta(ad.responseInfo),
                            ),
                        )
                    }
                    synchronized(nativeAds) {
                        if (nativeAds.size >= poolMax) {
                            ad.destroy()
                        } else {
                            nativeAds.add(
                                PooledNativeAd(
                                    ad = ad,
                                    loadedAtMillis = SystemTimeProvider.nowMillis(),
                                    placement = placement,
                                    adUnitId = adUnitId,
                                ),
                            )
                        }
                    }
                    Timber.d("Native ad loaded (pool=%d)", synchronized(nativeAds) { nativeAds.size })
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (error.code == 3) {
                            Timber.i("Native no-fill code=%d msg=%s", error.code, error.message)
                        } else {
                            Timber.w("Native load warning code=%d msg=%s", error.code, error.message)
                        }
                        loadBackoffState = AdLoadBackoffPolicy.onLoadFailure(
                            nowMillis = SystemTimeProvider.nowMillis(),
                            current = loadBackoffState,
                            errorCode = error.code,
                        )
                        adRevenueLogger.logFailedToLoad(
                            adFormat = AdFormat.NATIVE,
                            placement = placement,
                            adUnitId = adUnitId,
                            errorCode = error.code,
                            errorMessage = error.message,
                            route = null,
                            backoffAttempt = loadBackoffState.failureStreak,
                        )
                        if (loadedCount.incrementAndGet() >= targetCount) {
                            isLoading = false
                        }
                    }

                    override fun onAdLoaded() {
                        loadBackoffState = AdLoadBackoffPolicy.onLoadSuccess()
                        val current = loadedCount.incrementAndGet()
                        _adLoadedFlow.tryEmit(Unit)
                        if (current >= targetCount) {
                            isLoading = false
                        }
                    }

                    override fun onAdImpression() {
                        // Native click/impression callbacks here can't be deterministically tied to the
                        // exact pooled ad instance after UI handoff. Use ILRD + served logs instead.
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setVideoOptions(
                            VideoOptions.Builder()
                                .setStartMuted(true)
                                .build(),
                        )
                        .setRequestMultipleImages(false)
                        .build(),
                )
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        }
    }

    fun getNativeAd(placement: AdPlacement = AdPlacement.NATIVE_DEFAULT): NativeAd? {
        val policy = adsPolicyProvider.getPolicy()
        adRevenueLogger.logShowIntent(
            adFormat = AdFormat.NATIVE,
            placement = placement,
            adReady = synchronized(nativeAds) { nativeAds.any { it.placement == placement } || nativeAds.isNotEmpty() },
        )
        if (!canUseNativeAds(placement, policy)) {
            Timber.d(
                "Native get blocked placement=%s reason=%s",
                placement.analyticsValue,
                resolveSuppressReasonForNative(placement, policy),
            )
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.NATIVE,
                placement = placement,
                adUnitId = currentAdUnitId.ifBlank { "unknown" },
                suppressReason = resolveSuppressReasonForNative(placement, policy),
                route = null,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.NATIVE,
                placement = placement,
                suppressReason = resolveSuppressReasonForNative(placement, policy),
            )
            destroyAds()
            return null
        }
        pruneExpiredAds()

        val pooled = synchronized(nativeAds) {
            val exactIdx = nativeAds.indexOfFirst { it.placement == placement }
            val idx = when {
                exactIdx >= 0 -> exactIdx
                !policy.nativeExactPlacementOnly && nativeAds.isNotEmpty() -> 0
                else -> null
            }
            if (idx == null) {
                null
            } else {
                val selected = nativeAds.removeAt(idx)
                if (selected.placement != placement) {
                    Timber.d(
                        "Native placement fallback requested=%s served=%s adUnit=%s",
                        placement.analyticsValue,
                        selected.placement.analyticsValue,
                        selected.adUnitId,
                    )
                }
                selected
            }
        }

        val currentSize = synchronized(nativeAds) { nativeAds.size }
        if (currentSize <= 0 && currentAdUnitId.isNotEmpty() && !isLoading) {
            loadAds(currentLoadContext ?: appContext, currentAdUnitId, placement, 1)
        }

        if (pooled != null) {
            Timber.d(
                "Native ad served placement=%s adUnit=%s",
                pooled.placement.analyticsValue,
                pooled.adUnitId,
            )
            adRevenueLogger.logShowStarted(
                adFormat = AdFormat.NATIVE,
                placement = pooled.placement,
                adUnitId = pooled.adUnitId,
                route = null,
                trigger = "native_slot",
            )
            adRevenueLogger.logServed(
                adFormat = AdFormat.NATIVE,
                placement = pooled.placement,
                adUnitId = pooled.adUnitId,
            )
        }
        if (pooled == null) {
            Timber.d(
                "Native get returned null placement=%s poolSize=%d loading=%s exactOnly=%s",
                placement.analyticsValue,
                currentSize,
                isLoading,
                policy.nativeExactPlacementOnly,
            )
            adRevenueLogger.logShowNotLoaded(
                adFormat = AdFormat.NATIVE,
                placement = placement,
                trigger = if (policy.nativeExactPlacementOnly) "exact_only" else "pool_empty",
            )
        }

        return pooled?.ad
    }

    fun destroyAds() {
        synchronized(nativeAds) {
            nativeAds.forEach { it.ad.destroy() }
            nativeAds.clear()
        }
        isLoading = false
        Timber.d("All native ads destroyed")
    }

    private fun pruneExpiredAds() {
        val ttlMs = adsPolicyProvider.getPolicy().nativeTtlMs.takeIf { it > 0 } ?: DEFAULT_NATIVE_TTL_MS
        val now = SystemTimeProvider.nowMillis()
        synchronized(nativeAds) {
            val iterator = nativeAds.iterator()
            while (iterator.hasNext()) {
                val pooled = iterator.next()
                if (now - pooled.loadedAtMillis > ttlMs) {
                    pooled.ad.destroy()
                    iterator.remove()
                }
            }
        }
    }

    private fun canUseNativeAds(
        placement: AdPlacement,
        policy: AdsPolicyConfig = adsPolicyProvider.getPolicy(),
    ): Boolean {
        if (!AdsConsentRuntimeState.canRequestAds.value) return false
        if (!canShowAdsByGate) return false
        if (!policy.isNativePlacementEnabled(placement)) return false
        return true
    }

    private fun resolveSuppressReasonForNative(
        placement: AdPlacement,
        policy: AdsPolicyConfig = adsPolicyProvider.getPolicy(),
    ): AdSuppressReason =
        when {
            !AdsConsentRuntimeState.canRequestAds.value ->
                AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked()
            !policy.isNativePlacementEnabled(placement) -> AdSuppressReason.PLACEMENT_DISABLED
            !canShowAdsByGate -> AdSuppressReason.AD_GATE
            else -> AdSuppressReason.NOT_LOADED
        }
}
