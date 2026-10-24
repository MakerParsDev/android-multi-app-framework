package com.parsfilo.contentapp.feature.prayertimes.ui

import java.text.Normalizer
import java.util.Locale

/**
 * Fold Turkish letters and Unicode combining marks for search only.
 * Keep the original place names and provider identifiers for display and persistence.
 */
internal object PrayerLocationSearch {
    private val combiningMarks = Regex("\\p{M}+")

    fun normalize(value: String): String {
        val lower = value.trim().lowercase(Locale.ROOT).replace('ı', 'i')
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(combiningMarks, "")
    }

    fun <T> filter(items: List<T>, query: String, name: (T) -> String): List<T> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return items
        return items.filter { normalize(name(it)).contains(normalizedQuery) }
    }
}
