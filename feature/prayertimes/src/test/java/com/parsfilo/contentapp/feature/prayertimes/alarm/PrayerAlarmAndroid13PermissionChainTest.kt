package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.dao.prayer.PrayerTimesDao
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesData
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.datastore.UserPreferencesData
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android 13 simulated notification-permission boundary with the real production
 * notifier AND scheduler. Real platform delivery is tested by the manual API33
 * test-APK probe; this Robolectric test never changes a device permission or alarm.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrayerAlarmAndroid13PermissionChainTest {
    private val application = RuntimeEnvironment.getApplication()
    private val alarms = application.getSystemService(AlarmManager::class.java)
    private val notifications =
        application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun deniedNotificationStillQueuesNextRealSchedulerAlarm() = verifyChain(permissionGranted = false)

    @Test
    fun grantedNotificationPostsAndQueuesNextRealSchedulerAlarm() = verifyChain(permissionGranted = true)

    private fun verifyChain(permissionGranted: Boolean) = runTest {
        // ContextCompat.checkSelfPermission delegates to checkPermission on API33.
        // This is strictly a synthetic grant/deny view, not a permission mutation.
        val context = object : ContextWrapper(application) {
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int {
                if (permission == Manifest.permission.POST_NOTIFICATIONS) {
                    return if (permissionGranted) {
                        PackageManager.PERMISSION_GRANTED
                    } else {
                        PackageManager.PERMISSION_DENIED
                    }
                }
                return super.checkPermission(permission, pid, uid)
            }
        }
        val prayerPrefs = mockk<PrayerPreferencesDataSource>()
        every { prayerPrefs.preferences } returns flowOf(
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
                selectedAlarmPrayerKeys = setOf(PrayerAlarmScheduler.PRAYER_YATSI),
                alarmSoundUri = null,
            ),
        )
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val dao = mockk<PrayerTimesDao>()
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
        val scheduler = PrayerAlarmScheduler(
            context,
            dao,
            prayerPrefs,
            mockk<AppAnalytics>(relaxed = true),
            Dispatchers.Unconfined,
        )
        val userPrefs = mockk<PreferencesDataSource>()
        val userData = mockk<UserPreferencesData>()
        every { userData.notificationsEnabled } returns true
        every { userPrefs.userData } returns flowOf(userData)
        val notifier = PrayerAlarmNotifier(context, userPrefs, mockk(relaxed = true))
        val receiver = PrayerAlarmReceiver().apply {
            dependenciesProvider = { PrayerAlarmDependencies(scheduler, notifier, prayerPrefs) }
            ioDispatcher = StandardTestDispatcher(testScheduler)
        }
        scheduler.cancelScheduledAlarm()
        notifications.cancelAll()
        try {
            val receiverShadow = dispatch(receiver)
            advanceUntilIdle()

            val scheduledAlarms = shadowOf(alarms).scheduledAlarms
            assertThat(scheduledAlarms).hasSize(1)
            assertThat(scheduledAlarms.single().triggerAtTime).isGreaterThan(System.currentTimeMillis())
            assertThat(shadowOf(scheduledAlarms.single().operation).savedIntent.action)
                .isEqualTo(PrayerAlarmScheduler.ACTION_FIRE_ALARM)
            assertThat(receiverShadow.wentAsync()).isTrue()
            assertThat(shadowOf(receiverShadow.originalPendingResult).future.isDone).isTrue()

            val notificationIds = notifications.activeNotifications.map { it.id }
            if (permissionGranted) {
                assertThat(notificationIds).contains(41_106)
                assertThat(notificationIds).contains(41_099)
            } else {
                assertThat(notificationIds).isEmpty()
            }
        } finally {
            notifications.cancelAll()
            scheduler.cancelScheduledAlarm()
        }
    }

    private fun dispatch(receiver: BroadcastReceiver): ShadowBroadcastReceiver {
        val pending = ShadowBroadcastPendingResult::class.java.getDeclaredMethod(
            "create",
            Int::class.javaPrimitiveType,
            String::class.java,
            Bundle::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(null, 0, null, null, false) as BroadcastReceiver.PendingResult
        BroadcastReceiver::class.java.getMethod(
            "setPendingResult",
            BroadcastReceiver.PendingResult::class.java,
        ).invoke(receiver, pending)
        return shadowOf(receiver).also {
            it.onReceive(application, Intent(PrayerAlarmScheduler.ACTION_FIRE_ALARM), AtomicBoolean())
        }
    }
}
