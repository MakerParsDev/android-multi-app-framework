package com.parsfilo.contentapp.feature.prayertimes.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerTimesRepository
import com.parsfilo.contentapp.feature.prayertimes.widget.PrayerTimesWidgetReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
        Timber.d("PrayerTimesRefreshWorker refreshing districtId=%s", districtId)
        deps.prayerTimesRepository().refreshIfNeeded(districtId = districtId, force = false)
        deps.prayerAlarmScheduler().scheduleNextForCurrentFlavor()
        runCatching { PrayerTimesWidgetReceiver.refreshAll(applicationContext) }
            .onFailure { error ->
                Timber.w(error, "PrayerTimesRefreshWorker widget refresh failed")
            }
        Timber.d("PrayerTimesRefreshWorker finished: success")
        return Result.success()
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
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerTimesWorkerEntryPoint {
    fun prayerTimesRepository(): PrayerTimesRepository

    fun prayerPreferencesDataSource(): PrayerPreferencesDataSource

    fun prayerAlarmScheduler(): PrayerAlarmScheduler
}
