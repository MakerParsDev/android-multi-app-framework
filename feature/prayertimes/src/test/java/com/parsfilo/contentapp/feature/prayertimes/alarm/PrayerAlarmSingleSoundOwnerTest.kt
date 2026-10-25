package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.datastore.UserPreferencesData
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PrayerAlarmSingleSoundOwnerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val userPreferences = mockk<PreferencesDataSource>()
    private val userData = mockk<UserPreferencesData>()
    private val soundPlayer = mockk<PrayerAlarmSoundPlayer>(relaxed = true)
    private val notifier = PrayerAlarmNotifier(context, userPreferences, soundPlayer)

    @Before
    fun setup() {
        notifications.cancelAll()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.deleteNotificationChannel(PrayerAlarmNotifier.CHANNEL_ID)
        }
        every { userPreferences.userData } returns flowOf(userData)
        every { userData.notificationsEnabled } returns true
    }

    @Test
    fun modernAndroidNeverStartsSecondAppSoundEvenForCustomUriAndTwoReminders() = runTest {
        notifier.show(payload(PrayerAlarmScheduler.PRAYER_IMSAK), PrayerAppVariant.NAMAZ_VAKITLERI, "content://qa/custom-tone")
        notifier.show(payload(PrayerAlarmScheduler.PRAYER_OGLE), PrayerAppVariant.NAMAZ_VAKITLERI, null)

        verify(exactly = 0) { soundPlayer.play(any()) }
        assertThat(notifications.getNotificationChannel(PrayerAlarmNotifier.CHANNEL_ID)).isNotNull()
        val posted = notifications.activeNotifications.map { it.notification }
        assertThat(posted).hasSize(3)
        assertThat(posted.all { it.groupAlertBehavior == android.app.Notification.GROUP_ALERT_CHILDREN }).isTrue()
    }

    @Test
    fun alreadyMutedChannelIsNotReconfiguredOrBypassed() = runTest {
        val channel = NotificationChannel(
            PrayerAlarmNotifier.CHANNEL_ID,
            "Previously muted prayer reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { setSound(null, null) }
        notifications.createNotificationChannel(channel)

        notifier.show(payload(PrayerAlarmScheduler.PRAYER_IMSAK), PrayerAppVariant.NAMAZ_VAKITLERI, "content://qa/custom-tone")
        verify(exactly = 0) { soundPlayer.play(any()) }
        assertThat(notifications.getNotificationChannel(PrayerAlarmNotifier.CHANNEL_ID).sound).isNull()
        assertThat(notifications.activeNotifications).hasLength(2)
    }

    @Test
    fun existingCustomChannelSoundRemainsTheSingleSystemSelectedSound() = runTest {
        val chosenByUser = Uri.parse("content://qa/channel-tone")
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        notifications.createNotificationChannel(
            NotificationChannel(
                PrayerAlarmNotifier.CHANNEL_ID,
                "User selected channel sound",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { setSound(chosenByUser, attributes) },
        )

        notifier.show(payload(PrayerAlarmScheduler.PRAYER_IMSAK), PrayerAppVariant.NAMAZ_VAKITLERI, "content://qa/old-app-tone")
        assertThat(notifications.getNotificationChannel(PrayerAlarmNotifier.CHANNEL_ID).sound)
            .isEqualTo(chosenByUser)
        verify(exactly = 0) { soundPlayer.play(any()) }
    }

    @Test
    fun systemBlockedNotificationsSuppressPlayerAndPosting() = runTest {
        shadowOf(notifications).setNotificationsEnabled(false)
        try {
            notifier.show(payload(PrayerAlarmScheduler.PRAYER_IMSAK), PrayerAppVariant.NAMAZ_VAKITLERI, "content://qa/custom")
            assertThat(notifications.activeNotifications).isEmpty()
            verify(exactly = 0) { soundPlayer.play(any()) }
        } finally {
            shadowOf(notifications).setNotificationsEnabled(true)
        }
    }

    @Test
    fun appNotificationConsentOffSuppressesBothNotificationAndSound() = runTest {
        every { userData.notificationsEnabled } returns false
        notifier.show(payload(PrayerAlarmScheduler.PRAYER_IMSAK), PrayerAppVariant.NAMAZ_VAKITLERI, "content://qa/custom-tone")
        assertThat(notifications.activeNotifications).isEmpty()
        verify(exactly = 0) { soundPlayer.play(any()) }
    }

    @Test
    @Config(sdk = [24])
    fun preChannelAndroidKeepsSelectedAppToneAsSingleAudioOwner() = runTest {
        assertThat(Build.VERSION.SDK_INT).isLessThan(26)
        notifier.show(payload(PrayerAlarmScheduler.PRAYER_IMSAK), PrayerAppVariant.NAMAZ_VAKITLERI, "content://qa/custom-tone")
        notifier.show(payload(PrayerAlarmScheduler.PRAYER_OGLE), PrayerAppVariant.NAMAZ_VAKITLERI, null)
        verify(exactly = 1) { soundPlayer.play("content://qa/custom-tone") }
        verify(exactly = 1) { soundPlayer.play(null) }
        assertThat(notifications.activeNotifications).hasLength(3)
    }

    private fun payload(prayer: String) = PrayerAlarmPayload(
        prayerKey = prayer,
        timeHm = "12:00",
        localDate = "2026-10-26",
        triggerAtMillis = System.currentTimeMillis(),
    )
}
