package com.parsfilo.contentapp.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiraclesNavigationAdPolicyTest {
    @Test
    fun `first detail always opens without an interstitial`() {
        assertFalse(shouldRequestFirstDetailInterstitial(hasOpenedDetail = false, hasHostActivity = true))
        assertFalse(shouldRequestFirstDetailInterstitial(hasOpenedDetail = false, hasHostActivity = false))
    }

    @Test
    fun `later detail navigation remains eligible when host is available`() {
        assertTrue(shouldRequestFirstDetailInterstitial(hasOpenedDetail = true, hasHostActivity = true))
        assertFalse(shouldRequestFirstDetailInterstitial(hasOpenedDetail = true, hasHostActivity = false))
    }
}
