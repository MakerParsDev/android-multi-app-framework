package com.parsfilo.contentapp.feature.content.ui

import com.parsfilo.contentapp.core.model.Verse

internal data class VerseTranslation(
    val text: String,
    val isTurkishFallback: Boolean
)

internal fun selectVerseTranslation(verse: Verse, language: String): VerseTranslation {
    val locale = language.lowercase()
    return when {
        locale.startsWith("de") && verse.german.isNotBlank() ->
            VerseTranslation(verse.german, isTurkishFallback = false)
        locale.startsWith("en") && verse.english.isNotBlank() ->
            VerseTranslation(verse.english, isTurkishFallback = false)
        locale.startsWith("tr") ->
            VerseTranslation(verse.turkish, isTurkishFallback = false)
        else ->
            VerseTranslation(verse.turkish, isTurkishFallback = true)
    }
}
