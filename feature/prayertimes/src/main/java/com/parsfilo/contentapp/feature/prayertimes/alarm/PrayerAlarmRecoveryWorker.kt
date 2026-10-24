package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Durable, unique follow-up when the receiver cannot schedule the next alarm.
 * Does not display notifications, alter user preferences or bypass scheduler permission checks.
 */
class PrayerAlarmRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val variant = inputData.getString(KEY_VARIANT)
            ?.let { runCatching { PrayerAppVariant.valueOf(it) }.getOrNull() }
            ?: return Result.failure()

        return try {
            val entry = EntryPointAccessors.fromApplication(
                applicationContext,
                PrayerAlarmRecoveryEntryPoint::class.java,
            )
            val prefs = entry.recoveryPrayerPreferencesDataSource().preferences.first()
            // Do not retry when the user deliberately disabled alarms or selected no district.
            if (!prefs.alarmEnabled || prefs.selectedDistrictId == null) return Result.success()
            val scheduled = entry.recoveryPrayerAlarmScheduler().scheduleNext(variant)
            if (!scheduled) {
                Timber.w("Prayer recovery did not find a schedulable next alarm variant=%s", variant.name)
            }
            scheduleOutcome(scheduled, runAttemptCount)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Prayer alarm recovery failed attempt=%d variant=%s", runAttemptCount + 1, variant.name)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        internal const val MAX_RETRIES = 2

        internal fun scheduleOutcome(scheduled: Boolean, runAttemptCount: Int): ListenableWorker.Result =
            when {
                scheduled -> Result.success()
                runAttemptCount < MAX_RETRIES -> Result.retry()
                else -> Result.failure()
            }

        private const val KEY_VARIANT = "prayer_alarm_recovery_variant"
        private const val WORK_PREFIX = "prayer_alarm_recovery_"
        private const val INITIAL_DELAY_MINUTES = 5L
        private const val BACKOFF_MINUTES = 10L

        suspend fun enqueue(context: Context, variant: PrayerAppVariant) {
            val request = OneTimeWorkRequestBuilder<PrayerAlarmRecoveryWorker>()
                .setInitialDelay(INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_VARIANT to variant.name))
                .build()
            // One outstanding request per app variant; no duplicate worker on repeated alarms.
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_PREFIX + variant.name,
                ExistingWorkPolicy.KEEP,
                request,
            ).await()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerAlarmRecoveryEntryPoint {
    fun recoveryPrayerAlarmScheduler(): PrayerAlarmScheduler

    fun recoveryPrayerPreferencesDataSource(): PrayerPreferencesDataSource
}
