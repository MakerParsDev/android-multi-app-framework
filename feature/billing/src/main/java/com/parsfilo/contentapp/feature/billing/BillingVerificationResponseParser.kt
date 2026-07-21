package com.parsfilo.contentapp.feature.billing

import org.json.JSONObject
import timber.log.Timber

internal data class BillingVerificationFields(
    val verified: Boolean = false,
    val expiryTimeMillis: Long = 0L,
    val autoRenewing: Boolean = false,
    val purchaseState: String = "UNKNOWN",
    val acknowledgementState: String = "UNKNOWN",
)

internal fun parseBillingVerificationResponse(
    body: String,
    decoder: (String) -> BillingVerificationFields = ::decodeBillingVerificationFields,
): VerificationResult =
    runCatching {
        decoder(body).toVerificationResult()
    }.getOrElse { throwable ->
        Timber.w(throwable, "Billing purchase verification response parse failed")
        parseErrorResult()
    }

private fun decodeBillingVerificationFields(body: String): BillingVerificationFields {
    val json = JSONObject(body)
    return BillingVerificationFields(
        verified = json.optBoolean("verified", false),
        expiryTimeMillis = json.optLong("expiryTimeMillis", 0L),
        autoRenewing = json.optBoolean("autoRenewing", false),
        purchaseState = json.optString("purchaseState", "UNKNOWN"),
        acknowledgementState = json.optString("acknowledgementState", "UNKNOWN"),
    )
}

private fun BillingVerificationFields.toVerificationResult(): VerificationResult =
    VerificationResult(
        verified = verified,
        expiryTimeMillis = expiryTimeMillis.takeIf { it > 0L },
        isAutoRenewing = autoRenewing,
        purchaseState = purchaseState,
        acknowledgementState = acknowledgementState,
        error = null,
    )

private fun parseErrorResult(): VerificationResult =
    VerificationResult(
        verified = false,
        expiryTimeMillis = null,
        isAutoRenewing = false,
        purchaseState = "PARSE_ERROR",
        acknowledgementState = "UNKNOWN",
        error = "Verification response parse failed",
    )
