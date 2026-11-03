package com.parsfilo.contentapp.feature.prayertimes.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesDay
import com.parsfilo.contentapp.feature.prayertimes.ui.components.initialPrayerDayIndex
import org.junit.Test

class PrayerTimesInitialDayTest {
    private fun day(iso: String) = PrayerTimesDay(
        localDate = iso,
        imsak = "05:30",
        gunes = "06:45",
        ogle = "12:30",
        ikindi = "15:30",
        aksam = "18:00",
        yatsi = "19:20",
    )

    @Test fun todayWinsOverTwoDaysOfHistoricalCache() {
        val days = listOf(day("2026-11-01"), day("2026-11-02"), day("2026-11-03"), day("2026-11-04"))
        assertThat(initialPrayerDayIndex(days, "2026-11-03")).isEqualTo(2)
    }

    @Test fun missingTodaySelectsNearestFutureDay() {
        val days = listOf(day("2026-11-01"), day("2026-11-04"), day("2026-11-07"))
        assertThat(initialPrayerDayIndex(days, "2026-11-03")).isEqualTo(1)
    }

    @Test fun onlyHistoricalCacheFallsBackToLatestDay() {
        val days = listOf(day("2026-10-30"), day("2026-11-01"), day("2026-11-02"))
        assertThat(initialPrayerDayIndex(days, "2026-11-03")).isEqualTo(2)
    }

    @Test fun onlyFutureDaysSelectsFirstAvailableDay() {
        val days = listOf(day("2026-11-05"), day("2026-11-06"))
        assertThat(initialPrayerDayIndex(days, "2026-11-03")).isEqualTo(0)
    }

    @Test fun emptyCacheIsHandledWithoutIndexError() {
        assertThat(initialPrayerDayIndex(emptyList(), "2026-11-03")).isEqualTo(0)
    }
}
