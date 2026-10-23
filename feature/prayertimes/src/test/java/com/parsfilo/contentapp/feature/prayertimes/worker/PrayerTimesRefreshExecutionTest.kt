package com.parsfilo.contentapp.feature.prayertimes.worker

import androidx.work.ListenableWorker
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerTimesRepository
import com.parsfilo.contentapp.feature.prayertimes.model.RefreshResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PrayerTimesRefreshExecutionTest {
    private val repository = mockk<PrayerTimesRepository>()
    private val scheduler = mockk<PrayerAlarmScheduler>()

    @Before
    fun setUp() {
        coEvery { scheduler.scheduleNextForCurrentFlavor() } returns true
    }

    private suspend fun execute(alarmEnabled: Boolean = true) = PrayerTimesRefreshExecution.run(
        repository = repository,
        scheduler = scheduler,
        districtId = 42,
        alarmEnabled = alarmEnabled,
    )

    @Test fun `fresh successful API result schedules and succeeds`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.Refreshed
        assertThat(execute()).isEqualTo(ListenableWorker.Result.success())
        coVerify(exactly = 1) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test fun `healthy cached result succeeds with a future alarm`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.SkippedFresh
        assertThat(execute()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test fun `network fallback cache succeeds when alarm stays available`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.ServedFromCache
        assertThat(execute()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test fun `HTTP 503 returns retry and does not run scheduler`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.Failed(IOException("503"))
        assertThat(execute()).isEqualTo(ListenableWorker.Result.retry())
        coVerify(exactly = 0) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test fun `wrapped cancellation propagates rather than retrying`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.Failed(
            CancellationException("worker cancelled"),
        )
        val attempt = runCatching { execute() }
        assertThat(attempt.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        coVerify(exactly = 0) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test fun `temporary parsing failure returns retry`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.Failed(IllegalStateException("empty response"))
        assertThat(execute()).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test fun `thrown network timeout returns retry`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } throws IOException("timeout")
        assertThat(execute()).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test fun `invalid location skips retries and scheduling`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.InvalidSelection
        assertThat(execute()).isEqualTo(ListenableWorker.Result.success())
        coVerify(exactly = 0) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test fun `cached data with no upcoming enabled alarm retries`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.ServedFromCache
        coEvery { scheduler.scheduleNextForCurrentFlavor() } returns false
        assertThat(execute()).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test fun `fresh data but no upcoming enabled alarm retries`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.Refreshed
        coEvery { scheduler.scheduleNextForCurrentFlavor() } returns false
        assertThat(execute()).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test fun `alarm disabled is a valid no scheduling result`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.SkippedFresh
        coEvery { scheduler.scheduleNextForCurrentFlavor() } returns false
        assertThat(execute(alarmEnabled = false)).isEqualTo(ListenableWorker.Result.success())
    }

    @Test fun `alarm manager exception retries`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.Refreshed
        coEvery { scheduler.scheduleNextForCurrentFlavor() } throws SecurityException("alarm service unavailable")
        assertThat(execute()).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test fun `worker cancellation is not converted into retry`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } throws CancellationException("work cancelled")
        val attempt = runCatching { execute() }
        assertThat(attempt.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        coVerify(exactly = 0) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test fun `alarm cancellation is not converted into retry`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returns RefreshResult.Refreshed
        coEvery { scheduler.scheduleNextForCurrentFlavor() } throws CancellationException("work cancelled")
        val attempt = runCatching { execute() }
        assertThat(attempt.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
    }

    @Test fun `transient failure can recover on the following attempt`() = runTest {
        coEvery { repository.refreshIfNeeded(42, false) } returnsMany listOf(
            RefreshResult.Failed(IOException("offline")),
            RefreshResult.Refreshed,
        )
        assertThat(execute()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(execute()).isEqualTo(ListenableWorker.Result.success())
        coVerify(exactly = 1) { scheduler.scheduleNextForCurrentFlavor() }
    }
}
