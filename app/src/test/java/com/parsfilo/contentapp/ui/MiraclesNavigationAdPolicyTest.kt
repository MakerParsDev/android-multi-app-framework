package com.parsfilo.contentapp.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiraclesNavigationAdPolicyTest {
    @Test
    fun `first detail always opens without an interstitial`() {
        assertFalse(shouldRequestMiraclesInterstitial(hasOpenedDetail = false, hasHostActivity = true))
        assertFalse(shouldRequestMiraclesInterstitial(hasOpenedDetail = false, hasHostActivity = false))
    }

    @Test
    fun `later detail navigation remains eligible when host is available`() {
        assertTrue(shouldRequestMiraclesInterstitial(hasOpenedDetail = true, hasHostActivity = true))
        assertFalse(shouldRequestMiraclesInterstitial(hasOpenedDetail = true, hasHostActivity = false))
    }
}
