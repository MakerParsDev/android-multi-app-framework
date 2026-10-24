package com.parsfilo.contentapp.feature.prayertimes.alarm

import androidx.work.ListenableWorker
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerTimesRepository
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import com.parsfilo.contentapp.feature.prayertimes.model.RefreshResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class PrayerAlarmRecoveryCacheRefillTest {
    private val scheduler = mockk<PrayerAlarmScheduler>()
    private val repository = mockk<PrayerTimesRepository>()
    private val variant = PrayerAppVariant.NAMAZ_VAKITLERI

    private suspend fun run(attempt: Int = 0): ListenableWorker.Result =
        PrayerAlarmRecoveryExecution.run(
            scheduler = scheduler,
            repository = repository,
            variant = variant,
            districtId = 42,
            runAttemptCount = attempt,
        )

    @Test fun cachedFutureAlarmSucceedsOfflineWithoutApiCall() = runTest {
        coEvery { scheduler.scheduleNext(variant) } returns true
        assertThat(run()).isEqualTo(ListenableWorker.Result.success())
        coVerify(exactly = 1) { scheduler.scheduleNext(variant) }
        coVerify(exactly = 0) { repository.refreshIfNeeded(any(), any(), any()) }
    }

    @Test fun missingFutureAlarmRefillsCacheAndSchedules() = runTest {
        coEvery { scheduler.scheduleNext(variant) } returnsMany listOf(false, true)
        coEvery { repository.refreshIfNeeded(42, true, false) } returns RefreshResult.Refreshed
        assertThat(run()).isEqualTo(ListenableWorker.Result.success())
        coVerify(exactly = 2) { scheduler.scheduleNext(variant) }
        coVerify(exactly = 1) { repository.refreshIfNeeded(42, true, false) }
    }

    @Test fun temporaryNetworkOutageRetriesWithoutSchedulingAgain() = runTest {
        coEvery { scheduler.scheduleNext(variant) } returns false
        coEvery { repository.refreshIfNeeded(42, true, false) } returns RefreshResult.Failed(IOException("503"))
        assertThat(run()).isEqualTo(ListenableWorker.Result.retry())
        coVerify(exactly = 1) { scheduler.scheduleNext(variant) }
    }

    @Test fun missingCacheAfterRefillRetriesOnlyWithinBudget() = runTest {
        coEvery { scheduler.scheduleNext(variant) } returns false
        coEvery { repository.refreshIfNeeded(42, true, false) } returns RefreshResult.ServedFromCache
        assertThat(run(attempt = 1)).isEqualTo(ListenableWorker.Result.retry())
        assertThat(run(attempt = 2)).isEqualTo(ListenableWorker.Result.failure())
        coVerify(exactly = 4) { scheduler.scheduleNext(variant) }
    }

    @Test fun permanentlyInvalidSelectionDoesNotRetry() = runTest {
        coEvery { scheduler.scheduleNext(variant) } returns false
        coEvery { repository.refreshIfNeeded(42, true, false) } returns RefreshResult.InvalidSelection
        assertThat(run()).isEqualTo(ListenableWorker.Result.failure())
        coVerify(exactly = 1) { scheduler.scheduleNext(variant) }
    }

    @Test fun cancellationFromRefillIsPropagated() = runTest {
        coEvery { scheduler.scheduleNext(variant) } returns false
        coEvery { repository.refreshIfNeeded(42, true, false) } returns RefreshResult.Failed(
            CancellationException("stop"),
        )
        assertThat(runCatching { run() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
    }
}
