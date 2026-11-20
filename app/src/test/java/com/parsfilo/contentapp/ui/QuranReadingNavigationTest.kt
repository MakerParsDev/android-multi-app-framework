package com.parsfilo.contentapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranReadingNavigationTest {
    @Test
    fun firstAndRepeatedSuraSelectionsNavigateDirectly() {
        val visited = mutableListOf<String>()

        repeat(3) {
            navigateToQuranReading(suraNumber = 1) { visited += it }
        }

        assertEquals(
            listOf("quran_sura_detail/1", "quran_sura_detail/1", "quran_sura_detail/1"),
            visited,
        )
    }

    @Test
    fun lastReadResumesAtTheExactVerseWithoutAnAdCallback() {
        val visited = mutableListOf<String>()

        navigateToQuranReading(suraNumber = 2, ayahNumber = 13) { visited += it }

        assertEquals(listOf("quran_sura_detail/2?ayahNumber=13"), visited)
    }
}
