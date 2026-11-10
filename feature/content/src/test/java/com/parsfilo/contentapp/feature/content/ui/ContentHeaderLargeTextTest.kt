package com.parsfilo.contentapp.feature.content.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentHeaderLargeTextTest {
    @Test
    fun normalSystemTextKeepsCompactSingleRowHeader() {
        assertFalse(shouldStackContentHeaderTitle(1.0f))
        assertFalse(shouldStackContentHeaderTitle(1.29f))
    }

    @Test
    fun accessibilityTextPlacesFullTitleBelowActions() {
        assertTrue(shouldStackContentHeaderTitle(1.3f))
        assertTrue(shouldStackContentHeaderTitle(2.0f))
    }
}
