package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.dao.prayer.PrayerTimesDao
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesData
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrayerAlarmExactDeliveryPolicyTest {
    private val app: Context = RuntimeEnvironment.getApplication()

    @Test
    fun grantedExactAccessUsesSingleExactSystemAlarm() = runTest {
        verifyScheduling(exactAccess = true, securityFailure = false, exactCalls = 1, inexactCalls = 0)
    }

    @Test
    fun deniedExactAccessPreservesScheduledBestEffortReminder() = runTest {
        verifyScheduling(exactAccess = false, securityFailure = false, exactCalls = 0, inexactCalls = 1)
    }

    @Test
    fun accessRevokedDuringSetFallsBackWithoutLosingReminder() = runTest {
        verifyScheduling(exactAccess = true, securityFailure = true, exactCalls = 1, inexactCalls = 1)
    }

    @Test
    @Config(sdk = [28])
    fun androidNineUsesExactWithoutAnySpecialAccessPrompt() = runTest {
        verifyScheduling(exactAccess = false, securityFailure = false, exactCalls = 1, inexactCalls = 0)
    }

    private suspend fun verifyScheduling(
        exactAccess: Boolean,
        securityFailure: Boolean,
        exactCalls: Int,
        inexactCalls: Int,
    ) {
        val manager = mockk<AlarmManager>(relaxed = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            every { manager.canScheduleExactAlarms() } returns exactAccess
        }
        if (securityFailure) {
            every { manager.setExactAndAllowWhileIdle(any(), any(), any()) } throws
                SecurityException("synthetic revoked access")
        }
        val context = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) manager else super.getSystemService(name)
        }
        val prefs = mockk<PrayerPreferencesDataSource>()
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
                alarmOffsetMinutes = 5,
                selectedAlarmPrayerKeys = setOf(PrayerAlarmScheduler.PRAYER_YATSI),
                alarmSoundUri = "content://test/custom",
            ),
        )
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val dayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.time)
        val expectedTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .parse("$dayIso 21:00")!!.time - 5 * 60_000L
        val dao = mockk<PrayerTimesDao>()
        coEvery { dao.getPrayerTimes(42, any(), any()) } returns listOf(
            PrayerTimeEntity(
                districtId = 42,
                localDate = dayIso,
                imsak = "05:00",
                gunes = "06:00",
                ogle = "12:00",
                ikindi = "15:00",
                aksam = "18:00",
                yatsi = "21:00",
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        val scheduler = PrayerAlarmScheduler(
            context,
            dao,
            prefs,
            mockk<AppAnalytics>(relaxed = true),
            Dispatchers.Unconfined,
        )
        try {
            assertThat(scheduler.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()
            val exactTime = slot<Long>()
            val fallbackTime = slot<Long>()
            verify(exactly = exactCalls) {
                manager.setExactAndAllowWhileIdle(
                    eq(AlarmManager.RTC_WAKEUP),
                    if (exactCalls > 0) capture(exactTime) else any(),
                    any<PendingIntent>(),
                )
            }
            verify(exactly = inexactCalls) {
                manager.setAndAllowWhileIdle(
                    eq(AlarmManager.RTC_WAKEUP),
                    if (inexactCalls > 0) capture(fallbackTime) else any(),
                    any<PendingIntent>(),
                )
            }
            if (exactCalls > 0) assertThat(exactTime.captured).isEqualTo(expectedTime)
            if (inexactCalls > 0) assertThat(fallbackTime.captured).isEqualTo(expectedTime)
        } finally {
            scheduler.cancelScheduledAlarm()
        }
    }
}
