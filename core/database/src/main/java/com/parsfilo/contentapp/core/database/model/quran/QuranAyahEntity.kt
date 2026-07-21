package com.parsfilo.contentapp.core.database.model.quran

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "quran_ayahs",
    primaryKeys = ["suraNumber", "ayahNumber"],
    indices = [
        Index(value = ["suraNumber"]),
    ],
)
data class QuranAyahEntity(
    val suraNumber: Int,
    val ayahNumber: Int,
    val arabic: String,
    val latin: String,
    val turkish: String,
    val english: String,
    val german: String,
)
