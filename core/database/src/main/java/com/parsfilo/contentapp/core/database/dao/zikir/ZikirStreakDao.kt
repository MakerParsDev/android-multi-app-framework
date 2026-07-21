package com.parsfilo.contentapp.core.database.dao.zikir

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.parsfilo.contentapp.core.database.model.zikir.ZikirStreakEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ZikirStreakDao {
    @Query("SELECT * FROM zikir_streak WHERE id = 1")
    suspend fun getStreak(): ZikirStreakEntity?

    @Query("SELECT * FROM zikir_streak WHERE id = 1")
    fun observeStreak(): Flow<ZikirStreakEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(streak: ZikirStreakEntity)
}
