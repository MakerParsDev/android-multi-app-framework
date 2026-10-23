package com.parsfilo.contentapp.feature.counter.ui

import com.parsfilo.contentapp.feature.counter.alarm.ReminderScheduleMode

sealed interface CounterReminderUiEvent {
    data object ReminderSaved : CounterReminderUiEvent

    data object ReminderScheduleUnavailable : CounterReminderUiEvent

    data object RequestExactAlarmPermission : CounterReminderUiEvent

    data object ExactAlarmPermissionGranted : CounterReminderUiEvent

    data object ExactAlarmPermissionStillMissing : CounterReminderUiEvent
}

internal fun reminderFeedbackFor(mode: ReminderScheduleMode): CounterReminderUiEvent =
    if (mode == ReminderScheduleMode.UNAVAILABLE) {
        CounterReminderUiEvent.ReminderScheduleUnavailable
    } else {
        CounterReminderUiEvent.ReminderSaved
    }
