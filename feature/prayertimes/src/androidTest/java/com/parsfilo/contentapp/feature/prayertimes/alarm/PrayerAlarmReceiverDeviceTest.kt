package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesData
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hardware-/emulator-backed BroadcastReceiver test with strictly injected fakes.
 * No real scheduler/notification service is ever invoked. The test APK is disposable.
 */
@RunWith(AndroidJUnit4::class)
class PrayerAlarmReceiverDeviceTest {
    @Test fun notifierExceptionStillRearms() = exercise(Fault.NOTIFIER)

    @Test fun preferencesExceptionStillRearms() = exercise(Fault.PREFERENCES)

    @Test fun schedulerExceptionIsContained() = exercise(Fault.SCHEDULER)

    @Test fun normalDeliverySchedulesOnce() = exercise(Fault.NORMAL)

    @Test fun unavailableOrDisabledReminderDoesNotLoop() = exercise(Fault.DISABLED)

    private enum class Fault { NOTIFIER, PREFERENCES, SCHEDULER, NORMAL, DISABLED }

    private fun exercise(fault: Fault) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val scheduler = mockk<PrayerAlarmScheduler>()
        val notifier = mockk<PrayerAlarmNotifier>()
        val preferences = mockk<PrayerPreferencesDataSource>()
        val data = mockk<PrayerPreferencesData>()
        val called = CompletableDeferred<Unit>()
        every { data.alarmSoundUri } returns null
        every { preferences.preferences } returns if (fault == Fault.PREFERENCES) {
            flow { throw IllegalStateException("synthetic preferences failure") }
        } else {
            flowOf(data)
        }
        coEvery { notifier.show(any(), any(), any()) } coAnswers {
            if (fault == Fault.NOTIFIER) throw SecurityException("synthetic notification denied")
        }
        coEvery { scheduler.scheduleNext(any()) } coAnswers {
            called.complete(Unit)
            if (fault == Fault.SCHEDULER) throw IllegalStateException("synthetic scheduler failure")
            fault != Fault.DISABLED
        }
        val receiver = PrayerAlarmReceiver().apply {
            dependenciesProvider = { PrayerAlarmDependencies(scheduler, notifier, preferences) }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PrayerAlarmScheduler.ACTION_FIRE_ALARM),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            val intent = Intent(PrayerAlarmScheduler.ACTION_FIRE_ALARM)
                .setPackage(context.packageName)
                .putExtra(PrayerAlarmScheduler.EXTRA_VARIANT, PrayerAppVariant.NAMAZ_VAKITLERI.name)
            context.sendBroadcast(intent)
            runBlocking { withTimeout(9_000L) { called.await() } }
            // Wait for the receiver's bounded finalization before checking exact invocation counts.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            coVerify(exactly = 1) { scheduler.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI) }
            coVerify(exactly = if (fault == Fault.PREFERENCES) 0 else 1) {
                notifier.show(any(), any(), any())
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
