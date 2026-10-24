package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.database.dao.prayer.PrayerTimesDao
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesData
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Exercises real AlarmManager only in this disposable instrumented module package.
 * No user app, real location, API, existing notification, or alarm is touched.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30) // Android 9 instrumented MockK crashes natively on SM-G611F; tested by Robolectric instead.
class PrayerAlarmRearmDeviceTest {
    private val app: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val prefs = mockk<PrayerPreferencesDataSource>()
    private val dao = mockk<PrayerTimesDao>()
    private val analytics = mockk<AppAnalytics>(relaxed = true)

    private fun scheduler(context: Context = app) = PrayerAlarmScheduler(
        context = context,
        prayerTimesDao = dao,
        prayerPreferencesDataSource = prefs,
        appAnalytics = analytics,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun selectPrayer(key: String) {
        every { prefs.preferences } returns flowOf(
            PrayerPreferencesData(
                mode = "MANUAL",
                selectedCountryId = 1,
                selectedCityId = 2,
                selectedDistrictId = 42,
                lastAutoDistrictId = null,
                locationPermissionPrompted = false,
                lastSuccessfulRefreshAt = 0L,
                alarmEnabled = true,
                alarmOffsetMinutes = 0,
                selectedAlarmPrayerKeys = setOf(key),
                alarmSoundUri = null,
            ),
        )
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        coEvery { dao.getPrayerTimes(eq(42), any(), any()) } returns listOf(
            PrayerTimeEntity(
                districtId = 42,
                localDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.time),
                imsak = "05:00",
                gunes = "06:00",
                ogle = "12:00",
                ikindi = "15:00",
                aksam = "18:00",
                yatsi = "21:00",
                fetchedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun pendingFor(requestCode: Int): PendingIntent? {
        val intent = Intent(app, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmScheduler.ACTION_FIRE_ALARM
        }
        return PendingIntent.getBroadcast(
            app,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
    }

    private fun checkIsolated() {
        assertFalse("Must never test on installed user's real app", app.packageName == "com.parsfilo.namazvakitleri")
    }

    @Test
    fun armImsakAfterYatsiDoesNotCancelImsakWithLegacyCleanup() = runBlocking {
        checkIsolated()
        val alarm = scheduler()
        alarm.cancelScheduledAlarm()
        try {
            selectPrayer(PrayerAlarmScheduler.PRAYER_YATSI)
            assertTrue(alarm.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI))
            assertNotNull(pendingFor(PrayerAlarmScheduler.REQUEST_CODE_YATSI))

            selectPrayer(PrayerAlarmScheduler.PRAYER_IMSAK)
            assertTrue(alarm.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI))
            assertNotNull(pendingFor(PrayerAlarmScheduler.REQUEST_CODE_IMSAK))
            assertNull(pendingFor(PrayerAlarmScheduler.REQUEST_CODE_YATSI))
        } finally {
            alarm.cancelScheduledAlarm()
        }
    }

    @Test
    fun failedSwitchPreservesPriorAlarmPendingIntent() = runBlocking {
        checkIsolated()
        val realScheduler = scheduler()
        realScheduler.cancelScheduledAlarm()
        try {
            selectPrayer(PrayerAlarmScheduler.PRAYER_YATSI)
            assertTrue(realScheduler.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI))
            assertNotNull(pendingFor(PrayerAlarmScheduler.REQUEST_CODE_YATSI))

            val fakeAlarmManager = mockk<AlarmManager>(relaxed = true)
            val realAlarmManager = app.getSystemService(AlarmManager::class.java)
            every { fakeAlarmManager.setAndAllowWhileIdle(any(), any(), any()) } throws
                SecurityException("synthetic failure")
            every { fakeAlarmManager.cancel(any<PendingIntent>()) } answers {
                realAlarmManager.cancel(firstArg<PendingIntent>())
            }
            val failingContext = object : ContextWrapper(app) {
                override fun getSystemService(name: String): Any? =
                    if (name == Context.ALARM_SERVICE) fakeAlarmManager else super.getSystemService(name)
            }
            selectPrayer(PrayerAlarmScheduler.PRAYER_IKINDI)
            try {
                scheduler(failingContext).scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)
                throw AssertionError("Expected platform failure")
            } catch (expected: SecurityException) {
                assertTrue(expected.message.orEmpty().contains("synthetic failure"))
            }
            assertNotNull(pendingFor(PrayerAlarmScheduler.REQUEST_CODE_YATSI))
        } finally {
            realScheduler.cancelScheduledAlarm()
        }
    }
}
