package com.parsfilo.contentapp.feature.counter.data

import com.google.common.truth.Truth.assertThat
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
class ZikirDailyBoundaryTest {
    @Test fun midnightResubscribesToDailyRoomQueryWithoutDatabaseInvalidation() = runTest {
        val istanbul = TimeZone.getTimeZone("Europe/Istanbul")
        val base = Instant.parse("2026-10-24T20:59:59Z").toEpochMilli()
        val queriedCutoffs = mutableListOf<Long>()
        val outputs = mutableListOf<Long>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
            queryCurrentLocalDay(
                nowMillis = { base + testScheduler.currentTime },
                timeZone = { istanbul },
            ) { cutoff ->
                queriedCutoffs += cutoff
                flowOf(cutoff)
            }.collect { outputs += it }
        }
        runCurrent()
        assertThat(outputs).containsExactly(localDayBounds(base, istanbul).first)
        advanceTimeBy(1001L)
        runCurrent()
        assertThat(outputs).hasSize(2)
        assertThat(queriedCutoffs).hasSize(2)
        assertThat(outputs.last()).isEqualTo(localDayBounds(base + 1001L, istanbul).first)
    }

    @Test fun sameDayPollingDoesNotRestartDaoQuery() = runTest {
        val zone = TimeZone.getTimeZone("Europe/Istanbul")
        val base = Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
        val cutoffs = mutableListOf<Long>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
            queryCurrentLocalDay(
                nowMillis = { base + testScheduler.currentTime },
                timeZone = { zone },
            ) { cutoff ->
                cutoffs += cutoff
                flowOf(cutoff)
            }.collect()
        }
        runCurrent()
        advanceTimeBy(180_000L)
        runCurrent()
        assertThat(cutoffs).containsExactly(localDayBounds(base, zone).first)
    }

    @Test fun changingTimezoneWhileOpenReissuesDailyCutoff() = runTest {
        var zone = TimeZone.getTimeZone("Europe/Istanbul")
        val base = Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
        val cutoffs = mutableListOf<Long>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
            queryCurrentLocalDay(
                nowMillis = { base + testScheduler.currentTime },
                timeZone = { zone },
            ) { cutoff ->
                cutoffs += cutoff
                flowOf(cutoff)
            }.collect()
        }
        runCurrent()
        val first = cutoffs.single()
        zone = TimeZone.getTimeZone("America/New_York")
        advanceTimeBy(60_001L)
        runCurrent()
        assertThat(cutoffs).hasSize(2)
        assertThat(cutoffs.last()).isNotEqualTo(first)
    }

    @Test fun dstStartUsesCalendarDayInsteadOfFixed24Hours() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val march29 = Instant.parse("2026-03-29T10:00:00Z").toEpochMilli()
        val (start, next) = localDayBounds(march29, berlin)
        assertThat(next - start).isEqualTo(23 * 60 * 60 * 1000L)
    }
}
