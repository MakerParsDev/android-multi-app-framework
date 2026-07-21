package com.parsfilo.contentapp.feature.ads.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
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
import kotlin.math.max

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
    val dimens = LocalDimens.current
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space6),
    ) {
        val adWidthDp = max(1, maxWidth.value.toInt())
        val adSize = remember(adContext, placement, adWidthDp) {
            placement.adaptiveBannerSize(adContext, adWidthDp)
        }
        val adView = remember(adUnitId, adWidthDp, showPlacementLabels, adSize) {
            val loadStartedAtMillis = SystemTimeProvider.nowMillis()
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
                        val fillLatencyMs = (SystemTimeProvider.nowMillis() - loadStartedAtMillis).coerceAtLeast(0L)
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
                        revenueLogger.logFailedToLoad(
                            adFormat = AdFormat.BANNER,
                            placement = placement,
                            adUnitId = adUnitId,
                            errorCode = error.code,
                            errorMessage = error.message,
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
            modifier = Modifier.fillMaxWidth(),
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

private fun AdPlacement.adaptiveBannerSize(
    context: android.content.Context,
    adWidthDp: Int,
): AdSize =
    when (this) {
        AdPlacement.BANNER_CONTENT_LIST,
        AdPlacement.BANNER_CONTENT_DETAIL,
        -> AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, adWidthDp)

        else -> AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, adWidthDp)
    }
