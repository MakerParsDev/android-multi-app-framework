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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
        updateStreakAfterSession()
        true
    }

    override fun getRecentSessions(limit: Int): Flow<List<ZikirSession>> {
        return zikirSessionDao.getRecentSessions(limit).map { list ->
            list.map { it.toModel() }
        }
    }

    override fun getTodayTotalCount(): Flow<Int> =
        queryCurrentLocalDay { cutoff ->
            zikirSessionDao.getTotalCountSince(cutoff).map { it ?: 0 }
        }

    override fun getTodayCompletedSessionCount(): Flow<Int> =
        queryCurrentLocalDay { cutoff ->
            zikirSessionDao.getCompletedSessionCountSince(cutoff)
        }

    override suspend fun getOrCreateStreak(): ZikirStreakEntity {
        return zikirStreakDao.getStreak() ?: ZikirStreakEntity().also { zikirStreakDao.upsert(it) }
    }

    override fun observeStreak(): Flow<ZikirStreakEntity> {
        return zikirStreakDao.observeStreak().map { it ?: ZikirStreakEntity() }
    }

    private suspend fun updateStreakAfterSession() {
        val today = todayKey()
        val yesterday = yesterdayKey()
        val current = getOrCreateStreak()

        val updated = when (current.lastActivityDate) {
            today -> current
            yesterday -> {
                val next = current.currentStreak + 1
                current.copy(
                    currentStreak = next,
                    longestStreak = max(current.longestStreak, next),
                    lastActivityDate = today,
                )
            }
            else -> {
                current.copy(
                    currentStreak = 1,
                    longestStreak = max(current.longestStreak, 1),
                    lastActivityDate = today,
                )
            }
        }

        zikirStreakDao.upsert(updated)
    }

    private fun todayKey(): String = dateFormatter().format(System.currentTimeMillis())

    private fun yesterdayKey(): String {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return dateFormatter().format(calendar.time)
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
): Flow<T> = flow {
    while (true) {
        val now = nowMillis()
        val zone = timeZone()
        val (startOfDay, nextMidnight) = localDayBounds(now, zone)
        emit(startOfDay)
        delay((nextMidnight - now).coerceIn(1L, 60_000L))
    }
}.distinctUntilChanged().flatMapLatest(query)

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
