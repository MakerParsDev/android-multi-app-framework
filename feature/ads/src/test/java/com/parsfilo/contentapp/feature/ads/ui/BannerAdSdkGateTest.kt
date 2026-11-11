package com.parsfilo.contentapp.feature.ads.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.ads.AdAgeGateStatus
import com.parsfilo.contentapp.feature.ads.AdSuppressReason
import com.parsfilo.contentapp.feature.ads.AdsPrivacyState
import com.parsfilo.contentapp.feature.ads.ConsentStatus
import org.junit.Test

class BannerAdSdkGateTest {
    private val consented = AdsPrivacyState.CanRequestAds(
        consentStatus = ConsentStatus.NotRequired,
        privacyOptionsRequired = false,
        ageGateStatus = AdAgeGateStatus.UNKNOWN,
    )

    @Test
    fun consentedBannerWaitsForSdkInitializationBeforeFirstLoad() {
        assertThat(bannerSuppressionReason(true, consented, true, true, false))
            .isEqualTo(AdSuppressReason.SDK_NOT_READY)
        assertThat(bannerSuppressionReason(true, consented, true, true, true)).isNull()
    }

    @Test
    fun sdkReadinessNeverOverridesExistingConsentOrEntitlementRestrictions() {
        val gathering = AdsPrivacyState.Gathering(AdAgeGateStatus.UNKNOWN)
        assertThat(bannerSuppressionReason(false, gathering, true, true, false))
            .isEqualTo(AdSuppressReason.CONSENT_PENDING)
        assertThat(bannerSuppressionReason(true, consented, false, true, false))
            .isEqualTo(AdSuppressReason.PLACEMENT_DISABLED)
        assertThat(bannerSuppressionReason(true, consented, true, false, false))
            .isEqualTo(AdSuppressReason.AD_GATE)
    }
}
