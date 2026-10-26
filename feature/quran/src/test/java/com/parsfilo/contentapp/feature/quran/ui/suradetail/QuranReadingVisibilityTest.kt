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
    fun nativeAdsGetSeparateRowsAndNeverMapToAnAyah() {
        val rows = quranLazyRowAyahIndices(
            ayahCount = 8,
            includeNativeAds = true,
            shouldInsertAdAfter = { index, _ -> index == 4 },
        )
        assertEquals(listOf(0, 1, 2, 3, 4, null, 5, 6, 7), rows)
        // An ad is fully on screen while the preceding verse is completely
        // offscreen. Only ayah rows are eligible to become reading markers.
        val visibleRows = listOf(QuranVisibleItem(index = 5, offset = 0, size = 1100))
        val visibleAyahs = visibleRows.mapNotNull { row ->
            rows.getOrNull(row.index)?.let { row.copy(index = it) }
        }
        assertNull(firstMeaningfullyVisibleAyahIndex(visibleAyahs, 0, 1000))
        assertEquals(7, rows.indexOf(6))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), quranLazyRowAyahIndices(8, false) { _, _ -> true })
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
