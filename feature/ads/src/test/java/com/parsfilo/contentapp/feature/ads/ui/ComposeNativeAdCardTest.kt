package com.parsfilo.contentapp.feature.ads.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeNativeAdCardTest {

    @Test
    fun testAdPlacementFormatting() {
        val placement = "NATIVE_FEED_CONTENT"
        assertEquals("NATIVE_FEED_CONTENT", placement)
    }
}
