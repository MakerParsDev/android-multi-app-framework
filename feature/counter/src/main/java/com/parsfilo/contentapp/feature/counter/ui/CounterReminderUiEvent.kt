package com.parsfilo.contentapp.feature.counter.ui

sealed interface CounterReminderUiEvent {
    data object RequestExactAlarmPermission : CounterReminderUiEvent

    data object ExactAlarmPermissionGranted : CounterReminderUiEvent

    data object ExactAlarmPermissionStillMissing : CounterReminderUiEvent
}
