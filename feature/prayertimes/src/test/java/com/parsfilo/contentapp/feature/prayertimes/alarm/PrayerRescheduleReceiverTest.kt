package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.common.broadcast.SystemBroadcastRescheduleGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
@Config(sdk = [24, 36])
class PrayerRescheduleReceiverTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `spoofed action is ignored before async work starts`() = runTest {
        val scheduler = mockk<PrayerAlarmScheduler>(relaxed = true)
        val receiver = receiver(scheduler, this)
        val shadow = shadowOf(receiver)

        dispatch(receiver, Intent("com.attacker.FORCE_RESCHEDULE"))
        advanceUntilIdle()

        assertThat(shadow.wentAsync()).isFalse()
        coVerify(exactly = 0) { scheduler.scheduleNextForCurrentFlavor() }
    }

    @Test
    fun `all supported system actions schedule and finish`() = runTest {
        SUPPORTED_ACTIONS.forEach { action ->
            val scheduler = mockk<PrayerAlarmScheduler>(relaxed = true)
            val receiver = receiver(scheduler, this)
            val shadow = shadowOf(receiver)

            dispatch(receiver, Intent(action))
            advanceUntilIdle()

            coVerify(exactly = 1) { scheduler.scheduleNextForCurrentFlavor() }
            assertThat(shadowOf(shadow.originalPendingResult).future.isDone).isTrue()
        }
    }

    @Test
    fun `timeout still finishes pending result`() = runTest {
        val scheduler = mockk<PrayerAlarmScheduler>(relaxed = true)
        coEvery { scheduler.scheduleNextForCurrentFlavor() } coAnswers {
            CompletableDeferred<Unit>().await()
            false
        }
        val receiver = receiver(scheduler, this)
        val shadow = shadowOf(receiver)

        dispatch(receiver, Intent(Intent.ACTION_BOOT_COMPLETED))
        advanceTimeBy(PrayerRescheduleReceiver.RECEIVER_TIMEOUT_MS)
        advanceUntilIdle()

        assertThat(shadowOf(shadow.originalPendingResult).future.isDone).isTrue()
    }

    @Test
    fun `scheduler failure still finishes pending result`() = runTest {
        val scheduler = mockk<PrayerAlarmScheduler>(relaxed = true)
        coEvery { scheduler.scheduleNextForCurrentFlavor() } throws IllegalStateException("test failure")
        val receiver = receiver(scheduler, this)
        val shadow = shadowOf(receiver)

        dispatch(receiver, Intent(Intent.ACTION_BOOT_COMPLETED))
        advanceUntilIdle()

        assertThat(shadowOf(shadow.originalPendingResult).future.isDone).isTrue()
    }

    private fun receiver(
        scheduler: PrayerAlarmScheduler,
        scope: TestScope,
    ) = PrayerRescheduleReceiver().apply {
        dependenciesProvider = {
            PrayerRescheduleDependencies(
                scheduler = scheduler,
                ioDispatcher = StandardTestDispatcher(scope.testScheduler),
                rescheduleGate = SystemBroadcastRescheduleGate(),
            )
        }
    }

    private fun dispatch(
        receiver: BroadcastReceiver,
        intent: Intent,
    ): ShadowBroadcastReceiver {
        val pendingResult = createPendingResult()
        BroadcastReceiver::class.java
            .getMethod("setPendingResult", BroadcastReceiver.PendingResult::class.java)
            .invoke(receiver, pendingResult)
        return shadowOf(receiver).also { shadow ->
            shadow.onReceive(context, intent, java.util.concurrent.atomic.AtomicBoolean())
        }
    }

    private fun createPendingResult(): BroadcastReceiver.PendingResult {
        val create = ShadowBroadcastPendingResult::class.java.getDeclaredMethod(
            "create",
            Int::class.javaPrimitiveType,
            String::class.java,
            Bundle::class.java,
            Boolean::class.javaPrimitiveType,
        )
        create.isAccessible = true
        return create.invoke(null, 0, null, null, false) as BroadcastReceiver.PendingResult
    }

    private companion object {
        val SUPPORTED_ACTIONS = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
