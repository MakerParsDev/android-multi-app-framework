package com.parsfilo.contentapp.feature.ads

import android.content.Context
import com.google.android.gms.ads.AdLoader
import javax.inject.Inject

/**
 * Tiny AdMob boundary: keep SDK construction in production, while unit tests
 * supply a fake builder and explicitly fire the same SDK callbacks.
 */
class NativeAdLoaderFactory @Inject constructor() {
    fun create(context: Context, adUnitId: String): AdLoader.Builder =
        AdLoader.Builder(context, adUnitId)
}
