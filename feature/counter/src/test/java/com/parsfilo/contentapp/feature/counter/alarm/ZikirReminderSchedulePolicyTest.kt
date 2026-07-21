package com.parsfilo.contentapp.feature.counter.alarm

import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ZikirReminderSchedulePolicyTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `pre android 12 always uses exact alarm mode`() {
        assertThat(
            resolveReminderScheduleMode(
                sdkInt = Build.VERSION_CODES.R,
                canScheduleExactAlarms = false,
            ),
        ).isEqualTo(ReminderScheduleMode.EXACT)
    }

    @Test
    fun `android 12 uses exact mode when permission is available`() {
        assertThat(
            resolveReminderScheduleMode(
                sdkInt = Build.VERSION_CODES.S,
                canScheduleExactAlarms = true,
            ),
        ).isEqualTo(ReminderScheduleMode.EXACT)
    }

    @Test
    fun `android 12 falls back when exact alarm permission is unavailable`() {
        assertThat(
            resolveReminderScheduleMode(
                sdkInt = Build.VERSION_CODES.S,
                canScheduleExactAlarms = false,
            ),
        ).isEqualTo(ReminderScheduleMode.INEXACT_FALLBACK)
    }

    @Test
    fun `future reminder stays on the same day`() {
        val now = utcMillis(2026, Calendar.JULY, 11, 10, 15)

        val trigger = nextDailyTriggerAt(now, hour = 18, minute = 30, timeZone = utc)

        assertThat(trigger).isEqualTo(utcMillis(2026, Calendar.JULY, 11, 18, 30))
    }

    @Test
    fun `past reminder rolls to the next day`() {
        val now = utcMillis(2026, Calendar.JULY, 11, 22, 0)

        val trigger = nextDailyTriggerAt(now, hour = 18, minute = 30, timeZone = utc)

        assertThat(trigger).isEqualTo(utcMillis(2026, Calendar.JULY, 12, 18, 30))
    }

    @Test
    fun `equal reminder instant rolls to the next day`() {
        val now = utcMillis(2026, Calendar.JULY, 11, 18, 30)

        val trigger = nextDailyTriggerAt(now, hour = 18, minute = 30, timeZone = utc)

        assertThat(trigger).isEqualTo(utcMillis(2026, Calendar.JULY, 12, 18, 30))
    }

    @Test
    fun `invalid reminder time is clamped to valid bounds`() {
        val now = utcMillis(2026, Calendar.JULY, 11, 10, 0)

        val trigger = nextDailyTriggerAt(now, hour = 99, minute = -5, timeZone = utc)

        assertThat(trigger).isEqualTo(utcMillis(2026, Calendar.JULY, 11, 23, 0))
    }

    @Test
    fun `streak check before 21 is scheduled for the same day`() {
        val now = utcMillis(2026, Calendar.JULY, 11, 20, 30)

        val delay = nextStreakCheckDelayMillis(now, timeZone = utc)

        assertThat(delay).isEqualTo(30 * 60 * 1_000L)
    }

    @Test
    fun `streak check at 21 is scheduled for the next day`() {
        val now = utcMillis(2026, Calendar.JULY, 11, 21, 0)

        val delay = nextStreakCheckDelayMillis(now, timeZone = utc)

        assertThat(delay).isEqualTo(24 * 60 * 60 * 1_000L)
    }

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
