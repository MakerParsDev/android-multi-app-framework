package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class AdsConsentRuntimeStateTest {

    @After
    fun tearDown() {
        AdsConsentRuntimeState.update(AdsPrivacyState.Unknown)
    }

    @Test
    fun `obtained consent allows ad requests`() {
        AdsConsentRuntimeState.update(
            allowedState(consentStatus = ConsentStatus.Obtained, privacyOptionsRequired = true),
        )

        assertThat(AdsConsentRuntimeState.canRequestAds.value).isTrue()
        assertThat(AdsConsentRuntimeState.state.value.canRequestAds()).isTrue()
    }

    @Test
    fun `not required consent allows ad requests`() {
        AdsConsentRuntimeState.update(
            allowedState(consentStatus = ConsentStatus.NotRequired, privacyOptionsRequired = false),
        )

        assertThat(AdsConsentRuntimeState.canRequestAds.value).isTrue()
        assertThat(AdsConsentRuntimeState.state.value.canRequestAds()).isTrue()
    }

    @Test
    fun `required consent blocks ad requests`() {
        AdsConsentRuntimeState.update(blockedState(ConsentStatus.Required))

        assertThat(AdsConsentRuntimeState.canRequestAds.value).isFalse()
        assertThat(AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked())
            .isEqualTo(AdSuppressReason.NO_CONSENT)
    }

    @Test
    fun `unknown consent blocks ad requests`() {
        AdsConsentRuntimeState.update(AdsPrivacyState.Unknown)

        assertThat(AdsConsentRuntimeState.canRequestAds.value).isFalse()
        assertThat(AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked())
            .isEqualTo(AdSuppressReason.NO_CONSENT)
    }

    @Test
    fun `update failure without usable previous consent blocks ad requests`() {
        AdsConsentRuntimeState.update(
            blockedState(
                ConsentStatus.Error(
                    message = "consent update failed",
                    retryEligibleAtMillis = 0L,
                ),
            ),
        )

        assertThat(AdsConsentRuntimeState.canRequestAds.value).isFalse()
        assertThat(AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked(nowMillis = 1L))
            .isEqualTo(AdSuppressReason.CONSENT_ERROR)
    }

    @Test
    fun `retry backoff error reports retry suppression reason`() {
        AdsConsentRuntimeState.update(
            blockedState(
                ConsentStatus.Error(
                    message = "temporary network error",
                    retryEligibleAtMillis = 10_000L,
                ),
            ),
        )

        assertThat(AdsConsentRuntimeState.canRequestAds.value).isFalse()
        assertThat(AdsConsentRuntimeState.state.value.suppressReasonWhenBlocked(nowMillis = 1_000L))
            .isEqualTo(AdSuppressReason.CONSENT_RETRY_BACKOFF)
    }

    private fun allowedState(
        consentStatus: ConsentStatus,
        privacyOptionsRequired: Boolean,
    ): AdsPrivacyState =
        AdsPrivacyState.CanRequestAds(
            consentStatus = consentStatus,
            privacyOptionsRequired = privacyOptionsRequired,
            ageGateStatus = AdAgeGateStatus.AGE_16_OR_OVER,
        )

    private fun blockedState(consentStatus: ConsentStatus): AdsPrivacyState =
        AdsPrivacyState.DeniedOrLimited(
            consentStatus = consentStatus,
            privacyOptionsRequired = false,
            ageGateStatus = AdAgeGateStatus.AGE_16_OR_OVER,
        )
}
