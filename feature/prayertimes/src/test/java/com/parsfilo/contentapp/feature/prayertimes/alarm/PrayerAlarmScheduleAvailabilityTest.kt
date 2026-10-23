package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.dao.prayer.PrayerTimesDao
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesData
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import io.mockk.coEvery
import io.mockk.coVerify
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class PrayerAlarmScheduleAvailabilityTest {
    private val preferencesSource = mockk<PrayerPreferencesDataSource>()
    private val prayerDao = mockk<PrayerTimesDao>()
    private val analytics = mockk<AppAnalytics>(relaxed = true)

    @Test
    fun missingDistrictReturnsUnscheduledWithoutAccessingAlarmService() = runTest {
        every { preferencesSource.preferences } returns flowOf(preferences(district = null))
        val scheduler = schedulerWithUnavailableAlarmService()
        assertThat(scheduler.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isFalse()
        coVerify(exactly = 0) { prayerDao.getPrayerTimes(any(), any(), any()) }
        verify(exactly = 0) { analytics.logEvent(any(), any()) }
    }

    @Test
    fun absentUpcomingCacheReturnsUnscheduled() = runTest {
        every { preferencesSource.preferences } returns flowOf(preferences(district = 42))
        coEvery { prayerDao.getPrayerTimes(eq(42), any(), any()) } returns emptyList()
        val scheduler = schedulerWithUnavailableAlarmService()
        assertThat(scheduler.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isFalse()
        coVerify(exactly = 1) { prayerDao.getPrayerTimes(eq(42), any(), any()) }
        verify(exactly = 0) { analytics.logEvent(any(), any()) }
    }

    @Test
    fun missingAlarmServiceReturnsUnscheduledEvenWithUpcomingPrayer() = runTest {
        every { preferencesSource.preferences } returns flowOf(preferences(district = 42))
        coEvery { prayerDao.getPrayerTimes(eq(42), any(), any()) } returns listOf(upcomingPrayer())
        assertThat(
            schedulerWithUnavailableAlarmService().scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI),
        ).isFalse()
        verify(exactly = 0) { analytics.logEvent(any(), any()) }
    }

    @Test
    fun unselectedUpcomingPrayerDoesNotCreateFalseSchedule() = runTest {
        every { preferencesSource.preferences } returns flowOf(
            preferences(district = 42, keys = setOf("not-a-prayer-key")),
        )
        coEvery { prayerDao.getPrayerTimes(eq(42), any(), any()) } returns listOf(upcomingPrayer())
        assertThat(
            schedulerWithUnavailableAlarmService().scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI),
        ).isFalse()
        verify(exactly = 0) { analytics.logEvent(any(), any()) }
    }

    @Test
    fun disabledPreferencesCannotBeReportedAsScheduled() = runTest {
        every { preferencesSource.preferences } returns flowOf(preferences(district = null, enabled = false))
        assertThat(
            schedulerWithUnavailableAlarmService().scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI),
        ).isFalse()
        coVerify(exactly = 0) { prayerDao.getPrayerTimes(any(), any(), any()) }
    }

    private fun upcomingPrayer(): PrayerTimeEntity {
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.time)
        return PrayerTimeEntity(
            districtId = 42,
            localDate = tomorrowIso,
            imsak = "05:00",
            gunes = "06:00",
            ogle = "12:00",
            ikindi = "15:00",
            aksam = "18:00",
            yatsi = "21:00",
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun schedulerWithUnavailableAlarmService(): PrayerAlarmScheduler {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) null else super.getSystemService(name)
        }
        return PrayerAlarmScheduler(
            context = context,
            prayerTimesDao = prayerDao,
            prayerPreferencesDataSource = preferencesSource,
            appAnalytics = analytics,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
    }

    private fun preferences(
        district: Int?,
        enabled: Boolean = true,
        keys: Set<String> = setOf(PrayerAlarmScheduler.PRAYER_YATSI),
    ): PrayerPreferencesData = PrayerPreferencesData(
        mode = "MANUAL",
        selectedCountryId = if (district != null) 1 else null,
        selectedCityId = if (district != null) 2 else null,
        selectedDistrictId = district,
        lastAutoDistrictId = null,
        locationPermissionPrompted = false,
        lastSuccessfulRefreshAt = 0L,
        alarmEnabled = enabled,
        alarmOffsetMinutes = 0,
        selectedAlarmPrayerKeys = keys,
        alarmSoundUri = null,
    )
}
