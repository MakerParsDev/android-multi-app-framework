package com.parsfilo.contentapp.feature.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BillingVerificationResponseParserTest {
    @Test
    fun `decoded response maps all verification fields`() {
        val result =
            parseBillingVerificationResponse("body") {
                BillingVerificationFields(
                    verified = true,
                    expiryTimeMillis = 1_770_000_000_000L,
                    autoRenewing = true,
                    purchaseState = "PURCHASED",
                    acknowledgementState = "ACKNOWLEDGED",
                )
            }

        assertThat(result.verified).isTrue()
        assertThat(result.expiryTimeMillis).isEqualTo(1_770_000_000_000L)
        assertThat(result.isAutoRenewing).isTrue()
        assertThat(result.purchaseState).isEqualTo("PURCHASED")
        assertThat(result.acknowledgementState).isEqualTo("ACKNOWLEDGED")
        assertThat(result.error).isNull()
    }

    @Test
    fun `missing optional fields use safe defaults`() {
        val result = parseBillingVerificationResponse("body") { BillingVerificationFields() }

        assertThat(result.verified).isFalse()
        assertThat(result.expiryTimeMillis).isNull()
        assertThat(result.isAutoRenewing).isFalse()
        assertThat(result.purchaseState).isEqualTo("UNKNOWN")
        assertThat(result.acknowledgementState).isEqualTo("UNKNOWN")
        assertThat(result.error).isNull()
    }

    @Test
    fun `non-positive expiry is treated as absent`() {
        val result =
            parseBillingVerificationResponse("body") {
                BillingVerificationFields(expiryTimeMillis = 0L)
            }

        assertThat(result.expiryTimeMillis).isNull()
    }

    @Test
    fun `decoder failure returns parse error without throwing`() {
        val result =
            parseBillingVerificationResponse("body") {
                error("malformed response")
            }

        assertThat(result.verified).isFalse()
        assertThat(result.expiryTimeMillis).isNull()
        assertThat(result.isAutoRenewing).isFalse()
        assertThat(result.purchaseState).isEqualTo("PARSE_ERROR")
        assertThat(result.acknowledgementState).isEqualTo("UNKNOWN")
        assertThat(result.error).isEqualTo("Verification response parse failed")
    }
}
