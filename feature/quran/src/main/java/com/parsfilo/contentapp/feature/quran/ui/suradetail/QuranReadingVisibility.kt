package com.parsfilo.contentapp.feature.quran.ui.suradetail

/** Bounds from a laid-out LazyColumn item, not an item composed by prefetch. */
internal data class QuranVisibleItem(
    val index: Int,
    val offset: Int,
    val size: Int,
)

/**
 * First meaningfully seen verse: at least half of the smaller of its height and
 * the reading viewport must actually intersect that viewport.
 *
 * A long verse can exceed the entire viewport, so requiring half of the *full
 * item* would otherwise prevent any progress update while reading long verses.
 * Ignore 1px slivers at the edges, and never promote offscreen prefetched items.
 */
internal fun firstMeaningfullyVisibleAyahIndex(
    items: List<QuranVisibleItem>,
    viewportStart: Int,
    viewportEnd: Int,
): Int? {
    val viewportHeight = viewportEnd - viewportStart
    if (viewportHeight <= 0) return null
    return items.firstOrNull { item ->
        if (item.size <= 0) return@firstOrNull false
        val clippedStart = maxOf(item.offset.toLong(), viewportStart.toLong())
        val clippedEnd = minOf(item.offset.toLong() + item.size, viewportEnd.toLong())
        val visibleHeight = (clippedEnd - clippedStart).coerceAtLeast(0L)
        val thresholdHeight = minOf(item.size, viewportHeight).toLong()
        visibleHeight * 2 >= thresholdHeight
    }?.index
}

/**
 * Align LazyColumn row offsets with Quran ayahs when native ad slots are added
 * as separate rows. Null means an ad-only row which must never count as read.
 */
internal fun quranLazyRowAyahIndices(
    ayahCount: Int,
    includeNativeAds: Boolean,
    shouldInsertAdAfter: (Int, Int) -> Boolean,
): List<Int?> = buildList {
    repeat(ayahCount) { ayahIndex ->
        add(ayahIndex)
        if (includeNativeAds && shouldInsertAdAfter(ayahIndex, ayahCount)) {
            add(null)
        }
    }
}
