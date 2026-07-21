package com.parsfilo.contentapp.feature.ads

import timber.log.Timber

/**
 * Debug-only style telemetry (via Timber) for consent funnel conversion visibility.
 *
 * Funnel:
 * consent_started -> consent_granted/consent_denied -> ad_request_sent
 *
 * This is process-local and best-effort; counters reset on app process restart.
 */
object ConsentFunnelDebugDashboard {
    private val lock = Any()
    private var startedCount: Long = 0
    private var grantedCount: Long = 0
    private var deniedCount: Long = 0
    private var adRequestCount: Long = 0
    private var pendingStartedFlows: Long = 0
    private var unmatchedOutcomeCount: Long = 0

    fun onConsentStarted(trigger: String) {
        synchronized(lock) {
            startedCount += 1
            pendingStartedFlows += 1
            logSnapshotLocked(event = "consent_started", detail = "trigger=$trigger")
        }
    }

    fun onConsentOutcome(granted: Boolean, trigger: String) {
        synchronized(lock) {
            if (pendingStartedFlows <= 0) {
                unmatchedOutcomeCount += 1
                logSnapshotLocked(
                    event = if (granted) "consent_granted_unmatched" else "consent_denied_unmatched",
                    detail = "trigger=$trigger",
                )
                return
            }

            pendingStartedFlows -= 1
            if (granted) {
                grantedCount += 1
            } else {
                deniedCount += 1
            }
            logSnapshotLocked(
                event = if (granted) "consent_granted" else "consent_denied",
                detail = "trigger=$trigger",
            )
        }
    }

    fun onAdRequestSent(
        adFormat: AdFormat,
        placement: AdPlacement,
        canRequestAds: Boolean,
    ) {
        synchronized(lock) {
            adRequestCount += 1
            logSnapshotLocked(
                event = "ad_request_sent",
                detail = "format=${adFormat.analyticsValue},placement=${placement.analyticsValue},canRequestAds=$canRequestAds",
            )
        }
    }

    private fun logSnapshotLocked(event: String, detail: String) {
        val started = startedCount.toDouble().coerceAtLeast(1.0)
        val grantedRate = (grantedCount / started) * 100.0
        val deniedRate = (deniedCount / started) * 100.0
        val requestPerStarted = adRequestCount / started
        val requestPerGranted = if (grantedCount > 0) adRequestCount.toDouble() / grantedCount.toDouble() else 0.0

        Timber.d(
            "ConsentFunnel event=%s detail={%s} started=%d granted=%d denied=%d adRequests=%d pending=%d unmatched=%d rates{granted=%.1f%% denied=%.1f%% reqPerStarted=%.2f reqPerGranted=%.2f}",
            event,
            detail,
            startedCount,
            grantedCount,
            deniedCount,
            adRequestCount,
            pendingStartedFlows,
            unmatchedOutcomeCount,
            grantedRate,
            deniedRate,
            requestPerStarted,
            requestPerGranted,
        )
    }
}
