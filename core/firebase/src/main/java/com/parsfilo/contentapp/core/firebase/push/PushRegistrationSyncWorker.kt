package com.parsfilo.contentapp.core.firebase.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import java.util.concurrent.TimeUnit

class PushRegistrationSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reason = inputData.getString(KEY_REASON).orEmpty().ifBlank { DEFAULT_PERIODIC_REASON }
        Timber.d(
            "PushRegistrationSyncWorker started runAttempt=%d reason=%s isImmediate=%s",
            runAttemptCount,
            reason,
            inputData.getBoolean(KEY_IS_IMMEDIATE, false),
        )
        val deps = EntryPointAccessors.fromApplication(
            applicationContext,
            PushRegistrationSyncWorkerEntryPoint::class.java,
        )

        return runCatching {
            val synced = deps.pushRegistrationManager().syncRegistration(
                reason = reason,
                scheduleRetryOnFailure = false,
            )
            if (synced) {
                Timber.d("PushRegistrationSyncWorker finished: success")
                Result.success()
            } else {
                Timber.w("Push registration worker sync did not succeed (reason=%s).", reason)
                Result.retry()
            }
        }.getOrElse { throwable ->
            Timber.w(throwable, "Push registration worker sync failed (reason=%s).", reason)
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "push_registration_daily_sync"
        private const val IMMEDIATE_WORK_NAME = "push_registration_immediate_sync"
        private const val KEY_REASON = "reason"
        private const val KEY_IS_IMMEDIATE = "is_immediate"
        private const val DEFAULT_PERIODIC_REASON = "periodic_worker"
        private const val REPEAT_INTERVAL_HOURS = 24L
        private const val FLEX_INTERVAL_HOURS = 6L

        fun scheduleImmediate(
            context: Context,
            reason: String,
        ) {
            Timber.d("Scheduling immediate PushRegistrationSyncWorker reason=%s", reason)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<PushRegistrationSyncWorker>()
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        KEY_REASON to reason,
                        KEY_IS_IMMEDIATE to true,
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun schedule(context: Context) {
            Timber.d(
                "Scheduling PushRegistrationSyncWorker repeatHours=%d flexHours=%d",
                REPEAT_INTERVAL_HOURS,
                FLEX_INTERVAL_HOURS,
            )
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PushRegistrationSyncWorker>(
                REPEAT_INTERVAL_HOURS,
                TimeUnit.HOURS,
                FLEX_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushRegistrationSyncWorkerEntryPoint {
    fun pushRegistrationManager(): PushRegistrationManager
}
