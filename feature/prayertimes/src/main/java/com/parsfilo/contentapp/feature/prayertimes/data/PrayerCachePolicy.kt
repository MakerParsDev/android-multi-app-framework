package com.parsfilo.contentapp.feature.prayertimes.data

internal object PrayerCachePolicy {
    private const val STALE_DURATION_MS = 12L * 60 * 60 * 1000
    private const val PAST_DAYS = -2
    private const val FUTURE_DAYS = 45
    private const val MIN_COVERAGE_DAYS = 21

    fun dateWindow(todayIso: String): Pair<String, String> {
        return PrayerDateTime.shiftIsoDate(todayIso, PAST_DAYS) to
            PrayerDateTime.shiftIsoDate(todayIso, FUTURE_DAYS)
    }

    fun shouldRefresh(
        nowMillis: Long,
        lastSyncAtMillis: Long?,
        coverageEndIso: String?,
        todayIso: String,
        hasTodayCache: Boolean,
        force: Boolean,
    ): Boolean {
        if (force) return true
        if (!hasTodayCache) return true

        val stale = lastSyncAtMillis == null || nowMillis - lastSyncAtMillis > STALE_DURATION_MS
        val dayChanged = lastSyncAtMillis == null ||
            PrayerDateTime.dateIsoFromEpochMillis(lastSyncAtMillis) != todayIso
        val minCoverageIso = PrayerDateTime.shiftIsoDate(todayIso, MIN_COVERAGE_DAYS)
        val lowCoverage = coverageEndIso.isNullOrBlank() || coverageEndIso < minCoverageIso
        return stale || lowCoverage || dayChanged
    }

    fun pruneDistrictIds(
        recentDistrictIds: List<Int>,
        activeDistrictId: Int,
        maxCachedDistricts: Int = 3,
    ): List<Int> {
        val keepIds = buildSet {
            add(activeDistrictId)
            addAll(recentDistrictIds.take(maxCachedDistricts))
        }
        return recentDistrictIds.filterNot { keepIds.contains(it) }
    }
}
