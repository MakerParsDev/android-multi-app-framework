package com.parsfilo.contentapp.feature.quran.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayModeSelectorLayoutPolicyTest {
    @Test
    fun narrowPhoneAtLargeFontUsesReadableFullWidthButtons() {
        assertTrue(shouldStackDisplayModeButtons(fontScale = 2.0f, availableWidthDp = 411f))
        assertTrue(shouldStackDisplayModeButtons(fontScale = 1.3f, availableWidthDp = 599f))
    }

    @Test
    fun wideLandscapeAtLargeFontKeepsReadingViewportOpen() {
        assertFalse(shouldStackDisplayModeButtons(fontScale = 2.0f, availableWidthDp = 731f))
        assertFalse(shouldStackDisplayModeButtons(fontScale = 1.3f, availableWidthDp = 600f))
    }

    @Test
    fun ordinaryFontScaleUsesFlowLayoutEvenOnNarrowScreens() {
        assertFalse(shouldStackDisplayModeButtons(fontScale = 1.0f, availableWidthDp = 320f))
        assertFalse(shouldStackDisplayModeButtons(fontScale = 1.29f, availableWidthDp = 411f))
    }
}
