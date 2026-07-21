package com.parsfilo.contentapp.feature.counter.data

import com.parsfilo.contentapp.core.database.model.zikir.ZikirStreakEntity
import com.parsfilo.contentapp.feature.counter.model.ZikirItem
import com.parsfilo.contentapp.feature.counter.model.ZikirSession
import kotlinx.coroutines.flow.Flow

interface ZikirRepository {
    fun getZikirList(): List<ZikirItem>

    suspend fun saveSession(session: ZikirSession)

    fun getRecentSessions(limit: Int = 30): Flow<List<ZikirSession>>

    fun getTodayTotalCount(): Flow<Int>

    fun getTodayCompletedSessionCount(): Flow<Int>

    suspend fun getOrCreateStreak(): ZikirStreakEntity

    fun observeStreak(): Flow<ZikirStreakEntity>

    suspend fun updateStreakAfterSession()
}
