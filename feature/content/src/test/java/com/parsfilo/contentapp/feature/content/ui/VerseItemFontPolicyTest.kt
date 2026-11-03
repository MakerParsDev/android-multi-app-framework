package com.parsfilo.contentapp.feature.content.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.model.DisplayMode
import org.junit.Test

class VerseItemFontPolicyTest {
    @Test
    fun qifMarkUsesBundledFontInArabicMode() {
        assertThat(requiresExtendedQifFont(DisplayMode.ARABIC, "رُسُلِهِ\u08DE")).isTrue()
    }

    @Test
    fun otherModesAndVersesKeepExistingTypography() {
        assertThat(requiresExtendedQifFont(DisplayMode.ARABIC, "بِسْمِ اللَّهِ")).isFalse()
        assertThat(requiresExtendedQifFont(DisplayMode.LATIN, "\u08DE")).isFalse()
        assertThat(requiresExtendedQifFont(DisplayMode.TURKISH, "\u08DE")).isFalse()
    }
}
