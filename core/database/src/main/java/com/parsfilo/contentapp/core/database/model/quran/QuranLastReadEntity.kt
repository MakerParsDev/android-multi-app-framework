package com.parsfilo.contentapp.core.database.model.quran

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quran_last_read")
data class QuranLastReadEntity(
    @PrimaryKey val id: Int = 1,
    val suraNumber: Int,
    val ayahNumber: Int,
    val savedAt: Long = System.currentTimeMillis(),
)
