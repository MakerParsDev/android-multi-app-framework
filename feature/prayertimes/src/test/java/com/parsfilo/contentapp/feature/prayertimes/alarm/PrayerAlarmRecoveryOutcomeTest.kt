package com.parsfilo.contentapp.feature.prayertimes.alarm

import androidx.work.ListenableWorker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrayerAlarmRecoveryOutcomeTest {
    @Test fun unschedulableCacheRetriesBeforeBudgetExpires() {
        assertThat(PrayerAlarmRecoveryWorker.scheduleOutcome(false, 0))
            .isEqualTo(ListenableWorker.Result.retry())
        assertThat(PrayerAlarmRecoveryWorker.scheduleOutcome(false, 1))
            .isEqualTo(ListenableWorker.Result.retry())
    }

    @Test fun unschedulableCacheFailsAfterBoundedAttempts() {
        assertThat(PrayerAlarmRecoveryWorker.scheduleOutcome(false, 2))
            .isEqualTo(ListenableWorker.Result.failure())
    }

    @Test fun restoredCacheCompletesRecovery() {
        assertThat(PrayerAlarmRecoveryWorker.scheduleOutcome(true, 1))
            .isEqualTo(ListenableWorker.Result.success())
    }
}
