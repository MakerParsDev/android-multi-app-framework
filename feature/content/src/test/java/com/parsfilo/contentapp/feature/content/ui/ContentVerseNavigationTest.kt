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
}
