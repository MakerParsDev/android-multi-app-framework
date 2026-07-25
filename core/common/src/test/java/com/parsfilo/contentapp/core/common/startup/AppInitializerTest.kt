package com.parsfilo.contentapp.core.common.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class AppInitializerTest {

    @Test
    fun testInitializerOrder() {
        val order = listOf("Timber", "Firebase", "WorkManager", "Ads")
        assertEquals(4, order.size)
        assertEquals("Timber", order.first())
    }
}
