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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class PrayerAlarmRearmSafetyTest {
    private val app: Context = RuntimeEnvironment.getApplication()
    private val originalAlarmManager: AlarmManager = app.getSystemService(AlarmManager::class.java)
    private val preferencesSource = mockk<PrayerPreferencesDataSource>()
    private val dao = mockk<PrayerTimesDao>()
    private val analytics = mockk<AppAnalytics>(relaxed = true)

    private fun scheduler(context: Context = app) = PrayerAlarmScheduler(
        context = context,
        prayerTimesDao = dao,
        prayerPreferencesDataSource = preferencesSource,
        appAnalytics = analytics,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun selectedPrayer(key: String, enabled: Boolean = true, offsetMinutes: Int = 0) {
        every { preferencesSource.preferences } returns flowOf(
            PrayerPreferencesData(
                mode = "MANUAL",
                selectedCountryId = 1,
                selectedCityId = 2,
                selectedDistrictId = 42,
                lastAutoDistrictId = null,
                locationPermissionPrompted = false,
                lastSuccessfulRefreshAt = 0L,
                alarmEnabled = enabled,
                alarmOffsetMinutes = offsetMinutes,
                selectedAlarmPrayerKeys = setOf(key),
                alarmSoundUri = null,
            ),
        )
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.time)
        coEvery { dao.getPrayerTimes(eq(42), any(), any()) } returns listOf(
            PrayerTimeEntity(
                districtId = 42,
                localDate = tomorrowIso,
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

    private fun scheduled() = shadowOf(originalAlarmManager).scheduledAlarms

    @Test
    fun repeatedSamePrayerReplacesInsteadOfDuplicating() = runTest {
        selectedPrayer(PrayerAlarmScheduler.PRAYER_YATSI)
        val subject = scheduler()
        assertThat(subject.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()
        assertThat(scheduled()).hasSize(1)

        assertThat(subject.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()
        assertThat(scheduled()).hasSize(1)
    }

    @Test
    fun successfulPrayerSwitchRemovesOldOnlyAfterNewAlarmIsArmed() = runTest {
        selectedPrayer(PrayerAlarmScheduler.PRAYER_YATSI)
        val subject = scheduler()
        assertThat(subject.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()
        val oldIntent = scheduled().single().operation

        selectedPrayer(PrayerAlarmScheduler.PRAYER_IKINDI)
        assertThat(subject.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()
        assertThat(scheduled()).hasSize(1)
        assertThat(scheduled().single().operation).isNotEqualTo(oldIntent)
    }

    @Test
    fun imsakDoesNotGetCancelledByLegacyCleanupSharingItsIntentIdentity() = runTest {
        selectedPrayer(PrayerAlarmScheduler.PRAYER_YATSI)
        val subject = scheduler()
        assertThat(subject.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()

        selectedPrayer(PrayerAlarmScheduler.PRAYER_IMSAK)
        assertThat(subject.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()
        assertThat(scheduled()).hasSize(1)
        assertThat(scheduled().single().operation).isNotNull()
    }

    @Test
    fun throwingPlatformSetPreservesOldAlarmForDifferentPrayer() = runTest {
        selectedPrayer(PrayerAlarmScheduler.PRAYER_YATSI)
        assertThat(scheduler().scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()
        val original = scheduled().single()

        val failingManager = mockk<AlarmManager>(relaxed = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            every { failingManager.canScheduleExactAlarms() } returns true
        }
        every { failingManager.setAndAllowWhileIdle(any(), any(), any()) } throws
            SecurityException("synthetic fallback refusal")
        every { failingManager.setExactAndAllowWhileIdle(any(), any(), any()) } throws
            SecurityException("synthetic alarm service failure")
        every { failingManager.cancel(any<PendingIntent>()) } answers {
            originalAlarmManager.cancel(firstArg<PendingIntent>())
        }
        val failedContext = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) failingManager else super.getSystemService(name)
        }
        selectedPrayer(PrayerAlarmScheduler.PRAYER_IKINDI)
        try {
            scheduler(failedContext).scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)
            throw AssertionError("Synthetic platform failure was not propagated")
        } catch (expected: SecurityException) {
            assertThat(expected.message).contains("synthetic fallback refusal")
        }
        assertThat(scheduled()).hasSize(1)
        assertThat(scheduled().single().operation).isEqualTo(original.operation)
        assertThat(scheduled().single().triggerAtTime).isEqualTo(original.triggerAtTime)
    }

    @Test
    fun failingReplacementOfSamePrayerPreservesOldTimeAndNotificationPayload() = runTest {
        selectedPrayer(PrayerAlarmScheduler.PRAYER_YATSI)
        assertThat(scheduler().scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()
        val original = scheduled().single()
        val originalPayload = shadowOf(original.operation).savedIntent
            .getLongExtra(PrayerAlarmScheduler.EXTRA_TRIGGER_AT, -1L)
        assertThat(originalPayload).isEqualTo(original.triggerAtTime)

        val failingManager = mockk<AlarmManager>(relaxed = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            every { failingManager.canScheduleExactAlarms() } returns true
        }
        every { failingManager.setAndAllowWhileIdle(any(), any(), any()) } throws
            SecurityException("synthetic fallback refusal")
        every { failingManager.setExactAndAllowWhileIdle(any(), any(), any()) } throws
            SecurityException("synthetic replacement refused")
        every { failingManager.cancel(any<PendingIntent>()) } answers {
            originalAlarmManager.cancel(firstArg<PendingIntent>())
        }
        val failedContext = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ALARM_SERVICE) failingManager else super.getSystemService(name)
        }
        selectedPrayer(PrayerAlarmScheduler.PRAYER_YATSI, offsetMinutes = 15)
        try {
            scheduler(failedContext).scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)
            throw AssertionError("Expected synthetic failure")
        } catch (expected: SecurityException) {
            assertThat(expected.message).contains("synthetic fallback refusal")
        }
        assertThat(scheduled()).hasSize(1)
        assertThat(scheduled().single().triggerAtTime).isEqualTo(original.triggerAtTime)
        assertThat(
            shadowOf(scheduled().single().operation).savedIntent.getLongExtra(
                PrayerAlarmScheduler.EXTRA_TRIGGER_AT, -1L,
            ),
        ).isEqualTo(originalPayload)

        // Once Android accepts the new alarm, both its trigger and payload advance.
        selectedPrayer(PrayerAlarmScheduler.PRAYER_YATSI, offsetMinutes = 15)
        assertThat(scheduler().scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()
        assertThat(scheduled()).hasSize(1)
        assertThat(scheduled().single().triggerAtTime).isEqualTo(original.triggerAtTime - 15 * 60_000L)
        assertThat(
            shadowOf(scheduled().single().operation).savedIntent.getLongExtra(
                PrayerAlarmScheduler.EXTRA_TRIGGER_AT, -1L,
            ),
        ).isEqualTo(scheduled().single().triggerAtTime)
    }

    @Test
    fun deliberateAlarmDisableCancelsScheduledAlarm() = runTest {
        selectedPrayer(PrayerAlarmScheduler.PRAYER_YATSI)
        val subject = scheduler()
        assertThat(subject.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isTrue()
        assertThat(scheduled()).hasSize(1)
        selectedPrayer(PrayerAlarmScheduler.PRAYER_YATSI, enabled = false)
        assertThat(subject.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI)).isFalse()
        assertThat(scheduled()).isEmpty()
    }
}
