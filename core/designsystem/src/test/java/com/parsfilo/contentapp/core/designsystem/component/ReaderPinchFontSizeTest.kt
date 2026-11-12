package com.parsfilo.contentapp.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPinchFontSizeTest {
    @Test fun twoFingerScalePreservesNaturalFontSizeWithoutEnlargingWholeCard() {
        assertEquals(35, scaledReaderFontSize(initialSize = 28, zoomFactor = 1.25f, minSize = 22, maxSize = 42))
        assertEquals(24, scaledReaderFontSize(initialSize = 30, zoomFactor = 0.8f, minSize = 22, maxSize = 42))
    }

    @Test fun scaleLimitsAreSafeAndDoNotShrinkBelowLegibleFont() {
        assertEquals(22, scaledReaderFontSize(initialSize = 28, zoomFactor = 0.01f, minSize = 22, maxSize = 42))
        assertEquals(42, scaledReaderFontSize(initialSize = 28, zoomFactor = 50f, minSize = 22, maxSize = 42))
    }

    @Test fun invalidScaleIsIgnored() {
        assertEquals(28, scaledReaderFontSize(initialSize = 28, zoomFactor = Float.NaN, minSize = 22, maxSize = 42))
        assertEquals(28, scaledReaderFontSize(initialSize = 28, zoomFactor = -1f, minSize = 22, maxSize = 42))
    }

    @Test fun integerRoundedPreviewOnlyAfterRealZoom() {
        assertEquals(28, scaledReaderFontSize(initialSize = 28, zoomFactor = 1.01f, minSize = 22, maxSize = 42))
        assertEquals(29, scaledReaderFontSize(initialSize = 28, zoomFactor = 1.035f, minSize = 22, maxSize = 42))
    }
}
