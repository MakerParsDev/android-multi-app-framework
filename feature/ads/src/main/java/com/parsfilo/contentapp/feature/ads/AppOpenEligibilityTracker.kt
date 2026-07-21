package com.parsfilo.contentapp.feature.ads

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenEligibilityTracker @Inject constructor() {
    private var sessionAppOpenCount: Int = 0
    private var lastResumeAtMs: Long = 0L
    private var lastPauseAtMs: Long = 0L
    private var hasSeenFirstResume: Boolean = false

    data class ResumeSnapshot(
        val isColdStart: Boolean,
        val resumeGapMs: Long?,
        val sessionCount: Int,
    )

    fun onResume(nowMs: Long = SystemTimeProvider.nowMillis()): ResumeSnapshot {
        val isColdStart = !hasSeenFirstResume
        val resumeGap = if (lastPauseAtMs > 0L) {
            (nowMs - lastPauseAtMs).coerceAtLeast(0L)
        } else {
            null
        }
        hasSeenFirstResume = true
        lastResumeAtMs = nowMs
        return ResumeSnapshot(
            isColdStart = isColdStart,
            resumeGapMs = resumeGap,
            sessionCount = sessionAppOpenCount,
        )
    }

    fun onPause(nowMs: Long = SystemTimeProvider.nowMillis()) {
        lastPauseAtMs = nowMs
    }

    fun onShown() {
        sessionAppOpenCount += 1
    }
}
