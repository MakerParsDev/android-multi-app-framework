package com.parsfilo.contentapp.core.firebase

import android.os.Bundle

/**
 * Audio analytics:
 * - This app's audio is typically "content-level" (single track per flavor).
 * - Keep params generic (position/duration) to avoid high-cardinality identifiers.
 */
fun AppAnalytics.logAudioPlay(positionMs: Long, durationMs: Long) {
    logEvent(
        AnalyticsEventName.AUDIO_PLAY,
        Bundle().apply {
            putLong(AnalyticsParamKey.POSITION_MS, positionMs)
            putLong(AnalyticsParamKey.DURATION_MS, durationMs)
        },
    )
}

fun AppAnalytics.logAudioPause(positionMs: Long, durationMs: Long) {
    logEvent(
        AnalyticsEventName.AUDIO_PAUSE,
        Bundle().apply {
            putLong(AnalyticsParamKey.POSITION_MS, positionMs)
            putLong(AnalyticsParamKey.DURATION_MS, durationMs)
        },
    )
}

fun AppAnalytics.logAudioStop(positionMs: Long, durationMs: Long) {
    logEvent(
        AnalyticsEventName.AUDIO_STOP,
        Bundle().apply {
            putLong(AnalyticsParamKey.POSITION_MS, positionMs)
            putLong(AnalyticsParamKey.DURATION_MS, durationMs)
        },
    )
}

fun AppAnalytics.logAudioComplete(totalDurationMs: Long) {
    logEvent(
        AnalyticsEventName.AUDIO_COMPLETE,
        Bundle().apply {
            putLong(AnalyticsParamKey.TOTAL_DURATION_MS, totalDurationMs)
            putLong(AnalyticsParamKey.AUDIO_DURATION_MS, totalDurationMs)
        },
    )
}
