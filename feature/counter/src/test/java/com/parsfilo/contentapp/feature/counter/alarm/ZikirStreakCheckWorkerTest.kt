package com.parsfilo.contentapp.feature.counter.alarm

import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.model.zikir.ZikirStreakEntity
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.feature.counter.data.ZikirRepository
import dagger.hilt.android.EntryPointAccessors
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ZikirStreakCheckWorkerTest {
    private val preferences = mockk<PreferencesDataSource>()
    private val repository = mockk<ZikirRepository>()
    private val notifier = mockk<ZikirReminderNotifier>(relaxed = true)
    private val entryPoint = mockk<ZikirStreakWorkerEntryPoint>()

    @Before
    fun setUp() {
        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(
                RuntimeEnvironment.getApplication(),
                ZikirStreakWorkerEntryPoint::class.java,
            )
        } returns entryPoint
        every { entryPoint.preferencesDataSource() } returns preferences
        every { entryPoint.zikirRepository() } returns repository
        every { entryPoint.zikirReminderNotifier() } returns notifier
        every { preferences.zikirStreakReminderEnabled } returns flowOf(true)
        every { preferences.zikirDailyGoal } returns flowOf(33)
        every { repository.getTodayTotalCount() } returns flowOf(0)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun expiredStreakDoesNotNotifyEvenWhenPersistedCountIsPositive() = runTest {
        coEvery { repository.getOrCreateStreak() } returns savedStreak(daysAgo = 2, count = 12)

        assertThat(runWorker()).isEqualTo(androidx.work.ListenableWorker.Result.success())
        coVerify(exactly = 0) { notifier.showStreakReminder(any()) }
        verify(exactly = 1) { notifier.cancelStreakReminder() }
    }

    @Test
    fun eligibleStreakNotifiesWithDateValidCount() = runTest {
        coEvery { repository.getOrCreateStreak() } returns savedStreak(daysAgo = 1, count = 4)

        assertThat(runWorker()).isEqualTo(androidx.work.ListenableWorker.Result.success())
        coVerify(exactly = 1) { notifier.showStreakReminder(4) }
        verify(exactly = 0) { notifier.cancelStreakReminder() }
    }

    @Test
    fun disabledReminderNeverReadsStreakOrNotifies() = runTest {
        every { preferences.zikirStreakReminderEnabled } returns flowOf(false)

        assertThat(runWorker()).isEqualTo(androidx.work.ListenableWorker.Result.success())
        coVerify(exactly = 0) { repository.getOrCreateStreak() }
        coVerify(exactly = 0) { notifier.showStreakReminder(any()) }
        verify(exactly = 1) { notifier.cancelStreakReminder() }
    }

    @Test
    fun completedGoalDoesNotNotify() = runTest {
        every { repository.getTodayTotalCount() } returns flowOf(33)

        assertThat(runWorker()).isEqualTo(androidx.work.ListenableWorker.Result.success())
        coVerify(exactly = 0) { repository.getOrCreateStreak() }
        coVerify(exactly = 0) { notifier.showStreakReminder(any()) }
        verify(exactly = 1) { notifier.cancelStreakReminder() }
    }

    private suspend fun runWorker(): androidx.work.ListenableWorker.Result {
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.runAttemptCount } returns 0
        return ZikirStreakCheckWorker(RuntimeEnvironment.getApplication(), parameters).doWork()
    }

    private fun savedStreak(daysAgo: Int, count: Int): ZikirStreakEntity {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -daysAgo) }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        return ZikirStreakEntity(currentStreak = count, longestStreak = count + 5, lastActivityDate = date)
    }
}
