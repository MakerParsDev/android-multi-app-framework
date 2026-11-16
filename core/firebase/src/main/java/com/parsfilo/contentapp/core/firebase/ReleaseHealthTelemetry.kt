package com.parsfilo.contentapp.core.firebase

import javax.inject.Inject
import javax.inject.Singleton

/** Low-cardinality, version-scoped events without user or operation identifiers. */
enum class ReleaseHealthOperation(
    val attemptEvent: String,
    val successEvent: String,
    val failureEvent: String,
) {
    BILLING_VERIFICATION(
        AnalyticsEventName.BILLING_VERIFY_ATTEMPT,
        AnalyticsEventName.BILLING_VERIFY_SUCCESS,
        AnalyticsEventName.BILLING_VERIFY_FAILURE,
    ),
    PUSH_REGISTRATION(
        AnalyticsEventName.PUSH_SYNC_ATTEMPT,
        AnalyticsEventName.PUSH_SYNC_SUCCESS,
        AnalyticsEventName.PUSH_SYNC_FAILURE,
    ),
    REMOTE_CONFIG_FETCH(
        AnalyticsEventName.REMOTE_CONFIG_ATTEMPT,
        AnalyticsEventName.REMOTE_CONFIG_SUCCESS,
        AnalyticsEventName.REMOTE_CONFIG_FAILURE,
    ),
}

/**
 * Telemetry must never interrupt purchase verification, push sync or Remote Config.
 * A missing analytics event means INCOMPLETE, not zero failures.
 */
@Singleton
class ReleaseHealthTelemetry @Inject constructor(private val analytics: AppAnalytics) {
    fun attempt(operation: ReleaseHealthOperation) = emit(operation.attemptEvent)

    fun result(operation: ReleaseHealthOperation, successful: Boolean) =
        emit(if (successful) operation.successEvent else operation.failureEvent)

    private fun emit(event: String) {
        runCatching { analytics.logEvent(event) }
        // Event delivery is best-effort and governed by the user's analytics consent.
    }
}
