package com.parsfilo.contentapp.ui

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.feature.counter.alarm.ZikirStreakCheckWorker
import com.parsfilo.contentapp.feature.counter.alarm.ZikirStreakWorkerEntryPoint
import com.parsfilo.contentapp.feature.counter.model.ZikirSession
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * One disposable emulator-only end-to-end check:
 * real WorkManager, Hilt repo + persistent Room, DataStore, and Android NotificationManager.
 */
@RunWith(AndroidJUnit4::class)
class CounterStreakWorkManagerAcceptanceTest {
    @Test
    fun expiredValidCompletedAndDisabledStreakReminderRespectRealOsNotifications() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            BuildConfig.FLAVOR_NAME == "zikirmatik" &&
                context.packageName == "com.parsfilo.zikirmatik" &&
                Build.VERSION.SDK_INT >= 33 &&
                (Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")),
        )
        assertEquals(
            android.content.pm.PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        val entry = EntryPointAccessors.fromApplication(
            context, ZikirStreakWorkerEntryPoint::class.java,
        )
        val repo = entry.zikirRepository()
        val prefs = entry.preferencesDataSource()
        val workManager = WorkManager.getInstance(context)
        val notificationId = 94002
        manager.cancel(notificationId)

        try {
            runBlocking {
                prefs.setNotificationsEnabled(true)
                prefs.setZikirStreakReminderEnabled(true)
                prefs.setZikirDailyGoal(33)

                assertTrue(repo.saveSession(fakeSession(49801L, -2)))
                assertEquals(0, repo.getTodayTotalCount().first())
            }
            runWorker(workManager)
            assertFalse(manager.activeNotifications.any { it.id == notificationId })

            runBlocking { assertTrue(repo.saveSession(fakeSession(49802L, -1))) }
            runWorker(workManager)
            val shown = manager.activeNotifications.single { it.id == notificationId }
            assertTrue(shown.notification.extras.getCharSequence("android.text").toString().isNotBlank())

            // A previous reminder must not linger once the user's goal is completed.
            runBlocking { assertTrue(repo.saveSession(fakeSession(49803L, 0))) }
            runWorker(workManager)
            assertFalse("Stale streak reminder survives goal completion", manager.activeNotifications.any { it.id == notificationId })

            // The preference opt-out must withdraw any previously posted reminder, too.
            runBlocking { entry.zikirReminderNotifier().showStreakReminder(2) }
            assertTrue(manager.activeNotifications.any { it.id == notificationId })
            runBlocking { prefs.setZikirStreakReminderEnabled(false) }
            runWorker(workManager)
            assertFalse("Stale streak reminder survives opt-out", manager.activeNotifications.any { it.id == notificationId })
        } finally {
            manager.cancel(notificationId)
            runBlocking { prefs.setZikirStreakReminderEnabled(false) }
        }
    }

    private fun fakeSession(id: Long, daysAgo: Int): ZikirSession {
        val clock = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, daysAgo)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        return ZikirSession(
            id = id,
            zikirKey = "disposable_streak_acceptance",
            arabicText = "test",
            latinText = "test",
            targetCount = 33,
            completedCount = 33,
            completedAt = clock.timeInMillis,
            durationSeconds = 42L,
            isComplete = true,
        )
    }

    private fun runWorker(manager: WorkManager) {
        val request = OneTimeWorkRequestBuilder<ZikirStreakCheckWorker>().build()
        manager.enqueue(request).result.get(20, TimeUnit.SECONDS)
        repeat(100) {
            val info = manager.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS)
            if (info != null && info.state.isFinished) {
                assertEquals(WorkInfo.State.SUCCEEDED, info.state)
                return
            }
            Thread.sleep(150L)
        }
        throw AssertionError("Timed out waiting for real WorkManager streak check")
    }
}
