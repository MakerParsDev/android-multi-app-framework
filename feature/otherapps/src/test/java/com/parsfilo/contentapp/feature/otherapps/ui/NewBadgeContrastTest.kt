package com.parsfilo.contentapp.feature.otherapps.ui

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class NewBadgeContrastTest {
    @Test
    fun newBadgeTextMeetsAaSmallTextContrast() {
        val foreground = NewBadgeColors.foreground.luminance()
        val background = NewBadgeColors.background.luminance()
        val contrast = (maxOf(foreground, background) + 0.05f) /
            (minOf(foreground, background) + 0.05f)

        assertTrue("New-app badge text requires at least 4.5:1 contrast", contrast >= 4.5f)
    }
}
