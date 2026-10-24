package com.parsfilo.contentapp.feature.prayertimes.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerTimesRepository
import com.parsfilo.contentapp.feature.prayertimes.model.RefreshResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Real Android runtime, controlled fakes. No network, location, or AlarmManager calls. */
@RunWith(AndroidJUnit4::class)
class PrayerTimesRefreshExecutionDeviceTest {
    private val repository = mockk<PrayerTimesRepository>()
    private val scheduler = mockk<PrayerAlarmScheduler>()

    private suspend fun execute() = PrayerTimesRefreshExecution.run(
        repository = repository,
        scheduler = scheduler,
        districtId = 42,
        alarmEnabled = true,
    )

    @Test
    fun backend503RequestsRetryWithoutTouchingAlarms() = runBlocking {
        coEvery { repository.refreshIfNeeded(42, false, false) } returns
            RefreshResult.Failed(IOException("synthetic HTTP 503"))
        assertEquals(ListenableWorker.Result.retry(), execute())
        coVerify(exactly = 0) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test
    fun successfulNetworkRefreshSchedulesExactlyOnce() = runBlocking {
        coEvery { repository.refreshIfNeeded(42, false, false) } returns RefreshResult.Refreshed
        coEvery { scheduler.scheduleNextForCurrentFlavor() } returns true
        assertEquals(ListenableWorker.Result.success(), execute())
        coVerify(exactly = 1) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test
    fun healthyCacheSchedulesExactlyOnce() = runBlocking {
        coEvery { repository.refreshIfNeeded(42, false, false) } returns RefreshResult.SkippedFresh
        coEvery { scheduler.scheduleNextForCurrentFlavor() } returns true
        assertEquals(ListenableWorker.Result.success(), execute())
        coVerify(exactly = 1) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test
    fun missingUpcomingAlarmRequestsRetry() = runBlocking {
        coEvery { repository.refreshIfNeeded(42, false, false) } returns RefreshResult.ServedFromCache
        coEvery { scheduler.scheduleNextForCurrentFlavor() } returns false
        assertEquals(ListenableWorker.Result.retry(), execute())
        coVerify(exactly = 1) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test
    fun invalidSelectionDoesNotRetryOrSchedule() = runBlocking {
        coEvery { repository.refreshIfNeeded(42, false, false) } returns RefreshResult.InvalidSelection
        assertEquals(ListenableWorker.Result.success(), execute())
        coVerify(exactly = 0) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test
    fun transientFailureThenRecoverySchedulesOnlyOnSuccess() = runBlocking {
        coEvery { repository.refreshIfNeeded(42, false, false) } returnsMany listOf(
            RefreshResult.Failed(IOException("synthetic timeout")),
            RefreshResult.Refreshed,
        )
        coEvery { scheduler.scheduleNextForCurrentFlavor() } returns true
        assertEquals(ListenableWorker.Result.retry(), execute())
        assertEquals(ListenableWorker.Result.success(), execute())
        coVerify(exactly = 1) { scheduler.scheduleNextForCurrentFlavor() }
    }
}
