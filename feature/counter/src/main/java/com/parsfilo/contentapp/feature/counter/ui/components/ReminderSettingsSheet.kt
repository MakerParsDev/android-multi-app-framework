package com.parsfilo.contentapp.feature.counter.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.counter.R
import com.parsfilo.contentapp.feature.counter.model.ReminderSettings
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsSheet(
    initialSettings: ReminderSettings,
    onSave: (ReminderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val d = LocalDimens.current

    var enabled by remember(initialSettings) { mutableStateOf(initialSettings.enabled) }
    var hour by remember(initialSettings) { mutableIntStateOf(initialSettings.hour) }
    var minute by remember(initialSettings) { mutableIntStateOf(initialSettings.minute) }
    var dailyGoal by remember(initialSettings) { mutableIntStateOf(initialSettings.dailyGoal) }
    var streakEnabled by remember(initialSettings) { mutableStateOf(initialSettings.streakReminderEnabled) }

    fun openTimePicker() {
        TimePickerDialog(
            context,
            { _, h, m ->
                hour = h
                minute = m
            },
            hour,
            minute,
            true,
        ).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(d.space16),
            verticalArrangement = Arrangement.spacedBy(d.space12),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.counter_reminder_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = androidx.compose.ui.res.stringResource(R.string.counter_reminder_daily))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            AppButton(
                text = "${androidx.compose.ui.res.stringResource(R.string.counter_reminder_time_label)}: ${String.format(Locale.US, "%02d:%02d", hour, minute)}",
                onClick = { openTimePicker() },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = pluralStringResource(
                    R.plurals.counter_daily_goal_label,
                    dailyGoal,
                    dailyGoal,
                ),
            )
            Slider(
                value = dailyGoal.toFloat(),
                onValueChange = { dailyGoal = it.toInt() },
                valueRange = 33f..1000f,
                enabled = enabled,
            )
            Text(text = androidx.compose.ui.res.stringResource(R.string.counter_goal_slider_value, dailyGoal))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = androidx.compose.ui.res.stringResource(R.string.counter_streak_reminder))
                Switch(checked = streakEnabled, onCheckedChange = { streakEnabled = it })
            }

            AppButton(
                text = androidx.compose.ui.res.stringResource(R.string.counter_save),
                onClick = {
                    onSave(
                        ReminderSettings(
                            enabled = enabled,
                            hour = hour,
                            minute = minute,
                            dailyGoal = dailyGoal,
                            streakReminderEnabled = streakEnabled,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
