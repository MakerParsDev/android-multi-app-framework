package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Uses a disposable test package and real Android APIs; no AlarmManager or runtime mocking. */
@RunWith(AndroidJUnit4::class)
class PrayerAlarmMutedChannelDeviceTest {
    @Test
    fun twoSyntheticRemindersStaySilentAndKeepMutedChannel() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertEquals("Unexpected test host", TEST_PACKAGE, context.packageName)
        assertFalse("Never exercise the production app", context.packageName == PRODUCTION_PACKAGE)
        assertTrue("Test notifications must be enabled", NotificationManagerCompat.from(context).areNotificationsEnabled())

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = PrayerAlarmNotifier.CHANNEL_ID
        val preexistingChannel = manager.getNotificationChannel(channelId)
        assertNull("Refusing to change an existing channel", preexistingChannel)
        val channel = NotificationChannel(channelId, "Muted synthetic reminders", NotificationManager.IMPORTANCE_HIGH)
            .apply { setSound(null, null) }
        manager.createNotificationChannel(channel)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataFile = File(context.filesDir, "prayer-491-${UUID.randomUUID()}.preferences_pb")
        try {
            assertNull("Refusing to post to an audible channel", manager.getNotificationChannel(channelId).sound)
            val preferences = PreferencesDataSource(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { dataFile }),
            )
            val notifier = PrayerAlarmNotifier(context, preferences, PrayerAlarmSoundPlayer(context))
            manager.cancelAll()
            runBlocking {
                notifier.show(payload(PrayerAlarmScheduler.PRAYER_IMSAK), PrayerAppVariant.NAMAZ_VAKITLERI, "content://qa/old-app-tone")
                notifier.show(payload(PrayerAlarmScheduler.PRAYER_OGLE), PrayerAppVariant.NAMAZ_VAKITLERI, null)
            }
            assertNull("Notifier must preserve channel mute", manager.getNotificationChannel(channelId).sound)
            val active = manager.activeNotifications
            assertEquals("Two reminders and one group summary", 3, active.size)
            assertTrue(active.all { it.notification.groupAlertBehavior == android.app.Notification.GROUP_ALERT_CHILDREN })
        } finally {
            manager.cancelAll()
            manager.deleteNotificationChannel(channelId)
            scope.cancel()
            dataFile.delete()
        }
    }

    private fun payload(key: String) = PrayerAlarmPayload(
        prayerKey = key,
        timeHm = "12:00",
        localDate = "2026-10-26",
        triggerAtMillis = System.currentTimeMillis(),
    )

    private companion object {
        const val TEST_PACKAGE = "com.parsfilo.contentapp.feature.prayertimes.test"
        const val PRODUCTION_PACKAGE = "com.parsfilo.namazvakitleri"
    }
}
