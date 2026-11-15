package com.parsfilo.contentapp.monetization

import com.parsfilo.contentapp.feature.ads.AdAgeGateStatus
import com.parsfilo.contentapp.feature.ads.AdsPrivacyState
import com.parsfilo.contentapp.feature.ads.AppOpenTriggerReason
import com.parsfilo.contentapp.feature.ads.ConsentStatus
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

    @Test
    fun resumeWaitsForTemporaryUmpFailureBackoffButRetriesAtItsDeadline() {
        val limited =
            AdsPrivacyState.DeniedOrLimited(
                consentStatus = ConsentStatus.Error("network", retryEligibleAtMillis = 10_000L),
                privacyOptionsRequired = false,
                ageGateStatus = AdAgeGateStatus.UNKNOWN,
            )
        assertFalse(shouldRefreshAppOpenConsent(AppOpenTriggerReason.RESUME, limited, nowMillis = 9_999L))
        assertTrue(shouldRefreshAppOpenConsent(AppOpenTriggerReason.RESUME, limited, nowMillis = 10_000L))
        assertTrue(shouldRefreshAppOpenConsent(AppOpenTriggerReason.RESUME, limited, nowMillis = 10_001L))
    }

    @Test
    fun resumeStillRefreshesWhenConsentIsMissingOrUserDenied() {
        val missing =
            AdsPrivacyState.DeniedOrLimited(
                consentStatus = ConsentStatus.Missing,
                privacyOptionsRequired = false,
                ageGateStatus = AdAgeGateStatus.UNKNOWN,
            )
        val denied = missing.copy(consentStatus = ConsentStatus.Denied)
        assertTrue(shouldRefreshAppOpenConsent(AppOpenTriggerReason.RESUME, missing, nowMillis = 0L))
        assertTrue(shouldRefreshAppOpenConsent(AppOpenTriggerReason.RESUME, denied, nowMillis = 0L))
    }

    @Test
    fun resumeDoesNotSuppressRefreshWhenUmpStillAllowsAds() {
        val eligible =
            AdsPrivacyState.CanRequestAds(
                consentStatus = ConsentStatus.Error("network", retryEligibleAtMillis = 10_000L),
                privacyOptionsRequired = false,
                ageGateStatus = AdAgeGateStatus.UNKNOWN,
            )
        assertTrue(shouldRefreshAppOpenConsent(AppOpenTriggerReason.RESUME, eligible, nowMillis = 0L))
        assertFalse(shouldRefreshAppOpenConsent(AppOpenTriggerReason.COLD_START, eligible, nowMillis = 10_001L))
    }
}
