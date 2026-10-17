package com.parsfilo.contentapp.feature.ads.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.nativead.NativeAd
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.feature.ads.AdsConsentRuntimeState
import com.parsfilo.contentapp.feature.ads.AdsUiEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

@Composable
fun NativeFeedAdSlot(
    nativeAd: NativeAd?,
    nativePlacement: AdPlacement,
    nativeAdUnitId: String,
    bannerAdUnitId: String,
    bannerPlacement: AdPlacement,
    modifier: Modifier = Modifier,
    route: String? = null,
    useSmallFormat: Boolean = true,
) {
    val appContext = LocalContext.current.applicationContext
    val entryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, AdsUiEntryPoint::class.java)
    }
    val canRequestAds by AdsConsentRuntimeState.canRequestAds.collectAsState()
    val canShowAds by entryPoint.adGateChecker().shouldShowAds.collectAsState(initial = false)
    val policy = entryPoint.adsPolicyProvider().getPolicy()
    LaunchedEffect(nativeAd, nativePlacement, nativeAdUnitId, canRequestAds, canShowAds, policy.demandAwarePreloadEnabled) {
        if (policy.demandAwarePreloadEnabled && nativeAd == null && canRequestAds && canShowAds) {
            // A deferred request leaves nativeAd null; retry at most twice while
            // this slot remains composed and eligible. Changes to consent/slot
            // cancel the effect; manager deduplication and SDK backoff still apply.
            repeat(3) { attempt ->
                entryPoint.nativeAdManager().loadAds(
                    appContext, nativeAdUnitId, nativePlacement, count = 1,
                )
                if (attempt < 2) delay(60_000L)
            }
        }
    }
    if (nativeAd != null) {
        NativeAdItem(nativeAd = nativeAd, isSmallFormat = useSmallFormat, modifier = modifier)
        return
    }

    if (!policy.shouldUseNativeBannerFallback(appContext.packageName)) {
        return
    }

    BannerAd(
        adUnitId = bannerAdUnitId,
        placement = bannerPlacement,
        route = route,
        modifier = modifier,
    )
}
