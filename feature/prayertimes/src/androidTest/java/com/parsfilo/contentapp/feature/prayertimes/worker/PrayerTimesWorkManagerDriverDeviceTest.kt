package com.parsfilo.contentapp.feature.prayertimes.worker

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerTimesRepository
import com.parsfilo.contentapp.feature.prayertimes.model.RefreshResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real WorkManager periodic/TestDriver integration with synthetic repository and scheduler.
 * Runs only inside this separate feature-module test APK: no real app data, API,
 * user location, notification, or Android AlarmManager is touched.
 */
@RunWith(AndroidJUnit4::class)
class PrayerTimesWorkManagerDriverDeviceTest {
    private val workName = "prayer_times_daily_refresh"

    @Test
    fun periodicFailureKeepsWorkScheduledAndDoesNotArmAlarm() =
        checkScenario(
            expectedRetry = true,
            expectedSchedulingCalls = 0,
        ) { repository, _ ->
            coEvery { repository.refreshIfNeeded(42, false, false) } returns
                RefreshResult.Failed(IOException("synthetic HTTP 503"))
        }

    @Test
    fun thrownTimeoutRequestsWorkManagerRetry() =
        checkScenario(
            expectedRetry = true,
            expectedSchedulingCalls = 0,
        ) { repository, _ ->
            coEvery { repository.refreshIfNeeded(42, false, false) } throws IOException("synthetic timeout")
        }

    @Test
    fun invalidSelectionDoesNotRetryOrScheduleAlarm() =
        checkScenario(
            expectedRetry = false,
            expectedSchedulingCalls = 0,
        ) { repository, _ ->
            coEvery { repository.refreshIfNeeded(42, false, false) } returns RefreshResult.InvalidSelection
        }

    @Test
    fun freshCacheWithFutureAlarmCompletesPeriod() =
        checkScenario(
            expectedRetry = false,
            expectedSchedulingCalls = 1,
        ) { repository, _ ->
            coEvery { repository.refreshIfNeeded(42, false, false) } returns RefreshResult.SkippedFresh
        }

    @Test
    fun networkFallbackWithFutureAlarmCompletesPeriod() =
        checkScenario(
            expectedRetry = false,
            expectedSchedulingCalls = 1,
        ) { repository, _ ->
            coEvery { repository.refreshIfNeeded(42, false, false) } returns RefreshResult.ServedFromCache
        }

    @Test
    fun cachedButNoFutureAlarmRequestsRetry() =
        checkScenario(
            expectedRetry = true,
            expectedSchedulingCalls = 1,
        ) { repository, scheduler ->
            coEvery { repository.refreshIfNeeded(42, false, false) } returns RefreshResult.ServedFromCache
            coEvery { scheduler.scheduleNextForCurrentFlavor() } returns false
        }

    @Test
    fun schedulerExceptionRequestsRetry() =
        checkScenario(
            expectedRetry = true,
            expectedSchedulingCalls = 1,
        ) { repository, scheduler ->
            coEvery { repository.refreshIfNeeded(42, false, false) } returns RefreshResult.Refreshed
            coEvery { scheduler.scheduleNextForCurrentFlavor() } throws SecurityException("synthetic")
        }

    @Test
    fun retryRecoversAfterRepositoryReturnsHealthyData() {
        val calls = AtomicInteger()
        runWithManager(
            configure = { repository, _ ->
                coEvery { repository.refreshIfNeeded(42, false, false) } answers {
                    if (calls.incrementAndGet() == 1) {
                        RefreshResult.Failed(IOException("synthetic temporary outage"))
                    } else {
                        RefreshResult.Refreshed
                    }
                }
            },
        ) { manager, driver, id, runs, scheduler ->
            awaitPeriod(manager, id, runs, minimumRuns = 1, shouldRetry = true)
            coVerify(exactly = 0) { scheduler.scheduleNextForCurrentFlavor() }
            // Simulate the next eligible periodic cycle without altering any real network.
            driver.setAllConstraintsMet(id)
            driver.setPeriodDelayMet(id)
            awaitPeriod(manager, id, runs, minimumRuns = 2, shouldRetry = false)
            assertTrue("Recovery must call repository again", calls.get() >= 2)
            coVerify(exactly = 1) { scheduler.scheduleNextForCurrentFlavor() }
        }
    }

    private fun checkScenario(
        expectedRetry: Boolean,
        expectedSchedulingCalls: Int,
        configure: (PrayerTimesRepository, PrayerAlarmScheduler) -> Unit,
    ) {
        runWithManager(configure) { manager, _, id, runs, scheduler ->
            awaitPeriod(manager, id, runs, minimumRuns = 1, shouldRetry = expectedRetry)
            coVerify(exactly = expectedSchedulingCalls) { scheduler.scheduleNextForCurrentFlavor() }
        }
    }

    private fun runWithManager(
        configure: (PrayerTimesRepository, PrayerAlarmScheduler) -> Unit,
        check: (WorkManager, TestDriver, UUID, AtomicInteger, PrayerAlarmScheduler) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val repository = mockk<PrayerTimesRepository>()
        val scheduler = mockk<PrayerAlarmScheduler>()
        val workerRuns = AtomicInteger()
        coEvery { scheduler.scheduleNextForCurrentFlavor() } returns true
        configure(repository, scheduler)
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? {
                if (workerClassName != PrayerTimesRefreshWorker::class.java.name) return null
                return object : CoroutineWorker(appContext, workerParameters) {
                    override suspend fun doWork(): Result {
                        workerRuns.incrementAndGet()
                        return PrayerTimesRefreshExecution.run(
                            repository = repository,
                            scheduler = scheduler,
                            districtId = 42,
                            alarmEnabled = true,
                        )
                    }
                }
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setWorkerCoroutineContext(kotlinx.coroutines.Dispatchers.Default)
                .setMinimumLoggingLevel(Log.ERROR)
                .setTaskExecutor(SynchronousExecutor())
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        val manager = WorkManager.getInstance(context)
        try {
            PrayerTimesRefreshWorker.schedule(context)
            val active = manager.getWorkInfosForUniqueWork(workName).get(15, TimeUnit.SECONDS)
                .filter { it.state == WorkInfo.State.ENQUEUED }
            assertEquals("Exactly one active periodic work", 1, active.size)
            val id = active.single().id
            val driver = WorkManagerTestInitHelper.getTestDriver(context)
            assertNotNull(driver)
            driver!!.setAllConstraintsMet(id)
            driver.setPeriodDelayMet(id)
            check(manager, driver, id, workerRuns, scheduler)
        } finally {
            try {
                manager.cancelUniqueWork(workName).result.get(15, TimeUnit.SECONDS)
                manager.pruneWork().result.get(15, TimeUnit.SECONDS)
            } finally {
                WorkManagerTestInitHelper.closeWorkDatabase()
            }
        }
    }

    private fun awaitPeriod(
        manager: WorkManager,
        id: UUID,
        runs: AtomicInteger,
        minimumRuns: Int,
        shouldRetry: Boolean,
    ) {
        var current: WorkInfo? = null
        for (attempt in 0 until 100) {
            current = manager.getWorkInfoById(id).get(15, TimeUnit.SECONDS)
            if (
                runs.get() >= minimumRuns &&
                current?.state == WorkInfo.State.ENQUEUED &&
                (current.runAttemptCount > 0) == shouldRetry
            ) {
                break
            }
            Thread.sleep(50)
        }
        assertTrue("Periodic worker never executed enough times", runs.get() >= minimumRuns)
        assertEquals(WorkInfo.State.ENQUEUED, current?.state)
        if (shouldRetry) {
            assertTrue("Retry should increase attempt count", (current?.runAttemptCount ?: 0) > 0)
        } else {
            assertEquals("Successful period resets retry count", 0, current?.runAttemptCount)
        }
    }
}
