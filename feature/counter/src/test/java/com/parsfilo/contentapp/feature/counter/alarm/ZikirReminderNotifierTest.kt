package com.parsfilo.contentapp.feature.counter.alarm

import android.app.NotificationManager
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.datastore.UserPreferencesData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ZikirReminderNotifierTest {
    @Test
    fun repeatedStreakReminderReplacesTheExistingNotification() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val preferences = mockk<PreferencesDataSource>()
        val userData = mockk<UserPreferencesData>()
        every { userData.notificationsEnabled } returns true
        every { preferences.userData } returns flowOf(userData)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notifier = ZikirReminderNotifier(app, preferences)
        notifier.showStreakReminder(4)
        notifier.showStreakReminder(4)

        assertThat(manager.activeNotifications.map { it.id }).containsExactly(94002)
    }

    @Test
    fun cancellingStreakWarningPreservesOtherNotificationTypes() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val preferences = mockk<PreferencesDataSource>()
        val userData = mockk<UserPreferencesData>()
        every { userData.notificationsEnabled } returns true
        every { preferences.userData } returns flowOf(userData)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifier = ZikirReminderNotifier(app, preferences)

        notifier.showDailyReminder(0)
        notifier.showStreakReminder(4)
        notifier.showGoalCompleted(33)
        assertThat(manager.activeNotifications.map { it.id }).containsExactly(94001, 94002, 94003)

        notifier.cancelStreakReminder()

        assertThat(manager.activeNotifications.map { it.id }).containsExactly(94001, 94003)
    }

    @Test
    fun streakWarningExpiresAtDayBoundaryEvenWithoutAWorkerRerun() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val preferences = mockk<PreferencesDataSource>()
        val userData = mockk<UserPreferencesData>()
        every { userData.notificationsEnabled } returns true
        every { preferences.userData } returns flowOf(userData)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifier = ZikirReminderNotifier(app, preferences)

        notifier.showDailyReminder(0)
        notifier.showStreakReminder(4)
        notifier.showGoalCompleted(33)

        val notifications = manager.activeNotifications.associateBy { it.id }
        val streakTimeout = notifications.getValue(94002).notification.timeoutAfter
        assertThat(streakTimeout).isGreaterThan(0L)
        assertThat(streakTimeout).isAtMost(25 * 60 * 60 * 1000L)
        assertThat(notifications.getValue(94001).notification.timeoutAfter).isEqualTo(0L)
        assertThat(notifications.getValue(94003).notification.timeoutAfter).isEqualTo(0L)
    }

    @Test
    fun streakWarningTimeoutTracksLocalDayLengthIncludingDst() {
        val istanbul = ZoneId.of("Europe/Istanbul")
        val berlin = ZoneId.of("Europe/Berlin")
        assertThat(
            millisUntilNextLocalMidnight(
                LocalDate.of(2026, 11, 1).atTime(23, 59, 59).atZone(istanbul).toInstant().toEpochMilli(),
                TimeZone.getTimeZone(istanbul),
            ),
        ).isEqualTo(1_000L)
        assertThat(
            millisUntilNextLocalMidnight(
                LocalDate.of(2026, 3, 29).atStartOfDay(berlin).toInstant().toEpochMilli(),
                TimeZone.getTimeZone(berlin),
            ),
        ).isEqualTo(TimeUnit.HOURS.toMillis(23))
        assertThat(
            millisUntilNextLocalMidnight(
                LocalDate.of(2026, 10, 25).atStartOfDay(berlin).toInstant().toEpochMilli(),
                TimeZone.getTimeZone(berlin),
            ),
        ).isEqualTo(TimeUnit.HOURS.toMillis(25))
    }

    @Test
    fun notificationsDisabledInPreferencesSuppressesTheStreakReminder() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val preferences = mockk<PreferencesDataSource>()
        val userData = mockk<UserPreferencesData>()
        every { userData.notificationsEnabled } returns false
        every { preferences.userData } returns flowOf(userData)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        ZikirReminderNotifier(app, preferences).showStreakReminder(4)

        assertThat(manager.activeNotifications).isEmpty()
    }
}
