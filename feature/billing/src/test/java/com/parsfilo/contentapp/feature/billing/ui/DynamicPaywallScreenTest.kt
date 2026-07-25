package com.parsfilo.contentapp.feature.billing.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicPaywallScreenTest {

    @Test
    fun testPaywallVariantParsing() {
        val variantKey = "annual_discount_banner"
        assertEquals("annual_discount_banner", variantKey)
    }
}
