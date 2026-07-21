package com.parsfilo.contentapp.feature.counter.alarm

import android.os.Build
import java.util.Calendar
import java.util.TimeZone

internal const val STREAK_CHECK_HOUR = 21

internal fun resolveReminderScheduleMode(
    sdkInt: Int,
    canScheduleExactAlarms: Boolean,
): ReminderScheduleMode =
    if (sdkInt < Build.VERSION_CODES.S || canScheduleExactAlarms) {
        ReminderScheduleMode.EXACT
    } else {
        ReminderScheduleMode.INEXACT_FALLBACK
    }

internal fun nextDailyTriggerAt(
    nowEpochMillis: Long,
    hour: Int,
    minute: Int,
    timeZone: TimeZone = TimeZone.getDefault(),
): Long {
    val now =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = nowEpochMillis
        }
    val target =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = nowEpochMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    return target.timeInMillis
}

internal fun nextStreakCheckDelayMillis(
    nowEpochMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): Long =
    (
        nextDailyTriggerAt(
            nowEpochMillis = nowEpochMillis,
            hour = STREAK_CHECK_HOUR,
            minute = 0,
            timeZone = timeZone,
        ) - nowEpochMillis
    ).coerceAtLeast(0L)
