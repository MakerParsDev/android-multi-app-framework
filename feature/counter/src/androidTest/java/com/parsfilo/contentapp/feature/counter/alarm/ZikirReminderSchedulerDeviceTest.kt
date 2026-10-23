package com.parsfilo.contentapp.feature.counter.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * No real alarm is ever created or cancelled. A separate test APK is used.
 */
@RunWith(AndroidJUnit4::class)
class ZikirReminderSchedulerDeviceTest {
    private val fakePreferences = PreferencesDataSource(
        object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())

            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences = error("Test must not write preferences")
        },
    )

    @Test fun noAlarmServiceCannotMasqueradeAsInexactSuccess() {
        val realContext = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(realContext) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) null else super.getSystemService(name)
        }
        val scheduler = ZikirReminderScheduler(context, fakePreferences)
        assertEquals(ReminderScheduleMode.UNAVAILABLE, scheduler.scheduleDaily(9, 30))
    }

    @Test fun nullPendingIntentCannotMasqueradeAsInexactSuccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alarm = context.getSystemService(AlarmManager::class.java)
        requireNotNull(alarm)
        val scheduler = ZikirReminderScheduler(context, fakePreferences)
        // Returns before AlarmManager.cancel/set: nothing is scheduled.
        assertEquals(ReminderScheduleMode.UNAVAILABLE, scheduler.scheduleDaily(9, 30) { null })
    }
}
