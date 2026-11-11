package com.parsfilo.contentapp.feature.ads.ui

import com.parsfilo.contentapp.feature.ads.AdPlacement

const val MAX_INLINE_BANNER_HEIGHT_DP = 80
internal const val MAX_CONTENT_BANNER_WIDTH_DP = 360
internal const val STANDARD_BANNER_WIDTH_DP = 320

/** Bound banner requests to the actual parent width across multi-window sizes. */
internal fun BannerSizingPolicy.requestWidthDp(availableWidthDp: Int): Int =
    when {
        availableWidthDp <= 0 -> 0
        mode == BannerSizingMode.FIXED_STANDARD && availableWidthDp < STANDARD_BANNER_WIDTH_DP -> 0
        mode == BannerSizingMode.FIXED_STANDARD -> STANDARD_BANNER_WIDTH_DP
        else -> availableWidthDp.coerceAtMost(MAX_CONTENT_BANNER_WIDTH_DP)
    }

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
