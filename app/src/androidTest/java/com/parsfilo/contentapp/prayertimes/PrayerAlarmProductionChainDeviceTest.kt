package com.parsfilo.contentapp.prayertimes

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmReceiverEntryPoint
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manual, disposable-AVD-only acceptance of the actual installed flavor,
 * Hilt-provided production scheduler, persisted Room data, platform alarms,
 * production broadcast receiver and actual notification manager.
 *
 * A normal androidTest run has no side effects. Opt in with:
 *   -e amf497ProductionE2e true -e class <this test class>
 *
 * Emulator CI-smoke acceptance remains unchanged. A separate explicit
 * -e amf497PhysicalDevice true flag allows only the dedicated Samsung SM-G611F
 * running API28, to verify two actual hardware-delivered alarm broadcasts.
 * Physical tests also require verifiably muted alarm and media streams
 * and active Do Not Disturb; Samsung Android 9 may use PRIORITY mode
 * with no alarm/media exceptions instead of reporting filter NONE.
 * The host must confirm the dedicated test phone/app state before install.
 * Never enable physical-device testing on a personal phone.
 */
@RunWith(AndroidJUnit4::class)
class PrayerAlarmProductionChainDeviceTest {
    /** Non-audible guard preflight; does not broadcast or arm an alarm. */
    @Test
    fun dedicatedPhoneHasSafeMutedAlarmAndMediaStreams() {
        if (InstrumentationRegistry.getArguments().getString("amf497PhysicalGuard") != "true") return
        assertApprovedTestDevice()
    }

    @Test
    fun installedPrayerAppDeliversTimedAlarmAndRearmsUsingHiltRoom() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("amf497ProductionE2e") != "true") return

        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertEquals("com.parsfilo.namazvakitleri", context.packageName)
        assertApprovedTestDevice()
        assertTrue("No remote app startup or ad/analytics initialization", BuildConfig.CI_SMOKE)
        if (isEmulator()) {
            assertTrue("Modern-API emulator acceptance only", Build.VERSION.SDK_INT >= 33)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            assertEquals(
                "POST_NOTIFICATIONS must be granted on isolated device",
                PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
            )
        }

        val entry = EntryPointAccessors.fromApplication(
            context,
            PrayerAlarmReceiverEntryPoint::class.java,
        )
        val scheduler = entry.prayerAlarmScheduler()
        val preferences = entry.prayerPreferencesDataSource()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue("Notifications must be enabled for the dedicated QA package", notificationManager.areNotificationsEnabled())

        // Same singleton Hilt graph and Room DAO used by the production scheduler.
        val dao = entry.prayerTimesDao()

        // Reject reused app state *before* cleanup can clear user selections.
        requireFreshPrayerState(preferences)

        try {
            runBlocking {
                val first = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, 2)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // API33 setAndAllowWhileIdle is inexact; leave a long gap to
                // avoid treating Android's minimum idle-alarm interval as a bug.
                val second = (first.clone() as Calendar).apply { add(Calendar.MINUTE, 10) }
                val third = (first.clone() as Calendar).apply { add(Calendar.MINUTE, 20) }
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(first.time)
                // Fails clearly if crossing midnight; do not alter system time/time zone.
                assertEquals(day, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(third.time))
                val format = SimpleDateFormat("HH:mm", Locale.US)
                dao.upsertPrayerTimes(
                    listOf(
                        PrayerTimeEntity(
                            districtId = TEST_DISTRICT_ID,
                            localDate = day,
                            imsak = format.format(first.time),
                            gunes = "00:00",
                            ogle = format.format(second.time),
                            ikindi = format.format(third.time),
                            aksam = "00:00",
                            yatsi = "00:00",
                            fetchedAt = System.currentTimeMillis(),
                        ),
                    ),
                )
                assertEquals(1, dao.countByDistrictAndDate(TEST_DISTRICT_ID, day))
                preferences.setManualSelection(1, 1, TEST_DISTRICT_ID)
                preferences.setAlarmOffsetMinutes(0)
                preferences.setSelectedAlarmPrayerKeys(
                    setOf(
                        PrayerAlarmScheduler.PRAYER_IMSAK,
                        PrayerAlarmScheduler.PRAYER_OGLE,
                        PrayerAlarmScheduler.PRAYER_IKINDI,
                    ),
                )
                preferences.setAlarmEnabled(true)
                assertTrue("Real production scheduler must arm the first alarm", scheduler.scheduleNext(PrayerAppVariant.NAMAZ_VAKITLERI))
            }

            // Android schedules setAndAllowWhileIdle within an inexact
            // platform delivery window. A three-minute watchdog was too
            // aggressive when the first prayer is nearly two minutes ahead.
            awaitNotification(notificationManager, FIRST_NOTIFICATION_ID, TimeUnit.MINUTES.toMillis(5))
            awaitRearmedPendingIntent(
                context,
                41_033,
                PrayerAlarmScheduler.PRAYER_OGLE,
                10_000L,
            )
            if (!isEmulator()) {
                // Hardware-only extended acceptance: the second *scheduled*
                // alarm must actually fire, then production must arm a third.
                // A direct broadcast would not prove AlarmManager delivery.
                // setAndAllowWhileIdle is inexact: a short second-alarm deadline
                // could misclassify platform scheduling latency as app failure.
                awaitNotification(notificationManager, SECOND_NOTIFICATION_ID, TimeUnit.MINUTES.toMillis(30))
                awaitRearmedPendingIntent(
                    context,
                    41_034,
                    PrayerAlarmScheduler.PRAYER_IKINDI,
                    10_000L,
                )
            }
        } finally {
            runBlocking {
                preferences.setAlarmEnabled(false)
                scheduler.cancelScheduledAlarm()
                dao.deletePrayerTimesByDistrict(TEST_DISTRICT_ID)
                preferences.clearSelection()
                preferences.setSelectedAlarmPrayerKeys(emptySet())
            }
            notificationManager.cancel(FIRST_NOTIFICATION_ID)
            notificationManager.cancel(SECOND_NOTIFICATION_ID)
            notificationManager.cancel(41_099)
        }
    }

    /**
     * Diagnostic split: bypass AlarmManager timing, but keep the installed
     * production BroadcastReceiver, Hilt/Room, notifier and next-alarm rearm.
     * This never establishes that Android delivered a scheduled alarm.
     */
    @Test
    fun isolatedExplicitBroadcastExercisesRealReceiverNotifierAndRearm() {
        if (InstrumentationRegistry.getArguments().getString("amf497ProductionDirect") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertEquals("com.parsfilo.namazvakitleri", context.packageName)
        assertApprovedTestDevice()
        assertTrue(BuildConfig.CI_SMOKE)
        if (Build.VERSION.SDK_INT >= 33) {
            assertEquals(
                PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
            )
        }
        val entry = EntryPointAccessors.fromApplication(
            context,
            PrayerAlarmReceiverEntryPoint::class.java,
        )
        val prefs = entry.prayerPreferencesDataSource()
        val dao = entry.prayerTimesDao()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue("Notifications must be enabled", nm.areNotificationsEnabled())
        // Do not enter the destructive cleanup block with a preexisting selection.
        requireFreshPrayerState(prefs)
        try {
            runBlocking {
                val next = Calendar.getInstance().apply { add(Calendar.MINUTE, 10) }
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(next.time)
                val hm = SimpleDateFormat("HH:mm", Locale.US).format(next.time)
                dao.upsertPrayerTimes(
                    listOf(
                        PrayerTimeEntity(
                            districtId = TEST_DISTRICT_ID,
                            localDate = date,
                            imsak = hm,
                            gunes = "00:00",
                            ogle = "00:00",
                            ikindi = "00:00",
                            aksam = "00:00",
                            yatsi = "00:00",
                            fetchedAt = System.currentTimeMillis(),
                        ),
                    ),
                )
                prefs.setManualSelection(1, 1, TEST_DISTRICT_ID)
                prefs.setSelectedAlarmPrayerKeys(setOf(PrayerAlarmScheduler.PRAYER_IMSAK))
                prefs.setAlarmOffsetMinutes(0)
                prefs.setAlarmEnabled(true)
            }
            context.sendBroadcast(
                android.content.Intent(context, com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmReceiver::class.java)
                    .apply {
                        action = PrayerAlarmScheduler.ACTION_FIRE_ALARM
                        putExtra(PrayerAlarmScheduler.EXTRA_VARIANT, PrayerAppVariant.NAMAZ_VAKITLERI.name)
                        putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_KEY, PrayerAlarmScheduler.PRAYER_OGLE)
                        putExtra(PrayerAlarmScheduler.EXTRA_TIME_HM, "12:00")
                        putExtra(PrayerAlarmScheduler.EXTRA_LOCAL_DATE, "2026-10-25")
                        putExtra(PrayerAlarmScheduler.EXTRA_TRIGGER_AT, System.currentTimeMillis())
                    },
            )
            awaitNotification(nm, SECOND_NOTIFICATION_ID, 25_000L)
            awaitRearmedPendingIntent(
                context,
                41_031,
                PrayerAlarmScheduler.PRAYER_IMSAK,
                10_000L,
            )
        } finally {
            runBlocking {
                prefs.setAlarmEnabled(false)
                entry.prayerAlarmScheduler().cancelScheduledAlarm()
                dao.deletePrayerTimesByDistrict(TEST_DISTRICT_ID)
                prefs.clearSelection()
                prefs.setSelectedAlarmPrayerKeys(emptySet())
            }
            nm.cancel(SECOND_NOTIFICATION_ID)
            nm.cancel(41_099)
        }
    }

    private fun assertApprovedTestDevice() {
        if (isEmulator()) return
        val args = InstrumentationRegistry.getArguments()
        assertTrue(
            "Physical acceptance requires explicit dedicated-device opt-in",
            args.getString("amf497PhysicalDevice") == "true",
        )
        assertTrue(
            "Only the approved Android 9 Samsung SM-G611F test phone is allowed",
            Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
                Build.MODEL == "SM-G611F" &&
                Build.VERSION.SDK_INT == 28 &&
                Build.FINGERPRINT.contains("/on7xreflte"),
        )
        val manager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audio = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertTrue(
            "Physical QA must have active Do Not Disturb and muted alarm/media audio",
            manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                (
                    manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY &&
                        audio.isStreamMute(AudioManager.STREAM_ALARM) &&
                        audio.isStreamMute(AudioManager.STREAM_MUSIC)
                ),
        )
    }

    private fun requireFreshPrayerState(preferences: PrayerPreferencesDataSource) {
        val initial = runBlocking { preferences.preferences.first() }
        assertFalse("Disposable app cannot have an enabled alarm", initial.alarmEnabled)
        assertNull("Disposable app cannot have a selected country", initial.selectedCountryId)
        assertNull("Disposable app cannot have a selected city", initial.selectedCityId)
        assertNull("Disposable app cannot have a selected district", initial.selectedDistrictId)
        assertTrue("Disposable app cannot have selected prayer alerts", initial.selectedAlarmPrayerKeys.isEmpty())
        assertEquals("Disposable app cannot have a saved alarm offset", 0, initial.alarmOffsetMinutes)
    }

    private fun awaitRearmedPendingIntent(
        context: Context,
        requestCode: Int,
        prayerKey: String,
        timeoutMs: Long,
    ) {
        val intent = android.content.Intent(
            context,
            com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmReceiver::class.java,
        ).apply {
            action = PrayerAlarmScheduler.ACTION_FIRE_ALARM
            putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_KEY, prayerKey)
        }
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            val rearmed = android.app.PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            if (rearmed != null) return
            SystemClock.sleep(250L)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Production receiver did not rearm $prayerKey before timeout")
    }

    private fun awaitNotification(manager: NotificationManager, notificationId: Int, timeoutMs: Long) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            if (manager.activeNotifications.any { it.id == notificationId }) return
            SystemClock.sleep(1_000)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Production notification $notificationId not delivered before timeout")
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)
        return fingerprint.contains("generic") || model.contains("emulator") || model.contains("sdk_gphone")
    }

    private companion object {
        private const val TEST_DISTRICT_ID = 9_500_497
        private const val FIRST_NOTIFICATION_ID = 41_100
        private const val SECOND_NOTIFICATION_ID = 41_101
    }
}
