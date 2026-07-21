package com.parsfilo.contentapp.core.firebase.config

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointsProviderTest {

    @Test
    fun `returns default endpoint when remote value is null`() {
        assertEquals("https://default", resolveEndpointValue(null, "https://default"))
    }

    @Test
    fun `returns default endpoint when remote value is blank`() {
        assertEquals("https://default", resolveEndpointValue("   ", "https://default"))
    }

    @Test
    fun `returns remote endpoint when value is present`() {
        assertEquals("https://remote", resolveEndpointValue("https://remote", "https://default"))
    }
}
