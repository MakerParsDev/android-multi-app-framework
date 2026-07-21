package com.parsfilo.contentapp.feature.counter.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.parsfilo.contentapp.feature.counter.R
import com.parsfilo.contentapp.feature.counter.model.ZikirSession

@Composable
fun SessionCompleteDialog(
    session: ZikirSession,
    currentStreak: Int,
    todayTotalCount: Int,
    onShareClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.counter_session_done_title), style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            val minutes = session.durationSeconds / 60
            val seconds = session.durationSeconds % 60
            val durationText = if (minutes > 0) {
                stringResource(R.string.counter_duration_minutes_seconds, minutes, seconds)
            } else {
                pluralStringResource(
                    R.plurals.counter_duration_seconds,
                    seconds.toInt().coerceAtLeast(0),
                    seconds,
                )
            }
            Text(
                text = buildString {
                    appendLine(stringResource(R.string.counter_session_done_body, session.completedCount, session.latinText))
                    appendLine(session.arabicText)
                    appendLine()
                    appendLine(stringResource(R.string.counter_session_duration, durationText))
                    appendLine(stringResource(R.string.counter_today_total, todayTotalCount))
                    if (currentStreak > 0) {
                        appendLine(
                            pluralStringResource(
                                R.plurals.counter_streak_label,
                                currentStreak,
                                currentStreak,
                            )
                        )
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.counter_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onShareClick) {
                Text(text = stringResource(R.string.counter_share_session))
            }
        },
    )
}
