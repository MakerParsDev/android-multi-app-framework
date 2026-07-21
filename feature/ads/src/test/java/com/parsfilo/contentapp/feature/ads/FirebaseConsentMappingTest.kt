package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FirebaseConsentMappingTest {
    @Test
    fun `advanced consent mode keeps analytics collection enabled when storage is denied`() {
        val result = resolveFirebaseConsentModeState(
            canRequestAds = false,
            signals = null,
        )

        assertThat(result.analyticsCollectionEnabled).isTrue()
        assertThat(result.flags.analyticsStorageGranted).isFalse()
        assertThat(result.flags.adStorageGranted).isFalse()
    }

    @Test
    fun `advanced consent mode keeps collection enabled for granular tcf denial`() {
        val result = resolveFirebaseConsentModeState(
            canRequestAds = true,
            signals = ConsentSignalSnapshot(
                // Purpose 1 denied while ad requests remain available under the CMP decision.
                tcfPurposeConsents = "0000000",
                tcfVendorConsents = null,
                gdprApplies = true,
                usPrivacyString = null,
                gppString = null,
            ),
        )

        assertThat(result.analyticsCollectionEnabled).isTrue()
        assertThat(result.flags.analyticsStorageGranted).isFalse()
        assertThat(result.flags.adStorageGranted).isFalse()
    }

    @Test
    fun `granted maps all firebase consent flags to true`() {
        val result = mapToFirebaseConsentGrantedFlags(granted = true)

        assertThat(result.adStorageGranted).isTrue()
        assertThat(result.analyticsStorageGranted).isTrue()
        assertThat(result.adUserDataGranted).isTrue()
        assertThat(result.adPersonalizationGranted).isTrue()
    }

    @Test
    fun `denied maps all firebase consent flags to false`() {
        val result = mapToFirebaseConsentGrantedFlags(granted = false)

        assertThat(result.adStorageGranted).isFalse()
        assertThat(result.analyticsStorageGranted).isFalse()
        assertThat(result.adUserDataGranted).isFalse()
        assertThat(result.adPersonalizationGranted).isFalse()
    }

    @Test
    fun `tcf purpose consents map granular flags`() {
        val result = mapToFirebaseConsentGrantedFlags(
            canRequestAds = true,
            signals = ConsentSignalSnapshot(
                // p1=1, p3=0, p4=1, p7=1
                tcfPurposeConsents = "1001001",
                tcfVendorConsents = null,
                gdprApplies = true,
                usPrivacyString = null,
                gppString = null,
            ),
        )

        assertThat(result.adStorageGranted).isTrue()
        assertThat(result.analyticsStorageGranted).isTrue()
        assertThat(result.adUserDataGranted).isTrue()
        assertThat(result.adPersonalizationGranted).isFalse()
    }

    @Test
    fun `us privacy opt out disables user data and personalization`() {
        val result = mapToFirebaseConsentGrantedFlags(
            canRequestAds = true,
            signals = ConsentSignalSnapshot(
                tcfPurposeConsents = null,
                tcfVendorConsents = null,
                gdprApplies = null,
                usPrivacyString = "1YYN",
                gppString = null,
            ),
        )

        assertThat(result.adStorageGranted).isTrue()
        assertThat(result.analyticsStorageGranted).isTrue()
        assertThat(result.adUserDataGranted).isFalse()
        assertThat(result.adPersonalizationGranted).isFalse()
    }
}
