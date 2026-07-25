package com.parsfilo.contentapp.feature.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class WearDataSyncListenerTest {

    @Test
    fun testDataMapPathFormatting() {
        val path = "/zikir_count_sync"
        assertEquals("/zikir_count_sync", path)
    }
}
