package com.parsfilo.contentapp.feature.counter.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.parsfilo.contentapp.feature.counter.R
import com.parsfilo.contentapp.feature.counter.model.CompletionPersistenceStatus
import com.parsfilo.contentapp.feature.counter.model.ZikirSession

@Composable
fun SessionCompleteDialog(
    session: ZikirSession,
    persistenceStatus: CompletionPersistenceStatus,
    onRetrySave: () -> Unit,
    currentStreak: Int,
    todayTotalCount: Int,
    onShareClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isSaved = persistenceStatus == CompletionPersistenceStatus.SAVED
    AlertDialog(
        onDismissRequest = { if (isSaved) onDismiss() },
        title = {
            Text(
                text = when (persistenceStatus) {
                    CompletionPersistenceStatus.SAVING -> stringResource(R.string.counter_session_saving)
                    CompletionPersistenceStatus.FAILED -> stringResource(R.string.counter_session_save_failed)
                    else -> stringResource(R.string.counter_session_done_title)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
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
                text = if (!isSaved) {
                    stringResource(
                        if (persistenceStatus == CompletionPersistenceStatus.FAILED) {
                            R.string.counter_session_save_retry_description
                        } else {
                            R.string.counter_session_save_pending_description
                        },
                    )
                } else {
                    buildString {
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
                    }
                },
            )
        },
        confirmButton = {
            if (persistenceStatus == CompletionPersistenceStatus.FAILED) {
                TextButton(onClick = onRetrySave) {
                    Text(text = stringResource(R.string.counter_session_retry_save))
                }
            } else {
                TextButton(onClick = onDismiss, enabled = isSaved) {
                    Text(text = stringResource(R.string.counter_continue))
                }
            }
        },
        dismissButton = {
            if (isSaved) {
                TextButton(onClick = onShareClick) {
                    Text(text = stringResource(R.string.counter_share_session))
                }
            }
        },
    )
}
