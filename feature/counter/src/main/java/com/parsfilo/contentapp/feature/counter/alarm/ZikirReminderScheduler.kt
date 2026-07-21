package com.parsfilo.contentapp.feature.counter.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZikirReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: PreferencesDataSource,
) {

    fun scheduleDaily(hour: Int, minute: Int): ReminderScheduleMode {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
            ?: run {
                Timber.w("AlarmManager unavailable; reminder scheduling skipped")
                return ReminderScheduleMode.INEXACT_FALLBACK
            }
        val pendingIntent = reminderPendingIntent(
            flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: run {
            Timber.w("Reminder PendingIntent could not be created; scheduling skipped")
            return ReminderScheduleMode.INEXACT_FALLBACK
        }

        val triggerAt =
            nextDailyTriggerAt(
                nowEpochMillis = System.currentTimeMillis(),
                hour = hour,
                minute = minute,
            )
        alarmManager.cancel(pendingIntent)
        val mode = scheduleAlarm(
            alarmManager = alarmManager,
            triggerAt = triggerAt,
            pendingIntent = pendingIntent,
        )
        Timber.d(
            "Zikir reminder scheduled mode=%s hour=%d minute=%d triggerAt=%d",
            mode,
            hour,
            minute,
            triggerAt,
        )
        return mode
    }

    fun cancel() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = reminderPendingIntent(PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Timber.d("Zikir reminder cancelled")
        }
    }

    suspend fun scheduleOrCancelFromPreferences(): ReminderScheduleMode? {
        val enabled = preferencesDataSource.zikirReminderEnabled.first()
        if (!enabled) {
            Timber.d("Zikir reminder disabled in preferences; cancelling reminder")
            cancel()
            return null
        }
        val hour = preferencesDataSource.zikirReminderHour.first()
        val minute = preferencesDataSource.zikirReminderMinute.first()
        return scheduleDaily(hour, minute)
    }

    fun canRequestExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return !canScheduleExactAlarmsNow()
    }

    fun canScheduleExactAlarmsNow(): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager.canScheduleExactAlarms()
    }

    fun scheduleStreakCheckWorker() {
        val initialDelay = nextStreakCheckDelayMillis(System.currentTimeMillis())

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<ZikirStreakCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            STREAK_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Timber.d("Zikir streak check worker scheduled (daily, initialDelayMs=%d)", initialDelay)
    }

    private fun reminderPendingIntent(flags: Int): PendingIntent? {
        val intent = Intent(context, ZikirReminderReceiver::class.java).apply {
            action = ACTION_ZIKIR_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY_REMINDER,
            intent,
            flags,
        )
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerAt: Long,
        pendingIntent: PendingIntent,
    ): ReminderScheduleMode {
        val canScheduleExactAlarms =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        return when (
            resolveReminderScheduleMode(
                sdkInt = Build.VERSION.SDK_INT,
                canScheduleExactAlarms = canScheduleExactAlarms,
            )
        ) {
            ReminderScheduleMode.EXACT -> {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                    ReminderScheduleMode.EXACT
                } catch (securityException: SecurityException) {
                    Timber.w(
                        securityException,
                        "Exact alarm denied at runtime; falling back to inexact scheduling",
                    )
                    scheduleInexact(alarmManager, triggerAt, pendingIntent)
                }
            }

            ReminderScheduleMode.INEXACT_FALLBACK -> {
                Timber.d(
                    "Exact alarm access unavailable on API %d; using inexact fallback",
                    Build.VERSION.SDK_INT,
                )
                scheduleInexact(alarmManager, triggerAt, pendingIntent)
            }
        }
    }

    private fun scheduleInexact(
        alarmManager: AlarmManager,
        triggerAt: Long,
        pendingIntent: PendingIntent,
    ): ReminderScheduleMode {
        // Fallback avoids crashes when exact alarm special access is unavailable on Android 12+.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent,
        )
        Timber.d("Inexact reminder fallback scheduled triggerAt=%d", triggerAt)
        return ReminderScheduleMode.INEXACT_FALLBACK
    }

    companion object {
        const val ACTION_ZIKIR_REMINDER = "com.parsfilo.zikirmatik.ZIKIR_REMINDER"
        const val STREAK_WORK_NAME = "zikir_streak_check_work"
        private const val REQUEST_CODE_DAILY_REMINDER = 72041
    }
}

enum class ReminderScheduleMode {
    EXACT,
    INEXACT_FALLBACK,
}
