package com.parsfilo.contentapp.core.model

/**
 * Mucize Dua modeli
 * mucizedualar flavor'ında kullanılır
 */
data class MiraclesPrayer(
    val duaIsim: String,
    val duaAciklama: String,
    val duaBesmele: String,
    val duaArapca: String,
    val duaLatinOkunus: String = "",
    val originalArabicVerses: List<MiraclesOriginalVerse> = emptyList(),
    val originalArabicIncludesBasmala: Boolean = false,
) {
    // Legacy Mucize Dualar data stores Latin phonetics in duaArapca.
    // Never present Latin text as original Arabic scripture.
    val hasArabicScript: Boolean
        get() = duaArapca.any { char ->
            char.code in 0x0600..0x06FF ||
                char.code in 0x0750..0x077F ||
                char.code in 0x08A0..0x08FF
        }
}

// Original Quran Arabic is separate from legacy Latin phonetic data.
data class MiraclesOriginalVerse(
    val reference: String,
    val arabic: String,
)
