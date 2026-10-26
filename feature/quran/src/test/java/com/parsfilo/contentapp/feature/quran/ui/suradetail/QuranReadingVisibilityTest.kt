package com.parsfilo.contentapp.feature.quran.ui.suradetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuranReadingVisibilityTest {
    @Test
    fun prefetchedFifthVerseDoesNotAdvanceVisibleThirdAndFourth() {
        assertEquals(
            3,
            firstMeaningfullyVisibleAyahIndex(
                listOf(
                    QuranVisibleItem(2, -250, 1000), // verse 3, genuinely visible
                    QuranVisibleItem(3, 780, 500), // verse 4, partly visible
                    QuranVisibleItem(4, 1320, 500), // verse 5, prefetched offscreen
                ),
                viewportStart = 0,
                viewportEnd = 1100,
            )?.plus(1),
        )
    }

    @Test
    fun edgeSliverIsIgnoredInFavorOfNextMeaningfullyVisibleVerse() {
        assertEquals(
            4,
            firstMeaningfullyVisibleAyahIndex(
                listOf(QuranVisibleItem(2, -999, 1000), QuranVisibleItem(3, 1, 900)),
                viewportStart = 0,
                viewportEnd = 1000,
            )?.plus(1),
        )
    }

    @Test
    fun longVerseLargerThanViewportCanCountWhenHalfViewportIsVisible() {
        assertEquals(
            7,
            firstMeaningfullyVisibleAyahIndex(
                listOf(QuranVisibleItem(6, -1450, 2050)),
                viewportStart = 0,
                viewportEnd = 1000,
            )?.plus(1),
        )
    }

    @Test
    fun onlyEdgeSliverDoesNotReportProgress() {
        assertNull(
            firstMeaningfullyVisibleAyahIndex(
                listOf(QuranVisibleItem(4, -990, 1000)),
                viewportStart = 0,
                viewportEnd = 1000,
            ),
        )
    }

    @Test
    fun noScrollInitialVerseIsVisibleAndNoLayoutMeansNoProgress() {
        assertEquals(
            0,
            firstMeaningfullyVisibleAyahIndex(
                listOf(QuranVisibleItem(0, 0, 700), QuranVisibleItem(1, 740, 600)),
                viewportStart = 0,
                viewportEnd = 1000,
            ),
        )
        assertNull(firstMeaningfullyVisibleAyahIndex(emptyList(), 0, 1000))
        assertNull(firstMeaningfullyVisibleAyahIndex(listOf(QuranVisibleItem(0, 0, 300)), 0, 0))
    }
}
