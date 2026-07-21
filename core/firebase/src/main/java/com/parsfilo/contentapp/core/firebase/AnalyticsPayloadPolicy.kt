package com.parsfilo.contentapp.core.firebase

import android.os.Bundle

private const val MAX_ANALYTICS_STRING_LENGTH = 100
private const val REDACTED_VALUE = "[redacted]"
private const val MIN_PHONE_DIGIT_COUNT = 10

private val forbiddenParameterTokens =
    setOf(
        "email",
        "phone",
        "address",
        "auth_token",
        "access_token",
        "refresh_token",
        "purchase_token",
        "advertising_id",
        "device_id",
        "latitude",
        "longitude",
    )

private val droppedFreeTextParameters =
    setOf(
        AnalyticsParamKey.ERROR_MESSAGE,
    )

private val admobAdUnitIdPattern = Regex("^ca-app-pub-\\d{16}/\\d{10}$")
private val governedContentIdPattern = Regex("^(verse|surah|page):\\d{1,12}$")

private val emailPattern = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
private val bearerPattern = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{8,}")
private val jwtPattern = Regex("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}")
private val phonePattern = Regex("(?<!\\d)(?:\\+?\\d[\\d ()-]{8,}\\d)(?!\\d)")

internal fun isAnalyticsParameterAllowed(key: String): Boolean {
    val normalized = key.trim().lowercase()
    if (normalized.isBlank()) return false
    if (normalized in droppedFreeTextParameters) return false
    return forbiddenParameterTokens.none { token ->
        normalized == token ||
            normalized.startsWith("${token}_") ||
            normalized.endsWith("_$token") ||
            normalized.contains("_${token}_")
    }
}

internal fun sanitizeAnalyticsString(
    key: String,
    value: String,
): String? {
    if (!isAnalyticsParameterAllowed(key)) return null
    val normalized = value.trim().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return null
    val phoneSafeStructuredValue =
        when (key) {
            AnalyticsParamKey.AD_UNIT_ID -> admobAdUnitIdPattern.matches(normalized)
            AnalyticsParamKey.CONTENT_ID -> governedContentIdPattern.matches(normalized)
            else -> false
        }
    val containsSensitiveValue =
        emailPattern.containsMatchIn(normalized) ||
            bearerPattern.containsMatchIn(normalized) ||
            jwtPattern.containsMatchIn(normalized) ||
            (!phoneSafeStructuredValue && containsPhoneLikeValue(normalized))
    return if (containsSensitiveValue) {
        REDACTED_VALUE
    } else {
        normalized.take(MAX_ANALYTICS_STRING_LENGTH)
    }
}

private fun containsPhoneLikeValue(value: String): Boolean =
    value.count(Char::isDigit) >= MIN_PHONE_DIGIT_COUNT && phonePattern.containsMatchIn(value)

@Suppress("DEPRECATION")
internal fun sanitizeAnalyticsBundle(params: Bundle?): Bundle? {
    if (params == null) return null
    val sanitized = Bundle()
    params.keySet().sorted().forEach { key ->
        if (!isAnalyticsParameterAllowed(key)) return@forEach
        when (val value = params.get(key)) {
            is String -> sanitizeAnalyticsString(key, value)?.let { sanitized.putString(key, it) }
            is Long -> sanitized.putLong(key, value)
            is Int -> sanitized.putLong(key, value.toLong())
            is Double -> sanitized.putDouble(key, value)
            is Float -> sanitized.putDouble(key, value.toDouble())
            is Boolean -> sanitized.putLong(key, if (value) 1L else 0L)
        }
    }
    return sanitized.takeUnless { it.isEmpty }
}

internal fun isAllowedAnalyticsUserId(userId: String): Boolean =
    userId.startsWith("anon_") && userId.length in 6..40 && userId.drop(5).all { it.isLetterOrDigit() || it == '_' || it == '-' }
