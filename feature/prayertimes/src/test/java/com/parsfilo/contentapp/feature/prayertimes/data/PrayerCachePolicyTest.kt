package com.parsfilo.contentapp.feature.prayertimes.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PrayerCachePolicyTest {

    @Test
    fun `shouldRefresh returns false when cache is healthy`() {
        val now = epochMillis(year = 2026, month = 2, day = 18, hour = 10)
        val today = "2026-02-18"

        val shouldRefresh = PrayerCachePolicy.shouldRefresh(
            nowMillis = now,
            lastSyncAtMillis = epochMillis(year = 2026, month = 2, day = 18, hour = 9, minute = 59),
            coverageEndIso = "2026-03-20",
            todayIso = today,
            hasTodayCache = true,
            force = false,
        )

        assertThat(shouldRefresh).isFalse()
    }

    @Test
    fun `shouldRefresh returns true when stale`() {
        val now = epochMillis(year = 2026, month = 2, day = 18, hour = 20)
        val today = "2026-02-18"

        val shouldRefresh = PrayerCachePolicy.shouldRefresh(
            nowMillis = now,
            lastSyncAtMillis = epochMillis(year = 2026, month = 2, day = 18, hour = 6),
            coverageEndIso = "2026-03-20",
            todayIso = today,
            hasTodayCache = true,
            force = false,
        )

        assertThat(shouldRefresh).isTrue()
    }

    @Test
    fun `shouldRefresh returns true when day changed`() {
        val zoneId = ZoneId.systemDefault()
        val lastSyncAt = LocalDateTime.of(2026, 2, 17, 23, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val now = LocalDateTime.of(2026, 2, 18, 6, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val shouldRefresh = PrayerCachePolicy.shouldRefresh(
            nowMillis = now,
            lastSyncAtMillis = lastSyncAt,
            coverageEndIso = "2026-03-20",
            todayIso = "2026-02-18",
            hasTodayCache = true,
            force = false,
        )

        assertThat(shouldRefresh).isTrue()
    }

    @Test
    fun `pruneDistrictIds keeps active and most recent three`() {
        val dropped = PrayerCachePolicy.pruneDistrictIds(
            recentDistrictIds = listOf(10, 11, 12, 13, 14),
            activeDistrictId = 13,
            maxCachedDistricts = 3,
        )

        assertThat(dropped).containsExactly(14)
    }

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
