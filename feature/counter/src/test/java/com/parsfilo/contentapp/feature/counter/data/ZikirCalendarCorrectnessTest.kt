package com.parsfilo.contentapp.feature.counter.data

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.model.zikir.ZikirStreakEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class ZikirCalendarCorrectnessTest {
    @Test
    fun boundedDayQueryExcludesFutureRowsAndSwitchesWithoutRoomInvalidation() = runTest {
        val zone = TimeZone.getTimeZone("Europe/Istanbul")
        val startClock = Instant.parse("2026-10-24T20:59:59Z").toEpochMilli()
        val (dayAStart, dayBStart) = localDayBounds(startClock, zone)
        val syntheticSessions = listOf(
            dayAStart + 60_000L to 33,
            dayBStart + 60_000L to 33,
        )
        val counts = mutableListOf<Int>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
            queryCurrentLocalDayWindow(
                nowMillis = { startClock + testScheduler.currentTime },
                timeZone = { zone },
            ) { from, until ->
                flowOf(syntheticSessions.filter { (time, _) -> time >= from && time < until }.sumOf { it.second })
            }.collect { counts += it }
        }
        runCurrent()
        assertThat(counts).containsExactly(33)
        advanceTimeBy(1001L)
        runCurrent()
        assertThat(counts).containsExactly(33, 33).inOrder()
    }

    @Test
    fun calendarDayWindowsHandleDstAndNonHourOffsets() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val india = TimeZone.getTimeZone("Asia/Kolkata")
        val inBerlin = Instant.parse("2026-03-29T10:00:00Z").toEpochMilli()
        val (_, next) = localDayBounds(inBerlin, berlin)
        val (start, _) = localDayBounds(inBerlin, berlin)
        assertThat(next - start).isEqualTo(23L * 60 * 60 * 1000)
        val (indiaStart, _) = localDayBounds(inBerlin, india)
        assertThat(indiaStart % (60 * 60 * 1000)).isNotEqualTo(0L)
    }

    @Test
    fun streakRetainsOnlyTodayOrYesterdayActivityWithoutLosingHistoricalMax() {
        val zone = TimeZone.getTimeZone("Europe/Istanbul")
        val now = Instant.parse("2026-10-05T09:00:00Z").toEpochMilli()
        val streak = ZikirStreakEntity(
            currentStreak = 10,
            longestStreak = 21,
            lastActivityDate = "2026-10-04",
        )
        assertThat(effectiveActiveStreak(streak, now, zone)).isEqualTo(10)
        assertThat(effectiveActiveStreak(streak.copy(lastActivityDate = "2026-10-05"), now, zone)).isEqualTo(10)
        assertThat(effectiveActiveStreak(streak.copy(lastActivityDate = "2026-10-03"), now, zone)).isEqualTo(0)
        assertThat(effectiveActiveStreak(streak.copy(lastActivityDate = "2026-10-06"), now, zone)).isEqualTo(0)
        assertThat(streak.longestStreak).isEqualTo(21)
        assertThat(streak.currentStreak).isEqualTo(10)
    }

    @Test
    fun yesterdayStreakExpiresOnNextMidnightAcrossDstAndTimezoneShift() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val before = Instant.parse("2026-03-29T21:59:59Z").toEpochMilli()
        val after = Instant.parse("2026-03-29T22:00:01Z").toEpochMilli()
        val streak = ZikirStreakEntity(currentStreak = 5, longestStreak = 8, lastActivityDate = "2026-03-28")
        assertThat(effectiveActiveStreak(streak, before, berlin)).isEqualTo(5)
        assertThat(effectiveActiveStreak(streak, after, berlin)).isEqualTo(0)
        val newYork = TimeZone.getTimeZone("America/New_York")
        assertThat(effectiveActiveStreak(streak, after, newYork)).isEqualTo(5)
    }
}
