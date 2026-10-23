package com.parsfilo.contentapp.feature.counter.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.counter.alarm.ReminderScheduleMode
import org.junit.Test

class CounterReminderUiEventTest {
    @Test fun missingScheduleShowsFailureNotSuccess() {
        assertThat(reminderFeedbackFor(ReminderScheduleMode.UNAVAILABLE))
            .isEqualTo(CounterReminderUiEvent.ReminderScheduleUnavailable)
    }

    @Test fun confirmedExactScheduleShowsSuccess() {
        assertThat(reminderFeedbackFor(ReminderScheduleMode.EXACT))
            .isEqualTo(CounterReminderUiEvent.ReminderSaved)
    }

    @Test fun confirmedInexactScheduleShowsSuccess() {
        assertThat(reminderFeedbackFor(ReminderScheduleMode.INEXACT_FALLBACK))
            .isEqualTo(CounterReminderUiEvent.ReminderSaved)
    }
}
