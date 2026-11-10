package com.parsfilo.contentapp.feature.content.ui

internal enum class ReaderBannerPosition {
    HIDDEN,
    ABOVE_VERSES,
    AFTER_VERSES,
}

/** Keep portrait banners above the reader; only short landscape defers them. */
internal fun resolveReaderBannerPosition(showTopBanner: Boolean, compactLandscape: Boolean): ReaderBannerPosition =
    when {
        !showTopBanner -> ReaderBannerPosition.HIDDEN
        compactLandscape -> ReaderBannerPosition.AFTER_VERSES
        else -> ReaderBannerPosition.ABOVE_VERSES
    }

private const val MIN_CONTENT_ITEMS_FOR_INLINE_FEED_ADS = 12
private const val INLINE_FEED_AD_INTERVAL = 10
private const val MIN_CONTENT_ITEMS_AFTER_INLINE_FEED_AD = 2

fun shouldShowTopBannerForScrollableContent(totalContentItems: Int): Boolean =
    totalContentItems > 0

fun shouldPreferInlineFeedAds(totalContentItems: Int): Boolean =
    totalContentItems >= MIN_CONTENT_ITEMS_FOR_INLINE_FEED_ADS

fun shouldInsertNativeAdAfterVerse(
    verseIndex: Int,
    totalVerses: Int
): Boolean = shouldInsertInlineFeedAdAfterItem(
    itemIndex = verseIndex,
    totalItems = totalVerses,
)

fun shouldInsertInlineFeedAdAfterItem(
    itemIndex: Int,
    totalItems: Int,
): Boolean {
    if (!shouldPreferInlineFeedAds(totalItems)) return false
    if (itemIndex < 0 || itemIndex >= totalItems) return false

    val consumedItems = itemIndex + 1
    val remainingItems = totalItems - consumedItems

    return consumedItems % INLINE_FEED_AD_INTERVAL == 0 &&
        remainingItems >= MIN_CONTENT_ITEMS_AFTER_INLINE_FEED_AD
}
