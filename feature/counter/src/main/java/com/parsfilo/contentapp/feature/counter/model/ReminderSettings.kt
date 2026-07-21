package com.parsfilo.contentapp.feature.counter.model

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    val dailyGoal: Int = 100,
    val streakReminderEnabled: Boolean = true,
)
