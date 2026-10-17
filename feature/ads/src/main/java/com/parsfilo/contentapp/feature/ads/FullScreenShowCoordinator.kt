package com.parsfilo.contentapp.feature.ads

import android.os.SystemClock
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

    @Synchronized
    fun tryBegin(
        format: AdFormat,
        nowMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        require(format == AdFormat.APP_OPEN || format == AdFormat.INTERSTITIAL)
        // Never abandon an active SDK-owned show solely because time elapsed.
        // Only its actual dismissal/failure callback may release the reservation.
        if (activeFormat != null) return false
        val lastImpression = lastImpressionAtMs
        if (lastImpression != null && (nowMs - lastImpression) in 0 until MIN_SPACING_MS) {
            return false
        }
        activeFormat = format
        return true
    }

    @Synchronized
    fun onImpression(format: AdFormat, nowMs: Long = SystemClock.elapsedRealtime()) {
        if (activeFormat == format) lastImpressionAtMs = nowMs
    }

    @Synchronized
    fun finish(format: AdFormat) {
        if (activeFormat == format) activeFormat = null
    }

    companion object {
        const val MIN_SPACING_MS: Long = 30_000L
    }
}
