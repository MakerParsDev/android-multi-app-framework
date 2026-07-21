package com.parsfilo.contentapp.core.database.model.zikir

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "zikir_sessions",
    indices = [
        Index(value = ["completedAt"]),
        Index(value = ["zikirKey", "completedAt"]),
    ],
)
data class ZikirSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val zikirKey: String,
    val arabicText: String,
    val latinText: String,
    val targetCount: Int,
    val completedCount: Int,
    val completedAt: Long,
    val durationSeconds: Long,
    val isComplete: Boolean,
)
