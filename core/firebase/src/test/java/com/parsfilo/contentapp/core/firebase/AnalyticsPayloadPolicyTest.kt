package com.parsfilo.contentapp.core.firebase

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.google.firebase.analytics.FirebaseAnalytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnalyticsPayloadPolicyTest {
    @Test
    fun sanitizerDropsFreeTextAndForbiddenKeys() {
        val sanitized =
            sanitizeAnalyticsBundle(
                Bundle().apply {
                    putString(AnalyticsParamKey.ERROR_MESSAGE, "raw server response")
                    putString("email", "person@example.com")
                    putString(AnalyticsParamKey.ERROR, "HTTP_500")
                },
            )

        assertThat(sanitized).isNotNull()
        assertThat(sanitized!!.containsKey(AnalyticsParamKey.ERROR_MESSAGE)).isFalse()
        assertThat(sanitized.containsKey("email")).isFalse()
        assertThat(sanitized.getString(AnalyticsParamKey.ERROR)).isEqualTo("HTTP_500")
    }

    @Test
    fun parameterPolicyRejectsForbiddenTokensAtEveryBoundary() {
        assertThat(isAnalyticsParameterAllowed("email_hash")).isFalse()
        assertThat(isAnalyticsParameterAllowed("customer_email_hash")).isFalse()
        assertThat(isAnalyticsParameterAllowed("hashed_device_id")).isFalse()
        assertThat(isAnalyticsParameterAllowed("user_tenure_days")).isTrue()
    }

    @Test
    fun sanitizerRedactsSensitiveValuesAndNormalizesText() {
        val sanitized =
            sanitizeAnalyticsBundle(
                Bundle().apply {
                    putString(AnalyticsParamKey.REASON, "  contact person@example.com now  ")
                    putString(AnalyticsParamKey.ROUTE, "  content   detail  ")
                },
            )

        assertThat(sanitized!!.getString(AnalyticsParamKey.REASON)).isEqualTo("[redacted]")
        assertThat(sanitized.getString(AnalyticsParamKey.ROUTE)).isEqualTo("content detail")
    }

    @Test
    fun sanitizerPreservesGovernedStructuredIdentifiersAndDates() {
        val adUnitId = "ca-app-pub-3940256099942544/6300978111"
        val sanitized =
            sanitizeAnalyticsBundle(
                Bundle().apply {
                    putString(AnalyticsParamKey.AD_UNIT_ID, adUnitId)
                    putString(AnalyticsParamKey.CONTENT_ID, "verse:1234567890")
                    putString(AnalyticsParamKey.LOCAL_DATE, "2026-07-12")
                },
            )

        assertThat(sanitized!!.getString(AnalyticsParamKey.AD_UNIT_ID)).isEqualTo(adUnitId)
        assertThat(sanitized.getString(AnalyticsParamKey.CONTENT_ID)).isEqualTo("verse:1234567890")
        assertThat(sanitized.getString(AnalyticsParamKey.LOCAL_DATE)).isEqualTo("2026-07-12")
    }

    @Test
    fun sanitizerNeverLetsStructuredKeysBypassSensitiveValueDetection() {
        val emailLikeValue = "person" + 64.toChar() + "example.com"
        val phoneLikeValue = listOf("90555", "1234567").joinToString(separator = "")
        val sanitized =
            sanitizeAnalyticsBundle(
                Bundle().apply {
                    putString(AnalyticsParamKey.CONTENT_ID, emailLikeValue)
                    putString(AnalyticsParamKey.PRODUCT_ID, phoneLikeValue)
                },
            )

        assertThat(sanitized!!.getString(AnalyticsParamKey.CONTENT_ID)).isEqualTo("[redacted]")
        assertThat(sanitized.getString(AnalyticsParamKey.PRODUCT_ID)).isEqualTo("[redacted]")
    }

    @Test
    fun sanitizerNormalizesSupportedNumericTypes() {
        val sanitized =
            sanitizeAnalyticsBundle(
                Bundle().apply {
                    putInt(AnalyticsParamKey.ERROR_CODE, 500)
                    putFloat(AnalyticsParamKey.AD_VALUE, 1.25f)
                    putBoolean(AnalyticsParamKey.CAN_REQUEST_ADS, true)
                },
            )

        assertThat(sanitized!!.getLong(AnalyticsParamKey.ERROR_CODE)).isEqualTo(500L)
        assertThat(sanitized.getDouble(AnalyticsParamKey.AD_VALUE)).isWithin(0.001).of(1.25)
        assertThat(sanitized.getLong(AnalyticsParamKey.CAN_REQUEST_ADS)).isEqualTo(1L)
    }

    @Test
    fun defaultContextUpdatesPreservePreviouslyConfiguredValues() {
        val firebaseAnalytics = mockk<FirebaseAnalytics>(relaxed = true)
        val published = mutableListOf<Bundle>()
        every { firebaseAnalytics.setDefaultEventParameters(any()) } answers {
            published += Bundle(firstArg<Bundle>())
        }
        val appAnalytics = AppAnalytics(firebaseAnalytics)

        appAnalytics.setDefaultEventParameters(
            Bundle().apply {
                putString(AnalyticsParamKey.FLAVOR_ID, "amenerrasulu")
                putString(AnalyticsParamKey.APP_VERSION, "1.2.3")
            },
        )
        appAnalytics.setDefaultEventParameter(AnalyticsParamKey.CONSENT_STATE, "granted")

        val finalBundle = published.last()
        assertThat(finalBundle.getString(AnalyticsParamKey.FLAVOR_ID)).isEqualTo("amenerrasulu")
        assertThat(finalBundle.getString(AnalyticsParamKey.APP_VERSION)).isEqualTo("1.2.3")
        assertThat(finalBundle.getString(AnalyticsParamKey.CONSENT_STATE)).isEqualTo("granted")
    }

    @Test
    fun logEventNeverForwardsFreeTextErrorMessage() {
        val firebaseAnalytics = mockk<FirebaseAnalytics>(relaxed = true)
        val forwarded = mutableListOf<Bundle>()
        every { firebaseAnalytics.logEvent(any(), any<Bundle>()) } answers {
            forwarded += secondArg<Bundle>()
        }
        val appAnalytics = AppAnalytics(firebaseAnalytics)

        appAnalytics.logEvent(
            AnalyticsEventName.BILLING_ERROR,
            Bundle().apply {
                putString(AnalyticsParamKey.ERROR, "SERVICE_UNAVAILABLE")
                putString(AnalyticsParamKey.ERROR_MESSAGE, "user@example.com token=secret")
            },
        )

        val payload = forwarded.single()
        assertThat(payload.getString(AnalyticsParamKey.ERROR)).isEqualTo("SERVICE_UNAVAILABLE")
        assertThat(payload.containsKey(AnalyticsParamKey.ERROR_MESSAGE)).isFalse()
    }

    @Test
    fun userIdRequiresOpaqueAnonymousPrefix() {
        val firebaseAnalytics = mockk<FirebaseAnalytics>(relaxed = true)
        val appAnalytics = AppAnalytics(firebaseAnalytics)

        assertThrows(IllegalArgumentException::class.java) {
            appAnalytics.setUserId("person@example.com")
        }
        appAnalytics.setUserId("anon_0123456789abcdef")

        verify(exactly = 1) { firebaseAnalytics.setUserId("anon_0123456789abcdef") }
    }
}
