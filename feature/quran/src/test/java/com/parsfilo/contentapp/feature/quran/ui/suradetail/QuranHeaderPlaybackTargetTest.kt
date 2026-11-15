package com.parsfilo.contentapp.feature.quran.ui.suradetail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuranHeaderPlaybackTargetTest {
    @Test
    fun emptyReaderDisablesHeaderPlayback() {
        assertThat(
            quranHeaderPlaybackTarget(
                ayahNumbers = emptyList(),
                visibleAyahNumber = null,
                playingAyahNumber = null,
                isPlaying = false,
            ),
        ).isNull()
    }

    @Test
    fun playTargetsCurrentlyVisibleAyahInsteadOfAlwaysFirst() {
        assertThat(
            quranHeaderPlaybackTarget(
                ayahNumbers = listOf(1, 2, 3, 4),
                visibleAyahNumber = 3,
                playingAyahNumber = null,
                isPlaying = false,
            ),
        ).isEqualTo(3)
    }

    @Test
    fun pauseTargetsActiveAyahEvenAfterReaderScrolls() {
        assertThat(
            quranHeaderPlaybackTarget(
                ayahNumbers = listOf(1, 2, 3, 4),
                visibleAyahNumber = 1,
                playingAyahNumber = 4,
                isPlaying = true,
            ),
        ).isEqualTo(4)
    }

    @Test
    fun savedPlaceIsPreferredBeforeInitialRestoreCompletes() {
        assertThat(
            quranHeaderPlaybackTarget(
                ayahNumbers = listOf(1, 2, 3, 4),
                visibleAyahNumber = 1,
                playingAyahNumber = null,
                isPlaying = false,
                pendingRestoreAyahNumber = 3,
            ),
        ).isEqualTo(3)
    }

    @Test
    fun missingActiveAyahFallsBackToVisibleThenFirst() {
        assertThat(
            quranHeaderPlaybackTarget(
                ayahNumbers = listOf(1, 2, 3),
                visibleAyahNumber = 2,
                playingAyahNumber = 99,
                isPlaying = true,
            ),
        ).isEqualTo(2)
        assertThat(
            quranHeaderPlaybackTarget(
                ayahNumbers = listOf(1, 2, 3),
                visibleAyahNumber = null,
                playingAyahNumber = null,
                isPlaying = false,
            ),
        ).isEqualTo(1)
    }
}
