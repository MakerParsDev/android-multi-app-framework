package com.parsfilo.contentapp.feature.counter.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.AppDatabase
import com.parsfilo.contentapp.core.datastore.ActiveZikirSessionSnapshot
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.feature.counter.model.ZikirSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** Uses only disposable test databases and preferences; no user data is touched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ZikirCompletionAtomicityTest {
    @Test
    fun pendingCompletionReplaysExactlyOnceAcrossRealDiskAndRoomRestarts() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefsDir = Files.createTempDirectory("amf486-pending-").toFile()
        val prefsFile = prefsDir.resolve("active-session.preferences_pb")
        val databaseName = "amf486-pending-${UUID.randomUUID()}.db"
        val now = System.currentTimeMillis()
        val pending = ActiveZikirSessionSnapshot(
            zikirKey = "subhanallah",
            sessionId = 8132459232L,
            count = 33,
            target = 33,
            startedAt = now - 2_000L,
            updatedAt = now,
        )
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun preferences() = PreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { prefsFile }),
        )

        fun database() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()

        fun repository(db: AppDatabase) = ZikirRepositoryImpl(
            zikirSessionDao = db.zikirSessionDao(),
            zikirStreakDao = db.zikirStreakDao(),
            database = db,
            context = context,
        )

        try {
            // The completion checkpoint reached durable DataStore, but Room has not committed.
            preferences().setActiveZikirSessionSnapshot(pending)
            assertThat(prefsFile.exists()).isTrue()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            val recovered = preferences().activeZikirSessionSnapshot.first()
            assertThat(recovered).isEqualTo(pending)
            val session = ZikirSession(
                id = recovered!!.sessionId,
                zikirKey = recovered.zikirKey,
                arabicText = "synthetic",
                latinText = "synthetic",
                targetCount = recovered.target,
                completedCount = recovered.count,
                completedAt = recovered.updatedAt,
                durationSeconds = (recovered.updatedAt - recovered.startedAt) / 1_000L,
                isComplete = true,
            )
            // First replay commits history/streak, then a crash occurs BEFORE snapshot cleanup.
            val firstDb = database()
            try {
                assertThat(repository(firstDb).saveSession(session)).isTrue()
            } finally {
                firstDb.close()
            }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            // A second restart must replay the SAME ID, not append history or advance streak.
            val nextPrefs = preferences()
            assertThat(nextPrefs.activeZikirSessionSnapshot.first()?.sessionId).isEqualTo(session.id)
            val secondDb = database()
            try {
                val repo = repository(secondDb)
                assertThat(repo.saveSession(session)).isFalse()
                assertThat(repo.getRecentSessions().first()).hasSize(1)
                assertThat(repo.getTodayTotalCount().first()).isEqualTo(33)
                assertThat(repo.getTodayCompletedSessionCount().first()).isEqualTo(1)
                assertThat(repo.observeStreak().first().currentStreak).isEqualTo(1)
                nextPrefs.clearActiveZikirSessionIfId(session.id)
                assertThat(nextPrefs.activeZikirSessionSnapshot.first()).isNull()
            } finally {
                secondDb.close()
            }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            // A third fresh process has neither a stale completion nor duplicated Room records.
            assertThat(preferences().activeZikirSessionSnapshot.first()).isNull()
            val thirdDb = database()
            try {
                val repo = repository(thirdDb)
                assertThat(repo.getRecentSessions().first()).hasSize(1)
                assertThat(repo.getTodayTotalCount().first()).isEqualTo(33)
                assertThat(repo.observeStreak().first().currentStreak).isEqualTo(1)
            } finally {
                thirdDb.close()
            }
        } finally {
            scope.cancel()
            context.deleteDatabase(databaseName)
            prefsDir.deleteRecursively()
        }
    }

    @Test
    fun delayedYesterdayCompletionKeepsYesterdayStreakDateAndDoesNotInventTodayProgress() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = ZikirRepositoryImpl(
                zikirSessionDao = database.zikirSessionDao(),
                zikirStreakDao = database.zikirStreakDao(),
                database = database,
                context = context,
            )
            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -1)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val expectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(yesterday.time)
            val delayedSession = ZikirSession(
                id = 8132459233L,
                zikirKey = "synthetic_yesterday_completion",
                arabicText = "synthetic",
                latinText = "synthetic",
                targetCount = 33,
                completedCount = 33,
                completedAt = yesterday.timeInMillis,
                durationSeconds = 42,
                isComplete = true,
            )
            // Room save simulates a crash-recovered completion being replayed today.
            assertThat(repo.saveSession(delayedSession)).isTrue()
            assertThat(database.zikirStreakDao().getStreak()!!.lastActivityDate).isEqualTo(expectedDate)
            assertThat(repo.getTodayCompletedSessionCount().first()).isEqualTo(0)
            assertThat(repo.getTodayTotalCount().first()).isEqualTo(0)
            assertThat(repo.observeStreak().first().currentStreak).isEqualTo(1)

            // Once today's activity really completes, it should extend the
            // yesterday streak, not collapse the days into a single record.
            val today = (yesterday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(today.time)
            assertThat(
                repo.saveSession(delayedSession.copy(id = 8132459234L, completedAt = today.timeInMillis)),
            ).isTrue()
            val afterToday = database.zikirStreakDao().getStreak()!!
            assertThat(afterToday.lastActivityDate).isEqualTo(todayDate)
            assertThat(afterToday.currentStreak).isEqualTo(2)
            assertThat(afterToday.longestStreak).isEqualTo(2)

            // A second older completion reaching Room late must not roll the
            // persisted last activity back to yesterday or inflate the streak.
            assertThat(repo.saveSession(delayedSession.copy(id = 8132459235L))).isTrue()
            val afterLateReplay = database.zikirStreakDao().getStreak()!!
            assertThat(afterLateReplay.lastActivityDate).isEqualTo(todayDate)
            assertThat(afterLateReplay.currentStreak).isEqualTo(2)
            assertThat(afterLateReplay.longestStreak).isEqualTo(2)
            assertThat(repo.getRecentSessions().first()).hasSize(3)
            assertThat(repo.getTodayCompletedSessionCount().first()).isEqualTo(1)
        } finally {
            database.close()
        }
    }

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
