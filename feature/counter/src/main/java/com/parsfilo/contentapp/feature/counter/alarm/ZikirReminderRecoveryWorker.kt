package com.parsfilo.contentapp.feature.counter.alarm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Persistently tries to restore the one-shot reminder after an exceptional receiver rearm failure.
 * All retries call the existing preference-aware, stable-PendingIntent scheduler.
 */
class ZikirReminderRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result =
        try {
            val entry = EntryPointAccessors.fromApplication(
                applicationContext,
                ZikirReminderRecoveryEntryPoint::class.java,
            )
            // Null is expected when the reminder has since been disabled.
            entry.recoveryZikirReminderScheduler().scheduleOrCancelFromPreferences()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Zikir reminder recovery failed attempt=%d", runAttemptCount + 1)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }

    companion object {
        internal const val MAX_RETRIES = 2
        private const val UNIQUE_WORK_NAME = "zikir_reminder_recovery"
        private const val INITIAL_DELAY_MINUTES = 5L
        private const val BACKOFF_MINUTES = 10L

        suspend fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ZikirReminderRecoveryWorker>()
                .setInitialDelay(INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            ).await()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ZikirReminderRecoveryEntryPoint {
    fun recoveryZikirReminderScheduler(): ZikirReminderScheduler
}
