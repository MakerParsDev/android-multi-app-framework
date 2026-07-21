package com.parsfilo.contentapp.feature.prayertimes.data

import java.util.Locale

internal object PrayerLocationMatcher {
    fun normalize(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace("i̇", "i")
            .replace('ı', 'i')
            .replace('İ', 'i')
            .replace('ş', 's')
            .replace('Ş', 's')
            .replace('ğ', 'g')
            .replace('Ğ', 'g')
            .replace('ç', 'c')
            .replace('Ç', 'c')
            .replace('ö', 'o')
            .replace('Ö', 'o')
            .replace('ü', 'u')
            .replace('Ü', 'u')
            .replace("'", "")
            .replace("-", " ")
            .trim()
    }

    fun <T> bestMatch(
        input: String,
        items: List<T>,
        trName: (T) -> String,
        enName: (T) -> String,
    ): T? {
        val normalizedInput = normalize(input)
        if (normalizedInput.isBlank()) return null

        return items.firstOrNull {
            normalize(trName(it)) == normalizedInput || normalize(enName(it)) == normalizedInput
        } ?: items.firstOrNull {
            normalize(trName(it)).contains(normalizedInput) ||
                normalize(enName(it)).contains(normalizedInput)
        }
    }
}
