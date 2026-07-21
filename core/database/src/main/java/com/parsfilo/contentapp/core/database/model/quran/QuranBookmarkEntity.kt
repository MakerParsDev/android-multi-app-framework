package com.parsfilo.contentapp.core.database.model.quran

import androidx.room.Entity

@Entity(
    tableName = "quran_bookmarks",
    primaryKeys = ["suraNumber", "ayahNumber"],
)
data class QuranBookmarkEntity(
    val suraNumber: Int,
    val ayahNumber: Int,
    val savedAt: Long = System.currentTimeMillis(),
    val note: String = "",
)
