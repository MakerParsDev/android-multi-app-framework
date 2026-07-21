package com.parsfilo.contentapp.core.database.dao.zikir

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.parsfilo.contentapp.core.database.model.zikir.ZikirSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ZikirSessionDao {
    @Insert
    suspend fun insert(session: ZikirSessionEntity): Long

    @Query("SELECT * FROM zikir_sessions ORDER BY completedAt DESC")
    fun getAllSessions(): Flow<List<ZikirSessionEntity>>

    @Query("SELECT * FROM zikir_sessions ORDER BY completedAt DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<ZikirSessionEntity>>

    @Query("SELECT SUM(completedCount) FROM zikir_sessions WHERE completedAt >= :sinceMs")
    fun getTotalCountSince(sinceMs: Long): Flow<Int?>

    @Query("SELECT COUNT(*) FROM zikir_sessions WHERE completedAt >= :sinceMs AND isComplete = 1")
    fun getCompletedSessionCountSince(sinceMs: Long): Flow<Int>

    @Query("SELECT SUM(completedCount) FROM zikir_sessions WHERE completedAt >= :sinceMs AND zikirKey = :key")
    fun getTotalForKeyToday(key: String, sinceMs: Long): Flow<Int?>
}
