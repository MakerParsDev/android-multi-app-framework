package com.parsfilo.contentapp.core.firebase

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.parsfilo.contentapp.core.common.logging.sanitizeLogMessage
import com.parsfilo.contentapp.core.common.logging.toPrivacySafeThrowable
import javax.inject.Inject
import javax.inject.Singleton

/** Stable release identity attached to every Crashlytics event in the process. */
data class RuntimeReleaseContext(
    val packageName: String,
    val flavor: String,
    val versionCode: Int,
    val versionName: String,
    val buildType: String,
    val releaseRevision: String,
    val releaseTrack: String,
)

enum class RuntimeSignal(
    val analyticsValue: String,
    val component: String,
) {
    BILLING_PURCHASE_VERIFICATION("billing_purchase_verification_failure", "billing"),
    REMOTE_CONFIG_FETCH("remote_config_fetch_failure", "remote_config"),
    UMP_CONSENT("ump_consent_failure", "consent"),
    MOBILE_ADS_INITIALIZATION("mobile_ads_initialization_failure", "ads"),
}

data class RuntimeFailure(
    val signal: RuntimeSignal,
    val code: String,
    val message: String,
    val cause: Throwable? = null,
    val attributes: Map<String, String> = emptyMap(),
)

interface RuntimeObservability {
    fun configure(context: RuntimeReleaseContext)

    fun recordFailure(failure: RuntimeFailure)
}

@Singleton
class FirebaseRuntimeObservability @Inject constructor(
    private val crashlytics: FirebaseCrashlytics,
) : RuntimeObservability {
    override fun configure(context: RuntimeReleaseContext) {
        crashlytics.setCustomKey(KEY_PACKAGE_NAME, context.packageName)
        crashlytics.setCustomKey(KEY_FLAVOR, context.flavor)
        crashlytics.setCustomKey(KEY_VERSION_CODE, context.versionCode)
        crashlytics.setCustomKey(KEY_VERSION_NAME, context.versionName)
        crashlytics.setCustomKey(KEY_BUILD_TYPE, context.buildType)
        crashlytics.setCustomKey(KEY_RELEASE_REVISION, normalizeValue(context.releaseRevision))
        crashlytics.setCustomKey(KEY_RELEASE_TRACK, normalizeValue(context.releaseTrack))
    }

    override fun recordFailure(failure: RuntimeFailure) {
        val normalizedCode = normalizeValue(failure.code)
        val normalizedMessage = normalizeMessage(failure.message)
        val normalizedAttributes = normalizeRuntimeAttributes(failure.attributes)
        crashlytics.log(
            buildRuntimeSignalLog(
                signal = failure.signal,
                code = normalizedCode,
                message = normalizedMessage,
                attributes = normalizedAttributes,
            ),
        )
        crashlytics.recordException(
            runtimeSignalException(
                signal = failure.signal,
                code = normalizedCode,
                message = normalizedMessage,
                cause = failure.cause?.toPrivacySafeThrowable(),
            ),
        )
    }

    private fun normalizeValue(value: String): String =
        sanitizeLogMessage(value).trim().replace(Regex("\\s+"), " ")
            .take(MAX_ATTRIBUTE_VALUE_LENGTH)
            .ifBlank { "unknown" }

    private fun normalizeMessage(message: String): String =
        sanitizeLogMessage(message).trim().replace(Regex("\\s+"), " ")
            .take(MAX_MESSAGE_LENGTH)
            .ifBlank { "unspecified failure" }

    private companion object {
        private const val KEY_PACKAGE_NAME = "package_name"
        private const val KEY_FLAVOR = "flavor"
        private const val KEY_VERSION_CODE = "version_code"
        private const val KEY_VERSION_NAME = "version_name"
        private const val KEY_BUILD_TYPE = "build_type"
        private const val KEY_RELEASE_REVISION = "release_revision"
        private const val KEY_RELEASE_TRACK = "release_track"
    }
}

internal fun runtimeSignalException(
    signal: RuntimeSignal,
    code: String,
    message: String,
    cause: Throwable?,
): RuntimeException =
    when (signal) {
        RuntimeSignal.BILLING_PURCHASE_VERIFICATION -> {
            BillingPurchaseVerificationFailure(code, message, cause)
        }

        RuntimeSignal.REMOTE_CONFIG_FETCH -> {
            RemoteConfigFetchFailure(code, message, cause)
        }

        RuntimeSignal.UMP_CONSENT -> {
            UmpConsentFailure(code, message, cause)
        }

        RuntimeSignal.MOBILE_ADS_INITIALIZATION -> {
            MobileAdsInitializationFailure(code, message, cause)
        }
    }

internal fun buildRuntimeSignalLog(
    signal: RuntimeSignal,
    code: String,
    message: String,
    attributes: Map<String, String>,
): String =
    buildString {
        append("runtime_signal=")
        append(signal.analyticsValue)
        append(" component=")
        append(signal.component)
        append(" code=")
        append(code)
        append(" message=")
        append(message)
        attributes.toSortedMap().forEach { (key, value) ->
            append(' ')
            append(key)
            append('=')
            append(value)
        }
    }

internal fun normalizeRuntimeAttributes(attributes: Map<String, String>): Map<String, String> {
    val normalized = LinkedHashMap<String, String>(attributes.size.coerceAtMost(MAX_ATTRIBUTE_COUNT))
    var acceptedCount = 0
    for ((rawKey, rawValue) in attributes) {
        val key = rawKey.trim().lowercase().replace(RUNTIME_ATTRIBUTE_KEY_CLEANUP_REGEX, "_")
            .trim('_')
            .take(MAX_ATTRIBUTE_KEY_LENGTH)
        if (key.isNotBlank() && key !in SENSITIVE_ATTRIBUTE_KEYS) {
            normalized[key] = normalizeAttributeValue(rawValue)
            acceptedCount += 1
            if (acceptedCount >= MAX_ATTRIBUTE_COUNT) break
        }
    }
    return normalized
}

private fun normalizeAttributeValue(value: String): String =
    sanitizeLogMessage(value).trim().replace(Regex("\\s+"), " ")
        .take(MAX_ATTRIBUTE_VALUE_LENGTH)
        .ifBlank { "unknown" }

private open class RuntimeSignalFailure(
    signal: RuntimeSignal,
    code: String,
    message: String,
    cause: Throwable?,
) : RuntimeException("${signal.analyticsValue}[$code]: $message", cause)

private class BillingPurchaseVerificationFailure(
    code: String,
    message: String,
    cause: Throwable?,
) : RuntimeSignalFailure(RuntimeSignal.BILLING_PURCHASE_VERIFICATION, code, message, cause)

private class RemoteConfigFetchFailure(
    code: String,
    message: String,
    cause: Throwable?,
) : RuntimeSignalFailure(RuntimeSignal.REMOTE_CONFIG_FETCH, code, message, cause)

private class UmpConsentFailure(
    code: String,
    message: String,
    cause: Throwable?,
) : RuntimeSignalFailure(RuntimeSignal.UMP_CONSENT, code, message, cause)

private class MobileAdsInitializationFailure(
    code: String,
    message: String,
    cause: Throwable?,
) : RuntimeSignalFailure(RuntimeSignal.MOBILE_ADS_INITIALIZATION, code, message, cause)

private const val MAX_ATTRIBUTE_COUNT = 12
private const val MAX_ATTRIBUTE_KEY_LENGTH = 40
private const val MAX_ATTRIBUTE_VALUE_LENGTH = 100
private val RUNTIME_ATTRIBUTE_KEY_CLEANUP_REGEX = Regex("[^a-z0-9_]+")
private const val MAX_MESSAGE_LENGTH = 240
private val SENSITIVE_ATTRIBUTE_KEYS =
    setOf(
        "purchase_token",
        "auth_token",
        "id_token",
        "app_check_token",
        "authorization",
        "email",
        "user_id",
    )
