package com.parsfilo.contentapp.feature.content.ui.miracles

import com.parsfilo.contentapp.core.model.MiraclesPrayer
import java.text.Normalizer
import java.util.Locale

/** Search the canonical names without changing stored data or their detail indices. */
internal fun findEsmaNames(
    prayers: List<MiraclesPrayer>,
    query: String,
): List<IndexedValue<MiraclesPrayer>> {
    val normalized = foldName(query)
    if (normalized.isEmpty()) return prayers.withIndex().toList()
    val numeric = query.trim().toIntOrNull()
    if (numeric != null) return prayers.withIndex().filter { (index, _) -> index + 1 == numeric }
    return prayers.withIndex().filter { (_, prayer) ->
        foldName(prayer.duaLatinOkunus).contains(normalized) ||
            foldName(prayer.duaIsim).contains(normalized) ||
            foldName(prayer.duaArapca).contains(normalized)
    }
}

/** Case/diacritic/punctuation folding with stable Turkish dotted/dotless-I matching. */
private fun foldName(value: String): String {
    val lower = value.lowercase(Locale.ROOT).replace('ı', 'i')
    val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
    return decomposed.filter { char ->
        char.isLetterOrDigit() && Character.getType(char) != Character.NON_SPACING_MARK.toInt()
    }
}
