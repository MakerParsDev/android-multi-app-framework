package com.parsfilo.contentapp.feature.quran.ui.suradetail

/**
 * Locate the requested verse in the actual loaded sequence rather than by
 * subtracting one; a missing verse must not silently reset the reading marker.
 */
internal fun initialQuranAyahIndex(
    loadedAyahNumbers: List<Int>,
    requestedAyahNumber: Int?,
): Int? = requestedAyahNumber?.takeIf { it > 0 }?.let { requested ->
    loadedAyahNumbers.indexOf(requested).takeIf { it >= 0 }
}
