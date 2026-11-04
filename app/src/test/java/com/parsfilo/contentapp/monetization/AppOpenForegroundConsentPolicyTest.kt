package com.parsfilo.contentapp.monetization

import com.parsfilo.contentapp.feature.ads.AppOpenTriggerReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenForegroundConsentPolicyTest {
    @Test
    fun coldStartDoesNotTriggerSecondUmpRefresh() {
        assertFalse(shouldRefreshAppOpenConsent(AppOpenTriggerReason.COLD_START))
    }

    @Test
    fun resumeStillRefreshesUmpBeforeAppOpen() {
        assertTrue(shouldRefreshAppOpenConsent(AppOpenTriggerReason.RESUME))
    }
}
