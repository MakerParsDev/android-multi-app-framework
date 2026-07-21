package com.parsfilo.contentapp.core.database.model.quran

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "quran_audio_cache",
    primaryKeys = ["reciterId", "suraNumber", "ayahNumber"],
    indices = [
        Index(value = ["reciterId", "suraNumber"]),
    ],
)
data class QuranAudioCacheEntity(
    val reciterId: String,
    val suraNumber: Int,
    val ayahNumber: Int,
    val filePath: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L,
)
