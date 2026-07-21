package com.parsfilo.contentapp.feature.counter.data

import android.content.Context
import com.parsfilo.contentapp.core.database.dao.zikir.ZikirSessionDao
import com.parsfilo.contentapp.core.database.dao.zikir.ZikirStreakDao
import com.parsfilo.contentapp.core.database.model.zikir.ZikirSessionEntity
import com.parsfilo.contentapp.core.database.model.zikir.ZikirStreakEntity
import com.parsfilo.contentapp.feature.counter.model.ZikirItem
import com.parsfilo.contentapp.feature.counter.model.ZikirSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ZikirRepositoryImpl @Inject constructor(
    private val zikirSessionDao: ZikirSessionDao,
    private val zikirStreakDao: ZikirStreakDao,
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

    override suspend fun saveSession(session: ZikirSession) {
        zikirSessionDao.insert(session.toEntity())
    }

    override fun getRecentSessions(limit: Int): Flow<List<ZikirSession>> {
        return zikirSessionDao.getRecentSessions(limit).map { list ->
            list.map { it.toModel() }
        }
    }

    override fun getTodayTotalCount(): Flow<Int> {
        return zikirSessionDao.getTotalCountSince(startOfTodayMillis()).map { it ?: 0 }
    }

    override fun getTodayCompletedSessionCount(): Flow<Int> {
        return zikirSessionDao.getCompletedSessionCountSince(startOfTodayMillis())
    }

    override suspend fun getOrCreateStreak(): ZikirStreakEntity {
        return zikirStreakDao.getStreak() ?: ZikirStreakEntity().also { zikirStreakDao.upsert(it) }
    }

    override fun observeStreak(): Flow<ZikirStreakEntity> {
        return zikirStreakDao.observeStreak().map { it ?: ZikirStreakEntity() }
    }

    override suspend fun updateStreakAfterSession() {
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

    private fun startOfTodayMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
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
