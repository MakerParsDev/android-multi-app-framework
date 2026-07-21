package com.parsfilo.contentapp.core.database.model.quran

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quran_suras",
    indices = [
        Index(value = ["nameLatin"]),
        Index(value = ["nameTurkish"]),
    ],
)
data class QuranSuraEntity(
    @PrimaryKey val number: Int,
    val nameArabic: String,
    val nameLatin: String,
    val nameTurkish: String,
    val nameEnglish: String,
    val revelationType: String,
    val ayahCount: Int,
)
