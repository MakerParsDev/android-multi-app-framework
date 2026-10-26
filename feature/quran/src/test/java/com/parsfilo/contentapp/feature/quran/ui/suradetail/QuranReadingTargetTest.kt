package com.parsfilo.contentapp.feature.quran.ui.suradetail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuranReadingTargetTest {
    @Test
    fun lastReadVerseFiveResolvesToFifthAyah() {
        assertThat(initialQuranAyahIndex((1..286).toList(), 5)).isEqualTo(4)
    }

    @Test
    fun bookmarkedVerseUsesItsLoadedPosition() {
        assertThat(initialQuranAyahIndex(listOf(1, 2, 5, 7), 5)).isEqualTo(2)
    }

    @Test
    fun invalidAndMissingTargetsDoNotSilentlyResetSavedProgress() {
        assertThat(initialQuranAyahIndex(listOf(1, 2, 3), null)).isNull()
        assertThat(initialQuranAyahIndex(listOf(1, 2, 3), -1)).isNull()
        assertThat(initialQuranAyahIndex(listOf(1, 2, 3), 5)).isNull()
    }
}
