package com.parsfilo.contentapp.feature.content.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.model.Verse
import org.junit.Test

class ContentVerseNavigationTest {
    private fun verses(count: Int) = (1..count).map { Verse(it, "", "", "") }

    @Test fun initialMiddleAndLastVersesUseOriginalIds() {
        for (size in listOf(17, 29, 40, 96)) {
            val list = verses(size)
            assertThat(contentLazyItemIndexForVerse(list, 1, false)).isEqualTo(0)
            assertThat(contentLazyItemIndexForVerse(list, size / 2, false))
                .isEqualTo(size / 2 - 1)
            assertThat(contentLazyItemIndexForVerse(list, size, false)).isEqualTo(size - 1)
        }
    }

    @Test fun inlineAdSlotsDoNotOffsetTargetVersesIncorrectly() {
        val list = verses(96)
        assertThat(contentLazyItemIndexForVerse(list, 1, true)).isEqualTo(0)
        assertThat(contentLazyItemIndexForVerse(list, 10, true)).isEqualTo(9)
        assertThat(contentLazyItemIndexForVerse(list, 11, true)).isEqualTo(11)
        assertThat(contentLazyItemIndexForVerse(list, 21, true)).isEqualTo(22)
        assertThat(contentLazyItemIndexForVerse(list, 96, true)).isEqualTo(104)
    }

    @Test fun missingOrNonSequentialOriginalIdsAreHandledWithoutSubstitution() {
        val list = listOf(
            Verse(1, "", "", ""), Verse(8, "", "", ""), Verse(42, "", "", "")
        )
        assertThat(contentLazyItemIndexForVerse(list, 8, false)).isEqualTo(1)
        assertThat(contentLazyItemIndexForVerse(list, 2, false)).isNull()
        assertThat(contentLazyItemIndexForVerse(list, 0, false)).isNull()
        assertThat(contentLazyItemIndexForVerse(emptyList(), 1, true)).isNull()
    }

    @Test fun malformedPastedIdsAreRejectedNotTransformed() {
        assertThat(canonicalVerseId("12")).isEqualTo(12)
        assertThat(canonicalVerseId("0012")).isEqualTo(12)
        assertThat(canonicalVerseId("1 2")).isNull()
        assertThat(canonicalVerseId("12a")).isNull()
        assertThat(canonicalVerseId("12\n")).isNull()
        assertThat(canonicalVerseId("1-2")).isNull()
        assertThat(canonicalVerseId("9999999")).isNull()
        assertThat(canonicalVerseId("0")).isNull()
        assertThat(canonicalVerseId("")).isNull()
    }

    @Test fun savingPlacePicksMeasuredMostVisibleVerseNotPrefetchOrAd() {
        val visible = listOf(
            VisibleContentItem("native_ad_9", 0, 150),
            VisibleContentItem("verse_11", 150, 700),
            VisibleContentItem("verse_12", 850, 450),
        )
        assertThat(visibleVerseToSave(visible, 0, 1000)).isEqualTo(11)
        assertThat(visibleVerseToSave(visible, 900, 1000)).isEqualTo(12)
    }

    @Test fun immediateJumpSavePrefersSelectedVerseOverLargerFollowingVerse() {
        val visible = listOf(
            VisibleContentItem("verse_9", 0, 40),
            VisibleContentItem("verse_10", 40, 30),
            VisibleContentItem("verse_11", 70, 830),
        )
        val anchor = JumpSaveAnchor(verseId = 9, firstVisibleItemIndex = 8, firstVisibleItemScrollOffset = 0)
        assertThat(visibleVerseToSave(visible, 0, 1000)).isEqualTo(11)
        assertThat(verseToSaveAfterJump(visible, 0, 1000, 8, 0, anchor)).isEqualTo(9)
        assertThat(verseToSaveAfterJump(visible, 0, 1000, 8, 1, anchor)).isEqualTo(11)
        assertThat(verseToSaveAfterJump(visible, 0, 1000, 9, 0, anchor)).isEqualTo(11)
        assertThat(verseToSaveAfterJump(visible, 0, 1000, 8, 0, null)).isEqualTo(11)
    }

    @Test fun jumpAnchorNeverSavesOffscreenVerseOrNativeAd() {
        val anchor = JumpSaveAnchor(verseId = 9, firstVisibleItemIndex = 8, firstVisibleItemScrollOffset = 0)
        val visible = listOf(
            VisibleContentItem("verse_9", 1300, 100),
            VisibleContentItem("native_ad_9", 0, 300),
            VisibleContentItem("verse_11", 300, 500),
        )
        assertThat(verseToSaveAfterJump(visible, 0, 800, 8, 0, anchor)).isEqualTo(11)
        assertThat(verseToSaveAfterJump(visible.filter { it.key != "verse_11" }, 0, 800, 8, 0, anchor)).isNull()
    }

    @Test fun outsideViewportAndAdOnlyCannotAdvanceAnySavedPlace() {
        val prefetch = listOf(VisibleContentItem("verse_99", 1300, 600))
        assertThat(visibleVerseToSave(prefetch, 0, 1000)).isNull()
        assertThat(
            visibleVerseToSave(
                listOf(VisibleContentItem("native_ad_9", 0, 1000)), 0, 1000
            )
        ).isNull()
        assertThat(visibleVerseToSave(emptyList(), 0, 1000)).isNull()
        assertThat(
            visibleVerseToSave(
                listOf(VisibleContentItem("verse_5", 0, 1000)), 500, 500
            )
        ).isNull()
    }

    @Test fun savedCanonicalIdStillMapsWithAdSlotsAndIdGaps() {
        val list = (1..40).map { Verse(it * 2, "", "", "") }
        assertThat(contentLazyItemIndexForVerse(list, 2, true)).isEqualTo(0)
        assertThat(contentLazyItemIndexForVerse(list, 80, true)).isEqualTo(42)
        assertThat(contentLazyItemIndexForVerse(list, 41, true)).isNull()
    }
}
