package com.parsfilo.contentapp.feature.counter.data

import android.content.Context
import androidx.room.withTransaction
import com.parsfilo.contentapp.core.database.AppDatabase
import com.parsfilo.contentapp.core.database.dao.zikir.ZikirSessionDao
import com.parsfilo.contentapp.core.database.dao.zikir.ZikirStreakDao
import com.parsfilo.contentapp.core.database.model.zikir.ZikirSessionEntity
import com.parsfilo.contentapp.core.database.model.zikir.ZikirStreakEntity
import com.parsfilo.contentapp.feature.counter.model.ZikirItem
import com.parsfilo.contentapp.feature.counter.model.ZikirSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ZikirRepositoryImpl @Inject constructor(
    private val zikirSessionDao: ZikirSessionDao,
    private val zikirStreakDao: ZikirStreakDao,
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
) : ZikirRepository {

    @Volatile
    private var cachedZikirList: List<ZikirItem>? = null

    override fun getZikirList(): List<ZikirItem> {
        cachedZikirList?.let { return it }
        val parsed = runCatching {
            val json = context.assets.open("data.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ZikirItem(
                            key = obj.optString("key"),
                            arabicText = obj.optString("arabicText"),
                            latinText = obj.optString("latinText"),
                            turkishMeaning = obj.optString("turkishMeaning"),
                            defaultTarget = obj.optInt("defaultTarget", 33),
                            virtue = obj.optString("virtue"),
                            virtueSource = obj.optString("virtueSource"),
                        )
                    )
                }
            }
        }.getOrElse { error ->
            Timber.e(error, "Failed to parse zikirmatik data.json")
            emptyList()
        }
        cachedZikirList = parsed
        return parsed
    }

    override suspend fun saveSession(session: ZikirSession): Boolean = database.withTransaction {
        require(session.id != 0L) { "Completed sessions require a stable nonzero ID" }
        val insertedId = zikirSessionDao.insert(session.toEntity())
        if (insertedId == -1L) {
            // Retrying a save that already committed is a success, not a new session.
            return@withTransaction false
        }
        updateStreakAfterSession(session.completedAt)
        true
    }

    override fun getRecentSessions(limit: Int): Flow<List<ZikirSession>> {
        return zikirSessionDao.getRecentSessions(limit).map { list ->
            list.map { it.toModel() }
        }
    }

    override fun getTodayTotalCount(): Flow<Int> =
        queryCurrentLocalDayWindow { since, before ->
            zikirSessionDao.getTotalCountBetween(since, before).map { it ?: 0 }
        }

    override fun getTodayCompletedSessionCount(): Flow<Int> =
        queryCurrentLocalDayWindow { since, before ->
            zikirSessionDao.getCompletedSessionCountBetween(since, before)
        }

    override suspend fun getOrCreateStreak(): ZikirStreakEntity {
        return zikirStreakDao.getStreak() ?: ZikirStreakEntity().also { zikirStreakDao.upsert(it) }
    }

    override fun observeStreak(): Flow<ZikirStreakEntity> =
        combine(
            zikirStreakDao.observeStreak(),
            queryCurrentLocalDay { cutoff -> flowOf(cutoff) },
        ) { persisted, dayStart ->
            val streak = persisted ?: ZikirStreakEntity()
            streak.copy(
                currentStreak = effectiveActiveStreak(
                    streak = streak,
                    nowMillis = dayStart,
                    zone = TimeZone.getDefault(),
                ),
            )
        }.distinctUntilChanged()

    private suspend fun updateStreakAfterSession(completedAt: Long) {
        // A completed session can be replayed after a process crash, including
        // after local midnight. Attribute the streak to when it actually ended,
        // rather than when Room eventually persisted the recovery checkpoint.
        val calendar = Calendar.getInstance().apply { timeInMillis = completedAt }
        val format = dateFormatter()
        val completionDay = format.format(calendar.time)
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        val previousDay = format.format(calendar.time)
        val current = getOrCreateStreak()

        val updated = when {
            current.lastActivityDate >= completionDay -> current
            current.lastActivityDate == previousDay -> {
                val next = current.currentStreak + 1
                current.copy(
                    currentStreak = next,
                    longestStreak = max(current.longestStreak, next),
                    lastActivityDate = completionDay,
                )
            }
            else -> current.copy(
                currentStreak = 1,
                longestStreak = max(current.longestStreak, 1),
                lastActivityDate = completionDay,
            )
        }

        zikirStreakDao.upsert(updated)
    }

    private fun dateFormatter(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
}

internal fun ZikirSession.toEntity(): ZikirSessionEntity {
    return ZikirSessionEntity(
        id = id,
        zikirKey = zikirKey,
        arabicText = arabicText,
        latinText = latinText,
        targetCount = targetCount,
        completedCount = completedCount,
        completedAt = completedAt,
        durationSeconds = durationSeconds,
        isComplete = isComplete,
    )
}

internal fun ZikirSessionEntity.toModel(): ZikirSession {
    return ZikirSession(
        id = id,
        zikirKey = zikirKey,
        arabicText = arabicText,
        latinText = latinText,
        targetCount = targetCount,
        completedCount = completedCount,
        completedAt = completedAt,
        durationSeconds = durationSeconds,
        isComplete = isComplete,
    )
}

/**
 * Re-subscribe to the Room query after local midnight, even if no rows change.
 * The minute-long upper bound also detects time-zone and manual clock changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> queryCurrentLocalDay(
    nowMillis: () -> Long = System::currentTimeMillis,
    timeZone: () -> TimeZone = TimeZone::getDefault,
    query: (Long) -> Flow<T>,
): Flow<T> = queryCurrentLocalDayWindow(nowMillis, timeZone) { since, _ -> query(since) }

/** A bounded [start, nextMidnight) SQL window prevents future-dated rows entering today's totals. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> queryCurrentLocalDayWindow(
    nowMillis: () -> Long = System::currentTimeMillis,
    timeZone: () -> TimeZone = TimeZone::getDefault,
    query: (Long, Long) -> Flow<T>,
): Flow<T> = flow {
    while (true) {
        val now = nowMillis()
        val (startOfDay, nextMidnight) = localDayBounds(now, timeZone())
        emit(startOfDay to nextMidnight)
        delay((nextMidnight - now).coerceIn(1L, 60_000L))
    }
}.distinctUntilChanged().flatMapLatest { (since, before) -> query(since, before) }

/** Correct across leap days, DST changes and non-hour time-zone offsets. */
internal fun localDayBounds(nowMillis: Long, timeZone: TimeZone): Pair<Long, Long> {
    val calendar = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    return start to calendar.timeInMillis
}

/**
 * A recorded streak is still active today when completed today or yesterday.
 * A missed full local calendar day breaks the active streak, but never erases
 * persisted history or the longest-streak record.
 */
internal fun effectiveActiveStreak(
    streak: ZikirStreakEntity,
    nowMillis: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
): Int {
    if (streak.currentStreak <= 0 || streak.lastActivityDate.isBlank()) return 0
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
    val today = format.format(calendar.time)
    calendar.add(Calendar.DAY_OF_MONTH, -1)
    val yesterday = format.format(calendar.time)
    return if (streak.lastActivityDate == today || streak.lastActivityDate == yesterday) {
        streak.currentStreak
    } else {
        0
    }
}
