package com.parsfilo.contentapp.feature.counter.data

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.AppDatabase
import com.parsfilo.contentapp.feature.counter.model.ZikirSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Entirely disposable in-memory Room database; no user data is touched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ZikirCompletionAtomicityTest {
    @Test
    fun repeatedStableIdIsStoredExactlyOnceAndDoesNotDoubleCountStreak() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        ).build()
        try {
            val repo = ZikirRepositoryImpl(
                zikirSessionDao = database.zikirSessionDao(),
                zikirStreakDao = database.zikirStreakDao(),
                database = database,
                context = context,
            )
            val session = ZikirSession(
                id = 8132459231L,
                zikirKey = "synthetic_zikir",
                arabicText = "synthetic",
                latinText = "synthetic",
                targetCount = 33,
                completedCount = 33,
                completedAt = System.currentTimeMillis(),
                durationSeconds = 42,
                isComplete = true,
            )
            assertThat(repo.saveSession(session)).isTrue()
            assertThat(repo.saveSession(session)).isFalse()
            assertThat(repo.getRecentSessions().first()).hasSize(1)
            assertThat(repo.getTodayTotalCount().first()).isEqualTo(33)
            assertThat(repo.getTodayCompletedSessionCount().first()).isEqualTo(1)
            assertThat(repo.observeStreak().first().currentStreak).isEqualTo(1)
        } finally {
            database.close()
        }
    }
}
