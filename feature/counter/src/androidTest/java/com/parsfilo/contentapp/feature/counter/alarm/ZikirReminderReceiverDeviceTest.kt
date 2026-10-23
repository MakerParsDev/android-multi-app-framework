package com.parsfilo.contentapp.feature.counter.alarm

import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.parsfilo.contentapp.feature.counter.data.ZikirRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real Android broadcast path with injected fake dependencies; no user alarms or notifications.
 */
@RunWith(AndroidJUnit4::class)
class ZikirReminderReceiverDeviceTest {
    @Test fun notifierExceptionStillRearms() = exercise(Fault.NOTIFIER)

    @Test fun daoExceptionStillRearms() = exercise(Fault.DAO)

    @Test fun schedulerExceptionIsContained() = exercise(Fault.SCHEDULER)

    @Test fun normalDeliverySchedulesOnce() = exercise(Fault.NORMAL)

    @Test fun unavailableOrDisabledReminderDoesNotLoop() = exercise(Fault.DISABLED)

    @Test fun durableRecoveryEnqueueDeduplicates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val manager = WorkManager.getInstance(context)
        val workName = "zikir_reminder_recovery"
        try {
            runBlocking {
                ZikirReminderRecoveryWorker.enqueue(context)
                ZikirReminderRecoveryWorker.enqueue(context)
            }
            val entries = manager.getWorkInfosForUniqueWork(workName).get(20, TimeUnit.SECONDS)
            assertEquals(1, entries.size)
            assertEquals(WorkInfo.State.ENQUEUED, entries.single().state)
        } finally {
            manager.cancelUniqueWork(workName).result.get(20, TimeUnit.SECONDS)
        }
    }

    private enum class Fault { NOTIFIER, DAO, SCHEDULER, NORMAL, DISABLED }

    private fun exercise(fault: Fault) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val scheduler = mockk<ZikirReminderScheduler>()
        val notifier = mockk<ZikirReminderNotifier>()
        val repository = mockk<ZikirRepository>()
        val called = CompletableDeferred<Unit>()
        val recoveryCalled = CompletableDeferred<Unit>()
        val recoveryCount = AtomicInteger(0)
        every { repository.getTodayTotalCount() } returns if (fault == Fault.DAO) {
            flow { throw IllegalStateException("synthetic DAO failure") }
        } else {
            flowOf(7)
        }
        coEvery { notifier.showDailyReminder(any()) } coAnswers {
            if (fault == Fault.NOTIFIER) throw SecurityException("synthetic notification denied")
        }
        coEvery { scheduler.scheduleOrCancelFromPreferences() } coAnswers {
            called.complete(Unit)
            if (fault == Fault.SCHEDULER) throw IllegalStateException("synthetic scheduler failure")
            if (fault == Fault.DISABLED) null else ReminderScheduleMode.EXACT
        }
        val receiver = ZikirReminderReceiver().apply {
            dependenciesProvider = {
                ZikirReminderDependencies(scheduler, notifier, repository, enqueueRecovery = {
                    recoveryCount.incrementAndGet()
                    recoveryCalled.complete(Unit)
                })
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ZikirReminderScheduler.ACTION_ZIKIR_REMINDER),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            context.sendBroadcast(Intent(ZikirReminderScheduler.ACTION_ZIKIR_REMINDER).setPackage(context.packageName))
            runBlocking { withTimeout(9_000L) { called.await() } }
            if (fault == Fault.SCHEDULER) {
                runBlocking { withTimeout(9_000L) { recoveryCalled.await() } }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(if (fault == Fault.SCHEDULER) 1 else 0, recoveryCount.get())
            coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
            coVerify(exactly = if (fault == Fault.DAO) 0 else 1) {
                notifier.showDailyReminder(any())
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
