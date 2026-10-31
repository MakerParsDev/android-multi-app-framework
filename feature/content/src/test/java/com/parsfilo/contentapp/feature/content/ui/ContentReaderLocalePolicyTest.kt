package com.parsfilo.contentapp.feature.content.ui

import com.parsfilo.contentapp.core.model.Verse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentReaderLocalePolicyTest {
    private val verse = Verse(
        id = 1,
        arabic = "نص",
        latin = "Latin",
        turkish = "Türkçe anlam",
        english = "English meaning",
        german = "Deutsche Bedeutung"
    )

    @Test
    fun translatedContentUsesAvailableEnglishAndGerman() {
        assertEquals("English meaning", selectVerseTranslation(verse, "en").text)
        assertEquals("Deutsche Bedeutung", selectVerseTranslation(verse, "de").text)
        assertFalse(selectVerseTranslation(verse, "en").isTurkishFallback)
        assertFalse(selectVerseTranslation(verse, "de").isTurkishFallback)
    }

    @Test
    fun missingLocalesKeepOriginalTurkishAndIdentifyFallback() {
        val onlyTurkish = verse.copy(english = "", german = "")
        assertEquals("Türkçe anlam", selectVerseTranslation(onlyTurkish, "en").text)
        assertEquals("Türkçe anlam", selectVerseTranslation(onlyTurkish, "de").text)
        assertTrue(selectVerseTranslation(onlyTurkish, "en").isTurkishFallback)
        assertTrue(selectVerseTranslation(onlyTurkish, "de").isTurkishFallback)
        assertFalse(selectVerseTranslation(onlyTurkish, "tr").isTurkishFallback)
    }

    @Test
    fun readerKeepsScrollableVerseViewportOnShortScreensOnly() {
        assertTrue(shouldUseCompactReader(283f))
        assertFalse(shouldUseCompactReader(627f))
        assertFalse(shouldUseCompactReader(420f))
        assertFalse(shouldUseCompactReader(569f, 1f))
        assertTrue(shouldUseCompactReader(569f, 2f))
        assertTrue(shouldUseCompactReader(700f, 1.8f))
        assertFalse(shouldUseCompactReader(720f, 2f))
    }

    @Test
    fun readerModesStackForNarrowScreensOrLargeSystemFont() {
        assertTrue(shouldStackReaderModes(320f, 1f))
        assertTrue(shouldStackReaderModes(360f, 2f))
        assertTrue(shouldStackReaderModes(411f, 1.5f))
        assertFalse(shouldStackReaderModes(360f, 1f))
        assertFalse(shouldStackReaderModes(411f, 1f))
        assertFalse(shouldStackReaderModes(731f, 2f))
        assertTrue(shouldStackReaderModes(599f, 2f))
    }
}
