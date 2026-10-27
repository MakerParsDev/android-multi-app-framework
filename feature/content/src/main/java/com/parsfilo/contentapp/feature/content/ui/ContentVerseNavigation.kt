package com.parsfilo.contentapp.feature.content.ui

import com.parsfilo.contentapp.core.model.Verse

/** Translate a canonical verse ID to the actual lazy list slot, including preceding inline ads. */
internal fun contentLazyItemIndexForVerse(
    verses: List<Verse>,
    verseId: Int,
    inlineAds: Boolean,
): Int? {
    val index = verses.indexOfFirst { it.id == verseId }
    if (index < 0) return null
    if (!inlineAds) return index
    val adsBefore = (0 until index).count { previous ->
        shouldInsertNativeAdAfterVerse(previous, verses.size)
    }
    return index + adsBefore
}

/** Never silently rewrite pasted malformed text into a different canonical verse ID. */
internal fun canonicalVerseId(raw: String): Int? {
    if (raw.isEmpty() || raw.length > 6 || raw.any { it !in '0'..'9' }) return null
    return raw.toIntOrNull()?.takeIf { it > 0 }
}

/** One composed lazy item and its actual measured position; no prefetch-derived progress. */
internal data class VisibleContentItem(val key: Any, val offset: Int, val size: Int)

/** Choose the verse actually most visible *when the user presses Save*. Ads are ignored. */
internal fun visibleVerseToSave(
    visible: List<VisibleContentItem>,
    viewportStart: Int,
    viewportEnd: Int,
): Int? {
    if (viewportEnd <= viewportStart) return null
    return visible.mapNotNull { item ->
        val key = item.key as? String ?: return@mapNotNull null
        if (!key.startsWith("verse_")) return@mapNotNull null
        val id = canonicalVerseId(key.removePrefix("verse_")) ?: return@mapNotNull null
        val overlap = (
            minOf(viewportEnd, item.offset + item.size) -
                maxOf(viewportStart, item.offset)
        ).coerceAtLeast(0)
        if (overlap == 0) null else id to overlap
    }.maxByOrNull { it.second }?.first
}
