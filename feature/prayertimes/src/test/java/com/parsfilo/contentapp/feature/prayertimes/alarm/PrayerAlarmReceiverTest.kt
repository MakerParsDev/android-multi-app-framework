package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesData
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.datastore.UserPreferencesData
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
class PrayerAlarmReceiverTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun notificationFailureDoesNotStopRearm() = runTest {
        val (receiver, scheduler, notifier, _) = createReceiver(this)
        coEvery { notifier.show(any(), any(), any()) } throws SecurityException("synthetic denied")
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleNext(any()) }
        assertFinished(shadow)
    }

    @Test fun preferencesFailureDoesNotStopRearm() = runTest {
        val (receiver, scheduler, notifier, prefs) = createReceiver(this)
        every { prefs.preferences } returns flow { throw IllegalStateException("synthetic failure") }
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 0) { notifier.show(any(), any(), any()) }
        coVerify(exactly = 1) { scheduler.scheduleNext(any()) }
        assertFinished(shadow)
    }

    @Test fun successfulDeliveryRearmsOnlyOnce() = runTest {
        val (receiver, scheduler, notifier, _) = createReceiver(this)
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { notifier.show(any(), any(), any()) }
        coVerify(exactly = 1) { scheduler.scheduleNext(any()) }
        assertFinished(shadow)
    }

    /** Covers the production notifier-to-scheduler sequence without using a real user alarm. */
    @Config(sdk = [28]) // No Android 13 notification permission required for this isolated Robolectric test.
    @Test fun realNotificationIsPostedWhileNextSchedulingStillRuns() = runTest {
        val (receiver, scheduler, _, prayerPrefs) = createReceiver(this)
        val userPrefs = mockk<PreferencesDataSource>()
        val userData = mockk<UserPreferencesData>()
        every { userData.notificationsEnabled } returns true
        every { userPrefs.userData } returns flowOf(userData)
        val sound = mockk<PrayerAlarmSoundPlayer>(relaxed = true)
        val notifier = PrayerAlarmNotifier(context, userPrefs, sound)
        receiver.dependenciesProvider = {
            PrayerAlarmDependencies(scheduler, notifier, prayerPrefs)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
        try {
            val shadow = dispatch(receiver)
            advanceUntilIdle()
            val notificationIds = manager.activeNotifications.map { it.id }
            assertThat(notificationIds).contains(41_106)
            assertThat(notificationIds).contains(41_099)
            coVerify(exactly = 1) { scheduler.scheduleNext(any()) }
            assertFinished(shadow)
        } finally {
            manager.cancelAll()
        }
    }

    @Test fun schedulerExceptionStillFinishesPendingResult() = runTest {
        val (receiver, scheduler, notifier, prefs) = createReceiver(this)
        val recoveryCalls = java.util.concurrent.atomic.AtomicInteger(0)
        receiver.dependenciesProvider = {
            PrayerAlarmDependencies(scheduler, notifier, prefs, enqueueRecovery = {
                recoveryCalls.incrementAndGet()
            })
        }
        coEvery { scheduler.scheduleNext(any()) } throws IllegalStateException("synthetic failure")
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleNext(any()) }
        assertThat(recoveryCalls.get()).isEqualTo(1)
        assertFinished(shadow)
    }

    @Test fun enqueueFailureDoesNotLeakFromReceiver() = runTest {
        val (receiver, scheduler, notifier, prefs) = createReceiver(this)
        receiver.dependenciesProvider = {
            PrayerAlarmDependencies(scheduler, notifier, prefs, enqueueRecovery = {
                throw IllegalStateException("synthetic WorkManager failure")
            })
        }
        coEvery { scheduler.scheduleNext(any()) } throws IllegalStateException("rearm failed")
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleNext(any()) }
        assertFinished(shadow)
    }

    @Test fun stalledRecoveryEnqueueIsBounded() = runTest {
        val (receiver, scheduler, notifier, prefs) = createReceiver(this)
        receiver.dependenciesProvider = {
            PrayerAlarmDependencies(scheduler, notifier, prefs, enqueueRecovery = {
                CompletableDeferred<Unit>().await()
            })
        }
        coEvery { scheduler.scheduleNext(any()) } throws IllegalStateException("rearm failed")
        val shadow = dispatch(receiver)
        runCurrent()
        advanceTimeBy(PrayerAlarmReceiver.RECOVERY_ENQUEUE_TIMEOUT_MS)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleNext(any()) }
        assertFinished(shadow)
    }

    @Test fun slowNotificationTimesOutButRearms() = runTest {
        val (receiver, scheduler, notifier, _) = createReceiver(this)
        coEvery { notifier.show(any(), any(), any()) } coAnswers { CompletableDeferred<Unit>().await() }
        val shadow = dispatch(receiver)
        runCurrent()
        advanceTimeBy(PrayerAlarmReceiver.NOTIFICATION_TIMEOUT_MS)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleNext(any()) }
        assertFinished(shadow)
    }

    @Test fun disabledOrUnavailableSchedulingStillFinishesPendingResult() = runTest {
        val (receiver, scheduler, _, _) = createReceiver(this)
        coEvery { scheduler.scheduleNext(any()) } returns false
        val shadow = dispatch(receiver)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleNext(any()) }
        assertFinished(shadow)
    }

    @Test fun stalledSchedulerTimesOutAndFinishesPendingResult() = runTest {
        val (receiver, scheduler, notifier, prefs) = createReceiver(this)
        val recoveryCalls = java.util.concurrent.atomic.AtomicInteger(0)
        receiver.dependenciesProvider = {
            PrayerAlarmDependencies(scheduler, notifier, prefs, enqueueRecovery = {
                recoveryCalls.incrementAndGet()
            })
        }
        coEvery { scheduler.scheduleNext(any()) } coAnswers {
            CompletableDeferred<Boolean>().await()
        }
        val shadow = dispatch(receiver)
        runCurrent()
        advanceTimeBy(PrayerAlarmReceiver.SCHEDULING_TIMEOUT_MS)
        advanceUntilIdle()
        coVerify(exactly = 1) { scheduler.scheduleNext(any()) }
        assertThat(recoveryCalls.get()).isEqualTo(1)
        assertFinished(shadow)
    }

    private data class Dependencies(
        val receiver: PrayerAlarmReceiver,
        val scheduler: PrayerAlarmScheduler,
        val notifier: PrayerAlarmNotifier,
        val preferences: PrayerPreferencesDataSource,
    )

    private fun createReceiver(scope: TestScope): Dependencies {
        val scheduler = mockk<PrayerAlarmScheduler>()
        val notifier = mockk<PrayerAlarmNotifier>()
        val prefs = mockk<PrayerPreferencesDataSource>()
        val data = mockk<PrayerPreferencesData>()
        every { data.alarmSoundUri } returns null
        every { prefs.preferences } returns flowOf(data)
        coEvery { notifier.show(any(), any(), any()) } returns Unit
        coEvery { scheduler.scheduleNext(any()) } returns true
        val receiver = PrayerAlarmReceiver().apply {
            dependenciesProvider = { PrayerAlarmDependencies(scheduler, notifier, prefs) }
            ioDispatcher = StandardTestDispatcher(scope.testScheduler)
        }
        return Dependencies(receiver, scheduler, notifier, prefs)
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
            it.onReceive(context, Intent(PrayerAlarmScheduler.ACTION_FIRE_ALARM), java.util.concurrent.atomic.AtomicBoolean())
        }
    }

    private fun assertFinished(shadow: ShadowBroadcastReceiver) {
        assertThat(shadowOf(shadow.originalPendingResult).future.isDone).isTrue()
    }
}
