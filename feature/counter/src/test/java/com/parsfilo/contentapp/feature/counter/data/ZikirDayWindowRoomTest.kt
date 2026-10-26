package com.parsfilo.contentapp.feature.counter.data

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.AppDatabase
import com.parsfilo.contentapp.core.database.model.zikir.ZikirSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.TimeZone

/** Disposable in-memory database; never modifies a physical user's zikirmatik sessions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ZikirDayWindowRoomTest {
    @Test
    fun roomSumsOnlyCurrentBoundedLocalCalendarDay() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).build()
        try {
            val zone = TimeZone.getTimeZone("Europe/Istanbul")
            val today = Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
            val (dayAStart, dayBStart) = localDayBounds(today, zone)
            val (_, dayCStart) = localDayBounds(dayBStart + 10_000L, zone)
            val dao = db.zikirSessionDao()
            for ((id, completedAt, count) in listOf(
                Triple(1L, dayAStart + 300_000L, 33),
                Triple(2L, dayBStart + 300_000L, 21),
                Triple(3L, dayCStart + 300_000L, 100),
            )) {
                dao.insert(
                    ZikirSessionEntity(
                        id = id,
                        zikirKey = "synthetic_only",
                        arabicText = "",
                        latinText = "synthetic",
                        targetCount = count,
                        completedCount = count,
                        completedAt = completedAt,
                        durationSeconds = 3,
                        isComplete = true,
                    ),
                )
            }
            assertThat(dao.getTotalCountBetween(dayAStart, dayBStart).first()).isEqualTo(33)
            assertThat(dao.getTotalCountBetween(dayBStart, dayCStart).first()).isEqualTo(21)
            assertThat(dao.getCompletedSessionCountBetween(dayAStart, dayBStart).first()).isEqualTo(1)
            assertThat(dao.getCompletedSessionCountBetween(dayBStart, dayCStart).first()).isEqualTo(1)
        } finally {
            db.close()
        }
    }
}
