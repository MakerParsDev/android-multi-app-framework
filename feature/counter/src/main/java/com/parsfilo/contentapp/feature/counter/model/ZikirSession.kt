package com.parsfilo.contentapp.feature.counter.model

data class ZikirSession(
    val id: Long = 0,
    val zikirKey: String,
    val arabicText: String,
    val latinText: String,
    val targetCount: Int,
    val completedCount: Int,
    val completedAt: Long,
    val durationSeconds: Long,
    val isComplete: Boolean,
)
