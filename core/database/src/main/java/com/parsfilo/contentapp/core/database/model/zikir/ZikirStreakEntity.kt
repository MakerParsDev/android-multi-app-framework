package com.parsfilo.contentapp.core.database.model.zikir

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zikir_streak")
data class ZikirStreakEntity(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastActivityDate: String = "",
)
