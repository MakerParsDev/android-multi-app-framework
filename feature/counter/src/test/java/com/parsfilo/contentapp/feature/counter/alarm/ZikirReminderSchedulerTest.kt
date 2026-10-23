package com.parsfilo.contentapp.feature.counter.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class ZikirReminderSchedulerTest {
    private val preferences = mockk<PreferencesDataSource>()

    @Test fun missingAlarmManagerIsUnavailable() {
        val scheduler = ZikirReminderScheduler(contextWith(null), preferences)
        assertThat(scheduler.isAlarmServiceAvailable()).isFalse()
        assertThat(scheduler.scheduleDaily(9, 30)).isEqualTo(ReminderScheduleMode.UNAVAILABLE)
    }

    @Test fun missingPendingIntentIsUnavailableWithoutAlarmWrite() {
        val alarmManager = mockk<AlarmManager>(relaxed = true)
        val scheduler = ZikirReminderScheduler(contextWith(alarmManager), preferences)
        assertThat(scheduler.isAlarmServiceAvailable()).isTrue()
        val result = scheduler.scheduleDaily(9, 30) { null }
        assertThat(result).isEqualTo(ReminderScheduleMode.UNAVAILABLE)
        verify(exactly = 0) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test fun actualExactSchedulingIsDistinctFromUnavailable() {
        val alarmManager = mockk<AlarmManager>(relaxed = true)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            every { alarmManager.canScheduleExactAlarms() } returns true
        }
        val pendingIntent = mockk<PendingIntent>()
        val scheduler = ZikirReminderScheduler(contextWith(alarmManager), preferences)
        assertThat(scheduler.scheduleDaily(9, 30) { pendingIntent }).isEqualTo(ReminderScheduleMode.EXACT)
        verify(exactly = 1) { alarmManager.setExactAndAllowWhileIdle(any(), any(), pendingIntent) }
        verify(exactly = 0) { alarmManager.setAndAllowWhileIdle(any(), any(), pendingIntent) }
    }

    @Test fun inexactFallbackRequiresRealAlarmManagerCall() {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        val alarmManager = mockk<AlarmManager>(relaxed = true)
        every { alarmManager.canScheduleExactAlarms() } returns false
        val pendingIntent = mockk<PendingIntent>()
        val scheduler = ZikirReminderScheduler(contextWith(alarmManager), preferences)
        assertThat(scheduler.scheduleDaily(9, 30) { pendingIntent }).isEqualTo(ReminderScheduleMode.INEXACT_FALLBACK)
        verify(exactly = 1) { alarmManager.setAndAllowWhileIdle(any(), any(), pendingIntent) }
    }

    @Test fun schedulerExceptionIsNotReportedAsSuccess() {
        val alarmManager = mockk<AlarmManager>(relaxed = true)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            every { alarmManager.canScheduleExactAlarms() } returns true
        }
        every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) } throws
            IllegalStateException("synthetic schedule failure")
        val scheduler = ZikirReminderScheduler(contextWith(alarmManager), preferences)
        try {
            scheduler.scheduleDaily(9, 30) { mockk() }
            throw AssertionError("Scheduler exception must propagate")
        } catch (error: IllegalStateException) {
            assertThat(error).hasMessageThat().contains("synthetic")
        }
    }

    @Test fun disabledReminderDoesNotAttemptScheduling() = runTest {
        every { preferences.zikirReminderEnabled } returns flowOf(false)
        val scheduler = ZikirReminderScheduler(contextWith(null), preferences)
        assertThat(scheduler.scheduleOrCancelFromPreferences()).isNull()
    }

    private fun contextWith(alarmManager: AlarmManager?): Context {
        val original = RuntimeEnvironment.getApplication()
        return object : ContextWrapper(original) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) alarmManager else super.getSystemService(name)
        }
    }
}
