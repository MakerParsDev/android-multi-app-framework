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
