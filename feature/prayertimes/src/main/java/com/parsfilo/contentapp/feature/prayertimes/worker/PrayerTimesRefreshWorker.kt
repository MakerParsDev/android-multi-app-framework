package com.parsfilo.contentapp.feature.prayertimes.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerTimesRepository
import com.parsfilo.contentapp.feature.prayertimes.model.RefreshResult
import com.parsfilo.contentapp.feature.prayertimes.widget.PrayerTimesWidgetReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

class PrayerTimesRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Timber.d("PrayerTimesRefreshWorker started runAttempt=%d", runAttemptCount)
        val deps = EntryPointAccessors.fromApplication(
            applicationContext,
            PrayerTimesWorkerEntryPoint::class.java,
        )

        val preferences = deps.prayerPreferencesDataSource().preferences.first()

        val districtId = preferences.selectedDistrictId ?: run {
            Timber.d("PrayerTimesRefreshWorker skipped: district is not selected")
            return Result.success()
        }
        val result = PrayerTimesRefreshExecution.run(
            repository = deps.prayerTimesRepository(),
            scheduler = deps.prayerAlarmScheduler(),
            districtId = districtId,
            alarmEnabled = preferences.alarmEnabled,
        )
        if (result == Result.success()) {
            runCatching { PrayerTimesWidgetReceiver.refreshAll(applicationContext) }
                .onFailure { error ->
                    if (error is CancellationException || error !is Exception) throw error
                    Timber.w("PrayerTimesRefreshWorker widget refresh failed (%s)", error.javaClass.simpleName)
                }
        }
        return result
    }

    companion object {
        private const val WORK_NAME = "prayer_times_daily_refresh"
        private const val REPEAT_INTERVAL_HOURS = 24L
        private const val FLEX_INTERVAL_HOURS = 2L

        fun schedule(context: Context) {
            Timber.d(
                "Scheduling PrayerTimesRefreshWorker repeatHours=%d flexHours=%d",
                REPEAT_INTERVAL_HOURS,
                FLEX_INTERVAL_HOURS,
            )
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PrayerTimesRefreshWorker>(
                REPEAT_INTERVAL_HOURS,
                TimeUnit.HOURS,
                FLEX_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                // Retry transient API/alarm failures with bounded exponential delay,
                // rather than waiting for the next 24-hour periodic window.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

/** Small, testable boundary between prayer data refresh and periodic WorkManager outcomes. */
internal object PrayerTimesRefreshExecution {
    suspend fun run(
        repository: PrayerTimesRepository,
        scheduler: PrayerAlarmScheduler,
        districtId: Int,
        alarmEnabled: Boolean,
    ): ListenableWorker.Result {
        val refresh = runCatching {
            // The worker owns the scheduling result so it can request retry on failure.
            // Do not let the repository schedule once more while refreshing API data.
            repository.refreshIfNeeded(
                districtId = districtId,
                force = false,
                scheduleAlarmAfterRefresh = false,
            )
        }.getOrElse { error ->
            // Preserve cancellation and fatal errors: retry only recoverable exceptions.
            if (error is CancellationException || error !is Exception) throw error
            Timber.w(error, "Prayer refresh threw; retrying")
            return ListenableWorker.Result.retry()
        }

        when (refresh) {
            is RefreshResult.Failed -> {
                // Some repository implementations wrap cancellation in Failed.
                if (refresh.throwable is CancellationException || refresh.throwable !is Exception) {
                    throw refresh.throwable
                }
                Timber.w("Prayer refresh failed (%s); retrying", refresh.throwable.javaClass.simpleName)
                return ListenableWorker.Result.retry()
            }
            RefreshResult.InvalidSelection -> {
                // Retrying a user-selected invalid district cannot fix it.
                Timber.w("Prayer refresh skipped: invalid location selection")
                return ListenableWorker.Result.success()
            }
            RefreshResult.Refreshed,
            RefreshResult.SkippedFresh,
            RefreshResult.ServedFromCache -> Unit
        }

        val scheduled = runCatching {
            scheduler.scheduleNextForCurrentFlavor()
        }.getOrElse { error ->
            if (error is CancellationException || error !is Exception) throw error
            Timber.w(error, "Prayer alarm scheduling threw; retrying")
            return ListenableWorker.Result.retry()
        }
        if (alarmEnabled && !scheduled) {
            // A cache hit (including stale data served during an API outage) is
            // not sufficient when no upcoming alarm can actually be scheduled.
            Timber.w("Prayer alarm enabled but no upcoming alarm was scheduled; retrying")
            return ListenableWorker.Result.retry()
        }
        Timber.d("PrayerTimesRefreshWorker finished: %s", refresh.javaClass.simpleName)
        return ListenableWorker.Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerTimesWorkerEntryPoint {
    fun prayerTimesRepository(): PrayerTimesRepository

    fun prayerPreferencesDataSource(): PrayerPreferencesDataSource

    fun prayerAlarmScheduler(): PrayerAlarmScheduler
}
