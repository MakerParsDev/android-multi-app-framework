package com.parsfilo.contentapp.feature.counter.model

enum class CounterHapticEvent {
    TAP,
    TEN,
    TARGET,
}

data class CounterUiState(
    val isLoading: Boolean = true,
    val zikirList: List<ZikirItem> = emptyList(),
    val selectedZikir: ZikirItem? = null,
    val currentCount: Int = 0,
    val targetCount: Int = 33,
    val sessionStartTime: Long = 0L,
    val isSessionActive: Boolean = false,
    val todayTotalCount: Int = 0,
    val completedSessionsToday: Int = 0,
    val dailyGoal: Int = 100,
    val dailyGoalProgress: Float = 0f,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val recentSessions: List<ZikirSession> = emptyList(),
    val showZikirSelector: Boolean = false,
    val showSessionComplete: Boolean = false,
    val showReminderSettings: Boolean = false,
    val showSessionHistory: Boolean = false,
    val showSharePreview: Boolean = false,
    val shareText: String = "",
    val isHapticEnabled: Boolean = true,
    val isSoundEnabled: Boolean = false,
    val reminderSettings: ReminderSettings = ReminderSettings(),
    val isPremium: Boolean = false,
    val lastCompletedSession: ZikirSession? = null,
    val historyUnlockedForSession: Boolean = false,
    val showFirstSessionReminderHint: Boolean = false,
)
