package com.parsfilo.contentapp.feature.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BillingVerificationJsonDecoderTest {
    @Test
    fun `real json decoder maps backend response fields`() {
        val result =
            parseBillingVerificationResponse(
                """
                {
                  "verified": true,
                  "expiryTimeMillis": 1770000000000,
                  "autoRenewing": true,
                  "purchaseState": "PURCHASED",
                  "acknowledgementState": "ACKNOWLEDGED"
                }
                """.trimIndent(),
            )

        assertThat(result.verified).isTrue()
        assertThat(result.expiryTimeMillis).isEqualTo(1_770_000_000_000L)
        assertThat(result.isAutoRenewing).isTrue()
        assertThat(result.purchaseState).isEqualTo("PURCHASED")
        assertThat(result.acknowledgementState).isEqualTo("ACKNOWLEDGED")
        assertThat(result.error).isNull()
    }

    @Test
    fun `real json decoder uses safe defaults for missing fields`() {
        val result = parseBillingVerificationResponse("{\"verified\":false}")

        assertThat(result.verified).isFalse()
        assertThat(result.expiryTimeMillis).isNull()
        assertThat(result.isAutoRenewing).isFalse()
        assertThat(result.purchaseState).isEqualTo("UNKNOWN")
        assertThat(result.acknowledgementState).isEqualTo("UNKNOWN")
        assertThat(result.error).isNull()
    }

    @Test
    fun `real json decoder returns parse error for malformed response`() {
        val result = parseBillingVerificationResponse("not-json")

        assertThat(result.verified).isFalse()
        assertThat(result.expiryTimeMillis).isNull()
        assertThat(result.isAutoRenewing).isFalse()
        assertThat(result.purchaseState).isEqualTo("PARSE_ERROR")
        assertThat(result.acknowledgementState).isEqualTo("UNKNOWN")
        assertThat(result.error).isEqualTo("Verification response parse failed")
    }
}
