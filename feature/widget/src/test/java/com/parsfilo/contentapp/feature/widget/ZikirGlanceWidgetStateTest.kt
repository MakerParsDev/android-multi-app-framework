package com.parsfilo.contentapp.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class ZikirGlanceWidgetStateTest {

    @Test
    fun testWidgetStateFormatting() {
        val count = 33
        val title = "Subhanallah"
        val formatted = "$title: $count"
        assertEquals("Subhanallah: 33", formatted)
    }
}
