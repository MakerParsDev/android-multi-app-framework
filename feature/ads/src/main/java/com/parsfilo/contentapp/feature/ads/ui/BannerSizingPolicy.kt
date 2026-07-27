package com.parsfilo.contentapp.feature.ads.ui

import com.parsfilo.contentapp.feature.ads.AdPlacement

internal const val MAX_INLINE_BANNER_HEIGHT_DP = 80

internal enum class BannerSizingMode {
    FIXED_STANDARD,
    INLINE_ADAPTIVE,
}

internal data class BannerSizingPolicy(
    val mode: BannerSizingMode,
    val maxHeightDp: Int? = null,
)

internal fun AdPlacement.bannerSizingPolicy(): BannerSizingPolicy =
    when (this) {
        AdPlacement.BANNER_CONTENT_LIST,
        AdPlacement.BANNER_CONTENT_DETAIL,
        -> BannerSizingPolicy(
            mode = BannerSizingMode.INLINE_ADAPTIVE,
            maxHeightDp = MAX_INLINE_BANNER_HEIGHT_DP,
        )

        else -> BannerSizingPolicy(mode = BannerSizingMode.FIXED_STANDARD)
    }
