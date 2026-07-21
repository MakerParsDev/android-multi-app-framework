package com.parsfilo.contentapp.feature.counter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.common.broadcast.SystemBroadcastRescheduleGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@Config(sdk = [24, 36])
class ZikirSystemBroadcastReceiverTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `spoofed action is ignored before async work starts`() = runTest {
        val scheduler = mockk<ZikirReminderScheduler>(relaxed = true)
        val receiver = receiver(scheduler, this)
        val shadow = shadowOf(receiver)

        dispatch(receiver, Intent("com.attacker.FORCE_RESCHEDULE"))
        advanceUntilIdle()

        assertThat(shadow.wentAsync()).isFalse()
        coVerify(exactly = 0) { scheduler.scheduleOrCancelFromPreferences() }
        verify(exactly = 0) { scheduler.scheduleStreakCheckWorker() }
    }

    @Test
    fun `all supported system actions schedule and finish`() = runTest {
        SUPPORTED_ACTIONS.forEach { action ->
            val scheduler = mockk<ZikirReminderScheduler>(relaxed = true)
            val receiver = receiver(scheduler, this)
            val shadow = shadowOf(receiver)

            dispatch(receiver, Intent(action))
            advanceUntilIdle()

            coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
            verify(exactly = 1) { scheduler.scheduleStreakCheckWorker() }
            assertThat(shadowOf(shadow.originalPendingResult).future.isDone).isTrue()
        }
    }

    @Test
    fun `repeated broadcasts share one in-flight reschedule`() = runTest {
        val scheduler = mockk<ZikirReminderScheduler>(relaxed = true)
        val release = CompletableDeferred<Unit>()
        coEvery { scheduler.scheduleOrCancelFromPreferences() } coAnswers {
            release.await()
            null
        }
        val gate = SystemBroadcastRescheduleGate()
        val first = receiver(scheduler, this, gate)
        val second = receiver(scheduler, this, gate)
        val firstShadow = shadowOf(first)
        val secondShadow = shadowOf(second)

        dispatch(first, Intent(Intent.ACTION_TIME_CHANGED))
        dispatch(second, Intent(Intent.ACTION_TIME_CHANGED))
        runCurrent()

        coVerify(exactly = 1) { scheduler.scheduleOrCancelFromPreferences() }
        release.complete(Unit)
        advanceUntilIdle()

        verify(exactly = 1) { scheduler.scheduleStreakCheckWorker() }
        assertThat(shadowOf(firstShadow.originalPendingResult).future.isDone).isTrue()
        assertThat(shadowOf(secondShadow.originalPendingResult).future.isDone).isTrue()
    }

    @Test
    fun `timeout still finishes pending result`() = runTest {
        val scheduler = mockk<ZikirReminderScheduler>(relaxed = true)
        coEvery { scheduler.scheduleOrCancelFromPreferences() } coAnswers {
            CompletableDeferred<Unit>().await()
            null
        }
        val receiver = receiver(scheduler, this)
        val shadow = shadowOf(receiver)

        dispatch(receiver, Intent(Intent.ACTION_BOOT_COMPLETED))
        runCurrent()
        advanceTimeBy(ZikirSystemBroadcastReceiver.RECEIVER_TIMEOUT_MS)
        runCurrent()

        assertThat(shadowOf(shadow.originalPendingResult).future.isDone).isTrue()
        verify(exactly = 0) { scheduler.scheduleStreakCheckWorker() }
    }

    @Test
    fun `scheduler failure still finishes pending result`() = runTest {
        val scheduler = mockk<ZikirReminderScheduler>(relaxed = true)
        coEvery { scheduler.scheduleOrCancelFromPreferences() } throws IllegalStateException("test failure")
        val receiver = receiver(scheduler, this)
        val shadow = shadowOf(receiver)

        dispatch(receiver, Intent(Intent.ACTION_BOOT_COMPLETED))
        advanceUntilIdle()

        assertThat(shadowOf(shadow.originalPendingResult).future.isDone).isTrue()
        verify(exactly = 0) { scheduler.scheduleStreakCheckWorker() }
    }

    private fun receiver(
        scheduler: ZikirReminderScheduler,
        scope: TestScope,
        gate: SystemBroadcastRescheduleGate = SystemBroadcastRescheduleGate(),
    ) = ZikirSystemBroadcastReceiver().apply {
        dependenciesProvider = {
            ZikirSystemBroadcastDependencies(
                scheduler = scheduler,
                ioDispatcher = StandardTestDispatcher(scope.testScheduler),
                rescheduleGate = gate,
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
