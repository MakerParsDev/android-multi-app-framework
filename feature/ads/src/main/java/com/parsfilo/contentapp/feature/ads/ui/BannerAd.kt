package com.parsfilo.contentapp.feature.ads.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.parsfilo.contentapp.feature.ads.AdFormat
import com.parsfilo.contentapp.feature.ads.AdPaidEventContext
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.feature.ads.AdSuppressReason
import com.parsfilo.contentapp.feature.ads.AdsConsentRuntimeState
import com.parsfilo.contentapp.feature.ads.AdsUiEntryPoint
import com.parsfilo.contentapp.feature.ads.SystemTimeProvider
import com.parsfilo.contentapp.feature.ads.findActivity
import com.parsfilo.contentapp.feature.ads.suppressReasonWhenBlocked
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

@Composable
fun BannerAd(
    adUnitId: String,
    modifier: Modifier = Modifier,
    placement: AdPlacement = AdPlacement.BANNER_DEFAULT,
    route: String? = null,
    showPlacementLabels: Boolean = true,
) {
    val canRequestAds by AdsConsentRuntimeState.canRequestAds.collectAsState()
    val privacyState by AdsConsentRuntimeState.state.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adContext = remember(context) { context.findActivity() ?: context }
    val appContext = context.applicationContext
    val entryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, AdsUiEntryPoint::class.java)
    }
    val revenueLogger = remember(entryPoint) { entryPoint.adRevenueLogger() }
    val adGateChecker = remember(entryPoint) { entryPoint.adGateChecker() }
    val adsPolicyProvider = remember(entryPoint) { entryPoint.adsPolicyProvider() }
    val shouldShowAds by adGateChecker.shouldShowAds.collectAsState(initial = false)
    val policy = adsPolicyProvider.getPolicy()
    val suppressReason: AdSuppressReason? = when {
        !canRequestAds -> privacyState.suppressReasonWhenBlocked()
        !policy.isBannerPlacementEnabled(placement) -> AdSuppressReason.PLACEMENT_DISABLED
        !shouldShowAds -> AdSuppressReason.AD_GATE
        else -> null
    }
    if (suppressReason != null) {
        LaunchedEffect(adUnitId, placement, route, suppressReason) {
            Timber.d(
                "Banner suppressed placement=%s route=%s reason=%s adUnit=%s",
                placement.analyticsValue,
                route,
                suppressReason,
                adUnitId,
            )
            revenueLogger.logSuppressed(
                adFormat = AdFormat.BANNER,
                placement = placement,
                adUnitId = adUnitId,
                suppressReason = suppressReason,
                route = route,
            )
        }
        return
    }

    val adRequest = remember { AdRequest.Builder().build() }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val sizingPolicy = remember(placement) { placement.bannerSizingPolicy() }
        // Use the measured slot rather than physical screen width so split-screen,
        // rotation and gesture insets cannot create an oversized request.
        val availableWidthDp = maxWidth.value.toInt()
        if (availableWidthDp <= 0) return@BoxWithConstraints

        val adWidthDp = sizingPolicy.requestWidthDp(availableWidthDp)
        // Standard banners require 320dp. Do not substitute a taller adaptive
        // anchored creative on narrow windows: reading space takes precedence.
        if (adWidthDp <= 0) return@BoxWithConstraints
        val adSize = remember(sizingPolicy, adWidthDp) {
            sizingPolicy.toAdSize(adWidthDp)
        }
        // Keep the state and the SDK view keyed to the same request identity:
        // both placement and route are captured by the AdListener callbacks.
        val requestKey = BannerAdRequestKey(
            adUnitId = adUnitId,
            placement = placement,
            route = route,
            widthDp = adWidthDp,
            sizeWidth = adSize.width,
            sizeHeight = adSize.height,
            showPlacementLabels = showPlacementLabels,
        )
        val adView = remember(requestKey, adContext) {
            val loadStartedAtMillis = SystemTimeProvider.nowMillis()
            // The SDK can auto-refresh without calling our loadAd again. Only
            // the explicit first request has a measurable start timestamp.
            var initialLoadPending = true
            AdView(adContext).apply {
                this.adUnitId = adUnitId
                setAdSize(adSize)
                Timber.d(
                    "Banner load requested placement=%s widthDp=%d heightDp=%d route=%s adUnit=%s labels=%s",
                    placement.analyticsValue,
                    adWidthDp,
                    adSize.height,
                    route,
                    adUnitId,
                    showPlacementLabels,
                )
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        val fillLatencyMs =
                            if (initialLoadPending) {
                                (SystemTimeProvider.nowMillis() - loadStartedAtMillis).coerceAtLeast(0L)
                            } else {
                                null
                            }
                        initialLoadPending = false
                        revenueLogger.logLoaded(
                            adFormat = AdFormat.BANNER,
                            placement = placement,
                            adUnitId = adUnitId,
                            route = route,
                            fillLatencyMs = fillLatencyMs,
                            adapterName = responseInfo?.mediationAdapterClassName,
                        )
                        revenueLogger.logServed(
                            adFormat = AdFormat.BANNER,
                            placement = placement,
                            adUnitId = adUnitId,
                            route = route,
                        )
                    }

                    override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                        initialLoadPending = false
                        revenueLogger.logFailedToLoad(
                            adFormat = AdFormat.BANNER,
                            placement = placement,
                            adUnitId = adUnitId,
                            errorCode = error.code,
                            errorMessage = error.message,
                            errorDomain = error.domain,
                            responseInfo = error.responseInfo,
                            route = route,
                            backoffAttempt = null,
                        )
                    }

                    override fun onAdImpression() {
                        revenueLogger.logImpression(
                            adFormat = AdFormat.BANNER,
                            placement = placement,
                            adUnitId = adUnitId,
                            route = route,
                        )
                    }

                    override fun onAdClicked() {
                        revenueLogger.logClick(
                            adFormat = AdFormat.BANNER,
                            placement = placement,
                            adUnitId = adUnitId,
                            route = route,
                        )
                    }
                }
                onPaidEventListener = { adValue ->
                    revenueLogger.logPaidEvent(
                        AdPaidEventContext(
                            adUnitId = adUnitId,
                            adFormat = AdFormat.BANNER,
                            placement = placement,
                            route = route,
                            adValue = adValue,
                            responseMeta = revenueLogger.extractResponseMeta(responseInfo),
                        ),
                    )
                }
                revenueLogger.logRequest(
                    adFormat = AdFormat.BANNER,
                    placement = placement,
                    adUnitId = adUnitId,
                    route = route,
                )
                loadAd(adRequest)
            }
        }

        // Keep the view attached after a failed load: for AdMob-managed banner
        // refresh, the SDK retries only while the banner stays visible. Removing
        // and destroying this view prevents that recovery and manual retries can
        // duplicate the SDK's own requests.
        DisposableEffect(adView, lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        Timber.d("Banner resume placement=%s route=%s", placement.analyticsValue, route)
                        adView.resume()
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        Timber.d("Banner pause placement=%s route=%s", placement.analyticsValue, route)
                        adView.pause()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                Timber.d("Banner destroy placement=%s route=%s", placement.analyticsValue, route)
                lifecycleOwner.lifecycle.removeObserver(observer)
                adView.destroy()
            }
        }

        AndroidView(
            modifier = Modifier.width(adWidthDp.dp),
            factory = { adView },
            update = { view ->
                if (view.adUnitId != adUnitId) {
                    view.adUnitId = adUnitId
                    view.setAdSize(adSize)
                    revenueLogger.logRequest(
                        adFormat = AdFormat.BANNER,
                        placement = placement,
                        adUnitId = adUnitId,
                        route = route,
                    )
                    view.loadAd(adRequest)
                }
            },
        )
    }
}

/** Identity shared by load state and SDK view, including callback attribution. */
internal data class BannerAdRequestKey(
    val adUnitId: String,
    val placement: AdPlacement,
    val route: String?,
    val widthDp: Int,
    val sizeWidth: Int,
    val sizeHeight: Int,
    val showPlacementLabels: Boolean,
)

private fun BannerSizingPolicy.toAdSize(adWidthDp: Int): AdSize =
    when (mode) {
        BannerSizingMode.INLINE_ADAPTIVE ->
            AdSize.getInlineAdaptiveBannerAdSize(
                adWidthDp,
                requireNotNull(maxHeightDp),
            )

        BannerSizingMode.FIXED_STANDARD -> AdSize.BANNER
    }
