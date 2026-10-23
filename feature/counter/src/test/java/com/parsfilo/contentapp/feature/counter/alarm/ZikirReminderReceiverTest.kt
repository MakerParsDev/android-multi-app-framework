package com.parsfilo.contentapp.feature.counter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.counter.data.ZikirRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBroadcastPendingResult
import org.robolectric.shadows.ShadowBroadcastReceiver

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class ZikirReminderReceiverTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun notificationFailureDoesNotStopRearm() = runTest {
        val (receiver, scheduler, notifier, _) = createReceiver(this)
        coEvery { notifier.showDailyReminder(any()) } throws SecurityException("synthetic denied")
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        assertFinished(shadow)
    }

    @Test fun daoFailureDoesNotStopRearm() = runTest {
        val (receiver, scheduler, notifier, repo) = createReceiver(this)
        every { repo.getTodayTotalCount() } returns flow { throw IllegalStateException("synthetic DAO failure") }
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 0) { notifier.showDailyReminder(any()) }
        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        assertFinished(shadow)
    }

    @Test fun unavailableScheduleRequestsRecoveryOnce() = runTest {
        val (receiver, scheduler, notifier, repo) = createReceiver(this)
        val recoveryCalls = java.util.concurrent.atomic.AtomicInteger()
        receiver.dependenciesProvider = {
            ZikirReminderDependencies(scheduler, notifier, repo, enqueueRecovery = {
                recoveryCalls.incrementAndGet()
            })
        }
        coEvery { scheduler.scheduleOrCancelFromPreferences() } returns ReminderScheduleMode.UNAVAILABLE
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        assertThat(recoveryCalls.get()).isEqualTo(1)
        assertFinished(shadow)
    }

    @Test fun successfulInexactScheduleDoesNotRequestRecovery() = runTest {
        val (receiver, scheduler, notifier, repo) = createReceiver(this)
        val recoveryCalls = java.util.concurrent.atomic.AtomicInteger()
        receiver.dependenciesProvider = {
            ZikirReminderDependencies(scheduler, notifier, repo, enqueueRecovery = {
                recoveryCalls.incrementAndGet()
            })
        }
        coEvery { scheduler.scheduleOrCancelFromPreferences() } returns ReminderScheduleMode.INEXACT_FALLBACK
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        assertThat(recoveryCalls.get()).isEqualTo(0)
        assertFinished(shadow)
    }

    @Test fun normalDeliveryRearmsOnlyOnce() = runTest {
        val (receiver, scheduler, notifier, _) = createReceiver(this)
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { notifier.showDailyReminder(7) }
        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        assertFinished(shadow)
    }

    @Test fun schedulerFailureStillFinishesPendingResult() = runTest {
        val (receiver, scheduler, notifier, repo) = createReceiver(this)
        val recoveryCalls = java.util.concurrent.atomic.AtomicInteger(0)
        receiver.dependenciesProvider = {
            ZikirReminderDependencies(scheduler, notifier, repo, enqueueRecovery = {
                recoveryCalls.incrementAndGet()
            })
        }
        coEvery { scheduler.scheduleOrCancelFromPreferences() } throws IllegalStateException("synthetic failure")
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        assertThat(recoveryCalls.get()).isEqualTo(1)
        assertFinished(shadow)
    }

    @Test fun enqueueFailureDoesNotLeakFromReceiver() = runTest {
        val (receiver, scheduler, notifier, repo) = createReceiver(this)
        receiver.dependenciesProvider = {
            ZikirReminderDependencies(scheduler, notifier, repo, enqueueRecovery = {
                throw IllegalStateException("synthetic WorkManager failure")
            })
        }
        coEvery { scheduler.scheduleOrCancelFromPreferences() } throws IllegalStateException("rearm failed")
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        assertFinished(shadow)
    }

    @Test fun stalledRecoveryEnqueueIsBounded() = runTest {
        val (receiver, scheduler, notifier, repo) = createReceiver(this)
        receiver.dependenciesProvider = {
            ZikirReminderDependencies(scheduler, notifier, repo, enqueueRecovery = {
                CompletableDeferred<Unit>().await()
            })
        }
        coEvery { scheduler.scheduleOrCancelFromPreferences() } throws IllegalStateException("rearm failed")
        val shadow = dispatch(receiver)
        runCurrent()
        advanceTimeBy(ZikirReminderReceiver.RECOVERY_ENQUEUE_TIMEOUT_MS)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        assertFinished(shadow)
    }

    @Test fun slowNotificationTimesOutButRearms() = runTest {
        val (receiver, scheduler, notifier, _) = createReceiver(this)
        coEvery { notifier.showDailyReminder(any()) } coAnswers { CompletableDeferred<Unit>().await() }
        val shadow = dispatch(receiver)
        runCurrent()
        advanceTimeBy(ZikirReminderReceiver.NOTIFICATION_TIMEOUT_MS)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        assertFinished(shadow)
    }

    @Test fun disabledReminderCancelsWithoutNotificationFailure() = runTest {
        val (receiver, scheduler, _, _) = createReceiver(this)
        coEvery { scheduler.scheduleOrCancelFromPreferences() } returns null
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        assertFinished(shadow)
    }

    @Test fun stalledSchedulerTimesOutAndFinishesPendingResult() = runTest {
        val (receiver, scheduler, notifier, repo) = createReceiver(this)
        val recoveryCalls = java.util.concurrent.atomic.AtomicInteger(0)
        receiver.dependenciesProvider = {
            ZikirReminderDependencies(scheduler, notifier, repo, enqueueRecovery = {
                recoveryCalls.incrementAndGet()
            })
        }
        coEvery { scheduler.scheduleOrCancelFromPreferences() } coAnswers {
            CompletableDeferred<ReminderScheduleMode?>().await()
        }
        val shadow = dispatch(receiver)
        runCurrent()
        advanceTimeBy(ZikirReminderReceiver.SCHEDULING_TIMEOUT_MS)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        assertThat(recoveryCalls.get()).isEqualTo(1)
        assertFinished(shadow)
    }

    private data class Dependencies(
        val receiver: ZikirReminderReceiver,
        val scheduler: ZikirReminderScheduler,
        val notifier: ZikirReminderNotifier,
        val repository: ZikirRepository,
    )

    private fun createReceiver(scope: TestScope): Dependencies {
        val scheduler = mockk<ZikirReminderScheduler>()
        val notifier = mockk<ZikirReminderNotifier>()
        val repo = mockk<ZikirRepository>()
        every { repo.getTodayTotalCount() } returns flowOf(7)
        coEvery { notifier.showDailyReminder(any()) } returns Unit
        coEvery { scheduler.scheduleOrCancelFromPreferences() } returns ReminderScheduleMode.EXACT
        val receiver = ZikirReminderReceiver().apply {
            dependenciesProvider = { ZikirReminderDependencies(scheduler, notifier, repo) }
            ioDispatcher = StandardTestDispatcher(scope.testScheduler)
        }
        return Dependencies(receiver, scheduler, notifier, repo)
    }

    private fun dispatch(receiver: BroadcastReceiver): ShadowBroadcastReceiver {
        val pending = ShadowBroadcastPendingResult::class.java.getDeclaredMethod(
            "create", Int::class.javaPrimitiveType, String::class.java,
            Bundle::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(null, 0, null, null, false) as BroadcastReceiver.PendingResult
        BroadcastReceiver::class.java.getMethod(
            "setPendingResult", BroadcastReceiver.PendingResult::class.java,
        ).invoke(receiver, pending)
        return shadowOf(receiver).also {
            it.onReceive(context, Intent(ZikirReminderScheduler.ACTION_ZIKIR_REMINDER), java.util.concurrent.atomic.AtomicBoolean())
        }
    }

    private fun assertFinished(shadow: ShadowBroadcastReceiver) {
        assertThat(shadowOf(shadow.originalPendingResult).future.isDone).isTrue()
    }
}
