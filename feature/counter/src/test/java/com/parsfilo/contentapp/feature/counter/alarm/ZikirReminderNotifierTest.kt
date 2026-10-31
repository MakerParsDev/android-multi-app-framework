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
