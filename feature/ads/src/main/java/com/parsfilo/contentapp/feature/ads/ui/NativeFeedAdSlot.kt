package com.parsfilo.contentapp.feature.ads.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.nativead.NativeAd
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.feature.ads.AdsUiEntryPoint
import dagger.hilt.android.EntryPointAccessors

@Composable
fun NativeFeedAdSlot(
    nativeAd: NativeAd?,
    nativePlacement: AdPlacement,
    bannerAdUnitId: String,
    bannerPlacement: AdPlacement,
    modifier: Modifier = Modifier,
    route: String? = null,
    useSmallFormat: Boolean = true,
) {
    if (nativeAd != null) {
        NativeAdItem(nativeAd = nativeAd, isSmallFormat = useSmallFormat, modifier = modifier)
        return
    }

    val appContext = LocalContext.current.applicationContext
    val entryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, AdsUiEntryPoint::class.java)
    }
    val policy = remember(entryPoint) { entryPoint.adsPolicyProvider().getPolicy() }
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
