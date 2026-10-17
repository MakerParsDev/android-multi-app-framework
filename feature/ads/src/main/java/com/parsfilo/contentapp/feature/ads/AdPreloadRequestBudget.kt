package com.parsfilo.contentapp.feature.ads

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional best-effort local request budget across native/interstitial/app-open.
 * This is NOT an official AdMob rate limit. Banner auto-refresh and user-initiated
 * rewarded requests are intentionally excluded.
 */
@Singleton
class AdPreloadRequestBudget @Inject constructor() {
    private val requests = ArrayDeque<Long>()

    @Synchronized
    fun tryReserve(count: Int = 1, nowMs: Long = SystemTimeProvider.nowMillis()): Boolean {
        require(count in 1..MAX_REQUESTS_PER_MINUTE)
        while (requests.isNotEmpty() && (nowMs - requests.first >= WINDOW_MS || nowMs < requests.first)) {
            requests.removeFirst()
        }
        if (requests.size + count > MAX_REQUESTS_PER_MINUTE) return false
        repeat(count) { requests.addLast(nowMs) }
        return true
    }

    companion object {
        const val MAX_REQUESTS_PER_MINUTE = 12
        const val WINDOW_MS = 60_000L
    }
}
