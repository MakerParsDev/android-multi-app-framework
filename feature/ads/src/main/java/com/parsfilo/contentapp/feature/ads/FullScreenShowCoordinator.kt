package com.parsfilo.contentapp.feature.ads

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes automatic full-screen ads across managers and prevents back-to-back
 * impressions. User-initiated rewarded ads retain their own eligibility policy.
 * This in-memory guard intentionally complements rather than replaces per-format
 * persisted frequency caps.
 */
@Singleton
class FullScreenShowCoordinator @Inject constructor() {
    private var activeFormat: AdFormat? = null
    private var lastImpressionAtMs: Long? = null
    private var activeSinceMs: Long = 0L

    @Synchronized
    fun tryBegin(
        format: AdFormat,
        nowMs: Long = SystemTimeProvider.nowMillis(),
    ): Boolean {
        require(format == AdFormat.APP_OPEN || format == AdFormat.INTERSTITIAL)
        // Failsafe for lost SDK callbacks; never keep a process blocked indefinitely.
        if (activeFormat != null && nowMs - activeSinceMs > STALE_RESERVATION_MS) {
            activeFormat = null
        }
        if (activeFormat != null) return false
        val lastImpression = lastImpressionAtMs
        if (lastImpression != null && (nowMs - lastImpression) in 0 until MIN_SPACING_MS) {
            return false
        }
        activeFormat = format
        activeSinceMs = nowMs
        return true
    }

    @Synchronized
    fun onImpression(format: AdFormat, nowMs: Long = SystemTimeProvider.nowMillis()) {
        if (activeFormat == format) lastImpressionAtMs = nowMs
    }

    @Synchronized
    fun finish(format: AdFormat) {
        if (activeFormat == format) activeFormat = null
    }

    companion object {
        const val MIN_SPACING_MS: Long = 30_000L
        private const val STALE_RESERVATION_MS: Long = 10 * 60_000L
    }
}
