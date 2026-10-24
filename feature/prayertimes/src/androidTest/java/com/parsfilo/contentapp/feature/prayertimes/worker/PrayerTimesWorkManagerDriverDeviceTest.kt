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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests the real periodic WorkManager request and retry machinery on the device.
 * A test WorkerFactory replaces only dependency wiring with synthetic data.
 * No live server, saved location, notification, or AlarmManager is touched.
 */
@RunWith(AndroidJUnit4::class)
class PrayerTimesWorkManagerDriverDeviceTest {
    @Test
    fun periodicFailureKeepsWorkScheduledAndDoesNotArmAlarm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val repository = mockk<PrayerTimesRepository>()
        val scheduler = mockk<PrayerAlarmScheduler>()
        val workerRuns = AtomicInteger()
        coEvery { repository.refreshIfNeeded(42, false, false) } returns
            RefreshResult.Failed(IOException("synthetic HTTP 503"))
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
        val uniqueName = "prayer_times_daily_refresh"
        try {
            PrayerTimesRefreshWorker.schedule(context)
            val scheduled = manager.getWorkInfosForUniqueWork(uniqueName).get(15, TimeUnit.SECONDS)
            assertEquals(1, scheduled.size)
            val workId = scheduled.single().id
            val driver = WorkManagerTestInitHelper.getTestDriver(context)
            assertNotNull(driver)
            driver!!.setAllConstraintsMet(workId)
            driver.setPeriodDelayMet(workId)
            var current: WorkInfo? = null
            for (attempt in 0 until 70) {
                current = manager.getWorkInfoById(workId).get(15, TimeUnit.SECONDS)
                if (workerRuns.get() > 0 && current?.state == WorkInfo.State.ENQUEUED) break
                Thread.sleep(50)
            }
            assertTrue("Periodic worker never executed", workerRuns.get() > 0)
            assertEquals(WorkInfo.State.ENQUEUED, current?.state)
            assertTrue("Retry should increase attempt count", (current?.runAttemptCount ?: 0) > 0)
            coVerify(exactly = 0) { scheduler.scheduleNextForCurrentFlavor() }
        } finally {
            try {
                manager.cancelUniqueWork(uniqueName).result.get(15, TimeUnit.SECONDS)
            } finally {
                WorkManagerTestInitHelper.closeWorkDatabase()
            }
        }
    }
}
