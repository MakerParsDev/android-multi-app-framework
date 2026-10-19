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

    @Test
    fun `EEA refusal has denied analytics status and counts as resolved decision`() {
        assertThat(ConsentStatus.Denied.analyticsValue()).isEqualTo("denied")
        assertThat(ConsentStatus.Denied.isSuccessfulResolution()).isTrue()
        assertThat(ConsentStatus.Obtained.analyticsValue()).isEqualTo("granted")
        assertThat(ConsentStatus.Obtained.isSuccessfulResolution()).isTrue()
    }

    @Test
    fun `UMP preference reader handles numeric GDPR flags and string TCF bits`() {
        val prefs = io.mockk.mockk<android.content.SharedPreferences>()
        io.mockk.every { prefs.all } returns mapOf(
            "IABTCF_gdprApplies" to 1,
            "IABTCF_PurposeConsents" to "00000000000",
            "test_boolean" to true,
        )

        assertThat(readConsentPreferenceValue(prefs, "IABTCF_gdprApplies")).isEqualTo("1")
        assertThat(readConsentPreferenceValue(prefs, "IABTCF_PurposeConsents")).isEqualTo("00000000000")
        assertThat(readConsentPreferenceValue(prefs, "test_boolean")).isEqualTo("true")
        assertThat(readConsentPreferenceValue(prefs, "absent")).isNull()
    }

    @Test
    fun `EEA all-zero TCF purpose choice is explicit refusal despite ad eligibility`() {
        val signals = ConsentSignalSnapshot(
            tcfPurposeConsents = "00000000000",
            tcfVendorConsents = null,
            gdprApplies = true,
            usPrivacyString = null,
            gppString = null,
        )
        assertThat(isExplicitEeaConsentDenied(signals)).isTrue()
        // Consent Mode and ad eligibility are deliberately independent.
        assertThat(mapToFirebaseConsentGrantedFlags(canRequestAds = true, signals = signals).adStorageGranted)
            .isFalse()
    }

    @Test
    fun `EEA refusal classification excludes granted and non-EEA choices`() {
        fun signals(purposes: String?, gdpr: Boolean?) = ConsentSignalSnapshot(
            tcfPurposeConsents = purposes,
            tcfVendorConsents = null,
            gdprApplies = gdpr,
            usPrivacyString = null,
            gppString = null,
        )
        assertThat(isExplicitEeaConsentDenied(signals("10000000000", true))).isFalse()
        assertThat(isExplicitEeaConsentDenied(signals(null, true))).isFalse()
        assertThat(isExplicitEeaConsentDenied(signals("00000000000", false))).isFalse()
        assertThat(isExplicitEeaConsentDenied(signals("invalid", true))).isFalse()
    }

    @Test
    fun `refresh uses current EEA consent even when UMP allows limited ads`() {
        val rejected = ConsentSignalSnapshot(
            tcfPurposeConsents = "00000000000",
            tcfVendorConsents = null,
            gdprApplies = true,
            usPrivacyString = null,
            gppString = null,
        )
        val accepted = rejected.copy(tcfPurposeConsents = "10000000000")
        val malformed = accepted.copy(tcfPurposeConsents = "invalid")
        val missingSignals = rejected.copy(tcfPurposeConsents = null, gdprApplies = null)
        val otherRegion = rejected.copy(gdprApplies = false)

        assertThat(resolveRefreshedConsentStatus(true, ConsentStatus.Obtained, rejected))
            .isEqualTo(ConsentStatus.Denied)
        assertThat(resolveRefreshedConsentStatus(true, ConsentStatus.Denied, accepted))
            .isEqualTo(ConsentStatus.Obtained)
        assertThat(resolveRefreshedConsentStatus(true, ConsentStatus.NotRequired, accepted))
            .isEqualTo(ConsentStatus.Obtained)
        assertThat(isEeaConsentObtained(accepted)).isTrue()
        assertThat(isEeaConsentObtained(malformed)).isFalse()
        assertThat(resolveRefreshedConsentStatus(true, ConsentStatus.NotRequired, malformed))
            .isEqualTo(ConsentStatus.NotRequired)
        assertThat(resolveRefreshedConsentStatus(true, ConsentStatus.Denied, malformed))
            .isEqualTo(ConsentStatus.Denied)
        assertThat(resolveRefreshedConsentStatus(true, ConsentStatus.Denied, missingSignals))
            .isEqualTo(ConsentStatus.Denied)
        assertThat(resolveRefreshedConsentStatus(true, ConsentStatus.Denied, otherRegion))
            .isEqualTo(ConsentStatus.Obtained)
        assertThat(resolveRefreshedConsentStatus(true, ConsentStatus.NotRequired, otherRegion))
            .isEqualTo(ConsentStatus.NotRequired)
        assertThat(resolveRefreshedConsentStatus(false, ConsentStatus.Obtained, accepted))
            .isEqualTo(ConsentStatus.Denied)
        assertThat(resolveRefreshedConsentStatus(true, null, accepted))
            .isEqualTo(ConsentStatus.Obtained)
    }
}
