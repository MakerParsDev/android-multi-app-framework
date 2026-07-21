package com.parsfilo.contentapp.feature.counter.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.feature.counter.data.ZikirRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import timber.log.Timber

class ZikirStreakCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Timber.d("ZikirStreakCheckWorker started runAttempt=%d", runAttemptCount)
        val deps = EntryPointAccessors.fromApplication(
            applicationContext,
            ZikirStreakWorkerEntryPoint::class.java,
        )

        val prefs = deps.preferencesDataSource()
        val streakReminderEnabled = prefs.zikirStreakReminderEnabled.first()
        if (!streakReminderEnabled) {
            Timber.d("ZikirStreakCheckWorker skipped: reminder disabled")
            return Result.success()
        }

        val dailyGoal = prefs.zikirDailyGoal.first()
        val todayTotal = deps.zikirRepository().getTodayTotalCount().first()
        if (todayTotal >= dailyGoal) {
            Timber.d(
                "ZikirStreakCheckWorker skipped: goal reached todayTotal=%d dailyGoal=%d",
                todayTotal,
                dailyGoal,
            )
            return Result.success()
        }

        val streak = deps.zikirRepository().getOrCreateStreak()
        if (streak.currentStreak <= 0) {
            Timber.d("ZikirStreakCheckWorker skipped: no active streak")
            return Result.success()
        }

        deps.zikirReminderNotifier().showStreakReminder(streak.currentStreak)
        Timber.d(
            "ZikirStreakCheckWorker reminder shown currentStreak=%d todayTotal=%d dailyGoal=%d",
            streak.currentStreak,
            todayTotal,
            dailyGoal,
        )
        return Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ZikirStreakWorkerEntryPoint {
    fun zikirRepository(): ZikirRepository

    fun preferencesDataSource(): PreferencesDataSource

    fun zikirReminderNotifier(): ZikirReminderNotifier
}
