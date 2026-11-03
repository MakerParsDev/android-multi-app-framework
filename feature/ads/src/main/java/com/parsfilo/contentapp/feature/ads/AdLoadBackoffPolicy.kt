package com.parsfilo.contentapp.feature.ads

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal data class AdLoadBackoffState(
    val failureStreak: Int = 0,
    val nextLoadAllowedAtMillis: Long = 0L,
)

internal object AdLoadBackoffPolicy {
    private val scheduleMs = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L)

    fun onLoadSuccess(): AdLoadBackoffState = AdLoadBackoffState()

    fun onLoadFailure(
        nowMillis: Long,
        current: AdLoadBackoffState,
        errorCode: Int?,
        errorDomain: String?,
        random: Random = Random.Default,
    ): AdLoadBackoffState {
        val nextStreak = current.failureStreak + 1
        val baseDelay = scheduleMs[min(nextStreak - 1, scheduleMs.lastIndex)]
        // SDK numeric codes are not globally unique across error domains.
        val isGoogleNoFill = errorCode == AdRequest.ERROR_CODE_NO_FILL &&
            errorDomain == MobileAds.ERROR_DOMAIN
        val tunedDelay = if (isGoogleNoFill) baseDelay else max(3_000L, baseDelay / 2)
        val jittered = applyJitter(tunedDelay, random)
        return AdLoadBackoffState(
            failureStreak = nextStreak,
            nextLoadAllowedAtMillis = nowMillis + jittered,
        )
    }

    fun canLoad(nowMillis: Long, state: AdLoadBackoffState): Boolean =
        nowMillis >= state.nextLoadAllowedAtMillis

    private fun applyJitter(baseDelayMs: Long, random: Random): Long {
        val jitterRange = (baseDelayMs * 0.2).toLong()
        if (jitterRange <= 0L) return baseDelayMs
        return baseDelayMs + random.nextLong(-jitterRange, jitterRange + 1)
    }
}
